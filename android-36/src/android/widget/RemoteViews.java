/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.widget;

import static android.appwidget.flags.Flags.FLAG_DRAW_DATA_PARCEL;
import static android.appwidget.flags.Flags.FLAG_REMOTE_VIEWS_PROTO;
import static android.appwidget.flags.Flags.drawDataParcel;
import static android.appwidget.flags.Flags.remoteAdapterConversion;
import static android.content.res.Flags.FLAG_SELF_TARGETING_ANDROID_RESOURCE_FRRO;
import static android.util.TypedValue.TYPE_INT_COLOR_ARGB8;
import static android.util.proto.ProtoInputStream.NO_MORE_FIELDS;
import static android.view.inputmethod.Flags.FLAG_HOME_SCREEN_HANDWRITING_DELEGATOR;

import android.annotation.AttrRes;
import android.annotation.ColorInt;
import android.annotation.ColorRes;
import android.annotation.DimenRes;
import android.annotation.DrawableRes;
import android.annotation.FlaggedApi;
import android.annotation.IdRes;
import android.annotation.IntDef;
import android.annotation.LayoutRes;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.Px;
import android.annotation.StringRes;
import android.annotation.StyleRes;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.ActivityThread;
import android.app.Application;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager.ServiceCollectionCache;
import android.appwidget.flags.Flags;
import android.compat.annotation.UnsupportedAppUsage;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentSender;
import android.content.om.FabricatedOverlay;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlendMode;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.Process;
import android.os.RemoteException;
import android.os.StrictMode;
import android.os.Trace;
import android.os.UserHandle;
import android.system.Os;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.util.IntArray;
import android.util.Log;
import android.util.LongArray;
import android.util.LongSparseArray;
import android.util.Pair;
import android.util.SizeF;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.util.TypedValue.ComplexDimensionUnit;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import android.util.proto.ProtoStream;
import android.util.proto.ProtoUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.LayoutInflater.Filter;
import android.view.MotionEvent;
import android.view.RemotableViewMethod;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.ViewManager;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.ViewStub;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

import com.android.internal.R;
import com.android.internal.util.Preconditions;
import com.android.internal.widget.IRemoteViewsFactory;
import com.android.internal.widget.remotecompose.core.CoreDocument;
import com.android.internal.widget.remotecompose.core.operations.Theme;
import com.android.internal.widget.remotecompose.player.RemoteComposeDocument;
import com.android.internal.widget.remotecompose.player.RemoteComposePlayer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A class that describes a view hierarchy that can be displayed in
 * another process. The hierarchy is inflated from a layout resource
 * file, and this class provides some basic operations for modifying
 * the content of the inflated hierarchy.
 *
 * <p>{@code RemoteViews} is limited to support for the following layouts:</p>
 * <ul>
 *   <li>{@link android.widget.AdapterViewFlipper}</li>
 *   <li>{@link android.widget.FrameLayout}</li>
 *   <li>{@link android.widget.GridLayout}</li>
 *   <li>{@link android.widget.GridView}</li>
 *   <li>{@link android.widget.LinearLayout}</li>
 *   <li>{@link android.widget.ListView}</li>
 *   <li>{@link android.widget.RelativeLayout}</li>
 *   <li>{@link android.widget.StackView}</li>
 *   <li>{@link android.widget.ViewFlipper}</li>
 * </ul>
 * <p>And the following widgets:</p>
 * <ul>
 *   <li>{@link android.widget.AnalogClock}</li>
 *   <li>{@link android.widget.Button}</li>
 *   <li>{@link android.widget.Chronometer}</li>
 *   <li>{@link android.widget.ImageButton}</li>
 *   <li>{@link android.widget.ImageView}</li>
 *   <li>{@link android.widget.ProgressBar}</li>
 *   <li>{@link android.widget.TextClock}</li>
 *   <li>{@link android.widget.TextView}</li>
 * </ul>
 * <p>As of API 31, the following widgets and layouts may also be used:</p>
 * <ul>
 *     <li>{@link android.widget.CheckBox}</li>
 *     <li>{@link android.widget.RadioButton}</li>
 *     <li>{@link android.widget.RadioGroup}</li>
 *     <li>{@link android.widget.Switch}</li>
 * </ul>
 * <p>Descendants of these classes are not supported.</p>
 */
public class RemoteViews implements Parcelable, Filter {

    private static final String LOG_TAG = "RemoteViews";

    /** The intent extra for whether the view whose checked state changed is currently checked. */
    public static final String EXTRA_CHECKED = "android.widget.extra.CHECKED";

    /**
     * The intent extra that contains the appWidgetId.
     * @hide
     */
    static final String EXTRA_REMOTEADAPTER_APPWIDGET_ID = "remoteAdapterAppWidgetId";

    /**
     * The intent extra that contains {@code true} if inflating as dak text theme.
     * @hide
     */
    static final String EXTRA_REMOTEADAPTER_ON_LIGHT_BACKGROUND = "remoteAdapterOnLightBackground";

    /**
     * The intent extra that contains the bounds for all shared elements.
     */
    public static final String EXTRA_SHARED_ELEMENT_BOUNDS =
            "android.widget.extra.SHARED_ELEMENT_BOUNDS";

    /**
     * Maximum depth of nested views calls from {@link #addView(int, RemoteViews)} and
     * {@link #RemoteViews(RemoteViews, RemoteViews)}.
     */
    private static final int MAX_NESTED_VIEWS = 10;

    /**
     * Maximum number of RemoteViews that can be specified in constructor.
     */
    private static final int MAX_INIT_VIEW_COUNT = 16;

    // The unique identifiers for each custom {@link Action}.
    private static final int SET_ON_CLICK_RESPONSE_TAG = 1;
    private static final int REFLECTION_ACTION_TAG = 2;
    private static final int SET_DRAWABLE_TINT_TAG = 3;
    private static final int VIEW_GROUP_ACTION_ADD_TAG = 4;
    private static final int VIEW_CONTENT_NAVIGATION_TAG = 5;
    private static final int SET_EMPTY_VIEW_ACTION_TAG = 6;
    private static final int VIEW_GROUP_ACTION_REMOVE_TAG = 7;
    private static final int SET_PENDING_INTENT_TEMPLATE_TAG = 8;
    private static final int SET_REMOTE_VIEW_ADAPTER_INTENT_TAG = 10;
    private static final int TEXT_VIEW_DRAWABLE_ACTION_TAG = 11;
    private static final int BITMAP_REFLECTION_ACTION_TAG = 12;
    private static final int TEXT_VIEW_SIZE_ACTION_TAG = 13;
    private static final int VIEW_PADDING_ACTION_TAG = 14;
    private static final int SET_REMOTE_INPUTS_ACTION_TAG = 18;
    private static final int LAYOUT_PARAM_ACTION_TAG = 19;
    private static final int SET_RIPPLE_DRAWABLE_COLOR_TAG = 21;
    private static final int SET_INT_TAG_TAG = 22;
    private static final int REMOVE_FROM_PARENT_ACTION_TAG = 23;
    private static final int RESOURCE_REFLECTION_ACTION_TAG = 24;
    private static final int COMPLEX_UNIT_DIMENSION_REFLECTION_ACTION_TAG = 25;
    private static final int SET_COMPOUND_BUTTON_CHECKED_TAG = 26;
    private static final int SET_RADIO_GROUP_CHECKED = 27;
    private static final int SET_VIEW_OUTLINE_RADIUS_TAG = 28;
    private static final int SET_ON_CHECKED_CHANGE_RESPONSE_TAG = 29;
    private static final int NIGHT_MODE_REFLECTION_ACTION_TAG = 30;
    private static final int SET_REMOTE_COLLECTION_ITEMS_ADAPTER_TAG = 31;
    private static final int ATTRIBUTE_REFLECTION_ACTION_TAG = 32;
    private static final int SET_REMOTE_ADAPTER_TAG = 33;
    private static final int SET_ON_STYLUS_HANDWRITING_RESPONSE_TAG = 34;
    private static final int SET_DRAW_INSTRUCTION_TAG = 35;

    /** @hide **/
    @IntDef(prefix = "MARGIN_", value = {
            MARGIN_LEFT,
            MARGIN_TOP,
            MARGIN_RIGHT,
            MARGIN_BOTTOM,
            MARGIN_START,
            MARGIN_END
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface MarginType {}
    /** The value will apply to the marginLeft. */
    public static final int MARGIN_LEFT = 0;
    /** The value will apply to the marginTop. */
    public static final int MARGIN_TOP = 1;
    /** The value will apply to the marginRight. */
    public static final int MARGIN_RIGHT = 2;
    /** The value will apply to the marginBottom. */
    public static final int MARGIN_BOTTOM = 3;
    /** The value will apply to the marginStart. */
    public static final int MARGIN_START = 4;
    /** The value will apply to the marginEnd. */
    public static final int MARGIN_END = 5;

    @IntDef(prefix = "VALUE_TYPE_", value = {
            VALUE_TYPE_RAW,
            VALUE_TYPE_COMPLEX_UNIT,
            VALUE_TYPE_RESOURCE,
            VALUE_TYPE_ATTRIBUTE
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface ValueType {}
    static final int VALUE_TYPE_RAW = 1;
    static final int VALUE_TYPE_COMPLEX_UNIT = 2;
    static final int VALUE_TYPE_RESOURCE = 3;
    static final int VALUE_TYPE_ATTRIBUTE = 4;

    /** @hide **/
    @IntDef(flag = true, value = {
            FLAG_REAPPLY_DISALLOWED,
            FLAG_WIDGET_IS_COLLECTION_CHILD,
            FLAG_USE_LIGHT_BACKGROUND_LAYOUT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ApplyFlags {}
    /**
     * Whether reapply is disallowed on this remoteview. This maybe be true if some actions modify
     * the layout in a way that isn't recoverable, since views are being removed.
     * @hide
     */
    public static final int FLAG_REAPPLY_DISALLOWED = 1;
    /**
     * This flag indicates whether this RemoteViews object is being created from a
     * RemoteViewsService for use as a child of a widget collection. This flag is used
     * to determine whether or not certain features are available, in particular,
     * setting on click extras and setting on click pending intents. The former is enabled,
     * and the latter disabled when this flag is true.
     * @hide
     */
    public static final int FLAG_WIDGET_IS_COLLECTION_CHILD = 2;
    /**
     * When this flag is set, the views is inflated with {@link #mLightBackgroundLayoutId} instead
     * of {link #mLayoutId}
     * @hide
     */
    public static final int FLAG_USE_LIGHT_BACKGROUND_LAYOUT = 4;

    /**
     * This mask determines which flags are propagated to nested RemoteViews (either added by
     * addView, or set as portrait/landscape/sized RemoteViews).
     */
    static final int FLAG_MASK_TO_PROPAGATE =
            FLAG_WIDGET_IS_COLLECTION_CHILD | FLAG_USE_LIGHT_BACKGROUND_LAYOUT;

    /**
     * A ReadWriteHelper which has the same behavior as ReadWriteHelper.DEFAULT, but which is
     * intentionally a different instance in order to trick Bundle reader so that it doesn't allow
     * lazy initialization.
     */
    private static final Parcel.ReadWriteHelper ALTERNATIVE_DEFAULT = new Parcel.ReadWriteHelper();

    /**
     * Used to restrict the views which can be inflated
     *
     * @see android.view.LayoutInflater.Filter#onLoadClass(java.lang.Class)
     */
    private static final LayoutInflater.Filter INFLATER_FILTER =
            (clazz) -> clazz.isAnnotationPresent(RemoteViews.RemoteView.class);

    /**
     * The maximum waiting time for remote adapter conversion in milliseconds
     *
     * @hide
     */
    private static final int MAX_ADAPTER_CONVERSION_WAITING_TIME_MS = 20_000;

    /**
     * Application that hosts the remote views.
     *
     * @hide
     */
    @UnsupportedAppUsage
    public ApplicationInfo mApplication;

    /**
     * The resource ID of the layout file. (Added to the parcel)
     */
    @UnsupportedAppUsage
    private int mLayoutId;

    /**
     * The resource ID of the layout file in dark text mode. (Added to the parcel)
     */
    private int mLightBackgroundLayoutId = 0;

    /**
     * An array of actions to perform on the view tree once it has been
     * inflated
     */
    @UnsupportedAppUsage
    private ArrayList<Action> mActions;

    /**
     * Maps bitmaps to unique indicies to avoid Bitmap duplication.
     */
    @UnsupportedAppUsage
    private BitmapCache mBitmapCache = new BitmapCache();

    /**
     * Maps Intent ID to RemoteCollectionItems to avoid duplicate items
     */
    private @NonNull RemoteCollectionCache mCollectionCache = new RemoteCollectionCache();

    /** Cache of ApplicationInfos used by collection items. */
    private ApplicationInfoCache mApplicationInfoCache = new ApplicationInfoCache();

    /**
     * Indicates whether or not this RemoteViews object is contained as a child of any other
     * RemoteViews.
     */
    private boolean mIsRoot = true;

    /**
     * Constants to whether or not this RemoteViews is composed of a landscape and portrait
     * RemoteViews.
     */
    private static final int MODE_NORMAL = 0;
    private static final int MODE_HAS_LANDSCAPE_AND_PORTRAIT = 1;
    private static final int MODE_HAS_SIZED_REMOTEVIEWS = 2;

    /**
     * Used in conjunction with the special constructor
     * {@link #RemoteViews(RemoteViews, RemoteViews)} to keep track of the landscape and portrait
     * RemoteViews.
     */
    private RemoteViews mLandscape = null;
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    private RemoteViews mPortrait = null;
    /**
     * List of RemoteViews with their ideal size. There must be at least two if the map is not null.
     *
     * The smallest remote view is always the last element in the list.
     */
    private List<RemoteViews> mSizedRemoteViews = null;

    /**
     * Ideal size for this RemoteViews.
     *
     * Only to be used on children views used in a {@link RemoteViews} with
     * {@link RemoteViews#hasSizedRemoteViews()}.
     */
    private SizeF mIdealSize = null;

    @ApplyFlags
    private int mApplyFlags = 0;

    /**
     * Id to use to override the ID of the top-level view in this RemoteViews.
     *
     * Only used if this RemoteViews is defined from a XML layout value.
     */
    private int mViewId = View.NO_ID;

    /**
     * Id used to uniquely identify a {@link RemoteViews} instance coming from a given provider.
     */
    private long mProviderInstanceId = -1;

    /** Class cookies of the Parcel this instance was read from. */
    private Map<Class, Object> mClassCookies;

    /**
     * {@link LayoutInflater.Factory2} which will be passed into a {@link LayoutInflater} instance
     * used by this class.
     */
    @Nullable
    private LayoutInflater.Factory2 mLayoutInflaterFactory2;

    /**
     * Indicates whether this {@link RemoteViews} was instantiated with a {@link DrawInstructions}
     * object. {@link DrawInstructions} serves as an alternative protocol for the host process
     * to render.
     */
    private boolean mHasDrawInstructions;

    @Nullable
    private SparseArray<PendingIntent> mPendingIntentTemplate;

    @Nullable
    private SparseArray<Intent> mFillInIntent;

    private static final InteractionHandler DEFAULT_INTERACTION_HANDLER =
            (view, pendingIntent, response) ->
                    startPendingIntent(view, pendingIntent, response.getLaunchOptions(view));

    private static final ArrayMap<MethodKey, MethodArgs> sMethods = new ArrayMap<>();

    /**
     * This key is used to perform lookups in sMethods without causing allocations.
     */
    private static final MethodKey sLookupKey = new MethodKey();

    /**
     * @hide
     */
    public void setRemoteInputs(@IdRes int viewId, RemoteInput[] remoteInputs) {
        mActions.add(new SetRemoteInputsAction(viewId, remoteInputs));
    }

    /**
     * Sets {@link LayoutInflater.Factory2} to be passed into {@link LayoutInflater} used
     * by this class instance. It has to be set before the views are inflated to have any effect.
     *
     * The factory callbacks will be called on the background thread so the implementation needs
     * to be thread safe.
     *
     * @hide
     */
    public void setLayoutInflaterFactory(@Nullable LayoutInflater.Factory2 factory) {
        mLayoutInflaterFactory2 = factory;
    }

    /**
     * Returns currently set {@link LayoutInflater.Factory2}.
     *
     * @hide
     */
    @Nullable
    public LayoutInflater.Factory2 getLayoutInflaterFactory() {
        return mLayoutInflaterFactory2;
    }

    /**
     * Reduces all images and ensures that they are all below the given sizes.
     *
     * @param maxWidth the maximum width allowed
     * @param maxHeight the maximum height allowed
     *
     * @hide
     */
    public void reduceImageSizes(int maxWidth, int maxHeight) {
        ArrayList<Bitmap> cache = mBitmapCache.mBitmaps;
        for (int i = 0; i < cache.size(); i++) {
            Bitmap bitmap = cache.get(i);
            cache.set(i, Icon.scaleDownIfNecessary(bitmap, maxWidth, maxHeight));
        }
    }

    /**
     * Sets an integer tag to the view.
     *
     * @hide
     */
    public void setIntTag(@IdRes int viewId, @IdRes int key, int tag) {
        addAction(new SetIntTagAction(viewId, key, tag));
    }

    /**
     * Set a view tag associating a View with an ID to be used for widget interaction usage events
     * ({@link android.app.usage.UsageEvents.Event}). When this RemoteViews is applied to a bound
     * widget, any clicks or scrolls on the tagged view will be reported to
     * {@link android.app.usage.UsageStatsManager} using this tag.
     *
     * @param viewId ID of the View whose tag will be set
     * @param tag The integer tag to use for the event
     *
     * @see android.appwidget.AppWidgetManager#EVENT_TYPE_WIDGET_INTERACTION
     * @see android.appwidget.AppWidgetManager#EXTRA_EVENT_CLICKED_VIEWS
     * @see android.appwidget.AppWidgetManager#EXTRA_EVENT_SCROLLED_VIEWS
     * @see android.app.usage.UsageStatsManager#queryEventsForSelf
     */
    @FlaggedApi(Flags.FLAG_ENGAGEMENT_METRICS)
    public void setUsageEventTag(@IdRes int viewId, int tag) {
        addAction(new SetIntTagAction(viewId, com.android.internal.R.id.remoteViewsMetricsId, tag));
    }

    /**
     * Set that it is disallowed to reapply another remoteview with the same layout as this view.
     * This should be done if an action is destroying the view tree of the base layout.
     *
     * @hide
     */
    public void addFlags(@ApplyFlags int flags) {
        mApplyFlags = mApplyFlags | flags;

        int flagsToPropagate = flags & FLAG_MASK_TO_PROPAGATE;
        if (flagsToPropagate != 0) {
            if (hasSizedRemoteViews()) {
                for (RemoteViews remoteView : mSizedRemoteViews) {
                    remoteView.addFlags(flagsToPropagate);
                }
            } else if (hasLandscapeAndPortraitLayouts()) {
                mLandscape.addFlags(flagsToPropagate);
                mPortrait.addFlags(flagsToPropagate);
            }
        }
    }

    /**
     * @hide
     */
    public boolean hasFlags(@ApplyFlags int flag) {
        return (mApplyFlags & flag) == flag;
    }

    /**
     * Stores information related to reflection method lookup.
     */
    static class MethodKey {
        public Class targetClass;
        public Class paramClass;
        public String methodName;

        @Override
        public boolean equals(@Nullable Object o) {
            if (!(o instanceof MethodKey)) {
                return false;
            }
            MethodKey p = (MethodKey) o;
            return Objects.equals(p.targetClass, targetClass)
                    && Objects.equals(p.paramClass, paramClass)
                    && Objects.equals(p.methodName, methodName);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(targetClass) ^ Objects.hashCode(paramClass)
                    ^ Objects.hashCode(methodName);
        }

        public void set(Class targetClass, Class paramClass, String methodName) {
            this.targetClass = targetClass;
            this.paramClass = paramClass;
            this.methodName = methodName;
        }
    }


    /**
     * Stores information related to reflection method lookup result.
     */
    static class MethodArgs {
        public MethodHandle syncMethod;
        public MethodHandle asyncMethod;
        public String asyncMethodName;
    }

    /**
     * This annotation indicates that a subclass of View is allowed to be used
     * with the {@link RemoteViews} mechanism.
     */
    @Target({ ElementType.TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RemoteView {
    }

    /**
     * Exception to send when something goes wrong executing an action
     *
     */
    public static class ActionException extends RuntimeException {
        public ActionException(Exception ex) {
            super(ex);
        }
        public ActionException(String message) {
            super(message);
        }
        /**
         * @hide
         */
        public ActionException(Throwable t) {
            super(t);
        }
    }

    /**
     * Handler for view interactions (such as clicks) within a RemoteViews.
     *
     * @hide
     */
    public interface InteractionHandler {
        /**
         * Invoked when the user performs an interaction on the View.
         *
         * @param view the View with which the user interacted
         * @param pendingIntent the base PendingIntent associated with the view
         * @param response the response to the interaction, which knows how to fill in the
         *                 attached PendingIntent
         *
         * @hide
         */
        boolean onInteraction(
                View view,
                PendingIntent pendingIntent,
                RemoteResponse response);

        /**
         * Invoked when an AbsListView is scrolled.
         * @param view view that was scrolled
         *
         * @hide
         */
        default void onScroll(@NonNull AbsListView view) {}
    }

    /**
     * Base class for all actions that can be performed on an
     * inflated view.
     *
     * SUBCLASSES MUST BE IMMUTABLE SO CLONE WORKS!!!!!
     */
    private abstract static class Action {
        @IdRes
        @UnsupportedAppUsage
        int mViewId;

        public abstract void apply(View root, ViewGroup rootParent, ActionApplyParams params)
                throws ActionException;

        public static final int MERGE_REPLACE = 0;
        public static final int MERGE_APPEND = 1;
        public static final int MERGE_IGNORE = 2;

        public void setHierarchyRootData(HierarchyRootData root) {
            // Do nothing
        }

        @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
        public int mergeBehavior() {
            return MERGE_REPLACE;
        }

        public abstract int getActionTag();

        public String getUniqueKey() {
            return (getActionTag() + "_" + mViewId);
        }

        /**
         * This is called on the background thread. It should perform any non-ui computations
         * and return the final action which will run on the UI thread.
         * Override this if some of the tasks can be performed async.
         */
        public Action initActionAsync(ViewTree root, ViewGroup rootParent,
                ActionApplyParams params) {
            return this;
        }

        public boolean prefersAsyncApply() {
            return false;
        }

        /** See {@link RemoteViews#visitUris(Consumer)}. **/
        public void visitUris(@NonNull Consumer<Uri> visitor) {
            // Nothing to visit by default.
        }

        /** See {@link RemoteViews#visitIcons(Consumer)}. **/
        public void visitIcons(@NonNull Consumer<Icon> visitor) {
            // Nothing to visit by default.
        }

        public abstract void writeToParcel(Parcel dest, int flags);

        /**
         * Override to return true if this Action can be serialized to Protobuf, and implement
         * writeToProto / createFromProto.
         *
         * If this returns false, then the action will be omitted from RemoteViews previews created
         * with createPreviewFromProto / writePreviewToProto.
         *
         * Because Parcelables should not be serialized to disk, any action that contains an Intent,
         * PendingIntent, or Bundle should return false here.
         */
        public boolean canWriteToProto() {
            return false;
        }

        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Action class used during async inflation of RemoteViews. Subclasses are not parcelable.
     */
    private abstract static class RuntimeAction extends Action {
        @Override
        public final int getActionTag() {
            return 0;
        }

        @Override
        public final void writeToParcel(Parcel dest, int flags) {
            throw new UnsupportedOperationException();
        }
    }

    // Constant used during async execution. It is not parcelable.
    private static final Action ACTION_NOOP = new RuntimeAction() {
        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) { }
    };

    /**
     * Merges the passed RemoteViews actions with this RemoteViews actions according to
     * action-specific merge rules.
     *
     * @param newRv
     *
     * @hide
     */
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    public void mergeRemoteViews(RemoteViews newRv) {
        if (newRv == null) return;
        // We first copy the new RemoteViews, as the process of merging modifies the way the actions
        // reference the bitmap cache. We don't want to modify the object as it may need to
        // be merged and applied multiple times.
        RemoteViews copy = new RemoteViews(newRv);

        HashMap<String, Action> map = new HashMap<String, Action>();
        if (mActions == null) {
            mActions = new ArrayList<Action>();
        }

        int count = mActions.size();
        for (int i = 0; i < count; i++) {
            Action a = mActions.get(i);
            map.put(a.getUniqueKey(), a);
        }

        ArrayList<Action> newActions = copy.mActions;
        if (newActions == null) return;
        count = newActions.size();
        for (int i = 0; i < count; i++) {
            Action a = newActions.get(i);
            String key = newActions.get(i).getUniqueKey();
            int mergeBehavior = newActions.get(i).mergeBehavior();
            if (map.containsKey(key) && mergeBehavior == Action.MERGE_REPLACE) {
                mActions.remove(map.get(key));
                map.remove(key);
            }

            // If the merge behavior is ignore, we don't bother keeping the extra action
            if (mergeBehavior == Action.MERGE_REPLACE || mergeBehavior == Action.MERGE_APPEND) {
                mActions.add(a);
            }
        }

        // Because pruning can remove the need for bitmaps, we reconstruct the caches.
        reconstructCaches();
    }

    /**
     * Return {@code true} only if this {@code RemoteViews} is a legacy list widget that uses
     * {@code Intent} for inflating child entries.
     *
     * @hide
     */
    public boolean isLegacyListRemoteViews() {
        return mCollectionCache.mIdToUriMapping.size() > 0;
    }

    /**
     * Note all {@link Uri} that are referenced internally, with the expectation that Uri permission
     * grants will need to be issued to ensure the recipient of this object is able to render its
     * contents.
     * See b/281044385 for more context and examples about what happens when this isn't done
     * correctly.
     *
     * @hide
     */
    public void visitUris(@NonNull Consumer<Uri> visitor) {
        if (mActions != null) {
            for (int i = 0; i < mActions.size(); i++) {
                mActions.get(i).visitUris(visitor);
            }
        }
        if (mSizedRemoteViews != null) {
            for (int i = 0; i < mSizedRemoteViews.size(); i++) {
                mSizedRemoteViews.get(i).visitUris(visitor);
            }
        }
        if (mLandscape != null) {
            mLandscape.visitUris(visitor);
        }
        if (mPortrait != null) {
            mPortrait.visitUris(visitor);
        }
    }

    /**
     * Note all {@link Icon} that are referenced internally.
     * @hide
     */
    public void visitIcons(@NonNull Consumer<Icon> visitor) {
        if (mActions != null) {
            for (int i = 0; i < mActions.size(); i++) {
                mActions.get(i).visitIcons(visitor);
            }
        }
        if (mSizedRemoteViews != null) {
            for (int i = 0; i < mSizedRemoteViews.size(); i++) {
                mSizedRemoteViews.get(i).visitIcons(visitor);
            }
        }
        if (mLandscape != null) {
            mLandscape.visitIcons(visitor);
        }
        if (mPortrait != null) {
            mPortrait.visitIcons(visitor);
        }
    }

    /**
     * @hide
     * @return True if there is a change
     */
    public boolean replaceRemoteCollections(int viewId) {
        boolean isActionReplaced = false;
        if (mActions != null) {
            for (int i = 0; i < mActions.size(); i++) {
                Action action = mActions.get(i);
                if (action instanceof SetRemoteCollectionItemListAdapterAction itemsAction
                        && itemsAction.mViewId == viewId
                        && itemsAction.mServiceIntent != null) {
                    SetRemoteCollectionItemListAdapterAction newCollectionAction =
                            new SetRemoteCollectionItemListAdapterAction(
                                    itemsAction.mViewId, itemsAction.mServiceIntent);
                    newCollectionAction.mIntentId = itemsAction.mIntentId;
                    newCollectionAction.mIsReplacedIntoAction = true;
                    mActions.set(i, newCollectionAction);
                    isActionReplaced = true;
                } else if (action instanceof SetRemoteViewsAdapterIntent intentAction
                        && intentAction.mViewId == viewId) {
                    mActions.set(i, new SetRemoteCollectionItemListAdapterAction(
                            intentAction.mViewId, intentAction.mIntent));
                    isActionReplaced = true;
                } else if (action instanceof ViewGroupActionAdd groupAction
                        && groupAction.mNestedViews != null) {
                    isActionReplaced |= groupAction.mNestedViews.replaceRemoteCollections(viewId);
                }
            }
        }
        if (mSizedRemoteViews != null) {
            for (int i = 0; i < mSizedRemoteViews.size(); i++) {
                isActionReplaced |= mSizedRemoteViews.get(i).replaceRemoteCollections(viewId);
            }
        }
        if (mLandscape != null) {
            isActionReplaced |= mLandscape.replaceRemoteCollections(viewId);
        }
        if (mPortrait != null) {
            isActionReplaced |= mPortrait.replaceRemoteCollections(viewId);
        }

        return isActionReplaced;
    }

    /**
     * @return True if has set remote adapter using service intent
     * @hide
     */
    public boolean hasLegacyLists() {
        if (mActions != null) {
            for (int i = 0; i < mActions.size(); i++) {
                Action action = mActions.get(i);
                if ((action instanceof SetRemoteCollectionItemListAdapterAction itemsAction
                        && itemsAction.mServiceIntent != null)
                        || (action instanceof SetRemoteViewsAdapterIntent intentAction
                                && intentAction.mIntent != null)
                        || (action instanceof ViewGroupActionAdd groupAction
                                && groupAction.mNestedViews != null
                                && groupAction.mNestedViews.hasLegacyLists())) {
                    return true;
                }
            }
        }
        if (mSizedRemoteViews != null) {
            for (int i = 0; i < mSizedRemoteViews.size(); i++) {
                if (mSizedRemoteViews.get(i).hasLegacyLists()) {
                    return true;
                }
            }
        }
        if (mLandscape != null && mLandscape.hasLegacyLists()) {
            return true;
        }
        return mPortrait != null && mPortrait.hasLegacyLists();
    }

    private static void visitIconUri(Icon icon, @NonNull Consumer<Uri> visitor) {
        if (icon != null && (icon.getType() == Icon.TYPE_URI
                || icon.getType() == Icon.TYPE_URI_ADAPTIVE_BITMAP)) {
            visitor.accept(icon.getUri());
        }
    }

    private static class RemoteViewsContextWrapper extends ContextWrapper {
        private final Context mContextForResources;

        RemoteViewsContextWrapper(Context context, Context contextForResources) {
            super(context);
            mContextForResources = contextForResources;
        }

        @Override
        public Resources getResources() {
            return mContextForResources.getResources();
        }

        @Override
        public Resources.Theme getTheme() {
            return mContextForResources.getTheme();
        }

        @Override
        public String getPackageName() {
            return mContextForResources.getPackageName();
        }

        @Override
        public UserHandle getUser() {
            return mContextForResources.getUser();
        }

        @Override
        public int getUserId() {
            return mContextForResources.getUserId();
        }

        @Override
        public boolean isRestricted() {
            // Override isRestricted and direct to resource's implementation. The isRestricted is
            // used for determining the risky resources loading, e.g. fonts, thus direct to context
            // for resource.
            return mContextForResources.isRestricted();
        }
    }

    private static class SetEmptyView extends Action {
        int mEmptyViewId;

        SetEmptyView(@IdRes int viewId, @IdRes int emptyViewId) {
            this.mViewId = viewId;
            this.mEmptyViewId = emptyViewId;
        }

        SetEmptyView(Parcel in) {
            this.mViewId = in.readInt();
            this.mEmptyViewId = in.readInt();
        }

        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(this.mViewId);
            out.writeInt(this.mEmptyViewId);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            final View view = root.findViewById(mViewId);
            if (!(view instanceof AdapterView<?>)) return;

            AdapterView<?> adapterView = (AdapterView<?>) view;

            final View emptyView = root.findViewById(mEmptyViewId);
            if (emptyView == null) return;

            adapterView.setEmptyView(emptyView);
        }

        @Override
        public int getActionTag() {
            return SET_EMPTY_VIEW_ACTION_TAG;
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            final long token = out.start(RemoteViewsProto.Action.SET_EMPTY_VIEW_ACTION);
            out.write(RemoteViewsProto.SetEmptyViewAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            out.write(RemoteViewsProto.SetEmptyViewAction.EMPTY_VIEW_ID,
                    appResources.getResourceName(mViewId));
            out.end(token);
        }

        public static PendingResources<Action> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            final long token = in.start(RemoteViewsProto.Action.SET_EMPTY_VIEW_ACTION);
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.SetEmptyViewAction.VIEW_ID:
                        values.put(RemoteViewsProto.SetEmptyViewAction.VIEW_ID,
                                in.readString(RemoteViewsProto.SetEmptyViewAction.VIEW_ID));
                        break;
                    case (int) RemoteViewsProto.SetEmptyViewAction.EMPTY_VIEW_ID:
                        values.put(RemoteViewsProto.SetEmptyViewAction.EMPTY_VIEW_ID,
                                in.readString(RemoteViewsProto.SetEmptyViewAction.EMPTY_VIEW_ID));
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
            in.end(token);

            checkContainsKeys(values, new long[]{RemoteViewsProto.SetEmptyViewAction.VIEW_ID,
                    RemoteViewsProto.SetEmptyViewAction.EMPTY_VIEW_ID});

            return (context, resources, rootData, depth) -> {
                int viewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.SetEmptyViewAction.VIEW_ID);
                int emptyViewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.SetEmptyViewAction.EMPTY_VIEW_ID);
                return new SetEmptyView(viewId, emptyViewId);
            };
        }
    }

    private static class SetPendingIntentTemplate extends Action {
        @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
        PendingIntent mPendingIntentTemplate;

        public SetPendingIntentTemplate(@IdRes int id, PendingIntent pendingIntentTemplate) {
            this.mViewId = id;
            this.mPendingIntentTemplate = pendingIntentTemplate;
        }

        public SetPendingIntentTemplate(Parcel parcel) {
            mViewId = parcel.readInt();
            mPendingIntentTemplate = PendingIntent.readPendingIntentOrNullFromParcel(parcel);
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            PendingIntent.writePendingIntentOrNullToParcel(mPendingIntentTemplate, dest);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            final View target = root.findViewById(mViewId);
            if (target == null) return;

            // If the view isn't an AdapterView, setting a PendingIntent template doesn't make sense
            if (target instanceof AdapterView<?>) {
                AdapterView<?> av = (AdapterView<?>) target;
                // The PendingIntent template is stored in the view's tag.
                OnItemClickListener listener = (parent, view, position, id) -> {
                    RemoteResponse response = findRemoteResponseTag(view);
                    if (response != null) {
                        response.handleViewInteraction(view, params.handler);
                    }
                };
                av.setOnItemClickListener(listener);
                av.setTag(mPendingIntentTemplate);
            } else {
                Log.e(LOG_TAG, "Cannot setPendingIntentTemplate on a view which is not" +
                        "an AdapterView (id: " + mViewId + ")");
                return;
            }
        }

        @Nullable
        private RemoteResponse findRemoteResponseTag(@Nullable View rootView) {
            if (rootView == null) return null;

            ArrayDeque<View> viewsToCheck = new ArrayDeque<>();
            viewsToCheck.addLast(rootView);

            while (!viewsToCheck.isEmpty()) {
                View view = viewsToCheck.removeFirst();
                Object tag = view.getTag(R.id.fillInIntent);
                if (tag instanceof RemoteResponse) return (RemoteResponse) tag;
                if (!(view instanceof ViewGroup)) continue;

                ViewGroup viewGroup = (ViewGroup) view;
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    viewsToCheck.addLast(viewGroup.getChildAt(i));
                }
            }

            return null;
        }

        @Override
        public int getActionTag() {
            return SET_PENDING_INTENT_TEMPLATE_TAG;
        }
    }

    /**
     * Cache of {@link ApplicationInfo}s that can be used to ensure that the same
     * {@link ApplicationInfo} instance is used throughout the RemoteViews.
     */
    private static class ApplicationInfoCache {
        private final Map<Pair<String, Integer>, ApplicationInfo> mPackageUserToApplicationInfo;

        ApplicationInfoCache() {
            mPackageUserToApplicationInfo = new ArrayMap<>();
        }

        /**
         * Adds the {@link ApplicationInfo} to the cache if it's not present. Returns either the
         * provided {@code applicationInfo} or a previously added value with the same package name
         * and uid.
         */
        @Nullable
        ApplicationInfo getOrPut(@Nullable ApplicationInfo applicationInfo) {
            Pair<String, Integer> key = getPackageUserKey(applicationInfo);
            if (key == null) return null;
            return mPackageUserToApplicationInfo.computeIfAbsent(key, ignored -> applicationInfo);
        }

        /** Puts the {@link ApplicationInfo} in the cache, replacing any previously stored value. */
        void put(@Nullable ApplicationInfo applicationInfo) {
            Pair<String, Integer> key = getPackageUserKey(applicationInfo);
            if (key == null) return;
            mPackageUserToApplicationInfo.put(key, applicationInfo);
        }

        /**
         * Returns the currently stored {@link ApplicationInfo} from the cache matching
         * {@code  applicationInfo}, or null if there wasn't any.
         */
        @Nullable ApplicationInfo get(@Nullable ApplicationInfo applicationInfo) {
            Pair<String, Integer> key = getPackageUserKey(applicationInfo);
            if (key == null) return null;
            return mPackageUserToApplicationInfo.get(key);
        }
    }

    private class SetRemoteCollectionItemListAdapterAction extends Action {
        private @Nullable RemoteCollectionItems mItems;
        final Intent mServiceIntent;
        int mIntentId = -1;
        boolean mIsReplacedIntoAction = false;

        SetRemoteCollectionItemListAdapterAction(@IdRes int id,
                @NonNull RemoteCollectionItems items) {
            mViewId = id;
            items.setHierarchyRootData(getHierarchyRootData());
            mItems = items;
            mServiceIntent = null;
        }

        SetRemoteCollectionItemListAdapterAction(@IdRes int id, Intent intent) {
            mViewId = id;
            mItems = null;
            mServiceIntent = intent;
        }

        SetRemoteCollectionItemListAdapterAction(Parcel parcel) {
            mViewId = parcel.readInt();
            mIntentId = parcel.readInt();
            mIsReplacedIntoAction = parcel.readBoolean();
            mServiceIntent = parcel.readTypedObject(Intent.CREATOR);
            mItems = mServiceIntent != null
                    ? null
                    : new RemoteCollectionItems(parcel, getHierarchyRootData());
        }

        @Override
        public void setHierarchyRootData(HierarchyRootData rootData) {
            if (mItems != null) {
                mItems.setHierarchyRootData(rootData);
                return;
            }

            if (mIntentId != -1) {
                // Set the root data for items in the cache instead
                mCollectionCache.setHierarchyDataForId(mIntentId, rootData);
            }
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            dest.writeInt(mIntentId);
            dest.writeBoolean(mIsReplacedIntoAction);
            dest.writeTypedObject(mServiceIntent, flags);
            if (mItems != null) {
                mItems.writeToParcel(dest, flags, /* attached= */ true);
            }
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params)
                throws ActionException {
            View target = root.findViewById(mViewId);
            if (target == null) return;

            RemoteCollectionItems items = mIntentId == -1
                    ? mItems == null
                            ? new RemoteCollectionItems.Builder().build()
                            : mItems
                    : mCollectionCache.getItemsForId(mIntentId);

            // Ensure that we are applying to an AppWidget root
            if (!(rootParent instanceof AppWidgetHostView)) {
                Log.e(LOG_TAG, "setRemoteAdapter can only be used for "
                        + "AppWidgets (root id: " + mViewId + ")");
                return;
            }

            if (!(target instanceof AdapterView)) {
                Log.e(LOG_TAG, "Cannot call setRemoteAdapter on a view which is not "
                        + "an AdapterView (id: " + mViewId + ")");
                return;
            }

            AdapterView adapterView = (AdapterView) target;
            Adapter adapter = adapterView.getAdapter();
            boolean onLightBackground = hasFlags(FLAG_USE_LIGHT_BACKGROUND_LAYOUT);
            // We can reuse the adapter if it's a RemoteCollectionItemsAdapter and the view type
            // count hasn't increased. Note that AbsListView allocates a fixed size array for view
            // recycling in setAdapter, so we must call setAdapter again if the number of view types
            // increases.
            if (adapter instanceof RemoteCollectionItemsAdapter
                    && adapter.getViewTypeCount() >= items.getViewTypeCount()) {
                try {
                    ((RemoteCollectionItemsAdapter) adapter)
                            .setData(
                                    items,
                                    params.handler,
                                    params.colorResources,
                                    onLightBackground);
                } catch (Throwable throwable) {
                    // setData should never failed with the validation in the items builder, but if
                    // it does, catch and rethrow.
                    throw new ActionException(throwable);
                }
                return;
            }

            try {
                adapterView.setAdapter(
                        new RemoteCollectionItemsAdapter(
                                items, params.handler, params.colorResources, onLightBackground));
            } catch (Throwable throwable) {
                // This could throw if the AdapterView somehow doesn't accept BaseAdapter due to
                // a type error.
                throw new ActionException(throwable);
            }
            if (adapterView instanceof AbsListView listView) {
                listView.setOnScrollListener(new AbsListView.OnScrollListener() {
                    @Override
                    public void onScrollStateChanged(AbsListView view, int scrollState) {
                        if (scrollState != SCROLL_STATE_IDLE) {
                            params.handler.onScroll(view);
                        }
                    }

                    @Override
                    public void onScroll(AbsListView view, int firstVisibleItem,
                            int visibleItemCount, int totalItemCount) {
                    }
                });
            }
        }

        @Override
        public int getActionTag() {
            return SET_REMOTE_COLLECTION_ITEMS_ADAPTER_TAG;
        }

        @Override
        public String getUniqueKey() {
            return (SET_REMOTE_ADAPTER_TAG + "_" + mViewId);
        }

        @Override
        public void visitUris(@NonNull Consumer<Uri> visitor) {
            if (mItems == null) {
                // Null item indicates adapter conversion took place, so the URIs in cached items
                // need to be validated.
                RemoteCollectionItems cachedItems = mCollectionCache.getItemsForId(mIntentId);
                if (cachedItems != null) {
                    cachedItems.visitUris(visitor);
                }
                return;
            }

            mItems.visitUris(visitor);
        }

        @Override
        public void visitIcons(Consumer<Icon> visitor) {
            if (mItems == null) {
                RemoteCollectionItems cachedItems = mCollectionCache.getItemsForId(mIntentId);
                if (cachedItems != null) {
                    cachedItems.visitIcons(visitor);
                }
                return;
            }

            mItems.visitIcons(visitor);
        }

        @Override
        public boolean canWriteToProto() {
            // Skip actions that do not contain items (intent only actions)
            return mItems != null;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            if (mItems == null) return;
            final long token = out.start(
                    RemoteViewsProto.Action.SET_REMOTE_COLLECTION_ITEM_LIST_ADAPTER_ACTION);
            out.write(RemoteViewsProto.SetRemoteCollectionItemListAdapterAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            final long itemsToken = out.start(
                    RemoteViewsProto.SetRemoteCollectionItemListAdapterAction.ITEMS);
            mItems.writeToProto(context, out, /* attached= */ true);
            out.end(itemsToken);
            out.end(token);
        }
    }

    private PendingResources<Action> createSetRemoteCollectionItemListAdapterActionFromProto(
            ProtoInputStream in) throws Exception {
        final LongSparseArray<Object> values = new LongSparseArray<>();

        final long token = in.start(
                RemoteViewsProto.Action.SET_REMOTE_COLLECTION_ITEM_LIST_ADAPTER_ACTION);
        while (in.nextField() != NO_MORE_FIELDS) {
            switch (in.getFieldNumber()) {
                case (int) RemoteViewsProto.SetRemoteCollectionItemListAdapterAction.VIEW_ID:
                    values.put(RemoteViewsProto.SetRemoteCollectionItemListAdapterAction.VIEW_ID,
                            in.readString(
                                    RemoteViewsProto
                                            .SetRemoteCollectionItemListAdapterAction.VIEW_ID));
                    break;
                case (int) RemoteViewsProto.SetRemoteCollectionItemListAdapterAction.ITEMS:
                    final long itemsToken = in.start(
                            RemoteViewsProto.SetRemoteCollectionItemListAdapterAction.ITEMS);
                    values.put(RemoteViewsProto.SetRemoteCollectionItemListAdapterAction.ITEMS,
                            RemoteCollectionItems.createFromProto(in));
                    in.end(itemsToken);
                    break;
                default:
                    Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                            + ProtoUtils.currentFieldToString(in));
            }
        }
        in.end(token);

        checkContainsKeys(values,
                new long[]{RemoteViewsProto.SetRemoteCollectionItemListAdapterAction.VIEW_ID,
                        RemoteViewsProto.SetRemoteCollectionItemListAdapterAction.ITEMS});

        return (context, resources, rootData, depth) -> {
            int viewId = getAsIdentifier(resources, values,
                    RemoteViewsProto.SetRemoteCollectionItemListAdapterAction.VIEW_ID);
            return new SetRemoteCollectionItemListAdapterAction(viewId,
                    ((PendingResources<RemoteCollectionItems>) values.get(
                            RemoteViewsProto.SetRemoteCollectionItemListAdapterAction.ITEMS))
                            .create(context, resources, rootData, depth));
        };
    }

    /**
     * The maximum size for RemoteViews with converted RemoteCollectionItemsAdapter.
     * When converting RemoteViewsAdapter to RemoteCollectionItemsAdapter, we want to put size
     * limits on each unique RemoteCollectionItems in order to not exceed the transaction size limit
     * for each parcel (typically 1 MB). We leave a certain ratio of the maximum size as a buffer
     * for missing calculations of certain parameters (e.g. writing a RemoteCollectionItems to the
     * parcel will write its Id array as well, but that is missing when writing itschild RemoteViews
     * directly to the parcel as we did in RemoteViewsService)
     *
     * @hide
     */
    private static final int MAX_SINGLE_PARCEL_SIZE = (int) (1_000_000 * 0.8);

    /**
     * @hide
     */
    public CompletableFuture<Void> collectAllIntents(int bitmapSizeLimit,
            @NonNull ServiceCollectionCache collectionCache) {
        return mCollectionCache.collectAllIntentsNoComplete(this, bitmapSizeLimit,
                collectionCache);
    }

    private class RemoteCollectionCache {
        private final SparseArray<String> mIdToUriMapping = new SparseArray<>();
        private final Map<String, RemoteCollectionItems> mUriToCollectionMapping = new HashMap<>();

        RemoteCollectionCache() { }

        RemoteCollectionCache(RemoteCollectionCache src) {
            for (int i = 0; i < src.mIdToUriMapping.size(); i++) {
                String uri = src.mIdToUriMapping.valueAt(i);
                mIdToUriMapping.put(src.mIdToUriMapping.keyAt(i), uri);
                mUriToCollectionMapping.put(uri, src.mUriToCollectionMapping.get(uri));
            }
        }

        RemoteCollectionCache(Parcel in) {
            int cacheSize = in.readInt();
            HierarchyRootData currentRootData = new HierarchyRootData(mBitmapCache,
                    this,
                    mApplicationInfoCache,
                    mClassCookies);
            for (int i = 0; i < cacheSize; i++) {
                int intentId = in.readInt();
                String intentUri = in.readString8();
                RemoteCollectionItems items = new RemoteCollectionItems(in, currentRootData);
                addMapping(intentId, intentUri, items);
            }
        }

        void addMapping(int intentId, String intentUri, RemoteCollectionItems items) {
            mIdToUriMapping.put(intentId, intentUri);
            mUriToCollectionMapping.put(intentUri, items);
        }


        void setHierarchyDataForId(int intentId, HierarchyRootData data) {
            String uri = mIdToUriMapping.get(intentId);
            if (mUriToCollectionMapping.get(uri) == null) {
                Log.e(LOG_TAG, "Error setting hierarchy data for id=" + intentId);
                return;
            }

            RemoteCollectionItems items = mUriToCollectionMapping.get(uri);
            items.setHierarchyRootData(data);
        }

        RemoteCollectionItems getItemsForId(int intentId) {
            String uri = mIdToUriMapping.get(intentId);
            return mUriToCollectionMapping.get(uri);
        }

        public @NonNull CompletableFuture<Void> collectAllIntentsNoComplete(
                @NonNull RemoteViews inViews, int bitmapSizeLimit,
                @NonNull ServiceCollectionCache collectionCache) {
            SparseArray<Intent> idToIntentMapping = new SparseArray<>();
            // Collect the number of uinque Intent (which is equal to the number of new connections
            // to make) for size allocation and exclude certain collections from being written to
            // the parcel to better estimate the space left for reallocation.
            collectAllIntentsInternal(inViews, idToIntentMapping);

            // Calculate the individual size here
            int numOfIntents = idToIntentMapping.size();
            if (numOfIntents == 0) {
                Log.e(LOG_TAG, "Possibly notifying updates for nonexistent view Id");
                return CompletableFuture.completedFuture(null);
            }

            Parcel sizeTestParcel = Parcel.obtain();
            // Write self RemoteViews to the parcel, which includes the actions/bitmaps/collection
            // cache to see how much space is left for the RemoteCollectionItems that are to be
            // updated.
            RemoteViews.this.writeToParcel(sizeTestParcel,
                    /* flags= */ 0,
                    /* intentsToIgnore= */ idToIntentMapping);
            int remainingSize = MAX_SINGLE_PARCEL_SIZE - sizeTestParcel.dataSize();
            sizeTestParcel.recycle();

            int individualSize = remainingSize < 0
                    ? 0
                    : remainingSize / numOfIntents;

            int individualBitmapSizeLimit = (bitmapSizeLimit - getBitmapMemoryUsedByActions())
                    / numOfIntents;

            return connectAllUniqueIntents(individualSize, individualBitmapSizeLimit,
                    idToIntentMapping, collectionCache);
        }

        private void collectAllIntentsInternal(@NonNull RemoteViews inViews,
                @NonNull SparseArray<Intent> idToIntentMapping) {
            if (inViews.hasSizedRemoteViews()) {
                for (RemoteViews remoteViews : inViews.mSizedRemoteViews) {
                    collectAllIntentsInternal(remoteViews, idToIntentMapping);
                }
            } else if (inViews.hasLandscapeAndPortraitLayouts()) {
                collectAllIntentsInternal(inViews.mLandscape, idToIntentMapping);
                collectAllIntentsInternal(inViews.mPortrait, idToIntentMapping);
            } else if (inViews.mActions != null) {
                for (Action action : inViews.mActions) {
                    if (action instanceof SetRemoteCollectionItemListAdapterAction rca) {
                        // Deal with the case where the intent is replaced into the action list
                        if (rca.mIntentId != -1 && !rca.mIsReplacedIntoAction) {
                            continue;
                        }

                        if (rca.mIntentId != -1 && rca.mIsReplacedIntoAction) {
                            rca.mIsReplacedIntoAction = false;

                            // Avoid redundant connections for the same intent. Also making sure
                            // that the number of connections we are making is always equal to the
                            // nmuber of unique intents that are being used for the updates.
                            if (idToIntentMapping.contains(rca.mIntentId)) {
                                continue;
                            }

                            idToIntentMapping.put(rca.mIntentId, rca.mServiceIntent);
                            rca.mItems = null;
                            continue;
                        }

                        // Differentiate between the normal collection actions and the ones with
                        // intents.
                        if (rca.mServiceIntent != null) {
                            final String uri = rca.mServiceIntent.toUri(0);
                            int index = mIdToUriMapping.indexOfValueByValue(uri);
                            if (index == -1) {
                                int newIntentId = mIdToUriMapping.size();
                                rca.mIntentId = newIntentId;
                                mIdToUriMapping.put(newIntentId, uri);
                            } else {
                                rca.mIntentId = mIdToUriMapping.keyAt(index);
                                rca.mItems = null;
                                continue;
                            }

                            idToIntentMapping.put(rca.mIntentId, rca.mServiceIntent);
                            rca.mItems = null;
                        } else {
                            for (RemoteViews views : rca.mItems.mViews) {
                                collectAllIntentsInternal(views, idToIntentMapping);
                            }
                        }
                    } else if (action instanceof ViewGroupActionAdd vgaa
                            && vgaa.mNestedViews != null) {
                        collectAllIntentsInternal(vgaa.mNestedViews, idToIntentMapping);
                    }
                }
            }
        }

        private @NonNull CompletableFuture<Void> connectAllUniqueIntents(int individualSize,
                int individualBitmapSize, @NonNull SparseArray<Intent> idToIntentMapping,
                @NonNull ServiceCollectionCache collectionCache) {
            List<CompletableFuture<Void>> intentFutureList = new ArrayList<>();
            for (int i = 0; i < idToIntentMapping.size(); i++) {
                String currentIntentUri = mIdToUriMapping.get(idToIntentMapping.keyAt(i));
                Intent currentIntent = idToIntentMapping.valueAt(i);
                intentFutureList.add(getItemsFutureFromIntentWithTimeout(currentIntent,
                        individualSize, individualBitmapSize, collectionCache)
                        .thenAccept(items -> {
                            items.setHierarchyRootData(getHierarchyRootData());
                            mUriToCollectionMapping.put(currentIntentUri, items);
                        }));
            }

            return CompletableFuture.allOf(intentFutureList.toArray(CompletableFuture[]::new));
        }

        private static CompletableFuture<RemoteCollectionItems> getItemsFutureFromIntentWithTimeout(
                Intent intent, int individualSize, int individualBitmapSize,
                @NonNull ServiceCollectionCache collectionCache) {
            if (intent == null) {
                Log.e(LOG_TAG, "Null intent received when generating adapter future");
                return CompletableFuture.completedFuture(new RemoteCollectionItems
                        .Builder().build());
            }

            final Context context = ActivityThread.currentApplication();

            final CompletableFuture<RemoteCollectionItems> result = new CompletableFuture<>();
            String contextPackageName = context.getPackageName();
            ComponentName intentComponent = intent.getComponent();
            if (contextPackageName != null
                    && intentComponent != null
                    && (!contextPackageName.equals(intentComponent.getPackageName()))) {
                // We shouldn't allow for connections to other packages
                result.complete(new RemoteCollectionItems.Builder().build());
                return result;
            }

            collectionCache.connectAndConsume(intent, iBinder -> {
                RemoteCollectionItems items;
                try {
                    items = IRemoteViewsFactory.Stub.asInterface(iBinder)
                            .getRemoteCollectionItems(individualSize,
                                    individualBitmapSize);
                } catch (RemoteException re) {
                    items = new RemoteCollectionItems.Builder().build();
                    Log.e(LOG_TAG, "Error getting collection items from the"
                            + " factory", re);
                }

                if (items == null) {
                    items = new RemoteCollectionItems.Builder().build();
                }

                result.complete(items);
            }, result.defaultExecutor());

            result.completeOnTimeout(
                    new RemoteCollectionItems.Builder().build(),
                    MAX_ADAPTER_CONVERSION_WAITING_TIME_MS, TimeUnit.MILLISECONDS);

            return result;
        }

        public void writeToParcel(Parcel out, int flags,
                @Nullable SparseArray<Intent> intentsToIgnore) {
            out.writeInt(mIdToUriMapping.size());
            for (int i = 0; i < mIdToUriMapping.size(); i++) {
                int currentIntentId = mIdToUriMapping.keyAt(i);
                if (intentsToIgnore != null && intentsToIgnore.contains(currentIntentId)) {
                    // Skip writing collections that are to be updated in the following steps to
                    // better estimate the RemoteViews size.
                    continue;
                }
                out.writeInt(currentIntentId);
                String intentUri = mIdToUriMapping.valueAt(i);
                out.writeString8(intentUri);
                mUriToCollectionMapping.get(intentUri).writeToParcel(out, flags, true);
            }
        }

        public void writeToProto(Context context, ProtoOutputStream out) {
            final long token = out.start(RemoteViewsProto.REMOTE_COLLECTION_CACHE);
            for (int i = 0; i < mIdToUriMapping.size(); i++) {
                final long entryToken = out.start(RemoteViewsProto.RemoteCollectionCache.ENTRIES);
                out.write(RemoteViewsProto.RemoteCollectionCache.Entry.ID,
                        mIdToUriMapping.keyAt(i));
                String intentUri = mIdToUriMapping.valueAt(i);
                out.write(RemoteViewsProto.RemoteCollectionCache.Entry.URI, intentUri);
                final long itemsToken = out.start(
                        RemoteViewsProto.RemoteCollectionCache.Entry.ITEMS);
                mUriToCollectionMapping.get(intentUri).writeToProto(context, out, /* attached= */
                        true);
                out.end(itemsToken);
                out.end(entryToken);
            }
            out.end(token);
        }
    }

    private PendingResources<RemoteCollectionCache> populateRemoteCollectionCacheFromProto(
            ProtoInputStream in) throws Exception {
        final ArrayList<LongSparseArray<Object>> entries = new ArrayList<>();
        final long token = in.start(RemoteViewsProto.REMOTE_COLLECTION_CACHE);
        while (in.nextField() != NO_MORE_FIELDS) {
            switch (in.getFieldNumber()) {
                case (int) RemoteViewsProto.RemoteCollectionCache.ENTRIES:
                    final LongSparseArray<Object> entry = new LongSparseArray<>();

                    final long entryToken = in.start(
                            RemoteViewsProto.RemoteCollectionCache.ENTRIES);
                    while (in.nextField() != NO_MORE_FIELDS) {
                        switch (in.getFieldNumber()) {
                            case (int) RemoteViewsProto.RemoteCollectionCache.Entry.ID:
                                entry.put(RemoteViewsProto.RemoteCollectionCache.Entry.ID,
                                        in.readInt(
                                                RemoteViewsProto.RemoteCollectionCache.Entry.ID));
                                break;
                            case (int) RemoteViewsProto.RemoteCollectionCache.Entry.URI:
                                entry.put(RemoteViewsProto.RemoteCollectionCache.Entry.URI,
                                        in.readString(
                                                RemoteViewsProto.RemoteCollectionCache.Entry.URI));
                                break;
                            case (int) RemoteViewsProto.RemoteCollectionCache.Entry.ITEMS:
                                final long itemsToken = in.start(
                                        RemoteViewsProto.RemoteCollectionCache.Entry.ITEMS);
                                entry.put(RemoteViewsProto.RemoteCollectionCache.Entry.ITEMS,
                                        RemoteCollectionItems.createFromProto(in));
                                in.end(itemsToken);
                                break;
                            default:
                                Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                        + ProtoUtils.currentFieldToString(in));
                        }
                    }
                    in.end(entryToken);

                    checkContainsKeys(entry,
                            new long[]{RemoteViewsProto.RemoteCollectionCache.Entry.ID,
                                    RemoteViewsProto.RemoteCollectionCache.Entry.URI,
                                    RemoteViewsProto.RemoteCollectionCache.Entry.ITEMS});

                    entries.add(entry);
                    break;
                default:
                    Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                            + ProtoUtils.currentFieldToString(in));
            }
        }
        in.end(token);

        return (context, resources, rootData, depth) -> {
            for (LongSparseArray<Object> entry : entries) {
                int id = (int) entry.get(RemoteViewsProto.RemoteCollectionCache.Entry.ID);
                String uri = (String) entry.get(RemoteViewsProto.RemoteCollectionCache.Entry.URI);
                // Depth resets to 0 for RemoteCollectionItems
                RemoteCollectionItems items = ((PendingResources<RemoteCollectionItems>) entry.get(
                        RemoteViewsProto.RemoteCollectionCache.Entry.ITEMS)).create(context,
                        resources, rootData, depth);
                rootData.mRemoteCollectionCache.addMapping(id, uri, items);
            }
            // Redundant return, but type signature requires we return something.
            return rootData.mRemoteCollectionCache;
        };
    }

    private class SetRemoteViewsAdapterIntent extends Action {
        Intent mIntent;
        boolean mIsAsync = false;

        public SetRemoteViewsAdapterIntent(@IdRes int id, Intent intent) {
            this.mViewId = id;
            this.mIntent = intent;
        }

        public SetRemoteViewsAdapterIntent(Parcel parcel) {
            mViewId = parcel.readInt();
            mIntent = parcel.readTypedObject(Intent.CREATOR);
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            dest.writeTypedObject(mIntent, flags);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            final View target = root.findViewById(mViewId);
            if (target == null) return;

            // Ensure that we are applying to an AppWidget root
            if (!(rootParent instanceof AppWidgetHostView)) {
                Log.e(LOG_TAG, "setRemoteAdapter can only be used for "
                        + "AppWidgets (root id: " + mViewId + ")");
                return;
            }

            // Ensure that we are calling setRemoteAdapter on an AdapterView that supports it
            if (!(target instanceof AbsListView) && !(target instanceof AdapterViewAnimator)) {
                Log.e(LOG_TAG, "Cannot setRemoteAdapter on a view which is not "
                        + "an AbsListView or AdapterViewAnimator (id: " + mViewId + ")");
                return;
            }

            // Embed the AppWidget Id for use in RemoteViewsAdapter when connecting to the intent
            // RemoteViewsService
            AppWidgetHostView host = (AppWidgetHostView) rootParent;
            mIntent.putExtra(EXTRA_REMOTEADAPTER_APPWIDGET_ID, host.getAppWidgetId())
                    .putExtra(EXTRA_REMOTEADAPTER_ON_LIGHT_BACKGROUND,
                            hasFlags(FLAG_USE_LIGHT_BACKGROUND_LAYOUT));

            if (target instanceof AbsListView) {
                AbsListView v = (AbsListView) target;
                v.setRemoteViewsAdapter(mIntent, mIsAsync);
                v.setRemoteViewsInteractionHandler(params.handler);
                v.setOnScrollListener(new AbsListView.OnScrollListener() {
                    @Override
                    public void onScrollStateChanged(AbsListView view, int scrollState) {
                        if (scrollState != SCROLL_STATE_IDLE) {
                            params.handler.onScroll(view);
                        }
                    }

                    @Override
                    public void onScroll(AbsListView view, int firstVisibleItem,
                            int visibleItemCount, int totalItemCount) {
                    }
                });
            } else if (target instanceof AdapterViewAnimator) {
                AdapterViewAnimator v = (AdapterViewAnimator) target;
                v.setRemoteViewsAdapter(mIntent, mIsAsync);
                v.setRemoteViewsOnClickHandler(params.handler);
            }
        }

        @Override
        public Action initActionAsync(ViewTree root, ViewGroup rootParent,
                ActionApplyParams params) {
            SetRemoteViewsAdapterIntent copy = new SetRemoteViewsAdapterIntent(mViewId, mIntent);
            copy.mIsAsync = true;
            return copy;
        }

        @Override
        public int getActionTag() {
            return SET_REMOTE_VIEW_ADAPTER_INTENT_TAG;
        }
    }

    /**
     * Equivalent to calling
     * {@link android.view.View#setOnClickListener(android.view.View.OnClickListener)}
     * to launch the provided {@link PendingIntent}.
     */
    private class SetOnClickResponse extends Action {
        final RemoteResponse mResponse;

        SetOnClickResponse(@IdRes int id, RemoteResponse response) {
            this.mViewId = id;
            this.mResponse = response;
        }

        SetOnClickResponse(Parcel parcel) {
            mViewId = parcel.readInt();
            mResponse = new RemoteResponse();
            mResponse.readFromParcel(parcel);
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            mResponse.writeToParcel(dest, flags);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            if (hasDrawInstructions() && root instanceof RemoteComposePlayer) {
                return;
            }
            final View target = root.findViewById(mViewId);
            if (target == null) return;

            if (mResponse.mPendingIntent != null) {
                // If the view is an AdapterView, setting a PendingIntent on click doesn't make
                // much sense, do they mean to set a PendingIntent template for the
                // AdapterView's children?
                if (hasFlags(FLAG_WIDGET_IS_COLLECTION_CHILD)) {
                    Log.w(LOG_TAG, "Cannot SetOnClickResponse for collection item "
                            + "(id: " + mViewId + ")");
                    ApplicationInfo appInfo = root.getContext().getApplicationInfo();

                    // We let this slide for HC and ICS so as to not break compatibility. It should
                    // have been disabled from the outset, but was left open by accident.
                    if (appInfo != null
                            && appInfo.targetSdkVersion >= Build.VERSION_CODES.JELLY_BEAN) {
                        return;
                    }
                }
                target.setTagInternal(R.id.pending_intent_tag, mResponse.mPendingIntent);
            } else if (mResponse.mFillIntent != null) {
                if (!hasFlags(FLAG_WIDGET_IS_COLLECTION_CHILD)) {
                    Log.e(LOG_TAG, "The method setOnClickFillInIntent is available "
                            + "only from RemoteViewsFactory (ie. on collection items).");
                    return;
                }
                if (target == root) {
                    // Target is a root node of an AdapterView child. Set the response in the tag.
                    // Actual click handling is done by OnItemClickListener in
                    // SetPendingIntentTemplate, which uses this tag information.
                    target.setTagInternal(com.android.internal.R.id.fillInIntent, mResponse);
                    return;
                }
            } else {
                // No intent to apply, clear the listener and any tags that were previously set.
                target.setOnClickListener(null);
                target.setTagInternal(R.id.pending_intent_tag, null);
                target.setTagInternal(com.android.internal.R.id.fillInIntent, null);
                return;
            }
            target.setOnClickListener(v ->
                    mResponse.handleViewInteraction(v, params.handler));
        }

        @Override
        public int getActionTag() {
            return SET_ON_CLICK_RESPONSE_TAG;
        }
    }

    /** Helper action to configure handwriting delegation via {@link PendingIntent}. */
    private class SetOnStylusHandwritingResponse extends Action {
        final PendingIntent mPendingIntent;

        SetOnStylusHandwritingResponse(@IdRes int id, @Nullable PendingIntent pendingIntent) {
            this.mViewId = id;
            this.mPendingIntent = pendingIntent;
        }

        SetOnStylusHandwritingResponse(@NonNull Parcel parcel) {
            mViewId = parcel.readInt();
            mPendingIntent = PendingIntent.readPendingIntentOrNullFromParcel(parcel);
        }

        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(mViewId);
            PendingIntent.writePendingIntentOrNullToParcel(mPendingIntent, dest);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            final View target = root.findViewById(mViewId);
            if (target == null) return;

            if (hasFlags(FLAG_WIDGET_IS_COLLECTION_CHILD)) {
                Log.w(LOG_TAG, "Cannot use setOnStylusHandwritingPendingIntent for collection item "
                        + "(id: " + mViewId + ")");
                return;
            }

            if (mPendingIntent != null) {
                RemoteResponse response = RemoteResponse.fromPendingIntent(mPendingIntent);
                target.setHandwritingDelegatorCallback(
                        () -> response.handleViewInteraction(target, params.handler));
                target.setAllowedHandwritingDelegatePackage(mPendingIntent.getCreatorPackage());
            } else {
                target.setHandwritingDelegatorCallback(null);
                target.setAllowedHandwritingDelegatePackage(null);
            }
        }

        @Override
        public int getActionTag() {
            return SET_ON_STYLUS_HANDWRITING_RESPONSE_TAG;
        }
    }

    /**
     * Equivalent to calling
     * {@link android.widget.CompoundButton#setOnCheckedChangeListener(
     * android.widget.CompoundButton.OnCheckedChangeListener)}
     * to launch the provided {@link PendingIntent}.
     */
    private class SetOnCheckedChangeResponse extends Action {
        private final RemoteResponse mResponse;

        SetOnCheckedChangeResponse(@IdRes int id, RemoteResponse response) {
            this.mViewId = id;
            this.mResponse = response;
        }

        SetOnCheckedChangeResponse(Parcel parcel) {
            mViewId = parcel.readInt();
            mResponse = new RemoteResponse();
            mResponse.readFromParcel(parcel);
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            mResponse.writeToParcel(dest, flags);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            final View target = root.findViewById(mViewId);
            if (target == null) return;
            if (!(target instanceof CompoundButton)) {
                Log.w(LOG_TAG, "setOnCheckedChange methods cannot be used on "
                        + "non-CompoundButton child (id: " + mViewId + ")");
                return;
            }
            CompoundButton button = (CompoundButton) target;

            if (mResponse.mPendingIntent != null) {
                // setOnCheckedChangePendingIntent cannot be used with collection children, which
                // must use setOnCheckedChangeFillInIntent instead.
                if (hasFlags(FLAG_WIDGET_IS_COLLECTION_CHILD)) {
                    Log.w(LOG_TAG, "Cannot setOnCheckedChangePendingIntent for collection item "
                            + "(id: " + mViewId + ")");
                    return;
                }
                target.setTagInternal(R.id.pending_intent_tag, mResponse.mPendingIntent);
            } else if (mResponse.mFillIntent != null) {
                if (!hasFlags(FLAG_WIDGET_IS_COLLECTION_CHILD)) {
                    Log.e(LOG_TAG, "The method setOnCheckedChangeFillInIntent is available "
                            + "only from RemoteViewsFactory (ie. on collection items).");
                    return;
                }
            } else {
                // No intent to apply, clear any existing listener or tag.
                button.setOnCheckedChangeListener(null);
                button.setTagInternal(R.id.remote_checked_change_listener_tag, null);
                return;
            }

            OnCheckedChangeListener onCheckedChangeListener =
                    (v, isChecked) -> mResponse.handleViewInteraction(v, params.handler);
            button.setTagInternal(R.id.remote_checked_change_listener_tag, onCheckedChangeListener);
            button.setOnCheckedChangeListener(onCheckedChangeListener);
        }

        @Override
        public int getActionTag() {
            return SET_ON_CHECKED_CHANGE_RESPONSE_TAG;
        }
    }

    /** @hide **/
    public static Rect getSourceBounds(View v) {
        final float appScale = v.getContext().getResources()
                .getCompatibilityInfo().applicationScale;
        final int[] pos = new int[2];
        v.getLocationOnScreen(pos);

        final Rect rect = new Rect();
        rect.left = (int) (pos[0] * appScale + 0.5f);
        rect.top = (int) (pos[1] * appScale + 0.5f);
        rect.right = (int) ((pos[0] + v.getWidth()) * appScale + 0.5f);
        rect.bottom = (int) ((pos[1] + v.getHeight()) * appScale + 0.5f);
        return rect;
    }

    @Nullable
    private static Class<?> getParameterType(int type) {
        switch (type) {
            case BaseReflectionAction.BOOLEAN:
                return boolean.class;
            case BaseReflectionAction.BYTE:
                return byte.class;
            case BaseReflectionAction.SHORT:
                return short.class;
            case BaseReflectionAction.INT:
                return int.class;
            case BaseReflectionAction.LONG:
                return long.class;
            case BaseReflectionAction.FLOAT:
                return float.class;
            case BaseReflectionAction.DOUBLE:
                return double.class;
            case BaseReflectionAction.CHAR:
                return char.class;
            case BaseReflectionAction.STRING:
                return String.class;
            case BaseReflectionAction.CHAR_SEQUENCE:
                return CharSequence.class;
            case BaseReflectionAction.URI:
                return Uri.class;
            case BaseReflectionAction.BITMAP:
                return Bitmap.class;
            case BaseReflectionAction.BUNDLE:
                return Bundle.class;
            case BaseReflectionAction.INTENT:
                return Intent.class;
            case BaseReflectionAction.COLOR_STATE_LIST:
                return ColorStateList.class;
            case BaseReflectionAction.ICON:
                return Icon.class;
            case BaseReflectionAction.BLEND_MODE:
                return BlendMode.class;
            default:
                return null;
        }
    }

    @Nullable
    private static MethodHandle getMethod(View view, String methodName, Class<?> paramType,
            boolean async) {
        MethodArgs result;
        Class<? extends View> klass = view.getClass();

        synchronized (sMethods) {
            // The key is defined by the view class, param class and method name.
            sLookupKey.set(klass, paramType, methodName);
            result = sMethods.get(sLookupKey);

            if (result == null) {
                Method method;
                try {
                    if (paramType == null) {
                        method = klass.getMethod(methodName);
                    } else {
                        method = klass.getMethod(methodName, paramType);
                    }
                    if (!method.isAnnotationPresent(RemotableViewMethod.class)) {
                        throw new ActionException("view: " + klass.getName()
                                + " can't use method with RemoteViews: "
                                + methodName + getParameters(paramType));
                    }

                    result = new MethodArgs();
                    result.syncMethod = MethodHandles.publicLookup().unreflect(method);
                    result.asyncMethodName =
                            method.getAnnotation(RemotableViewMethod.class).asyncImpl();
                } catch (NoSuchMethodException | IllegalAccessException ex) {
                    throw new ActionException("view: " + klass.getName() + " doesn't have method: "
                            + methodName + getParameters(paramType));
                }

                MethodKey key = new MethodKey();
                key.set(klass, paramType, methodName);
                sMethods.put(key, result);
            }

            if (!async) {
                return result.syncMethod;
            }
            // Check this so see if async method is implemented or not.
            if (result.asyncMethodName.isEmpty()) {
                return null;
            }
            // Async method is lazily loaded. If it is not yet loaded, load now.
            if (result.asyncMethod == null) {
                MethodType asyncType = result.syncMethod.type()
                        .dropParameterTypes(0, 1).changeReturnType(Runnable.class);
                try {
                    result.asyncMethod = MethodHandles.publicLookup().findVirtual(
                            klass, result.asyncMethodName, asyncType);
                } catch (NoSuchMethodException | IllegalAccessException ex) {
                    throw new ActionException("Async implementation declared as "
                            + result.asyncMethodName + " but not defined for " + methodName
                            + ": public Runnable " + result.asyncMethodName + " ("
                            + TextUtils.join(",", asyncType.parameterArray()) + ")");
                }
            }
            return result.asyncMethod;
        }
    }

    private static String getParameters(Class<?> paramType) {
        if (paramType == null) return "()";
        return "(" + paramType + ")";
    }

    /**
     * Equivalent to calling
     * {@link Drawable#setColorFilter(int, android.graphics.PorterDuff.Mode)},
     * on the {@link Drawable} of a given view.
     * <p>
     * The operation will be performed on the {@link Drawable} returned by the
     * target {@link View#getBackground()} by default.  If targetBackground is false,
     * we assume the target is an {@link ImageView} and try applying the operations
     * to {@link ImageView#getDrawable()}.
     * <p>
     */
    private static class SetDrawableTint extends Action {
        boolean mTargetBackground;
        @ColorInt int mColorFilter;
        PorterDuff.Mode mFilterMode;

        SetDrawableTint(@IdRes int id, boolean targetBackground,
                @ColorInt int colorFilter, @NonNull PorterDuff.Mode mode) {
            this.mViewId = id;
            this.mTargetBackground = targetBackground;
            this.mColorFilter = colorFilter;
            this.mFilterMode = mode;
        }

        SetDrawableTint(Parcel parcel) {
            mViewId = parcel.readInt();
            mTargetBackground = parcel.readInt() != 0;
            mColorFilter = parcel.readInt();
            mFilterMode = PorterDuff.intToMode(parcel.readInt());
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            dest.writeInt(mTargetBackground ? 1 : 0);
            dest.writeInt(mColorFilter);
            dest.writeInt(PorterDuff.modeToInt(mFilterMode));
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            final View target = root.findViewById(mViewId);
            if (target == null) return;

            // Pick the correct drawable to modify for this view
            Drawable targetDrawable = null;
            if (mTargetBackground) {
                targetDrawable = target.getBackground();
            } else if (target instanceof ImageView) {
                ImageView imageView = (ImageView) target;
                targetDrawable = imageView.getDrawable();
            }

            if (targetDrawable != null) {
                targetDrawable.mutate().setColorFilter(mColorFilter, mFilterMode);
            }
        }

        @Override
        public int getActionTag() {
            return SET_DRAWABLE_TINT_TAG;
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            final long token = out.start(RemoteViewsProto.Action.SET_DRAWABLE_TINT_ACTION);
            out.write(RemoteViewsProto.SetDrawableTintAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            out.write(RemoteViewsProto.SetDrawableTintAction.COLOR_FILTER, mColorFilter);
            out.write(RemoteViewsProto.SetDrawableTintAction.FILTER_MODE,
                    PorterDuff.modeToInt(mFilterMode));
            out.write(RemoteViewsProto.SetDrawableTintAction.TARGET_BACKGROUND, mTargetBackground);
            out.end(token);
        }

        public static PendingResources<Action> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            final long token = in.start(RemoteViewsProto.Action.SET_DRAWABLE_TINT_ACTION);
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.SetDrawableTintAction.VIEW_ID:
                        values.put(RemoteViewsProto.SetDrawableTintAction.VIEW_ID,
                                in.readString(RemoteViewsProto.SetDrawableTintAction.VIEW_ID));
                        break;
                    case (int) RemoteViewsProto.SetDrawableTintAction.TARGET_BACKGROUND:
                        values.put(RemoteViewsProto.SetDrawableTintAction.TARGET_BACKGROUND,
                                in.readBoolean(
                                        RemoteViewsProto.SetDrawableTintAction.TARGET_BACKGROUND));
                        break;
                    case (int) RemoteViewsProto.SetDrawableTintAction.COLOR_FILTER:
                        values.put(RemoteViewsProto.SetDrawableTintAction.COLOR_FILTER,
                                in.readInt(RemoteViewsProto.SetDrawableTintAction.COLOR_FILTER));
                        break;
                    case (int) RemoteViewsProto.SetDrawableTintAction.FILTER_MODE:
                        values.put(RemoteViewsProto.SetDrawableTintAction.FILTER_MODE,
                                PorterDuff.intToMode(in.readInt(
                                        RemoteViewsProto.SetDrawableTintAction.FILTER_MODE)));
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
            in.end(token);

            checkContainsKeys(values, new long[]{RemoteViewsProto.SetDrawableTintAction.VIEW_ID});

            return (context, resources, rootData, depth) -> {
                int viewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.SetDrawableTintAction.VIEW_ID);
                return new SetDrawableTint(viewId, (boolean) values.get(
                        RemoteViewsProto.SetDrawableTintAction.TARGET_BACKGROUND, false),
                        (int) values.get(RemoteViewsProto.SetDrawableTintAction.COLOR_FILTER, 0),
                        (PorterDuff.Mode) values.get(
                                RemoteViewsProto.SetDrawableTintAction.FILTER_MODE));
            };
        }
    }

    /**
     * Equivalent to calling
     * {@link RippleDrawable#setColor(ColorStateList)},
     * on the {@link Drawable} of a given view.
     * <p>
     * The operation will be performed on the {@link Drawable} returned by the
     * target {@link View#getBackground()}.
     * <p>
     */
    private static class SetRippleDrawableColor extends Action {
        ColorStateList mColorStateList;

        SetRippleDrawableColor(@IdRes int id, ColorStateList colorStateList) {
            this.mViewId = id;
            this.mColorStateList = colorStateList;
        }

        SetRippleDrawableColor(Parcel parcel) {
            mViewId = parcel.readInt();
            mColorStateList = parcel.readParcelable(null, android.content.res.ColorStateList.class);
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            dest.writeParcelable(mColorStateList, 0);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            final View target = root.findViewById(mViewId);
            if (target == null) return;

            // Pick the correct drawable to modify for this view
            Drawable targetDrawable = target.getBackground();

            if (targetDrawable instanceof RippleDrawable) {
                ((RippleDrawable) targetDrawable.mutate()).setColor(mColorStateList);
            }
        }

        @Override
        public int getActionTag() {
            return SET_RIPPLE_DRAWABLE_COLOR_TAG;
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            final long token = out.start(RemoteViewsProto.Action.SET_RIPPLE_DRAWABLE_COLOR_ACTION);
            out.write(RemoteViewsProto.SetRippleDrawableColorAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            writeColorStateListToProto(out, mColorStateList,
                    RemoteViewsProto.SetRippleDrawableColorAction.COLOR_STATE_LIST);
            out.end(token);
        }

        public static PendingResources<Action> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            final long token = in.start(RemoteViewsProto.Action.SET_RIPPLE_DRAWABLE_COLOR_ACTION);
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.SetRippleDrawableColorAction.VIEW_ID:
                        values.put(RemoteViewsProto.SetRippleDrawableColorAction.VIEW_ID,
                                in.readString(
                                        RemoteViewsProto.SetRippleDrawableColorAction.VIEW_ID));
                        break;
                    case (int) RemoteViewsProto.SetRippleDrawableColorAction.COLOR_STATE_LIST:
                        values.put(RemoteViewsProto.SetRippleDrawableColorAction.COLOR_STATE_LIST,
                                createColorStateListFromProto(in,
                                        RemoteViewsProto
                                                .SetRippleDrawableColorAction.COLOR_STATE_LIST));
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
            in.end(token);

            checkContainsKeys(values,
                    new long[]{RemoteViewsProto.SetRippleDrawableColorAction.VIEW_ID,
                            RemoteViewsProto.SetRippleDrawableColorAction.COLOR_STATE_LIST});

            return (context, resources, rootData, depth) -> {
                int viewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.SetRippleDrawableColorAction.VIEW_ID);
                return new SetRippleDrawableColor(viewId, (ColorStateList) values.get(
                        RemoteViewsProto.SetRippleDrawableColorAction.COLOR_STATE_LIST));
            };
        }
    }

    /**
     * @deprecated As RemoteViews may be reapplied frequently, it is preferable to call
     * {@link #setDisplayedChild(int, int)} to ensure that the adapter index does not change
     * unexpectedly.
     */
    @Deprecated
    private final class ViewContentNavigation extends Action {
        final boolean mNext;

        ViewContentNavigation(@IdRes int viewId, boolean next) {
            this.mViewId = viewId;
            this.mNext = next;
        }

        ViewContentNavigation(Parcel in) {
            this.mViewId = in.readInt();
            this.mNext = in.readBoolean();
        }

        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(this.mViewId);
            out.writeBoolean(this.mNext);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            final View view = root.findViewById(mViewId);
            if (view == null) return;

            try {
                getMethod(view,
                        mNext ? "showNext" : "showPrevious", null, false /* async */).invoke(view);
            } catch (Throwable ex) {
                throw new ActionException(ex);
            }
        }

        public int mergeBehavior() {
            return MERGE_IGNORE;
        }

        @Override
        public int getActionTag() {
            return VIEW_CONTENT_NAVIGATION_TAG;
        }
    }

    /**
     * @hide
     */
    @NonNull BitmapCache getBitmapCache() {
        return mBitmapCache;
    }

    static class BitmapCache {
        @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
        ArrayList<Bitmap> mBitmaps;
        SparseIntArray mBitmapHashes;
        long mBitmapMemory = -1;

        public BitmapCache() {
            mBitmaps = new ArrayList<>();
            mBitmapHashes = new SparseIntArray();
        }

        public BitmapCache(Parcel source) {
            mBitmaps = source.createTypedArrayList(Bitmap.CREATOR);
            mBitmapHashes = new SparseIntArray();
            for (int i = 0; i < mBitmaps.size(); i++) {
                Bitmap b = mBitmaps.get(i);
                if (b != null) {
                    mBitmapHashes.put(b.hashCode(), i);
                }
            }
        }

        BitmapCache(BitmapCache other) {
            mBitmaps = new ArrayList<>(other.mBitmaps);
            mBitmapHashes = other.mBitmapHashes.clone();
        }

        public int getBitmapId(Bitmap b) {
            if (b == null) {
                return -1;
            } else {
                int hash = b.hashCode();
                int hashId = mBitmapHashes.get(hash, -1);
                if (hashId != -1) {
                    return hashId;
                } else {
                    if (b.isMutable()) {
                        b = b.asShared();
                    }
                    mBitmaps.add(b);
                    mBitmapHashes.put(hash, mBitmaps.size() - 1);
                    mBitmapMemory = -1;
                    return (mBitmaps.size() - 1);
                }
            }
        }

        @Nullable
        public Bitmap getBitmapForId(int id) {
            if (id == -1 || id >= mBitmaps.size()) {
                return null;
            }
            return mBitmaps.get(id);
        }

        public void writeBitmapsToParcel(Parcel dest, int flags) {
            dest.writeTypedList(mBitmaps, flags);
        }

        public void writeBitmapsToProto(ProtoOutputStream out) {
            for (int i = 0; i < mBitmaps.size(); i++) {
                final Bitmap bitmap = mBitmaps.get(i);
                final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, bytes);
                out.write(RemoteViewsProto.BITMAP_CACHE, bytes.toByteArray());
            }
        }

        public long getBitmapMemory() {
            if (mBitmapMemory < 0) {
                mBitmapMemory = 0;
                int count = mBitmaps.size();
                for (int i = 0; i < count; i++) {
                    mBitmapMemory += mBitmaps.get(i).getAllocationByteCount();
                }
            }
            return mBitmapMemory;
        }

        public void mergeWithCache(BitmapCache other) {
            for (int i = 0; i < other.mBitmaps.size(); i++) {
                getBitmapId(other.mBitmaps.get(i));
            }
        }
    }

    private class BitmapReflectionAction extends Action {
        int mBitmapId;
        @UnsupportedAppUsage
        Bitmap mBitmap;
        @UnsupportedAppUsage
        String mMethodName;

        BitmapReflectionAction(@IdRes int viewId, String methodName, Bitmap bitmap) {
            this.mBitmap = bitmap;
            this.mViewId = viewId;
            this.mMethodName = methodName;
            mBitmapId = mBitmapCache.getBitmapId(bitmap);
        }

        BitmapReflectionAction(Parcel in) {
            mViewId = in.readInt();
            mMethodName = in.readString8();
            mBitmapId = in.readInt();
            mBitmap = mBitmapCache.getBitmapForId(mBitmapId);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            dest.writeString8(mMethodName);
            dest.writeInt(mBitmapId);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params)
                throws ActionException {
            ReflectionAction ra = new ReflectionAction(mViewId, mMethodName,
                    BaseReflectionAction.BITMAP,
                    mBitmap);
            ra.apply(root, rootParent, params);
        }

        @Override
        public void setHierarchyRootData(HierarchyRootData rootData) {
            mBitmapId = rootData.mBitmapCache.getBitmapId(mBitmap);
        }

        @Override
        public int getActionTag() {
            return BITMAP_REFLECTION_ACTION_TAG;
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            final long token = out.start(RemoteViewsProto.Action.BITMAP_REFLECTION_ACTION);
            out.write(RemoteViewsProto.BitmapReflectionAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            out.write(RemoteViewsProto.BitmapReflectionAction.METHOD_NAME, mMethodName);
            out.write(RemoteViewsProto.BitmapReflectionAction.BITMAP_ID, mBitmapId);
            out.end(token);
        }
    }

    private PendingResources<Action> createFromBitmapReflectionActionFromProto(ProtoInputStream in)
            throws Exception {
        final LongSparseArray<Object> values = new LongSparseArray<>();

        final long token = in.start(RemoteViewsProto.Action.BITMAP_REFLECTION_ACTION);
        while (in.nextField() != NO_MORE_FIELDS) {
            switch (in.getFieldNumber()) {
                case (int) RemoteViewsProto.BitmapReflectionAction.VIEW_ID:
                    values.put(RemoteViewsProto.BitmapReflectionAction.VIEW_ID,
                            in.readString(RemoteViewsProto.BitmapReflectionAction.VIEW_ID));
                    break;
                case (int) RemoteViewsProto.BitmapReflectionAction.METHOD_NAME:
                    values.put(RemoteViewsProto.BitmapReflectionAction.METHOD_NAME,
                            in.readString(RemoteViewsProto.BitmapReflectionAction.METHOD_NAME));
                    break;
                case (int) RemoteViewsProto.BitmapReflectionAction.BITMAP_ID:
                    values.put(RemoteViewsProto.BitmapReflectionAction.BITMAP_ID,
                            in.readInt(RemoteViewsProto.BitmapReflectionAction.BITMAP_ID));
                    break;
                default:
                    Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                            + ProtoUtils.currentFieldToString(in));
            }
        }
        in.end(token);

        checkContainsKeys(values, new long[]{RemoteViewsProto.BitmapReflectionAction.VIEW_ID,
                RemoteViewsProto.BitmapReflectionAction.METHOD_NAME});

        return (context, resources, rootData, depth) -> {
            int viewId = getAsIdentifier(resources, values,
                    RemoteViewsProto.BitmapReflectionAction.VIEW_ID);
            return new BitmapReflectionAction(viewId,
                    (String) values.get(RemoteViewsProto.BitmapReflectionAction.METHOD_NAME),
                    rootData.mBitmapCache.getBitmapForId(
                            (int) values.get(RemoteViewsProto.BitmapReflectionAction.BITMAP_ID,
                                    0)));
        };

    }

    /**
     * Base class for the reflection actions.
     */
    private abstract static class BaseReflectionAction extends Action {
        static final int BOOLEAN = 1;
        static final int BYTE = 2;
        static final int SHORT = 3;
        static final int INT = 4;
        static final int LONG = 5;
        static final int FLOAT = 6;
        static final int DOUBLE = 7;
        static final int CHAR = 8;
        static final int STRING = 9;
        static final int CHAR_SEQUENCE = 10;
        static final int URI = 11;
        // BITMAP actions are never stored in the list of actions. They are only used locally
        // to implement BitmapReflectionAction, which eliminates duplicates using BitmapCache.
        static final int BITMAP = 12;
        static final int BUNDLE = 13;
        static final int INTENT = 14;
        static final int COLOR_STATE_LIST = 15;
        static final int ICON = 16;
        static final int BLEND_MODE = 17;

        @UnsupportedAppUsage
        String mMethodName;
        int mType;

        BaseReflectionAction(@IdRes int viewId, String methodName, int type) {
            this.mViewId = viewId;
            this.mMethodName = methodName;
            this.mType = type;
        }

        BaseReflectionAction(Parcel in) {
            this.mViewId = in.readInt();
            this.mMethodName = in.readString8();
            this.mType = in.readInt();
            //noinspection ConstantIfStatement
            if (false) {
                Log.d(LOG_TAG, "read viewId=0x" + Integer.toHexString(this.mViewId)
                        + " methodName=" + this.mMethodName + " type=" + this.mType);
            }
        }

        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(this.mViewId);
            out.writeString8(this.mMethodName);
            out.writeInt(this.mType);
        }

        /**
         * Returns the value to use as parameter for the method.
         *
         * The view might be passed as {@code null} if the parameter value is requested outside of
         * inflation. If the parameter cannot be determined at that time, the method should return
         * {@code null} but not raise any exception.
         */
        @Nullable
        protected abstract Object getParameterValue(@Nullable View view) throws ActionException;

        @Override
        public final void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            final View view = root.findViewById(mViewId);
            if (view == null) return;

            Class<?> param = getParameterType(this.mType);
            if (param == null) {
                throw new ActionException("bad type: " + this.mType);
            }
            Object value = getParameterValue(view);
            try {
                getMethod(view, this.mMethodName, param, false /* async */).invoke(view, value);
            } catch (Throwable ex) {
                throw new ActionException(ex);
            }
        }

        @Override
        public final Action initActionAsync(ViewTree root, ViewGroup rootParent,
                ActionApplyParams params) {
            final View view = root.findViewById(mViewId);
            if (view == null) return ACTION_NOOP;

            Class<?> param = getParameterType(this.mType);
            if (param == null) {
                throw new ActionException("bad type: " + this.mType);
            }

            Object value = getParameterValue(view);
            try {
                MethodHandle method = getMethod(view, this.mMethodName, param, true /* async */);
                // Upload the bitmap to GPU if the parameter is of type Bitmap or Icon.
                // Since bitmaps in framework are seldomly modified, this is supposed to accelerate
                // the operations.
                if (value instanceof Bitmap bitmap) {
                    bitmap.prepareToDraw();
                }

                if (value instanceof Icon icon
                        && (icon.getType() == Icon.TYPE_BITMAP
                                || icon.getType() == Icon.TYPE_ADAPTIVE_BITMAP)) {
                    Bitmap bitmap = icon.getBitmap();
                    if (bitmap != null) {
                        bitmap.prepareToDraw();
                    }
                }

                if (method != null) {
                    Runnable endAction = (Runnable) method.invoke(view, value);
                    if (endAction == null) {
                        return ACTION_NOOP;
                    }
                    // Special case view stub
                    if (endAction instanceof ViewStub.ViewReplaceRunnable) {
                        root.createTree();
                        // Replace child tree
                        root.findViewTreeById(mViewId).replaceView(
                                ((ViewStub.ViewReplaceRunnable) endAction).view);
                    }
                    return new RunnableAction(endAction);
                }
            } catch (Throwable ex) {
                throw new ActionException(ex);
            }

            return this;
        }

        public final int mergeBehavior() {
            // smoothScrollBy is cumulative, everything else overwites.
            if (mMethodName.equals("smoothScrollBy")) {
                return MERGE_APPEND;
            } else {
                return MERGE_REPLACE;
            }
        }

        @Override
        public final String getUniqueKey() {
            // Each type of reflection action corresponds to a setter, so each should be seen as
            // unique from the standpoint of merging.
            return super.getUniqueKey() + this.mMethodName + this.mType;
        }

        @Override
        public final boolean prefersAsyncApply() {
            return this.mType == URI || this.mType == ICON;
        }

        @Override
        public void visitUris(@NonNull Consumer<Uri> visitor) {
            switch (this.mType) {
                case URI:
                    final Uri uri = (Uri) getParameterValue(null);
                    if (uri != null) visitor.accept(uri);
                    break;
                case ICON:
                    final Icon icon = (Icon) getParameterValue(null);
                    if (icon != null) visitIconUri(icon, visitor);
                    break;
                // TODO(b/281044385): Should we do anything about type BUNDLE?
            }
        }

        @Override
        public void visitIcons(@NonNull Consumer<Icon> visitor) {
            if (mType == ICON && getParameterValue(null) instanceof Icon icon) {
                visitor.accept(icon);
            }
        }
    }

    /** Class for the reflection actions. */
    private static final class ReflectionAction extends BaseReflectionAction {
        @UnsupportedAppUsage
        Object mValue;

        ReflectionAction(@IdRes int viewId, String methodName, int type, Object value) {
            super(viewId, methodName, type);
            this.mValue = value;
        }

        ReflectionAction(Parcel in) {
            super(in);
            // For some values that may have been null, we first check a flag to see if they were
            // written to the parcel.
            switch (this.mType) {
                case BOOLEAN:
                    this.mValue = in.readBoolean();
                    break;
                case BYTE:
                    this.mValue = in.readByte();
                    break;
                case SHORT:
                    this.mValue = (short) in.readInt();
                    break;
                case INT:
                    this.mValue = in.readInt();
                    break;
                case LONG:
                    this.mValue = in.readLong();
                    break;
                case FLOAT:
                    this.mValue = in.readFloat();
                    break;
                case DOUBLE:
                    this.mValue = in.readDouble();
                    break;
                case CHAR:
                    this.mValue = (char) in.readInt();
                    break;
                case STRING:
                    this.mValue = in.readString8();
                    break;
                case CHAR_SEQUENCE:
                    this.mValue = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
                    break;
                case URI:
                    this.mValue = in.readTypedObject(Uri.CREATOR);
                    break;
                case BITMAP:
                    this.mValue = in.readTypedObject(Bitmap.CREATOR);
                    break;
                case BUNDLE:
                    // Because we use Parcel.allowSquashing() when writing, and that affects
                    //  how the contents of Bundles are written, we need to ensure the bundle is
                    //  unparceled immediately, not lazily.  Setting a custom ReadWriteHelper
                    //  just happens to have that effect on Bundle.readFromParcel().
                    // TODO(b/212731590): build this state tracking into Bundle
                    if (in.hasReadWriteHelper()) {
                        this.mValue = in.readBundle();
                    } else {
                        in.setReadWriteHelper(ALTERNATIVE_DEFAULT);
                        this.mValue = in.readBundle();
                        in.setReadWriteHelper(null);
                    }
                    break;
                case INTENT:
                    this.mValue = in.readTypedObject(Intent.CREATOR);
                    break;
                case COLOR_STATE_LIST:
                    this.mValue = in.readTypedObject(ColorStateList.CREATOR);
                    break;
                case ICON:
                    this.mValue = in.readTypedObject(Icon.CREATOR);
                    break;
                case BLEND_MODE:
                    this.mValue = BlendMode.fromValue(in.readInt());
                    break;
                default:
                    break;
            }
        }

        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            // For some values which are null, we record an integer flag to indicate whether
            // we have written a valid value to the parcel.
            switch (this.mType) {
                case BOOLEAN:
                    out.writeBoolean((Boolean) this.mValue);
                    break;
                case BYTE:
                    out.writeByte((Byte) this.mValue);
                    break;
                case SHORT:
                    out.writeInt((Short) this.mValue);
                    break;
                case INT:
                    out.writeInt((Integer) this.mValue);
                    break;
                case LONG:
                    out.writeLong((Long) this.mValue);
                    break;
                case FLOAT:
                    out.writeFloat((Float) this.mValue);
                    break;
                case DOUBLE:
                    out.writeDouble((Double) this.mValue);
                    break;
                case CHAR:
                    out.writeInt((int) ((Character) this.mValue).charValue());
                    break;
                case STRING:
                    out.writeString8((String) this.mValue);
                    break;
                case CHAR_SEQUENCE:
                    TextUtils.writeToParcel((CharSequence) this.mValue, out, flags);
                    break;
                case BUNDLE:
                    out.writeBundle((Bundle) this.mValue);
                    break;
                case BLEND_MODE:
                    out.writeInt(BlendMode.toValue((BlendMode) this.mValue));
                    break;
                case URI:
                case BITMAP:
                case INTENT:
                case COLOR_STATE_LIST:
                case ICON:
                    out.writeTypedObject((Parcelable) this.mValue, flags);
                    break;
                default:
                    break;
            }
        }

        @Nullable
        @Override
        protected Object getParameterValue(@Nullable View view) throws ActionException {
            return this.mValue;
        }

        @Override
        public int getActionTag() {
            return REFLECTION_ACTION_TAG;
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            final long token = out.start(RemoteViewsProto.Action.REFLECTION_ACTION);
            out.write(RemoteViewsProto.ReflectionAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            out.write(RemoteViewsProto.ReflectionAction.METHOD_NAME, mMethodName);
            out.write(RemoteViewsProto.ReflectionAction.PARAMETER_TYPE, mType);
            if (this.mValue != null) {
                switch (this.mType) {
                    case BOOLEAN:
                        // ProtoOutputStream will omit this write if the value is false
                        out.write(RemoteViewsProto.ReflectionAction.BOOLEAN_VALUE,
                                (boolean) this.mValue);
                        break;
                    case BYTE:
                        out.write(RemoteViewsProto.ReflectionAction.BYTE_VALUE,
                                new byte[]{(byte) this.mValue});
                        break;
                    case SHORT:
                        out.write(RemoteViewsProto.ReflectionAction.SHORT_VALUE,
                                (short) this.mValue);
                        break;
                    case INT:
                        out.write(RemoteViewsProto.ReflectionAction.INT_VALUE, (int) this.mValue);
                        break;
                    case LONG:
                        out.write(RemoteViewsProto.ReflectionAction.LONG_VALUE, (long) this.mValue);
                        break;
                    case FLOAT:
                        out.write(RemoteViewsProto.ReflectionAction.FLOAT_VALUE,
                                (float) this.mValue);
                        break;
                    case DOUBLE:
                        out.write(RemoteViewsProto.ReflectionAction.DOUBLE_VALUE,
                                (double) this.mValue);
                        break;
                    case CHAR:
                        out.write(RemoteViewsProto.ReflectionAction.CHAR_VALUE,
                                (Character) this.mValue);
                        break;
                    case STRING:
                        out.write(RemoteViewsProto.ReflectionAction.STRING_VALUE,
                                (String) this.mValue);
                        break;
                    case CHAR_SEQUENCE:
                        long csToken = out.start(
                                RemoteViewsProto.ReflectionAction.CHAR_SEQUENCE_VALUE);
                        RemoteViewsSerializers.writeCharSequenceToProto(out,
                                (CharSequence) this.mValue);
                        out.end(csToken);
                        break;
                    case URI:
                        out.write(RemoteViewsProto.ReflectionAction.URI_VALUE,
                                ((Uri) this.mValue).toString());
                        break;
                    case BITMAP:
                        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        ((Bitmap) this.mValue).compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100,
                                bytes);
                        out.write(RemoteViewsProto.ReflectionAction.BITMAP_VALUE,
                                bytes.toByteArray());
                        break;
                    case BLEND_MODE:
                        out.write(RemoteViewsProto.ReflectionAction.BLEND_MODE_VALUE,
                                BlendMode.toValue((BlendMode) this.mValue));
                        break;
                    case COLOR_STATE_LIST:
                        writeColorStateListToProto(out, (ColorStateList) this.mValue,
                                RemoteViewsProto.ReflectionAction.COLOR_STATE_LIST_VALUE);
                        break;
                    case ICON:
                        writeIconToProto(out, appResources, (Icon) this.mValue,
                                RemoteViewsProto.ReflectionAction.ICON_VALUE);
                        break;
                    case BUNDLE:
                    case INTENT:
                    default:
                        break;
                }
            }
            out.end(token);
        }

        public static PendingResources<Action> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            final long token = in.start(RemoteViewsProto.Action.REFLECTION_ACTION);
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.ReflectionAction.VIEW_ID:
                        values.put(RemoteViewsProto.ReflectionAction.VIEW_ID,
                                in.readString(RemoteViewsProto.ReflectionAction.VIEW_ID));
                        break;
                    case (int) RemoteViewsProto.ReflectionAction.METHOD_NAME:
                        values.put(RemoteViewsProto.ReflectionAction.METHOD_NAME,
                                in.readString(RemoteViewsProto.ReflectionAction.METHOD_NAME));
                        break;
                    case (int) RemoteViewsProto.ReflectionAction.PARAMETER_TYPE:
                        values.put(RemoteViewsProto.ReflectionAction.PARAMETER_TYPE,
                                in.readInt(RemoteViewsProto.ReflectionAction.PARAMETER_TYPE));
                        break;
                    case (int) RemoteViewsProto.ReflectionAction.BOOLEAN_VALUE:
                        values.put(RemoteViewsProto.ReflectionAction.BOOLEAN_VALUE,
                                in.readBoolean(RemoteViewsProto.ReflectionAction.BOOLEAN_VALUE));
                        break;
                    case (int) RemoteViewsProto.ReflectionAction.BYTE_VALUE:
                        values.put(RemoteViewsProto.ReflectionAction.BYTE_VALUE,
                                in.readBytes(RemoteViewsProto.ReflectionAction.BYTE_VALUE));
                        break;
                    case (int) RemoteViewsProto.ReflectionAction.SHORT_VALUE:
                        values.put(RemoteViewsProto.ReflectionAction.SHORT_VALUE,
                                (short) in.readInt(RemoteViewsProto.ReflectionAction.SHORT_VALUE));
                        break;
                    case (int) RemoteViewsProto.ReflectionAction.INT_VALUE:
                        values.put(RemoteViewsProto.ReflectionAction.INT_VALUE,
                                in.readInt(RemoteViewsProto.ReflectionAction.INT_VALUE));
                        break;
                    case (int) RemoteViewsProto.ReflectionAction.LONG_VALUE:
                        values.put(RemoteViewsProto.ReflectionAction.LONG_VALUE,
                                in.readLong(RemoteViewsProto.ReflectionAction.LONG_VALUE));
                        break;
                    case (int) RemoteViewsProto.ReflectionAction.FLOAT_VALUE:
                        values.put(RemoteViewsProto.ReflectionAction.FLOAT_VALUE,
                                in.readFloat(RemoteViewsProto.ReflectionAction.FLOAT_VALUE));
                        break;
                    case (int) RemoteViewsProto.ReflectionAction.DOUBLE_VALUE:
                        values.put(RemoteViewsProto.ReflectionAction.DOUBLE_VALUE,
                                in.readDouble(RemoteViewsProto.ReflectionAction.DOUBLE_VALUE));
                        break;
                    case (int) RemoteViewsProto.ReflectionAction.CHAR_VALUE:
                        values.put(RemoteViewsProto.ReflectionAction.CHAR_VALUE,
                                (char) in.readInt(RemoteViewsProto.ReflectionAction.CHAR_VALUE));
                        break;
                    case (int) RemoteViewsProto.ReflectionAction.STRING_VALUE:
                        values.put(RemoteViewsProto.ReflectionAction.STRING_VALUE,
                                in.readString(RemoteViewsProto.ReflectionAction.STRING_VALUE));
                        break;
                    case (int) RemoteViewsProto.ReflectionAction.CHAR_SEQUENCE_VALUE:
                        values.put(RemoteViewsProto.ReflectionAction.CHAR_SEQUENCE_VALUE,
                                createCharSequenceFromProto(in,
                                        RemoteViewsProto.ReflectionAction.CHAR_SEQUENCE_VALUE));
                        break;
                    case (int) RemoteViewsProto.ReflectionAction.URI_VALUE:
                        values.put(RemoteViewsProto.ReflectionAction.URI_VALUE,
                                in.readString(RemoteViewsProto.ReflectionAction.URI_VALUE));
                        break;
                    case (int) RemoteViewsProto.ReflectionAction.BITMAP_VALUE:
                        byte[] bitmapData = in.readBytes(
                                RemoteViewsProto.ReflectionAction.BITMAP_VALUE);
                        values.put(RemoteViewsProto.ReflectionAction.BITMAP_VALUE,
                                BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.length));
                        break;
                    case (int) RemoteViewsProto.ReflectionAction.COLOR_STATE_LIST_VALUE:
                        values.put(RemoteViewsProto.ReflectionAction.COLOR_STATE_LIST_VALUE,
                                createColorStateListFromProto(in,
                                        RemoteViewsProto.ReflectionAction.COLOR_STATE_LIST_VALUE));
                        break;
                    case (int) RemoteViewsProto.ReflectionAction.ICON_VALUE:
                        values.put(RemoteViewsProto.ReflectionAction.ICON_VALUE,
                                createIconFromProto(in,
                                        RemoteViewsProto.ReflectionAction.ICON_VALUE));
                        break;
                    case (int) RemoteViewsProto.ReflectionAction.BLEND_MODE_VALUE:
                        values.put(RemoteViewsProto.ReflectionAction.BLEND_MODE_VALUE,
                                BlendMode.fromValue(in.readInt(
                                        RemoteViewsProto.ReflectionAction.BLEND_MODE_VALUE)));
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
            in.end(token);

            checkContainsKeys(values, new long[]{RemoteViewsProto.ReflectionAction.VIEW_ID,
                    RemoteViewsProto.ReflectionAction.METHOD_NAME,
                    RemoteViewsProto.ReflectionAction.PARAMETER_TYPE});

            return (context, resources, rootData, depth) -> {
                int viewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.ReflectionAction.VIEW_ID);
                Object value = null;
                int parameterType = (int) values.get(
                        RemoteViewsProto.ReflectionAction.PARAMETER_TYPE);
                switch (parameterType) {
                    case BOOLEAN:
                        value = (boolean) values.get(
                                RemoteViewsProto.ReflectionAction.BOOLEAN_VALUE, false);
                        break;
                    case BYTE:
                        byte[] bytes = (byte[]) values.get(
                                RemoteViewsProto.ReflectionAction.BYTE_VALUE);
                        if (bytes != null && bytes.length > 0) {
                            value = bytes[0];
                        }
                        break;
                    case SHORT:
                        value = (short) values.get(RemoteViewsProto.ReflectionAction.SHORT_VALUE,
                                0);
                        break;
                    case INT:
                        value = (int) values.get(RemoteViewsProto.ReflectionAction.INT_VALUE, 0);
                        break;
                    case LONG:
                        value = (long) values.get(RemoteViewsProto.ReflectionAction.LONG_VALUE, 0);
                        break;
                    case FLOAT:
                        value = (float) values.get(RemoteViewsProto.ReflectionAction.FLOAT_VALUE,
                                0);
                        break;
                    case DOUBLE:
                        value = (double) values.get(RemoteViewsProto.ReflectionAction.DOUBLE_VALUE,
                                0);
                        break;
                    case CHAR:
                        value = (char) values.get(RemoteViewsProto.ReflectionAction.CHAR_VALUE, 0);
                        break;
                    case STRING:
                        value = (String) values.get(RemoteViewsProto.ReflectionAction.STRING_VALUE);
                        break;
                    case CHAR_SEQUENCE:
                        value = (CharSequence) values.get(
                                RemoteViewsProto.ReflectionAction.CHAR_SEQUENCE_VALUE);
                        break;
                    case URI:
                        value = Uri.parse(
                                (String) values.get(RemoteViewsProto.ReflectionAction.URI_VALUE));
                        break;
                    case BITMAP:
                        value = (Bitmap) values.get(RemoteViewsProto.ReflectionAction.BITMAP_VALUE);
                        break;
                    case BLEND_MODE:
                        value = (BlendMode) values.get(
                                RemoteViewsProto.ReflectionAction.BLEND_MODE_VALUE);
                        break;
                    case COLOR_STATE_LIST:
                        value = (ColorStateList) values.get(
                                RemoteViewsProto.ReflectionAction.COLOR_STATE_LIST_VALUE);
                        break;
                    case ICON:
                        value = ((PendingResources<Icon>) values.get(
                                RemoteViewsProto.ReflectionAction.ICON_VALUE)).create(context,
                                resources, rootData, depth);
                        break;
                    case BUNDLE:
                    case INTENT:
                    default:
                        // omit the action for unsupported parameter types
                        return null;
                }
                return new ReflectionAction(viewId,
                        (String) values.get(RemoteViewsProto.ReflectionAction.METHOD_NAME),
                        parameterType, value);
            };
        }
    }

    private static final class ResourceReflectionAction extends BaseReflectionAction {
        static final int DIMEN_RESOURCE = 1;
        static final int COLOR_RESOURCE = 2;
        static final int STRING_RESOURCE = 3;

        private final int mResourceType;
        private final int mResId;

        ResourceReflectionAction(@IdRes int viewId, String methodName, int parameterType,
                int resourceType, int resId) {
            super(viewId, methodName, parameterType);
            this.mResourceType = resourceType;
            this.mResId = resId;
        }

        ResourceReflectionAction(Parcel in) {
            super(in);
            this.mResourceType = in.readInt();
            this.mResId = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(this.mResourceType);
            dest.writeInt(this.mResId);
        }

        @Nullable
        @Override
        protected Object getParameterValue(@Nullable View view) throws ActionException {
            if (view == null) return null;

            Resources resources = view.getContext().getResources();
            try {
                switch (this.mResourceType) {
                    case DIMEN_RESOURCE:
                        switch (this.mType) {
                            case BaseReflectionAction.INT:
                                return mResId == 0 ? 0 : resources.getDimensionPixelSize(mResId);
                            case BaseReflectionAction.FLOAT:
                                return mResId == 0 ? 0f : resources.getDimension(mResId);
                            default:
                                throw new ActionException(
                                        "dimen resources must be used as INT or FLOAT, "
                                                + "not " + this.mType);
                        }
                    case COLOR_RESOURCE:
                        switch (this.mType) {
                            case BaseReflectionAction.INT:
                                return mResId == 0 ? 0 : view.getContext().getColor(mResId);
                            case BaseReflectionAction.COLOR_STATE_LIST:
                                return mResId == 0
                                        ? null : view.getContext().getColorStateList(mResId);
                            default:
                                throw new ActionException(
                                        "color resources must be used as INT or COLOR_STATE_LIST,"
                                                + " not " + this.mType);
                        }
                    case STRING_RESOURCE:
                        switch (this.mType) {
                            case BaseReflectionAction.CHAR_SEQUENCE:
                                return mResId == 0 ? null : resources.getText(mResId);
                            case BaseReflectionAction.STRING:
                                return mResId == 0 ? null : resources.getString(mResId);
                            default:
                                throw new ActionException(
                                        "string resources must be used as STRING or CHAR_SEQUENCE,"
                                                + " not " + this.mType);
                        }
                    default:
                        throw new ActionException("unknown resource type: " + this.mResourceType);
                }
            } catch (ActionException ex) {
                throw ex;
            } catch (Throwable t) {
                throw new ActionException(t);
            }
        }

        @Override
        public int getActionTag() {
            return RESOURCE_REFLECTION_ACTION_TAG;
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            final long token = out.start(RemoteViewsProto.Action.RESOURCE_REFLECTION_ACTION);
            out.write(RemoteViewsProto.ResourceReflectionAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            out.write(RemoteViewsProto.ResourceReflectionAction.METHOD_NAME, mMethodName);
            out.write(RemoteViewsProto.ResourceReflectionAction.PARAMETER_TYPE, mType);
            out.write(RemoteViewsProto.ResourceReflectionAction.RESOURCE_TYPE, mResourceType);
            if (mResId != 0) {
                out.write(RemoteViewsProto.ResourceReflectionAction.RES_ID,
                        appResources.getResourceName(mResId));
            }
            out.end(token);
        }

        public static PendingResources<Action> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            final long token = in.start(RemoteViewsProto.Action.RESOURCE_REFLECTION_ACTION);
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.ResourceReflectionAction.VIEW_ID:
                        values.put(RemoteViewsProto.ResourceReflectionAction.VIEW_ID,
                                in.readString(RemoteViewsProto.ResourceReflectionAction.VIEW_ID));
                        break;
                    case (int) RemoteViewsProto.ResourceReflectionAction.METHOD_NAME:
                        values.put(RemoteViewsProto.ResourceReflectionAction.METHOD_NAME,
                                in.readString(
                                        RemoteViewsProto.ResourceReflectionAction.METHOD_NAME));
                        break;
                    case (int) RemoteViewsProto.ResourceReflectionAction.RESOURCE_TYPE:
                        values.put(RemoteViewsProto.ResourceReflectionAction.RESOURCE_TYPE,
                                in.readInt(
                                        RemoteViewsProto.ResourceReflectionAction.RESOURCE_TYPE));
                        break;
                    case (int) RemoteViewsProto.ResourceReflectionAction.RES_ID:
                        values.put(RemoteViewsProto.ResourceReflectionAction.RES_ID,
                                in.readString(RemoteViewsProto.ResourceReflectionAction.RES_ID));
                        break;
                    case (int) RemoteViewsProto.ResourceReflectionAction.PARAMETER_TYPE:
                        values.put(RemoteViewsProto.ResourceReflectionAction.PARAMETER_TYPE,
                                in.readInt(
                                        RemoteViewsProto.ResourceReflectionAction.PARAMETER_TYPE));
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
            in.end(token);

            checkContainsKeys(values, new long[]{RemoteViewsProto.ResourceReflectionAction.VIEW_ID,
                    RemoteViewsProto.ResourceReflectionAction.METHOD_NAME,
                    RemoteViewsProto.ResourceReflectionAction.PARAMETER_TYPE});

            return (context, resources, rootData, depth) -> {
                int viewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.ResourceReflectionAction.VIEW_ID);

                int resId = (values.indexOfKey(RemoteViewsProto.ResourceReflectionAction.RES_ID)
                        >= 0) ? getAsIdentifier(resources, values,
                        RemoteViewsProto.ResourceReflectionAction.RES_ID) : 0;
                return new ResourceReflectionAction(viewId,
                        (String) values.get(RemoteViewsProto.ResourceReflectionAction.METHOD_NAME),
                        (int) values.get(RemoteViewsProto.ResourceReflectionAction.PARAMETER_TYPE),
                        (int) values.get(RemoteViewsProto.ResourceReflectionAction.RESOURCE_TYPE,
                                0), resId);
            };
        }
    }

    private static final class AttributeReflectionAction extends BaseReflectionAction {
        static final int DIMEN_RESOURCE = 1;
        static final int COLOR_RESOURCE = 2;
        static final int STRING_RESOURCE = 3;

        private final int mResourceType;
        private final int mAttrId;

        AttributeReflectionAction(@IdRes int viewId, String methodName, int parameterType,
                int resourceType, int attrId) {
            super(viewId, methodName, parameterType);
            this.mResourceType = resourceType;
            this.mAttrId = attrId;
        }

        AttributeReflectionAction(Parcel in) {
            super(in);
            this.mResourceType = in.readInt();
            this.mAttrId = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(this.mResourceType);
            dest.writeInt(this.mAttrId);
        }

        @Override
        protected Object getParameterValue(View view) throws ActionException {
            TypedArray typedArray = view.getContext().obtainStyledAttributes(new int[]{mAttrId});
            try {
                // When mAttrId == 0, we will depend on the default values below
                if (mAttrId != 0 && typedArray.getType(0) == TypedValue.TYPE_NULL) {
                    throw new ActionException("Attribute 0x" + Integer.toHexString(this.mAttrId)
                            + " is not defined");
                }
                switch (this.mResourceType) {
                    case DIMEN_RESOURCE:
                        switch (this.mType) {
                            case BaseReflectionAction.INT:
                                return typedArray.getDimensionPixelSize(0, 0);
                            case BaseReflectionAction.FLOAT:
                                return typedArray.getDimension(0, 0);
                            default:
                                throw new ActionException(
                                        "dimen attribute 0x" + Integer.toHexString(this.mAttrId)
                                                + " must be used as INT or FLOAT,"
                                                + " not " + this.mType);
                        }
                    case COLOR_RESOURCE:
                        switch (this.mType) {
                            case BaseReflectionAction.INT:
                                return typedArray.getColor(0, 0);
                            case BaseReflectionAction.COLOR_STATE_LIST:
                                return typedArray.getColorStateList(0);
                            default:
                                throw new ActionException(
                                        "color attribute 0x" + Integer.toHexString(this.mAttrId)
                                                + " must be used as INT or COLOR_STATE_LIST,"
                                                + " not " + this.mType);
                        }
                    case STRING_RESOURCE:
                        switch (this.mType) {
                            case BaseReflectionAction.CHAR_SEQUENCE:
                                return typedArray.getText(0);
                            case BaseReflectionAction.STRING:
                                return typedArray.getString(0);
                            default:
                                throw new ActionException(
                                        "string attribute 0x" + Integer.toHexString(this.mAttrId)
                                                + " must be used as STRING or CHAR_SEQUENCE,"
                                                + " not " + this.mType);
                        }
                    default:
                        // Note: This can only be an implementation error.
                        throw new ActionException(
                                "Unknown resource type: " + this.mResourceType);
                }
            } catch (ActionException ex) {
                throw ex;
            } catch (Throwable t) {
                throw new ActionException(t);
            } finally {
                typedArray.recycle();
            }
        }

        @Override
        public int getActionTag() {
            return ATTRIBUTE_REFLECTION_ACTION_TAG;
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            final long token = out.start(RemoteViewsProto.Action.ATTRIBUTE_REFLECTION_ACTION);
            out.write(RemoteViewsProto.AttributeReflectionAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            out.write(RemoteViewsProto.AttributeReflectionAction.METHOD_NAME, mMethodName);
            out.write(RemoteViewsProto.AttributeReflectionAction.PARAMETER_TYPE, mType);
            out.write(RemoteViewsProto.AttributeReflectionAction.RESOURCE_TYPE, mResourceType);
            if (mAttrId != 0) {
                out.write(RemoteViewsProto.AttributeReflectionAction.ATTRIBUTE_ID,
                        appResources.getResourceName(mAttrId));
            }
            out.end(token);
        }

        public static PendingResources<Action> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            final long token = in.start(RemoteViewsProto.Action.ATTRIBUTE_REFLECTION_ACTION);
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.AttributeReflectionAction.VIEW_ID: {
                        values.put(RemoteViewsProto.AttributeReflectionAction.VIEW_ID,
                                in.readString(RemoteViewsProto.AttributeReflectionAction.VIEW_ID));
                        break;
                    }
                    case (int) RemoteViewsProto.AttributeReflectionAction.METHOD_NAME:
                        values.put(RemoteViewsProto.AttributeReflectionAction.METHOD_NAME,
                                in.readString(
                                        RemoteViewsProto.AttributeReflectionAction.METHOD_NAME));
                        break;
                    case (int) RemoteViewsProto.AttributeReflectionAction.ATTRIBUTE_ID:
                        values.put(RemoteViewsProto.AttributeReflectionAction.ATTRIBUTE_ID,
                                in.readString(
                                        RemoteViewsProto.AttributeReflectionAction.ATTRIBUTE_ID));
                        break;
                    case (int) RemoteViewsProto.AttributeReflectionAction.PARAMETER_TYPE:
                        values.put(RemoteViewsProto.AttributeReflectionAction.PARAMETER_TYPE,
                                in.readInt(
                                        RemoteViewsProto.AttributeReflectionAction.PARAMETER_TYPE));
                        break;
                    case (int) RemoteViewsProto.AttributeReflectionAction.RESOURCE_TYPE:
                        values.put(RemoteViewsProto.AttributeReflectionAction.RESOURCE_TYPE,
                                in.readInt(
                                        RemoteViewsProto.AttributeReflectionAction.RESOURCE_TYPE));
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
            in.end(token);

            checkContainsKeys(values, new long[]{RemoteViewsProto.AttributeReflectionAction.VIEW_ID,
                    RemoteViewsProto.AttributeReflectionAction.METHOD_NAME,
                    RemoteViewsProto.AttributeReflectionAction.PARAMETER_TYPE,
                    RemoteViewsProto.AttributeReflectionAction.RESOURCE_TYPE});

            return (context, resources, rootData, depth) -> {
                int viewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.AttributeReflectionAction.VIEW_ID);
                int attributeId = (values.indexOfKey(
                        RemoteViewsProto.AttributeReflectionAction.ATTRIBUTE_ID) >= 0)
                        ? getAsIdentifier(resources, values,
                        RemoteViewsProto.AttributeReflectionAction.ATTRIBUTE_ID) : 0;
                return new AttributeReflectionAction(viewId,
                        (String) values.get(RemoteViewsProto.AttributeReflectionAction.METHOD_NAME),
                        (int) values.get(RemoteViewsProto.AttributeReflectionAction.PARAMETER_TYPE),
                        (int) values.get(RemoteViewsProto.AttributeReflectionAction.RESOURCE_TYPE),
                        attributeId);
            };
        }
    }

    private static final class ComplexUnitDimensionReflectionAction extends BaseReflectionAction {
        private final float mValue;
        @ComplexDimensionUnit
        private final int mUnit;

        ComplexUnitDimensionReflectionAction(int viewId, String methodName, int parameterType,
                float value, @ComplexDimensionUnit int unit) {
            super(viewId, methodName, parameterType);
            this.mValue = value;
            this.mUnit = unit;
        }

        ComplexUnitDimensionReflectionAction(Parcel in) {
            super(in);
            this.mValue = in.readFloat();
            this.mUnit = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeFloat(this.mValue);
            dest.writeInt(this.mUnit);
        }

        @Nullable
        @Override
        protected Object getParameterValue(@Nullable View view) throws ActionException {
            if (view == null) return null;

            DisplayMetrics dm = view.getContext().getResources().getDisplayMetrics();
            try {
                int data = TypedValue.createComplexDimension(this.mValue, this.mUnit);
                switch (this.mType) {
                    case ReflectionAction.INT:
                        return TypedValue.complexToDimensionPixelSize(data, dm);
                    case ReflectionAction.FLOAT:
                        return TypedValue.complexToDimension(data, dm);
                    default:
                        throw new ActionException(
                                "parameter type must be INT or FLOAT, not " + this.mType);
                }
            } catch (ActionException ex) {
                throw ex;
            } catch (Throwable t) {
                throw new ActionException(t);
            }
        }

        @Override
        public int getActionTag() {
            return COMPLEX_UNIT_DIMENSION_REFLECTION_ACTION_TAG;
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            final long token = out.start(
                    RemoteViewsProto.Action.COMPLEX_UNIT_DIMENSION_REFLECTION_ACTION);
            out.write(RemoteViewsProto.ComplexUnitDimensionReflectionAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            out.write(RemoteViewsProto.ComplexUnitDimensionReflectionAction.METHOD_NAME,
                    mMethodName);
            out.write(RemoteViewsProto.ComplexUnitDimensionReflectionAction.PARAMETER_TYPE, mType);
            out.write(RemoteViewsProto.ComplexUnitDimensionReflectionAction.DIMENSION_VALUE,
                    mValue);
            out.write(RemoteViewsProto.ComplexUnitDimensionReflectionAction.UNIT, mUnit);
            out.end(token);
        }

        public static PendingResources<Action> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            final long token = in.start(
                    RemoteViewsProto.Action.COMPLEX_UNIT_DIMENSION_REFLECTION_ACTION);
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.ComplexUnitDimensionReflectionAction.VIEW_ID:
                        values.put(RemoteViewsProto.ComplexUnitDimensionReflectionAction.VIEW_ID,
                                in.readString(
                                        RemoteViewsProto
                                                .ComplexUnitDimensionReflectionAction.VIEW_ID));
                        break;
                    case (int) RemoteViewsProto.ComplexUnitDimensionReflectionAction.METHOD_NAME:
                        values.put(
                                RemoteViewsProto.ComplexUnitDimensionReflectionAction.METHOD_NAME,
                                in.readString(
                                        RemoteViewsProto
                                                .ComplexUnitDimensionReflectionAction.METHOD_NAME));
                        break;
                    case (int) RemoteViewsProto.ComplexUnitDimensionReflectionAction.PARAMETER_TYPE:
                        values.put(
                                RemoteViewsProto
                                        .ComplexUnitDimensionReflectionAction.PARAMETER_TYPE,
                                in.readInt(
                                        RemoteViewsProto
                                                .ComplexUnitDimensionReflectionAction
                                                .PARAMETER_TYPE));
                        break;
                    case (int) RemoteViewsProto
                            .ComplexUnitDimensionReflectionAction.DIMENSION_VALUE:
                        values.put(
                                RemoteViewsProto
                                        .ComplexUnitDimensionReflectionAction.DIMENSION_VALUE,
                                in.readFloat(
                                        RemoteViewsProto
                                                .ComplexUnitDimensionReflectionAction
                                                .DIMENSION_VALUE));
                        break;
                    case (int) RemoteViewsProto.ComplexUnitDimensionReflectionAction.UNIT:
                        values.put(RemoteViewsProto.ComplexUnitDimensionReflectionAction.UNIT,
                                in.readInt(
                                        RemoteViewsProto
                                                .ComplexUnitDimensionReflectionAction.UNIT));
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
            in.end(token);

            checkContainsKeys(values,
                    new long[]{RemoteViewsProto.ComplexUnitDimensionReflectionAction.VIEW_ID,
                            RemoteViewsProto.ComplexUnitDimensionReflectionAction.METHOD_NAME,
                            RemoteViewsProto.ComplexUnitDimensionReflectionAction.PARAMETER_TYPE});

            return (context, resources, rootData, depth) -> {
                int viewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.ComplexUnitDimensionReflectionAction.VIEW_ID);
                return new ComplexUnitDimensionReflectionAction(viewId, (String) values.get(
                        RemoteViewsProto.ComplexUnitDimensionReflectionAction.METHOD_NAME),
                        (int) values.get(
                                RemoteViewsProto
                                        .ComplexUnitDimensionReflectionAction.PARAMETER_TYPE),
                        (float) values.get(
                                RemoteViewsProto
                                        .ComplexUnitDimensionReflectionAction.DIMENSION_VALUE,
                                0),
                        (int) values.get(RemoteViewsProto.ComplexUnitDimensionReflectionAction.UNIT,
                                0));
            };
        }
    }

    private static final class NightModeReflectionAction extends BaseReflectionAction {
        private final Object mLightValue;
        private final Object mDarkValue;

        NightModeReflectionAction(
                @IdRes int viewId,
                String methodName,
                int type,
                Object lightValue,
                Object darkValue) {
            super(viewId, methodName, type);
            mLightValue = lightValue;
            mDarkValue = darkValue;
        }

        NightModeReflectionAction(Parcel in) {
            super(in);
            switch (this.mType) {
                case ICON:
                    mLightValue = in.readTypedObject(Icon.CREATOR);
                    mDarkValue = in.readTypedObject(Icon.CREATOR);
                    break;
                case COLOR_STATE_LIST:
                    mLightValue = in.readTypedObject(ColorStateList.CREATOR);
                    mDarkValue = in.readTypedObject(ColorStateList.CREATOR);
                    break;
                case INT:
                    mLightValue = in.readInt();
                    mDarkValue = in.readInt();
                    break;
                default:
                    throw new ActionException("Unexpected night mode action type: " + this.mType);
            }
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            switch (this.mType) {
                case ICON:
                case COLOR_STATE_LIST:
                    out.writeTypedObject((Parcelable) mLightValue, flags);
                    out.writeTypedObject((Parcelable) mDarkValue, flags);
                    break;
                case INT:
                    out.writeInt((int) mLightValue);
                    out.writeInt((int) mDarkValue);
                    break;
            }
        }

        @Nullable
        @Override
        protected Object getParameterValue(@Nullable View view) throws ActionException {
            if (view == null) return null;

            Configuration configuration = view.getResources().getConfiguration();
            return configuration.isNightModeActive() ? mDarkValue : mLightValue;
        }

        @Override
        public int getActionTag() {
            return NIGHT_MODE_REFLECTION_ACTION_TAG;
        }

        @Override
        public void visitUris(@NonNull Consumer<Uri> visitor) {
            if (this.mType == ICON) {
                visitIconUri((Icon) mDarkValue, visitor);
                visitIconUri((Icon) mLightValue, visitor);
            }
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            final long token = out.start(RemoteViewsProto.Action.NIGHT_MODE_REFLECTION_ACTION);
            out.write(RemoteViewsProto.NightModeReflectionAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            out.write(RemoteViewsProto.NightModeReflectionAction.METHOD_NAME, mMethodName);
            out.write(RemoteViewsProto.NightModeReflectionAction.PARAMETER_TYPE, mType);
            switch (this.mType) {
                case ICON:
                    writeIconToProto(out, appResources, (Icon) mLightValue,
                            RemoteViewsProto.NightModeReflectionAction.LIGHT_ICON);
                    writeIconToProto(out, appResources, (Icon) mDarkValue,
                            RemoteViewsProto.NightModeReflectionAction.DARK_ICON);
                    break;
                case COLOR_STATE_LIST:
                    writeColorStateListToProto(out, (ColorStateList) mLightValue,
                            RemoteViewsProto.NightModeReflectionAction.LIGHT_COLOR_STATE_LIST);
                    writeColorStateListToProto(out, (ColorStateList) mDarkValue,
                            RemoteViewsProto.NightModeReflectionAction.DARK_COLOR_STATE_LIST);
                    break;
                case INT:
                    out.write(RemoteViewsProto.NightModeReflectionAction.LIGHT_INT,
                            (int) mLightValue);
                    out.write(RemoteViewsProto.NightModeReflectionAction.DARK_INT,
                            (int) mDarkValue);
                    break;
            }
            out.end(token);
        }

        public static PendingResources<Action> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            final long token = in.start(RemoteViewsProto.Action.NIGHT_MODE_REFLECTION_ACTION);
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.NightModeReflectionAction.VIEW_ID:
                        values.put(RemoteViewsProto.NightModeReflectionAction.VIEW_ID,
                                in.readString(RemoteViewsProto.NightModeReflectionAction.VIEW_ID));
                        break;
                    case (int) RemoteViewsProto.NightModeReflectionAction.METHOD_NAME:
                        values.put(RemoteViewsProto.NightModeReflectionAction.METHOD_NAME,
                                in.readString(
                                        RemoteViewsProto.NightModeReflectionAction.METHOD_NAME));
                        break;
                    case (int) RemoteViewsProto.NightModeReflectionAction.PARAMETER_TYPE:
                        values.put(RemoteViewsProto.NightModeReflectionAction.PARAMETER_TYPE,
                                in.readInt(
                                        RemoteViewsProto.NightModeReflectionAction.PARAMETER_TYPE));
                        break;
                    case (int) RemoteViewsProto.NightModeReflectionAction.LIGHT_ICON:
                        values.put(RemoteViewsProto.NightModeReflectionAction.LIGHT_ICON,
                                createIconFromProto(in,
                                        RemoteViewsProto.NightModeReflectionAction.LIGHT_ICON));
                        break;
                    case (int) RemoteViewsProto.NightModeReflectionAction.LIGHT_COLOR_STATE_LIST:
                        values.put(
                                RemoteViewsProto.NightModeReflectionAction.LIGHT_COLOR_STATE_LIST,
                                createColorStateListFromProto(in,
                                        RemoteViewsProto
                                                .NightModeReflectionAction.LIGHT_COLOR_STATE_LIST));
                        break;
                    case (int) RemoteViewsProto.NightModeReflectionAction.LIGHT_INT:
                        values.put(RemoteViewsProto.NightModeReflectionAction.LIGHT_INT,
                                in.readInt(RemoteViewsProto.NightModeReflectionAction.LIGHT_INT));
                        break;
                    case (int) RemoteViewsProto.NightModeReflectionAction.DARK_ICON:
                        values.put(RemoteViewsProto.NightModeReflectionAction.DARK_ICON,
                                createIconFromProto(in,
                                        RemoteViewsProto.NightModeReflectionAction.DARK_ICON));
                        break;
                    case (int) RemoteViewsProto.NightModeReflectionAction.DARK_COLOR_STATE_LIST:
                        values.put(RemoteViewsProto.NightModeReflectionAction.DARK_COLOR_STATE_LIST,
                                createColorStateListFromProto(in,
                                        RemoteViewsProto
                                                .NightModeReflectionAction.DARK_COLOR_STATE_LIST));
                        break;
                    case (int) RemoteViewsProto.NightModeReflectionAction.DARK_INT:
                        values.put(RemoteViewsProto.NightModeReflectionAction.DARK_INT,
                                in.readInt(RemoteViewsProto.NightModeReflectionAction.DARK_INT));
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
            in.end(token);

            checkContainsKeys(values, new long[]{RemoteViewsProto.NightModeReflectionAction.VIEW_ID,
                    RemoteViewsProto.NightModeReflectionAction.METHOD_NAME,
                    RemoteViewsProto.NightModeReflectionAction.PARAMETER_TYPE});

            return (context, resources, rootData, depth) -> {
                int viewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.NightModeReflectionAction.VIEW_ID);
                String methodName = (String) values.get(
                        RemoteViewsProto.NightModeReflectionAction.METHOD_NAME);
                int parameterType = (int) values.get(
                        RemoteViewsProto.NightModeReflectionAction.PARAMETER_TYPE);
                switch (parameterType) {
                    case ICON:
                        PendingResources<Icon> pendingLightIcon =
                                (PendingResources<Icon>) values.get(
                                        RemoteViewsProto.NightModeReflectionAction.LIGHT_ICON);
                        PendingResources<Icon> pendingDarkIcon =
                                (PendingResources<Icon>) values.get(
                                        RemoteViewsProto.NightModeReflectionAction.DARK_ICON);
                        Icon lightIcon = pendingLightIcon != null ? pendingLightIcon.create(context,
                                resources, rootData, depth) : null;
                        Icon darkIcon = pendingDarkIcon != null ? pendingDarkIcon.create(context,
                                resources, rootData, depth) : null;
                        return new NightModeReflectionAction(viewId, methodName, parameterType,
                                lightIcon, darkIcon);
                    case COLOR_STATE_LIST:
                        return new NightModeReflectionAction(viewId, methodName, parameterType,
                                (ColorStateList) values.get(
                                        RemoteViewsProto
                                                .NightModeReflectionAction.LIGHT_COLOR_STATE_LIST),
                                (ColorStateList) values.get(
                                        RemoteViewsProto
                                                .NightModeReflectionAction.DARK_COLOR_STATE_LIST));
                    case INT:
                        return new NightModeReflectionAction(viewId, methodName, parameterType,
                                (int) values.get(
                                        RemoteViewsProto.NightModeReflectionAction.LIGHT_INT, 0),
                                (int) values.get(
                                        RemoteViewsProto.NightModeReflectionAction.DARK_INT, 0));
                    default:
                        throw new RuntimeException("Unknown parameterType: " + parameterType);
                }
            };
        }
    }

    /**
     * This is only used for async execution of actions and it not parcelable.
     */
    private static final class RunnableAction extends RuntimeAction {
        private final Runnable mRunnable;

        RunnableAction(Runnable r) {
            mRunnable = r;
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            mRunnable.run();
        }
    }

    private static boolean hasStableId(View view) {
        Object tag = view.getTag(com.android.internal.R.id.remote_views_stable_id);
        return tag != null;
    }

    private static int getStableId(View view) {
        Integer id = (Integer) view.getTag(com.android.internal.R.id.remote_views_stable_id);
        return id == null ? ViewGroupActionAdd.NO_ID : id;
    }

    private static void setStableId(View view, int stableId) {
        view.setTagInternal(com.android.internal.R.id.remote_views_stable_id, stableId);
    }

    // Returns the next recyclable child of the view group, or -1 if there are none.
    private static int getNextRecyclableChild(ViewGroup vg) {
        Integer tag = (Integer) vg.getTag(com.android.internal.R.id.remote_views_next_child);
        return tag == null ? -1 : tag;
    }

    private static int getViewLayoutId(View v) {
        return (Integer) v.getTag(R.id.widget_frame);
    }

    private static void setNextRecyclableChild(ViewGroup vg, int nextChild, int numChildren) {
        if (nextChild < 0 || nextChild >= numChildren) {
            vg.setTagInternal(com.android.internal.R.id.remote_views_next_child, -1);
        } else {
            vg.setTagInternal(com.android.internal.R.id.remote_views_next_child, nextChild);
        }
    }

    private void finalizeViewRecycling(ViewGroup root) {
        // Remove any recyclable children that were not used. nextChild should either be -1 or point
        // to the next recyclable child that hasn't been recycled.
        int nextChild = getNextRecyclableChild(root);
        if (nextChild >= 0 && nextChild < root.getChildCount()) {
            root.removeViews(nextChild, root.getChildCount() - nextChild);
        }
        // Make sure on the next round, we don't try to recycle if removeAllViews is not called.
        setNextRecyclableChild(root, -1, 0);
        // Traverse the view tree.
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof ViewGroup && !child.isRootNamespace()) {
                finalizeViewRecycling((ViewGroup) child);
            }
        }
    }

    /**
     * ViewGroup methods that are related to adding Views.
     */
    private class ViewGroupActionAdd extends Action {
        static final int NO_ID = -1;
        @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
        private RemoteViews mNestedViews;
        private int mIndex;
        private int mStableId;

        ViewGroupActionAdd(@IdRes int viewId, RemoteViews nestedViews) {
            this(viewId, nestedViews, -1 /* index */, NO_ID /* nestedViewId */);
        }

        ViewGroupActionAdd(@IdRes int viewId, RemoteViews nestedViews, int index) {
            this(viewId, nestedViews, index, NO_ID /* nestedViewId */);
        }

        ViewGroupActionAdd(@IdRes int viewId, RemoteViews nestedViews, int index, int stableId) {
            this.mViewId = viewId;
            mNestedViews = nestedViews;
            mIndex = index;
            mStableId = stableId;
            nestedViews.configureAsChild(getHierarchyRootData());
        }

        ViewGroupActionAdd(Parcel parcel, ApplicationInfo info, int depth) {
            mViewId = parcel.readInt();
            mIndex = parcel.readInt();
            mStableId = parcel.readInt();
            mNestedViews = new RemoteViews(parcel, getHierarchyRootData(), info, depth);
            mNestedViews.addFlags(mApplyFlags);
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            dest.writeInt(mIndex);
            dest.writeInt(mStableId);
            mNestedViews.writeToParcel(dest, flags);
        }

        @Override
        public void setHierarchyRootData(HierarchyRootData root) {
            mNestedViews.configureAsChild(root);
        }

        private int findViewIndexToRecycle(ViewGroup target, RemoteViews newContent) {
            for (int nextChild = getNextRecyclableChild(target); nextChild < target.getChildCount();
                    nextChild++) {
                View child = target.getChildAt(nextChild);
                if (getStableId(child) == mStableId) {
                    return nextChild;
                }
            }
            return -1;
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            final Context context = root.getContext();
            final ViewGroup target = root.findViewById(mViewId);

            if (target == null) {
                return;
            }

            // If removeAllViews was called, this returns the next potential recycled view.
            // If there are no more views to recycle (or removeAllViews was not called), this
            // will return -1.
            final int nextChild = getNextRecyclableChild(target);
            RemoteViews rvToApply = mNestedViews.getRemoteViewsToApply(context);

            int flagsToPropagate = mApplyFlags & FLAG_MASK_TO_PROPAGATE;
            if (flagsToPropagate != 0) rvToApply.addFlags(flagsToPropagate);

            if (nextChild >= 0 && mStableId != NO_ID) {
                // At that point, the views starting at index nextChild are the ones recyclable but
                // not yet recycled. All views added on that round of application are placed before.
                // Find the next view with the same stable id, or -1.
                int recycledViewIndex = findViewIndexToRecycle(target, rvToApply);
                if (recycledViewIndex >= 0) {
                    View child = target.getChildAt(recycledViewIndex);
                    if (rvToApply.canRecycleView(child)) {
                        if (nextChild < recycledViewIndex) {
                            target.removeViews(nextChild, recycledViewIndex - nextChild);
                        }
                        setNextRecyclableChild(target, nextChild + 1, target.getChildCount());
                        rvToApply.reapplyNestedViews(context, child, rootParent, params);
                        return;
                    }
                    // If we cannot recycle the views, we still remove all views in between to
                    // avoid weird behaviors and insert the new view in place of the old one.
                    target.removeViews(nextChild, recycledViewIndex - nextChild + 1);
                }
            }
            // If we cannot recycle, insert the new view before the next recyclable child.

            // Inflate nested views and add as children
            View nestedView = rvToApply.apply(context, target, rootParent, null /* size */, params);
            if (mStableId != NO_ID) {
                setStableId(nestedView, mStableId);
            }
            target.addView(nestedView, mIndex >= 0 ? mIndex : nextChild);
            if (nextChild >= 0) {
                // If we are at the end, there is no reason to try to recycle anymore
                setNextRecyclableChild(target, nextChild + 1, target.getChildCount());
            }
        }

        @Override
        public Action initActionAsync(ViewTree root, ViewGroup rootParent,
                ActionApplyParams params) {
            // In the async implementation, update the view tree so that subsequent calls to
            // findViewById return the current view.
            root.createTree();
            ViewTree target = root.findViewTreeById(mViewId);
            if ((target == null) || !(target.mRoot instanceof ViewGroup)) {
                return ACTION_NOOP;
            }
            final ViewGroup targetVg = (ViewGroup) target.mRoot;

            // Inflate nested views and perform all the async tasks for the child remoteView.
            final Context context = root.mRoot.getContext();

            // If removeAllViews was called, this returns the next potential recycled view.
            // If there are no more views to recycle (or removeAllViews was not called), this
            // will return -1.
            final int nextChild = getNextRecyclableChild(targetVg);
            if (nextChild >= 0 && mStableId != NO_ID) {
                RemoteViews rvToApply = mNestedViews.getRemoteViewsToApply(context);
                final int recycledViewIndex = target.findChildIndex(nextChild,
                        view -> getStableId(view) == mStableId);
                if (recycledViewIndex >= 0) {
                    // At that point, the views starting at index nextChild are the ones
                    // recyclable but not yet recycled. All views added on that round of
                    // application are placed before.
                    ViewTree recycled = target.mChildren.get(recycledViewIndex);
                    // We can only recycle the view if the layout id is the same.
                    if (rvToApply.canRecycleView(recycled.mRoot)) {
                        if (recycledViewIndex > nextChild) {
                            target.removeChildren(nextChild, recycledViewIndex - nextChild);
                        }
                        setNextRecyclableChild(targetVg, nextChild + 1, target.mChildren.size());
                        final AsyncApplyTask reapplyTask = rvToApply.getInternalAsyncApplyTask(
                                context,
                                targetVg, null /* listener */, params, null /* size */,
                                recycled.mRoot);
                        final ViewTree tree = reapplyTask.doInBackground();
                        if (tree == null) {
                            throw new ActionException(reapplyTask.mError);
                        }
                        return new RuntimeAction() {
                            @Override
                            public void apply(View root, ViewGroup rootParent,
                                    ActionApplyParams params) throws ActionException {
                                reapplyTask.onPostExecute(tree);
                                if (recycledViewIndex > nextChild) {
                                    targetVg.removeViews(nextChild, recycledViewIndex - nextChild);
                                }
                            }
                        };
                    }
                    // If the layout id is different, still remove the children as if we recycled
                    // the view, to insert at the same place.
                    target.removeChildren(nextChild, recycledViewIndex - nextChild + 1);
                    return insertNewView(context, target, params,
                            () -> targetVg.removeViews(nextChild,
                                    recycledViewIndex - nextChild + 1));

                }
            }
            // If we cannot recycle, simply add the view at the same available slot.
            return insertNewView(context, target, params, () -> {});
        }

        private Action insertNewView(Context context, ViewTree target,
                ActionApplyParams params, Runnable finalizeAction) {
            ViewGroup targetVg = (ViewGroup) target.mRoot;
            int nextChild = getNextRecyclableChild(targetVg);
            final AsyncApplyTask task = mNestedViews.getInternalAsyncApplyTask(context, targetVg,
                    null /* listener */, params, null /* size */,  null /* result */);
            final ViewTree tree = task.doInBackground();

            if (tree == null) {
                throw new ActionException(task.mError);
            }
            if (mStableId != NO_ID) {
                setStableId(task.mResult, mStableId);
            }

            // Update the global view tree, so that next call to findViewTreeById
            // goes through the subtree as well.
            final int insertIndex = mIndex >= 0 ? mIndex : nextChild;
            target.addChild(tree, insertIndex);
            if (nextChild >= 0) {
                setNextRecyclableChild(targetVg, nextChild + 1, target.mChildren.size());
            }

            return new RuntimeAction() {
                @Override
                public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
                    task.onPostExecute(tree);
                    finalizeAction.run();
                    targetVg.addView(task.mResult, insertIndex);
                }
            };
        }

        @Override
        public int mergeBehavior() {
            return MERGE_APPEND;
        }

        @Override
        public boolean prefersAsyncApply() {
            return mNestedViews.prefersAsyncApply();
        }

        @Override
        public int getActionTag() {
            return VIEW_GROUP_ACTION_ADD_TAG;
        }

        @Override
        public void visitUris(@NonNull Consumer<Uri> visitor) {
            mNestedViews.visitUris(visitor);
        }

        @Override
        public void visitIcons(@NonNull Consumer<Icon> visitor) {
            mNestedViews.visitIcons(visitor);
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            if (!Flags.remoteViewsProto()) return;
            final long token = out.start(RemoteViewsProto.Action.VIEW_GROUP_ADD_ACTION);
            out.write(RemoteViewsProto.ViewGroupAddAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            out.write(RemoteViewsProto.ViewGroupAddAction.INDEX, mIndex);
            out.write(RemoteViewsProto.ViewGroupAddAction.STABLE_ID, mStableId);
            long rvToken = out.start(RemoteViewsProto.ViewGroupAddAction.NESTED_VIEWS);
            mNestedViews.writePreviewToProto(context, out);
            out.end(rvToken);
            out.end(token);
        }
    }

    private PendingResources<Action> createViewGroupActionAddFromProto(ProtoInputStream in)
            throws Exception {
        final LongSparseArray<Object> values = new LongSparseArray<>();

        final long token = in.start(RemoteViewsProto.Action.VIEW_GROUP_ADD_ACTION);
        while (in.nextField() != NO_MORE_FIELDS) {
            switch (in.getFieldNumber()) {
                case (int) RemoteViewsProto.ViewGroupAddAction.VIEW_ID:
                    values.put(RemoteViewsProto.ViewGroupAddAction.VIEW_ID,
                            in.readString(RemoteViewsProto.ViewGroupAddAction.VIEW_ID));
                    break;
                case (int) RemoteViewsProto.ViewGroupAddAction.NESTED_VIEWS:
                    final long nvToken = in.start(RemoteViewsProto.ViewGroupAddAction.NESTED_VIEWS);
                    values.put(RemoteViewsProto.ViewGroupAddAction.NESTED_VIEWS,
                            createFromProto(in));
                    in.end(nvToken);
                    break;
                case (int) RemoteViewsProto.ViewGroupAddAction.INDEX:
                    values.put(RemoteViewsProto.ViewGroupAddAction.INDEX,
                            in.readInt(RemoteViewsProto.ViewGroupAddAction.INDEX));
                    break;
                case (int) RemoteViewsProto.ViewGroupAddAction.STABLE_ID:
                    values.put(RemoteViewsProto.ViewGroupAddAction.STABLE_ID,
                            in.readInt(RemoteViewsProto.ViewGroupAddAction.STABLE_ID));
                    break;
                default:
                    Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                            + ProtoUtils.currentFieldToString(in));
            }
        }
        in.end(token);

        checkContainsKeys(values, new long[]{RemoteViewsProto.ViewGroupAddAction.VIEW_ID,
                RemoteViewsProto.ViewGroupAddAction.NESTED_VIEWS});

        return (context, resources, rootData, depth) -> {
            int viewId = getAsIdentifier(resources, values,
                    RemoteViewsProto.ViewGroupAddAction.VIEW_ID);
            return new ViewGroupActionAdd(viewId, ((PendingResources<RemoteViews>) values.get(
                    RemoteViewsProto.ViewGroupAddAction.NESTED_VIEWS)).create(context, resources,
                    rootData, depth),
                    (int) values.get(RemoteViewsProto.ViewGroupAddAction.INDEX, 0),
                    (int) values.get(RemoteViewsProto.ViewGroupAddAction.STABLE_ID, 0));
        };
    }

    /**
     * ViewGroup methods related to removing child views.
     */
    private static class ViewGroupActionRemove extends Action {
        /**
         * Id that indicates that all child views of the affected ViewGroup should be removed.
         *
         * <p>Using -2 because the default id is -1. This avoids accidentally matching that.
         */
        private static final int REMOVE_ALL_VIEWS_ID = -2;

        private int mViewIdToKeep;

        ViewGroupActionRemove(@IdRes int viewId) {
            this(viewId, REMOVE_ALL_VIEWS_ID);
        }

        ViewGroupActionRemove(@IdRes int viewId, @IdRes int viewIdToKeep) {
            this.mViewId = viewId;
            mViewIdToKeep = viewIdToKeep;
        }

        ViewGroupActionRemove(Parcel parcel) {
            mViewId = parcel.readInt();
            mViewIdToKeep = parcel.readInt();
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            dest.writeInt(mViewIdToKeep);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            final ViewGroup target = root.findViewById(mViewId);

            if (target == null) {
                return;
            }

            if (mViewIdToKeep == REMOVE_ALL_VIEWS_ID) {
                // Remote any view without a stable id
                for (int i = target.getChildCount() - 1; i >= 0; i--) {
                    if (!hasStableId(target.getChildAt(i))) {
                        target.removeViewAt(i);
                    }
                }
                // In the end, only children with a stable id (i.e. recyclable) are left.
                setNextRecyclableChild(target, 0, target.getChildCount());
                return;
            }

            removeAllViewsExceptIdToKeep(target);
        }

        @Override
        public Action initActionAsync(ViewTree root, ViewGroup rootParent,
                ActionApplyParams params) {
            // In the async implementation, update the view tree so that subsequent calls to
            // findViewById return the current view.
            root.createTree();
            ViewTree target = root.findViewTreeById(mViewId);

            if ((target == null) || !(target.mRoot instanceof ViewGroup)) {
                return ACTION_NOOP;
            }

            final ViewGroup targetVg = (ViewGroup) target.mRoot;

            if (mViewIdToKeep == REMOVE_ALL_VIEWS_ID) {
                target.mChildren.removeIf(childTree -> !hasStableId(childTree.mRoot));
                setNextRecyclableChild(targetVg, 0, target.mChildren.size());
            } else {
                // Remove just the children which don't match the excepted view
                target.mChildren.removeIf(childTree -> childTree.mRoot.getId() != mViewIdToKeep);
                if (target.mChildren.isEmpty()) {
                    target.mChildren = null;
                }
            }
            return new RuntimeAction() {
                @Override
                public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
                    if (mViewIdToKeep == REMOVE_ALL_VIEWS_ID) {
                        for (int i = targetVg.getChildCount() - 1; i >= 0; i--) {
                            if (!hasStableId(targetVg.getChildAt(i))) {
                                targetVg.removeViewAt(i);
                            }
                        }
                        return;
                    }

                    removeAllViewsExceptIdToKeep(targetVg);
                }
            };
        }

        /**
         * Iterates through the children in the given ViewGroup and removes all the views that
         * do not have an id of {@link #mViewIdToKeep}.
         */
        private void removeAllViewsExceptIdToKeep(ViewGroup viewGroup) {
            // Otherwise, remove all the views that do not match the id to keep.
            int index = viewGroup.getChildCount() - 1;
            while (index >= 0) {
                if (viewGroup.getChildAt(index).getId() != mViewIdToKeep) {
                    viewGroup.removeViewAt(index);
                }
                index--;
            }
        }

        @Override
        public int getActionTag() {
            return VIEW_GROUP_ACTION_REMOVE_TAG;
        }

        @Override
        public int mergeBehavior() {
            return MERGE_APPEND;
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            final long token = out.start(RemoteViewsProto.Action.VIEW_GROUP_REMOVE_ACTION);
            out.write(RemoteViewsProto.ViewGroupRemoveAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            if (mViewIdToKeep != REMOVE_ALL_VIEWS_ID) {
                out.write(RemoteViewsProto.ViewGroupRemoveAction.VIEW_ID_TO_KEEP,
                        appResources.getResourceName(mViewIdToKeep));
            }
            out.end(token);
        }

        public static PendingResources<Action> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            final long token = in.start(RemoteViewsProto.Action.VIEW_GROUP_REMOVE_ACTION);
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.ViewGroupRemoveAction.VIEW_ID:
                        values.put(RemoteViewsProto.ViewGroupRemoveAction.VIEW_ID,
                                in.readString(RemoteViewsProto.ViewGroupRemoveAction.VIEW_ID));
                        break;
                    case (int) RemoteViewsProto.ViewGroupRemoveAction.VIEW_ID_TO_KEEP:
                        values.put(RemoteViewsProto.ViewGroupRemoveAction.VIEW_ID_TO_KEEP,
                                in.readString(
                                        RemoteViewsProto.ViewGroupRemoveAction.VIEW_ID_TO_KEEP));
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
            in.end(token);

            checkContainsKeys(values, new long[]{RemoteViewsProto.ViewGroupRemoveAction.VIEW_ID});

            return (context, resources, rootData, depth) -> {
                int viewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.ViewGroupRemoveAction.VIEW_ID);
                int viewIdToKeep = (values.indexOfKey(
                        RemoteViewsProto.ViewGroupRemoveAction.VIEW_ID_TO_KEEP) >= 0)
                        ? getAsIdentifier(resources, values,
                        RemoteViewsProto.ViewGroupRemoveAction.VIEW_ID_TO_KEEP)
                        : REMOVE_ALL_VIEWS_ID;
                return new ViewGroupActionRemove(viewId, viewIdToKeep);
            };
        }
    }

    /**
     * Action to remove a view from its parent.
     */
    private static class RemoveFromParentAction extends Action {
        RemoveFromParentAction(@IdRes int viewId) {
            this.mViewId = viewId;
        }

        RemoveFromParentAction(Parcel parcel) {
            mViewId = parcel.readInt();
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            final View target = root.findViewById(mViewId);

            if (target == null || target == root) {
                return;
            }

            ViewParent parent = target.getParent();
            if (parent instanceof ViewManager) {
                ((ViewManager) parent).removeView(target);
            }
        }

        @Override
        public Action initActionAsync(ViewTree root, ViewGroup rootParent,
                ActionApplyParams params) {
            // In the async implementation, update the view tree so that subsequent calls to
            // findViewById return the correct view.
            root.createTree();
            ViewTree target = root.findViewTreeById(mViewId);

            if (target == null || target == root) {
                return ACTION_NOOP;
            }

            ViewTree parent = root.findViewTreeParentOf(target);
            if (parent == null || !(parent.mRoot instanceof ViewManager)) {
                return ACTION_NOOP;
            }
            final ViewManager parentVg = (ViewManager) parent.mRoot;

            parent.mChildren.remove(target);
            return new RuntimeAction() {
                @Override
                public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
                    parentVg.removeView(target.mRoot);
                }
            };
        }

        @Override
        public int getActionTag() {
            return REMOVE_FROM_PARENT_ACTION_TAG;
        }

        @Override
        public int mergeBehavior() {
            return MERGE_APPEND;
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            final long token = out.start(RemoteViewsProto.Action.REMOVE_FROM_PARENT_ACTION);
            out.write(RemoteViewsProto.RemoveFromParentAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            out.end(token);
        }

        public static PendingResources<Action> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            final long token = in.start(RemoteViewsProto.Action.REMOVE_FROM_PARENT_ACTION);
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.RemoveFromParentAction.VIEW_ID:
                        values.put(RemoteViewsProto.RemoveFromParentAction.VIEW_ID,
                                in.readString(RemoteViewsProto.RemoveFromParentAction.VIEW_ID));
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
            in.end(token);

            checkContainsKeys(values, new long[]{RemoteViewsProto.RemoveFromParentAction.VIEW_ID});

            return (context, resources, rootData, depth) -> {
                int viewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.RemoveFromParentAction.VIEW_ID);
                return new RemoveFromParentAction(viewId);
            };
        }
    }

    /**
     * Helper action to set compound drawables on a TextView. Supports relative
     * (s/t/e/b) or cardinal (l/t/r/b) arrangement.
     */
    private static class TextViewDrawableAction extends Action {
        boolean mIsRelative = false;
        boolean mUseIcons = false;
        int mD1, mD2, mD3, mD4;
        Icon mI1, mI2, mI3, mI4;

        boolean mDrawablesLoaded = false;
        Drawable mId1, mId2, mId3, mId4;

        public TextViewDrawableAction(@IdRes int viewId, boolean isRelative, @DrawableRes int d1,
                @DrawableRes int d2, @DrawableRes int d3, @DrawableRes int d4) {
            this.mViewId = viewId;
            this.mIsRelative = isRelative;
            this.mUseIcons = false;
            this.mD1 = d1;
            this.mD2 = d2;
            this.mD3 = d3;
            this.mD4 = d4;
        }

        public TextViewDrawableAction(@IdRes int viewId, boolean isRelative,
                Icon i1, Icon i2, Icon i3, Icon i4) {
            this.mViewId = viewId;
            this.mIsRelative = isRelative;
            this.mUseIcons = true;
            this.mI1 = i1;
            this.mI2 = i2;
            this.mI3 = i3;
            this.mI4 = i4;
        }

        public TextViewDrawableAction(Parcel parcel) {
            mViewId = parcel.readInt();
            mIsRelative = (parcel.readInt() != 0);
            mUseIcons = (parcel.readInt() != 0);
            if (mUseIcons) {
                mI1 = parcel.readTypedObject(Icon.CREATOR);
                mI2 = parcel.readTypedObject(Icon.CREATOR);
                mI3 = parcel.readTypedObject(Icon.CREATOR);
                mI4 = parcel.readTypedObject(Icon.CREATOR);
            } else {
                mD1 = parcel.readInt();
                mD2 = parcel.readInt();
                mD3 = parcel.readInt();
                mD4 = parcel.readInt();
            }
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            dest.writeInt(mIsRelative ? 1 : 0);
            dest.writeInt(mUseIcons ? 1 : 0);
            if (mUseIcons) {
                dest.writeTypedObject(mI1, 0);
                dest.writeTypedObject(mI2, 0);
                dest.writeTypedObject(mI3, 0);
                dest.writeTypedObject(mI4, 0);
            } else {
                dest.writeInt(mD1);
                dest.writeInt(mD2);
                dest.writeInt(mD3);
                dest.writeInt(mD4);
            }
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            final TextView target = root.findViewById(mViewId);
            if (target == null) return;
            if (mDrawablesLoaded) {
                if (mIsRelative) {
                    target.setCompoundDrawablesRelativeWithIntrinsicBounds(mId1, mId2, mId3, mId4);
                } else {
                    target.setCompoundDrawablesWithIntrinsicBounds(mId1, mId2, mId3, mId4);
                }
            } else if (mUseIcons) {
                final Context ctx = target.getContext();
                final Drawable id1 = mI1 == null ? null : mI1.loadDrawable(ctx);
                final Drawable id2 = mI2 == null ? null : mI2.loadDrawable(ctx);
                final Drawable id3 = mI3 == null ? null : mI3.loadDrawable(ctx);
                final Drawable id4 = mI4 == null ? null : mI4.loadDrawable(ctx);
                if (mIsRelative) {
                    target.setCompoundDrawablesRelativeWithIntrinsicBounds(id1, id2, id3, id4);
                } else {
                    target.setCompoundDrawablesWithIntrinsicBounds(id1, id2, id3, id4);
                }
            } else {
                if (mIsRelative) {
                    target.setCompoundDrawablesRelativeWithIntrinsicBounds(mD1, mD2, mD3, mD4);
                } else {
                    target.setCompoundDrawablesWithIntrinsicBounds(mD1, mD2, mD3, mD4);
                }
            }
        }

        @Override
        public Action initActionAsync(ViewTree root, ViewGroup rootParent,
                ActionApplyParams params) {
            final TextView target = root.findViewById(mViewId);
            if (target == null) return ACTION_NOOP;

            TextViewDrawableAction copy = mUseIcons
                    ? new TextViewDrawableAction(mViewId, mIsRelative, mI1, mI2, mI3, mI4)
                    : new TextViewDrawableAction(mViewId, mIsRelative, mD1, mD2, mD3, mD4);

            // Load the drawables on the background thread.
            copy.mDrawablesLoaded = true;
            final Context ctx = target.getContext();

            if (mUseIcons) {
                copy.mId1 = mI1 == null ? null : mI1.loadDrawable(ctx);
                copy.mId2 = mI2 == null ? null : mI2.loadDrawable(ctx);
                copy.mId3 = mI3 == null ? null : mI3.loadDrawable(ctx);
                copy.mId4 = mI4 == null ? null : mI4.loadDrawable(ctx);
            } else {
                copy.mId1 = mD1 == 0 ? null : ctx.getDrawable(mD1);
                copy.mId2 = mD2 == 0 ? null : ctx.getDrawable(mD2);
                copy.mId3 = mD3 == 0 ? null : ctx.getDrawable(mD3);
                copy.mId4 = mD4 == 0 ? null : ctx.getDrawable(mD4);
            }
            return copy;
        }

        @Override
        public boolean prefersAsyncApply() {
            return mUseIcons;
        }

        @Override
        public int getActionTag() {
            return TEXT_VIEW_DRAWABLE_ACTION_TAG;
        }

        @Override
        public void visitUris(@NonNull Consumer<Uri> visitor) {
            if (mUseIcons) {
                visitIconUri(mI1, visitor);
                visitIconUri(mI2, visitor);
                visitIconUri(mI3, visitor);
                visitIconUri(mI4, visitor);
            }
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context,
                Resources appResources) { // rebase
            final long token = out.start(RemoteViewsProto.Action.TEXT_VIEW_DRAWABLE_ACTION);
            out.write(RemoteViewsProto.TextViewDrawableAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            out.write(RemoteViewsProto.TextViewDrawableAction.IS_RELATIVE, mIsRelative);
            if (mUseIcons) {
                long iconsToken = out.start(RemoteViewsProto.TextViewDrawableAction.ICONS);
                if (mI1 != null) {
                    writeIconToProto(out, appResources, mI1,
                            RemoteViewsProto.TextViewDrawableAction.Icons.ONE);
                }
                if (mI2 != null) {
                    writeIconToProto(out, appResources, mI2,
                            RemoteViewsProto.TextViewDrawableAction.Icons.TWO);
                }
                if (mI3 != null) {
                    writeIconToProto(out, appResources, mI3,
                            RemoteViewsProto.TextViewDrawableAction.Icons.THREE);
                }
                if (mI4 != null) {
                    writeIconToProto(out, appResources, mI4,
                            RemoteViewsProto.TextViewDrawableAction.Icons.FOUR);
                }
                out.end(iconsToken);
            } else {
                long resourcesToken = out.start(RemoteViewsProto.TextViewDrawableAction.RESOURCES);
                if (mD1 != 0) {
                    out.write(RemoteViewsProto.TextViewDrawableAction.Resources.ONE,
                            appResources.getResourceName(mD1));
                }
                if (mD2 != 0) {
                    out.write(RemoteViewsProto.TextViewDrawableAction.Resources.TWO,
                            appResources.getResourceName(mD2));
                }
                if (mD3 != 0) {
                    out.write(RemoteViewsProto.TextViewDrawableAction.Resources.THREE,
                            appResources.getResourceName(mD3));
                }
                if (mD4 != 0) {
                    out.write(RemoteViewsProto.TextViewDrawableAction.Resources.FOUR,
                            appResources.getResourceName(mD4));
                }
                out.end(resourcesToken);
            }
            out.end(token);
        }

        public static PendingResources<Action> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            values.put(RemoteViewsProto.TextViewDrawableAction.ICONS,
                    new SparseArray<PendingResources<Icon>>());
            values.put(RemoteViewsProto.TextViewDrawableAction.RESOURCES,
                    new SparseArray<String>());
            final long token = in.start(RemoteViewsProto.Action.TEXT_VIEW_DRAWABLE_ACTION);
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.TextViewDrawableAction.VIEW_ID:
                        values.put(RemoteViewsProto.TextViewDrawableAction.VIEW_ID,
                                in.readString(RemoteViewsProto.TextViewDrawableAction.VIEW_ID));
                        break;
                    case (int) RemoteViewsProto.TextViewDrawableAction.IS_RELATIVE:
                        values.put(RemoteViewsProto.TextViewDrawableAction.IS_RELATIVE,
                                in.readBoolean(
                                        RemoteViewsProto.TextViewDrawableAction.IS_RELATIVE));
                        break;
                    case (int) RemoteViewsProto.TextViewDrawableAction.RESOURCES:
                        final long resourcesToken = in.start(
                                RemoteViewsProto.TextViewDrawableAction.RESOURCES);
                        while (in.nextField() != NO_MORE_FIELDS) {
                            switch (in.getFieldNumber()) {
                                case (int) RemoteViewsProto.TextViewDrawableAction.Resources.ONE:
                                    ((SparseArray<String>) values.get(
                                            RemoteViewsProto.TextViewDrawableAction.RESOURCES)).put(
                                            1, in.readString(
                                                    RemoteViewsProto
                                                            .TextViewDrawableAction.Resources.ONE));
                                    break;
                                case (int) RemoteViewsProto.TextViewDrawableAction.Resources.TWO:
                                    ((SparseArray<String>) values.get(
                                            RemoteViewsProto.TextViewDrawableAction.RESOURCES)).put(
                                            2, in.readString(
                                                    RemoteViewsProto
                                                            .TextViewDrawableAction.Resources.TWO));
                                    break;
                                case (int) RemoteViewsProto.TextViewDrawableAction.Resources.THREE:
                                    ((SparseArray<String>) values.get(
                                            RemoteViewsProto.TextViewDrawableAction.RESOURCES)).put(
                                            3, in.readString(
                                                    RemoteViewsProto
                                                            .TextViewDrawableAction
                                                            .Resources.THREE));
                                    break;
                                case (int) RemoteViewsProto.TextViewDrawableAction.Resources.FOUR:
                                    ((SparseArray<String>) values.get(
                                            RemoteViewsProto.TextViewDrawableAction.RESOURCES)).put(
                                            4, in.readString(
                                                    RemoteViewsProto
                                                            .TextViewDrawableAction
                                                            .Resources.FOUR));
                                    break;
                                default:
                                    Log.w(LOG_TAG,
                                            "Unhandled field while reading RemoteViews proto!\n"
                                                    + ProtoUtils.currentFieldToString(in));
                            }
                        }
                        in.end(resourcesToken);
                        break;
                    case (int) RemoteViewsProto.TextViewDrawableAction.ICONS:
                        final long iconsToken = in.start(
                                RemoteViewsProto.TextViewDrawableAction.ICONS);
                        while (in.nextField() != NO_MORE_FIELDS) {
                            switch (in.getFieldNumber()) {
                                case (int) RemoteViewsProto.TextViewDrawableAction.Icons.ONE:
                                    ((SparseArray<PendingResources<Icon>>) values.get(
                                            RemoteViewsProto.TextViewDrawableAction.ICONS)).put(1,
                                            createIconFromProto(in,
                                                    RemoteViewsProto
                                                            .TextViewDrawableAction.Icons.ONE));
                                    break;
                                case (int) RemoteViewsProto.TextViewDrawableAction.Icons.TWO:
                                    ((SparseArray<PendingResources<Icon>>) values.get(
                                            RemoteViewsProto.TextViewDrawableAction.ICONS)).put(2,
                                            createIconFromProto(in,
                                                    RemoteViewsProto
                                                            .TextViewDrawableAction.Icons.TWO));
                                    break;
                                case (int) RemoteViewsProto.TextViewDrawableAction.Icons.THREE:
                                    ((SparseArray<PendingResources<Icon>>) values.get(
                                            RemoteViewsProto.TextViewDrawableAction.ICONS)).put(3,
                                            createIconFromProto(in,
                                                    RemoteViewsProto
                                                            .TextViewDrawableAction.Icons.THREE));
                                    break;
                                case (int) RemoteViewsProto.TextViewDrawableAction.Icons.FOUR:
                                    ((SparseArray<PendingResources<Icon>>) values.get(
                                            RemoteViewsProto.TextViewDrawableAction.ICONS)).put(4,
                                            createIconFromProto(in,
                                                    RemoteViewsProto
                                                            .TextViewDrawableAction.Icons.FOUR));
                                    break;
                                default:
                                    Log.w(LOG_TAG,
                                            "Unhandled field while reading RemoteViews proto!\n"
                                                    + ProtoUtils.currentFieldToString(in));
                            }
                        }
                        in.end(iconsToken);
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
            in.end(token);

            checkContainsKeys(values, new long[]{RemoteViewsProto.TextViewDrawableAction.VIEW_ID});

            return (context, resources, rootData, depth) -> {
                int viewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.TextViewDrawableAction.VIEW_ID);
                SparseArray<PendingResources<Icon>> icons =
                        (SparseArray<PendingResources<Icon>>) values.get(
                                RemoteViewsProto.TextViewDrawableAction.ICONS);
                SparseArray<String> resArray = (SparseArray<String>) values.get(
                        RemoteViewsProto.TextViewDrawableAction.RESOURCES);
                boolean isRelative = (boolean) values.get(
                        RemoteViewsProto.TextViewDrawableAction.IS_RELATIVE, false);
                if (icons.size() > 0) {
                    return new TextViewDrawableAction(viewId, isRelative,
                            icons.get(1).create(context, resources, rootData, depth),
                            icons.get(2).create(context, resources, rootData, depth),
                            icons.get(3).create(context, resources, rootData, depth),
                            icons.get(4).create(context, resources, rootData, depth));
                } else {
                    int first = resArray.contains(1) ? getAsIdentifier(resources, resArray, 1) : 0;
                    int second = resArray.contains(2) ? getAsIdentifier(resources, resArray, 2) : 0;
                    int third = resArray.contains(3) ? getAsIdentifier(resources, resArray, 3) : 0;
                    int fourth = resArray.contains(4) ? getAsIdentifier(resources, resArray, 4) : 0;
                    return new TextViewDrawableAction(viewId, isRelative, first, second, third,
                            fourth);
                }
            };
        }
    }

    /**
     * Helper action to set text size on a TextView in any supported units.
     */
    private static class TextViewSizeAction extends Action {
        int mUnits;
        float mSize;

        TextViewSizeAction(@IdRes int viewId, @ComplexDimensionUnit int units, float size) {
            this.mViewId = viewId;
            this.mUnits = units;
            this.mSize = size;
        }

        TextViewSizeAction(Parcel parcel) {
            mViewId = parcel.readInt();
            mUnits = parcel.readInt();
            mSize = parcel.readFloat();
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            dest.writeInt(mUnits);
            dest.writeFloat(mSize);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            final TextView target = root.findViewById(mViewId);
            if (target == null) return;
            target.setTextSize(mUnits, mSize);
        }

        @Override
        public int getActionTag() {
            return TEXT_VIEW_SIZE_ACTION_TAG;
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            final long token = out.start(RemoteViewsProto.Action.TEXT_VIEW_SIZE_ACTION);
            out.write(RemoteViewsProto.TextViewSizeAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            out.write(RemoteViewsProto.TextViewSizeAction.UNITS, mUnits);
            out.write(RemoteViewsProto.TextViewSizeAction.SIZE, mSize);
            out.end(token);
        }

        public static PendingResources<Action> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            final long token = in.start(RemoteViewsProto.Action.TEXT_VIEW_SIZE_ACTION);
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.TextViewSizeAction.VIEW_ID:
                        values.put(RemoteViewsProto.TextViewSizeAction.VIEW_ID,
                                in.readString(RemoteViewsProto.TextViewSizeAction.VIEW_ID));
                        break;
                    case (int) RemoteViewsProto.TextViewSizeAction.UNITS:
                        values.put(RemoteViewsProto.TextViewSizeAction.UNITS,
                                in.readInt(RemoteViewsProto.TextViewSizeAction.UNITS));
                        break;
                    case (int) RemoteViewsProto.TextViewSizeAction.SIZE:
                        values.put(RemoteViewsProto.TextViewSizeAction.SIZE,
                                in.readFloat(RemoteViewsProto.TextViewSizeAction.SIZE));
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
            in.end(token);

            checkContainsKeys(values, new long[]{RemoteViewsProto.TextViewSizeAction.VIEW_ID});

            return (context, resources, rootData, depth) -> {
                int viewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.TextViewSizeAction.VIEW_ID);
                return new TextViewSizeAction(viewId,
                        (int) values.get(RemoteViewsProto.TextViewSizeAction.UNITS, 0),
                        (float) values.get(RemoteViewsProto.TextViewSizeAction.SIZE, 0));
            };
        }
    }

    /**
     * Helper action to set padding on a View.
     */
    private static class ViewPaddingAction extends Action {
        @Px int mLeft, mTop, mRight, mBottom;

        public ViewPaddingAction(@IdRes int viewId, @Px int left, @Px int top,
                @Px int right, @Px int bottom) {
            this.mViewId = viewId;
            this.mLeft = left;
            this.mTop = top;
            this.mRight = right;
            this.mBottom = bottom;
        }

        public ViewPaddingAction(Parcel parcel) {
            mViewId = parcel.readInt();
            mLeft = parcel.readInt();
            mTop = parcel.readInt();
            mRight = parcel.readInt();
            mBottom = parcel.readInt();
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            dest.writeInt(mLeft);
            dest.writeInt(mTop);
            dest.writeInt(mRight);
            dest.writeInt(mBottom);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            final View target = root.findViewById(mViewId);
            if (target == null) return;
            target.setPadding(mLeft, mTop, mRight, mBottom);
        }

        @Override
        public int getActionTag() {
            return VIEW_PADDING_ACTION_TAG;
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            final long token = out.start(RemoteViewsProto.Action.VIEW_PADDING_ACTION);
            out.write(RemoteViewsProto.ViewPaddingAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            out.write(RemoteViewsProto.ViewPaddingAction.LEFT, mLeft);
            out.write(RemoteViewsProto.ViewPaddingAction.RIGHT, mRight);
            out.write(RemoteViewsProto.ViewPaddingAction.TOP, mTop);
            out.write(RemoteViewsProto.ViewPaddingAction.BOTTOM, mBottom);
            out.end(token);
        }

        public static PendingResources<Action> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            final long token = in.start(RemoteViewsProto.Action.VIEW_PADDING_ACTION);
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.ViewPaddingAction.VIEW_ID:
                        values.put(RemoteViewsProto.ViewPaddingAction.VIEW_ID,
                                in.readString(RemoteViewsProto.ViewPaddingAction.VIEW_ID));
                        break;
                    case (int) RemoteViewsProto.ViewPaddingAction.LEFT:
                        values.put(RemoteViewsProto.ViewPaddingAction.LEFT,
                                in.readInt(RemoteViewsProto.ViewPaddingAction.LEFT));
                        break;
                    case (int) RemoteViewsProto.ViewPaddingAction.RIGHT:
                        values.put(RemoteViewsProto.ViewPaddingAction.RIGHT,
                                in.readInt(RemoteViewsProto.ViewPaddingAction.RIGHT));
                        break;
                    case (int) RemoteViewsProto.ViewPaddingAction.TOP:
                        values.put(RemoteViewsProto.ViewPaddingAction.TOP,
                                in.readInt(RemoteViewsProto.ViewPaddingAction.TOP));
                        break;
                    case (int) RemoteViewsProto.ViewPaddingAction.BOTTOM:
                        values.put(RemoteViewsProto.ViewPaddingAction.BOTTOM,
                                in.readInt(RemoteViewsProto.ViewPaddingAction.BOTTOM));
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
            in.end(token);

            checkContainsKeys(values, new long[]{RemoteViewsProto.ViewPaddingAction.VIEW_ID});

            return (context, resources, rootData, depth) -> {
                int viewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.ViewPaddingAction.VIEW_ID);
                return new ViewPaddingAction(viewId,
                        (int) values.get(RemoteViewsProto.ViewPaddingAction.LEFT, 0),
                        (int) values.get(RemoteViewsProto.ViewPaddingAction.TOP, 0),
                        (int) values.get(RemoteViewsProto.ViewPaddingAction.RIGHT, 0),
                        (int) values.get(RemoteViewsProto.ViewPaddingAction.BOTTOM, 0));
            };
        }
    }

    /**
     * Helper action to set layout params on a View.
     */
    private static class LayoutParamAction extends Action {
        static final int LAYOUT_MARGIN_LEFT = MARGIN_LEFT;
        static final int LAYOUT_MARGIN_TOP = MARGIN_TOP;
        static final int LAYOUT_MARGIN_RIGHT = MARGIN_RIGHT;
        static final int LAYOUT_MARGIN_BOTTOM = MARGIN_BOTTOM;
        static final int LAYOUT_MARGIN_START = MARGIN_START;
        static final int LAYOUT_MARGIN_END = MARGIN_END;
        static final int LAYOUT_WIDTH = 8;
        static final int LAYOUT_HEIGHT = 9;

        final int mProperty;
        final int mValueType;
        final int mValue;

        /**
         * @param viewId ID of the view alter
         * @param property which layout parameter to alter
         * @param value new value of the layout parameter
         * @param units the units of the given value
         */
        LayoutParamAction(@IdRes int viewId, int property, float value,
                @ComplexDimensionUnit int units) {
            this.mViewId = viewId;
            this.mProperty = property;
            this.mValueType = VALUE_TYPE_COMPLEX_UNIT;
            this.mValue = TypedValue.createComplexDimension(value, units);
        }

        /**
         * @param viewId ID of the view alter
         * @param property which layout parameter to alter
         * @param value value to set.
         * @param valueType must be one of {@link #VALUE_TYPE_COMPLEX_UNIT},
         *   {@link #VALUE_TYPE_RESOURCE}, {@link #VALUE_TYPE_ATTRIBUTE} or
         *   {@link #VALUE_TYPE_RAW}.
         */
        LayoutParamAction(@IdRes int viewId, int property, int value, @ValueType int valueType) {
            this.mViewId = viewId;
            this.mProperty = property;
            this.mValueType = valueType;
            this.mValue = value;
        }

        public LayoutParamAction(Parcel parcel) {
            mViewId = parcel.readInt();
            mProperty = parcel.readInt();
            mValueType = parcel.readInt();
            mValue = parcel.readInt();
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            dest.writeInt(mProperty);
            dest.writeInt(mValueType);
            dest.writeInt(mValue);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            final View target = root.findViewById(mViewId);
            if (target == null) {
                return;
            }
            ViewGroup.LayoutParams layoutParams = target.getLayoutParams();
            if (layoutParams == null) {
                return;
            }
            switch (mProperty) {
                case LAYOUT_MARGIN_LEFT:
                    if (layoutParams instanceof MarginLayoutParams) {
                        ((MarginLayoutParams) layoutParams).leftMargin = getPixelOffset(target);
                        target.setLayoutParams(layoutParams);
                    }
                    break;
                case LAYOUT_MARGIN_TOP:
                    if (layoutParams instanceof MarginLayoutParams) {
                        ((MarginLayoutParams) layoutParams).topMargin = getPixelOffset(target);
                        target.setLayoutParams(layoutParams);
                    }
                    break;
                case LAYOUT_MARGIN_RIGHT:
                    if (layoutParams instanceof MarginLayoutParams) {
                        ((MarginLayoutParams) layoutParams).rightMargin = getPixelOffset(target);
                        target.setLayoutParams(layoutParams);
                    }
                    break;
                case LAYOUT_MARGIN_BOTTOM:
                    if (layoutParams instanceof MarginLayoutParams) {
                        ((MarginLayoutParams) layoutParams).bottomMargin = getPixelOffset(target);
                        target.setLayoutParams(layoutParams);
                    }
                    break;
                case LAYOUT_MARGIN_START:
                    if (layoutParams instanceof MarginLayoutParams) {
                        ((MarginLayoutParams) layoutParams).setMarginStart(getPixelOffset(target));
                        target.setLayoutParams(layoutParams);
                    }
                    break;
                case LAYOUT_MARGIN_END:
                    if (layoutParams instanceof MarginLayoutParams) {
                        ((MarginLayoutParams) layoutParams).setMarginEnd(getPixelOffset(target));
                        target.setLayoutParams(layoutParams);
                    }
                    break;
                case LAYOUT_WIDTH:
                    layoutParams.width = getPixelSize(target);
                    target.setLayoutParams(layoutParams);
                    break;
                case LAYOUT_HEIGHT:
                    layoutParams.height = getPixelSize(target);
                    target.setLayoutParams(layoutParams);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown property " + mProperty);
            }
        }

        private int getPixelOffset(View target) {
            try {
                switch (mValueType) {
                    case VALUE_TYPE_ATTRIBUTE:
                        TypedArray typedArray = target.getContext().obtainStyledAttributes(
                                new int[]{this.mValue});
                        try {
                            return typedArray.getDimensionPixelOffset(0, 0);
                        } finally {
                            typedArray.recycle();
                        }
                    case VALUE_TYPE_RESOURCE:
                        if (mValue == 0) {
                            return 0;
                        }
                        return target.getResources().getDimensionPixelOffset(mValue);
                    case VALUE_TYPE_COMPLEX_UNIT:
                        return TypedValue.complexToDimensionPixelOffset(mValue,
                                target.getResources().getDisplayMetrics());
                    default:
                        return mValue;
                }
            } catch (Throwable t) {
                throw new ActionException(t);
            }
        }

        private int getPixelSize(View target) {
            try {
                switch (mValueType) {
                    case VALUE_TYPE_ATTRIBUTE:
                        TypedArray typedArray = target.getContext().obtainStyledAttributes(
                                new int[]{this.mValue});
                        try {
                            return typedArray.getDimensionPixelSize(0, 0);
                        } finally {
                            typedArray.recycle();
                        }
                    case VALUE_TYPE_RESOURCE:
                        if (mValue == 0) {
                            return 0;
                        }
                        return target.getResources().getDimensionPixelSize(mValue);
                    case VALUE_TYPE_COMPLEX_UNIT:
                        return TypedValue.complexToDimensionPixelSize(mValue,
                                target.getResources().getDisplayMetrics());
                    default:
                        return mValue;
                }
            } catch (Throwable t) {
                throw new ActionException(t);
            }
        }

        @Override
        public int getActionTag() {
            return LAYOUT_PARAM_ACTION_TAG;
        }

        @Override
        public String getUniqueKey() {
            return super.getUniqueKey() + mProperty;
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            final long token = out.start(RemoteViewsProto.Action.LAYOUT_PARAM_ACTION);
            out.write(RemoteViewsProto.LayoutParamAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            out.write(RemoteViewsProto.LayoutParamAction.PROPERTY, mProperty);
            out.write(RemoteViewsProto.LayoutParamAction.LAYOUT_VALUE, mValue);
            out.write(RemoteViewsProto.LayoutParamAction.VALUE_TYPE, mValueType);
            out.end(token);
        }

        public static PendingResources<Action> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            final long token = in.start(RemoteViewsProto.Action.LAYOUT_PARAM_ACTION);
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.LayoutParamAction.VIEW_ID:
                        values.put(RemoteViewsProto.LayoutParamAction.VIEW_ID,
                                in.readString(RemoteViewsProto.LayoutParamAction.VIEW_ID));
                        break;
                    case (int) RemoteViewsProto.LayoutParamAction.PROPERTY:
                        values.put(RemoteViewsProto.LayoutParamAction.PROPERTY,
                                in.readInt(RemoteViewsProto.LayoutParamAction.PROPERTY));
                        break;
                    case (int) RemoteViewsProto.LayoutParamAction.LAYOUT_VALUE:
                        values.put(RemoteViewsProto.LayoutParamAction.LAYOUT_VALUE,
                                in.readInt(RemoteViewsProto.LayoutParamAction.LAYOUT_VALUE));
                        break;
                    case (int) RemoteViewsProto.LayoutParamAction.VALUE_TYPE:
                        values.put(RemoteViewsProto.LayoutParamAction.VALUE_TYPE,
                                in.readInt(RemoteViewsProto.LayoutParamAction.VALUE_TYPE));
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
            in.end(token);

            checkContainsKeys(values, new long[]{RemoteViewsProto.LayoutParamAction.VIEW_ID});

            return (context, resources, rootData, depth) -> {
                int viewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.LayoutParamAction.VIEW_ID);
                return new LayoutParamAction(viewId,
                        (int) values.get(RemoteViewsProto.LayoutParamAction.PROPERTY, 0),
                        (int) values.get(RemoteViewsProto.LayoutParamAction.LAYOUT_VALUE, 0),
                        (int) values.get(RemoteViewsProto.LayoutParamAction.VALUE_TYPE, 0));
            };
        }
    }

    /**
     * Helper action to add a view tag with RemoteInputs.
     */
    private static class SetRemoteInputsAction extends Action {
        final Parcelable[] mRemoteInputs;

        public SetRemoteInputsAction(@IdRes int viewId, RemoteInput[] remoteInputs) {
            this.mViewId = viewId;
            this.mRemoteInputs = remoteInputs;
        }

        public SetRemoteInputsAction(Parcel parcel) {
            mViewId = parcel.readInt();
            mRemoteInputs = parcel.createTypedArray(RemoteInput.CREATOR);
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            dest.writeTypedArray(mRemoteInputs, flags);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            final View target = root.findViewById(mViewId);
            if (target == null) return;

            target.setTagInternal(R.id.remote_input_tag, mRemoteInputs);
        }

        @Override
        public int getActionTag() {
            return SET_REMOTE_INPUTS_ACTION_TAG;
        }
    }

    private static class SetIntTagAction extends Action {
        @IdRes private final int mViewId;
        @IdRes private final int mKey;
        private final int mTag;

        SetIntTagAction(@IdRes int viewId, @IdRes int key, int tag) {
            mViewId = viewId;
            mKey = key;
            mTag = tag;
        }

        SetIntTagAction(Parcel parcel) {
            mViewId = parcel.readInt();
            mKey = parcel.readInt();
            mTag = parcel.readInt();
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            dest.writeInt(mKey);
            dest.writeInt(mTag);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params) {
            final View target = root.findViewById(mViewId);
            if (target == null) return;

            target.setTagInternal(mKey, mTag);
        }

        @Override
        public int getActionTag() {
            return SET_INT_TAG_TAG;
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            final long token = out.start(RemoteViewsProto.Action.SET_INT_TAG_ACTION);
            out.write(RemoteViewsProto.SetIntTagAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            out.write(RemoteViewsProto.SetIntTagAction.KEY,
                    appResources.getResourceName(mKey)); // rebase
            out.write(RemoteViewsProto.SetIntTagAction.TAG, mTag);
            out.end(token);
        }

        public static PendingResources<Action> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            final long token = in.start(RemoteViewsProto.Action.SET_INT_TAG_ACTION);
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.SetIntTagAction.VIEW_ID:
                        values.put(RemoteViewsProto.SetIntTagAction.VIEW_ID,
                                in.readString(RemoteViewsProto.SetIntTagAction.VIEW_ID));
                        break;
                    case (int) RemoteViewsProto.SetIntTagAction.KEY:
                        values.put(RemoteViewsProto.SetIntTagAction.KEY,
                                in.readString(RemoteViewsProto.SetIntTagAction.KEY));
                        break;
                    case (int) RemoteViewsProto.SetIntTagAction.TAG:
                        values.put(RemoteViewsProto.SetIntTagAction.TAG,
                                in.readInt(RemoteViewsProto.SetIntTagAction.TAG));
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
            in.end(token);

            checkContainsKeys(values, new long[]{RemoteViewsProto.SetIntTagAction.VIEW_ID,
                    RemoteViewsProto.SetIntTagAction.KEY});

            return (context, resources, rootData, depth) -> {
                int viewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.SetIntTagAction.VIEW_ID);
                int keyId = getAsIdentifier(resources, values,
                        RemoteViewsProto.SetIntTagAction.KEY);
                return new SetIntTagAction(viewId, keyId,
                        (int) values.get(RemoteViewsProto.SetIntTagAction.TAG, 0));
            };
        }
    }

    private static class SetCompoundButtonCheckedAction extends Action {
        private final boolean mChecked;

        SetCompoundButtonCheckedAction(@IdRes int viewId, boolean checked) {
            this.mViewId = viewId;
            mChecked = checked;
        }

        SetCompoundButtonCheckedAction(Parcel in) {
            mViewId = in.readInt();
            mChecked = in.readBoolean();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            dest.writeBoolean(mChecked);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params)
                throws ActionException {
            final View target = root.findViewById(mViewId);
            if (target == null) return;

            if (!(target instanceof CompoundButton)) {
                Log.w(LOG_TAG, "Cannot set checked to view "
                        + mViewId + " because it is not a CompoundButton");
                return;
            }

            CompoundButton button = (CompoundButton) target;
            Object tag = button.getTag(R.id.remote_checked_change_listener_tag);
            // Temporarily unset the checked change listener so calling setChecked doesn't launch
            // the intent.
            if (tag instanceof OnCheckedChangeListener) {
                button.setOnCheckedChangeListener(null);
                button.setChecked(mChecked);
                button.setOnCheckedChangeListener((OnCheckedChangeListener) tag);
            } else {
                button.setChecked(mChecked);
            }
        }

        @Override
        public int getActionTag() {
            return SET_COMPOUND_BUTTON_CHECKED_TAG;
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            final long token = out.start(
                    RemoteViewsProto.Action.SET_COMPOUND_BUTTON_CHECKED_ACTION);
            out.write(RemoteViewsProto.SetCompoundButtonCheckedAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            out.write(RemoteViewsProto.SetCompoundButtonCheckedAction.CHECKED, mChecked);
            out.end(token);
        }

        public static PendingResources<Action> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            final long token = in.start(RemoteViewsProto.Action.SET_COMPOUND_BUTTON_CHECKED_ACTION);
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.SetCompoundButtonCheckedAction.VIEW_ID:
                        values.put(RemoteViewsProto.SetCompoundButtonCheckedAction.VIEW_ID,
                                in.readString(
                                        RemoteViewsProto.SetCompoundButtonCheckedAction.VIEW_ID));
                        break;
                    case (int) RemoteViewsProto.SetCompoundButtonCheckedAction.CHECKED:
                        values.put(RemoteViewsProto.SetCompoundButtonCheckedAction.CHECKED,
                                in.readBoolean(
                                        RemoteViewsProto.SetCompoundButtonCheckedAction.CHECKED));
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
            in.end(token);

            checkContainsKeys(values,
                    new long[]{RemoteViewsProto.SetCompoundButtonCheckedAction.VIEW_ID});

            return (context, resources, rootData, depth) -> {
                int viewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.SetCompoundButtonCheckedAction.VIEW_ID);
                return new SetCompoundButtonCheckedAction(viewId, (boolean) values.get(
                        RemoteViewsProto.SetCompoundButtonCheckedAction.CHECKED, false));
            };
        }
    }

    private static class SetRadioGroupCheckedAction extends Action {
        @IdRes private final int mCheckedId;

        SetRadioGroupCheckedAction(@IdRes int viewId, @IdRes int checkedId) {
            this.mViewId = viewId;
            mCheckedId = checkedId;
        }

        SetRadioGroupCheckedAction(Parcel in) {
            mViewId = in.readInt();
            mCheckedId = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            dest.writeInt(mCheckedId);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params)
                throws ActionException {
            final View target = root.findViewById(mViewId);
            if (target == null) return;

            if (!(target instanceof RadioGroup)) {
                Log.w(LOG_TAG, "Cannot check " + mViewId + " because it's not a RadioGroup");
                return;
            }

            RadioGroup group = (RadioGroup) target;

            // Temporarily unset all the checked change listeners while we check the group.
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (!(child instanceof CompoundButton)) continue;

                Object tag = child.getTag(R.id.remote_checked_change_listener_tag);
                if (!(tag instanceof OnCheckedChangeListener)) continue;

                // Clear the checked change listener, we'll restore it after the check.
                ((CompoundButton) child).setOnCheckedChangeListener(null);
            }

            group.check(mCheckedId);

            // Loop through the children again and restore the checked change listeners.
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (!(child instanceof CompoundButton)) continue;

                Object tag = child.getTag(R.id.remote_checked_change_listener_tag);
                if (!(tag instanceof OnCheckedChangeListener)) continue;

                ((CompoundButton) child).setOnCheckedChangeListener((OnCheckedChangeListener) tag);
            }
        }

        @Override
        public int getActionTag() {
            return SET_RADIO_GROUP_CHECKED;
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            final long token = out.start(RemoteViewsProto.Action.SET_RADIO_GROUP_CHECKED_ACTION);
            out.write(RemoteViewsProto.SetRadioGroupCheckedAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            if (mCheckedId != -1) {
                out.write(RemoteViewsProto.SetRadioGroupCheckedAction.CHECKED_ID,
                        appResources.getResourceName(mCheckedId));
            }
            out.end(token);
        }

        public static PendingResources<Action> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            final long token = in.start(RemoteViewsProto.Action.SET_RADIO_GROUP_CHECKED_ACTION);
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.SetRadioGroupCheckedAction.VIEW_ID:
                        values.put(RemoteViewsProto.SetRadioGroupCheckedAction.VIEW_ID,
                                in.readString(RemoteViewsProto.SetRadioGroupCheckedAction.VIEW_ID));
                        break;
                    case (int) RemoteViewsProto.SetRadioGroupCheckedAction.CHECKED_ID:
                        values.put(RemoteViewsProto.SetRadioGroupCheckedAction.CHECKED_ID,
                                in.readString(
                                        RemoteViewsProto.SetRadioGroupCheckedAction.CHECKED_ID));
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
            in.end(token);

            checkContainsKeys(values,
                    new long[]{RemoteViewsProto.SetRadioGroupCheckedAction.VIEW_ID});

            return (context, resources, rootData, depth) -> {
                int viewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.SetRadioGroupCheckedAction.VIEW_ID);

                int checkedId = (values.indexOfKey(
                        RemoteViewsProto.SetRadioGroupCheckedAction.CHECKED_ID) >= 0)
                        ? getAsIdentifier(resources, values,
                        RemoteViewsProto.SetRadioGroupCheckedAction.CHECKED_ID) : -1;
                return new SetRadioGroupCheckedAction(viewId, checkedId);
            };
        }
    }

    private static class SetViewOutlinePreferredRadiusAction extends Action {
        @ValueType
        private final int mValueType;
        private final int mValue;

        SetViewOutlinePreferredRadiusAction(@IdRes int viewId, int value,
                @ValueType int valueType) {
            this.mViewId = viewId;
            this.mValueType = valueType;
            this.mValue = value;
        }

        SetViewOutlinePreferredRadiusAction(
                @IdRes int viewId, float radius, @ComplexDimensionUnit int units) {
            this.mViewId = viewId;
            this.mValueType = VALUE_TYPE_COMPLEX_UNIT;
            this.mValue = TypedValue.createComplexDimension(radius, units);

        }

        SetViewOutlinePreferredRadiusAction(Parcel in) {
            mViewId = in.readInt();
            mValueType = in.readInt();
            mValue = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mViewId);
            dest.writeInt(mValueType);
            dest.writeInt(mValue);
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params)
                throws ActionException {
            final View target = root.findViewById(mViewId);
            if (target == null) return;

            try {
                float radius;
                switch (mValueType) {
                    case VALUE_TYPE_ATTRIBUTE:
                        TypedArray typedArray = target.getContext().obtainStyledAttributes(
                                new int[]{mValue});
                        try {
                            radius = typedArray.getDimension(0, 0);
                        } finally {
                            typedArray.recycle();
                        }
                        break;
                    case VALUE_TYPE_RESOURCE:
                        radius = mValue == 0 ? 0 : target.getResources().getDimension(mValue);
                        break;
                    case VALUE_TYPE_COMPLEX_UNIT:
                        radius = TypedValue.complexToDimension(mValue,
                                target.getResources().getDisplayMetrics());
                        break;
                    default:
                        radius = mValue;
                }
                target.setOutlineProvider(new RemoteViewOutlineProvider(radius));
            } catch (Throwable t) {
                throw new ActionException(t);
            }
        }

        @Override
        public int getActionTag() {
            return SET_VIEW_OUTLINE_RADIUS_TAG;
        }

        @Override
        public boolean canWriteToProto() {
            return true;
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            final long token = out.start(
                    RemoteViewsProto.Action.SET_VIEW_OUTLINE_PREFERRED_RADIUS_ACTION);
            out.write(RemoteViewsProto.SetViewOutlinePreferredRadiusAction.VIEW_ID,
                    appResources.getResourceName(mViewId));
            out.write(RemoteViewsProto.SetViewOutlinePreferredRadiusAction.VALUE_TYPE, mValueType);
            out.write(RemoteViewsProto.SetViewOutlinePreferredRadiusAction.VALUE, mValue);
            out.end(token);
        }

        public static PendingResources<Action> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            final long token = in.start(
                    RemoteViewsProto.Action.SET_VIEW_OUTLINE_PREFERRED_RADIUS_ACTION);
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.SetViewOutlinePreferredRadiusAction.VIEW_ID:
                        values.put(RemoteViewsProto.SetViewOutlinePreferredRadiusAction.VIEW_ID,
                                in.readString(
                                        RemoteViewsProto
                                                .SetViewOutlinePreferredRadiusAction.VIEW_ID));
                        break;
                    case (int) RemoteViewsProto.SetViewOutlinePreferredRadiusAction.VALUE_TYPE:
                        values.put(RemoteViewsProto.SetViewOutlinePreferredRadiusAction.VALUE_TYPE,
                                in.readInt(
                                        RemoteViewsProto
                                                .SetViewOutlinePreferredRadiusAction.VALUE_TYPE));
                        break;
                    case (int) RemoteViewsProto.SetViewOutlinePreferredRadiusAction.VALUE:
                        values.put(RemoteViewsProto.SetViewOutlinePreferredRadiusAction.VALUE,
                                in.readInt(
                                        RemoteViewsProto
                                                .SetViewOutlinePreferredRadiusAction.VALUE));
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
            in.end(token);

            checkContainsKeys(values,
                    new long[]{RemoteViewsProto.SetViewOutlinePreferredRadiusAction.VIEW_ID,
                            RemoteViewsProto.SetViewOutlinePreferredRadiusAction.VALUE_TYPE});

            return (context, resources, rootData, depth) -> {
                int viewId = getAsIdentifier(resources, values,
                        RemoteViewsProto.SetViewOutlinePreferredRadiusAction.VIEW_ID);
                return new SetViewOutlinePreferredRadiusAction(viewId,
                        (int) values.get(RemoteViewsProto.SetViewOutlinePreferredRadiusAction.VALUE,
                                0), (int) values.get(
                        RemoteViewsProto.SetViewOutlinePreferredRadiusAction.VALUE_TYPE));
            };
        }
    }

    /**
     * OutlineProvider for a view with a radius set by
     * {@link #setViewOutlinePreferredRadius(int, float, int)}.
     */
    public static final class RemoteViewOutlineProvider extends ViewOutlineProvider {
        private final float mRadius;

        public RemoteViewOutlineProvider(float radius) {
            mRadius = radius;
        }

        /** Returns the corner radius used when providing the view outline. */
        public float getRadius() {
            return mRadius;
        }

        @Override
        public void getOutline(@NonNull View view, @NonNull Outline outline) {
            outline.setRoundRect(
                    0 /*left*/,
                    0 /* top */,
                    view.getWidth() /* right */,
                    view.getHeight() /* bottom */,
                    mRadius);
        }
    }

    private class SetDrawInstructionAction extends Action {

        @Nullable
        private final DrawInstructions mInstructions;

        SetDrawInstructionAction(@NonNull final DrawInstructions instructions) {
            mInstructions = instructions;
        }

        SetDrawInstructionAction(@NonNull final Parcel in) {
            if (drawDataParcel()) {
                mInstructions = DrawInstructions.readFromParcel(in);
            } else {
                mInstructions = null;
            }
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            if (drawDataParcel()) {
                DrawInstructions.writeToParcel(mInstructions, dest, flags);
            }
        }

        @Override
        public void apply(View root, ViewGroup rootParent, ActionApplyParams params)
                throws ActionException {
            if (drawDataParcel() && mInstructions != null
                    && root instanceof RemoteComposePlayer player) {
                final List<byte[]> bytes = mInstructions.mInstructions;
                if (bytes.isEmpty()) {
                    return;
                }
                try (ByteArrayInputStream is = new ByteArrayInputStream(bytes.get(0))) {
                    player.setDocument(new RemoteComposeDocument(is));
                    player.addIdActionListener((viewId, metadata) -> {
                        mActions.forEach(action -> {
                            if (viewId == action.mViewId
                                    && action instanceof SetOnClickResponse setOnClickResponse) {
                                final RemoteResponse response = setOnClickResponse.mResponse;
                                if (response.mFillIntent == null) {
                                    response.mFillIntent = new Intent();
                                }
                                response.mFillIntent.putExtra(
                                        "remotecompose_metadata", metadata);
                                response.handleViewInteraction(player, params.handler);
                            }
                        });
                    });
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Failed to render draw instructions", e);
                }
            }
        }

        @Override
        public int getActionTag() {
            return SET_DRAW_INSTRUCTION_TAG;
        }

        @Override
        public boolean canWriteToProto() {
            return drawDataParcel();
        }

        @Override
        public void writeToProto(ProtoOutputStream out, Context context, Resources appResources) {
            if (!drawDataParcel()) return;
            final long token = out.start(RemoteViewsProto.Action.SET_DRAW_INSTRUCTION_ACTION);
            if (mInstructions != null) {
                for (byte[] bytes : mInstructions.mInstructions) {
                    out.write(RemoteViewsProto.SetDrawInstructionAction.INSTRUCTIONS, bytes);
                }
            }
            out.end(token);
        }
    }

    @FlaggedApi(FLAG_DRAW_DATA_PARCEL)
    private PendingResources<Action> createSetDrawInstructionActionFromProto(ProtoInputStream in)
            throws Exception {
        List<byte[]> instructions = new ArrayList<byte[]>();

        final long token = in.start(RemoteViewsProto.Action.SET_DRAW_INSTRUCTION_ACTION);
        while (in.nextField() != NO_MORE_FIELDS) {
            switch (in.getFieldNumber()) {
                case (int) RemoteViewsProto.SetDrawInstructionAction.INSTRUCTIONS:
                    instructions.add(
                            in.readBytes(RemoteViewsProto.SetDrawInstructionAction.INSTRUCTIONS));
                    break;
                default:
                    Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                            + ProtoUtils.currentFieldToString(in));
            }
        }
        in.end(token);

        return (context, resources, rootData, depth) -> new SetDrawInstructionAction(
                new DrawInstructions.Builder(instructions).build());
    }

    /**
     * Create a new RemoteViews object that will display the views contained
     * in the specified layout file.
     *
     * @param packageName Name of the package that contains the layout resource
     * @param layoutId The id of the layout resource
     */
    public RemoteViews(String packageName, int layoutId) {
        this(getApplicationInfo(packageName, UserHandle.myUserId()), layoutId);
    }

    /**
     * Create a new RemoteViews object that will display the views contained
     * in the specified layout file and change the id of the root view to the specified one.
     *
     * @param packageName Name of the package that contains the layout resource
     * @param layoutId The id of the layout resource
     */
    public RemoteViews(@NonNull String packageName, @LayoutRes int layoutId, @IdRes int viewId) {
        this(packageName, layoutId);
        this.mViewId = viewId;
    }

    /**
     * Create a new RemoteViews object that will display the views contained
     * in the specified layout file.
     *
     * @param application The application whose content is shown by the views.
     * @param layoutId The id of the layout resource.
     *
     * @hide
     */
    protected RemoteViews(ApplicationInfo application, @LayoutRes int layoutId) {
        mApplication = application;
        mLayoutId = layoutId;
        mApplicationInfoCache.put(application);
    }

    private boolean hasMultipleLayouts() {
        return hasLandscapeAndPortraitLayouts() || hasSizedRemoteViews();
    }

    private boolean hasLandscapeAndPortraitLayouts() {
        return (mLandscape != null) && (mPortrait != null);
    }

    private boolean hasSizedRemoteViews() {
        return mSizedRemoteViews != null;
    }

    @Nullable
    private SizeF getIdealSize() {
        return mIdealSize;
    }

    private void setIdealSize(@Nullable SizeF size) {
        mIdealSize = size;
    }

    /**
     * Finds the smallest view in {@code mSizedRemoteViews}.
     * This method must not be called if {@code mSizedRemoteViews} is null.
     */
    private RemoteViews findSmallestRemoteView() {
        return mSizedRemoteViews.get(mSizedRemoteViews.size() - 1);
    }

    /**
     * Create a new RemoteViews object that will inflate as the specified
     * landspace or portrait RemoteViews, depending on the current configuration.
     *
     * @param landscape The RemoteViews to inflate in landscape configuration
     * @param portrait The RemoteViews to inflate in portrait configuration
     * @throws IllegalArgumentException if either landscape or portrait are null or if they are
     *   not from the same application
     */
    public RemoteViews(RemoteViews landscape, RemoteViews portrait) {
        if (landscape == null || portrait == null) {
            throw new IllegalArgumentException("Both RemoteViews must be non-null");
        }
        if (!landscape.hasSameAppInfo(portrait.mApplication)) {
            throw new IllegalArgumentException(
                    "Both RemoteViews must share the same package and user");
        }
        mApplication = portrait.mApplication;
        mLayoutId = portrait.mLayoutId;
        mViewId = portrait.mViewId;
        mLightBackgroundLayoutId = portrait.mLightBackgroundLayoutId;

        mLandscape = landscape;
        mPortrait = portrait;

        mClassCookies = (portrait.mClassCookies != null)
                ? portrait.mClassCookies : landscape.mClassCookies;

        configureDescendantsAsChildren();
    }

    /**
     * Create a new RemoteViews object that will inflate the layout with the closest size
     * specification.
     *
     * The default remote views in that case is always the one with the smallest area.
     *
     * If the {@link RemoteViews} host provides the size of the view, the layout with the largest
     * area that fits entirely in the provided size will be used (i.e. the width and height of
     * the layout must be less than the size of the view, with a 1dp margin to account for
     * rounding). If no layout fits in the view, the layout with the smallest area will be used.
     *
     * @param remoteViews Mapping of size to layout.
     * @throws IllegalArgumentException if the map is empty, there are more than
     *   MAX_INIT_VIEW_COUNT layouts or the remote views are not all from the same application.
     */
    public RemoteViews(@NonNull Map<SizeF, RemoteViews> remoteViews) {
        if (remoteViews.isEmpty()) {
            throw new IllegalArgumentException("The set of RemoteViews cannot be empty");
        }
        if (remoteViews.size() > MAX_INIT_VIEW_COUNT) {
            throw new IllegalArgumentException("Too many RemoteViews in constructor");
        }
        if (remoteViews.size() == 1) {
            // If the map only contains a single mapping, treat this as if that RemoteViews was
            // passed as the top-level RemoteViews.
            RemoteViews single = remoteViews.values().iterator().next();
            initializeFrom(single, /* hierarchyRoot= */ single);
            return;
        }
        mClassCookies = initializeSizedRemoteViews(
                remoteViews.entrySet().stream().map(
                        entry -> {
                            entry.getValue().setIdealSize(entry.getKey());
                            return entry.getValue();
                        }
                ).iterator()
        );

        RemoteViews smallestView = findSmallestRemoteView();
        mApplication = smallestView.mApplication;
        mLayoutId = smallestView.mLayoutId;
        mViewId = smallestView.mViewId;
        mLightBackgroundLayoutId = smallestView.mLightBackgroundLayoutId;

        configureDescendantsAsChildren();
    }

    // Initialize mSizedRemoteViews and return the class cookies.
    private Map<Class, Object> initializeSizedRemoteViews(Iterator<RemoteViews> remoteViews) {
        List<RemoteViews> sizedRemoteViews = new ArrayList<>();
        Map<Class, Object> classCookies = null;
        float viewArea = Float.MAX_VALUE;
        RemoteViews smallestView = null;
        while (remoteViews.hasNext()) {
            RemoteViews view = remoteViews.next();
            SizeF size = view.getIdealSize();
            if (size == null) {
                throw new IllegalStateException("Expected RemoteViews to have ideal size");
            }
            float newViewArea = size.getWidth() * size.getHeight();
            if (smallestView != null && !view.hasSameAppInfo(smallestView.mApplication)) {
                throw new IllegalArgumentException(
                        "All RemoteViews must share the same package and user");
            }
            if (smallestView == null || newViewArea < viewArea) {
                if (smallestView != null) {
                    sizedRemoteViews.add(smallestView);
                }
                viewArea = newViewArea;
                smallestView = view;
            } else {
                sizedRemoteViews.add(view);
            }
            view.setIdealSize(size);
            if (classCookies == null) {
                classCookies = view.mClassCookies;
            }
        }
        sizedRemoteViews.add(smallestView);
        mSizedRemoteViews = sizedRemoteViews;
        return classCookies;
    }

    /**
     * Creates a copy of another RemoteViews.
     */
    public RemoteViews(RemoteViews src) {
        initializeFrom(src, /* hierarchyRoot= */ null);
    }

    /**
     * No-arg constructor for use with {@link #initializeFrom(RemoteViews, RemoteViews)}. A
     * constructor taking two RemoteViews parameters would clash with the landscape/portrait
     * constructor.
     */
    private RemoteViews() {}

    private static RemoteViews createInitializedFrom(@NonNull RemoteViews src,
            @Nullable RemoteViews hierarchyRoot) {
        RemoteViews child = new RemoteViews();
        child.initializeFrom(src, hierarchyRoot);
        return child;
    }

    private void initializeFrom(@NonNull RemoteViews src, @Nullable RemoteViews hierarchyRoot) {
        if (hierarchyRoot == null) {
            mBitmapCache = src.mBitmapCache;
            // We need to create a new instance because we don't reconstruct collection cache
            mCollectionCache = new RemoteCollectionCache(src.mCollectionCache);
            mApplicationInfoCache = src.mApplicationInfoCache;
        } else {
            mBitmapCache = hierarchyRoot.mBitmapCache;
            mCollectionCache = hierarchyRoot.mCollectionCache;
            mApplicationInfoCache = hierarchyRoot.mApplicationInfoCache;
        }
        if (hierarchyRoot == null || src.mIsRoot) {
            // If there's no provided root, or if src was itself a root, then this RemoteViews is
            // the root of the new hierarchy.
            mIsRoot = true;
            hierarchyRoot = this;
        } else {
            // Otherwise, we're a descendant in the hierarchy.
            mIsRoot = false;
        }
        mApplication = src.mApplication;
        mLayoutId = src.mLayoutId;
        mLightBackgroundLayoutId = src.mLightBackgroundLayoutId;
        mApplyFlags = src.mApplyFlags;
        mClassCookies = src.mClassCookies;
        mIdealSize = src.mIdealSize;
        mProviderInstanceId = src.mProviderInstanceId;
        mHasDrawInstructions = src.mHasDrawInstructions;

        if (src.hasLandscapeAndPortraitLayouts()) {
            mLandscape = createInitializedFrom(src.mLandscape, hierarchyRoot);
            mPortrait = createInitializedFrom(src.mPortrait, hierarchyRoot);
        }

        if (src.hasSizedRemoteViews()) {
            mSizedRemoteViews = new ArrayList<>(src.mSizedRemoteViews.size());
            for (RemoteViews srcView : src.mSizedRemoteViews) {
                mSizedRemoteViews.add(createInitializedFrom(srcView, hierarchyRoot));
            }
        }

        if (src.mActions != null) {
            Parcel p = Parcel.obtain();
            p.putClassCookies(mClassCookies);
            src.writeActionsToParcel(p, /* flags= */ 0);
            p.setDataPosition(0);
            // Since src is already in memory, we do not care about stack overflow as it has
            // already been read once.
            readActionsFromParcel(p, 0);
            p.recycle();
        }

        // Now that everything is initialized and duplicated, create new caches for this
        // RemoteViews and recursively set up all descendants.
        if (mIsRoot) {
            reconstructCaches();
        }
    }

    /**
     * Reads a RemoteViews object from a parcel.
     *
     * @param parcel the parcel object
     */
    public RemoteViews(Parcel parcel) {
        this(parcel, /* rootData= */ null, /* info= */ null, /* depth= */ 0);
    }

    /**
     * Instantiates a RemoteViews object using {@link DrawInstructions}, which serves as an
     * alternative to XML layout. {@link DrawInstructions} objects contains the instructions which
     * can be interpreted and rendered accordingly in the host process.
     *
     * @param drawInstructions The {@link DrawInstructions} object
     */
    @FlaggedApi(FLAG_DRAW_DATA_PARCEL)
    public RemoteViews(@NonNull final DrawInstructions drawInstructions) {
        Objects.requireNonNull(drawInstructions);
        mHasDrawInstructions = true;
        addAction(new SetDrawInstructionAction(drawInstructions));
    }

    private RemoteViews(@NonNull Parcel parcel, @Nullable HierarchyRootData rootData,
            @Nullable ApplicationInfo info, int depth) {
        if (depth > MAX_NESTED_VIEWS
                && (UserHandle.getAppId(Binder.getCallingUid()) != Process.SYSTEM_UID)) {
            throw new IllegalArgumentException("Too many nested views.");
        }
        depth++;

        int mode = parcel.readInt();

        if (rootData == null) {
            // We only store a bitmap cache in the root of the RemoteViews.
            mBitmapCache = new BitmapCache(parcel);
            // Store the class cookies such that they are available when we clone this RemoteView.
            mClassCookies = parcel.copyClassCookies();
            mCollectionCache = new RemoteCollectionCache(parcel);
        } else {
            configureAsChild(rootData);
        }

        if (mode == MODE_NORMAL) {
            mApplication = parcel.readTypedObject(ApplicationInfo.CREATOR);
            mIdealSize = parcel.readInt() == 0 ? null : SizeF.CREATOR.createFromParcel(parcel);
            mLayoutId = parcel.readInt();
            mViewId = parcel.readInt();
            mLightBackgroundLayoutId = parcel.readInt();

            readActionsFromParcel(parcel, depth);
        } else if (mode == MODE_HAS_SIZED_REMOTEVIEWS) {
            int numViews = parcel.readInt();
            if (numViews > MAX_INIT_VIEW_COUNT) {
                throw new IllegalArgumentException(
                        "Too many views in mapping from size to RemoteViews.");
            }
            List<RemoteViews> remoteViews = new ArrayList<>(numViews);
            for (int i = 0; i < numViews; i++) {
                RemoteViews view = new RemoteViews(parcel, getHierarchyRootData(), info, depth);
                info = view.mApplication;
                remoteViews.add(view);
            }
            initializeSizedRemoteViews(remoteViews.iterator());
            RemoteViews smallestView = findSmallestRemoteView();
            mApplication = smallestView.mApplication;
            mLayoutId = smallestView.mLayoutId;
            mViewId = smallestView.mViewId;
            mLightBackgroundLayoutId = smallestView.mLightBackgroundLayoutId;
        } else {
            // MODE_HAS_LANDSCAPE_AND_PORTRAIT
            mLandscape = new RemoteViews(parcel, getHierarchyRootData(), info, depth);
            mPortrait =
                    new RemoteViews(parcel, getHierarchyRootData(), mLandscape.mApplication, depth);
            mApplication = mPortrait.mApplication;
            mLayoutId = mPortrait.mLayoutId;
            mViewId = mPortrait.mViewId;
            mLightBackgroundLayoutId = mPortrait.mLightBackgroundLayoutId;
        }
        mApplyFlags = parcel.readInt();
        mProviderInstanceId = parcel.readLong();
        mHasDrawInstructions = parcel.readBoolean();

        // Ensure that all descendants have their caches set up recursively.
        if (mIsRoot) {
            configureDescendantsAsChildren();
        }
    }

    private void readActionsFromParcel(Parcel parcel, int depth) {
        int count = parcel.readInt();
        if (count > 0) {
            mActions = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                mActions.add(getActionFromParcel(parcel, depth));
            }
        }
    }

    private Action getActionFromParcel(Parcel parcel, int depth) {
        int tag = parcel.readInt();
        switch (tag) {
            case SET_ON_CLICK_RESPONSE_TAG:
                return new SetOnClickResponse(parcel);
            case SET_DRAWABLE_TINT_TAG:
                return new SetDrawableTint(parcel);
            case REFLECTION_ACTION_TAG:
                return new ReflectionAction(parcel);
            case VIEW_GROUP_ACTION_ADD_TAG:
                return new ViewGroupActionAdd(parcel, mApplication, depth);
            case VIEW_GROUP_ACTION_REMOVE_TAG:
                return new ViewGroupActionRemove(parcel);
            case VIEW_CONTENT_NAVIGATION_TAG:
                return new ViewContentNavigation(parcel);
            case SET_EMPTY_VIEW_ACTION_TAG:
                return new SetEmptyView(parcel);
            case SET_PENDING_INTENT_TEMPLATE_TAG:
                return new SetPendingIntentTemplate(parcel);
            case SET_REMOTE_VIEW_ADAPTER_INTENT_TAG:
                return new SetRemoteViewsAdapterIntent(parcel);
            case TEXT_VIEW_DRAWABLE_ACTION_TAG:
                return new TextViewDrawableAction(parcel);
            case TEXT_VIEW_SIZE_ACTION_TAG:
                return new TextViewSizeAction(parcel);
            case VIEW_PADDING_ACTION_TAG:
                return new ViewPaddingAction(parcel);
            case BITMAP_REFLECTION_ACTION_TAG:
                return new BitmapReflectionAction(parcel);
            case SET_REMOTE_INPUTS_ACTION_TAG:
                return new SetRemoteInputsAction(parcel);
            case LAYOUT_PARAM_ACTION_TAG:
                return new LayoutParamAction(parcel);
            case SET_RIPPLE_DRAWABLE_COLOR_TAG:
                return new SetRippleDrawableColor(parcel);
            case SET_INT_TAG_TAG:
                return new SetIntTagAction(parcel);
            case REMOVE_FROM_PARENT_ACTION_TAG:
                return new RemoveFromParentAction(parcel);
            case RESOURCE_REFLECTION_ACTION_TAG:
                return new ResourceReflectionAction(parcel);
            case COMPLEX_UNIT_DIMENSION_REFLECTION_ACTION_TAG:
                return new ComplexUnitDimensionReflectionAction(parcel);
            case SET_COMPOUND_BUTTON_CHECKED_TAG:
                return new SetCompoundButtonCheckedAction(parcel);
            case SET_RADIO_GROUP_CHECKED:
                return new SetRadioGroupCheckedAction(parcel);
            case SET_VIEW_OUTLINE_RADIUS_TAG:
                return new SetViewOutlinePreferredRadiusAction(parcel);
            case SET_ON_CHECKED_CHANGE_RESPONSE_TAG:
                return new SetOnCheckedChangeResponse(parcel);
            case NIGHT_MODE_REFLECTION_ACTION_TAG:
                return new NightModeReflectionAction(parcel);
            case SET_REMOTE_COLLECTION_ITEMS_ADAPTER_TAG:
                return new SetRemoteCollectionItemListAdapterAction(parcel);
            case ATTRIBUTE_REFLECTION_ACTION_TAG:
                return new AttributeReflectionAction(parcel);
            case SET_ON_STYLUS_HANDWRITING_RESPONSE_TAG:
                return new SetOnStylusHandwritingResponse(parcel);
            case SET_DRAW_INSTRUCTION_TAG:
                return new SetDrawInstructionAction(parcel);
            default:
                throw new ActionException("Tag " + tag + " not found");
        }
    }

    /**
     * Returns a deep copy of the RemoteViews object. The RemoteView may not be
     * attached to another RemoteView -- it must be the root of a hierarchy.
     *
     * @deprecated use {@link #RemoteViews(RemoteViews)} instead.
     * @throws IllegalStateException if this is not the root of a RemoteView
     *         hierarchy
     */
    @Override
    @Deprecated
    public RemoteViews clone() {
        Preconditions.checkState(mIsRoot, "RemoteView has been attached to another RemoteView. "
                + "May only clone the root of a RemoteView hierarchy.");

        return new RemoteViews(this);
    }

    public String getPackage() {
        return (mApplication != null) ? mApplication.packageName : null;
    }

    /**
     * Returns the layout id of the root layout associated with this RemoteViews. In the case
     * that the RemoteViews has both a landscape and portrait root, this will return the layout
     * id associated with the portrait layout.
     *
     * @return the layout id.
     */
    public int getLayoutId() {
        return hasFlags(FLAG_USE_LIGHT_BACKGROUND_LAYOUT) && (mLightBackgroundLayoutId != 0)
                ? mLightBackgroundLayoutId : mLayoutId;
    }

    /**
     * Sets the root of the hierarchy and then recursively traverses the tree to update the root
     * and populate caches for all descendants.
     */
    private void configureAsChild(@NonNull HierarchyRootData rootData) {
        mIsRoot = false;
        mBitmapCache = rootData.mBitmapCache;
        mCollectionCache = rootData.mRemoteCollectionCache;
        mApplicationInfoCache = rootData.mApplicationInfoCache;
        mClassCookies = rootData.mClassCookies;
        configureDescendantsAsChildren();
    }

    /**
     * Recursively traverses the tree to update the root and populate caches for all descendants.
     */
    private void configureDescendantsAsChildren() {
        // Before propagating down the tree, replace our application from the root application info
        // cache, to ensure the same instance is present throughout the hierarchy to allow for
        // squashing.
        mApplication = mApplicationInfoCache.getOrPut(mApplication);

        HierarchyRootData rootData = getHierarchyRootData();
        if (hasSizedRemoteViews()) {
            for (RemoteViews remoteView : mSizedRemoteViews) {
                remoteView.configureAsChild(rootData);
            }
        } else if (hasLandscapeAndPortraitLayouts()) {
            mLandscape.configureAsChild(rootData);
            mPortrait.configureAsChild(rootData);
        } else {
            if (mActions != null) {
                for (Action action : mActions) {
                    action.setHierarchyRootData(rootData);
                }
            }
        }
    }

    /**
     * Recreates caches at the root level of the hierarchy, then recursively populates the caches
     * down the hierarchy.
     */
    private void reconstructCaches() {
        if (!mIsRoot) return;
        mBitmapCache = new BitmapCache();
        mApplicationInfoCache = new ApplicationInfoCache();
        mApplication = mApplicationInfoCache.getOrPut(mApplication);
        configureDescendantsAsChildren();
    }

    /**
     * Returns an estimate of the bitmap heap memory usage by setBitmap and setImageViewBitmap in
     * this RemoteViews.
     *
     * @hide
     */
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    public long estimateMemoryUsage() {
        return mBitmapCache.getBitmapMemory();
    }

    /**
     * Returns an estimate of bitmap heap memory usage by setIcon and setImageViewIcon in this
     * RemoteViews. Note that this function will count duplicate Icons in its estimate.
     *
     * @hide
     */
    public long estimateIconMemoryUsage() {
        AtomicLong total = new AtomicLong(0);
        visitIcons(icon -> {
            if (icon.getType() == Icon.TYPE_BITMAP || icon.getType() == Icon.TYPE_ADAPTIVE_BITMAP) {
                total.addAndGet(icon.getBitmap().getAllocationByteCount());
            }
        });
        return total.get();
    }

    /**
     * Returns an estimate of the bitmap heap memory usage for all Icon and Bitmap actions in this
     * RemoteViews.
     *
     * @hide
     */
    public long estimateTotalBitmapMemoryUsage() {
        return estimateMemoryUsage() + estimateIconMemoryUsage();
    }

    /**
     * Add an action to be executed on the remote side when apply is called.
     *
     * @param a The action to add
     */
    private void addAction(Action a) {
        if (hasMultipleLayouts()) {
            throw new RuntimeException("RemoteViews specifying separate layouts for orientation"
                    + " or size cannot be modified. Instead, fully configure each layouts"
                    + " individually before constructing the combined layout.");
        }
        if (mActions == null) {
            mActions = new ArrayList<>();
        }
        mActions.add(a);
    }

    /**
     * Equivalent to calling {@link ViewGroup#addView(View)} after inflating the
     * given {@link RemoteViews}. This allows users to build "nested"
     * {@link RemoteViews}. In cases where consumers of {@link RemoteViews} may
     * recycle layouts, use {@link #removeAllViews(int)} to clear any existing
     * children.
     *
     * @param viewId The id of the parent {@link ViewGroup} to add child into.
     * @param nestedView {@link RemoteViews} that describes the child.
     */
    public void addView(@IdRes int viewId, RemoteViews nestedView) {
        // Clear all children when nested views omitted
        addAction(nestedView == null
                ? new ViewGroupActionRemove(viewId)
                : new ViewGroupActionAdd(viewId, nestedView));
    }

    /**
     * Equivalent to calling {@link ViewGroup#addView(View)} after inflating the given
     * {@link RemoteViews}. If the {@link RemoteViews} may be re-inflated or updated,
     * {@link #removeAllViews(int)} must be called on the same {@code viewId
     * } before the first call to this method for the behavior of this method to be predictable.
     *
     * The {@code stableId} will be used to identify a potential view to recycled when the remote
     * view is inflated. Views can be re-used if inserted in the same order, potentially with
     * some views appearing / disappearing. To be recycled the view must not change the layout
     * used to inflate it or its view id (see {@link RemoteViews#RemoteViews(String, int, int)}).
     *
     * Note: if a view is re-used, all the actions will be re-applied on it. However, its properties
     * are not reset, so what was applied in previous round will have an effect. As a view may be
     * re-created at any time by the host, the RemoteViews should not rely on keeping information
     * from previous applications and always re-set all the properties they need.
     *
     * @param viewId The id of the parent {@link ViewGroup} to add child into.
     * @param nestedView {@link RemoteViews} that describes the child.
     * @param stableId An id that is stable across different versions of RemoteViews.
     */
    public void addStableView(@IdRes int viewId, @NonNull RemoteViews nestedView, int stableId) {
        addAction(new ViewGroupActionAdd(viewId, nestedView, -1 /* index */, stableId));
    }

    /**
     * Equivalent to calling {@link ViewGroup#addView(View, int)} after inflating the
     * given {@link RemoteViews}.
     *
     * @param viewId The id of the parent {@link ViewGroup} to add the child into.
     * @param nestedView {@link RemoteViews} of the child to add.
     * @param index The position at which to add the child.
     *
     * @hide
     */
    @UnsupportedAppUsage
    public void addView(@IdRes int viewId, RemoteViews nestedView, int index) {
        addAction(new ViewGroupActionAdd(viewId, nestedView, index));
    }

    /**
     * Equivalent to calling {@link ViewGroup#removeAllViews()}.
     *
     * @param viewId The id of the parent {@link ViewGroup} to remove all
     *            children from.
     */
    public void removeAllViews(@IdRes int viewId) {
        addAction(new ViewGroupActionRemove(viewId));
    }

    /**
     * Removes all views in the {@link ViewGroup} specified by the {@code viewId} except for any
     * child that has the {@code viewIdToKeep} as its id.
     *
     * @param viewId The id of the parent {@link ViewGroup} to remove children from.
     * @param viewIdToKeep The id of a child that should not be removed.
     *
     * @hide
     */
    public void removeAllViewsExceptId(@IdRes int viewId, @IdRes int viewIdToKeep) {
        addAction(new ViewGroupActionRemove(viewId, viewIdToKeep));
    }

    /**
     * Removes the {@link View} specified by the {@code viewId} from its parent {@link ViewManager}.
     * This will do nothing if the viewId specifies the root view of this RemoteViews.
     *
     * @param viewId The id of the {@link View} to remove from its parent.
     *
     * @hide
     */
    public void removeFromParent(@IdRes int viewId) {
        addAction(new RemoveFromParentAction(viewId));
    }

    /**
     * Equivalent to calling {@link AdapterViewAnimator#showNext()}
     *
     * @param viewId The id of the view on which to call {@link AdapterViewAnimator#showNext()}
     * @deprecated As RemoteViews may be reapplied frequently, it is preferable to call
     * {@link #setDisplayedChild(int, int)} to ensure that the adapter index does not change
     * unexpectedly.
     */
    @Deprecated
    public void showNext(@IdRes int viewId) {
        addAction(new ViewContentNavigation(viewId, true /* next */));
    }

    /**
     * Equivalent to calling {@link AdapterViewAnimator#showPrevious()}
     *
     * @param viewId The id of the view on which to call {@link AdapterViewAnimator#showPrevious()}
     * @deprecated As RemoteViews may be reapplied frequently, it is preferable to call
     * {@link #setDisplayedChild(int, int)} to ensure that the adapter index does not change
     * unexpectedly.
     */
    @Deprecated
    public void showPrevious(@IdRes int viewId) {
        addAction(new ViewContentNavigation(viewId, false /* next */));
    }

    /**
     * Equivalent to calling {@link AdapterViewAnimator#setDisplayedChild(int)}
     *
     * @param viewId The id of the view on which to call
     *               {@link AdapterViewAnimator#setDisplayedChild(int)}
     */
    public void setDisplayedChild(@IdRes int viewId, int childIndex) {
        setInt(viewId, "setDisplayedChild", childIndex);
    }

    /**
     * Equivalent to calling {@link View#setVisibility(int)}
     *
     * @param viewId The id of the view whose visibility should change
     * @param visibility The new visibility for the view
     */
    public void setViewVisibility(@IdRes int viewId, @View.Visibility int visibility) {
        setInt(viewId, "setVisibility", visibility);
    }

    /**
     * Equivalent to calling {@link TextView#setText(CharSequence)}
     *
     * @param viewId The id of the view whose text should change
     * @param text The new text for the view
     */
    public void setTextViewText(@IdRes int viewId, CharSequence text) {
        setCharSequence(viewId, "setText", text);
    }

    /**
     * Equivalent to calling {@link TextView#setTextSize(int, float)}
     *
     * @param viewId The id of the view whose text size should change
     * @param units The units of size (e.g. COMPLEX_UNIT_SP)
     * @param size The size of the text
     */
    public void setTextViewTextSize(@IdRes int viewId, int units, float size) {
        addAction(new TextViewSizeAction(viewId, units, size));
    }

    /**
     * Equivalent to calling
     * {@link TextView#setCompoundDrawablesWithIntrinsicBounds(int, int, int, int)}.
     *
     * @param viewId The id of the view whose text should change
     * @param left The id of a drawable to place to the left of the text, or 0
     * @param top The id of a drawable to place above the text, or 0
     * @param right The id of a drawable to place to the right of the text, or 0
     * @param bottom The id of a drawable to place below the text, or 0
     */
    public void setTextViewCompoundDrawables(@IdRes int viewId, @DrawableRes int left,
            @DrawableRes int top, @DrawableRes int right, @DrawableRes int bottom) {
        addAction(new TextViewDrawableAction(viewId, false, left, top, right, bottom));
    }

    /**
     * Equivalent to calling {@link
     * TextView#setCompoundDrawablesRelativeWithIntrinsicBounds(int, int, int, int)}.
     *
     * @param viewId The id of the view whose text should change
     * @param start The id of a drawable to place before the text (relative to the
     * layout direction), or 0
     * @param top The id of a drawable to place above the text, or 0
     * @param end The id of a drawable to place after the text, or 0
     * @param bottom The id of a drawable to place below the text, or 0
     */
    public void setTextViewCompoundDrawablesRelative(@IdRes int viewId, @DrawableRes int start,
            @DrawableRes int top, @DrawableRes int end, @DrawableRes int bottom) {
        addAction(new TextViewDrawableAction(viewId, true, start, top, end, bottom));
    }

    /**
     * Equivalent to calling {@link
     * TextView#setCompoundDrawablesWithIntrinsicBounds(Drawable, Drawable, Drawable, Drawable)}
     * using the drawables yielded by {@link Icon#loadDrawable(Context)}.
     *
     * @param viewId The id of the view whose text should change
     * @param left an Icon to place to the left of the text, or 0
     * @param top an Icon to place above the text, or 0
     * @param right an Icon to place to the right of the text, or 0
     * @param bottom an Icon to place below the text, or 0
     *
     * @hide
     */
    public void setTextViewCompoundDrawables(@IdRes int viewId,
            Icon left, Icon top, Icon right, Icon bottom) {
        addAction(new TextViewDrawableAction(viewId, false, left, top, right, bottom));
    }

    /**
     * Equivalent to calling {@link
     * TextView#setCompoundDrawablesRelativeWithIntrinsicBounds(Drawable, Drawable, Drawable, Drawable)}
     * using the drawables yielded by {@link Icon#loadDrawable(Context)}.
     *
     * @param viewId The id of the view whose text should change
     * @param start an Icon to place before the text (relative to the
     * layout direction), or 0
     * @param top an Icon to place above the text, or 0
     * @param end an Icon to place after the text, or 0
     * @param bottom an Icon to place below the text, or 0
     *
     * @hide
     */
    public void setTextViewCompoundDrawablesRelative(@IdRes int viewId,
            Icon start, Icon top, Icon end, Icon bottom) {
        addAction(new TextViewDrawableAction(viewId, true, start, top, end, bottom));
    }

    /**
     * Equivalent to calling {@link ImageView#setImageResource(int)}
     *
     * @param viewId The id of the view whose drawable should change
     * @param srcId The new resource id for the drawable
     */
    public void setImageViewResource(@IdRes int viewId, @DrawableRes int srcId) {
        setInt(viewId, "setImageResource", srcId);
    }

    /**
     * Equivalent to calling {@link ImageView#setImageURI(Uri)}
     *
     * @param viewId The id of the view whose drawable should change
     * @param uri The Uri for the image
     */
    public void setImageViewUri(@IdRes int viewId, Uri uri) {
        setUri(viewId, "setImageURI", uri);
    }

    /**
     * Equivalent to calling {@link ImageView#setImageBitmap(Bitmap)}
     *
     * @param viewId The id of the view whose bitmap should change
     * @param bitmap The new Bitmap for the drawable
     */
    public void setImageViewBitmap(@IdRes int viewId, Bitmap bitmap) {
        setBitmap(viewId, "setImageBitmap", bitmap);
    }

    /**
     * Equivalent to calling {@link ImageView#setImageIcon(Icon)}
     *
     * @param viewId The id of the view whose bitmap should change
     * @param icon The new Icon for the ImageView
     */
    public void setImageViewIcon(@IdRes int viewId, Icon icon) {
        setIcon(viewId, "setImageIcon", icon);
    }

    /**
     * Equivalent to calling {@link AdapterView#setEmptyView(View)}
     *
     * @param viewId The id of the view on which to set the empty view
     * @param emptyViewId The view id of the empty view
     */
    public void setEmptyView(@IdRes int viewId, @IdRes int emptyViewId) {
        addAction(new SetEmptyView(viewId, emptyViewId));
    }

    /**
     * Equivalent to calling {@link Chronometer#setBase Chronometer.setBase},
     * {@link Chronometer#setFormat Chronometer.setFormat},
     * and {@link Chronometer#start Chronometer.start()} or
     * {@link Chronometer#stop Chronometer.stop()}.
     *
     * @param viewId The id of the {@link Chronometer} to change
     * @param base The time at which the timer would have read 0:00.  This
     *             time should be based off of
     *             {@link android.os.SystemClock#elapsedRealtime SystemClock.elapsedRealtime()}.
     * @param format The Chronometer format string, or null to
     *               simply display the timer value.
     * @param started True if you want the clock to be started, false if not.
     *
     * @see #setChronometerCountDown(int, boolean)
     */
    public void setChronometer(@IdRes int viewId, long base, String format, boolean started) {
        setLong(viewId, "setBase", base);
        setString(viewId, "setFormat", format);
        setBoolean(viewId, "setStarted", started);
    }

    /**
     * Equivalent to calling {@link Chronometer#setCountDown(boolean) Chronometer.setCountDown} on
     * the chronometer with the given viewId.
     *
     * @param viewId The id of the {@link Chronometer} to change
     * @param isCountDown True if you want the chronometer to count down to base instead of
     *                    counting up.
     */
    public void setChronometerCountDown(@IdRes int viewId, boolean isCountDown) {
        setBoolean(viewId, "setCountDown", isCountDown);
    }

    /**
     * Equivalent to calling {@link ProgressBar#setMax ProgressBar.setMax},
     * {@link ProgressBar#setProgress ProgressBar.setProgress}, and
     * {@link ProgressBar#setIndeterminate ProgressBar.setIndeterminate}
     *
     * If indeterminate is true, then the values for max and progress are ignored.
     *
     * @param viewId The id of the {@link ProgressBar} to change
     * @param max The 100% value for the progress bar
     * @param progress The current value of the progress bar.
     * @param indeterminate True if the progress bar is indeterminate,
     *                false if not.
     */
    public void setProgressBar(@IdRes int viewId, int max, int progress,
            boolean indeterminate) {
        setBoolean(viewId, "setIndeterminate", indeterminate);
        if (!indeterminate) {
            setInt(viewId, "setMax", max);
            setInt(viewId, "setProgress", progress);
        }
    }

    /**
     * Equivalent to calling
     * {@link android.view.View#setOnClickListener(android.view.View.OnClickListener)}
     * to launch the provided {@link PendingIntent}. The source bounds
     * ({@link Intent#getSourceBounds()}) of the intent will be set to the bounds of the clicked
     * view in screen space.
     * Note that any activity options associated with the mPendingIntent may get overridden
     * before starting the intent.
     *
     * When setting the on-click action of items within collections (eg. {@link ListView},
     * {@link StackView} etc.), this method will not work. Instead, use {@link
     * RemoteViews#setPendingIntentTemplate(int, PendingIntent)} in conjunction with
     * {@link RemoteViews#setOnClickFillInIntent(int, Intent)}.
     *
     * @param viewId The id of the view that will trigger the {@link PendingIntent} when clicked
     * @param pendingIntent The {@link PendingIntent} to send when user clicks
     */
    public void setOnClickPendingIntent(@IdRes int viewId, PendingIntent pendingIntent) {
        setOnClickResponse(viewId, RemoteResponse.fromPendingIntent(pendingIntent));
    }

    /**
     * Equivalent of calling
     * {@link android.view.View#setOnClickListener(android.view.View.OnClickListener)}
     * to launch the provided {@link RemoteResponse}.
     *
     * @param viewId The id of the view that will trigger the {@link RemoteResponse} when clicked
     * @param response The {@link RemoteResponse} to send when user clicks
     */
    public void setOnClickResponse(@IdRes int viewId, @NonNull RemoteResponse response) {
        addAction(new SetOnClickResponse(viewId, response));
    }

    /**
     * When using collections (eg. {@link ListView}, {@link StackView} etc.) in widgets, it is very
     * costly to set PendingIntents on the individual items, and is hence not recommended. Instead
     * this method should be used to set a single PendingIntent template on the collection, and
     * individual items can differentiate their on-click behavior using
     * {@link RemoteViews#setOnClickFillInIntent(int, Intent)}.
     *
     * @param viewId The id of the collection who's children will use this PendingIntent template
     *          when clicked
     * @param pendingIntentTemplate The {@link PendingIntent} to be combined with extras specified
     *          by a child of viewId and executed when that child is clicked
     */
    public void setPendingIntentTemplate(@IdRes int viewId, PendingIntent pendingIntentTemplate) {
        if (hasDrawInstructions()) {
            getPendingIntentTemplate().set(viewId, pendingIntentTemplate);
            tryAddRemoteResponse(viewId);
        } else {
            addAction(new SetPendingIntentTemplate(viewId, pendingIntentTemplate));
        }
    }

    /**
     * When using collections (eg. {@link ListView}, {@link StackView} etc.) in widgets, it is very
     * costly to set PendingIntents on the individual items, and is hence not recommended. Instead
     * a single PendingIntent template can be set on the collection, see {@link
     * RemoteViews#setPendingIntentTemplate(int, PendingIntent)}, and the individual on-click
     * action of a given item can be distinguished by setting a fillInIntent on that item. The
     * fillInIntent is then combined with the PendingIntent template in order to determine the final
     * intent which will be executed when the item is clicked. This works as follows: any fields
     * which are left blank in the PendingIntent template, but are provided by the fillInIntent
     * will be overwritten, and the resulting PendingIntent will be used. The rest
     * of the PendingIntent template will then be filled in with the associated fields that are
     * set in fillInIntent. See {@link Intent#fillIn(Intent, int)} for more details.
     *
     * @param viewId The id of the view on which to set the fillInIntent
     * @param fillInIntent The intent which will be combined with the parent's PendingIntent
     *        in order to determine the on-click behavior of the view specified by viewId
     */
    public void setOnClickFillInIntent(@IdRes int viewId, Intent fillInIntent) {
        if (hasDrawInstructions()) {
            getFillInIntent().set(viewId, fillInIntent);
            tryAddRemoteResponse(viewId);
        } else {
            setOnClickResponse(viewId, RemoteResponse.fromFillInIntent(fillInIntent));
        }
    }

    /**
     * Equivalent to calling
     * {@link android.widget.CompoundButton#setOnCheckedChangeListener(
     * android.widget.CompoundButton.OnCheckedChangeListener)}
     * to launch the provided {@link RemoteResponse}.
     *
     * The intent will be filled with the current checked state of the view at the key
     * {@link #EXTRA_CHECKED}.
     *
     * The {@link RemoteResponse} will not be launched in response to check changes arising from
     * {@link #setCompoundButtonChecked(int, boolean)} or {@link #setRadioGroupChecked(int, int)}
     * usages.
     *
     * The {@link RemoteResponse} must be created using
     * {@link RemoteResponse#fromFillInIntent(Intent)} in conjunction with
     * {@link RemoteViews#setPendingIntentTemplate(int, PendingIntent)} for items inside
     * collections (eg. {@link ListView}, {@link StackView} etc.).
     *
     * Otherwise, create the {@link RemoteResponse} using
     * {@link RemoteResponse#fromPendingIntent(PendingIntent)}.
     *
     * @param viewId The id of the view that will trigger the {@link PendingIntent} when checked
     *               state changes.
     * @param response The {@link RemoteResponse} to send when the checked state changes.
     */
    public void setOnCheckedChangeResponse(
            @IdRes int viewId,
            @NonNull RemoteResponse response) {
        addAction(
                new SetOnCheckedChangeResponse(
                        viewId,
                        response.setInteractionType(
                                RemoteResponse.INTERACTION_TYPE_CHECKED_CHANGE)));
    }

    /**
     * Equivalent to calling {@link View#setHandwritingDelegatorCallback(Runnable)} to send the
     * provided {@link PendingIntent}.
     *
     * <p>A common use case is a remote view which looks like a text editor but does not actually
     * support text editing itself, and clicking on the remote view launches an activity containing
     * an EditText. To support handwriting initiation in this case, this method can be called on the
     * remote view to configure it as a handwriting delegator, meaning that stylus movement on the
     * remote view triggers a {@link PendingIntent} and starts handwriting mode for the delegate
     * EditText. The {@link PendingIntent} is typically the same as the one passed to {@link
     * #setOnClickPendingIntent} which launches the activity containing the EditText. The EditText
     * should call {@link View#setIsHandwritingDelegate} to set it as a delegate, and also use
     * {@link View#setAllowedHandwritingDelegatorPackage} or {@link
     * android.view.inputmethod.InputMethodManager#HANDWRITING_DELEGATE_FLAG_HOME_DELEGATOR_ALLOWED}
     * if necessary to support delegators from the package displaying the remote view.
     *
     * @param viewId identifier of the view that will trigger the {@link PendingIntent} when a
     *     stylus {@link MotionEvent} occurs within the view's bounds
     * @param pendingIntent the {@link PendingIntent} to send, or {@code null} to clear the
     *     handwriting delegation
     */
    @FlaggedApi(FLAG_HOME_SCREEN_HANDWRITING_DELEGATOR)
    public void setOnStylusHandwritingPendingIntent(
            @IdRes int viewId, @Nullable PendingIntent pendingIntent) {
        addAction(new SetOnStylusHandwritingResponse(viewId, pendingIntent));
    }

    /**
     * @hide
     * Equivalent to calling
     * {@link Drawable#setColorFilter(int, android.graphics.PorterDuff.Mode)},
     * on the {@link Drawable} of a given view.
     * <p>
     *
     * @param viewId The id of the view that contains the target
     *            {@link Drawable}
     * @param targetBackground If true, apply these parameters to the
     *            {@link Drawable} returned by
     *            {@link android.view.View#getBackground()}. Otherwise, assume
     *            the target view is an {@link ImageView} and apply them to
     *            {@link ImageView#getDrawable()}.
     * @param colorFilter Specify a color for a
     *            {@link android.graphics.ColorFilter} for this drawable. This will be ignored if
     *            {@code mode} is {@code null}.
     * @param mode Specify a PorterDuff mode for this drawable, or null to leave
     *            unchanged.
     */
    public void setDrawableTint(@IdRes int viewId, boolean targetBackground,
            @ColorInt int colorFilter, @NonNull PorterDuff.Mode mode) {
        addAction(new SetDrawableTint(viewId, targetBackground, colorFilter, mode));
    }

    /**
     * @hide
     * Equivalent to calling
     * {@link RippleDrawable#setColor(ColorStateList)} on the {@link Drawable} of a given view,
     * assuming it's a {@link RippleDrawable}.
     * <p>
     *
     * @param viewId The id of the view that contains the target
     *            {@link RippleDrawable}
     * @param colorStateList Specify a color for a
     *            {@link ColorStateList} for this drawable.
     */
    public void setRippleDrawableColor(@IdRes int viewId, ColorStateList colorStateList) {
        addAction(new SetRippleDrawableColor(viewId, colorStateList));
    }

    /**
     * @hide
     * Equivalent to calling {@link android.widget.ProgressBar#setProgressTintList}.
     *
     * @param viewId The id of the view whose tint should change
     * @param tint the tint to apply, may be {@code null} to clear tint
     */
    public void setProgressTintList(@IdRes int viewId, ColorStateList tint) {
        addAction(new ReflectionAction(viewId, "setProgressTintList",
                BaseReflectionAction.COLOR_STATE_LIST, tint));
    }

    /**
     * @hide
     * Equivalent to calling {@link android.widget.ProgressBar#setProgressBackgroundTintList}.
     *
     * @param viewId The id of the view whose tint should change
     * @param tint the tint to apply, may be {@code null} to clear tint
     */
    public void setProgressBackgroundTintList(@IdRes int viewId, ColorStateList tint) {
        addAction(new ReflectionAction(viewId, "setProgressBackgroundTintList",
                BaseReflectionAction.COLOR_STATE_LIST, tint));
    }

    /**
     * @hide
     * Equivalent to calling {@link android.widget.ProgressBar#setIndeterminateTintList}.
     *
     * @param viewId The id of the view whose tint should change
     * @param tint the tint to apply, may be {@code null} to clear tint
     */
    public void setProgressIndeterminateTintList(@IdRes int viewId, ColorStateList tint) {
        addAction(new ReflectionAction(viewId, "setIndeterminateTintList",
                BaseReflectionAction.COLOR_STATE_LIST, tint));
    }

    /**
     * Equivalent to calling {@link android.widget.TextView#setTextColor(int)}.
     *
     * @param viewId The id of the view whose text color should change
     * @param color Sets the text color for all the states (normal, selected,
     *            focused) to be this color.
     */
    public void setTextColor(@IdRes int viewId, @ColorInt int color) {
        setInt(viewId, "setTextColor", color);
    }

    /**
     * @hide
     * Equivalent to calling {@link android.widget.TextView#setTextColor(ColorStateList)}.
     *
     * @param viewId The id of the view whose text color should change
     * @param colors the text colors to set
     */
    public void setTextColor(@IdRes int viewId, ColorStateList colors) {
        addAction(new ReflectionAction(viewId, "setTextColor",
                BaseReflectionAction.COLOR_STATE_LIST, colors));
    }

    /**
     * Equivalent to calling {@link android.widget.AbsListView#setRemoteViewsAdapter(Intent)}.
     *
     * @param appWidgetId The id of the app widget which contains the specified view. (This
     *      parameter is ignored in this deprecated method)
     * @param viewId The id of the {@link AdapterView}
     * @param intent The intent of the service which will be
     *            providing data to the RemoteViewsAdapter
     * @deprecated This method has been deprecated. See
     *      {@link android.widget.RemoteViews#setRemoteAdapter(int, Intent)}
     */
    @Deprecated
    public void setRemoteAdapter(int appWidgetId, @IdRes int viewId, Intent intent) {
        setRemoteAdapter(viewId, intent);
    }

    /**
     * Equivalent to calling {@link android.widget.AbsListView#setRemoteViewsAdapter(Intent)}.
     * Can only be used for App Widgets.
     *
     * @param viewId The id of the {@link AdapterView}
     * @param intent The intent of the service which will be
     *            providing data to the RemoteViewsAdapter
     * @deprecated use
     * {@link #setRemoteAdapter(int, android.widget.RemoteViews.RemoteCollectionItems)} instead
     */
    @Deprecated
    public void setRemoteAdapter(@IdRes int viewId, Intent intent) {
        if (remoteAdapterConversion()) {
            addAction(new SetRemoteCollectionItemListAdapterAction(viewId, intent));
        } else {
            addAction(new SetRemoteViewsAdapterIntent(viewId, intent));
        }
    }

    /**
     * Creates a simple Adapter for the viewId specified. The viewId must point to an AdapterView,
     * ie. {@link ListView}, {@link GridView}, {@link StackView} or {@link AdapterViewAnimator}.
     * This is a simpler but less flexible approach to populating collection widgets. Its use is
     * encouraged for most scenarios, as long as the total memory within the list of RemoteViews
     * is relatively small (ie. doesn't contain large or numerous Bitmaps, see {@link
     * RemoteViews#setImageViewBitmap}). In the case of numerous images, the use of API is still
     * possible by setting image URIs instead of Bitmaps, see {@link RemoteViews#setImageViewUri}.
     *
     * This API is supported in the compatibility library for previous API levels, see
     * RemoteViewsCompat.
     *
     * @param viewId The id of the {@link AdapterView}
     * @param list The list of RemoteViews which will populate the view specified by viewId.
     * @param viewTypeCount The maximum number of unique layout id's used to construct the list of
     *      RemoteViews. This count cannot change during the life-cycle of a given widget, so this
     *      parameter should account for the maximum possible number of types that may appear in the
     *      See {@link Adapter#getViewTypeCount()}.
     *
     * @hide
     * @deprecated this appears to have no users outside of UnsupportedAppUsage?
     */
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    @Deprecated
    public void setRemoteAdapter(@IdRes int viewId, ArrayList<RemoteViews> list,
            int viewTypeCount) {
        RemoteCollectionItems.Builder b = new RemoteCollectionItems.Builder();
        for (int i = 0; i < list.size(); i++) {
            b.addItem(i, list.get(i));
        }
        setRemoteAdapter(viewId, b.setViewTypeCount(viewTypeCount).build());
    }

    /**
     * Creates a simple Adapter for the viewId specified. The viewId must point to an AdapterView,
     * ie. {@link ListView}, {@link GridView}, {@link StackView} or {@link AdapterViewAnimator}.
     * This is a simpler but less flexible approach to populating collection widgets. Its use is
     * encouraged for most scenarios, as long as the total memory within the list of RemoteViews
     * is relatively small (ie. doesn't contain large or numerous Bitmaps, see {@link
     * RemoteViews#setImageViewBitmap}). In the case of numerous images, the use of API is still
     * possible by setting image URIs instead of Bitmaps, see {@link RemoteViews#setImageViewUri}.
     *
     * This API is supported in the compatibility library for previous API levels, see
     * RemoteViewsCompat.
     *
     * @param viewId The id of the {@link AdapterView}.
     * @param items The items to display in the {@link AdapterView}.
     */
    public void setRemoteAdapter(@IdRes int viewId, @NonNull RemoteCollectionItems items) {
        addAction(new SetRemoteCollectionItemListAdapterAction(viewId, items));
    }

    /**
     * Equivalent to calling {@link ListView#smoothScrollToPosition(int)}.
     *
     * @param viewId The id of the view to change
     * @param position Scroll to this adapter position
     */
    public void setScrollPosition(@IdRes int viewId, int position) {
        setInt(viewId, "smoothScrollToPosition", position);
    }

    /**
     * Equivalent to calling {@link ListView#smoothScrollByOffset(int)}.
     *
     * @param viewId The id of the view to change
     * @param offset Scroll by this adapter position offset
     */
    public void setRelativeScrollPosition(@IdRes int viewId, int offset) {
        setInt(viewId, "smoothScrollByOffset", offset);
    }

    /**
     * Equivalent to calling {@link android.view.View#setPadding(int, int, int, int)}.
     *
     * @param viewId The id of the view to change
     * @param left the left padding in pixels
     * @param top the top padding in pixels
     * @param right the right padding in pixels
     * @param bottom the bottom padding in pixels
     */
    public void setViewPadding(@IdRes int viewId,
            @Px int left, @Px int top, @Px int right, @Px int bottom) {
        addAction(new ViewPaddingAction(viewId, left, top, right, bottom));
    }

    /**
     * Equivalent to calling {@link MarginLayoutParams#setMarginEnd}.
     * Only works if the {@link View#getLayoutParams()} supports margins.
     *
     * @param viewId The id of the view to change
     * @param type The margin being set e.g. {@link #MARGIN_END}
     * @param dimen a dimension resource to apply to the margin, or 0 to clear the margin.
     */
    public void setViewLayoutMarginDimen(@IdRes int viewId, @MarginType int type,
            @DimenRes int dimen) {
        addAction(new LayoutParamAction(viewId, type, dimen, VALUE_TYPE_RESOURCE));
    }

    /**
     * Equivalent to calling {@link MarginLayoutParams#setMarginEnd}.
     * Only works if the {@link View#getLayoutParams()} supports margins.
     *
     * @param viewId The id of the view to change
     * @param type The margin being set e.g. {@link #MARGIN_END}
     * @param attr a dimension attribute to apply to the margin, or 0 to clear the margin.
     */
    public void setViewLayoutMarginAttr(@IdRes int viewId, @MarginType int type,
            @AttrRes int attr) {
        addAction(new LayoutParamAction(viewId, type, attr, VALUE_TYPE_ATTRIBUTE));
    }

    /**
     * Equivalent to calling {@link MarginLayoutParams#setMarginEnd}.
     * Only works if the {@link View#getLayoutParams()} supports margins.
     *
     * <p>NOTE: It is recommended to use {@link TypedValue#COMPLEX_UNIT_PX} only for 0.
     * Setting margins in pixels will behave poorly when the RemoteViews object is used on a
     * display with a different density.
     *
     * @param viewId The id of the view to change
     * @param type The margin being set e.g. {@link #MARGIN_END}
     * @param value a value for the margin the given units.
     * @param units The unit type of the value e.g. {@link TypedValue#COMPLEX_UNIT_DIP}
     */
    public void setViewLayoutMargin(@IdRes int viewId, @MarginType int type, float value,
            @ComplexDimensionUnit int units) {
        addAction(new LayoutParamAction(viewId, type, value, units));
    }

    /**
     * Equivalent to setting {@link android.view.ViewGroup.LayoutParams#width} except that you may
     * provide the value in any dimension units.
     *
     * <p>NOTE: It is recommended to use {@link TypedValue#COMPLEX_UNIT_PX} only for 0,
     * {@link ViewGroup.LayoutParams#WRAP_CONTENT}, or {@link ViewGroup.LayoutParams#MATCH_PARENT}.
     * Setting actual sizes in pixels will behave poorly when the RemoteViews object is used on a
     * display with a different density.
     *
     * @param width Width of the view in the given units
     * @param units The unit type of the value e.g. {@link TypedValue#COMPLEX_UNIT_DIP}
     */
    public void setViewLayoutWidth(@IdRes int viewId, float width,
            @ComplexDimensionUnit int units) {
        addAction(new LayoutParamAction(viewId, LayoutParamAction.LAYOUT_WIDTH, width, units));
    }

    /**
     * Equivalent to setting {@link android.view.ViewGroup.LayoutParams#width} with
     * the result of {@link Resources#getDimensionPixelSize(int)}.
     *
     * @param widthDimen the dimension resource for the view's width
     */
    public void setViewLayoutWidthDimen(@IdRes int viewId, @DimenRes int widthDimen) {
        addAction(new LayoutParamAction(viewId, LayoutParamAction.LAYOUT_WIDTH, widthDimen,
                VALUE_TYPE_RESOURCE));
    }

    /**
     * Equivalent to setting {@link android.view.ViewGroup.LayoutParams#width} with
     * the value of the given attribute in the current theme.
     *
     * @param widthAttr the dimension attribute for the view's width
     */
    public void setViewLayoutWidthAttr(@IdRes int viewId, @AttrRes int widthAttr) {
        addAction(new LayoutParamAction(viewId, LayoutParamAction.LAYOUT_WIDTH, widthAttr,
                VALUE_TYPE_ATTRIBUTE));
    }

    /**
     * Equivalent to setting {@link android.view.ViewGroup.LayoutParams#height} except that you may
     * provide the value in any dimension units.
     *
     * <p>NOTE: It is recommended to use {@link TypedValue#COMPLEX_UNIT_PX} only for 0,
     * {@link ViewGroup.LayoutParams#WRAP_CONTENT}, or {@link ViewGroup.LayoutParams#MATCH_PARENT}.
     * Setting actual sizes in pixels will behave poorly when the RemoteViews object is used on a
     * display with a different density.
     *
     * @param height height of the view in the given units
     * @param units The unit type of the value e.g. {@link TypedValue#COMPLEX_UNIT_DIP}
     */
    public void setViewLayoutHeight(@IdRes int viewId, float height,
            @ComplexDimensionUnit int units) {
        addAction(new LayoutParamAction(viewId, LayoutParamAction.LAYOUT_HEIGHT, height, units));
    }

    /**
     * Equivalent to setting {@link android.view.ViewGroup.LayoutParams#height} with
     * the result of {@link Resources#getDimensionPixelSize(int)}.
     *
     * @param heightDimen a dimen resource to read the height from.
     */
    public void setViewLayoutHeightDimen(@IdRes int viewId, @DimenRes int heightDimen) {
        addAction(new LayoutParamAction(viewId, LayoutParamAction.LAYOUT_HEIGHT, heightDimen,
                VALUE_TYPE_RESOURCE));
    }

    /**
     * Equivalent to setting {@link android.view.ViewGroup.LayoutParams#height} with
     * the value of the given attribute in the current theme.
     *
     * @param heightAttr a dimen attribute to read the height from.
     */
    public void setViewLayoutHeightAttr(@IdRes int viewId, @AttrRes int heightAttr) {
        addAction(new LayoutParamAction(viewId, LayoutParamAction.LAYOUT_HEIGHT, heightAttr,
                VALUE_TYPE_ATTRIBUTE));
    }

    /**
     * Sets an OutlineProvider on the view whose corner radius is a dimension calculated using
     * {@link TypedValue#applyDimension(int, float, DisplayMetrics)}.
     *
     * <p>NOTE: It is recommended to use {@link TypedValue#COMPLEX_UNIT_PX} only for 0.
     * Setting margins in pixels will behave poorly when the RemoteViews object is used on a
     * display with a different density.
     */
    public void setViewOutlinePreferredRadius(
            @IdRes int viewId, float radius, @ComplexDimensionUnit int units) {
        addAction(new SetViewOutlinePreferredRadiusAction(viewId, radius, units));
    }

    /**
     * Sets an OutlineProvider on the view whose corner radius is a dimension resource with
     * {@code resId}.
     */
    public void setViewOutlinePreferredRadiusDimen(@IdRes int viewId, @DimenRes int resId) {
        addAction(new SetViewOutlinePreferredRadiusAction(viewId, resId, VALUE_TYPE_RESOURCE));
    }

    /**
     * Sets an OutlineProvider on the view whose corner radius is a dimension attribute with
     * {@code attrId}.
     */
    public void setViewOutlinePreferredRadiusAttr(@IdRes int viewId, @AttrRes int attrId) {
        addAction(new SetViewOutlinePreferredRadiusAction(viewId, attrId, VALUE_TYPE_ATTRIBUTE));
    }

    /**
     * Call a method taking one boolean on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The value to pass to the method.
     */
    public void setBoolean(@IdRes int viewId, String methodName, boolean value) {
        addAction(new ReflectionAction(viewId, methodName, BaseReflectionAction.BOOLEAN, value));
    }

    /**
     * Call a method taking one byte on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The value to pass to the method.
     */
    public void setByte(@IdRes int viewId, String methodName, byte value) {
        addAction(new ReflectionAction(viewId, methodName, BaseReflectionAction.BYTE, value));
    }

    /**
     * Call a method taking one short on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The value to pass to the method.
     */
    public void setShort(@IdRes int viewId, String methodName, short value) {
        addAction(new ReflectionAction(viewId, methodName, BaseReflectionAction.SHORT, value));
    }

    /**
     * Call a method taking one int on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The value to pass to the method.
     */
    public void setInt(@IdRes int viewId, String methodName, int value) {
        addAction(new ReflectionAction(viewId, methodName, BaseReflectionAction.INT, value));
    }

    /**
     * Call a method taking one int, a size in pixels, on a view in the layout for this
     * RemoteViews.
     *
     * The dimension will be resolved from the resources at the time the {@link RemoteViews} is
     * (re-)applied.
     *
     * Undefined resources will result in an exception, except 0 which will resolve to 0.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param dimenResource The resource to resolve and pass as argument to the method.
     */
    public void setIntDimen(@IdRes int viewId, @NonNull String methodName,
            @DimenRes int dimenResource) {
        addAction(new ResourceReflectionAction(viewId, methodName, BaseReflectionAction.INT,
                ResourceReflectionAction.DIMEN_RESOURCE, dimenResource));
    }

    /**
     * Call a method taking one int, a size in pixels, on a view in the layout for this
     * RemoteViews.
     *
     * The dimension will be resolved from the specified dimension at the time of inflation.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The value of the dimension.
     * @param unit The unit in which the value is specified.
     */
    public void setIntDimen(@IdRes int viewId, @NonNull String methodName,
            float value, @ComplexDimensionUnit int unit) {
        addAction(new ComplexUnitDimensionReflectionAction(viewId, methodName, ReflectionAction.INT,
                value, unit));
    }

    /**
     * Call a method taking one int, a size in pixels, on a view in the layout for this
     * RemoteViews.
     *
     * The dimension will be resolved from the theme attribute at the time the
     * {@link RemoteViews} is (re-)applied.
     *
     * Unresolvable attributes will result in an exception, except 0 which will resolve to 0.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param dimenAttr The attribute to resolve and pass as argument to the method.
     */
    public void setIntDimenAttr(@IdRes int viewId, @NonNull String methodName,
            @AttrRes int dimenAttr) {
        addAction(new AttributeReflectionAction(viewId, methodName, BaseReflectionAction.INT,
                ResourceReflectionAction.DIMEN_RESOURCE, dimenAttr));
    }

    /**
     * Call a method taking one int, a color, on a view in the layout for this RemoteViews.
     *
     * The Color will be resolved from the resources at the time the {@link RemoteViews} is (re-)
     * applied.
     *
     * Undefined resources will result in an exception, except 0 which will resolve to 0.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param colorResource The resource to resolve and pass as argument to the method.
     */
    public void setColor(@IdRes int viewId, @NonNull String methodName,
            @ColorRes int colorResource) {
        addAction(new ResourceReflectionAction(viewId, methodName, BaseReflectionAction.INT,
                ResourceReflectionAction.COLOR_RESOURCE, colorResource));
    }

    /**
     * Call a method taking one int, a color, on a view in the layout for this RemoteViews.
     *
     * The Color will be resolved from the theme attribute at the time the {@link RemoteViews} is
     * (re-)applied.
     *
     * Unresolvable attributes will result in an exception, except 0 which will resolve to 0.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param colorAttribute The theme attribute to resolve and pass as argument to the method.
     */
    public void setColorAttr(@IdRes int viewId, @NonNull String methodName,
            @AttrRes int colorAttribute) {
        addAction(new AttributeReflectionAction(viewId, methodName, BaseReflectionAction.INT,
                AttributeReflectionAction.COLOR_RESOURCE, colorAttribute));
    }

    /**
     * Call a method taking one int, a color, on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param notNight The value to pass to the method when the view's configuration is set to
     *                 {@link Configuration#UI_MODE_NIGHT_NO}
     * @param night The value to pass to the method when the view's configuration is set to
     *                 {@link Configuration#UI_MODE_NIGHT_YES}
     */
    public void setColorInt(
            @IdRes int viewId,
            @NonNull String methodName,
            @ColorInt int notNight,
            @ColorInt int night) {
        addAction(
                new NightModeReflectionAction(
                        viewId,
                        methodName,
                        BaseReflectionAction.INT,
                        notNight,
                        night));
    }


    /**
     * Call a method taking one ColorStateList on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The value to pass to the method.
     */
    public void setColorStateList(@IdRes int viewId, @NonNull String methodName,
            @Nullable ColorStateList value) {
        addAction(new ReflectionAction(viewId, methodName, BaseReflectionAction.COLOR_STATE_LIST,
                value));
    }

    /**
     * Call a method taking one ColorStateList on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param notNight The value to pass to the method when the view's configuration is set to
     *                 {@link Configuration#UI_MODE_NIGHT_NO}
     * @param night The value to pass to the method when the view's configuration is set to
     *                 {@link Configuration#UI_MODE_NIGHT_YES}
     */
    public void setColorStateList(
            @IdRes int viewId,
            @NonNull String methodName,
            @Nullable ColorStateList notNight,
            @Nullable ColorStateList night) {
        addAction(
                new NightModeReflectionAction(
                        viewId,
                        methodName,
                        BaseReflectionAction.COLOR_STATE_LIST,
                        notNight,
                        night));
    }

    /**
     * Call a method taking one ColorStateList on a view in the layout for this RemoteViews.
     *
     * The ColorStateList will be resolved from the resources at the time the {@link RemoteViews} is
     * (re-)applied.
     *
     * Undefined resources will result in an exception, except 0 which will resolve to null.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param colorResource The resource to resolve and pass as argument to the method.
     */
    public void setColorStateList(@IdRes int viewId, @NonNull String methodName,
            @ColorRes int colorResource) {
        addAction(new ResourceReflectionAction(viewId, methodName,
                BaseReflectionAction.COLOR_STATE_LIST, ResourceReflectionAction.COLOR_RESOURCE,
                colorResource));
    }

    /**
     * Call a method taking one ColorStateList on a view in the layout for this RemoteViews.
     *
     * The ColorStateList will be resolved from the theme attribute at the time the
     * {@link RemoteViews} is (re-)applied.
     *
     * Unresolvable attributes will result in an exception, except 0 which will resolve to null.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param colorAttr The theme attribute to resolve and pass as argument to the method.
     */
    public void setColorStateListAttr(@IdRes int viewId, @NonNull String methodName,
            @AttrRes int colorAttr) {
        addAction(new AttributeReflectionAction(viewId, methodName,
                BaseReflectionAction.COLOR_STATE_LIST, ResourceReflectionAction.COLOR_RESOURCE,
                colorAttr));
    }

    /**
     * Call a method taking one long on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The value to pass to the method.
     */
    public void setLong(@IdRes int viewId, String methodName, long value) {
        addAction(new ReflectionAction(viewId, methodName, BaseReflectionAction.LONG, value));
    }

    /**
     * Call a method taking one float on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The value to pass to the method.
     */
    public void setFloat(@IdRes int viewId, String methodName, float value) {
        addAction(new ReflectionAction(viewId, methodName, BaseReflectionAction.FLOAT, value));
    }

    /**
     * Call a method taking one float, a size in pixels, on a view in the layout for this
     * RemoteViews.
     *
     * The dimension will be resolved from the resources at the time the {@link RemoteViews} is
     * (re-)applied.
     *
     * Undefined resources will result in an exception, except 0 which will resolve to 0f.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param dimenResource The resource to resolve and pass as argument to the method.
     */
    public void setFloatDimen(@IdRes int viewId, @NonNull String methodName,
            @DimenRes int dimenResource) {
        addAction(new ResourceReflectionAction(viewId, methodName, BaseReflectionAction.FLOAT,
                ResourceReflectionAction.DIMEN_RESOURCE, dimenResource));
    }

    /**
     * Call a method taking one float, a size in pixels, on a view in the layout for this
     * RemoteViews.
     *
     * The dimension will be resolved from the resources at the time the {@link RemoteViews} is
     * (re-)applied.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The value of the dimension.
     * @param unit The unit in which the value is specified.
     */
    public void setFloatDimen(@IdRes int viewId, @NonNull String methodName,
            float value, @ComplexDimensionUnit int unit) {
        addAction(
                new ComplexUnitDimensionReflectionAction(viewId, methodName, ReflectionAction.FLOAT,
                        value, unit));
    }

    /**
     * Call a method taking one float, a size in pixels, on a view in the layout for this
     * RemoteViews.
     *
     * The dimension will be resolved from the theme attribute at the time the {@link RemoteViews}
     * is (re-)applied.
     *
     * Unresolvable attributes will result in an exception, except 0 which will resolve to 0f.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param dimenAttr The attribute to resolve and pass as argument to the method.
     */
    public void setFloatDimenAttr(@IdRes int viewId, @NonNull String methodName,
            @AttrRes int dimenAttr) {
        addAction(new AttributeReflectionAction(viewId, methodName, BaseReflectionAction.FLOAT,
                ResourceReflectionAction.DIMEN_RESOURCE, dimenAttr));
    }

    /**
     * Call a method taking one double on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The value to pass to the method.
     */
    public void setDouble(@IdRes int viewId, String methodName, double value) {
        addAction(new ReflectionAction(viewId, methodName, BaseReflectionAction.DOUBLE, value));
    }

    /**
     * Call a method taking one char on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The value to pass to the method.
     */
    public void setChar(@IdRes int viewId, String methodName, char value) {
        addAction(new ReflectionAction(viewId, methodName, BaseReflectionAction.CHAR, value));
    }

    /**
     * Call a method taking one String on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The value to pass to the method.
     */
    public void setString(@IdRes int viewId, String methodName, String value) {
        addAction(new ReflectionAction(viewId, methodName, BaseReflectionAction.STRING, value));
    }

    /**
     * Call a method taking one CharSequence on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The value to pass to the method.
     */
    public void setCharSequence(@IdRes int viewId, String methodName, CharSequence value) {
        addAction(new ReflectionAction(viewId, methodName, BaseReflectionAction.CHAR_SEQUENCE,
                value));
    }

    /**
     * Call a method taking one CharSequence on a view in the layout for this RemoteViews.
     *
     * The CharSequence will be resolved from the resources at the time the {@link RemoteViews} is
     * (re-)applied.
     *
     * Undefined resources will result in an exception, except 0 which will resolve to null.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param stringResource The resource to resolve and pass as argument to the method.
     */
    public void setCharSequence(@IdRes int viewId, @NonNull String methodName,
            @StringRes int stringResource) {
        addAction(
                new ResourceReflectionAction(viewId, methodName, BaseReflectionAction.CHAR_SEQUENCE,
                        ResourceReflectionAction.STRING_RESOURCE, stringResource));
    }

    /**
     * Call a method taking one CharSequence on a view in the layout for this RemoteViews.
     *
     * The CharSequence will be resolved from the theme attribute at the time the
     * {@link RemoteViews} is (re-)applied.
     *
     * Unresolvable attributes will result in an exception, except 0 which will resolve to null.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param stringAttribute The attribute to resolve and pass as argument to the method.
     */
    public void setCharSequenceAttr(@IdRes int viewId, @NonNull String methodName,
            @AttrRes int stringAttribute) {
        addAction(
                new AttributeReflectionAction(viewId, methodName,
                        BaseReflectionAction.CHAR_SEQUENCE,
                        AttributeReflectionAction.STRING_RESOURCE, stringAttribute));
    }

    /**
     * Call a method taking one Uri on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The value to pass to the method.
     */
    public void setUri(@IdRes int viewId, String methodName, Uri value) {
        if (value != null) {
            // Resolve any filesystem path before sending remotely
            value = value.getCanonicalUri();
            if (StrictMode.vmFileUriExposureEnabled()) {
                value.checkFileUriExposed("RemoteViews.setUri()");
            }
        }
        addAction(new ReflectionAction(viewId, methodName, BaseReflectionAction.URI, value));
    }

    /**
     * Call a method taking one Bitmap on a view in the layout for this RemoteViews.
     * @more
     * <p class="note">The bitmap will be flattened into the parcel if this object is
     * sent across processes, so it may end up using a lot of memory, and may be fairly slow.</p>
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The value to pass to the method.
     */
    public void setBitmap(@IdRes int viewId, String methodName, Bitmap value) {
        addAction(new BitmapReflectionAction(viewId, methodName, value));
    }

    /**
     * Call a method taking one BlendMode on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The value to pass to the method.
     */
    public void setBlendMode(@IdRes int viewId, @NonNull String methodName,
            @Nullable BlendMode value) {
        addAction(new ReflectionAction(viewId, methodName, BaseReflectionAction.BLEND_MODE, value));
    }

    /**
     * Call a method taking one Bundle on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The value to pass to the method.
     */
    public void setBundle(@IdRes int viewId, String methodName, Bundle value) {
        addAction(new ReflectionAction(viewId, methodName, BaseReflectionAction.BUNDLE, value));
    }

    /**
     * Call a method taking one Intent on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The {@link android.content.Intent} to pass the method.
     */
    public void setIntent(@IdRes int viewId, String methodName, Intent value) {
        addAction(new ReflectionAction(viewId, methodName, BaseReflectionAction.INTENT, value));
    }

    /**
     * Call a method taking one Icon on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param value The {@link android.graphics.drawable.Icon} to pass the method.
     */
    public void setIcon(@IdRes int viewId, String methodName, Icon value) {
        addAction(new ReflectionAction(viewId, methodName, BaseReflectionAction.ICON, value));
    }

    /**
     * Call a method taking one Icon on a view in the layout for this RemoteViews.
     *
     * @param viewId The id of the view on which to call the method.
     * @param methodName The name of the method to call.
     * @param notNight The value to pass to the method when the view's configuration is set to
     *                 {@link Configuration#UI_MODE_NIGHT_NO}
     * @param night The value to pass to the method when the view's configuration is set to
     *                 {@link Configuration#UI_MODE_NIGHT_YES}
     */
    public void setIcon(
            @IdRes int viewId,
            @NonNull String methodName,
            @Nullable Icon notNight,
            @Nullable Icon night) {
        addAction(
                new NightModeReflectionAction(
                        viewId,
                        methodName,
                        BaseReflectionAction.ICON,
                        notNight,
                        night));
    }

    /**
     * Equivalent to calling View.setContentDescription(CharSequence).
     *
     * @param viewId The id of the view whose content description should change.
     * @param contentDescription The new content description for the view.
     */
    public void setContentDescription(@IdRes int viewId, CharSequence contentDescription) {
        setCharSequence(viewId, "setContentDescription", contentDescription);
    }

    /**
     * Equivalent to calling {@link android.view.View#setAccessibilityTraversalBefore(int)}.
     *
     * @param viewId The id of the view whose before view in accessibility traversal to set.
     * @param nextId The id of the next in the accessibility traversal.
     **/
    public void setAccessibilityTraversalBefore(@IdRes int viewId, @IdRes int nextId) {
        setInt(viewId, "setAccessibilityTraversalBefore", nextId);
    }

    /**
     * Equivalent to calling {@link android.view.View#setAccessibilityTraversalAfter(int)}.
     *
     * @param viewId The id of the view whose after view in accessibility traversal to set.
     * @param nextId The id of the next in the accessibility traversal.
     **/
    public void setAccessibilityTraversalAfter(@IdRes int viewId, @IdRes int nextId) {
        setInt(viewId, "setAccessibilityTraversalAfter", nextId);
    }

    /**
     * Equivalent to calling {@link View#setLabelFor(int)}.
     *
     * @param viewId The id of the view whose property to set.
     * @param labeledId The id of a view for which this view serves as a label.
     */
    public void setLabelFor(@IdRes int viewId, @IdRes int labeledId) {
        setInt(viewId, "setLabelFor", labeledId);
    }

    /**
     * Equivalent to calling {@link android.widget.CompoundButton#setChecked(boolean)}.
     *
     * @param viewId The id of the view whose property to set.
     * @param checked true to check the button, false to uncheck it.
     */
    public void setCompoundButtonChecked(@IdRes int viewId, boolean checked) {
        addAction(new SetCompoundButtonCheckedAction(viewId, checked));
    }

    /**
     * Equivalent to calling {@link android.widget.RadioGroup#check(int)}.
     *
     * @param viewId The id of the view whose property to set.
     * @param checkedId The unique id of the radio button to select in the group.
     */
    public void setRadioGroupChecked(@IdRes int viewId, @IdRes int checkedId) {
        addAction(new SetRadioGroupCheckedAction(viewId, checkedId));
    }

    /**
     * Provides an alternate layout ID, which can be used to inflate this view. This layout will be
     * used by the host when the widgets displayed on a light-background where foreground elements
     * and text can safely draw using a dark color without any additional background protection.
     */
    public void setLightBackgroundLayoutId(@LayoutRes int layoutId) {
        mLightBackgroundLayoutId = layoutId;
    }

    /**
     * If this view supports dark text versions, creates a copy representing that version,
     * otherwise returns itself.
     * @hide
     */
    public RemoteViews getDarkTextViews() {
        if (hasFlags(FLAG_USE_LIGHT_BACKGROUND_LAYOUT)) {
            return this;
        }

        try {
            addFlags(FLAG_USE_LIGHT_BACKGROUND_LAYOUT);
            return new RemoteViews(this);
        } finally {
            mApplyFlags &= ~FLAG_USE_LIGHT_BACKGROUND_LAYOUT;
        }
    }

    private boolean hasDrawInstructions() {
        return mHasDrawInstructions;
    }

    private RemoteViews getRemoteViewsToApply(Context context) {
        if (hasLandscapeAndPortraitLayouts()) {
            int orientation = context.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                return mLandscape;
            }
            return mPortrait;
        }
        if (hasSizedRemoteViews()) {
            return findSmallestRemoteView();
        }
        return this;
    }

    /**
     * Returns the square distance between two points.
     *
     * This is particularly useful when we only care about the ordering of the distances.
     */
    private static float squareDistance(SizeF p1, SizeF p2) {
        float dx = p1.getWidth() - p2.getWidth();
        float dy = p1.getHeight() - p2.getHeight();
        return dx * dx + dy * dy;
    }

    /**
     * Returns whether the layout fits in the space available to the widget.
     *
     * A layout fits on a widget if the widget size is known (i.e. not null) and both dimensions
     * are smaller than the ones of the widget, adding some padding to account for rounding errors.
     */
    private static boolean fitsIn(SizeF sizeLayout, @Nullable SizeF sizeWidget) {
        return sizeWidget != null && (Math.ceil(sizeWidget.getWidth()) + 1 > sizeLayout.getWidth())
                && (Math.ceil(sizeWidget.getHeight()) + 1 > sizeLayout.getHeight());
    }

    private RemoteViews findBestFitLayout(@NonNull SizeF widgetSize) {
        // Find the better remote view
        RemoteViews bestFit = null;
        float bestSqDist = Float.MAX_VALUE;
        for (RemoteViews layout : mSizedRemoteViews) {
            SizeF layoutSize = layout.getIdealSize();
            if (layoutSize == null) {
                throw new IllegalStateException("Expected RemoteViews to have ideal size");
            }

            if (fitsIn(layoutSize, widgetSize)) {
                if (bestFit == null) {
                    bestFit = layout;
                    bestSqDist = squareDistance(layoutSize, widgetSize);
                } else {
                    float newSqDist = squareDistance(layoutSize, widgetSize);
                    if (newSqDist < bestSqDist) {
                        bestFit = layout;
                        bestSqDist = newSqDist;
                    }
                }
            }
        }
        if (bestFit == null) {
            Log.w(LOG_TAG, "Could not find a RemoteViews fitting the current size: " + widgetSize);
            return findSmallestRemoteView();
        }
        return bestFit;
    }

    /**
     * Returns the most appropriate {@link RemoteViews} given the context and, if not null, the
     * size of the widget.
     *
     * If {@link RemoteViews#hasSizedRemoteViews()} returns true, the most appropriate view is
     * the one that fits in the widget (according to {@link RemoteViews#fitsIn}) and has the
     * diagonal the most similar to the widget. If no layout fits or the size of the widget is
     * not specified, the one with the smallest area will be chosen.
     *
     * @hide
     */
    public RemoteViews getRemoteViewsToApply(@NonNull Context context,
            @Nullable SizeF widgetSize) {
        if (!hasSizedRemoteViews() || widgetSize == null) {
            // If there isn't multiple remote views, fall back on the previous methods.
            return getRemoteViewsToApply(context);
        }
        return findBestFitLayout(widgetSize);
    }

    /**
     * Checks whether the change of size will lead to using a different {@link RemoteViews}.
     *
     * @hide
     */
    @Nullable
    public RemoteViews getRemoteViewsToApplyIfDifferent(@Nullable SizeF oldSize,
            @NonNull SizeF newSize) {
        if (!hasSizedRemoteViews()) {
            return null;
        }
        RemoteViews oldBestFit = oldSize == null ? findSmallestRemoteView() : findBestFitLayout(
                oldSize);
        RemoteViews newBestFit = findBestFitLayout(newSize);
        if (oldBestFit != newBestFit) {
            return newBestFit;
        }
        return null;
    }


    /**
     * Inflates the view hierarchy represented by this object and applies
     * all of the actions.
     *
     * <p><strong>Caller beware: this may throw</strong>
     *
     * @param context Default context to use
     * @param parent Parent that the resulting view hierarchy will be attached to. This method
     * does <strong>not</strong> attach the hierarchy. The caller should do so when appropriate.
     * @return The inflated view hierarchy
     */
    public View apply(Context context, ViewGroup parent) {
        return apply(context, parent, null);
    }

    /** @hide */
    public View apply(Context context, ViewGroup parent, InteractionHandler handler) {
        return apply(context, parent, handler, null);
    }

    /** @hide */
    public View apply(@NonNull Context context, @NonNull ViewGroup parent,
            @Nullable InteractionHandler handler, @Nullable SizeF size) {
        return apply(context, parent, size, new ActionApplyParams()
                .withInteractionHandler(handler));
    }

    /** @hide */
    public View applyWithTheme(@NonNull Context context, @NonNull ViewGroup parent,
            @Nullable InteractionHandler handler, @StyleRes int applyThemeResId) {
        return apply(context, parent, null, new ActionApplyParams()
                .withInteractionHandler(handler)
                .withThemeResId(applyThemeResId));
    }

    /** @hide */
    public View apply(Context context, ViewGroup parent, InteractionHandler handler,
            @Nullable SizeF size, @Nullable ColorResources colorResources) {
        return apply(context, parent, size, new ActionApplyParams()
                .withInteractionHandler(handler)
                .withColorResources(colorResources));
    }

    /** @hide **/
    public View apply(Context context, ViewGroup parent, @Nullable SizeF size,
            ActionApplyParams params) {
        return apply(context, parent, parent, size, params);
    }

    private View apply(Context context, ViewGroup directParent, ViewGroup rootParent,
            @Nullable SizeF size, ActionApplyParams params) {
        RemoteViews rvToApply = getRemoteViewsToApply(context, size);
        View result = inflateView(context, rvToApply, directParent,
                params.applyThemeResId, params.colorResources);
        rvToApply.performApply(result, rootParent, params);
        return result;
    }

    private View inflateView(Context context, RemoteViews rv, @Nullable ViewGroup parent,
            @StyleRes int applyThemeResId, @Nullable ColorResources colorResources) {
        try {
            Trace.beginSection(rv.hasDrawInstructions()
                    ? "RemoteViews#inflateViewWithDrawInstructions"
                    : "RemoteViews#inflateView");
            return inflateViewInternal(context, rv, parent, applyThemeResId, colorResources);
        } finally {
            Trace.endSection();
        }
    }

    private View inflateViewInternal(Context context, RemoteViews rv, @Nullable ViewGroup parent,
            @StyleRes int applyThemeResId, @Nullable ColorResources colorResources) {
        // RemoteViews may be built by an application installed in another
        // user. So build a context that loads resources from that user but
        // still returns the current users userId so settings like data / time formats
        // are loaded without requiring cross user persmissions.
        final Context contextForResources =
                getContextForResourcesEnsuringCorrectCachedApkPaths(context);
        if (colorResources != null) {
            colorResources.apply(contextForResources);
        }
        Context inflationContext = new RemoteViewsContextWrapper(context, contextForResources);

        // If mApplyThemeResId is not given, Theme.DeviceDefault will be used.
        if (applyThemeResId != 0) {
            inflationContext = new ContextThemeWrapper(inflationContext, applyThemeResId);
        }
        View v;
        // If the RemoteViews contains draw instructions, just use it instead.
        if (rv.hasDrawInstructions()) {
            final RemoteComposePlayer player = new RemoteComposePlayer(inflationContext);
            player.setDebug(Build.IS_USERDEBUG || Build.IS_ENG ? 1 : 0);
            v = player;
        } else {
            LayoutInflater inflater = LayoutInflater.from(context);

            // Clone inflater so we load resources from correct context and
            // we don't add a filter to the static version returned by getSystemService.
            inflater = inflater.cloneInContext(inflationContext);
            inflater.setFilter(shouldUseStaticFilter() ? INFLATER_FILTER : this);
            if (mLayoutInflaterFactory2 != null) {
                inflater.setFactory2(mLayoutInflaterFactory2);
            }
            v = inflater.inflate(rv.getLayoutId(), parent, false);
        }
        if (mViewId != View.NO_ID) {
            v.setId(mViewId);
            v.setTagInternal(R.id.remote_views_override_id, mViewId);
        }
        v.setTagInternal(R.id.widget_frame, rv.getLayoutId());
        return v;
    }

    /**
     * A static filter is much lighter than RemoteViews itself. It's optimized here only for
     * RemoteVies class. Subclasses should always override this and return true if not overriding
     * {@link this#onLoadClass(Class)}.
     *
     * @hide
     */
    protected boolean shouldUseStaticFilter() {
        return this.getClass().equals(RemoteViews.class);
    }

    /**
     * Implement this interface to receive a callback when
     * {@link #applyAsync} or {@link #reapplyAsync} is finished.
     * @hide
     */
    public interface OnViewAppliedListener {
        /**
         * Callback when the RemoteView has finished inflating,
         * but no actions have been applied yet.
         */
        default void onViewInflated(View v) {}

        void onViewApplied(View v);

        void onError(Exception e);
    }

    /**
     * Applies the views asynchronously, moving as much of the task on the background
     * thread as possible.
     *
     * @see #apply(Context, ViewGroup)
     * @param context Default context to use
     * @param parent Parent that the resulting view hierarchy will be attached to. This method
     * does <strong>not</strong> attach the hierarchy. The caller should do so when appropriate.
     * @param listener the callback to run when all actions have been applied. May be null.
     * @param executor The executor to use. If null {@link AsyncTask#THREAD_POOL_EXECUTOR} is used.
     * @return CancellationSignal
     * @hide
     */
    public CancellationSignal applyAsync(
            Context context, ViewGroup parent, Executor executor, OnViewAppliedListener listener) {
        return applyAsync(context, parent, executor, listener, null /* handler */);
    }

    /** @hide */
    public CancellationSignal applyAsync(Context context, ViewGroup parent,
            Executor executor, OnViewAppliedListener listener, InteractionHandler handler) {
        return applyAsync(context, parent, executor, listener, handler, null /* size */);
    }

    /** @hide */
    public CancellationSignal applyAsync(Context context, ViewGroup parent,
            Executor executor, OnViewAppliedListener listener, InteractionHandler handler,
            SizeF size) {
        return applyAsync(context, parent, executor, listener, handler, size,
                null /* themeColors */);
    }

    /** @hide */
    public CancellationSignal applyAsync(Context context, ViewGroup parent, Executor executor,
            OnViewAppliedListener listener, InteractionHandler handler, SizeF size,
            ColorResources colorResources) {

        ActionApplyParams params = new ActionApplyParams()
                .withInteractionHandler(handler)
                .withColorResources(colorResources)
                .withExecutor(executor);
        return new AsyncApplyTask(getRemoteViewsToApply(context, size), parent, context, listener,
                params, null /* result */, true /* topLevel */).startTaskOnExecutor(executor);
    }

    private AsyncApplyTask getInternalAsyncApplyTask(Context context, ViewGroup parent,
            OnViewAppliedListener listener, ActionApplyParams params, SizeF size, View result) {
        return new AsyncApplyTask(getRemoteViewsToApply(context, size), parent, context, listener,
                params, result, false /* topLevel */);
    }

    private class AsyncApplyTask extends AsyncTask<Void, Void, ViewTree>
            implements CancellationSignal.OnCancelListener {
        final CancellationSignal mCancelSignal = new CancellationSignal();
        final RemoteViews mRV;
        final ViewGroup mParent;
        final Context mContext;
        final OnViewAppliedListener mListener;
        final ActionApplyParams mApplyParams;

        /**
         * Whether the remote view is the top-level one (i.e. not within an action).
         *
         * This is only used if the result is specified (i.e. the view is being recycled).
         */
        final boolean mTopLevel;

        private View mResult;
        private ViewTree mTree;
        private Action[] mActions;
        private Exception mError;

        private AsyncApplyTask(
                RemoteViews rv, ViewGroup parent, Context context, OnViewAppliedListener listener,
                ActionApplyParams applyParams, View result, boolean topLevel) {
            mRV = rv;
            mParent = parent;
            mContext = context;
            mListener = listener;
            mTopLevel = topLevel;
            mApplyParams = applyParams;
            mResult = result;
        }

        @Nullable
        @Override
        protected ViewTree doInBackground(Void... params) {
            try {
                if (mResult == null) {
                    mResult = inflateView(mContext, mRV, mParent, 0, mApplyParams.colorResources);
                }

                mTree = new ViewTree(mResult);

                if (mRV.mActions != null) {
                    int count = mRV.mActions.size();
                    mActions = new Action[count];
                    try {
                        Trace.beginSection(hasDrawInstructions()
                                ? "RemoteViews#initActionAsyncWithDrawInstructions"
                                : "RemoteViews#initActionAsync");
                        for (int i = 0; i < count && !isCancelled(); i++) {
                            // TODO: check if isCancelled in nested views.
                            mActions[i] = mRV.mActions.get(i)
                                    .initActionAsync(mTree, mParent, mApplyParams);
                        }
                    } finally {
                        Trace.endSection();
                    }
                } else {
                    mActions = null;
                }
                return mTree;
            } catch (Exception e) {
                mError = e;
                return null;
            }
        }

        @Override
        protected void onPostExecute(ViewTree viewTree) {
            mCancelSignal.setOnCancelListener(null);
            if (mError == null) {
                if (mListener != null) {
                    mListener.onViewInflated(viewTree.mRoot);
                }

                try {
                    if (mActions != null) {
                        ActionApplyParams applyParams = mApplyParams.clone();
                        if (applyParams.handler == null) {
                            applyParams.handler = DEFAULT_INTERACTION_HANDLER;
                        }
                        try {
                            Trace.beginSection(hasDrawInstructions()
                                    ? "RemoteViews#applyActionsAsyncWithDrawInstructions"
                                    : "RemoteViews#applyActionsAsync");
                            for (Action a : mActions) {
                                a.apply(viewTree.mRoot, mParent, applyParams);
                            }
                        } finally {
                            Trace.endSection();
                        }
                    }
                    // If the parent of the view is has is a root, resolve the recycling.
                    if (mTopLevel && mResult instanceof ViewGroup) {
                        finalizeViewRecycling((ViewGroup) mResult);
                    }
                } catch (Exception e) {
                    mError = e;
                }
            }

            if (mListener != null) {
                if (mError != null) {
                    mListener.onError(mError);
                } else {
                    mListener.onViewApplied(viewTree.mRoot);
                }
            } else if (mError != null) {
                if (mError instanceof ActionException) {
                    throw (ActionException) mError;
                } else {
                    throw new ActionException(mError);
                }
            }
        }

        @Override
        public void onCancel() {
            cancel(true);
        }

        private CancellationSignal startTaskOnExecutor(Executor executor) {
            mCancelSignal.setOnCancelListener(this);
            executeOnExecutor(executor == null ? AsyncTask.THREAD_POOL_EXECUTOR : executor);
            return mCancelSignal;
        }
    }

    /**
     * Applies all of the actions to the provided view.
     *
     * <p><strong>Caller beware: this may throw</strong>
     *
     * @param v The view to apply the actions to.  This should be the result of
     * the {@link #apply(Context,ViewGroup)} call.
     */
    public void reapply(Context context, View v) {
        reapply(context, v, null /* size */, new ActionApplyParams());
    }

    /** @hide */
    public void reapply(Context context, View v, InteractionHandler handler) {
        reapply(context, v, null /* size */,
                new ActionApplyParams().withInteractionHandler(handler));
    }

    /** @hide */
    public void reapply(Context context, View v, InteractionHandler handler, SizeF size,
            ColorResources colorResources) {
        reapply(context, v, size, new ActionApplyParams()
                .withInteractionHandler(handler).withColorResources(colorResources));
    }

    /** @hide */
    public void reapply(Context context, View v, @Nullable SizeF size, ActionApplyParams params) {
        reapply(context, v, (ViewGroup) v.getParent(), size, params, true);
    }

    private void reapplyNestedViews(Context context, View v, ViewGroup rootParent,
            ActionApplyParams params) {
        reapply(context, v, rootParent, null, params, false);
    }

    // Note: topLevel should be true only for calls on the topLevel RemoteViews, internal calls
    // should set it to false.
    private void reapply(Context context, View v, ViewGroup rootParent,
            @Nullable SizeF size, ActionApplyParams params, boolean topLevel) {
        RemoteViews rvToApply = getRemoteViewsToReapply(context, v, size);
        rvToApply.performApply(v, rootParent, params);

        // If the parent of the view is has is a root, resolve the recycling.
        if (topLevel && v instanceof ViewGroup) {
            finalizeViewRecycling((ViewGroup) v);
        }
    }

    /** @hide */
    public boolean canRecycleView(@Nullable View v) {
        if (v == null || hasDrawInstructions()) {
            return false;
        }
        Integer previousLayoutId = (Integer) v.getTag(R.id.widget_frame);
        if (previousLayoutId == null) {
            return false;
        }
        Integer overrideIdTag = (Integer) v.getTag(R.id.remote_views_override_id);
        int overrideId = overrideIdTag == null ? View.NO_ID : overrideIdTag;
        // If mViewId is View.NO_ID, we only recycle if overrideId is also View.NO_ID.
        // Otherwise, it might be that, on a previous iteration, the view's ID was set to
        // something else, and it should now be reset to the ID defined in the XML layout file,
        // whatever it is.
        return previousLayoutId == getLayoutId() && mViewId == overrideId;
    }

    /**
     * Returns the RemoteViews that should be used in the reapply operation.
     *
     * If the current RemoteViews has multiple layout, this will select the correct one.
     *
     * @throws RuntimeException If the current RemoteViews should not be reapplied onto the provided
     * View.
     */
    private RemoteViews getRemoteViewsToReapply(Context context, View v, @Nullable SizeF size) {
        RemoteViews rvToApply = getRemoteViewsToApply(context, size);

        // In the case that a view has this RemoteViews applied in one orientation or size, is
        // persisted across change, and has the RemoteViews re-applied in a different situation
        // (orientation or size), we throw an exception, since the layouts may be completely
        // unrelated.
        // If the ViewID has been changed on the view, or is changed by the RemoteViews, we also
        // may throw an exception, as the RemoteViews will probably not apply properly.
        // However, we need to let potentially unrelated RemoteViews apply, as this lack of testing
        // is already used in production code in some apps.
        if (hasMultipleLayouts()
                || rvToApply.mViewId != View.NO_ID
                || v.getTag(R.id.remote_views_override_id) != null) {
            if (!rvToApply.canRecycleView(v)) {
                throw new RuntimeException("Attempting to re-apply RemoteViews to a view that" +
                        " that does not share the same root layout id.");
            }
        }

        return rvToApply;
    }

    /**
     * Applies all the actions to the provided view, moving as much of the task on the background
     * thread as possible.
     *
     * @see #reapply(Context, View)
     * @param context Default context to use
     * @param v The view to apply the actions to.  This should be the result of
     * the {@link #apply(Context,ViewGroup)} call.
     * @param listener the callback to run when all actions have been applied. May be null.
     * @param executor The executor to use. If null {@link AsyncTask#THREAD_POOL_EXECUTOR} is used
     * @return CancellationSignal
     * @hide
     */
    public CancellationSignal reapplyAsync(Context context, View v, Executor executor,
            OnViewAppliedListener listener) {
        return reapplyAsync(context, v, executor, listener, null);
    }

    /** @hide */
    public CancellationSignal reapplyAsync(Context context, View v, Executor executor,
            OnViewAppliedListener listener, InteractionHandler handler) {
        return reapplyAsync(context, v, executor, listener, handler, null, null);
    }

    /** @hide */
    public CancellationSignal reapplyAsync(Context context, View v, Executor executor,
            OnViewAppliedListener listener, InteractionHandler handler, SizeF size,
            ColorResources colorResources) {
        RemoteViews rvToApply = getRemoteViewsToReapply(context, v, size);

        ActionApplyParams params = new ActionApplyParams()
                .withColorResources(colorResources)
                .withInteractionHandler(handler)
                .withExecutor(executor);

        return new AsyncApplyTask(rvToApply, (ViewGroup) v.getParent(),
                context, listener, params, v, true /* topLevel */)
                .startTaskOnExecutor(executor);
    }

    private void performApply(View v, ViewGroup parent, ActionApplyParams params) {
        params = params.clone();
        if (params.handler == null) {
            params.handler = DEFAULT_INTERACTION_HANDLER;
        }
        if (v instanceof RemoteComposePlayer player) {
            player.setTheme(v.getResources().getConfiguration().isNightModeActive()
                    ? Theme.DARK : Theme.LIGHT);
        }
        if (mActions != null) {
            final int count = mActions.size();
            try {
                Trace.beginSection(hasDrawInstructions()
                        ? "RemoteViews#applyActionsWithDrawInstructions"
                        : "RemoteViews#applyActions");
                for (int i = 0; i < count; i++) {
                    mActions.get(i).apply(v, parent, params);
                }
            } finally {
                Trace.endSection();
            }
        }
    }

    /**
     * Returns true if the RemoteViews contains potentially costly operations and should be
     * applied asynchronously.
     *
     * @hide
     */
    public boolean prefersAsyncApply() {
        if (mActions != null) {
            final int count = mActions.size();
            for (int i = 0; i < count; i++) {
                if (mActions.get(i).prefersAsyncApply()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** @hide */
    public void updateAppInfo(@NonNull ApplicationInfo info) {
        ApplicationInfo existing = mApplicationInfoCache.get(info);
        if (existing != null && !existing.sourceDir.equals(info.sourceDir)) {
            // Overlay paths are generated against a particular version of an application.
            // The overlays paths of a newly upgraded application are incompatible with the
            // old version of the application.
            return;
        }

        // If we can update to the new AppInfo, put it in the cache and propagate the change
        // throughout the hierarchy.
        mApplicationInfoCache.put(info);
        configureDescendantsAsChildren();
    }

    private Context getContextForResourcesEnsuringCorrectCachedApkPaths(Context context) {
        if (mApplication != null) {
            if (context.getUserId() == UserHandle.getUserId(mApplication.uid)
                    && context.getPackageName().equals(mApplication.packageName)) {
                return context;
            }
            try {
                ApplicationInfo sanitizedApplication = mApplication;
                try {
                    // Use PackageManager as the source of truth for application information, rather
                    // than the parceled ApplicationInfo provided by the app.
                    sanitizedApplication = context.getPackageManager().getApplicationInfoAsUser(
                        mApplication.packageName, 0, UserHandle.getUserId(mApplication.uid));
                } catch(SecurityException se) {
                    Log.d(LOG_TAG, "Unable to fetch appInfo for " + mApplication.packageName);
                }

                Context applicationContext = context.createApplicationContext(
                        sanitizedApplication,
                        Context.CONTEXT_RESTRICTED);
                // Get the correct apk paths while maintaining the current context's configuration.
                return applicationContext.createConfigurationContext(
                        context.getResources().getConfiguration());
            } catch (NameNotFoundException e) {
                Log.e(LOG_TAG, "Package name " + mApplication.packageName + " not found");
            }
        }

        return context;
    }

    @NonNull
    private SparseArray<PendingIntent> getPendingIntentTemplate() {
        if (mPendingIntentTemplate == null) {
            mPendingIntentTemplate = new SparseArray<>();
        }
        return mPendingIntentTemplate;
    }

    @NonNull
    private SparseArray<Intent> getFillInIntent() {
        if (mFillInIntent == null) {
            mFillInIntent = new SparseArray<>();
        }
        return mFillInIntent;
    }

    private void tryAddRemoteResponse(final int viewId) {
        final PendingIntent pendingIntent = getPendingIntentTemplate().get(viewId);
        final Intent intent = getFillInIntent().get(viewId);
        if (pendingIntent != null && intent != null) {
            addAction(new SetOnClickResponse(viewId,
                    RemoteResponse.fromPendingIntentTemplateAndFillInIntent(
                            pendingIntent, intent)));
        }
    }

    /**
     * Utility class to hold all the options when applying the remote views
     * @hide
     */
    public class ActionApplyParams {
        public InteractionHandler handler;
        public ColorResources colorResources;
        public Executor executor;
        @StyleRes public int applyThemeResId;

        @Override
        public ActionApplyParams clone() {
            return new ActionApplyParams()
                    .withInteractionHandler(handler)
                    .withColorResources(colorResources)
                    .withExecutor(executor)
                    .withThemeResId(applyThemeResId);
        }

        public ActionApplyParams withInteractionHandler(InteractionHandler handler) {
            this.handler = handler;
            return this;
        }

        public ActionApplyParams withColorResources(ColorResources colorResources) {
            this.colorResources = colorResources;
            return this;
        }

        public ActionApplyParams withThemeResId(@StyleRes int themeResId) {
            this.applyThemeResId = themeResId;
            return this;
        }

        public ActionApplyParams withExecutor(Executor executor) {
            this.executor = executor;
            return this;
        }
    }

    /**
     * Object allowing the modification of a context to overload the system's dynamic colors.
     *
     * @hide
     */
    public static final class ColorResources {
        // Set of valid colors resources.
        private static final int FIRST_RESOURCE_COLOR_ID = android.R.color.system_neutral1_0;
        private static final int LAST_RESOURCE_COLOR_ID =
            android.R.color.system_error_1000;
        // Size, in bytes, of an entry in the array of colors in an ARSC file.
        private static final int ARSC_ENTRY_SIZE = 16;

        private static final String OVERLAY_NAME = "remote_views_color_resources";
        private static final String OVERLAY_TARGET_PACKAGE_NAME = "android";

        private final ResourcesLoader mLoader;
        private final SparseIntArray mColorMapping;

        private ColorResources(ResourcesLoader loader, SparseIntArray colorMapping) {
            mLoader = loader;
            mColorMapping = colorMapping;
        }

        /**
         * Apply the color resources to the given context.
         *
         * No resource resolution must have be done on the context given to that method.
         */
        public void apply(Context context) {
            context.getResources().addLoaders(mLoader);
        }

        public SparseIntArray getColorMapping() {
            return mColorMapping;
        }

        private static ByteArrayOutputStream readFileContent(InputStream input) throws IOException {
            ByteArrayOutputStream content = new ByteArrayOutputStream(2048);
            byte[] buffer = new byte[4096];
            while (input.available() > 0) {
                int read = input.read(buffer);
                content.write(buffer, 0, read);
            }
            return content;
        }

        /**
         * Creates the compiled resources content from the asset stored in the APK.
         *
         * The asset is a compiled resource with the correct resources name and correct ids, only
         * the values are incorrect. The last value is at the very end of the file. The resources
         * are in an array, the array's entries are 16 bytes each. We use this to work out the
         * location of all the positions of the various resources.
         */
        @Nullable
        private static byte[] createCompiledResourcesContent(Context context,
                SparseIntArray colorResources) throws IOException {
            byte[] content;
            try (InputStream input = context.getResources().openRawResource(
                    com.android.internal.R.raw.remote_views_color_resources)) {
                ByteArrayOutputStream rawContent = readFileContent(input);
                content = rawContent.toByteArray();
            }
            int valuesOffset =
                    content.length - (LAST_RESOURCE_COLOR_ID & 0xffff) * ARSC_ENTRY_SIZE - 4;
            if (valuesOffset < 0) {
                Log.e(LOG_TAG, "ARSC file for theme colors is invalid.");
                return null;
            }
            for (int colorRes = FIRST_RESOURCE_COLOR_ID; colorRes <= LAST_RESOURCE_COLOR_ID;
                    colorRes++) {
                // The last 2 bytes are the index in the color array.
                int index = colorRes & 0xffff;
                int offset = valuesOffset + index * ARSC_ENTRY_SIZE;
                int value = colorResources.get(colorRes, context.getColor(colorRes));
                // Write the 32 bit integer in little endian
                for (int b = 0; b < 4; b++) {
                    content[offset + b] = (byte) (value & 0xff);
                    value >>= 8;
                }
            }
            return content;
        }

        /**
         *  Adds a resource loader for theme colors to the given context. The loader is created
         *  based on resource files created at build time.
         *
         * <p>Only colors from {@link android.R.color#system_accent1_0} to
         * {@link android.R.color#system_error_1000} can be overloaded.</p>
         *
         * @param context Context of the view hosting the widget.
         * @param colorMapping Mapping of resources to color values.
         *
         * @hide
         */
        @Nullable
        public static ColorResources create(Context context, SparseIntArray colorMapping) {
            try {
                byte[] contentBytes = createCompiledResourcesContent(context, colorMapping);
                if (contentBytes == null) {
                    return null;
                }
                FileDescriptor arscFile = null;
                try {
                    arscFile = Os.memfd_create("remote_views_theme_colors.arsc", 0 /* flags */);
                    // Note: This must not be closed through the OutputStream.
                    try (OutputStream pipeWriter = new FileOutputStream(arscFile)) {
                        pipeWriter.write(contentBytes);

                        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.dup(arscFile)) {
                            ResourcesLoader colorsLoader = new ResourcesLoader();
                            colorsLoader.addProvider(ResourcesProvider
                                    .loadFromTable(pfd, null /* assetsProvider */));
                            return new ColorResources(colorsLoader, colorMapping.clone());
                        }
                    }
                } finally {
                    if (arscFile != null) {
                        Os.close(arscFile);
                    }
                }
            } catch (Exception ex) {
                Log.e(LOG_TAG, "Failed to setup the context for theme colors", ex);
            }
            return null;
        }

        /**
         *  Adds a resource loader for theme colors to the given context. The loader is created
         *  using fabricated runtime resource overlay (FRRO).
         *
         *  <p>The created class can overlay any color resources, private or public, at runtime.</p>
         *
         * @param context Context of the view hosting the widget.
         * @param colorMapping Mapping of resources to color values.
         *
         * @hide
         */
        @FlaggedApi(FLAG_SELF_TARGETING_ANDROID_RESOURCE_FRRO)
        @Nullable
        public static ColorResources createWithOverlay(Context context,
                SparseIntArray colorMapping) {
            try {
                String owningPackage = context.getPackageName();
                FabricatedOverlay overlay = new FabricatedOverlay.Builder(owningPackage,
                        OVERLAY_NAME, OVERLAY_TARGET_PACKAGE_NAME).build();

                for (int i = 0; i < colorMapping.size(); i++) {
                    overlay.setResourceValue(
                            context.getResources().getResourceName(colorMapping.keyAt(i)),
                            TYPE_INT_COLOR_ARGB8, colorMapping.valueAt(i), null);
                }
                OverlayManager overlayManager = context.getSystemService(OverlayManager.class);
                OverlayManagerTransaction.Builder transaction =
                        new OverlayManagerTransaction.Builder()
                                .registerFabricatedOverlay(overlay)
                                .setSelfTargeting(true);
                overlayManager.commit(transaction.build());

                OverlayInfo overlayInfo =
                        overlayManager.getOverlayInfosForTarget(OVERLAY_TARGET_PACKAGE_NAME)
                                .stream()
                                .filter(info -> TextUtils.equals(info.overlayName, OVERLAY_NAME)
                                        && TextUtils.equals(info.packageName, owningPackage))
                                .findFirst()
                                .orElse(null);
                if (overlayInfo == null) {
                    Log.e(LOG_TAG, "Failed to get overlay info ", new Throwable());
                    return null;
                }
                ResourcesLoader colorsLoader = new ResourcesLoader();
                colorsLoader.addProvider(ResourcesProvider.loadOverlay(overlayInfo));
                return new ColorResources(colorsLoader, colorMapping.clone());
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to add theme color overlay into loader", e);
            }
            return null;
        }
    }

    /**
     * Returns the number of actions in this RemoteViews. Can be used as a sequence number.
     *
     * @hide
     */
    public int getSequenceNumber() {
        return (mActions == null) ? 0 : mActions.size();
    }

    /**
     * Used to restrict the views which can be inflated
     *
     * @see android.view.LayoutInflater.Filter#onLoadClass(java.lang.Class)
     * @deprecated Used by system to enforce safe inflation of {@link RemoteViews}. Apps should not
     * override this method. Changing of this method will NOT affect the process where RemoteViews
     * is rendered.
     */
    @Deprecated
    public boolean onLoadClass(Class clazz) {
        return clazz.isAnnotationPresent(RemoteView.class);
    }

    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writeToParcel(dest, flags, /* intentsToIgnore= */ null);
    }

    private void writeToParcel(Parcel dest, int flags,
            @Nullable SparseArray<Intent> intentsToIgnore) {
        boolean prevSquashingAllowed = dest.allowSquashing();

        if (!hasMultipleLayouts()) {
            dest.writeInt(MODE_NORMAL);
            // We only write the bitmap cache if we are the root RemoteViews, as this cache
            // is shared by all children.
            if (mIsRoot) {
                mBitmapCache.writeBitmapsToParcel(dest, flags);
                mCollectionCache.writeToParcel(dest, flags, intentsToIgnore);
            }
            dest.writeTypedObject(mApplication, flags);
            if (mIsRoot || mIdealSize == null) {
                dest.writeInt(0);
            } else {
                dest.writeInt(1);
                mIdealSize.writeToParcel(dest, flags);
            }
            dest.writeInt(mLayoutId);
            dest.writeInt(mViewId);
            dest.writeInt(mLightBackgroundLayoutId);
            writeActionsToParcel(dest, flags);
        } else if (hasSizedRemoteViews()) {
            dest.writeInt(MODE_HAS_SIZED_REMOTEVIEWS);
            if (mIsRoot) {
                mBitmapCache.writeBitmapsToParcel(dest, flags);
                mCollectionCache.writeToParcel(dest, flags, intentsToIgnore);
            }
            dest.writeInt(mSizedRemoteViews.size());
            for (RemoteViews view : mSizedRemoteViews) {
                view.writeToParcel(dest, flags);
            }
        } else {
            dest.writeInt(MODE_HAS_LANDSCAPE_AND_PORTRAIT);
            // We only write the bitmap cache if we are the root RemoteViews, as this cache
            // is shared by all children.
            if (mIsRoot) {
                mBitmapCache.writeBitmapsToParcel(dest, flags);
                mCollectionCache.writeToParcel(dest, flags, intentsToIgnore);
            }
            mLandscape.writeToParcel(dest, flags);
            // Both RemoteViews already share the same package and user
            mPortrait.writeToParcel(dest, flags);
        }
        dest.writeInt(mApplyFlags);
        dest.writeLong(mProviderInstanceId);
        dest.writeBoolean(mHasDrawInstructions);

        dest.restoreAllowSquashing(prevSquashingAllowed);
    }

    private void writeActionsToParcel(Parcel parcel, int flags) {
        int count;
        if (mActions != null) {
            count = mActions.size();
        } else {
            count = 0;
        }
        parcel.writeInt(count);
        for (int i = 0; i < count; i++) {
            Action a = mActions.get(i);
            parcel.writeInt(a.getActionTag());
            a.writeToParcel(parcel, flags);
        }
    }

    @Nullable
    private static ApplicationInfo getApplicationInfo(@Nullable String packageName, int userId) {
        if (packageName == null) {
            return null;
        }

        // Get the application for the passed in package and user.
        Application application = ActivityThread.currentApplication();
        if (application == null) {
            throw new IllegalStateException("Cannot create remote views out of an aplication.");
        }

        ApplicationInfo applicationInfo = application.getApplicationInfo();
        if (UserHandle.getUserId(applicationInfo.uid) != userId
                || !applicationInfo.packageName.equals(packageName)) {
            try {
                Context context = application.getBaseContext().createPackageContextAsUser(
                        packageName, 0, new UserHandle(userId));
                applicationInfo = context.getApplicationInfo();
            } catch (NameNotFoundException nnfe) {
                throw new IllegalArgumentException("No such package " + packageName);
            }
        }

        return applicationInfo;
    }

    /**
     * Returns true if the {@link #mApplication} is same as the provided info.
     *
     * @hide
     */
    public boolean hasSameAppInfo(ApplicationInfo info) {
        return mApplication == null || mApplication.packageName.equals(info.packageName)
                && mApplication.uid == info.uid;
    }

    /**
     * Parcelable.Creator that instantiates RemoteViews objects
     */
    @NonNull
    public static final Parcelable.Creator<RemoteViews> CREATOR =
            new Parcelable.Creator<RemoteViews>() {
                public RemoteViews createFromParcel(Parcel parcel) {
                    return new RemoteViews(parcel);
                }

                public RemoteViews[] newArray(int size) {
                    return new RemoteViews[size];
                }
            };

    /**
     * A representation of the view hierarchy. Only views which have a valid ID are added
     * and can be searched.
     */
    private static class ViewTree {
        private static final int INSERT_AT_END_INDEX = -1;
        private View mRoot;
        private ArrayList<ViewTree> mChildren;

        private ViewTree(View root) {
            mRoot = root;
        }

        public void createTree() {
            if (mChildren != null) {
                return;
            }

            mChildren = new ArrayList<>();
            if (mRoot instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) mRoot;
                int count = vg.getChildCount();
                for (int i = 0; i < count; i++) {
                    addViewChild(vg.getChildAt(i));
                }
            }
        }

        @Nullable
        public ViewTree findViewTreeById(@IdRes int id) {
            if (mRoot.getId() == id) {
                return this;
            }
            if (mChildren == null) {
                return null;
            }
            for (ViewTree tree : mChildren) {
                ViewTree result = tree.findViewTreeById(id);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }

        @Nullable
        public ViewTree findViewTreeParentOf(ViewTree child) {
            if (mChildren == null) {
                return null;
            }
            for (ViewTree tree : mChildren) {
                if (tree == child) {
                    return this;
                }
                ViewTree result = tree.findViewTreeParentOf(child);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }

        public void replaceView(View v) {
            mRoot = v;
            mChildren = null;
            createTree();
        }

        @Nullable
        public <T extends View> T findViewById(@IdRes int id) {
            if (mChildren == null) {
                return mRoot.findViewById(id);
            }
            ViewTree tree = findViewTreeById(id);
            return tree == null ? null : (T) tree.mRoot;
        }

        public void addChild(ViewTree child) {
            addChild(child, INSERT_AT_END_INDEX);
        }

        /**
         * Adds the given {@link ViewTree} as a child at the given index.
         *
         * @param index The position at which to add the child or -1 to add last.
         */
        public void addChild(ViewTree child, int index) {
            if (mChildren == null) {
                mChildren = new ArrayList<>();
            }
            child.createTree();

            if (index == INSERT_AT_END_INDEX) {
                mChildren.add(child);
                return;
            }

            mChildren.add(index, child);
        }

        public void removeChildren(int start, int count) {
            if (mChildren != null) {
                for (int i = 0; i < count; i++) {
                    mChildren.remove(start);
                }
            }
        }

        private void addViewChild(View v) {
            // ViewTree only contains Views which can be found using findViewById.
            // If isRootNamespace is true, this view is skipped.
            // @see ViewGroup#findViewTraversal(int)
            if (v.isRootNamespace()) {
                return;
            }
            final ViewTree target;

            // If the view has a valid id, i.e., if can be found using findViewById, add it to the
            // tree, otherwise skip this view and add its children instead.
            if (v.getId() != 0) {
                ViewTree tree = new ViewTree(v);
                mChildren.add(tree);
                target = tree;
            } else {
                target = this;
            }

            if (v instanceof ViewGroup) {
                if (target.mChildren == null) {
                    target.mChildren = new ArrayList<>();
                    ViewGroup vg = (ViewGroup) v;
                    int count = vg.getChildCount();
                    for (int i = 0; i < count; i++) {
                        target.addViewChild(vg.getChildAt(i));
                    }
                }
            }
        }

        /** Find the first child for which the condition is true and return its index. */
        public int findChildIndex(Predicate<View> condition) {
            return findChildIndex(0, condition);
        }

        /**
         * Find the first child, starting at {@code startIndex}, for which the condition is true and
         * return its index.
         */
        public int findChildIndex(int startIndex, Predicate<View> condition) {
            if (mChildren == null) {
                return -1;
            }

            for (int i = startIndex; i < mChildren.size(); i++) {
                if (condition.test(mChildren.get(i).mRoot)) {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * Class representing a response to an action performed on any element of a RemoteViews.
     */
    public static class RemoteResponse {

        /** @hide **/
        @IntDef(prefix = "INTERACTION_TYPE_", value = {
                INTERACTION_TYPE_CLICK,
                INTERACTION_TYPE_CHECKED_CHANGE,
        })
        @Retention(RetentionPolicy.SOURCE)
        @interface InteractionType {}
        /** @hide */
        public static final int INTERACTION_TYPE_CLICK = 0;
        /** @hide */
        public static final int INTERACTION_TYPE_CHECKED_CHANGE = 1;

        private PendingIntent mPendingIntent;
        private Intent mFillIntent;

        private int mInteractionType = INTERACTION_TYPE_CLICK;
        private IntArray mViewIds;
        private ArrayList<String> mElementNames;

        /**
         * Creates a response which sends a pending intent as part of the response. The source
         * bounds ({@link Intent#getSourceBounds()}) of the intent will be set to the bounds of the
         * target view in screen space.
         * Note that any activity options associated with the mPendingIntent may get overridden
         * before starting the intent.
         *
         * @param pendingIntent The {@link PendingIntent} to send as part of the response
         */
        @NonNull
        public static RemoteResponse fromPendingIntent(@NonNull PendingIntent pendingIntent) {
            RemoteResponse response = new RemoteResponse();
            response.mPendingIntent = pendingIntent;
            return response;
        }

        /**
         * When using collections (eg. {@link ListView}, {@link StackView} etc.) in widgets, it is
         * very costly to set PendingIntents on the individual items, and is hence not recommended.
         * Instead a single PendingIntent template can be set on the collection, see {@link
         * RemoteViews#setPendingIntentTemplate(int, PendingIntent)}, and the individual on-click
         * action of a given item can be distinguished by setting a fillInIntent on that item. The
         * fillInIntent is then combined with the PendingIntent template in order to determine the
         * final intent which will be executed when the item is clicked. This works as follows: any
         * fields which are left blank in the PendingIntent template, but are provided by the
         * fillInIntent will be overwritten, and the resulting PendingIntent will be used. The rest
         * of the PendingIntent template will then be filled in with the associated fields that are
         * set in fillInIntent. See {@link Intent#fillIn(Intent, int)} for more details.
         * Creates a response which sends a pending intent as part of the response. The source
         * bounds ({@link Intent#getSourceBounds()}) of the intent will be set to the bounds of the
         * target view in screen space.
         * Note that any activity options associated with the mPendingIntent may get overridden
         * before starting the intent.
         *
         * @param fillIntent The intent which will be combined with the parent's PendingIntent in
         *                   order to determine the behavior of the response
         * @see RemoteViews#setPendingIntentTemplate(int, PendingIntent)
         * @see RemoteViews#setOnClickFillInIntent(int, Intent)
         */
        @NonNull
        public static RemoteResponse fromFillInIntent(@NonNull Intent fillIntent) {
            RemoteResponse response = new RemoteResponse();
            response.mFillIntent = fillIntent;
            if (fillIntent != null) {
                // Although the parameter is marked as @NonNull, it is nullable. The method that
                // calls it (RemoteReviews.setOnClickFillInIntent()) passes its fillInIntent
                // parameter to this method and it does not guarantee that the fillInIntent is
                // non-null.
                fillIntent.collectExtraIntentKeys();
            }
            return response;
        }

        private static RemoteResponse fromPendingIntentTemplateAndFillInIntent(
                @NonNull final PendingIntent pendingIntent, @NonNull final Intent intent) {
            RemoteResponse response = new RemoteResponse();
            response.mPendingIntent = pendingIntent;
            response.mFillIntent = intent;
            intent.collectExtraIntentKeys();
            return response;
        }

        /**
         * Adds a shared element to be transferred as part of the transition between Activities
         * using cross-Activity scene animations. The position of the first element will be used as
         * the epicenter for the exit Transition. The position of the associated shared element in
         * the launched Activity will be the epicenter of its entering Transition.
         *
         * @param viewId            The id of the view to be shared as part of the transition
         * @param sharedElementName The shared element name for this view
         * @see ActivityOptions#makeSceneTransitionAnimation(Activity, Pair[])
         */
        @NonNull
        public RemoteResponse addSharedElement(@IdRes int viewId,
                @NonNull String sharedElementName) {
            if (mViewIds == null) {
                mViewIds = new IntArray();
                mElementNames = new ArrayList<>();
            }
            mViewIds.add(viewId);
            mElementNames.add(sharedElementName);
            return this;
        }

        /**
         * Sets the interaction type for which this RemoteResponse responds.
         *
         * @param type the type of interaction for which this is a response, such as clicking or
         *             checked state changing
         *
         * @hide
         */
        @NonNull
        public RemoteResponse setInteractionType(@InteractionType int type) {
            mInteractionType = type;
            return this;
        }

        private void writeToParcel(Parcel dest, int flags) {
            PendingIntent.writePendingIntentOrNullToParcel(mPendingIntent, dest);
            dest.writeBoolean((mFillIntent != null));
            if (mFillIntent != null) {
                dest.writeTypedObject(mFillIntent, flags);
            }
            dest.writeInt(mInteractionType);
            dest.writeIntArray(mViewIds == null ? null : mViewIds.toArray());
            dest.writeStringList(mElementNames);
        }

        private void readFromParcel(Parcel parcel) {
            mPendingIntent = PendingIntent.readPendingIntentOrNullFromParcel(parcel);
            mFillIntent = parcel.readBoolean() ? parcel.readTypedObject(Intent.CREATOR) : null;
            mInteractionType = parcel.readInt();
            int[] viewIds = parcel.createIntArray();
            mViewIds = viewIds == null ? null : IntArray.wrap(viewIds);
            mElementNames = parcel.createStringArrayList();
        }

        private void handleViewInteraction(
                View v,
                InteractionHandler handler) {
            final PendingIntent pi;
            if (mPendingIntent != null) {
                pi = mPendingIntent;
            } else if (mFillIntent != null) {
                AdapterView<?> ancestor = getAdapterViewAncestor(v);
                if (ancestor == null) {
                    Log.e(LOG_TAG, "Collection item doesn't have AdapterView parent");
                    return;
                }

                // Ensure that a template pending intent has been set on the ancestor
                if (!(ancestor.getTag() instanceof PendingIntent)) {
                    Log.e(LOG_TAG, "Attempting setOnClickFillInIntent or "
                            + "setOnCheckedChangeFillInIntent without calling "
                            + "setPendingIntentTemplate on parent.");
                    return;
                }

                pi = (PendingIntent) ancestor.getTag();
            } else {
                Log.e(LOG_TAG, "Response has neither pendingIntent nor fillInIntent");
                return;
            }

            handler.onInteraction(v, pi, this);
        }

        /**
         * Returns the closest ancestor of the view that is an AdapterView or null if none could be
         * found.
         */
        @Nullable
        private static AdapterView<?> getAdapterViewAncestor(@Nullable View view) {
            if (view == null) return null;

            View parent = (View) view.getParent();
            // Break the for loop on the first encounter of:
            //    1) an AdapterView,
            //    2) an AppWidgetHostView that is not a child of an adapter view, or
            //    3) a null parent.
            // 2) and 3) are unexpected and catch the case where a child is not
            // correctly parented in an AdapterView.
            while (parent != null && !(parent instanceof AdapterView<?>)
                    && !((parent instanceof AppWidgetHostView)
                            && !(parent instanceof AppWidgetHostView.AdapterChildHostView))) {
                parent = (View) parent.getParent();
            }

            return parent instanceof AdapterView<?> ? (AdapterView<?>) parent : null;
        }

        /** @hide */
        public Pair<Intent, ActivityOptions> getLaunchOptions(View view) {
            Intent intent = mFillIntent == null ? new Intent() : new Intent(mFillIntent);
            intent.setSourceBounds(getSourceBounds(view));

            if (view instanceof CompoundButton
                    && mInteractionType == INTERACTION_TYPE_CHECKED_CHANGE) {
                intent.putExtra(EXTRA_CHECKED, ((CompoundButton) view).isChecked());
            }

            ActivityOptions opts = null;

            Context context = view.getContext();
            if (context.getResources().getBoolean(
                    com.android.internal.R.bool.config_overrideRemoteViewsActivityTransition)) {
                TypedArray windowStyle = context.getTheme().obtainStyledAttributes(
                        com.android.internal.R.styleable.Window);
                int windowAnimations = windowStyle.getResourceId(
                        com.android.internal.R.styleable.Window_windowAnimationStyle, 0);
                TypedArray windowAnimationStyle = context.obtainStyledAttributes(
                        windowAnimations, com.android.internal.R.styleable.WindowAnimation);
                int enterAnimationId = windowAnimationStyle.getResourceId(com.android.internal.R
                        .styleable.WindowAnimation_activityOpenRemoteViewsEnterAnimation, 0);
                windowStyle.recycle();
                windowAnimationStyle.recycle();

                if (enterAnimationId != 0) {
                    opts = ActivityOptions.makeCustomAnimation(context,
                            enterAnimationId, 0);
                    opts.setPendingIntentLaunchFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
            }

            if (opts == null && mViewIds != null && mElementNames != null) {
                View parent = (View) view.getParent();
                while (parent != null && !(parent instanceof AppWidgetHostView)) {
                    parent = (View) parent.getParent();
                }
                if (parent instanceof AppWidgetHostView) {
                    opts = ((AppWidgetHostView) parent).createSharedElementActivityOptions(
                            mViewIds.toArray(),
                            mElementNames.toArray(new String[mElementNames.size()]), intent);
                }
            }

            if (opts == null) {
                opts = ActivityOptions.makeBasic();
                opts.setPendingIntentLaunchFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            if (view.getDisplay() != null) {
                opts.setLaunchDisplayId(view.getDisplay().getDisplayId());
            } else {
                // TODO(b/218409359): Remove once bug is fixed.
                Log.w(LOG_TAG, "getLaunchOptions: view.getDisplay() is null!",
                        new Exception());
            }
            // If the user interacts with a visible element it is safe to assume they consent that
            // something is going to start.
            opts.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS);
            return Pair.create(intent, opts);
        }
    }

    /** @hide */
    public static boolean startPendingIntent(View view, PendingIntent pendingIntent,
            Pair<Intent, ActivityOptions> options) {
        try {
            // TODO: Unregister this handler if PendingIntent.FLAG_ONE_SHOT?
            Context context = view.getContext();
            // The NEW_TASK flags are applied through the activity options and not as a part of
            // the call to startIntentSender() to ensure that they are consistently applied to
            // both mutable and immutable PendingIntents.
            context.startIntentSender(
                    pendingIntent.getIntentSender(), options.first,
                    0, 0, 0, options.second.toBundle());
        } catch (IntentSender.SendIntentException e) {
            Log.e(LOG_TAG, "Cannot send pending intent: ", e);
            return false;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Cannot send pending intent due to unknown exception: ", e);
            return false;
        }
        return true;
    }

    private int getBitmapMemoryUsedByActions() {
        Set<Integer> bitmapIdSet = getBitmapIdsUsedByActions(new HashSet<>());
        int result = 0;
        for (int bitmapId: bitmapIdSet) {
            Bitmap currentBitmap = mBitmapCache.getBitmapForId(bitmapId);
            if (currentBitmap == null) {
                continue;
            }
            result += currentBitmap.getAllocationByteCount();
        }

        return result;
    }

    private Set<Integer> getBitmapIdsUsedByActions(@NonNull Set<Integer> intSet) {
        if (hasSizedRemoteViews()) {
            for (RemoteViews views: mSizedRemoteViews) {
                views.getBitmapIdsUsedByActions(intSet);
            }
        } else if (hasLandscapeAndPortraitLayouts()) {
            mLandscape.getBitmapIdsUsedByActions(intSet);
            mPortrait.getBitmapIdsUsedByActions(intSet);
        } else if (mActions != null) {
            for (Action action: mActions) {
                if (action instanceof ViewGroupActionAdd vgaa
                        && vgaa.mNestedViews != null) {
                    vgaa.mNestedViews.getBitmapIdsUsedByActions(intSet);
                } else if (action instanceof BitmapReflectionAction bitmapAction) {
                    intSet.add(bitmapAction.mBitmapId);
                }
            }
        }

        return intSet;
    }

    /** Representation of a fixed list of items to be displayed in a RemoteViews collection. */
    public static final class RemoteCollectionItems implements Parcelable {
        private final long[] mIds;
        private final RemoteViews[] mViews;
        private final boolean mHasStableIds;
        private final int mViewTypeCount;

        private HierarchyRootData mHierarchyRootData;

        RemoteCollectionItems(
                long[] ids, RemoteViews[] views, boolean hasStableIds, int viewTypeCount) {
            mIds = ids;
            mViews = views;
            mHasStableIds = hasStableIds;
            mViewTypeCount = viewTypeCount;
            if (ids.length != views.length) {
                throw new IllegalArgumentException(
                        "RemoteCollectionItems has different number of ids and views");
            }
            if (viewTypeCount < 1) {
                throw new IllegalArgumentException("View type count must be >= 1");
            }
            int layoutIdCount = (int) Arrays.stream(views)
                    .mapToInt(RemoteViews::getLayoutId)
                    .distinct()
                    .count();
            if (layoutIdCount > viewTypeCount) {
                throw new IllegalArgumentException(
                        "View type count is set to " + viewTypeCount + ", but the collection "
                                + "contains " + layoutIdCount + " different layout ids");
            }

            // Until the collection items are attached to a parent, we configure the first item
            // to be the root of the others to share caches and save space during serialization.
            if (views.length > 0) {
                setHierarchyRootData(views[0].getHierarchyRootData());
                views[0].mIsRoot = true;
            }
        }

        RemoteCollectionItems(@NonNull Parcel in, @Nullable HierarchyRootData hierarchyRootData) {
            mHasStableIds = in.readBoolean();
            mViewTypeCount = in.readInt();
            int length = in.readInt();
            mIds = new long[length];
            in.readLongArray(mIds);

            boolean attached = in.readBoolean();
            mViews = new RemoteViews[length];
            int firstChildIndex;
            if (attached) {
                if (hierarchyRootData == null) {
                    throw new IllegalStateException("Cannot unparcel a RemoteCollectionItems that "
                            + "was parceled as attached without providing data for a root "
                            + "RemoteViews");
                }
                mHierarchyRootData = hierarchyRootData;
                firstChildIndex = 0;
            } else {
                mViews[0] = new RemoteViews(in);
                mHierarchyRootData = mViews[0].getHierarchyRootData();
                firstChildIndex = 1;
            }

            for (int i = firstChildIndex; i < length; i++) {
                mViews[i] = new RemoteViews(
                        in,
                        mHierarchyRootData,
                        /* info= */ null,
                        /* depth= */ 0);
            }
        }

        void setHierarchyRootData(@NonNull HierarchyRootData rootData) {
            mHierarchyRootData = rootData;
            for (RemoteViews view : mViews) {
                view.configureAsChild(rootData);
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            writeToParcel(dest, flags, /* attached= */ false);
        }

        private void writeToParcel(@NonNull Parcel dest, int flags, boolean attached) {
            boolean prevAllowSquashing = dest.allowSquashing();

            dest.writeBoolean(mHasStableIds);
            dest.writeInt(mViewTypeCount);
            dest.writeInt(mIds.length);
            dest.writeLongArray(mIds);

            if (attached && mHierarchyRootData == null) {
                throw new IllegalStateException("Cannot call writeToParcelAttached for a "
                        + "RemoteCollectionItems without first calling setHierarchyRootData()");
            }

            // Write whether we parceled as attached or not. This allows cleaner validation and
            // proper error messaging when unparceling later.
            dest.writeBoolean(attached);
            boolean restoreRoot = false;
            if (!attached && mViews.length > 0 && !mViews[0].mIsRoot) {
                // If we're writing unattached, temporarily set the first item as the root so that
                // the bitmap cache is written to the parcel.
                restoreRoot = true;
                mViews[0].mIsRoot = true;
            }

            for (RemoteViews view : mViews) {
                view.writeToParcel(dest, flags);
            }

            if (restoreRoot) mViews[0].mIsRoot = false;
            dest.restoreAllowSquashing(prevAllowSquashing);
        }

        /** @hide */
        public void writeToProto(Context context, ProtoOutputStream out) {
            writeToProto(context, out, /* attached= */ false);
        }

        private void writeToProto(Context context, ProtoOutputStream out, boolean attached) {
            for (long id : mIds) {
                out.write(RemoteViewsProto.RemoteCollectionItems.IDS, id);
            }

            boolean restoreRoot = false;
            out.write(RemoteViewsProto.RemoteCollectionItems.ATTACHED, attached);
            if (!attached && mViews.length > 0 && !mViews[0].mIsRoot) {
                restoreRoot = true;
                mViews[0].mIsRoot = true;
            }
            for (RemoteViews view : mViews) {
                final long viewsToken = out.start(RemoteViewsProto.RemoteCollectionItems.VIEWS);
                view.writePreviewToProto(context, out);
                out.end(viewsToken);
            }
            if (restoreRoot) mViews[0].mIsRoot = false;
            out.write(RemoteViewsProto.RemoteCollectionItems.HAS_STABLE_IDS, mHasStableIds);
            out.write(RemoteViewsProto.RemoteCollectionItems.VIEW_TYPE_COUNT, mViewTypeCount);
        }

        /**
         * Overload used for testing unattached RemoteCollectionItems serialization.
         *
         * @hide
         */
        public static RemoteCollectionItems createFromProto(Context context, ProtoInputStream in)
                throws Exception {
            return createFromProto(in).create(context, context.getResources(), /* rootData= */
                    null, 0);
        }

        /** @hide */
        public static PendingResources<RemoteCollectionItems> createFromProto(ProtoInputStream in)
                throws Exception {
            final LongSparseArray<Object> values = new LongSparseArray<>();

            values.put(RemoteViewsProto.RemoteCollectionItems.IDS, new ArrayList<Long>());
            values.put(RemoteViewsProto.RemoteCollectionItems.VIEWS,
                    new ArrayList<PendingResources<RemoteViews>>());

            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.RemoteCollectionItems.IDS:
                        ((ArrayList<Long>) values.get(
                                RemoteViewsProto.RemoteCollectionItems.IDS)).add(
                                in.readLong(RemoteViewsProto.RemoteCollectionItems.IDS));
                        break;
                    case (int) RemoteViewsProto.RemoteCollectionItems.VIEWS:
                        final long viewsToken = in.start(
                                RemoteViewsProto.RemoteCollectionItems.VIEWS);
                        ((ArrayList<PendingResources<RemoteViews>>) values.get(
                                RemoteViewsProto.RemoteCollectionItems.VIEWS)).add(
                                RemoteViews.createFromProto(in));
                        in.end(viewsToken);
                        break;
                    case (int) RemoteViewsProto.RemoteCollectionItems.HAS_STABLE_IDS:
                        values.put(RemoteViewsProto.RemoteCollectionItems.HAS_STABLE_IDS,
                                in.readBoolean(
                                        RemoteViewsProto.RemoteCollectionItems.HAS_STABLE_IDS));
                        break;
                    case (int) RemoteViewsProto.RemoteCollectionItems.VIEW_TYPE_COUNT:
                        values.put(RemoteViewsProto.RemoteCollectionItems.VIEW_TYPE_COUNT,
                                in.readInt(RemoteViewsProto.RemoteCollectionItems.VIEW_TYPE_COUNT));
                        break;
                    case (int) RemoteViewsProto.RemoteCollectionItems.ATTACHED:
                        values.put(RemoteViewsProto.RemoteCollectionItems.ATTACHED,
                                in.readBoolean(RemoteViewsProto.RemoteCollectionItems.ATTACHED));
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }

            checkContainsKeys(values,
                    new long[]{RemoteViewsProto.RemoteCollectionItems.VIEW_TYPE_COUNT});

            return (context, resources, rootData, depth) -> {
                List<Long> idList = (List<Long>) values.get(
                        RemoteViewsProto.RemoteCollectionItems.IDS);
                long[] ids = new long[idList.size()];
                for (int i = 0; i < idList.size(); i++) {
                    ids[i] = idList.get(i);
                }
                boolean attached = (boolean) values.get(
                        RemoteViewsProto.RemoteCollectionItems.ATTACHED, false);
                List<PendingResources<RemoteViews>> pendingViews =
                        (List<PendingResources<RemoteViews>>) values.get(
                                RemoteViewsProto.RemoteCollectionItems.VIEWS);
                RemoteViews[] views = new RemoteViews[pendingViews.size()];

                if (attached && rootData == null) {
                    throw new IllegalStateException("Cannot create a RemoteCollectionItems from "
                            + "proto that was attached without providing HierarchyRootData");
                }

                int firstChildIndex = 0;
                if (!attached && pendingViews.size() > 0) {
                    // If written as unattached, get HierarchyRootData from first view
                    views[0] = pendingViews.get(0).create(context, resources, /* rootData= */ null,
                            /* depth= */ 0);
                    rootData = views[0].getHierarchyRootData();
                    firstChildIndex = 1;
                }
                for (int i = firstChildIndex; i < views.length; i++) {
                    // Depth is reset to 0 for RemoteCollectionItems item views, see Parcel
                    // constructor.
                    views[i] = pendingViews.get(i).create(context, resources, rootData,
                            /* depth= */ 0);
                }
                return new RemoteCollectionItems(ids, views,
                        (boolean) values.get(RemoteViewsProto.RemoteCollectionItems.HAS_STABLE_IDS,
                                false),
                        (int) values.get(RemoteViewsProto.RemoteCollectionItems.VIEW_TYPE_COUNT,
                                0));
            };
        }

        /**
         * Returns the id for {@code position}. See {@link #hasStableIds()} for whether this id
         * should be considered meaningful across collection updates.
         *
         * @return Id for the position.
         */
        public long getItemId(int position) {
            return mIds[position];
        }

        /**
         * Returns the {@link RemoteViews} to display at {@code position}.
         *
         * @return RemoteViews for the position.
         */
        @NonNull
        public RemoteViews getItemView(int position) {
            return mViews[position];
        }

        /**
         * Returns the number of elements in the collection.
         *
         * @return Count of items.
         */
        public int getItemCount() {
            return mIds.length;
        }

        /**
         * Returns the view type count for the collection when used in an adapter
         *
         * @return Count of view types for the collection when used in an adapter.
         * @see android.widget.Adapter#getViewTypeCount()
         */
        public int getViewTypeCount() {
            return mViewTypeCount;
        }

        /**
         * Indicates whether the item ids are stable across changes to the underlying data.
         *
         * @return True if the same id always refers to the same object.
         * @see android.widget.Adapter#hasStableIds()
         */
        public boolean hasStableIds() {
            return mHasStableIds;
        }

        @NonNull
        public static final Creator<RemoteCollectionItems> CREATOR =
                new Creator<RemoteCollectionItems>() {
            @NonNull
            @Override
            public RemoteCollectionItems createFromParcel(@NonNull Parcel source) {
                return new RemoteCollectionItems(source, /* hierarchyRoot= */ null);
            }

            @NonNull
            @Override
            public RemoteCollectionItems[] newArray(int size) {
                return new RemoteCollectionItems[size];
            }
        };

        /** Builder class for {@link RemoteCollectionItems} objects.*/
        public static final class Builder {
            private final LongArray mIds = new LongArray();
            private final List<RemoteViews> mViews = new ArrayList<>();
            private boolean mHasStableIds;
            private int mViewTypeCount;

            /**
             * Adds a {@link RemoteViews} to the collection.
             *
             * @param id Id to associate with the row. Use {@link #setHasStableIds(boolean)} to
             *           indicate that ids are stable across changes to the collection.
             * @param view RemoteViews to display for the row.
             */
            @NonNull
            // Covered by getItemId, getItemView, getItemCount.
            @SuppressLint("MissingGetterMatchingBuilder")
            public Builder addItem(long id, @NonNull RemoteViews view) {
                if (view == null) throw new NullPointerException();
                if (view.hasMultipleLayouts()) {
                    throw new IllegalArgumentException(
                            "RemoteViews used in a RemoteCollectionItems cannot specify separate "
                                    + "layouts for orientations or sizes.");
                }
                mIds.add(id);
                mViews.add(view);
                return this;
            }

            /**
             * Sets whether the item ids are stable across changes to the underlying data.
             *
             * @see android.widget.Adapter#hasStableIds()
             */
            @NonNull
            public Builder setHasStableIds(boolean hasStableIds) {
                mHasStableIds = hasStableIds;
                return this;
            }

            /**
             * Sets the view type count for the collection when used in an adapter. This can be set
             * to the maximum number of different layout ids that will be used by RemoteViews in
             * this collection.
             *
             * If this value is not set, then a value will be inferred from the provided items. As
             * a result, the adapter may need to be recreated when the list is updated with
             * previously unseen RemoteViews layouts for new items.
             *
             * @see android.widget.Adapter#getViewTypeCount()
             */
            @NonNull
            public Builder setViewTypeCount(int viewTypeCount) {
                mViewTypeCount = viewTypeCount;
                return this;
            }

            /** Creates the {@link RemoteCollectionItems} defined by this builder. */
            @NonNull
            public RemoteCollectionItems build() {
                if (mViewTypeCount < 1) {
                    // If a view type count wasn't specified, set it to be the number of distinct
                    // layout ids used in the items.
                    mViewTypeCount = (int) mViews.stream()
                            .mapToInt(RemoteViews::getLayoutId)
                            .distinct()
                            .count();
                }
                return new RemoteCollectionItems(
                        mIds.toArray(),
                        mViews.toArray(new RemoteViews[0]),
                        mHasStableIds,
                        Math.max(mViewTypeCount, 1));
            }
        }

        /**
         * See {@link RemoteViews#visitUris(Consumer)}.
         */
        private void visitUris(@NonNull Consumer<Uri> visitor) {
            for (RemoteViews view : mViews) {
                view.visitUris(visitor);
            }
        }

        /**
         * See {@link RemoteViews#visitIcons(Consumer)}.
         */
        private void visitIcons(@NonNull Consumer<Icon> visitor) {
            for (RemoteViews view : mViews) {
                view.visitIcons(visitor);
            }
        }
    }

    /**
     * A data parcel that carries the instructions to draw the RemoteViews, as an alternative to
     * XML layout.
     */
    @FlaggedApi(FLAG_DRAW_DATA_PARCEL)
    public static final class DrawInstructions {

        private static final long VERSION = 1L;

        @NonNull
        final List<byte[]> mInstructions;

        private DrawInstructions() {
            throw new UnsupportedOperationException(
                    "DrawInstructions cannot be instantiate without instructions");
        }

        private DrawInstructions(@NonNull List<byte[]> instructions) {
            // Create and retain an immutable copy of given instructions.
            mInstructions = new ArrayList<>(instructions.size());
            for (byte[] instruction : instructions) {
                final int len = instruction.length;
                final byte[] target = new byte[len];
                System.arraycopy(instruction, 0, target, 0, len);
                mInstructions.add(target);
            }
        }

        @Nullable
        private static DrawInstructions readFromParcel(@NonNull final Parcel in) {
            int size = in.readInt();
            if (size == -1) {
                return null;
            }
            byte[] instruction;
            final List<byte[]> instructions = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                instruction = in.readBlob();
                instructions.add(instruction);
            }
            return new DrawInstructions(instructions);
        }

        private static void writeToParcel(@Nullable final DrawInstructions drawInstructions,
                @NonNull final Parcel dest, final int flags) {
            if (drawInstructions == null) {
                dest.writeInt(-1);
                return;
            }
            final List<byte[]> instructions = drawInstructions.mInstructions;
            dest.writeInt(instructions.size());
            for (byte[] instruction : instructions) {
                dest.writeBlob(instruction);
            }
        }

        /**
         * Version number of {@link DrawInstructions} currently supported.
         */
        @FlaggedApi(FLAG_DRAW_DATA_PARCEL)
        public static long getSupportedVersion() {
            return (long) CoreDocument.getDocumentApiLevel();
        }

        /**
         * Builder class for {@link DrawInstructions} objects.
         */
        @FlaggedApi(FLAG_DRAW_DATA_PARCEL)
        public static final class Builder {

            private final List<byte[]> mInstructions;

            /**
             * Constructor.
             *
             * @param instructions Information to draw the RemoteViews.
             */
            @FlaggedApi(FLAG_DRAW_DATA_PARCEL)
            public Builder(@NonNull final List<byte[]> instructions) {
                mInstructions = new ArrayList<>(instructions);
            }

            /**
             * Creates a {@link DrawInstructions} instance.
             */
            @NonNull
            @FlaggedApi(FLAG_DRAW_DATA_PARCEL)
            public DrawInstructions build() {
                return new DrawInstructions(mInstructions);
            }
        }
    }

    /**
     * Get the ID of the top-level view of the XML layout, if set using
     * {@link RemoteViews#RemoteViews(String, int, int)}.
     */
    @IdRes
    public int getViewId() {
        return mViewId;
    }

    /**
     * Set the provider instance ID.
     *
     * This should only be used by {@link com.android.server.appwidget.AppWidgetService}.
     * @hide
     */
    public void setProviderInstanceId(long id) {
        mProviderInstanceId = id;
    }

    /**
     * Get the provider instance id.
     *
     * This should uniquely identifies {@link RemoteViews} coming from a given App Widget
     * Provider. This changes each time the App Widget provider update the {@link RemoteViews} of
     * its widget. Returns -1 if the {@link RemoteViews} doesn't come from an App Widget provider.
     * @hide
     */
    public long getProviderInstanceId() {
        return mProviderInstanceId;
    }

    /**
     * Identify the child of this {@link RemoteViews}, or 0 if this is not a child.
     *
     * The returned value is always a small integer, currently between 0 and 17.
     */
    private int getChildId(@NonNull RemoteViews child) {
        if (child == this) {
            return 0;
        }
        if (hasSizedRemoteViews()) {
            for (int i = 0; i < mSizedRemoteViews.size(); i++) {
                if (mSizedRemoteViews.get(i) == child) {
                    return i + 1;
                }
            }
        }
        if (hasLandscapeAndPortraitLayouts()) {
            if (mLandscape == child) {
                return 1;
            } else if (mPortrait == child) {
                return 2;
            }
        }
        // This is not a child of this RemoteViews.
        return 0;
    }

    /**
     * Identify uniquely this RemoteViews, or returns -1 if not possible.
     *
     * @param parent If the {@link RemoteViews} is not a root {@link RemoteViews}, this should be
     *              the parent that contains it.
     *
     * @hide
     */
    public long computeUniqueId(@Nullable RemoteViews parent) {
        if (mIsRoot) {
            long viewId = getProviderInstanceId();
            if (viewId != -1) {
                viewId <<= 8;
            }
            return viewId;
        }
        if (parent == null) {
            return -1;
        }
        long viewId = parent.getProviderInstanceId();
        if (viewId == -1) {
            return -1;
        }
        int childId = parent.getChildId(this);
        if (childId == -1) {
            return -1;
        }
        viewId <<= 8;
        viewId |= childId;
        return viewId;
    }

    @Nullable
    private static Pair<String, Integer> getPackageUserKey(@Nullable ApplicationInfo info) {
        if (info == null || info.packageName ==  null) return null;
        return Pair.create(info.packageName, info.uid);
    }

    private HierarchyRootData getHierarchyRootData() {
        return new HierarchyRootData(mBitmapCache, mCollectionCache,
                mApplicationInfoCache, mClassCookies);
    }

    private static final class HierarchyRootData {
        final BitmapCache mBitmapCache;
        final RemoteCollectionCache mRemoteCollectionCache;
        final ApplicationInfoCache mApplicationInfoCache;
        final Map<Class, Object> mClassCookies;

        HierarchyRootData(
                BitmapCache bitmapCache,
                RemoteCollectionCache remoteCollectionCache,
                ApplicationInfoCache applicationInfoCache,
                Map<Class, Object> classCookies) {
            mBitmapCache = bitmapCache;
            mRemoteCollectionCache = remoteCollectionCache;
            mApplicationInfoCache = applicationInfoCache;
            mClassCookies = classCookies;
        }
    }

    /**
     * Write this RemoteViews to proto.
     * @hide
     */
    @FlaggedApi(FLAG_REMOTE_VIEWS_PROTO)
    public void writePreviewToProto(@NonNull Context context, @NonNull ProtoOutputStream out) {
        if (mApplication != null) {
            // mApplication may be null if this was created with DrawInstructions constructor.
            out.write(RemoteViewsProto.PACKAGE_NAME, mApplication.packageName);
            out.write(RemoteViewsProto.UID, mApplication.uid);
        }
        Resources appResources = getContextForResourcesEnsuringCorrectCachedApkPaths(
                context).getResources();
        if (mLayoutId != 0) {
            out.write(RemoteViewsProto.LAYOUT_ID, appResources.getResourceName(mLayoutId));
        }
        if (mLightBackgroundLayoutId != 0) {
            out.write(RemoteViewsProto.LIGHT_BACKGROUND_LAYOUT_ID,
                    appResources.getResourceName(mLightBackgroundLayoutId));
        }
        if (mViewId != 0 && mViewId != -1) {
            out.write(RemoteViewsProto.VIEW_ID, appResources.getResourceName(mViewId));
        }
        if (mIsRoot) {
            mBitmapCache.writeBitmapsToProto(out);
            mCollectionCache.writeToProto(context, out);
        }
        out.write(RemoteViewsProto.IS_ROOT, mIsRoot);
        out.write(RemoteViewsProto.APPLY_FLAGS, mApplyFlags);
        out.write(RemoteViewsProto.HAS_DRAW_INSTRUCTIONS, mHasDrawInstructions);
        if (mProviderInstanceId != -1) {
            out.write(RemoteViewsProto.PROVIDER_INSTANCE_ID, mProviderInstanceId);
        }

        if (!hasMultipleLayouts()) {
            out.write(RemoteViewsProto.MODE, MODE_NORMAL);
            if (mIdealSize != null) {
                final long token = out.start(RemoteViewsProto.IDEAL_SIZE);
                out.write(SizeFProto.WIDTH, mIdealSize.getWidth());
                out.write(SizeFProto.HEIGHT, mIdealSize.getHeight());
                out.end(token);
            }

            if (mActions != null) {
                for (Action action : mActions) {
                    if (action.canWriteToProto()) {
                        final long token = out.start(RemoteViewsProto.ACTIONS);
                        action.writeToProto(out, context, appResources);
                        out.end(token);
                    }
                }
            }
        } else if (hasSizedRemoteViews()) {
            out.write(RemoteViewsProto.MODE, MODE_HAS_SIZED_REMOTEVIEWS);
            for (RemoteViews view : mSizedRemoteViews) {
                final long sizedViewToken = out.start(RemoteViewsProto.SIZED_REMOTEVIEWS);
                view.writePreviewToProto(context, out);
                out.end(sizedViewToken);
            }
        } else {
            out.write(RemoteViewsProto.MODE, MODE_HAS_LANDSCAPE_AND_PORTRAIT);
            final long landscapeViewToken = out.start(RemoteViewsProto.LANDSCAPE_REMOTEVIEWS);
            mLandscape.writePreviewToProto(context, out);
            out.end(landscapeViewToken);
            final long portraitViewToken = out.start(RemoteViewsProto.PORTRAIT_REMOTEVIEWS);
            mPortrait.writePreviewToProto(context, out);
            out.end(portraitViewToken);
        }
    }

    /**
     * Create a RemoteViews from proto input.
     * @hide
     */
    @FlaggedApi(FLAG_REMOTE_VIEWS_PROTO)
    public static RemoteViews createPreviewFromProto(Context context, ProtoInputStream in)
            throws Exception {
        return createFromProto(in).create(context, context.getResources(), /* rootData= */ null,
                /* depth= */ 0);
    }

    private static PendingResources<RemoteViews> createFromProto(ProtoInputStream in)
            throws Exception {
        // Grouping these variables into an anonymous object allows us to access them through `ref`
        // (which is final) later in the lambda.
        final var ref = new Object() {
            final RemoteViews mRv = new RemoteViews();
            int mMode = 0;
            int mApplyFlags = 0;
            long mProviderInstanceId = -1;
            String mPackageName = null;
            Integer mUid = null;
            SizeF mIdealSize = null;
            String mLayoutResName = null;
            String mLightBackgroundResName = null;
            String mViewResName = null;
            final List<PendingResources<Action>> mActions = new ArrayList<>();
            final List<PendingResources<RemoteViews>> mSizedRemoteViews = new ArrayList<>();
            PendingResources<RemoteViews> mLandscapeViews = null;
            PendingResources<RemoteViews> mPortraitViews = null;
            PendingResources<RemoteCollectionCache> mPopulateRemoteCollectionCache = null;
            boolean mIsRoot = false;
            boolean mHasDrawInstructions = false;
        };

        try {
            while (in.nextField() != NO_MORE_FIELDS) {
                switch (in.getFieldNumber()) {
                    case (int) RemoteViewsProto.MODE:
                        ref.mMode = in.readInt(RemoteViewsProto.MODE);
                        break;
                    case (int) RemoteViewsProto.PACKAGE_NAME:
                        ref.mPackageName = in.readString(RemoteViewsProto.PACKAGE_NAME);
                        break;
                    case (int) RemoteViewsProto.UID:
                        ref.mUid = in.readInt(RemoteViewsProto.UID);
                        break;
                    case (int) RemoteViewsProto.IDEAL_SIZE:
                        final long idealSizeToken = in.start(RemoteViewsProto.IDEAL_SIZE);
                        ref.mIdealSize = createSizeFFromProto(in);
                        in.end(idealSizeToken);
                        break;
                    case (int) RemoteViewsProto.LAYOUT_ID:
                        ref.mLayoutResName = in.readString(RemoteViewsProto.LAYOUT_ID);
                        break;
                    case (int) RemoteViewsProto.LIGHT_BACKGROUND_LAYOUT_ID:
                        ref.mLightBackgroundResName = in.readString(
                                RemoteViewsProto.LIGHT_BACKGROUND_LAYOUT_ID);
                        break;
                    case (int) RemoteViewsProto.VIEW_ID:
                        ref.mViewResName = in.readString(RemoteViewsProto.VIEW_ID);
                        break;
                    case (int) RemoteViewsProto.APPLY_FLAGS:
                        ref.mApplyFlags = in.readInt(RemoteViewsProto.APPLY_FLAGS);
                        break;
                    case (int) RemoteViewsProto.PROVIDER_INSTANCE_ID:
                        ref.mProviderInstanceId = in.readInt(RemoteViewsProto.PROVIDER_INSTANCE_ID);
                        break;
                    case (int) RemoteViewsProto.ACTIONS:
                        final long actionsToken = in.start(RemoteViewsProto.ACTIONS);
                        final PendingResources<Action> action = createActionFromProto(ref.mRv, in);
                        if (action != null) {
                            ref.mActions.add(action);
                        }
                        in.end(actionsToken);
                        break;
                    case (int) RemoteViewsProto.SIZED_REMOTEVIEWS:
                        final long sizedToken = in.start(RemoteViewsProto.SIZED_REMOTEVIEWS);
                        ref.mSizedRemoteViews.add(createFromProto(in));
                        in.end(sizedToken);
                        break;
                    case (int) RemoteViewsProto.LANDSCAPE_REMOTEVIEWS:
                        final long landscapeToken = in.start(
                                RemoteViewsProto.LANDSCAPE_REMOTEVIEWS);
                        ref.mLandscapeViews = createFromProto(in);
                        in.end(landscapeToken);
                        break;
                    case (int) RemoteViewsProto.PORTRAIT_REMOTEVIEWS:
                        final long portraitToken = in.start(RemoteViewsProto.PORTRAIT_REMOTEVIEWS);
                        ref.mPortraitViews = createFromProto(in);
                        in.end(portraitToken);
                        break;
                    case (int) RemoteViewsProto.BITMAP_CACHE:
                        byte[] src = in.readBytes(RemoteViewsProto.BITMAP_CACHE);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(src, 0, src.length);
                        ref.mRv.mBitmapCache.getBitmapId(bitmap);
                        break;
                    case (int) RemoteViewsProto.REMOTE_COLLECTION_CACHE:
                        final long collectionToken = in.start(
                                RemoteViewsProto.REMOTE_COLLECTION_CACHE);
                        ref.mPopulateRemoteCollectionCache =
                                ref.mRv.populateRemoteCollectionCacheFromProto(in);
                        in.end(collectionToken);
                        break;
                    case (int) RemoteViewsProto.IS_ROOT:
                        ref.mIsRoot = in.readBoolean(RemoteViewsProto.IS_ROOT);
                        break;
                    case (int) RemoteViewsProto.HAS_DRAW_INSTRUCTIONS:
                        ref.mHasDrawInstructions = in.readBoolean(
                                RemoteViewsProto.HAS_DRAW_INSTRUCTIONS);
                        break;
                    default:
                        Log.w(LOG_TAG, "Unhandled field while reading RemoteViews proto!\n"
                                + ProtoUtils.currentFieldToString(in));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return (context, resources, rootData, depth) -> {
            if (depth > MAX_NESTED_VIEWS && (UserHandle.getAppId(Binder.getCallingUid())
                    != Process.SYSTEM_UID)) {
                throw new IllegalArgumentException("Too many nested views.");
            }
            depth++;

            RemoteViews rv = ref.mRv;
            rv.mApplyFlags = ref.mApplyFlags;
            rv.mIsRoot = ref.mIsRoot;
            rv.mHasDrawInstructions = ref.mHasDrawInstructions;

            // The root view will read its HierarchyRootData (bitmap cache, collection cache) from
            // proto; all nested views will instead get it through the rootData parameter.
            if (rootData == null) {
                if (!rv.mIsRoot || depth != 1) {
                    throw new IllegalStateException(
                            "A nested view did not receive HierarchyRootData");
                }
                rootData = rv.getHierarchyRootData();
            } else {
                rv.configureAsChild(rootData);
            }

            Context appContext = null;
            Resources appResources = null;
            if (!ref.mHasDrawInstructions) {
                checkProtoResultNotNull(ref.mPackageName, "No application info");
                checkProtoResultNotNull(ref.mUid, "No uid");
                rv.mApplication = context.getPackageManager().getApplicationInfoAsUser(
                        ref.mPackageName, /* flags= */ 0, UserHandle.getUserId(ref.mUid));
                appContext = rv.getContextForResourcesEnsuringCorrectCachedApkPaths(context);
                appResources = appContext.getResources();

                checkProtoResultNotNull(ref.mLayoutResName, "No layout id");
                rv.mLayoutId = appResources.getIdentifier(ref.mLayoutResName, /* defType= */ null,
                        /* defPackage= */ null);
                checkValidResource(rv.mLayoutId, "Invalid layout id", ref.mLayoutResName);

                if (ref.mViewResName != null) {
                    rv.mViewId = appResources.getIdentifier(ref.mViewResName, /* defType= */ null,
                            /* defPackage= */ null);
                    checkValidResource(rv.mViewId, "Invalid view id", ref.mViewResName);
                }

                if (ref.mLightBackgroundResName != null) {
                    int lightBackgroundLayoutId = appResources.getIdentifier(
                            ref.mLightBackgroundResName,
                            /* defType= */ null, /* defPackage= */ null);
                    checkValidResource(lightBackgroundLayoutId,
                            "Invalid light background layout id", ref.mLightBackgroundResName);
                    rv.setLightBackgroundLayoutId(lightBackgroundLayoutId);
                }
            }
            if (ref.mPopulateRemoteCollectionCache != null) {
                ref.mPopulateRemoteCollectionCache.create(appContext, appResources, rootData,
                        depth);
            }
            if (ref.mProviderInstanceId != -1) {
                rv.mProviderInstanceId = ref.mProviderInstanceId;
            }
            if (ref.mMode == MODE_NORMAL) {
                rv.setIdealSize(ref.mIdealSize);
                boolean hasDrawInstructionAction = false;
                for (PendingResources<Action> pendingAction : ref.mActions) {
                    Action action = pendingAction.create(appContext, appResources, rootData, depth);
                    if (action != null) {
                        if (action instanceof SetDrawInstructionAction) {
                            hasDrawInstructionAction = true;
                        }
                        rv.addAction(action);
                    }
                }
                if (rv.mHasDrawInstructions && !hasDrawInstructionAction) {
                    throw new InvalidProtoException(
                            "RemoteViews proto is missing DrawInstructions");
                }
                return rv;
            } else if (ref.mMode == MODE_HAS_SIZED_REMOTEVIEWS) {
                List<RemoteViews> sizedViews = new ArrayList<>();
                for (RemoteViews.PendingResources<RemoteViews> pendingViews :
                        ref.mSizedRemoteViews) {
                    RemoteViews views = pendingViews.create(appContext, appResources, rootData,
                            depth);
                    sizedViews.add(views);
                }
                rv.initializeSizedRemoteViews(sizedViews.iterator());
                return rv;
            } else if (ref.mMode == MODE_HAS_LANDSCAPE_AND_PORTRAIT) {
                checkProtoResultNotNull(ref.mLandscapeViews, "Missing landscape views");
                checkProtoResultNotNull(ref.mPortraitViews, "Missing portrait views");
                RemoteViews parentRv = new RemoteViews(
                        ref.mLandscapeViews.create(appContext, appResources, rootData, depth),
                        ref.mPortraitViews.create(appContext, appResources, rootData, depth));
                parentRv.initializeFrom(/* src= */ rv, /* hierarchyRoot= */ rv);
                return parentRv;
            } else {
                throw new InvalidProtoException(ref.mMode + " is not a valid mode.");
            }
        };
    }

    private static class InvalidProtoException extends Exception {
        InvalidProtoException(String message) {
            super(message);
        }
    }

    interface PendingResources<T> {
        T create(Context context, Resources appResources, HierarchyRootData rootData, int depth)
                throws Exception;
    }

    @Nullable
    private static PendingResources<Action> createActionFromProto(RemoteViews rv,
            ProtoInputStream in) throws Exception {
        int actionFieldId = in.nextField();
        if (actionFieldId == NO_MORE_FIELDS) {
            // action was omitted
            return null;
        }
        switch (actionFieldId) {
            case (int) RemoteViewsProto.Action.ATTRIBUTE_REFLECTION_ACTION:
                return AttributeReflectionAction.createFromProto(in);
            case (int) RemoteViewsProto.Action.BITMAP_REFLECTION_ACTION:
                return rv.createFromBitmapReflectionActionFromProto(in);
            case (int) RemoteViewsProto.Action.COMPLEX_UNIT_DIMENSION_REFLECTION_ACTION:
                return ComplexUnitDimensionReflectionAction.createFromProto(in);
            case (int) RemoteViewsProto.Action.LAYOUT_PARAM_ACTION:
                return LayoutParamAction.createFromProto(in);
            case (int) RemoteViewsProto.Action.NIGHT_MODE_REFLECTION_ACTION:
                return NightModeReflectionAction.createFromProto(in);
            case (int) RemoteViewsProto.Action.REFLECTION_ACTION:
                return ReflectionAction.createFromProto(in);
            case (int) RemoteViewsProto.Action.REMOVE_FROM_PARENT_ACTION:
                return RemoveFromParentAction.createFromProto(in);
            case (int) RemoteViewsProto.Action.RESOURCE_REFLECTION_ACTION:
                return ResourceReflectionAction.createFromProto(in);
            case (int) RemoteViewsProto.Action.SET_COMPOUND_BUTTON_CHECKED_ACTION:
                return SetCompoundButtonCheckedAction.createFromProto(in);
            case (int) RemoteViewsProto.Action.SET_DRAWABLE_TINT_ACTION:
                return SetDrawableTint.createFromProto(in);
            case (int) RemoteViewsProto.Action.SET_EMPTY_VIEW_ACTION:
                return SetEmptyView.createFromProto(in);
            case (int) RemoteViewsProto.Action.SET_INT_TAG_ACTION:
                return SetIntTagAction.createFromProto(in);
            case (int) RemoteViewsProto.Action.SET_RADIO_GROUP_CHECKED_ACTION:
                return SetRadioGroupCheckedAction.createFromProto(in);
            case (int) RemoteViewsProto.Action.SET_REMOTE_COLLECTION_ITEM_LIST_ADAPTER_ACTION:
                return rv.createSetRemoteCollectionItemListAdapterActionFromProto(in);
            case (int) RemoteViewsProto.Action.SET_RIPPLE_DRAWABLE_COLOR_ACTION:
                return SetRippleDrawableColor.createFromProto(in);
            case (int) RemoteViewsProto.Action.SET_VIEW_OUTLINE_PREFERRED_RADIUS_ACTION:
                return SetViewOutlinePreferredRadiusAction.createFromProto(in);
            case (int) RemoteViewsProto.Action.TEXT_VIEW_DRAWABLE_ACTION:
                return TextViewDrawableAction.createFromProto(in);
            case (int) RemoteViewsProto.Action.TEXT_VIEW_SIZE_ACTION:
                return TextViewSizeAction.createFromProto(in);
            case (int) RemoteViewsProto.Action.VIEW_GROUP_ADD_ACTION:
                return rv.createViewGroupActionAddFromProto(in);
            case (int) RemoteViewsProto.Action.VIEW_GROUP_REMOVE_ACTION:
                return ViewGroupActionRemove.createFromProto(in);
            case (int) RemoteViewsProto.Action.VIEW_PADDING_ACTION:
                return ViewPaddingAction.createFromProto(in);
            case (int) RemoteViewsProto.Action.SET_DRAW_INSTRUCTION_ACTION:
                if (!drawDataParcel()) {
                    return null;
                }
                return rv.createSetDrawInstructionActionFromProto(in);
            default:
                throw new RuntimeException("Unhandled field while reading Action proto!\n"
                        + ProtoUtils.currentFieldToString(in));
        }
    }

    private static void checkValidResource(int id, String message, String resName)
            throws Exception {
        if (id == 0) throw new Exception(message + ": " + resName);
    }

    private static void checkProtoResultNotNull(Object o, String message)
            throws InvalidProtoException {
        if (o == null) {
            throw new InvalidProtoException(message);
        }
    }

    private static void checkContainsKeys(LongSparseArray<?> array, long[] requiredFields) {
        for (long requiredField : requiredFields) {
            if (array.indexOfKey(requiredField) < 0) {
                throw new IllegalArgumentException(
                        "RemoteViews proto missing field: " + ProtoStream.getFieldIdString(
                                requiredField));
            }
        }
    }

    private static int getAsIdentifier(Resources resources, LongSparseArray<?> array, long fieldId)
            throws Exception {
        String resName = (String) array.get(fieldId);
        int id = resources.getIdentifier(resName, /* defType= */ null, /* defPackage= */ null);
        checkValidResource(id, "Invalid id", resName);
        return id;
    }

    private static int getAsIdentifier(Resources resources, SparseArray<?> array, int key)
            throws Exception {
        String resName = (String) array.get(key);
        int id = resources.getIdentifier(resName, /* defType= */ null, /* defPackage= */ null);
        checkValidResource(id, "Invalid id", resName);
        return id;
    }

    private static SizeF createSizeFFromProto(ProtoInputStream in) throws Exception {
        float width = 0;
        float height = 0;
        while (in.nextField() != NO_MORE_FIELDS) {
            switch (in.getFieldNumber()) {
                case (int) SizeFProto.WIDTH:
                    width = in.readFloat(SizeFProto.WIDTH);
                    break;
                case (int) SizeFProto.HEIGHT:
                    height = in.readFloat(SizeFProto.HEIGHT);
                    break;
                default:
                    Log.w(LOG_TAG, "Unhandled field while reading SizeF proto!\n"
                            + ProtoUtils.currentFieldToString(in));
            }
        }

        return new SizeF(width, height);
    }

    private static void writeIconToProto(ProtoOutputStream out, Resources appResources, Icon icon,
            long fieldId) {
        long token = out.start(fieldId);
        RemoteViewsSerializers.writeIconToProto(out, appResources, icon);
        out.end(token);
    }

    private static PendingResources<Icon> createIconFromProto(ProtoInputStream in, long fieldId)
            throws Exception {
        long token = in.start(fieldId);
        Function<Resources, Icon> icon = RemoteViewsSerializers.createIconFromProto(in);
        in.end(token);
        return (context, resources, rootData, depth) -> icon.apply(resources);
    }

    private static void writeColorStateListToProto(ProtoOutputStream out,
            ColorStateList colorStateList, long fieldId) {
        long token = out.start(fieldId);
        colorStateList.writeToProto(out);
        out.end(token);
    }

    private static ColorStateList createColorStateListFromProto(ProtoInputStream in, long fieldId)
            throws Exception {
        long token = in.start(fieldId);
        ColorStateList colorStateList = ColorStateList.createFromProto(in);
        in.end(token);
        return colorStateList;
    }

    private static CharSequence createCharSequenceFromProto(ProtoInputStream in, long fieldId)
            throws Exception {
        long token = in.start(fieldId);
        CharSequence cs = RemoteViewsSerializers.createCharSequenceFromProto(in);
        in.end(token);
        return cs;
    }

}
