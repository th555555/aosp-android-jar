/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.server.notification;

import static android.app.Notification.COLOR_DEFAULT;
import static android.app.Notification.FLAG_AUTOGROUP_SUMMARY;
import static android.app.Notification.FLAG_AUTO_CANCEL;
import static android.app.Notification.FLAG_GROUP_SUMMARY;
import static android.app.Notification.FLAG_LOCAL_ONLY;
import static android.app.Notification.FLAG_NO_CLEAR;
import static android.app.Notification.FLAG_ONGOING_EVENT;
import static android.app.Notification.VISIBILITY_PRIVATE;
import static android.app.Notification.VISIBILITY_PUBLIC;
import static android.service.notification.Flags.notificationForceGrouping;
import static android.service.notification.Flags.notificationRegroupOnClassification;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Slog;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * NotificationManagerService helper for auto-grouping notifications.
 */
public class GroupHelper {
    private static final String TAG = "GroupHelper";
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    protected static final String AUTOGROUP_KEY = "ranker_group";

    protected static final int FLAG_INVALID = -1;

    // Flags that all autogroup summaries have
    protected static final int BASE_FLAGS =
            FLAG_AUTOGROUP_SUMMARY | FLAG_GROUP_SUMMARY | FLAG_LOCAL_ONLY;
    // Flag that autogroup summaries inherits if all children have the flag
    private static final int ALL_CHILDREN_FLAG = FLAG_AUTO_CANCEL;
    // Flags that autogroup summaries inherits if any child has them
    private static final int ANY_CHILDREN_FLAGS = FLAG_ONGOING_EVENT | FLAG_NO_CLEAR;

    protected static final String AGGREGATE_GROUP_KEY = "Aggregate_";

    // If an app posts more than NotificationManagerService.AUTOGROUP_SPARSE_GROUPS_AT_COUNT groups
    //  with less than this value, they will be forced grouped
    private static final int MIN_CHILD_COUNT_TO_AVOID_FORCE_GROUPING = 3;

    // Regrouping needed because the channel was updated, ie. importance changed
    static final int REGROUP_REASON_CHANNEL_UPDATE = 0;
    // Regrouping needed because of notification bundling
    static final int REGROUP_REASON_BUNDLE = 1;
    // Regrouping needed because of notification unbundling
    static final int REGROUP_REASON_UNBUNDLE = 2;
    // Regrouping needed because of notification unbundling + the original group summary exists
    static final int REGROUP_REASON_UNBUNDLE_ORIGINAL_GROUP = 3;

    @IntDef(prefix = { "REGROUP_REASON_" }, value = {
        REGROUP_REASON_CHANNEL_UPDATE,
        REGROUP_REASON_BUNDLE,
        REGROUP_REASON_UNBUNDLE,
        REGROUP_REASON_UNBUNDLE_ORIGINAL_GROUP,
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface RegroupingReason {}

    private final Callback mCallback;
    private final int mAutoGroupAtCount;
    private final int mAutogroupSparseGroupsAtCount;
    private final Context mContext;
    private final PackageManager mPackageManager;
    private boolean mIsTestHarnessExempted;

    // Only contains notifications that are not explicitly grouped by the app (aka no group or
    // sort key).
    // userId|packageName -> (keys of notifications that aren't in an explicit app group -> flags)
    @GuardedBy("mUngroupedNotifications")
    private final ArrayMap<String, ArrayMap<String, NotificationAttributes>> mUngroupedNotifications
            = new ArrayMap<>();

    // Contains the list of notifications that should be aggregated (forced grouping)
    // but there are less than mAutoGroupAtCount per section for a package.
    // The primary map's key is the full aggregated group key: userId|pkgName|g:groupName
    // The internal map's key is the notification record key
    @GuardedBy("mAggregatedNotifications")
    private final ArrayMap<FullyQualifiedGroupKey, ArrayMap<String, NotificationAttributes>>
            mUngroupedAbuseNotifications = new ArrayMap<>();

    // Contains the list of group summaries that were canceled when "singleton groups" were
    // force grouped. Key is userId|packageName|g:OriginalGroupName. Used to:
    // 1) remove the original group's children when an app cancels the already removed summary.
    // 2) perform the same side effects that would happen if the group is removed because
    //    all its force-regrouped children are removed (e.g. firing its deleteIntent).
    @GuardedBy("mAggregatedNotifications")
    private final ArrayMap<FullyQualifiedGroupKey, CachedSummary>
            mCanceledSummaries = new ArrayMap<>();

    // Represents the current state of the aggregated (forced grouped) notifications
    // Key is the full aggregated group key: userId|pkgName|g:groupName
    // And groupName is "Aggregate_"+sectionName
    @GuardedBy("mAggregatedNotifications")
    private final ArrayMap<FullyQualifiedGroupKey, ArrayMap<String, NotificationAttributes>>
            mAggregatedNotifications = new ArrayMap<>();

    private static List<NotificationSectioner> NOTIFICATION_SHADE_SECTIONS =
            getNotificationShadeSections();

    private static List<NotificationSectioner> NOTIFICATION_BUNDLE_SECTIONS;

    private static List<NotificationSectioner> getNotificationShadeSections() {
        ArrayList<NotificationSectioner> sectionsList = new ArrayList<>();
        if (android.service.notification.Flags.notificationClassification()) {
            sectionsList.addAll(List.of(
                new NotificationSectioner("PromotionsSection", 0, (record) ->
                        NotificationChannel.PROMOTIONS_ID.equals(record.getChannel().getId())
                        && record.getImportance() < NotificationManager.IMPORTANCE_DEFAULT),
                new NotificationSectioner("SocialSection", 0, (record) ->
                        NotificationChannel.SOCIAL_MEDIA_ID.equals(record.getChannel().getId())
                        && record.getImportance() < NotificationManager.IMPORTANCE_DEFAULT),
                new NotificationSectioner("NewsSection", 0, (record) ->
                        NotificationChannel.NEWS_ID.equals(record.getChannel().getId())
                        && record.getImportance() < NotificationManager.IMPORTANCE_DEFAULT),
                new NotificationSectioner("RecsSection", 0, (record) ->
                        NotificationChannel.RECS_ID.equals(record.getChannel().getId())
                        && record.getImportance() < NotificationManager.IMPORTANCE_DEFAULT)
                ));

            NOTIFICATION_BUNDLE_SECTIONS = new ArrayList<>(sectionsList);
        }

        if (Flags.notificationForceGroupConversations()) {
            // add priority people section
            sectionsList.add(new NotificationSectioner("PeopleSection(priority)", 1, (record) ->
                    record.isConversation() && record.getChannel().isImportantConversation()));

            if (android.app.Flags.sortSectionByTime()) {
                // add single people (alerting) section
                sectionsList.add(new NotificationSectioner("PeopleSection", 0,
                        NotificationRecord::isConversation));
            } else {
                // add people alerting section
                sectionsList.add(new NotificationSectioner("PeopleSection(alerting)", 1, (record) ->
                        record.isConversation()
                        && record.getImportance() >= NotificationManager.IMPORTANCE_DEFAULT));
                // add people silent section
                sectionsList.add(new NotificationSectioner("PeopleSection(silent)", 1, (record) ->
                        record.isConversation()
                        && record.getImportance() < NotificationManager.IMPORTANCE_DEFAULT));
            }
        }

        sectionsList.addAll(List.of(
            new NotificationSectioner("AlertingSection", 0, (record) ->
                record.getImportance() >= NotificationManager.IMPORTANCE_DEFAULT),
            new NotificationSectioner("SilentSection", 1, (record) ->
                record.getImportance() < NotificationManager.IMPORTANCE_DEFAULT)));
        return sectionsList;
    }

    public GroupHelper(Context context, PackageManager packageManager, int autoGroupAtCount,
            int autoGroupSparseGroupsAtCount, Callback callback) {
        mAutoGroupAtCount = autoGroupAtCount;
        mCallback =  callback;
        mContext = context;
        mPackageManager = packageManager;
        mAutogroupSparseGroupsAtCount = autoGroupSparseGroupsAtCount;
        NOTIFICATION_SHADE_SECTIONS = getNotificationShadeSections();
    }

    void setTestHarnessExempted(boolean isExempted) {
        // Allow E2E tests to post ungrouped notifications
        mIsTestHarnessExempted = ActivityManager.isRunningInUserTestHarness() && isExempted;
    }

    private String generatePackageKey(int userId, String pkg) {
        return userId + "|" + pkg;
    }

    @VisibleForTesting
    protected static int getAutogroupSummaryFlags(
            @NonNull final ArrayMap<String, NotificationAttributes> childrenMap) {
        final Collection<NotificationAttributes> children = childrenMap.values();
        boolean allChildrenHasFlag = children.size() > 0;
        int anyChildFlagSet = 0;
        for (NotificationAttributes childAttr: children) {
            if (!hasAnyFlag(childAttr.flags, ALL_CHILDREN_FLAG)) {
                allChildrenHasFlag = false;
            }
            if (hasAnyFlag(childAttr.flags, ANY_CHILDREN_FLAGS)) {
                anyChildFlagSet |= (childAttr.flags & ANY_CHILDREN_FLAGS);
            }
        }
        return BASE_FLAGS | (allChildrenHasFlag ? ALL_CHILDREN_FLAG : 0) | anyChildFlagSet;
    }

    private static boolean hasAnyFlag(int flags, int mask) {
        return (flags & mask) != 0;
    }

    /**
     * Called when a notification is newly posted. Checks whether that notification, and all other
     * active notifications should be grouped or ungrouped atuomatically, and returns whether.
     * @param record The posted notification.
     * @param autogroupSummaryExists Whether a summary for this notification already exists.
     * @return Whether the provided notification should be autogrouped synchronously.
     */
    public boolean onNotificationPosted(NotificationRecord record, boolean autogroupSummaryExists) {
        boolean sbnToBeAutogrouped = false;
        try {
            if (notificationForceGrouping()) {
                final StatusBarNotification sbn = record.getSbn();
                if (!sbn.isAppGroup()) {
                    sbnToBeAutogrouped = maybeGroupWithSections(record, autogroupSummaryExists);
                } else {
                    maybeUngroupOnAppGrouped(record);
                }
            } else {
                final StatusBarNotification sbn = record.getSbn();
                if (!sbn.isAppGroup()) {
                    sbnToBeAutogrouped = maybeGroup(sbn, autogroupSummaryExists);
                } else {
                    maybeUngroup(sbn, false, sbn.getUserId());
                }
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failure processing new notification", e);
        }
        return sbnToBeAutogrouped;
    }

    /**
     * Called when a notification was removed. Checks if that notification was part of an autogroup
     * and triggers any necessary cleanups: summary removal, clearing caches etc.
     *
     * @param record The removed notification.
     */
    public void onNotificationRemoved(NotificationRecord record) {
        try {
            if (notificationForceGrouping()) {
                Slog.wtf(TAG,
                        "This overload of onNotificationRemoved() should not be called if "
                                + "notification_force_grouping is enabled!",
                        new Exception("call stack"));
                onNotificationRemoved(record, new ArrayList<>(), false);
            } else {
                final StatusBarNotification sbn = record.getSbn();
                maybeUngroup(sbn, true, sbn.getUserId());
            }
        } catch (Exception e) {
            Slog.e(TAG, "Error processing canceled notification", e);
        }
    }

    /**
     * A non-app grouped notification has been added or updated
     * Evaluate if:
     * (a) an existing autogroup summary needs updated flags
     * (b) a new autogroup summary needs to be added with correct flags
     * (c) other non-app grouped children need to be moved to the autogroup
     *
     * And stores the list of upgrouped notifications & their flags
     */
    private boolean maybeGroup(StatusBarNotification sbn, boolean autogroupSummaryExists) {
        int flags = 0;
        List<String> notificationsToGroup = new ArrayList<>();
        List<NotificationAttributes> childrenAttr = new ArrayList<>();
        // Indicates whether the provided sbn should be autogrouped by the caller.
        boolean sbnToBeAutogrouped = false;
        synchronized (mUngroupedNotifications) {
            String packageKey = generatePackageKey(sbn.getUserId(), sbn.getPackageName());
            final ArrayMap<String, NotificationAttributes> children =
                    mUngroupedNotifications.getOrDefault(packageKey, new ArrayMap<>());
            NotificationAttributes attr = new NotificationAttributes(sbn.getNotification().flags,
                    sbn.getNotification().getSmallIcon(), sbn.getNotification().color,
                    sbn.getNotification().visibility, Notification.GROUP_ALERT_CHILDREN,
                    sbn.getNotification().getChannelId());
            children.put(sbn.getKey(), attr);
            mUngroupedNotifications.put(packageKey, children);

            if (children.size() >= mAutoGroupAtCount || autogroupSummaryExists) {
                flags = getAutogroupSummaryFlags(children);
                notificationsToGroup.addAll(children.keySet());
                childrenAttr.addAll(children.values());
            }
        }
        if (notificationsToGroup.size() > 0) {
            if (autogroupSummaryExists) {
                NotificationAttributes attr = new NotificationAttributes(flags,
                        sbn.getNotification().getSmallIcon(), sbn.getNotification().color,
                        VISIBILITY_PRIVATE, Notification.GROUP_ALERT_CHILDREN,
                        sbn.getNotification().getChannelId());
                if (Flags.autogroupSummaryIconUpdate()) {
                    attr = updateAutobundledSummaryAttributes(sbn.getPackageName(), childrenAttr,
                            attr);
                }

                mCallback.updateAutogroupSummary(sbn.getUserId(), sbn.getPackageName(),
                        AUTOGROUP_KEY, attr);
            } else {
                Icon summaryIcon = sbn.getNotification().getSmallIcon();
                int summaryIconColor = sbn.getNotification().color;
                int summaryVisibility = VISIBILITY_PRIVATE;
                String summaryChannelId = sbn.getNotification().getChannelId();
                if (Flags.autogroupSummaryIconUpdate()) {
                    // Calculate the initial summary icon, icon color and visibility
                    NotificationAttributes iconAttr = getAutobundledSummaryAttributes(
                            sbn.getPackageName(), childrenAttr);
                    summaryIcon = iconAttr.icon;
                    summaryIconColor = iconAttr.iconColor;
                    summaryVisibility = iconAttr.visibility;
                    summaryChannelId = iconAttr.channelId;
                }

                NotificationAttributes attr = new NotificationAttributes(flags, summaryIcon,
                        summaryIconColor, summaryVisibility, Notification.GROUP_ALERT_CHILDREN,
                        summaryChannelId);
                mCallback.addAutoGroupSummary(sbn.getUserId(), sbn.getPackageName(), sbn.getKey(),
                        AUTOGROUP_KEY, Integer.MAX_VALUE, attr);
            }
            for (String keyToGroup : notificationsToGroup) {
                if (android.app.Flags.checkAutogroupBeforePost()) {
                    if (keyToGroup.equals(sbn.getKey())) {
                        // Autogrouping for the provided notification is to be done synchronously.
                        sbnToBeAutogrouped = true;
                    } else {
                        mCallback.addAutoGroup(keyToGroup, AUTOGROUP_KEY, /*requestSort=*/true);
                    }
                } else {
                    mCallback.addAutoGroup(keyToGroup, AUTOGROUP_KEY, /*requestSort=*/true);
                }
            }
        }
        return sbnToBeAutogrouped;
    }

    /**
     * A notification was added that's app grouped, or a notification was removed.
     * Evaluate whether:
     * (a) an existing autogroup summary needs updated flags
     * (b) if we need to remove our autogroup overlay for this notification
     * (c) we need to remove the autogroup summary
     *
     * And updates the internal state of un-app-grouped notifications and their flags.
     */
    private void maybeUngroup(StatusBarNotification sbn, boolean notificationGone, int userId) {
        boolean removeSummary = false;
        int summaryFlags = FLAG_INVALID;
        boolean updateSummaryFlags = false;
        boolean removeAutogroupOverlay = false;
        List<NotificationAttributes> childrenAttrs = new ArrayList<>();
        synchronized (mUngroupedNotifications) {
            String key = generatePackageKey(sbn.getUserId(), sbn.getPackageName());
            final ArrayMap<String, NotificationAttributes> children =
                    mUngroupedNotifications.getOrDefault(key, new ArrayMap<>());
            if (children.size() == 0) {
                return;
            }

            // if this notif was autogrouped and now isn't
            if (children.containsKey(sbn.getKey())) {
                // if this notification was contributing flags that aren't covered by other
                // children to the summary, reevaluate flags for the summary
                int flags = children.remove(sbn.getKey()).flags;
                // this
                if (hasAnyFlag(flags, ANY_CHILDREN_FLAGS)) {
                    updateSummaryFlags = true;
                    summaryFlags = getAutogroupSummaryFlags(children);
                }
                // if this notification still exists and has an autogroup overlay, but is now
                // grouped by the app, clear the overlay
                if (!notificationGone && sbn.getOverrideGroupKey() != null) {
                    removeAutogroupOverlay = true;
                }

                // If there are no more children left to autogroup, remove the summary
                if (children.size() == 0) {
                    removeSummary = true;
                } else {
                    childrenAttrs.addAll(children.values());
                }
            }
        }

        if (removeSummary) {
            mCallback.removeAutoGroupSummary(userId, sbn.getPackageName(), AUTOGROUP_KEY);
        } else {
            NotificationAttributes attr = new NotificationAttributes(summaryFlags,
                    sbn.getNotification().getSmallIcon(), sbn.getNotification().color,
                    VISIBILITY_PRIVATE, Notification.GROUP_ALERT_CHILDREN,
                    sbn.getNotification().getChannelId());
            boolean attributesUpdated = false;
            if (Flags.autogroupSummaryIconUpdate()) {
                NotificationAttributes newAttr = updateAutobundledSummaryAttributes(
                        sbn.getPackageName(), childrenAttrs, attr);
                if (!newAttr.equals(attr)) {
                    attributesUpdated = true;
                    attr = newAttr;
                }
            }

            if (updateSummaryFlags || attributesUpdated) {
                mCallback.updateAutogroupSummary(userId, sbn.getPackageName(), AUTOGROUP_KEY, attr);
            }
        }
        if (removeAutogroupOverlay) {
            mCallback.removeAutoGroup(sbn.getKey());
        }
    }

    NotificationAttributes getAutobundledSummaryAttributes(@NonNull String packageName,
            @NonNull List<NotificationAttributes> childrenAttr) {
        Icon newIcon = null;
        boolean childrenHaveSameIcon = true;
        int newColor = Notification.COLOR_INVALID;
        boolean childrenHaveSameColor = true;
        int newVisibility = VISIBILITY_PRIVATE;

        // Both the icon drawable and the icon background color are updated according to this rule:
        // - if all child icons are identical => use the common icon
        // - if child icons are different: use the monochromatic app icon, if exists.
        // Otherwise fall back to a generic icon representing a stack.
        for (NotificationAttributes state: childrenAttr) {
            // Check for icon
            if (newIcon == null) {
                newIcon = state.icon;
            } else {
                if (!newIcon.sameAs(state.icon)) {
                    childrenHaveSameIcon = false;
                }
            }
            // Check for color
            if (newColor == Notification.COLOR_INVALID) {
                newColor = state.iconColor;
            } else {
                if (newColor != state.iconColor) {
                    childrenHaveSameColor = false;
                }
            }
            // Check for visibility. If at least one child is public, then set to public
            if (state.visibility == VISIBILITY_PUBLIC) {
                newVisibility = VISIBILITY_PUBLIC;
            }
        }
        if (!childrenHaveSameIcon) {
            newIcon = getMonochromeAppIcon(packageName);
        }
        if (!childrenHaveSameColor) {
            newColor = COLOR_DEFAULT;
        }

        // Use GROUP_ALERT_CHILDREN
        // Unless all children have GROUP_ALERT_SUMMARY => avoid muting all notifications in group
        int newGroupAlertBehavior = Notification.GROUP_ALERT_SUMMARY;
        for (NotificationAttributes attr: childrenAttr) {
            if (attr.groupAlertBehavior != Notification.GROUP_ALERT_SUMMARY) {
                newGroupAlertBehavior = Notification.GROUP_ALERT_CHILDREN;
                break;
            }
        }

        String channelId = !childrenAttr.isEmpty() ? childrenAttr.get(0).channelId : null;

        return new NotificationAttributes(0, newIcon, newColor, newVisibility,
                newGroupAlertBehavior, channelId);
    }

    NotificationAttributes updateAutobundledSummaryAttributes(@NonNull String packageName,
            @NonNull List<NotificationAttributes> childrenAttr,
            @NonNull NotificationAttributes oldAttr) {
        NotificationAttributes newAttr = getAutobundledSummaryAttributes(packageName,
                childrenAttr);
        Icon newIcon = newAttr.icon;
        int newColor = newAttr.iconColor;
        String newChannelId = newAttr.channelId;
        if (newAttr.icon == null) {
            newIcon = oldAttr.icon;
        }
        if (newAttr.iconColor == Notification.COLOR_INVALID) {
            newColor = oldAttr.iconColor;
        }
        if (newAttr.channelId == null) {
            newChannelId = oldAttr.channelId;
        }

        return new NotificationAttributes(oldAttr.flags, newIcon, newColor, newAttr.visibility,
                oldAttr.groupAlertBehavior, newChannelId);
    }

    private NotificationAttributes getSummaryAttributes(String pkgName,
            ArrayMap<String, NotificationAttributes> childrenMap) {
        int flags = getAutogroupSummaryFlags(childrenMap);
        NotificationAttributes attr = getAutobundledSummaryAttributes(pkgName,
                childrenMap.values().stream().toList());
        return new NotificationAttributes(flags, attr.icon, attr.iconColor, attr.visibility,
                attr.groupAlertBehavior, attr.channelId);
    }

    /**
     * Get the monochrome app icon for an app from the adaptive launcher icon
     *  or a fallback generic icon for autogroup summaries.
     *
     * @param pkg packageName of the app
     * @return a monochrome app icon or a fallback generic icon
     */
    @NonNull
    Icon getMonochromeAppIcon(@NonNull final String pkg) {
        Icon monochromeIcon = null;
        final int fallbackIconResId = R.drawable.ic_notification_summary_auto;
        try {
            final Drawable appIcon = mPackageManager.getApplicationIcon(pkg);
            if (appIcon instanceof AdaptiveIconDrawable) {
                if (((AdaptiveIconDrawable) appIcon).getMonochrome() != null) {
                    monochromeIcon = Icon.createWithResourceAdaptiveDrawable(pkg,
                            ((AdaptiveIconDrawable) appIcon).getSourceDrawableResId(), true,
                            -2.0f * AdaptiveIconDrawable.getExtraInsetFraction());
                }
            }
        } catch (NameNotFoundException e) {
            Slog.e(TAG, "Failed to getApplicationIcon() in getMonochromeAppIcon()", e);
        }
        if (monochromeIcon != null) {
            return monochromeIcon;
        } else {
            return Icon.createWithResource(mContext, fallbackIconResId);
        }
    }

    /**
     * A non-app-grouped notification has been added or updated
     * Evaluate if:
     * (a) an existing autogroup summary needs updated attributes
     * (b) a new autogroup summary needs to be added with correct attributes
     * (c) other non-app grouped children need to be moved to the autogroup
     * (d) the notification has been updated from a groupable to a non-groupable section and needs
     *  to trigger a cleanup
     *
     * This method implements autogrouping with sections support.
     *
     * And stores the list of upgrouped notifications & their flags
     */
    private boolean maybeGroupWithSections(NotificationRecord record,
            boolean autogroupSummaryExists) {
        final StatusBarNotification sbn = record.getSbn();
        boolean sbnToBeAutogrouped = false;
        final NotificationSectioner sectioner = getSection(record);
        if (sectioner == null) {
            maybeUngroupOnNonGroupableUpdate(record);
            if (DEBUG) {
                Slog.i(TAG, "Skipping autogrouping for " + record + " no valid section found.");
            }
            return false;
        }

        final String pkgName = sbn.getPackageName();
        final FullyQualifiedGroupKey fullAggregateGroupKey = new FullyQualifiedGroupKey(
                record.getUserId(), pkgName, sectioner);

        // The notification was part of a different section => trigger regrouping
        final FullyQualifiedGroupKey prevSectionKey = getPreviousValidSectionKey(record);
        if (prevSectionKey != null && !fullAggregateGroupKey.equals(prevSectionKey)) {
            if (DEBUG) {
                Slog.i(TAG, "Section changed for: " + record);
            }
            maybeUngroupOnSectionChanged(record, prevSectionKey);
        }

        // This notification is already aggregated
        if (record.getGroupKey().equals(fullAggregateGroupKey.toString())) {
            return false;
        }
        synchronized (mAggregatedNotifications) {
            ArrayMap<String, NotificationAttributes> ungrouped =
                mUngroupedAbuseNotifications.getOrDefault(fullAggregateGroupKey, new ArrayMap<>());
            ungrouped.put(record.getKey(), new NotificationAttributes(
                record.getFlags(),
                record.getNotification().getSmallIcon(),
                record.getNotification().color,
                record.getNotification().visibility,
                record.getNotification().getGroupAlertBehavior(),
                record.getChannel().getId()));
            mUngroupedAbuseNotifications.put(fullAggregateGroupKey, ungrouped);

            // scenario 0: ungrouped notifications
            if (ungrouped.size() >= mAutoGroupAtCount || autogroupSummaryExists) {
                if (DEBUG) {
                    if (ungrouped.size() >= mAutoGroupAtCount) {
                        Slog.i(TAG,
                            "Found >=" + mAutoGroupAtCount
                                + " ungrouped notifications => force grouping");
                    } else {
                        Slog.i(TAG, "Found aggregate summary => force grouping");
                    }
                }

                final ArrayMap<String, NotificationAttributes> aggregatedNotificationsAttrs =
                    mAggregatedNotifications.getOrDefault(fullAggregateGroupKey, new ArrayMap<>());
                aggregatedNotificationsAttrs.putAll(ungrouped);
                mAggregatedNotifications.put(fullAggregateGroupKey, aggregatedNotificationsAttrs);

                // add/update aggregate summary
                updateAggregateAppGroup(fullAggregateGroupKey, record.getKey(),
                        autogroupSummaryExists, sectioner.mSummaryId);

                // add notification to aggregate group
                for (String keyToGroup : ungrouped.keySet()) {
                    if (android.app.Flags.checkAutogroupBeforePost()) {
                        if (keyToGroup.equals(record.getKey())) {
                            // Autogrouping for the posted notification is to be done synchronously.
                            sbnToBeAutogrouped = true;
                        } else {
                            mCallback.addAutoGroup(keyToGroup, fullAggregateGroupKey.toString(),
                                    true);
                        }
                    } else {
                        mCallback.addAutoGroup(keyToGroup, fullAggregateGroupKey.toString(), true);
                    }
                }

                //cleanup mUngroupedAbuseNotifications
                mUngroupedAbuseNotifications.remove(fullAggregateGroupKey);
            }
        }

        return sbnToBeAutogrouped;
    }

    /**
     * A notification was added that was previously part of a valid section and needs to trigger
     * GH state cleanup.
     */
    private void maybeUngroupOnNonGroupableUpdate(NotificationRecord record) {
        maybeUngroupWithSections(record, getPreviousValidSectionKey(record));
    }

    /**
     * A notification was added that was previously part of a different section and needs to trigger
     * GH state cleanup.
     */
    private void maybeUngroupOnSectionChanged(NotificationRecord record,
            FullyQualifiedGroupKey prevSectionKey) {
        maybeUngroupWithSections(record, prevSectionKey);
        if (record.getGroupKey().equals(prevSectionKey.toString())) {
            record.setOverrideGroupKey(null);
        }
    }

    /**
     * A notification was added that is app-grouped.
     */
    private void maybeUngroupOnAppGrouped(NotificationRecord record) {
        FullyQualifiedGroupKey currentSectionKey = getSectionGroupKeyWithFallback(record);

        // The notification was part of a different section => trigger regrouping
        final FullyQualifiedGroupKey prevSectionKey = getPreviousValidSectionKey(record);
        if (prevSectionKey != null && !prevSectionKey.equals(currentSectionKey)) {
            if (DEBUG) {
                Slog.i(TAG, "Section changed for: " + record);
            }
            currentSectionKey = prevSectionKey;
        }

        maybeUngroupWithSections(record, currentSectionKey);
    }

    /**
     * Called when a notification is posted and is either app-grouped or was previously part of
     * a valid section and needs to trigger GH state cleanup.
     *
     * Evaluate whether:
     * (a) an existing autogroup summary needs updated attributes
     * (b) if we need to remove our autogroup overlay for this notification
     * (c) we need to remove the autogroup summary
     *
     * This method implements autogrouping with sections support.
     *
     * And updates the internal state of un-app-grouped notifications and their flags.
     */
    private void maybeUngroupWithSections(NotificationRecord record,
            @Nullable FullyQualifiedGroupKey fullAggregateGroupKey) {
        if (fullAggregateGroupKey == null) {
            if (DEBUG) {
                Slog.i(TAG,
                        "Skipping maybeUngroupWithSections for " + record
                            + " no valid section found.");
            }
            return;
        }

        final StatusBarNotification sbn = record.getSbn();
        final String pkgName = sbn.getPackageName();
        final int userId = record.getUserId();
        synchronized (mAggregatedNotifications) {
            // if this notification still exists and has an autogroup overlay, but is now
            // grouped by the app, clear the overlay
            ArrayMap<String, NotificationAttributes> ungrouped =
                mUngroupedAbuseNotifications.getOrDefault(fullAggregateGroupKey, new ArrayMap<>());
            ungrouped.remove(sbn.getKey());
            mUngroupedAbuseNotifications.put(fullAggregateGroupKey, ungrouped);

            final ArrayMap<String, NotificationAttributes> aggregatedNotificationsAttrs =
                mAggregatedNotifications.getOrDefault(fullAggregateGroupKey, new ArrayMap<>());
            // check if the removed notification was part of the aggregate group
            if (aggregatedNotificationsAttrs.containsKey(record.getKey())) {
                aggregatedNotificationsAttrs.remove(sbn.getKey());
                mAggregatedNotifications.put(fullAggregateGroupKey, aggregatedNotificationsAttrs);

                if (DEBUG) {
                    Slog.i(TAG, "maybeUngroup removeAutoGroup: " + record);
                }

                mCallback.removeAutoGroup(sbn.getKey());

                if (aggregatedNotificationsAttrs.isEmpty()) {
                    if (DEBUG) {
                        Slog.i(TAG, "Aggregate group is empty: " + fullAggregateGroupKey);
                    }
                    mCallback.removeAutoGroupSummary(userId, pkgName,
                            fullAggregateGroupKey.toString());
                    mAggregatedNotifications.remove(fullAggregateGroupKey);
                } else {
                    if (DEBUG) {
                        Slog.i(TAG,
                                "Aggregate group not empty, updating: " + fullAggregateGroupKey);
                    }
                    updateAggregateAppGroup(fullAggregateGroupKey, sbn.getKey(), true, 0);
                }
            }
        }
    }

    /**
     * Called when a notification is newly posted, after some delay, so that the app
     * has a chance to post a group summary or children (complete a group).
     * Checks whether that notification and other active notifications should be forced grouped
     * because their grouping is incorrect:
     *  - missing summary
     *  - only summaries
     *  - sparse groups == multiple groups with very few notifications
     *
     * @param record the notification that was posted
     * @param notificationList the full notification list from NotificationManagerService
     * @param summaryByGroupKey the map of group summaries from NotificationManagerService
     */
    @FlaggedApi(android.service.notification.Flags.FLAG_NOTIFICATION_FORCE_GROUPING)
    protected void onNotificationPostedWithDelay(final NotificationRecord record,
            final List<NotificationRecord> notificationList,
            final Map<String, NotificationRecord> summaryByGroupKey) {
        // Ungrouped notifications are handled separately in
        // {@link #onNotificationPosted(StatusBarNotification, boolean)}
        final StatusBarNotification sbn = record.getSbn();
        if (!sbn.isAppGroup()) {
            return;
        }

        if (record.isCanceled) {
            return;
        }

        if (mIsTestHarnessExempted) {
            return;
        }

        final NotificationSectioner sectioner = getSection(record);
        if (sectioner == null) {
            if (DEBUG) {
                Log.i(TAG, "Skipping autogrouping for " + record + " no valid section found.");
            }
            return;
        }

        final String pkgName = sbn.getPackageName();
        final FullyQualifiedGroupKey fullAggregateGroupKey = new FullyQualifiedGroupKey(
                record.getUserId(), pkgName, sectioner);

        // This notification is already aggregated
        if (record.getGroupKey().equals(fullAggregateGroupKey.toString())) {
            return;
        }

        synchronized (mAggregatedNotifications) {
            // scenario 1: group w/o summary
            // scenario 2: summary w/o children
            if (isGroupChildWithoutSummary(record, summaryByGroupKey) ||
                isGroupSummaryWithoutChildren(record, notificationList)) {
                if (DEBUG) {
                    Log.i(TAG, "isGroupChildWithoutSummary OR isGroupSummaryWithoutChild"
                            + record);
                }
                addToUngroupedAndMaybeAggregate(record, fullAggregateGroupKey, sectioner);
                return;
            }

            // Check if summary & child notifications are not part of the same section/bundle
            // Needs a check here if notification was bundled while enqueued
            if (notificationRegroupOnClassification()
                    && android.service.notification.Flags.notificationClassification()) {
                if (isGroupChildBundled(record, summaryByGroupKey)) {
                    if (DEBUG) {
                        Slog.v(TAG, "isGroupChildInDifferentBundleThanSummary: " + record);
                    }
                    moveNotificationsToNewSection(record.getUserId(), pkgName,
                            List.of(new NotificationMoveOp(record, null, fullAggregateGroupKey)),
                            Map.of(record.getKey(), REGROUP_REASON_BUNDLE));
                    return;
                }
            }

            // scenario 3: sparse/singleton groups
            if (Flags.notificationForceGroupSingletons()) {
                try {
                    groupSparseGroups(record, notificationList, summaryByGroupKey, sectioner,
                            fullAggregateGroupKey);
                } catch (Throwable e) {
                    Slog.wtf(TAG, "Failed to group sparse groups", e);
                }
            }
        }
    }

    @GuardedBy("mAggregatedNotifications")
    private void addToUngroupedAndMaybeAggregate(NotificationRecord record,
            FullyQualifiedGroupKey fullAggregateGroupKey, NotificationSectioner sectioner) {
        ArrayMap<String, NotificationAttributes> ungrouped =
                mUngroupedAbuseNotifications.getOrDefault(fullAggregateGroupKey,
                    new ArrayMap<>());
        ungrouped.put(record.getKey(), new NotificationAttributes(
                record.getFlags(),
                record.getNotification().getSmallIcon(),
                record.getNotification().color,
                record.getNotification().visibility,
                record.getNotification().getGroupAlertBehavior(),
                record.getChannel().getId()));
        mUngroupedAbuseNotifications.put(fullAggregateGroupKey, ungrouped);
        // Create/update summary and group if >= mAutoGroupAtCount notifications
        //  or if aggregate group exists
        boolean hasSummary = !mAggregatedNotifications.getOrDefault(fullAggregateGroupKey,
                new ArrayMap<>()).isEmpty();
        if (ungrouped.size() >= mAutoGroupAtCount || hasSummary) {
            if (DEBUG) {
                if (ungrouped.size() >= mAutoGroupAtCount) {
                    Slog.i(TAG, "Found >=" + mAutoGroupAtCount
                            + " ungrouped notifications => force grouping");
                } else {
                    Slog.i(TAG, "Found aggregate summary => force grouping");
                }
            }
            aggregateUngroupedNotifications(fullAggregateGroupKey, record.getKey(),
                    ungrouped, hasSummary, sectioner.mSummaryId);
        }
    }

    private static boolean isGroupChildBundled(final NotificationRecord record,
            final Map<String, NotificationRecord> summaryByGroupKey) {
        final StatusBarNotification sbn = record.getSbn();
        final String groupKey = record.getSbn().getGroupKey();

        if (!sbn.isAppGroup()) {
            return false;
        }

        if (record.getNotification().isGroupSummary()) {
            return false;
        }

        final NotificationRecord summary = summaryByGroupKey.get(groupKey);
        if (summary == null) {
            return false;
        }

        return isInBundleSection(record);
    }

    private static boolean isInBundleSection(final NotificationRecord record) {
        final NotificationSectioner sectioner = getSection(record);
        return (sectioner != null && NOTIFICATION_BUNDLE_SECTIONS.contains(sectioner));
    }

    /**
     * Called when a notification is removed, so that this helper can adjust the aggregate groups:
     *  - Removes the autogroup summary of the notification's section
     *      if the record was the last child.
     *  - Recalculates the autogroup summary "attributes":
     *      icon, icon color, visibility, groupAlertBehavior, flags - if the removed record was
     *  part of an autogroup.
     *  - Removes the saved summary of the original group, if the record was the last remaining
     *      child of a sparse group that was forced auto-grouped.
     *
     * see also {@link #onNotificationPostedWithDelay(NotificationRecord, List, Map)}
     *
     * @param record the removed notification
     * @param notificationList the full notification list from NotificationManagerService
     * @param sendingDelete whether the removed notification is being removed in a way that sends
     *                     its {@code deleteIntent}
     */
    @FlaggedApi(android.service.notification.Flags.FLAG_NOTIFICATION_FORCE_GROUPING)
    protected void onNotificationRemoved(final NotificationRecord record,
            final List<NotificationRecord> notificationList, boolean sendingDelete) {
        final StatusBarNotification sbn = record.getSbn();
        final String pkgName = sbn.getPackageName();
        final int userId = record.getUserId();

        final FullyQualifiedGroupKey fullAggregateGroupKey = getSectionGroupKeyWithFallback(record);
        if (fullAggregateGroupKey == null) {
            if (DEBUG) {
                Slog.i(TAG,
                        "Skipping autogroup cleanup for " + record + " no valid section found.");
            }
            return;
        }

        synchronized (mAggregatedNotifications) {
            ArrayMap<String, NotificationAttributes> ungrouped =
                mUngroupedAbuseNotifications.getOrDefault(fullAggregateGroupKey, new ArrayMap<>());
            ungrouped.remove(record.getKey());
            mUngroupedAbuseNotifications.put(fullAggregateGroupKey, ungrouped);

            final ArrayMap<String, NotificationAttributes> aggregatedNotificationsAttrs =
                mAggregatedNotifications.getOrDefault(fullAggregateGroupKey, new ArrayMap<>());
            // check if the removed notification was part of the aggregate group
            if (record.getGroupKey().equals(fullAggregateGroupKey.toString())
                    || aggregatedNotificationsAttrs.containsKey(record.getKey())) {
                aggregatedNotificationsAttrs.remove(record.getKey());
                mAggregatedNotifications.put(fullAggregateGroupKey, aggregatedNotificationsAttrs);

                if (aggregatedNotificationsAttrs.isEmpty()) {
                    if (DEBUG) {
                        Slog.i(TAG, "Aggregate group is empty: " + fullAggregateGroupKey);
                    }
                    mCallback.removeAutoGroupSummary(userId, pkgName,
                            fullAggregateGroupKey.toString());
                    mAggregatedNotifications.remove(fullAggregateGroupKey);
                } else {
                    if (DEBUG) {
                        Slog.i(TAG,
                                "Aggregate group not empty, updating: " + fullAggregateGroupKey);
                    }
                    updateAggregateAppGroup(fullAggregateGroupKey, sbn.getKey(), true, 0);
                }

                // Try to cleanup cached summaries if notification was canceled (not snoozed)
                // If the notification was cancelled by an action that fires its delete intent,
                // also fire it for the cached summary.
                if (record.isCanceled) {
                    maybeClearCanceledSummariesCache(pkgName, userId,
                            record.getNotification().getGroup(), notificationList, sendingDelete);
                }
            }
        }
    }

    /**
     * Get the section key for a notification. If the section is invalid, ie. notification is not
     * auto-groupable, then return the previous valid section, if any.
     * @param record the notification
     * @return a section group key, null if not found
     */
    @Nullable
    private FullyQualifiedGroupKey getSectionGroupKeyWithFallback(final NotificationRecord record) {
        final NotificationSectioner sectioner = getSection(record);
        if (sectioner != null) {
            return FullyQualifiedGroupKey.forRecord(record, sectioner);
        } else {
            return getPreviousValidSectionKey(record);
        }
    }

    /**
     * Get the previous valid section key of a notification that may have been updated to an invalid
     * section. This is needed in case a notification is updated as an ungroupable (invalid section)
     *  => auto-groups need to be updated/GH state cleanup.
     * @param record the notification
     * @return a section group key or null if not found
     */
    @Nullable
    private FullyQualifiedGroupKey getPreviousValidSectionKey(final NotificationRecord record) {
        synchronized (mAggregatedNotifications) {
            final String recordKey = record.getKey();
            // Search in ungrouped
            for (Entry<FullyQualifiedGroupKey, ArrayMap<String, NotificationAttributes>>
                        ungroupedSection : mUngroupedAbuseNotifications.entrySet()) {
                if (ungroupedSection.getValue().containsKey(recordKey)) {
                    return ungroupedSection.getKey();
                }
            }
            // Search in aggregated
            for (Entry<FullyQualifiedGroupKey, ArrayMap<String, NotificationAttributes>>
                    aggregatedSection : mAggregatedNotifications.entrySet()) {
                if (aggregatedSection.getValue().containsKey(recordKey)) {
                    return aggregatedSection.getKey();
                }
            }
        }
        return null;
    }

    /**
     * Called when a child notification is removed, after some delay, so that this helper can
     * trigger a forced grouping if the group has become sparse/singleton
     * or only the summary is left.
     *
     * see also {@link #onNotificationPostedWithDelay(NotificationRecord, List, Map)}
     *
     * @param summaryRecord the group summary of the notification that was removed
     * @param notificationList the full notification list from NotificationManagerService
     * @param summaryByGroupKey the map of group summaries from NotificationManagerService
     */
    @FlaggedApi(android.service.notification.Flags.FLAG_NOTIFICATION_FORCE_GROUPING)
    protected void onGroupedNotificationRemovedWithDelay(final NotificationRecord summaryRecord,
            final List<NotificationRecord> notificationList,
            final Map<String, NotificationRecord> summaryByGroupKey) {
        final StatusBarNotification sbn = summaryRecord.getSbn();
        if (!sbn.isAppGroup()) {
            return;
        }

        if (summaryRecord.isCanceled) {
            return;
        }

        if (mIsTestHarnessExempted) {
            return;
        }

        final NotificationSectioner sectioner = getSection(summaryRecord);
        if (sectioner == null) {
            if (DEBUG) {
                Slog.i(TAG,
                        "Skipping autogrouping for " + summaryRecord + " no valid section found.");
            }
            return;
        }

        final String pkgName = sbn.getPackageName();
        final FullyQualifiedGroupKey fullAggregateGroupKey = new FullyQualifiedGroupKey(
                summaryRecord.getUserId(), pkgName, sectioner);

        // This notification is already aggregated
        if (summaryRecord.getGroupKey().equals(fullAggregateGroupKey.toString())) {
            return;
        }

        synchronized (mAggregatedNotifications) {
            if (isGroupSummaryWithoutChildren(summaryRecord, notificationList)) {
                if (DEBUG) {
                    Slog.i(TAG, "isGroupSummaryWithoutChild " + summaryRecord);
                }
                addToUngroupedAndMaybeAggregate(summaryRecord, fullAggregateGroupKey, sectioner);
                return;
            }

            // Check if notification removal turned this group into a sparse/singleton group
            if (Flags.notificationForceGroupSingletons()) {
                try {
                    groupSparseGroups(summaryRecord, notificationList, summaryByGroupKey, sectioner,
                            fullAggregateGroupKey);
                } catch (Throwable e) {
                    Slog.wtf(TAG, "Failed to group sparse groups", e);
                }
            }
        }
    }

    /**
     * Called when a group summary is posted. If there are any ungrouped notifications that are
     * in that group, remove them as they are no longer candidates for autogrouping.
     *
     * @param summaryRecord the NotificationRecord for the newly posted group summary
     * @param notificationList the full notification list from NotificationManagerService
     */
    @FlaggedApi(android.service.notification.Flags.FLAG_NOTIFICATION_FORCE_GROUPING)
    protected void onGroupSummaryAdded(final NotificationRecord summaryRecord,
            final List<NotificationRecord> notificationList) {
        String groupKey = summaryRecord.getSbn().getGroup();
        synchronized (mAggregatedNotifications) {
            final NotificationSectioner sectioner = getSection(summaryRecord);
            if (sectioner == null) {
                Slog.w(TAG, "onGroupSummaryAdded " + summaryRecord + ": no valid section found");
                return;
            }

            FullyQualifiedGroupKey aggregateGroupKey = FullyQualifiedGroupKey.forRecord(
                    summaryRecord, sectioner);
            ArrayMap<String, NotificationAttributes> ungrouped =
                    mUngroupedAbuseNotifications.getOrDefault(aggregateGroupKey,
                            new ArrayMap<>());
            if (ungrouped.isEmpty()) {
                // don't bother looking through the notification list if there are no pending
                // ungrouped notifications in this section (likely to be the most common case)
                return;
            }

            // Look through full notification list for any notifications belonging to this group;
            // remove from ungrouped map if needed, as the presence of the summary means they will
            // now be grouped
            for (NotificationRecord r : notificationList) {
                if (!r.getNotification().isGroupSummary()
                        && groupKey.equals(r.getSbn().getGroup())
                        && ungrouped.containsKey(r.getKey())) {
                    ungrouped.remove(r.getKey());
                }
            }
            mUngroupedAbuseNotifications.put(aggregateGroupKey, ungrouped);
        }
    }

    private record NotificationMoveOp(NotificationRecord record, FullyQualifiedGroupKey oldGroup,
                                      FullyQualifiedGroupKey newGroup) { }

    /**
     * Called when a notification channel is updated (channel attributes have changed), so that this
     * helper can adjust the aggregate groups by moving children if their section has changed. see
     * {@link #onNotificationPostedWithDelay(NotificationRecord, List, Map)}
     *
     * @param userId the userId of the channel
     * @param pkgName the channel's package
     * @param channel the channel that was updated
     * @param notificationList the full notification list from NotificationManagerService
     */
    @FlaggedApi(android.service.notification.Flags.FLAG_NOTIFICATION_FORCE_GROUPING)
    public void onChannelUpdated(final int userId, final String pkgName,
            final NotificationChannel channel, final List<NotificationRecord> notificationList,
            ArrayMap<String, NotificationRecord> summaryByGroupKey) {
        synchronized (mAggregatedNotifications) {
            final ArrayMap<String, Integer> regroupingReasonMap = new ArrayMap<>();
            ArrayMap<String, NotificationRecord> notificationsToCheck = new ArrayMap<>();
            for (NotificationRecord r : notificationList) {
                if (r.getChannel().getId().equals(channel.getId())
                    && r.getSbn().getPackageName().equals(pkgName)
                    && r.getUserId() == userId) {
                    notificationsToCheck.put(r.getKey(), r);
                    regroupingReasonMap.put(r.getKey(), REGROUP_REASON_CHANNEL_UPDATE);
                    if (notificationRegroupOnClassification()) {
                        // Notification is unbundled and original summary found
                        // => regroup in original group
                        if (!isInBundleSection(r)
                                && isOriginalGroupSummaryPresent(r, summaryByGroupKey)) {
                            regroupingReasonMap.put(r.getKey(),
                                    REGROUP_REASON_UNBUNDLE_ORIGINAL_GROUP);
                        }
                    }
                }
            }

            regroupNotifications(userId, pkgName, notificationsToCheck, regroupingReasonMap);
        }
    }

    /**
     * Called when an individuial notification's channel is updated (moved to a new channel),
     * so that this helper can adjust the aggregate groups by moving children
     * if their section has changed.
     * see {@link #onNotificationPostedWithDelay(NotificationRecord, List, Map)}
     *
     * @param record the notification which had its channel updated
     */
    @FlaggedApi(android.service.notification.Flags.FLAG_NOTIFICATION_FORCE_GROUPING)
    public void onChannelUpdated(final NotificationRecord record) {
        synchronized (mAggregatedNotifications) {
            ArrayMap<String, NotificationRecord> notificationsToCheck = new ArrayMap<>();
            notificationsToCheck.put(record.getKey(), record);
            ArrayMap<String, Integer> regroupReasons = new ArrayMap<>();
            regroupReasons.put(record.getKey(), REGROUP_REASON_BUNDLE);
            regroupNotifications(record.getUserId(), record.getSbn().getPackageName(),
                    notificationsToCheck, regroupReasons);
        }
    }

    /**
     * Called when a notification that was classified (bundled) is restored to its original channel.
     * The notification will be restored to its original group, if any/if summary still exists.
     * Otherwise it will be moved to the appropriate section as an ungrouped notification.
     *
     * @param record the notification which had its channel updated
     * @param originalSummaryExists the original group summary exists
     */
    @FlaggedApi(android.service.notification.Flags.FLAG_NOTIFICATION_FORCE_GROUPING)
    public void onNotificationUnbundled(final NotificationRecord record,
            final boolean originalSummaryExists) {
        synchronized (mAggregatedNotifications) {
            ArrayMap<String, NotificationRecord> notificationsToCheck = new ArrayMap<>();
            notificationsToCheck.put(record.getKey(), record);
            regroupNotifications(record.getUserId(), record.getSbn().getPackageName(),
                    notificationsToCheck, Map.of(record.getKey(),
                        originalSummaryExists ? REGROUP_REASON_UNBUNDLE_ORIGINAL_GROUP
                            : REGROUP_REASON_UNBUNDLE));
        }
    }

    @GuardedBy("mAggregatedNotifications")
    private void regroupNotifications(int userId, String pkgName,
            ArrayMap<String, NotificationRecord> notificationsToCheck,
            Map<String, Integer> regroupReasons) {
        // The list of notification operations required after the channel update
        final ArrayList<NotificationMoveOp> notificationsToMove = new ArrayList<>();

        // Check any already auto-grouped notifications that may need to be re-grouped
        // after the channel update
        notificationsToMove.addAll(
                getAutogroupedNotificationsMoveOps(userId, pkgName,
                    notificationsToCheck));

        // Check any ungrouped notifications that may need to be auto-grouped
        // after the channel update
        notificationsToMove.addAll(
                getUngroupedNotificationsMoveOps(userId, pkgName, notificationsToCheck));

        // Handle "grouped correctly" notifications that were re-classified (bundled)
        if (notificationRegroupOnClassification()) {
            notificationsToMove.addAll(
                    getReclassifiedNotificationsMoveOps(userId, pkgName, notificationsToCheck));
        }

        // Batch move to new section
        if (!notificationsToMove.isEmpty()) {
            moveNotificationsToNewSection(userId, pkgName, notificationsToMove, regroupReasons);
        }
    }

    private List<NotificationMoveOp> getReclassifiedNotificationsMoveOps(int userId,
                String pkgName, ArrayMap<String, NotificationRecord> notificationsToCheck) {
        final ArrayList<NotificationMoveOp> notificationsToMove = new ArrayList<>();
        for (NotificationRecord record : notificationsToCheck.values()) {
            if (isChildOfValidAppGroup(record)) {
                // Check if section changes to a bundle section
                NotificationSectioner sectioner = getSection(record);
                if (sectioner != null && NOTIFICATION_BUNDLE_SECTIONS.contains(sectioner)) {
                    FullyQualifiedGroupKey newFullAggregateGroupKey =
                            new FullyQualifiedGroupKey(userId, pkgName, sectioner);
                    if (DEBUG) {
                        Slog.v(TAG, "Regroup after classification: " + record + " to: "
                                + newFullAggregateGroupKey);
                    }
                    notificationsToMove.add(
                            new NotificationMoveOp(record, null, newFullAggregateGroupKey));
                }
            }
        }
        return notificationsToMove;
    }

    /**
     *  Checks if the original group's summary exists for a notification that was regrouped
     * @param r notification to check
     * @param summaryByGroupKey map of the current group summaries
     * @return true if the original group summary exists
     */
    public static boolean isOriginalGroupSummaryPresent(final NotificationRecord r,
            final ArrayMap<String, NotificationRecord> summaryByGroupKey) {
        if (r.getSbn().isAppGroup() && r.getNotification().isGroupChild()) {
            final String oldGroupKey = GroupHelper.getFullAggregateGroupKey(
                    r.getSbn().getPackageName(), r.getOriginalGroupKey(), r.getUserId());
            NotificationRecord groupSummary = summaryByGroupKey.get(oldGroupKey);
            // We only care about app-provided valid groups
            return (groupSummary != null && !GroupHelper.isAggregatedGroup(groupSummary));
        }
        return false;
    }

    @GuardedBy("mAggregatedNotifications")
    private List<NotificationMoveOp> getAutogroupedNotificationsMoveOps(int userId, String pkgName,
            ArrayMap<String, NotificationRecord> notificationsToCheck) {
        final ArrayList<NotificationMoveOp> notificationsToMove = new ArrayList<>();
        final Set<FullyQualifiedGroupKey> oldGroups =
                new HashSet<>(mAggregatedNotifications.keySet());
        // Move auto-grouped updated notifications from the old groups to the new groups (section)
        for (FullyQualifiedGroupKey oldFullAggKey : oldGroups) {
            // Only check aggregate groups that match the same userId & packageName
            if (pkgName.equals(oldFullAggKey.pkg) && userId == oldFullAggKey.userId) {
                final ArrayMap<String, NotificationAttributes> notificationsInAggGroup =
                        mAggregatedNotifications.get(oldFullAggKey);
                if (notificationsInAggGroup == null) {
                    continue;
                }

                FullyQualifiedGroupKey newFullAggregateGroupKey = null;
                for (String key : notificationsInAggGroup.keySet()) {
                    if (notificationsToCheck.get(key) != null) {
                        // check if section changes
                        NotificationSectioner sectioner = getSection(notificationsToCheck.get(key));
                        if (sectioner == null) {
                            continue;
                        }
                        newFullAggregateGroupKey = new FullyQualifiedGroupKey(userId, pkgName,
                                sectioner);
                        if (!oldFullAggKey.equals(newFullAggregateGroupKey)) {
                            if (DEBUG) {
                                Log.i(TAG, "Change section on channel update: " + key);
                            }
                            notificationsToMove.add(
                                    new NotificationMoveOp(notificationsToCheck.get(key),
                                        oldFullAggKey, newFullAggregateGroupKey));
                            notificationsToCheck.remove(key);
                        }
                    }
                }
            }
        }
        return notificationsToMove;
    }

    @GuardedBy("mAggregatedNotifications")
    private List<NotificationMoveOp> getUngroupedNotificationsMoveOps(int userId, String pkgName,
            final ArrayMap<String, NotificationRecord> notificationsToCheck) {
        final ArrayList<NotificationMoveOp> notificationsToMove = new ArrayList<>();
        // Move any remaining ungrouped updated notifications from the old ungrouped list
        // to the new ungrouped section list, if necessary
        if (!notificationsToCheck.isEmpty()) {
            final Set<FullyQualifiedGroupKey> oldUngroupedSectionKeys =
                    new HashSet<>(mUngroupedAbuseNotifications.keySet());
            for (FullyQualifiedGroupKey oldFullAggKey : oldUngroupedSectionKeys) {
                // Only check aggregate groups that match the same userId & packageName
                if (pkgName.equals(oldFullAggKey.pkg) && userId == oldFullAggKey.userId) {
                    final ArrayMap<String, NotificationAttributes> ungroupedOld =
                            mUngroupedAbuseNotifications.get(oldFullAggKey);
                    if (ungroupedOld == null) {
                        continue;
                    }

                    FullyQualifiedGroupKey newFullAggregateGroupKey = null;
                    final Set<String> ungroupedKeys = new HashSet<>(ungroupedOld.keySet());
                    for (String key : ungroupedKeys) {
                        NotificationRecord record = notificationsToCheck.get(key);
                        if (record != null) {
                            // check if section changes
                            NotificationSectioner sectioner = getSection(record);
                            if (sectioner == null) {
                                continue;
                            }
                            newFullAggregateGroupKey = new FullyQualifiedGroupKey(userId, pkgName,
                                    sectioner);
                            if (!oldFullAggKey.equals(newFullAggregateGroupKey)) {
                                if (DEBUG) {
                                    Log.i(TAG, "Change ungrouped section: " + key);
                                }
                                notificationsToMove.add(
                                        new NotificationMoveOp(record, oldFullAggKey,
                                            newFullAggregateGroupKey));
                                notificationsToCheck.remove(key);
                                //Remove from previous ungrouped list
                                ungroupedOld.remove(key);
                            }
                        }
                    }
                    mUngroupedAbuseNotifications.put(oldFullAggKey, ungroupedOld);
                }
            }
        }
        return notificationsToMove;
    }

    @GuardedBy("mAggregatedNotifications")
    private void moveNotificationsToNewSection(final int userId, final String pkgName,
            final List<NotificationMoveOp> notificationsToMove,
            final Map<String, Integer> regroupReasons) {
        record GroupUpdateOp(FullyQualifiedGroupKey groupKey, NotificationRecord record,
                             boolean hasSummary) { }
        // Bundled operations to apply to groups affected by the channel update
        ArrayMap<FullyQualifiedGroupKey, GroupUpdateOp> groupsToUpdate = new ArrayMap<>();

        // App-provided (valid) groups of notifications that were classified (bundled).
        // Summaries will be canceled if all child notifications have been bundled.
        ArrayMap<String, String> originalGroupsOfBundledNotifications = new ArrayMap<>();

        for (NotificationMoveOp moveOp: notificationsToMove) {
            final NotificationRecord record = moveOp.record;
            final FullyQualifiedGroupKey oldFullAggregateGroupKey = moveOp.oldGroup;
            final FullyQualifiedGroupKey newFullAggregateGroupKey = moveOp.newGroup;

            if (DEBUG) {
                Log.i(TAG,
                    "moveNotificationToNewSection: " + record + " " + newFullAggregateGroupKey
                            + " from: " + oldFullAggregateGroupKey + " regroupingReason: "
                            + regroupReasons);
            }

            // Update/remove aggregate summary for old group
            if (oldFullAggregateGroupKey != null) {
                final ArrayMap<String, NotificationAttributes> oldAggregatedNotificationsAttrs =
                        mAggregatedNotifications.getOrDefault(oldFullAggregateGroupKey,
                            new ArrayMap<>());
                oldAggregatedNotificationsAttrs.remove(record.getKey());
                mAggregatedNotifications.put(oldFullAggregateGroupKey,
                        oldAggregatedNotificationsAttrs);

                // Only add once, for triggering notification
                if (!groupsToUpdate.containsKey(oldFullAggregateGroupKey)) {
                    groupsToUpdate.put(oldFullAggregateGroupKey,
                        new GroupUpdateOp(oldFullAggregateGroupKey, record, true));
                }
            } else {
                if (notificationRegroupOnClassification()) {
                    // Null "old aggregate group" => this notification was re-classified from
                    // a valid app-provided group => maybe cancel the original summary
                    // if no children are left
                    originalGroupsOfBundledNotifications.put(record.getKey(), record.getGroupKey());
                }
            }

            // Add moved notifications to the ungrouped list for new group and do grouping
            // after all notifications have been handled
            if (newFullAggregateGroupKey != null) {
                if (notificationRegroupOnClassification()
                    && regroupReasons.getOrDefault(record.getKey(), REGROUP_REASON_CHANNEL_UPDATE)
                        == REGROUP_REASON_UNBUNDLE_ORIGINAL_GROUP) {
                    // Just reset override group key, original summary exists
                    // => will be grouped back to its original group
                    record.setOverrideGroupKey(null);
                } else {
                    final ArrayMap<String, NotificationAttributes> newAggregatedNotificationsAttrs =
                        mAggregatedNotifications.getOrDefault(newFullAggregateGroupKey,
                            new ArrayMap<>());
                    boolean hasSummary = !newAggregatedNotificationsAttrs.isEmpty();
                    ArrayMap<String, NotificationAttributes> ungrouped =
                        mUngroupedAbuseNotifications.getOrDefault(newFullAggregateGroupKey,
                            new ArrayMap<>());
                    ungrouped.put(record.getKey(), new NotificationAttributes(
                        record.getFlags(),
                        record.getNotification().getSmallIcon(),
                        record.getNotification().color,
                        record.getNotification().visibility,
                        record.getNotification().getGroupAlertBehavior(),
                        record.getChannel().getId()));
                    mUngroupedAbuseNotifications.put(newFullAggregateGroupKey, ungrouped);

                    record.setOverrideGroupKey(null);

                    // Only add once, for triggering notification
                    if (!groupsToUpdate.containsKey(newFullAggregateGroupKey)) {
                        groupsToUpdate.put(newFullAggregateGroupKey,
                            new GroupUpdateOp(newFullAggregateGroupKey, record, hasSummary));
                    }
                }
            }
        }

        // Update groups (sections)
        for (FullyQualifiedGroupKey groupKey : groupsToUpdate.keySet()) {
            final ArrayMap<String, NotificationAttributes> aggregatedNotificationsAttrs =
                    mAggregatedNotifications.getOrDefault(groupKey, new ArrayMap<>());
            final ArrayMap<String, NotificationAttributes> ungrouped =
                    mUngroupedAbuseNotifications.getOrDefault(groupKey, new ArrayMap<>());

            NotificationRecord triggeringNotification = groupsToUpdate.get(groupKey).record;
            boolean hasSummary = groupsToUpdate.get(groupKey).hasSummary;
            //Group needs to be created/updated
            if (ungrouped.size() >= mAutoGroupAtCount
                    || (hasSummary && !aggregatedNotificationsAttrs.isEmpty())) {
                NotificationSectioner sectioner = getSection(triggeringNotification);
                if (sectioner == null) {
                    continue;
                }
                aggregateUngroupedNotifications(groupKey, triggeringNotification.getKey(),
                        ungrouped, hasSummary, sectioner.mSummaryId);
            } else {
                // Remove empty groups
                if (aggregatedNotificationsAttrs.isEmpty() && hasSummary) {
                    mCallback.removeAutoGroupSummary(userId, pkgName, groupKey.toString());
                    mAggregatedNotifications.remove(groupKey);
                }
            }
        }

        if (notificationRegroupOnClassification()) {
            // Cancel the summary if it's the last notification of the original app-provided group
            for (String triggeringKey : originalGroupsOfBundledNotifications.keySet()) {
                NotificationRecord canceledSummary =
                        mCallback.removeAppProvidedSummaryOnClassification(triggeringKey,
                        originalGroupsOfBundledNotifications.getOrDefault(triggeringKey, null));
                if (canceledSummary != null) {
                    cacheCanceledSummary(canceledSummary);
                }
            }
        }
    }

    static String getFullAggregateGroupKey(String pkgName,
            String groupName, int userId) {
        return new FullyQualifiedGroupKey(userId, pkgName, groupName).toString();
    }

    /**
     * Returns the full aggregate group key, which contains the userId and package name
     * in addition to the aggregate group key (name).
     * Equivalent to {@link StatusBarNotification#groupKey()}
     */
    static String getFullAggregateGroupKey(NotificationRecord record) {
        return new FullyQualifiedGroupKey(record.getUserId(), record.getSbn().getPackageName(),
                getSection(record)).toString();
    }

    protected static boolean isAggregatedGroup(NotificationRecord record) {
        return (record.mOriginalFlags & Notification.FLAG_AUTOGROUP_SUMMARY) != 0;
    }

    private boolean isNotificationAggregatedInSection(NotificationRecord record,
            NotificationSectioner sectioner) {
        final FullyQualifiedGroupKey fullAggregateGroupKey = FullyQualifiedGroupKey.forRecord(
                record, sectioner);
        return record.getGroupKey().equals(fullAggregateGroupKey.toString());
    }

    private boolean isChildOfValidAppGroup(NotificationRecord record) {
        final StatusBarNotification sbn = record.getSbn();
        if (!sbn.isAppGroup()) {
            return false;
        }

        if (!sbn.getNotification().isGroupChild()) {
            return false;
        }

        if (record.isCanceled) {
            return false;
        }

        final NotificationSectioner sectioner = getSection(record);
        if (sectioner == null) {
            if (DEBUG) {
                Slog.i(TAG, "Skipping autogrouping for " + record + " no valid section found.");
            }
            return false;
        }

        if (isNotificationAggregatedInSection(record, sectioner)) {
            return false;
        }

        return true;
    }

    private static int getNumChildrenForGroup(@NonNull final String groupKey,
            final List<NotificationRecord> notificationList) {
        //TODO (b/349072751): track grouping state in GroupHelper -> do not use notificationList
        int numChildren = 0;
        // find children for this summary
        for (NotificationRecord r : notificationList) {
            if (!r.getNotification().isGroupSummary()
                    && groupKey.equals(r.getSbn().getGroup())) {
                numChildren++;
            }
        }

        if (DEBUG) {
            Log.i(TAG, "getNumChildrenForGroup " + groupKey + " numChild: " + numChildren);
        }
        return numChildren;
    }

    private static boolean isGroupSummaryWithoutChildren(final NotificationRecord record,
            final List<NotificationRecord> notificationList) {
        final StatusBarNotification sbn = record.getSbn();
        final String groupKey = record.getSbn().getGroup();

        // ignore non app groups and non summaries
        if (!sbn.isAppGroup() || !record.getNotification().isGroupSummary()) {
            return false;
        }

        return getNumChildrenForGroup(groupKey, notificationList) == 0;
    }

    private static boolean isGroupChildWithoutSummary(final NotificationRecord record,
            final Map<String, NotificationRecord> summaryByGroupKey) {
        final StatusBarNotification sbn = record.getSbn();
        final String groupKey = record.getSbn().getGroupKey();

        if (!sbn.isAppGroup()) {
            return false;
        }

        if (record.getNotification().isGroupSummary()) {
            return false;
        }

        if (summaryByGroupKey.containsKey(groupKey)) {
            return false;
        }

        return true;
    }

    @GuardedBy("mAggregatedNotifications")
    private void aggregateUngroupedNotifications(FullyQualifiedGroupKey fullAggregateGroupKey,
            String triggeringNotifKey, Map<String, NotificationAttributes> ungrouped,
            final boolean hasSummary, int summaryId) {
        final ArrayMap<String, NotificationAttributes> aggregatedNotificationsAttrs =
                mAggregatedNotifications.getOrDefault(fullAggregateGroupKey, new ArrayMap<>());
        aggregatedNotificationsAttrs.putAll(ungrouped);
        mAggregatedNotifications.put(fullAggregateGroupKey, aggregatedNotificationsAttrs);

        // add/update aggregate summary
        updateAggregateAppGroup(fullAggregateGroupKey, triggeringNotifKey, hasSummary, summaryId);

        // add notification to aggregate group
        for (String key: ungrouped.keySet()) {
            mCallback.addAutoGroup(key, fullAggregateGroupKey.toString(), true);
        }

        //cleanup mUngroupedAbuseNotifications
        mUngroupedAbuseNotifications.remove(fullAggregateGroupKey);
    }

    @GuardedBy("mAggregatedNotifications")
    private void updateAggregateAppGroup(FullyQualifiedGroupKey fullAggregateGroupKey,
            String triggeringNotifKey, boolean hasSummary, int summaryId) {
        final ArrayMap<String, NotificationAttributes> aggregatedNotificationsAttrs =
                mAggregatedNotifications.getOrDefault(fullAggregateGroupKey, new ArrayMap<>());
        NotificationAttributes attr = getSummaryAttributes(fullAggregateGroupKey.pkg,
                aggregatedNotificationsAttrs);
        String channelId = hasSummary ? attr.channelId
                : aggregatedNotificationsAttrs.get(triggeringNotifKey).channelId;
        NotificationAttributes summaryAttr = new NotificationAttributes(attr.flags, attr.icon,
                attr.iconColor, attr.visibility, attr.groupAlertBehavior, channelId);

        if (!hasSummary) {
            if (DEBUG) {
                Log.i(TAG, "Create aggregate summary: " + fullAggregateGroupKey);
            }
            mCallback.addAutoGroupSummary(fullAggregateGroupKey.userId, fullAggregateGroupKey.pkg,
                    triggeringNotifKey, fullAggregateGroupKey.toString(), summaryId, summaryAttr);
        } else {
            if (DEBUG) {
                Log.i(TAG, "Update aggregate summary: " + fullAggregateGroupKey);
            }
            mCallback.updateAutogroupSummary(fullAggregateGroupKey.userId,
                    fullAggregateGroupKey.pkg, fullAggregateGroupKey.toString(), summaryAttr);
        }
    }

    @GuardedBy("mAggregatedNotifications")
    private void groupSparseGroups(final NotificationRecord record,
            final List<NotificationRecord> notificationList,
            final Map<String, NotificationRecord> summaryByGroupKey,
            final NotificationSectioner sectioner,
            final FullyQualifiedGroupKey fullAggregateGroupKey) {
        final ArrayMap<String, NotificationRecord> sparseGroupSummaries = getSparseGroups(
                fullAggregateGroupKey, notificationList, summaryByGroupKey, sectioner);
        if (sparseGroupSummaries.size() >= mAutogroupSparseGroupsAtCount) {
            if (DEBUG) {
                Log.i(TAG,
                    "Aggregate sparse groups for: " + record.getSbn().getPackageName()
                        + " Section: " + sectioner.mName);
            }

            ArrayMap<String, NotificationAttributes> ungrouped =
                    mUngroupedAbuseNotifications.getOrDefault(
                        fullAggregateGroupKey, new ArrayMap<>());
            final ArrayMap<String, NotificationAttributes> aggregatedNotificationsAttrs =
                    mAggregatedNotifications.getOrDefault(fullAggregateGroupKey, new ArrayMap<>());
            final boolean hasSummary = !aggregatedNotificationsAttrs.isEmpty();
            String triggeringKey = null;
            if (!record.getNotification().isGroupSummary()) {
                // Use this record as triggeringKey only if not a group summary (will be removed)
                triggeringKey = record.getKey();
            }
            for (NotificationRecord r : notificationList) {
                // Add notifications for detected sparse groups
                if (sparseGroupSummaries.containsKey(r.getGroupKey())) {
                    // Move child notifications to aggregate group
                    if (!r.getNotification().isGroupSummary()) {
                        if (DEBUG) {
                            Log.i(TAG, "Aggregate notification (sparse group): " + r);
                        }
                        mCallback.addAutoGroup(r.getKey(), fullAggregateGroupKey.toString(), true);
                        aggregatedNotificationsAttrs.put(r.getKey(),
                            new NotificationAttributes(r.getFlags(),
                                r.getNotification().getSmallIcon(), r.getNotification().color,
                                r.getNotification().visibility,
                                r.getNotification().getGroupAlertBehavior(),
                                r.getChannel().getId()));

                        // Pick the first valid triggeringKey
                        if (triggeringKey == null) {
                            triggeringKey = r.getKey();
                        }
                    } else if (r.getNotification().isGroupSummary()) {
                        // Remove summary notifications
                        if (DEBUG) {
                            Log.i(TAG, "Remove app summary (sparse group): " + r);
                        }
                        mCallback.removeAppProvidedSummary(r.getKey());
                        cacheCanceledSummary(r);
                    }
                } else {
                    // Add any notifications left ungrouped
                    if (ungrouped.containsKey(r.getKey())) {
                        if (DEBUG) {
                            Log.i(TAG, "Aggregate ungrouped (sparse group): " + r);
                        }
                        mCallback.addAutoGroup(r.getKey(), fullAggregateGroupKey.toString(), true);
                        aggregatedNotificationsAttrs.put(r.getKey(),ungrouped.get(r.getKey()));
                    }
                }
            }

            mAggregatedNotifications.put(fullAggregateGroupKey, aggregatedNotificationsAttrs);
            // add/update aggregate summary
            updateAggregateAppGroup(fullAggregateGroupKey, triggeringKey, hasSummary,
                    sectioner.mSummaryId);

            //cleanup mUngroupedAbuseNotifications
            mUngroupedAbuseNotifications.remove(fullAggregateGroupKey);
        }
    }

    @VisibleForTesting
    protected ArrayMap<String, NotificationRecord> getSparseGroups(
            final FullyQualifiedGroupKey fullAggregateGroupKey,
            final List<NotificationRecord> notificationList,
            final Map<String, NotificationRecord> summaryByGroupKey,
            final NotificationSectioner sectioner) {
        ArrayMap<String, NotificationRecord> sparseGroups = new ArrayMap<>();
        for (NotificationRecord summary : summaryByGroupKey.values()) {
            if (summary != null && sectioner.isInSection(summary)) {
                if (summary.getSbn().getPackageName().equalsIgnoreCase(fullAggregateGroupKey.pkg)
                        && summary.getUserId() == fullAggregateGroupKey.userId
                        && summary.getSbn().isAppGroup()
                        && !summary.getGroupKey().equals(fullAggregateGroupKey.toString())) {
                    int numChildren = getNumChildrenForGroupWithSection(summary.getSbn().getGroup(),
                            notificationList, sectioner);
                    if (numChildren > 0 && numChildren < MIN_CHILD_COUNT_TO_AVOID_FORCE_GROUPING) {
                        sparseGroups.put(summary.getGroupKey(), summary);
                    }
                }
            }
        }
        return sparseGroups;
    }

    /**
     *  Get the number of children of a group if all match a certain section.
     *  Used for force grouping sparse groups, where the summary may match a section but the
     *  child notifications do not: ie. conversations
     *
     * @param groupKey the group key (name)
     * @param notificationList all notifications list
     * @param sectioner the section to match
     * @return number of children in that group or -1 if section does not match
     */
    private int getNumChildrenForGroupWithSection(final String groupKey,
            final List<NotificationRecord> notificationList,
            final NotificationSectioner sectioner) {
        int numChildren = 0;
        for (NotificationRecord r : notificationList) {
            if (!r.getNotification().isGroupSummary() && groupKey.equals(r.getSbn().getGroup())) {
                NotificationSectioner childSection = getSection(r);
                if (childSection == null || childSection != sectioner) {
                    if (DEBUG) {
                        Slog.i(TAG,
                                "getNumChildrenForGroupWithSection skip because invalid section: "
                                    + groupKey + " r: " + r);
                    }
                    return -1;
                } else {
                    numChildren++;
                }
            }
        }

        if (DEBUG) {
            Slog.i(TAG,
                    "getNumChildrenForGroupWithSection " + groupKey + " numChild: " + numChildren);
        }
        return numChildren;
    }

    @GuardedBy("mAggregatedNotifications")
    private void cacheCanceledSummary(NotificationRecord record) {
        final FullyQualifiedGroupKey groupKey = new FullyQualifiedGroupKey(record.getUserId(),
                record.getSbn().getPackageName(), record.getNotification().getGroup());
        mCanceledSummaries.put(groupKey, new CachedSummary(
                record.getSbn().getId(),
                record.getSbn().getTag(),
                record.getNotification().getGroup(),
                record.getKey(),
                record.getNotification().deleteIntent));
    }

    @GuardedBy("mAggregatedNotifications")
    private void maybeClearCanceledSummariesCache(String pkgName, int userId,
            String groupName, List<NotificationRecord> notificationList,
            boolean sendSummaryDelete) {
        final FullyQualifiedGroupKey findKey = new FullyQualifiedGroupKey(userId, pkgName,
                groupName);
        CachedSummary summary = mCanceledSummaries.get(findKey);
        // Check if any notifications from original group remain
        if (summary != null) {
            if (DEBUG) {
                Log.i(TAG, "Try removing cached summary: " + summary);
            }
            boolean stillHasChildren = false;
            //TODO (b/349072751): track grouping state in GroupHelper -> do not use notificationList
            for (NotificationRecord r : notificationList) {
                if (summary.originalGroupKey.equals(r.getNotification().getGroup())
                    && r.getUser().getIdentifier() == userId
                    && r.getSbn().getPackageName().equals(pkgName)) {
                    stillHasChildren = true;
                    break;
                }
            }
            if (!stillHasChildren) {
                removeCachedSummary(pkgName, userId, summary);
                if (sendSummaryDelete && summary.deleteIntent != null) {
                    mCallback.sendAppProvidedSummaryDeleteIntent(pkgName, summary.deleteIntent);
                }
            }
        }
    }

    @VisibleForTesting
    @GuardedBy("mAggregatedNotifications")
    protected CachedSummary findCanceledSummary(String pkgName, String tag, int id, int userId) {
        for (FullyQualifiedGroupKey key: mCanceledSummaries.keySet()) {
            if (pkgName.equals(key.pkg) && userId == key.userId) {
                CachedSummary summary = mCanceledSummaries.get(key);
                if (summary != null && summary.id == id && TextUtils.equals(tag, summary.tag)) {
                    return summary;
                }
            }
        }
        return null;
    }

    @VisibleForTesting
    @GuardedBy("mAggregatedNotifications")
    protected CachedSummary findCanceledSummary(String pkgName, String tag, int id, int userId,
            String groupName) {
        final FullyQualifiedGroupKey findKey = new FullyQualifiedGroupKey(userId, pkgName,
                groupName);
        CachedSummary summary = mCanceledSummaries.get(findKey);
        if (summary != null && summary.id == id && TextUtils.equals(tag, summary.tag)) {
            return summary;
        } else {
            return null;
        }
    }

    @GuardedBy("mAggregatedNotifications")
    private void removeCachedSummary(String pkgName, int userId, CachedSummary summary) {
        final FullyQualifiedGroupKey key = new FullyQualifiedGroupKey(userId, pkgName,
                summary.originalGroupKey);
        mCanceledSummaries.remove(key);
    }

    protected boolean isUpdateForCanceledSummary(final NotificationRecord record) {
        synchronized (mAggregatedNotifications) {
            if (record.getSbn().isAppGroup() && record.getNotification().isGroupSummary()) {
                CachedSummary cachedSummary = findCanceledSummary(record.getSbn().getPackageName(),
                        record.getSbn().getTag(), record.getSbn().getId(), record.getUserId(),
                        record.getNotification().getGroup());
                return cachedSummary != null;
            }
            return false;
        }
    }

    /**
     * Cancels the original group's children when an app cancels a summary that was 'maybe'
     * previously removed due to forced grouping of a "sparse group".
     *
     * @param pkgName packageName
     * @param tag original summary notification tag
     * @param id original summary notification id
     * @param userId original summary userId
     */
    @FlaggedApi(Flags.FLAG_NOTIFICATION_FORCE_GROUP_SINGLETONS)
    public void maybeCancelGroupChildrenForCanceledSummary(String pkgName, String tag, int id,
            int userId, int cancelReason) {
        synchronized (mAggregatedNotifications) {
            final CachedSummary summary = findCanceledSummary(pkgName, tag, id, userId);
            if (summary != null) {
                if (DEBUG) {
                    Log.i(TAG, "Found cached summary: " + summary.key);
                }
                mCallback.removeNotificationFromCanceledGroup(userId, pkgName,
                        summary.originalGroupKey, cancelReason);
                removeCachedSummary(pkgName, userId, summary);
            }
        }
    }

    static NotificationSectioner getSection(final NotificationRecord record) {
        for (NotificationSectioner sectioner: NOTIFICATION_SHADE_SECTIONS) {
            if (sectioner.isInSection(record)) {
                return sectioner;
            }
        }
        return null;
    }

    record FullyQualifiedGroupKey(int userId, String pkg, String groupName) {
        FullyQualifiedGroupKey(int userId, String pkg, @Nullable NotificationSectioner sectioner) {
            this(userId, pkg, AGGREGATE_GROUP_KEY + (sectioner != null ? sectioner.mName : ""));
        }

        static FullyQualifiedGroupKey forRecord(NotificationRecord record,
                @Nullable NotificationSectioner sectioner) {
            return new FullyQualifiedGroupKey(record.getUserId(), record.getSbn().getPackageName(),
                    sectioner);
        }

        @Override
        public String toString() {
            return userId + "|" + pkg + "|" + "g:" + groupName;
        }
    }

    protected void dump(PrintWriter pw, String prefix) {
        synchronized (mAggregatedNotifications) {
            if (!mUngroupedAbuseNotifications.isEmpty()) {
                pw.println(prefix + "Ungrouped notifications:");
                for (FullyQualifiedGroupKey groupKey: mUngroupedAbuseNotifications.keySet()) {
                    if (!mUngroupedAbuseNotifications.getOrDefault(groupKey, new ArrayMap<>())
                            .isEmpty()) {
                        pw.println(prefix + prefix + groupKey.toString());
                        for (String notifKey : mUngroupedAbuseNotifications.get(groupKey)
                                .keySet()) {
                            pw.println(prefix + prefix + prefix + notifKey);
                        }
                    }
                }
                pw.println("");
            }

            if (!mAggregatedNotifications.isEmpty()) {
                pw.println(prefix + "Autogrouped notifications:");
                for (FullyQualifiedGroupKey groupKey: mAggregatedNotifications.keySet()) {
                    if (!mAggregatedNotifications.getOrDefault(groupKey, new ArrayMap<>())
                            .isEmpty()) {
                        pw.println(prefix + prefix + groupKey.toString());
                        for (String notifKey : mAggregatedNotifications.get(groupKey).keySet()) {
                            pw.println(prefix + prefix + prefix + notifKey);
                        }
                    }
                }
                pw.println("");
            }

            if (!mCanceledSummaries.isEmpty()) {
                pw.println(prefix + "Cached canceled summaries:");
                for (CachedSummary summary: mCanceledSummaries.values()) {
                    pw.println(prefix + prefix + prefix + summary.key + " -> "
                            + summary.originalGroupKey);
                }
                pw.println("");
            }
        }
    }

    protected static class NotificationSectioner {
        final String mName;
        final int mSummaryId;
        private final Predicate<NotificationRecord> mSectionChecker;

        private NotificationSectioner(String name, int summaryId,
                Predicate<NotificationRecord> sectionChecker) {
            mName = name;
            mSummaryId = summaryId;
            mSectionChecker = sectionChecker;
        }

        boolean isInSection(final NotificationRecord record) {
            return isNotificationGroupable(record) && mSectionChecker.test(record);
        }

        private boolean isNotificationGroupable(final NotificationRecord record) {
            if (!Flags.notificationForceGroupConversations()) {
                if (record.isConversation()) {
                    return false;
                }
            }

            Notification notification = record.getSbn().getNotification();
            boolean isColorizedFGS = notification.isForegroundService()
                && notification.isColorized()
                && record.getImportance() > NotificationManager.IMPORTANCE_MIN;
            boolean isCall = record.getImportance() > NotificationManager.IMPORTANCE_MIN
                && notification.isStyle(Notification.CallStyle.class);
            if (isColorizedFGS || isCall) {
                return false;
            }

            if (record.getSbn().getNotification().isMediaNotification()) {
                return false;
            }

            return true;
        }
    }

    record CachedSummary(int id, String tag, String originalGroupKey, String key,
                         @Nullable PendingIntent deleteIntent) { }

    protected static class NotificationAttributes {
        public final int flags;
        public final int iconColor;
        public final Icon icon;
        public final int visibility;
        public final int groupAlertBehavior;
        public final String channelId;

        public NotificationAttributes(int flags, Icon icon, int iconColor, int visibility,
                int groupAlertBehavior, String channelId) {
            this.flags = flags;
            this.icon = icon;
            this.iconColor = iconColor;
            this.visibility = visibility;
            this.groupAlertBehavior = groupAlertBehavior;
            this.channelId = channelId;
        }

        public NotificationAttributes(@NonNull NotificationAttributes attr) {
            this.flags = attr.flags;
            this.icon = attr.icon;
            this.iconColor = attr.iconColor;
            this.visibility = attr.visibility;
            this.groupAlertBehavior = attr.groupAlertBehavior;
            this.channelId = attr.channelId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof NotificationAttributes that)) {
                return false;
            }
            return flags == that.flags && iconColor == that.iconColor && icon.sameAs(that.icon)
                    && visibility == that.visibility
                    && groupAlertBehavior == that.groupAlertBehavior
                    && channelId.equals(that.channelId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(flags, iconColor, icon, visibility, groupAlertBehavior, channelId);
        }

        @Override
        public String toString() {
            return "NotificationAttributes: flags: " + flags + " icon: " + icon + " color: "
                    + iconColor + " vis: " + visibility + " groupAlertBehavior: "
                    + groupAlertBehavior + " channelId: " + channelId;
        }
    }

    protected interface Callback {
        void addAutoGroup(String key, String groupName, boolean requestSort);
        void removeAutoGroup(String key);

        void addAutoGroupSummary(int userId, String pkg, String triggeringKey, String groupName,
                int summaryId, NotificationAttributes summaryAttr);
        void removeAutoGroupSummary(int user, String pkg, String groupKey);

        void updateAutogroupSummary(int userId, String pkg, String groupKey,
                NotificationAttributes summaryAttr);

        // New callbacks for API abuse grouping
        void removeAppProvidedSummary(String key);

        /**
         * Send a cached summary's deleteIntent, when the last of its original children is removed.
         *
         * <p>While technically the group summary was "canceled" much earlier (because it was the
         * summary of a sparse group and its children got reparented), the posting package expected
         * the summary's deleteIntent to fire when the summary is auto-dismissed.
         */
        void sendAppProvidedSummaryDeleteIntent(String pkg, PendingIntent deleteIntent);

        void removeNotificationFromCanceledGroup(int userId, String pkg, String groupKey,
                int cancelReason);

        /**
         * Cancels the group summary of a notification that was regrouped because of classification
         *  (bundling). Only cancels if the summary is the last notification of the original group.
         * @param triggeringKey the triggering child notification key
         * @param groupKey the original group key
         * @return the canceled group summary or null if the summary was not canceled
         */
        @Nullable
        NotificationRecord removeAppProvidedSummaryOnClassification(String triggeringKey,
                @Nullable String groupKey);
    }
}
