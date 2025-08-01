/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.am;

import static android.app.ActivityManager.PROCESS_CAPABILITY_ALL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_ALL_IMPLICIT;
import static android.app.ActivityManager.PROCESS_CAPABILITY_BFSL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_CPU_TIME;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_CAMERA;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_LOCATION;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
import static android.app.ActivityManager.PROCESS_CAPABILITY_NONE;
import static android.app.ActivityManager.PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK;
import static android.app.ActivityManager.PROCESS_CAPABILITY_USER_RESTRICTED_NETWORK;
import static android.app.ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_BOUND_TOP;
import static android.app.ActivityManager.PROCESS_STATE_CACHED_ACTIVITY;
import static android.app.ActivityManager.PROCESS_STATE_CACHED_ACTIVITY_CLIENT;
import static android.app.ActivityManager.PROCESS_STATE_CACHED_EMPTY;
import static android.app.ActivityManager.PROCESS_STATE_CACHED_RECENT;
import static android.app.ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND;
import static android.app.ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND;
import static android.app.ActivityManager.PROCESS_STATE_LAST_ACTIVITY;
import static android.app.ActivityManager.PROCESS_STATE_NONEXISTENT;
import static android.app.ActivityManager.PROCESS_STATE_PERSISTENT;
import static android.app.ActivityManager.PROCESS_STATE_PERSISTENT_UI;
import static android.app.ActivityManager.PROCESS_STATE_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_TOP;
import static android.app.ActivityManager.PROCESS_STATE_TRANSIENT_BACKGROUND;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_ACTIVITY;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_ALLOWLIST;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_BACKUP;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_BIND_SERVICE;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_COMPONENT_DISABLED;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_EXECUTING_SERVICE;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_FINISH_RECEIVER;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_FOLLOW_UP;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_GET_PROVIDER;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_NONE;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_PROCESS_BEGIN;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_PROCESS_END;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_RECONFIGURATION;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_REMOVE_PROVIDER;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_REMOVE_TASK;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_RESTRICTION_CHANGE;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_SHELL;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_SHORT_FGS_TIMEOUT;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_START_RECEIVER;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_START_SERVICE;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_STOP_SERVICE;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_SYSTEM_INIT;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_UID_IDLE;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_UI_VISIBILITY;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_UNBIND_SERVICE;
import static android.content.Context.BIND_TREAT_LIKE_VISIBLE_FOREGROUND_SERVICE;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
import static android.media.audio.Flags.roForegroundAudioControl;
import static android.os.Process.THREAD_GROUP_BACKGROUND;
import static android.os.Process.THREAD_GROUP_DEFAULT;
import static android.os.Process.THREAD_GROUP_FOREGROUND_WINDOW;
import static android.os.Process.THREAD_GROUP_RESTRICTED;
import static android.os.Process.THREAD_GROUP_TOP_APP;
import static android.os.Process.THREAD_PRIORITY_DISPLAY;
import static android.os.Process.THREAD_PRIORITY_TOP_APP_BOOST;

import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_ALL;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_BACKUP;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_LRU;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_OOM_ADJ;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_OOM_ADJ_REASON;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_PROCESS_OBSERVERS;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_PSS;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_UID_OBSERVERS;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_USAGE_STATS;
import static com.android.server.am.ActivityManagerService.DISPATCH_OOM_ADJ_OBSERVER_MSG;
import static com.android.server.am.ActivityManagerService.FOLLOW_UP_OOMADJUSTER_UPDATE_MSG;
import static com.android.server.am.ActivityManagerService.IDLE_UIDS_MSG;
import static com.android.server.am.ActivityManagerService.TAG_BACKUP;
import static com.android.server.am.ActivityManagerService.TAG_LRU;
import static com.android.server.am.ActivityManagerService.TAG_OOM_ADJ;
import static com.android.server.am.ActivityManagerService.TAG_UID_OBSERVERS;
import static com.android.server.am.AppProfiler.TAG_PSS;
import static com.android.server.am.PlatformCompatCache.CACHED_COMPAT_CHANGE_CAMERA_MICROPHONE_CAPABILITY;
import static com.android.server.am.PlatformCompatCache.CACHED_COMPAT_CHANGE_PROCESS_CAPABILITY;
import static com.android.server.am.PlatformCompatCache.CACHED_COMPAT_CHANGE_USE_SHORT_FGS_USAGE_INTERACTION_TIME;
import static com.android.server.am.ProcessList.BACKUP_APP_ADJ;
import static com.android.server.am.ProcessList.CACHED_APP_IMPORTANCE_LEVELS;
import static com.android.server.am.ProcessList.CACHED_APP_MAX_ADJ;
import static com.android.server.am.ProcessList.CACHED_APP_MIN_ADJ;
import static com.android.server.am.ProcessList.FOREGROUND_APP_ADJ;
import static com.android.server.am.ProcessList.HEAVY_WEIGHT_APP_ADJ;
import static com.android.server.am.ProcessList.HOME_APP_ADJ;
import static com.android.server.am.ProcessList.INVALID_ADJ;
import static com.android.server.am.ProcessList.PERCEPTIBLE_APP_ADJ;
import static com.android.server.am.ProcessList.PERCEPTIBLE_LOW_APP_ADJ;
import static com.android.server.am.ProcessList.PERCEPTIBLE_MEDIUM_APP_ADJ;
import static com.android.server.am.ProcessList.PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ;
import static com.android.server.am.ProcessList.PERSISTENT_SERVICE_ADJ;
import static com.android.server.am.ProcessList.PREVIOUS_APP_ADJ;
import static com.android.server.am.ProcessList.PREVIOUS_APP_MAX_ADJ;
import static com.android.server.am.ProcessList.SCHED_GROUP_BACKGROUND;
import static com.android.server.am.ProcessList.SCHED_GROUP_DEFAULT;
import static com.android.server.am.ProcessList.SCHED_GROUP_FOREGROUND_WINDOW;
import static com.android.server.am.ProcessList.SCHED_GROUP_RESTRICTED;
import static com.android.server.am.ProcessList.SCHED_GROUP_TOP_APP;
import static com.android.server.am.ProcessList.SCHED_GROUP_TOP_APP_BOUND;
import static com.android.server.am.ProcessList.SERVICE_ADJ;
import static com.android.server.am.ProcessList.SERVICE_B_ADJ;
import static com.android.server.am.ProcessList.TAG_PROCESS_OBSERVERS;
import static com.android.server.am.ProcessList.UNKNOWN_ADJ;
import static com.android.server.am.ProcessList.VISIBLE_APP_ADJ;
import static com.android.server.wm.ActivityTaskManagerDebugConfig.DEBUG_SWITCH;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityManagerInternal.OomAdjReason;
import android.app.ActivityThread;
import android.app.AppProtoEnums;
import android.app.ApplicationExitInfo;
import android.app.usage.UsageEvents;
import android.compat.annotation.ChangeId;
import android.compat.annotation.EnabledAfter;
import android.compat.annotation.EnabledSince;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;
import android.net.NetworkPolicyManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Trace;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;
import android.util.proto.ProtoOutputStream;

import com.android.internal.annotations.CompositeRWLock;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.ServiceThread;
import com.android.server.am.PlatformCompatCache.CachedCompatChangeId;
import com.android.server.wm.ActivityServiceConnectionsHolder;
import com.android.server.wm.WindowProcessController;

import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * All of the code required to compute proc states and oom_adj values.
 */
public class OomAdjuster {
    static final String TAG = "OomAdjuster";

    public static final int oomAdjReasonToProto(@OomAdjReason int oomReason) {
        switch (oomReason) {
            case OOM_ADJ_REASON_NONE:
                return AppProtoEnums.OOM_ADJ_REASON_NONE;
            case OOM_ADJ_REASON_ACTIVITY:
                return AppProtoEnums.OOM_ADJ_REASON_ACTIVITY;
            case OOM_ADJ_REASON_FINISH_RECEIVER:
                return AppProtoEnums.OOM_ADJ_REASON_FINISH_RECEIVER;
            case OOM_ADJ_REASON_START_RECEIVER:
                return AppProtoEnums.OOM_ADJ_REASON_START_RECEIVER;
            case OOM_ADJ_REASON_BIND_SERVICE:
                return AppProtoEnums.OOM_ADJ_REASON_BIND_SERVICE;
            case OOM_ADJ_REASON_UNBIND_SERVICE:
                return AppProtoEnums.OOM_ADJ_REASON_UNBIND_SERVICE;
            case OOM_ADJ_REASON_START_SERVICE:
                return AppProtoEnums.OOM_ADJ_REASON_START_SERVICE;
            case OOM_ADJ_REASON_GET_PROVIDER:
                return AppProtoEnums.OOM_ADJ_REASON_GET_PROVIDER;
            case OOM_ADJ_REASON_REMOVE_PROVIDER:
                return AppProtoEnums.OOM_ADJ_REASON_REMOVE_PROVIDER;
            case OOM_ADJ_REASON_UI_VISIBILITY:
                return AppProtoEnums.OOM_ADJ_REASON_UI_VISIBILITY;
            case OOM_ADJ_REASON_ALLOWLIST:
                return AppProtoEnums.OOM_ADJ_REASON_ALLOWLIST;
            case OOM_ADJ_REASON_PROCESS_BEGIN:
                return AppProtoEnums.OOM_ADJ_REASON_PROCESS_BEGIN;
            case OOM_ADJ_REASON_PROCESS_END:
                return AppProtoEnums.OOM_ADJ_REASON_PROCESS_END;
            case OOM_ADJ_REASON_SHORT_FGS_TIMEOUT:
                return AppProtoEnums.OOM_ADJ_REASON_SHORT_FGS_TIMEOUT;
            case OOM_ADJ_REASON_SYSTEM_INIT:
                return AppProtoEnums.OOM_ADJ_REASON_SYSTEM_INIT;
            case OOM_ADJ_REASON_BACKUP:
                return AppProtoEnums.OOM_ADJ_REASON_BACKUP;
            case OOM_ADJ_REASON_SHELL:
                return AppProtoEnums.OOM_ADJ_REASON_SHELL;
            case OOM_ADJ_REASON_REMOVE_TASK:
                return AppProtoEnums.OOM_ADJ_REASON_REMOVE_TASK;
            case OOM_ADJ_REASON_UID_IDLE:
                return AppProtoEnums.OOM_ADJ_REASON_UID_IDLE;
            case OOM_ADJ_REASON_STOP_SERVICE:
                return AppProtoEnums.OOM_ADJ_REASON_STOP_SERVICE;
            case OOM_ADJ_REASON_EXECUTING_SERVICE:
                return AppProtoEnums.OOM_ADJ_REASON_EXECUTING_SERVICE;
            case OOM_ADJ_REASON_RESTRICTION_CHANGE:
                return AppProtoEnums.OOM_ADJ_REASON_RESTRICTION_CHANGE;
            case OOM_ADJ_REASON_COMPONENT_DISABLED:
                return AppProtoEnums.OOM_ADJ_REASON_COMPONENT_DISABLED;
            case OOM_ADJ_REASON_FOLLOW_UP:
                return AppProtoEnums.OOM_ADJ_REASON_FOLLOW_UP;
            case OOM_ADJ_REASON_RECONFIGURATION:
                return AppProtoEnums.OOM_ADJ_REASON_RECONFIGURATION;
            default:
                return AppProtoEnums.OOM_ADJ_REASON_UNKNOWN_TO_PROTO;
        }
    }

    public static final String oomAdjReasonToString(@OomAdjReason int oomReason) {
        final String OOM_ADJ_REASON_METHOD = "updateOomAdj";
        switch (oomReason) {
            case OOM_ADJ_REASON_NONE:
                return OOM_ADJ_REASON_METHOD + "_meh";
            case OOM_ADJ_REASON_ACTIVITY:
                return OOM_ADJ_REASON_METHOD + "_activityChange";
            case OOM_ADJ_REASON_FINISH_RECEIVER:
                return OOM_ADJ_REASON_METHOD + "_finishReceiver";
            case OOM_ADJ_REASON_START_RECEIVER:
                return OOM_ADJ_REASON_METHOD + "_startReceiver";
            case OOM_ADJ_REASON_BIND_SERVICE:
                return OOM_ADJ_REASON_METHOD + "_bindService";
            case OOM_ADJ_REASON_UNBIND_SERVICE:
                return OOM_ADJ_REASON_METHOD + "_unbindService";
            case OOM_ADJ_REASON_START_SERVICE:
                return OOM_ADJ_REASON_METHOD + "_startService";
            case OOM_ADJ_REASON_GET_PROVIDER:
                return OOM_ADJ_REASON_METHOD + "_getProvider";
            case OOM_ADJ_REASON_REMOVE_PROVIDER:
                return OOM_ADJ_REASON_METHOD + "_removeProvider";
            case OOM_ADJ_REASON_UI_VISIBILITY:
                return OOM_ADJ_REASON_METHOD + "_uiVisibility";
            case OOM_ADJ_REASON_ALLOWLIST:
                return OOM_ADJ_REASON_METHOD + "_allowlistChange";
            case OOM_ADJ_REASON_PROCESS_BEGIN:
                return OOM_ADJ_REASON_METHOD + "_processBegin";
            case OOM_ADJ_REASON_PROCESS_END:
                return OOM_ADJ_REASON_METHOD + "_processEnd";
            case OOM_ADJ_REASON_SHORT_FGS_TIMEOUT:
                return OOM_ADJ_REASON_METHOD + "_shortFgs";
            case OOM_ADJ_REASON_SYSTEM_INIT:
                return OOM_ADJ_REASON_METHOD + "_systemInit";
            case OOM_ADJ_REASON_BACKUP:
                return OOM_ADJ_REASON_METHOD + "_backup";
            case OOM_ADJ_REASON_SHELL:
                return OOM_ADJ_REASON_METHOD + "_shell";
            case OOM_ADJ_REASON_REMOVE_TASK:
                return OOM_ADJ_REASON_METHOD + "_removeTask";
            case OOM_ADJ_REASON_UID_IDLE:
                return OOM_ADJ_REASON_METHOD + "_uidIdle";
            case OOM_ADJ_REASON_STOP_SERVICE:
                return OOM_ADJ_REASON_METHOD + "_stopService";
            case OOM_ADJ_REASON_EXECUTING_SERVICE:
                return OOM_ADJ_REASON_METHOD + "_executingService";
            case OOM_ADJ_REASON_RESTRICTION_CHANGE:
                return OOM_ADJ_REASON_METHOD + "_restrictionChange";
            case OOM_ADJ_REASON_COMPONENT_DISABLED:
                return OOM_ADJ_REASON_METHOD + "_componentDisabled";
            case OOM_ADJ_REASON_FOLLOW_UP:
                return OOM_ADJ_REASON_METHOD + "_followUp";
            case OOM_ADJ_REASON_RECONFIGURATION:
                return OOM_ADJ_REASON_METHOD + "_reconfiguration";
            default:
                return "_unknown";
        }
    }

    /**
     * Flag {@link android.content.Context#BIND_INCLUDE_CAPABILITIES} is used
     * to pass while-in-use capabilities from client process to bound service. In targetSdkVersion
     * R and above, if client is a TOP activity, when this flag is present, bound service gets all
     * while-in-use capabilities; when this flag is not present, bound service gets no while-in-use
     * capability from client.
     */
    @ChangeId
    @EnabledAfter(targetSdkVersion=android.os.Build.VERSION_CODES.Q)
    static final long PROCESS_CAPABILITY_CHANGE_ID = 136274596L;

    /**
     * In targetSdkVersion R and above, foreground service has camera and microphone while-in-use
     * capability only when the {@link android.R.attr#foregroundServiceType} is configured as
     * {@link android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_CAMERA} and
     * {@link android.content.pm.ServiceInfo#FOREGROUND_SERVICE_TYPE_MICROPHONE} respectively in the
     * manifest file.
     * In targetSdkVersion below R, foreground service automatically have camera and microphone
     * capabilities.
     */
    @ChangeId
    @EnabledAfter(targetSdkVersion=android.os.Build.VERSION_CODES.Q)
    static final long CAMERA_MICROPHONE_CAPABILITY_CHANGE_ID = 136219221L;

    /**
     * For apps targeting S+, this determines whether to use a shorter timeout before elevating the
     * standby bucket to ACTIVE when apps start a foreground service.
     */
    @ChangeId
    @EnabledSince(targetSdkVersion = android.os.Build.VERSION_CODES.S)
    static final long USE_SHORT_FGS_USAGE_INTERACTION_TIME = 183972877L;

    /**
     * Service for optimizing resource usage from background apps.
     */
    CachedAppOptimizer mCachedAppOptimizer;

    /**
     * Re-rank apps getting a cache oom adjustment from lru to weighted order
     * based on weighted scores for LRU, PSS and cache use count.
     */
    CacheOomRanker mCacheOomRanker;

    ActivityManagerConstants mConstants;

    final long[] mTmpLong = new long[3];

    /**
     * Current sequence id for oom_adj computation traversal.
     */
    int mAdjSeq = 0;

    /**
     * Keep track of the number of service processes we last found, to
     * determine on the next iteration which should be B services.
     */
    int mNumServiceProcs = 0;
    int mNewNumAServiceProcs = 0;
    int mNewNumServiceProcs = 0;

    /**
     * Keep track of the non-cached/empty process we last found, to help
     * determine how to distribute cached/empty processes next time.
     */
    int mNumNonCachedProcs = 0;

    /**
     * Keep track of the number of cached hidden procs, to balance oom adj
     * distribution between those and empty procs.
     */
    int mNumCachedHiddenProcs = 0;

    /** Track all uids that have actively running processes. */
    @CompositeRWLock({"mService", "mProcLock"})
    ActiveUids mActiveUids;

    /**
     * The handler to execute {@link #setProcessGroup} (it may be heavy if the process has many
     * threads) for reducing the time spent in {@link #applyOomAdjLSP}.
     */
    private final Handler mProcessGroupHandler;

    protected final int[] mTmpSchedGroup = new int[1];

    final ActivityManagerService mService;
    final Injector mInjector;
    final GlobalState mGlobalState;
    final ProcessList mProcessList;
    final ActivityManagerGlobalLock mProcLock;

    private final int mNumSlots;
    protected final ArrayList<ProcessRecord> mTmpProcessList = new ArrayList<ProcessRecord>();
    protected final ArrayList<ProcessRecord> mTmpProcessList2 = new ArrayList<ProcessRecord>();
    protected final ArrayList<UidRecord> mTmpBecameIdle = new ArrayList<UidRecord>();
    protected final ActiveUids mTmpUidRecords;
    protected final ArrayDeque<ProcessRecord> mTmpQueue;
    protected final ArraySet<ProcessRecord> mTmpProcessSet = new ArraySet<>();
    protected final ArraySet<ProcessRecord> mPendingProcessSet = new ArraySet<>();
    protected final ArraySet<ProcessRecord> mProcessesInCycle = new ArraySet<>();

    /**
     * List of processes that we want to batch for LMKD to adjust their respective
     * OOM scores.
     */
    @GuardedBy("mService")
    protected final ArrayList<ProcessRecord> mProcsToOomAdj = new ArrayList<ProcessRecord>();

    /**
     * Flag to mark if there is an ongoing oomAdjUpdate: potentially the oomAdjUpdate
     * could be called recursively because of the indirect calls during the update;
     * however the oomAdjUpdate itself doesn't support recursion - in this case we'd
     * have to queue up the new targets found during the update, and perform another
     * round of oomAdjUpdate at the end of last update.
     */
    @GuardedBy("mService")
    private boolean mOomAdjUpdateOngoing = false;

    /**
     * Flag to mark if there is a pending full oomAdjUpdate.
     */
    @GuardedBy("mService")
    private boolean mPendingFullOomAdjUpdate = false;

    /**
     * Most recent reason string. We update it in sync with the trace.
     */
    @OomAdjReason
    protected int mLastReason;

    private final OomAdjusterDebugLogger mLogger;

    /**
     * The process state of the current TOP app.
     */
    @GuardedBy("mService")
    protected int mProcessStateCurTop = PROCESS_STATE_TOP;

    @GuardedBy("mService")
    private final ArraySet<ProcessRecord> mFollowUpUpdateSet = new ArraySet<>();

    private static final long NO_FOLLOW_UP_TIME = Long.MAX_VALUE;
    @GuardedBy("mService")
    private long mNextFollowUpUpdateUptimeMs = NO_FOLLOW_UP_TIME;

    /**
     * The oom score a client needs to be to raise a service with UI out of cache.
     */
    private static final int CACHING_UI_SERVICE_CLIENT_ADJ_THRESHOLD =
            Flags.raiseBoundUiServiceThreshold() ? SERVICE_ADJ : PERCEPTIBLE_APP_ADJ;

    static final long PERCEPTIBLE_TASK_TIMEOUT_MILLIS = 5 * 60 * 1000;

    @VisibleForTesting
    public static class Injector {
        boolean isChangeEnabled(@CachedCompatChangeId int cachedCompatChangeId,
                ApplicationInfo app, boolean defaultValue) {
            return PlatformCompatCache.getInstance()
                    .isChangeEnabled(cachedCompatChangeId, app, defaultValue);
        }

        long getUptimeMillis() {
            return SystemClock.uptimeMillis();
        }

        long getElapsedRealtimeMillis() {
            return SystemClock.elapsedRealtime();
        }

        void batchSetOomAdj(ArrayList<ProcessRecord> procsToOomAdj) {
            ProcessList.batchSetOomAdj(procsToOomAdj);
        }

        void setOomAdj(int pid, int uid, int adj) {
            ProcessList.setOomAdj(pid, uid, adj);
        }

        void setThreadPriority(int tid, int priority) {
            Process.setThreadPriority(tid, priority);
        }
    }

    // TODO(b/346822474): hook up global state usage.
    interface GlobalState {
        /** Is device's screen on. */
        boolean isAwake();

        /** What process is running a backup for a given userId. */
        ProcessRecord getBackupTarget(@UserIdInt int userId);

        /** Is memory level normal since last evaluation. */
        boolean isLastMemoryLevelNormal();
    }

    boolean isChangeEnabled(@CachedCompatChangeId int cachedCompatChangeId,
            ApplicationInfo app, boolean defaultValue) {
        return mInjector.isChangeEnabled(cachedCompatChangeId, app, defaultValue);
    }

    static ServiceThread createAdjusterThread() {
        // The process group is usually critical to the response time of foreground app, so the
        // setter should apply it as soon as possible.
        final ServiceThread adjusterThread =
                new ServiceThread(TAG, THREAD_PRIORITY_TOP_APP_BOOST, false /* allowIo */);
        adjusterThread.start();
        return adjusterThread;
    }

    OomAdjuster(ActivityManagerService service, ProcessList processList, ActiveUids activeUids,
            ServiceThread adjusterThread, GlobalState globalState,
            CachedAppOptimizer cachedAppOptimizer, Injector injector) {
        mService = service;
        mGlobalState = globalState;
        mInjector = injector;
        mProcessList = processList;
        mProcLock = service.mProcLock;
        mActiveUids = activeUids;

        mConstants = mService.mConstants;
        mCachedAppOptimizer = cachedAppOptimizer;
        mCacheOomRanker = new CacheOomRanker(service);

        mLogger = new OomAdjusterDebugLogger(this, mService.mConstants);

        mProcessGroupHandler = new Handler(adjusterThread.getLooper(), msg -> {
            final int group = msg.what;
            final ProcessRecord app = (ProcessRecord) msg.obj;
            setProcessGroup(app.getPid(), group, app.processName);
            if (Flags.phantomProcessesFix()) {
                mService.mPhantomProcessList.setProcessGroupForPhantomProcessOfApp(app, group);
            }
            return true;
        });
        mTmpUidRecords = new ActiveUids(service, false);
        mTmpQueue = new ArrayDeque<ProcessRecord>(mConstants.CUR_MAX_CACHED_PROCESSES << 1);
        mNumSlots = ((CACHED_APP_MAX_ADJ - CACHED_APP_MIN_ADJ + 1) >> 1)
                / CACHED_APP_IMPORTANCE_LEVELS;
    }

    void setProcessGroup(int pid, int group, String processName) {
        if (pid == ActivityManagerService.MY_PID) {
            // Skip setting the process group for system_server, keep it as default.
            return;
        }
        final boolean traceEnabled = Trace.isTagEnabled(Trace.TRACE_TAG_ACTIVITY_MANAGER);
        if (traceEnabled) {
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "setProcessGroup "
                    + processName + " to " + group);
        }
        try {
            android.os.Process.setProcessGroup(pid, group);
        } catch (Exception e) {
            if (DEBUG_ALL) {
                Slog.w(TAG, "Failed setting process group of " + pid + " to " + group, e);
            }
        } finally {
            if (traceEnabled) {
                Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
            }
        }
    }

    void setAppAndChildProcessGroup(ProcessRecord app, int group) {
        mProcessGroupHandler.sendMessage(mProcessGroupHandler.obtainMessage(
                group, app));
    }

    void initSettings() {
        mCachedAppOptimizer.init();
        mCacheOomRanker.init(ActivityThread.currentApplication().getMainExecutor());
        if (mService.mConstants.KEEP_WARMING_SERVICES.size() > 0) {
            final IntentFilter filter = new IntentFilter(Intent.ACTION_USER_SWITCHED);
            mService.mContext.registerReceiverForAllUsers(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    synchronized (mService) {
                        handleUserSwitchedLocked();
                    }
                }
            }, filter, null, mService.mHandler);
        }
    }

    /**
     * Update the keep-warming service flags upon user switches
     */
    @VisibleForTesting
    @GuardedBy("mService")
    void handleUserSwitchedLocked() {
        mProcessList.forEachLruProcessesLOSP(false,
                this::updateKeepWarmIfNecessaryForProcessLocked);
    }

    @GuardedBy("mService")
    private void updateKeepWarmIfNecessaryForProcessLocked(final ProcessRecord app) {
        final ArraySet<ComponentName> warmServices = mService.mConstants.KEEP_WARMING_SERVICES;
        boolean includeWarmPkg = false;
        final PackageList pkgList = app.getPkgList();
        for (int j = warmServices.size() - 1; j >= 0; j--) {
            if (pkgList.containsKey(warmServices.valueAt(j).getPackageName())) {
                includeWarmPkg = true;
                break;
            }
        }
        if (!includeWarmPkg) {
            return;
        }
        final ProcessServiceRecord psr = app.mServices;
        for (int j = psr.numberOfRunningServices() - 1; j >= 0; j--) {
            psr.getRunningServiceAt(j).updateKeepWarmLocked();
        }
    }

    /**
     * Update OomAdj for all processes in LRU list
     */
    @GuardedBy("mService")
    void updateOomAdjLocked(@OomAdjReason int oomAdjReason) {
        synchronized (mProcLock) {
            updateOomAdjLSP(oomAdjReason);
        }
    }

    @GuardedBy({"mService", "mProcLock"})
    private void updateOomAdjLSP(@OomAdjReason int oomAdjReason) {
        if (checkAndEnqueueOomAdjTargetLocked(null)) {
            // Simply return as there is an oomAdjUpdate ongoing
            return;
        }
        try {
            mOomAdjUpdateOngoing = true;
            performUpdateOomAdjLSP(oomAdjReason);
        } finally {
            // Kick off the handling of any pending targets enqueued during the above update
            mOomAdjUpdateOngoing = false;
            updateOomAdjPendingTargetsLocked(oomAdjReason);
        }
    }

    @GuardedBy({"mService", "mProcLock"})
    protected void performUpdateOomAdjLSP(@OomAdjReason int oomAdjReason) {
        final ProcessRecord topApp = mService.getTopApp();
        mProcessStateCurTop = mService.mAtmInternal.getTopProcessState();
        // Clear any pending ones because we are doing a full update now.
        mPendingProcessSet.clear();
        mService.mAppProfiler.mHasPreviousProcess = mService.mAppProfiler.mHasHomeProcess = false;
        updateOomAdjInnerLSP(oomAdjReason, topApp , null, null, true, true);
    }

    /**
     * Update OomAdj for specific process and its reachable processes (with direction/indirect
     * bindings from this process); Note its clients' proc state won't be re-evaluated if this proc
     * is hosting any service/content provider.
     *
     * @param app The process to update, or null to update all processes
     * @param oomAdjReason
     */
    @GuardedBy("mService")
    boolean updateOomAdjLocked(ProcessRecord app, @OomAdjReason int oomAdjReason) {
        synchronized (mProcLock) {
            return updateOomAdjLSP(app, oomAdjReason);
        }
    }

    @GuardedBy({"mService", "mProcLock"})
    private boolean updateOomAdjLSP(ProcessRecord app, @OomAdjReason int oomAdjReason) {
        if (app == null || !mConstants.OOMADJ_UPDATE_QUICK) {
            updateOomAdjLSP(oomAdjReason);
            return true;
        }

        if (checkAndEnqueueOomAdjTargetLocked(app)) {
            // Simply return true as there is an oomAdjUpdate ongoing
            return true;
        }

        try {
            mOomAdjUpdateOngoing = true;
            return performUpdateOomAdjLSP(app, oomAdjReason);
        } finally {
            // Kick off the handling of any pending targets enqueued during the above update
            mOomAdjUpdateOngoing = false;
            updateOomAdjPendingTargetsLocked(oomAdjReason);
        }
    }

    @GuardedBy({"mService", "mProcLock"})
    protected boolean performUpdateOomAdjLSP(ProcessRecord app, @OomAdjReason int oomAdjReason) {
        final ProcessRecord topApp = mService.getTopApp();

        mLastReason = oomAdjReason;
        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, oomAdjReasonToString(oomAdjReason));

        final ProcessStateRecord state = app.mState;

        // Next to find out all its reachable processes
        ArrayList<ProcessRecord> processes = mTmpProcessList;
        ActiveUids uids = mTmpUidRecords;
        mPendingProcessSet.add(app);
        mProcessStateCurTop = enqueuePendingTopAppIfNecessaryLSP();

        boolean containsCycle = collectReachableProcessesLocked(mPendingProcessSet,
                processes, uids);

        // Clear the pending set as they should've been included in 'processes'.
        mPendingProcessSet.clear();

        int size = processes.size();
        if (size > 0) {
            // Update these reachable processes
            updateOomAdjInnerLSP(oomAdjReason, topApp, processes, uids, containsCycle, false);
        } else if (state.getCurRawAdj() == UNKNOWN_ADJ) {
            // In case the app goes from non-cached to cached but it doesn't have other reachable
            // processes, its adj could be still unknown as of now, assign one.
            processes.add(app);
            applyLruAdjust(processes);
            applyOomAdjLSP(app, false, mInjector.getUptimeMillis(),
                    mInjector.getElapsedRealtimeMillis(), oomAdjReason);
        }
        mTmpProcessList.clear();
        mService.clearPendingTopAppLocked();
        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
        return true;
    }

    @GuardedBy({"mService", "mProcLock"})
    protected int enqueuePendingTopAppIfNecessaryLSP() {
        final int prevTopProcessState = mService.mAtmInternal.getTopProcessState();
        mService.enqueuePendingTopAppIfNecessaryLocked();
        final int topProcessState = mService.mAtmInternal.getTopProcessState();
        if (prevTopProcessState != topProcessState) {
            // Unlikely but possible: WM just updated the top process state, it may have
            // enqueued the new top app to the pending top UID list. Enqueue that one here too.
            mService.enqueuePendingTopAppIfNecessaryLocked();
        }
        return topProcessState;
    }

    /**
     * Collect the reachable processes from the given {@code apps}, the result will be
     * returned in the given {@code processes}, which will include the processes from
     * the given {@code apps}.
     */
    @GuardedBy("mService")
    protected boolean collectReachableProcessesLocked(ArraySet<ProcessRecord> apps,
            ArrayList<ProcessRecord> processes, ActiveUids uids) {
        final ArrayDeque<ProcessRecord> queue = mTmpQueue;
        queue.clear();
        processes.clear();
        for (int i = 0, size = apps.size(); i < size; i++) {
            final ProcessRecord app = apps.valueAt(i);
            app.mState.setReachable(true);
            queue.offer(app);
        }

        uids.clear();

        // Track if any of them reachables could include a cycle
        boolean containsCycle = false;
        // Scan downstreams of the process record
        for (ProcessRecord pr = queue.poll(); pr != null; pr = queue.poll()) {
            processes.add(pr);
            final UidRecord uidRec = pr.getUidRecord();
            if (uidRec != null) {
                uids.put(uidRec.getUid(), uidRec);
            }
            final ProcessServiceRecord psr = pr.mServices;
            for (int i = psr.numberOfConnections() - 1; i >= 0; i--) {
                ConnectionRecord cr = psr.getConnectionAt(i);
                ProcessRecord service = cr.hasFlag(ServiceInfo.FLAG_ISOLATED_PROCESS)
                        ? cr.binding.service.isolationHostProc : cr.binding.service.app;
                if (service == null || service == pr
                        || ((service.mState.getMaxAdj() >= ProcessList.SYSTEM_ADJ)
                                && (service.mState.getMaxAdj() < FOREGROUND_APP_ADJ))) {
                    continue;
                }
                containsCycle |= service.mState.isReachable();
                if (service.mState.isReachable()) {
                    continue;
                }
                if (cr.hasFlag(Context.BIND_WAIVE_PRIORITY)
                        && cr.notHasFlag(Context.BIND_TREAT_LIKE_ACTIVITY
                        | Context.BIND_ADJUST_WITH_ACTIVITY)) {
                    continue;
                }
                queue.offer(service);
                service.mState.setReachable(true);
            }
            final ProcessProviderRecord ppr = pr.mProviders;
            for (int i = ppr.numberOfProviderConnections() - 1; i >= 0; i--) {
                ContentProviderConnection cpc = ppr.getProviderConnectionAt(i);
                ProcessRecord provider = cpc.provider.proc;
                if (provider == null || provider == pr
                        || ((provider.mState.getMaxAdj() >= ProcessList.SYSTEM_ADJ)
                                && (provider.mState.getMaxAdj() < FOREGROUND_APP_ADJ))) {
                    continue;
                }
                containsCycle |= provider.mState.isReachable();
                if (provider.mState.isReachable()) {
                    continue;
                }
                queue.offer(provider);
                provider.mState.setReachable(true);
            }
            // See if this process has any corresponding SDK sandbox processes running, and if so
            // scan them as well.
            final List<ProcessRecord> sdkSandboxes =
                    mProcessList.getSdkSandboxProcessesForAppLocked(pr.uid);
            final int numSdkSandboxes = sdkSandboxes != null ? sdkSandboxes.size() : 0;
            for (int i = numSdkSandboxes - 1; i >= 0; i--) {
                ProcessRecord sdkSandbox = sdkSandboxes.get(i);
                containsCycle |= sdkSandbox.mState.isReachable();
                if (sdkSandbox.mState.isReachable()) {
                    continue;
                }
                queue.offer(sdkSandbox);
                sdkSandbox.mState.setReachable(true);
            }
            // If this process is a sandbox itself, also scan the app on whose behalf its running
            if (pr.isSdkSandbox) {
                for (int is = psr.numberOfRunningServices() - 1; is >= 0; is--) {
                    ServiceRecord s = psr.getRunningServiceAt(is);
                    ArrayMap<IBinder, ArrayList<ConnectionRecord>> serviceConnections =
                            s.getConnections();
                    for (int conni = serviceConnections.size() - 1; conni >= 0; conni--) {
                        ArrayList<ConnectionRecord> clist = serviceConnections.valueAt(conni);
                        for (int i = clist.size() - 1; i >= 0; i--) {
                            ConnectionRecord cr = clist.get(i);
                            ProcessRecord attributedApp = cr.binding.attributedClient;
                            if (attributedApp == null || attributedApp == pr
                                    || ((attributedApp.mState.getMaxAdj() >= ProcessList.SYSTEM_ADJ)
                                    && (attributedApp.mState.getMaxAdj() < FOREGROUND_APP_ADJ))) {
                                continue;
                            }
                            if (attributedApp.mState.isReachable()) {
                                continue;
                            }
                            queue.offer(attributedApp);
                            attributedApp.mState.setReachable(true);
                        }
                    }
                }
            }
        }

        int size = processes.size();
        if (size > 0) {
            // Reverse the process list, since the updateOomAdjInnerLSP scans from the end of it.
            for (int l = 0, r = size - 1; l < r; l++, r--) {
                final ProcessRecord t = processes.get(l);
                final ProcessRecord u = processes.get(r);
                t.mState.setReachable(false);
                u.mState.setReachable(false);
                processes.set(l, u);
                processes.set(r, t);
            }
        }

        return containsCycle;
    }

    /**
     * Enqueue the given process for a later oom adj update
     */
    @GuardedBy("mService")
    void enqueueOomAdjTargetLocked(ProcessRecord app) {
        if (app != null && app.mState.getMaxAdj() > FOREGROUND_APP_ADJ) {
            mPendingProcessSet.add(app);
        }
    }

    @GuardedBy("mService")
    void removeOomAdjTargetLocked(ProcessRecord app, boolean procDied) {
        if (app != null) {
            mPendingProcessSet.remove(app);
            if (procDied) {
                PlatformCompatCache.getInstance().invalidate(app.info);
            }
        }
    }

    /**
     * Check if there is an ongoing oomAdjUpdate, enqueue the given process record
     * to {@link #mPendingProcessSet} if there is one.
     *
     * @param app The target app to get an oomAdjUpdate, or a full oomAdjUpdate if it's null.
     * @return {@code true} if there is an ongoing oomAdjUpdate.
     */
    @GuardedBy("mService")
    private boolean checkAndEnqueueOomAdjTargetLocked(@Nullable ProcessRecord app) {
        if (!mOomAdjUpdateOngoing) {
            return false;
        }
        if (app != null) {
            mPendingProcessSet.add(app);
        } else {
            mPendingFullOomAdjUpdate = true;
        }
        return true;
    }

    /**
     * Kick off an oom adj update pass for the pending targets which are enqueued via
     * {@link #enqueueOomAdjTargetLocked}.
     */
    @GuardedBy("mService")
    void updateOomAdjPendingTargetsLocked(@OomAdjReason int oomAdjReason) {
        // First check if there is pending full update
        if (mPendingFullOomAdjUpdate) {
            mPendingFullOomAdjUpdate = false;
            mPendingProcessSet.clear();
            updateOomAdjLocked(oomAdjReason);
            return;
        }
        if (mPendingProcessSet.isEmpty()) {
            return;
        }

        if (mOomAdjUpdateOngoing) {
            // There's another oomAdjUpdate ongoing, return from here now;
            // that ongoing update would call us again at the end of it.
            return;
        }
        try {
            mOomAdjUpdateOngoing = true;
            performUpdateOomAdjPendingTargetsLocked(oomAdjReason);
        } finally {
            // Kick off the handling of any pending targets enqueued during the above update
            mOomAdjUpdateOngoing = false;
            updateOomAdjPendingTargetsLocked(oomAdjReason);
        }
    }

    @GuardedBy("mService")
    void updateOomAdjFollowUpTargetsLocked() {
        final long now = mInjector.getUptimeMillis();
        long nextFollowUpUptimeMs = Long.MAX_VALUE;
        mNextFollowUpUpdateUptimeMs = NO_FOLLOW_UP_TIME;
        for (int i = mFollowUpUpdateSet.size() - 1; i >= 0; i--) {
            final ProcessRecord proc = mFollowUpUpdateSet.valueAtUnchecked(i);
            final long followUpUptimeMs = proc.mState.getFollowupUpdateUptimeMs();

            if (proc.isKilled()) {
                // Process is dead, just remove from follow up set.
                mFollowUpUpdateSet.removeAt(i);
            } else if (followUpUptimeMs <= now) {
                // Add processes that need a follow up update.
                mPendingProcessSet.add(proc);
                proc.mState.setFollowupUpdateUptimeMs(NO_FOLLOW_UP_TIME);
                mFollowUpUpdateSet.removeAt(i);
            } else if (followUpUptimeMs < nextFollowUpUptimeMs) {
                // Figure out when to schedule the next follow up update.
                nextFollowUpUptimeMs = followUpUptimeMs;
            } else if (followUpUptimeMs == NO_FOLLOW_UP_TIME) {
                // The follow up is no longer needed for this process.
                mFollowUpUpdateSet.removeAt(i);
            }
        }

        if (nextFollowUpUptimeMs != Long.MAX_VALUE) {
            // There is still at least one process that needs a follow up.
            scheduleFollowUpOomAdjusterUpdateLocked(nextFollowUpUptimeMs, now);
        }

        updateOomAdjPendingTargetsLocked(OOM_ADJ_REASON_FOLLOW_UP);
    }

    @GuardedBy("mService")
    protected void performUpdateOomAdjPendingTargetsLocked(@OomAdjReason int oomAdjReason) {
        final ProcessRecord topApp = mService.getTopApp();

        mLastReason = oomAdjReason;
        Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, oomAdjReasonToString(oomAdjReason));
        mProcessStateCurTop = enqueuePendingTopAppIfNecessaryLSP();

        final ArrayList<ProcessRecord> processes = mTmpProcessList;
        final ActiveUids uids = mTmpUidRecords;
        collectReachableProcessesLocked(mPendingProcessSet, processes, uids);
        mPendingProcessSet.clear();
        synchronized (mProcLock) {
            updateOomAdjInnerLSP(oomAdjReason, topApp, processes, uids, true, false);
        }
        processes.clear();
        mService.clearPendingTopAppLocked();

        Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
    }

    /**
     * Update OomAdj for all processes within the given list (could be partial), or the whole LRU
     * list if the given list is null; when it's partial update, each process's client proc won't
     * get evaluated recursively here.
     *
     * <p>Note: If the given {@code processes} is not null, the expectation to it is, the caller
     * must have called {@link collectReachableProcessesLocked} on it.
     */
    @GuardedBy({"mService", "mProcLock"})
    private void updateOomAdjInnerLSP(@OomAdjReason int oomAdjReason, final ProcessRecord topApp,
            ArrayList<ProcessRecord> processes, ActiveUids uids, boolean potentialCycles,
            boolean startProfiling) {
        final boolean fullUpdate = processes == null;
        final ArrayList<ProcessRecord> activeProcesses = fullUpdate
                ? mProcessList.getLruProcessesLOSP() : processes;
        ActiveUids activeUids = uids;
        if (activeUids == null) {
            final int numUids = mActiveUids.size();
            activeUids = mTmpUidRecords;
            activeUids.clear();
            for (int i = 0; i < numUids; i++) {
                UidRecord uidRec = mActiveUids.valueAt(i);
                activeUids.put(uidRec.getUid(), uidRec);
            }
        }

        mLastReason = oomAdjReason;
        if (startProfiling) {
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, oomAdjReasonToString(oomAdjReason));
        }
        final long now = mInjector.getUptimeMillis();
        final long nowElapsed = mInjector.getElapsedRealtimeMillis();
        final long oldTime = now - mConstants.mMaxEmptyTimeMillis;
        final int numProc = activeProcesses.size();

        mAdjSeq++;
        if (fullUpdate) {
            mNewNumServiceProcs = 0;
            mNewNumAServiceProcs = 0;
        }

        // Reset state in all uid records.
        resetUidRecordsLsp(activeUids);

        boolean retryCycles = false;
        boolean computeClients = fullUpdate || potentialCycles;

        // need to reset cycle state before calling computeOomAdjLSP because of service conns
        for (int i = numProc - 1; i >= 0; i--) {
            ProcessRecord app = activeProcesses.get(i);
            final ProcessStateRecord state = app.mState;
            state.setReachable(false);
            // No need to compute again it has been evaluated in previous iteration
            if (state.getAdjSeq() != mAdjSeq) {
                state.setContainsCycle(false);
                state.setCurRawProcState(PROCESS_STATE_CACHED_EMPTY);
                state.setCurRawAdj(UNKNOWN_ADJ);
                state.setSetCapability(PROCESS_CAPABILITY_NONE);
                state.resetCachedInfo();
                state.setCurBoundByNonBgRestrictedApp(false);
            }
        }
        mProcessesInCycle.clear();
        for (int i = numProc - 1; i >= 0; i--) {
            ProcessRecord app = activeProcesses.get(i);
            final ProcessStateRecord state = app.mState;
            if (!app.isKilledByAm() && app.getThread() != null) {
                state.setProcStateChanged(false);
                app.mOptRecord.setLastOomAdjChangeReason(oomAdjReason);
                // It won't enter cycle if not computing clients.
                computeOomAdjLSP(app, UNKNOWN_ADJ, topApp, fullUpdate, now, false,
                        computeClients, oomAdjReason, true);
                // if any app encountered a cycle, we need to perform an additional loop later
                retryCycles |= state.containsCycle();
                // Keep the completedAdjSeq to up to date.
                state.setCompletedAdjSeq(mAdjSeq);
            }
        }

        if (mCacheOomRanker.useOomReranking()) {
            mCacheOomRanker.reRankLruCachedAppsLSP(mProcessList.getLruProcessesLSP(),
                    mProcessList.getLruProcessServiceStartLOSP());
        }

        if (computeClients) { // There won't be cycles if we didn't compute clients above.
            // Cycle strategy:
            // - Retry computing any process that has encountered a cycle.
            // - Continue retrying until no process was promoted.
            // - Iterate from least important to most important.
            int cycleCount = 0;
            while (retryCycles && cycleCount < 10) {
                cycleCount++;
                retryCycles = false;

                for (int i = 0; i < numProc; i++) {
                    ProcessRecord app = activeProcesses.get(i);
                    final ProcessStateRecord state = app.mState;
                    if (!app.isKilledByAm() && app.getThread() != null && state.containsCycle()) {
                        state.decAdjSeq();
                        state.decCompletedAdjSeq();
                    }
                }

                for (int i = 0; i < numProc; i++) {
                    ProcessRecord app = activeProcesses.get(i);
                    final ProcessStateRecord state = app.mState;
                    if (!app.isKilledByAm() && app.getThread() != null && state.containsCycle()) {
                        if (computeOomAdjLSP(app, UNKNOWN_ADJ, topApp, true, now,
                                true, true, oomAdjReason, true)) {
                            retryCycles = true;
                        }
                    }
                }
            }
        }
        mProcessesInCycle.clear();

        applyLruAdjust(mProcessList.getLruProcessesLOSP());

        postUpdateOomAdjInnerLSP(oomAdjReason, activeUids, now, nowElapsed, oldTime, true);

        if (startProfiling) {
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
        }
    }

    @GuardedBy({"mService", "mProcLock"})
    private void resetUidRecordsLsp(@NonNull ActiveUids activeUids) {
        // Reset state in all uid records.
        for (int  i = activeUids.size() - 1; i >= 0; i--) {
            final UidRecord uidRec = activeUids.valueAt(i);
            if (DEBUG_UID_OBSERVERS) {
                Slog.i(TAG_UID_OBSERVERS, "Starting update of " + uidRec);
            }
            uidRec.reset();
        }
    }

    @GuardedBy({"mService", "mProcLock"})
    protected void postUpdateOomAdjInnerLSP(@OomAdjReason int oomAdjReason, ActiveUids activeUids,
            long now, long nowElapsed, long oldTime, boolean doingAll) {
        mNumNonCachedProcs = 0;
        mNumCachedHiddenProcs = 0;

        updateAndTrimProcessLSP(now, nowElapsed, oldTime, activeUids,
                oomAdjReason, doingAll);
        mNumServiceProcs = mNewNumServiceProcs;

        if (mService.mAlwaysFinishActivities) {
            // Need to do this on its own message because the stack may not
            // be in a consistent state at this point.
            mService.mAtmInternal.scheduleDestroyAllActivities("always-finish");
        }

        updateUidsLSP(activeUids, nowElapsed);

        synchronized (mService.mProcessStats.mLock) {
            final long nowUptime = mInjector.getUptimeMillis();
            if (mService.mProcessStats.shouldWriteNowLocked(nowUptime)) {
                mService.mHandler.post(new ActivityManagerService.ProcStatsRunnable(mService,
                        mService.mProcessStats));
            }

            // Run this after making sure all procstates are updated.
            mService.mProcessStats.updateTrackingAssociationsLocked(mAdjSeq, nowUptime);
        }

        if (DEBUG_OOM_ADJ) {
            final long duration = mInjector.getUptimeMillis() - now;
            if (false) {
                Slog.d(TAG_OOM_ADJ, "Did OOM ADJ in " + duration + "ms",
                        new RuntimeException("here").fillInStackTrace());
            } else {
                Slog.d(TAG_OOM_ADJ, "Did OOM ADJ in " + duration + "ms");
            }
        }
    }

    @GuardedBy({"mService", "mProcLock"})
    protected void applyLruAdjust(ArrayList<ProcessRecord> lruList) {
        final int numLru = lruList.size();
        int nextPreviousAppAdj = PREVIOUS_APP_ADJ;
        if (mConstants.USE_TIERED_CACHED_ADJ) {
            final long now = mInjector.getUptimeMillis();
            int uiTargetAdj = 10;
            // mConstants.TIERED_CACHED_ADJ_UI_TIER_SIZE is 10 by default, but is configurable.
            final int uiTierMaxAdj = 10 + mConstants.TIERED_CACHED_ADJ_UI_TIER_SIZE;
            for (int i = numLru - 1; i >= 0; i--) {
                ProcessRecord app = lruList.get(i);
                final ProcessStateRecord state = app.mState;
                final ProcessCachedOptimizerRecord opt = app.mOptRecord;
                final int curAdj = state.getCurAdj();
                if (PREVIOUS_APP_ADJ <= curAdj && curAdj <= PREVIOUS_APP_MAX_ADJ) {
                    state.setCurAdj(nextPreviousAppAdj);
                    nextPreviousAppAdj = Math.min(nextPreviousAppAdj + 1, PREVIOUS_APP_MAX_ADJ);
                } else if (!app.isKilledByAm() && app.getThread() != null && (curAdj >= UNKNOWN_ADJ
                            || (state.hasShownUi() && curAdj >= CACHED_APP_MIN_ADJ))) {
                    final ProcessServiceRecord psr = app.mServices;
                    int targetAdj = CACHED_APP_MIN_ADJ;

                    if (opt != null && opt.isFreezeExempt()) {
                        // BIND_WAIVE_PRIORITY and the like get oom_adj 900
                        targetAdj += 0;
                    } else if (state.hasShownUi() && uiTargetAdj < uiTierMaxAdj) {
                        // The most recent UI-showing apps get [910, 910 + ui tier size).
                        targetAdj += uiTargetAdj++;
                    } else if ((state.getSetAdj() >= CACHED_APP_MIN_ADJ)
                            && (state.getLastStateTime()
                                    + mConstants.TIERED_CACHED_ADJ_DECAY_TIME) < now) {
                        // Older cached apps get 940 + ui tier size (950 by default).
                        targetAdj += 40 + mConstants.TIERED_CACHED_ADJ_UI_TIER_SIZE;
                    } else {
                        // Newer cached apps get 910 + ui tier size (920 by default).
                        targetAdj += 10 + mConstants.TIERED_CACHED_ADJ_UI_TIER_SIZE;
                    }
                    state.setCurRawAdj(targetAdj);
                    state.setCurAdj(psr.modifyRawOomAdj(targetAdj));
                }
            }
        } else {
            // First update the OOM adjustment for each of the
            // application processes based on their current state.
            int curCachedAdj = CACHED_APP_MIN_ADJ;
            int nextCachedAdj = curCachedAdj + (CACHED_APP_IMPORTANCE_LEVELS * 2);
            int curCachedImpAdj = 0;
            int curEmptyAdj = CACHED_APP_MIN_ADJ + CACHED_APP_IMPORTANCE_LEVELS;
            int nextEmptyAdj = curEmptyAdj + (CACHED_APP_IMPORTANCE_LEVELS * 2);

            final int emptyProcessLimit = mConstants.CUR_MAX_EMPTY_PROCESSES;
            final int cachedProcessLimit = mConstants.CUR_MAX_CACHED_PROCESSES
                                           - emptyProcessLimit;
            // Let's determine how many processes we have running vs.
            // how many slots we have for background processes; we may want
            // to put multiple processes in a slot of there are enough of
            // them.
            int numEmptyProcs = numLru - mNumNonCachedProcs - mNumCachedHiddenProcs;
            if (numEmptyProcs > cachedProcessLimit) {
                // If there are more empty processes than our limit on cached
                // processes, then use the cached process limit for the factor.
                // This ensures that the really old empty processes get pushed
                // down to the bottom, so if we are running low on memory we will
                // have a better chance at keeping around more cached processes
                // instead of a gazillion empty processes.
                numEmptyProcs = cachedProcessLimit;
            }
            int cachedFactor = (mNumCachedHiddenProcs > 0
                    ? (mNumCachedHiddenProcs + mNumSlots - 1) : 1)
                               / mNumSlots;
            if (cachedFactor < 1) cachedFactor = 1;

            int emptyFactor = (numEmptyProcs + mNumSlots - 1) / mNumSlots;
            if (emptyFactor < 1) emptyFactor = 1;

            int stepCached = -1;
            int stepEmpty = -1;
            int lastCachedGroup = 0;
            int lastCachedGroupImportance = 0;
            int lastCachedGroupUid = 0;


            for (int i = numLru - 1; i >= 0; i--) {
                ProcessRecord app = lruList.get(i);
                final ProcessStateRecord state = app.mState;
                final int curAdj = state.getCurAdj();
                if (PREVIOUS_APP_ADJ <= curAdj && curAdj <= PREVIOUS_APP_MAX_ADJ) {
                    state.setCurAdj(nextPreviousAppAdj);
                    nextPreviousAppAdj = Math.min(nextPreviousAppAdj + 1, PREVIOUS_APP_MAX_ADJ);
                } else if (!app.isKilledByAm() && app.getThread() != null
                               && curAdj >= UNKNOWN_ADJ) {
                    // If we haven't yet assigned the final cached adj to the process, do that now.
                    final ProcessServiceRecord psr = app.mServices;
                    switch (state.getCurProcState()) {
                        case PROCESS_STATE_LAST_ACTIVITY:
                        case PROCESS_STATE_CACHED_ACTIVITY:
                        case ActivityManager.PROCESS_STATE_CACHED_ACTIVITY_CLIENT:
                        case ActivityManager.PROCESS_STATE_CACHED_RECENT:
                            // Figure out the next cached level, taking into account groups.
                            boolean inGroup = false;
                            final int connectionGroup = psr.getConnectionGroup();
                            if (connectionGroup != 0) {
                                final int connectionImportance = psr.getConnectionImportance();
                                if (lastCachedGroupUid == app.uid
                                    && lastCachedGroup == connectionGroup) {
                                    // This is in the same group as the last process, just tweak
                                    // adjustment by importance.
                                    if (connectionImportance > lastCachedGroupImportance) {
                                        lastCachedGroupImportance = connectionImportance;
                                        if (curCachedAdj < nextCachedAdj
                                            && curCachedAdj < CACHED_APP_MAX_ADJ) {
                                            curCachedImpAdj++;
                                        }
                                    }
                                    inGroup = true;
                                } else {
                                    lastCachedGroupUid = app.uid;
                                    lastCachedGroup = connectionGroup;
                                    lastCachedGroupImportance = connectionImportance;
                                }
                            }
                            if (!inGroup && curCachedAdj != nextCachedAdj) {
                                stepCached++;
                                curCachedImpAdj = 0;
                                if (stepCached >= cachedFactor) {
                                    stepCached = 0;
                                    curCachedAdj = nextCachedAdj;
                                    nextCachedAdj += CACHED_APP_IMPORTANCE_LEVELS * 2;
                                    if (nextCachedAdj > CACHED_APP_MAX_ADJ) {
                                        nextCachedAdj = CACHED_APP_MAX_ADJ;
                                    }
                                }
                            }
                            // This process is a cached process holding activities...
                            // assign it the next cached value for that type, and then
                            // step that cached level.
                            state.setCurRawAdj(curCachedAdj + curCachedImpAdj);
                            state.setCurAdj(psr.modifyRawOomAdj(curCachedAdj + curCachedImpAdj));
                            if (DEBUG_LRU) {
                                Slog.d(TAG_LRU, "Assigning activity LRU #" + i
                                        + " adj: " + state.getCurAdj()
                                        + " (curCachedAdj=" + curCachedAdj
                                        + " curCachedImpAdj=" + curCachedImpAdj + ")");
                            }
                            break;
                        default:
                            // Figure out the next cached level.
                            if (curEmptyAdj != nextEmptyAdj) {
                                stepEmpty++;
                                if (stepEmpty >= emptyFactor) {
                                    stepEmpty = 0;
                                    curEmptyAdj = nextEmptyAdj;
                                    nextEmptyAdj += CACHED_APP_IMPORTANCE_LEVELS * 2;
                                    if (nextEmptyAdj > CACHED_APP_MAX_ADJ) {
                                        nextEmptyAdj = CACHED_APP_MAX_ADJ;
                                    }
                                }
                            }
                            // For everything else, assign next empty cached process
                            // level and bump that up.  Note that this means that
                            // long-running services that have dropped down to the
                            // cached level will be treated as empty (since their process
                            // state is still as a service), which is what we want.
                            state.setCurRawAdj(curEmptyAdj);
                            state.setCurAdj(psr.modifyRawOomAdj(curEmptyAdj));
                            if (DEBUG_LRU) {
                                Slog.d(TAG_LRU, "Assigning empty LRU #" + i
                                        + " adj: " + state.getCurAdj()
                                        + " (curEmptyAdj=" + curEmptyAdj
                                        + ")");
                            }
                            break;
                    }
                }
            }
        }
    }
    private long mNextNoKillDebugMessageTime;

    private double mLastFreeSwapPercent = 1.00;

    private static double getFreeSwapPercent() {
        return CachedAppOptimizer.getFreeSwapPercent();
    }

    @GuardedBy({"mService", "mProcLock"})
    private void updateAndTrimProcessLSP(final long now, final long nowElapsed,
            final long oldTime, final ActiveUids activeUids, @OomAdjReason int oomAdjReason,
            boolean doingAll) {
        ArrayList<ProcessRecord> lruList = mProcessList.getLruProcessesLOSP();
        final int numLru = lruList.size();

        final boolean doKillExcessiveProcesses = shouldKillExcessiveProcesses(now);
        if (!doKillExcessiveProcesses) {
            if (mNextNoKillDebugMessageTime < now) {
                Slog.d(TAG, "Not killing cached processes"); // STOPSHIP Remove it b/222365734
                mNextNoKillDebugMessageTime = now + 5000; // Every 5 seconds
            }
        }
        final int emptyProcessLimit = doKillExcessiveProcesses
                ? mConstants.CUR_MAX_EMPTY_PROCESSES : Integer.MAX_VALUE;
        final int cachedProcessLimit = doKillExcessiveProcesses
                ? (mConstants.CUR_MAX_CACHED_PROCESSES - emptyProcessLimit) : Integer.MAX_VALUE;
        int lastCachedGroup = 0;
        int lastCachedGroupUid = 0;
        int numCached = 0;
        int numCachedExtraGroup = 0;
        int numEmpty = 0;
        int numTrimming = 0;

        boolean proactiveKillsEnabled = mConstants.PROACTIVE_KILLS_ENABLED;
        double lowSwapThresholdPercent = mConstants.LOW_SWAP_THRESHOLD_PERCENT;
        double freeSwapPercent =  proactiveKillsEnabled ? getFreeSwapPercent() : 1.00;
        ProcessRecord lruCachedApp = null;

        for (int i = numLru - 1; i >= 0; i--) {
            ProcessRecord app = lruList.get(i);
            final ProcessStateRecord state = app.mState;
            if (!app.isKilledByAm() && app.getThread() != null) {
                if (!Flags.fixApplyOomadjOrder()) {
                    // We don't need to apply the update for the process which didn't get computed
                    if (state.getCompletedAdjSeq() == mAdjSeq) {
                        applyOomAdjLSP(app, doingAll, now, nowElapsed, oomAdjReason, true);
                    }
                }

                if (app.isPendingFinishAttach()) {
                    // Avoid trimming processes that are still initializing. If they aren't
                    // hosting any components yet because they may be unfairly killed.
                    // We however apply the oom scores set at #setAttachingProcessStatesLSP.
                    updateAppUidRecLSP(app);
                    continue;
                }

                final ProcessServiceRecord psr = app.mServices;
                // Count the number of process types.
                switch (state.getCurProcState()) {
                    case PROCESS_STATE_CACHED_ACTIVITY:
                    case ActivityManager.PROCESS_STATE_CACHED_ACTIVITY_CLIENT:
                        mNumCachedHiddenProcs++;
                        numCached++;
                        final int connectionGroup = psr.getConnectionGroup();
                        if (connectionGroup != 0) {
                            if (lastCachedGroupUid == app.info.uid
                                    && lastCachedGroup == connectionGroup) {
                                // If this process is the next in the same group, we don't
                                // want it to count against our limit of the number of cached
                                // processes, so bump up the group count to account for it.
                                numCachedExtraGroup++;
                            } else {
                                lastCachedGroupUid = app.info.uid;
                                lastCachedGroup = connectionGroup;
                            }
                        } else {
                            lastCachedGroupUid = lastCachedGroup = 0;
                        }
                        if ((numCached - numCachedExtraGroup) > cachedProcessLimit) {
                            app.killLocked("cached #" + numCached,
                                    "too many cached",
                                    ApplicationExitInfo.REASON_OTHER,
                                    ApplicationExitInfo.SUBREASON_TOO_MANY_CACHED,
                                    true);
                        } else if (proactiveKillsEnabled) {
                            lruCachedApp = app;
                        }
                        break;
                    case PROCESS_STATE_CACHED_EMPTY:
                        if (numEmpty > mConstants.CUR_TRIM_EMPTY_PROCESSES
                                && app.getLastActivityTime() < oldTime) {
                            app.killLocked("empty for " + ((now
                                    - app.getLastActivityTime()) / 1000) + "s",
                                    "empty for too long",
                                    ApplicationExitInfo.REASON_OTHER,
                                    ApplicationExitInfo.SUBREASON_TRIM_EMPTY,
                                    true);
                        } else {
                            numEmpty++;
                            if (numEmpty > emptyProcessLimit) {
                                app.killLocked("empty #" + numEmpty,
                                        "too many empty",
                                        ApplicationExitInfo.REASON_OTHER,
                                        ApplicationExitInfo.SUBREASON_TOO_MANY_EMPTY,
                                        true);
                            } else if (proactiveKillsEnabled) {
                                lruCachedApp = app;
                            }
                        }
                        break;
                    default:
                        mNumNonCachedProcs++;
                        break;
                }

                // TODO: b/319163103 - limit isolated/sandbox trimming to just the processes
                //  evaluated in the current update.
                if (app.isolated && psr.numberOfRunningServices() <= 0
                        && app.getIsolatedEntryPoint() == null) {
                    // If this is an isolated process, there are no services
                    // running in it, and it's not a special process with a
                    // custom entry point, then the process is no longer
                    // needed.  We agressively kill these because we can by
                    // definition not re-use the same process again, and it is
                    // good to avoid having whatever code was running in them
                    // left sitting around after no longer needed.
                    app.killLocked("isolated not needed", ApplicationExitInfo.REASON_OTHER,
                            ApplicationExitInfo.SUBREASON_ISOLATED_NOT_NEEDED, true);
                } else if (app.isSdkSandbox && psr.numberOfRunningServices() <= 0
                        && app.getActiveInstrumentation() == null) {
                    // If this is an SDK sandbox process and there are no services running it, we
                    // aggressively kill the sandbox as we usually don't want to re-use the same
                    // sandbox again.
                    app.killLocked("sandbox not needed", ApplicationExitInfo.REASON_OTHER,
                            ApplicationExitInfo.SUBREASON_SDK_SANDBOX_NOT_NEEDED, true);
                } else {
                    // Keeping this process, update its uid.
                    updateAppUidRecLSP(app);
                }

                if (state.getCurProcState() >= ActivityManager.PROCESS_STATE_HOME
                        && !app.isKilledByAm()) {
                    numTrimming++;
                }
            }
        }

        if (Flags.fixApplyOomadjOrder()) {
            // We need to apply the update starting from the least recently used.
            // Otherwise, they won't be in the correct LRU order in LMKD.
            for (int i = 0; i < numLru; i++) {
                ProcessRecord app = lruList.get(i);
                // We don't need to apply the update for the process which didn't get computed
                if (!app.isKilledByAm() && app.getThread() != null
                        && app.mState.getCompletedAdjSeq() == mAdjSeq) {
                    applyOomAdjLSP(app, doingAll, now, nowElapsed, oomAdjReason, true);
                }
            }
        }

        if (!mProcsToOomAdj.isEmpty()) {
            mInjector.batchSetOomAdj(mProcsToOomAdj);
            mProcsToOomAdj.clear();
        }

        if (proactiveKillsEnabled                               // Proactive kills enabled?
                && doKillExcessiveProcesses                     // Should kill excessive processes?
                && freeSwapPercent < lowSwapThresholdPercent    // Swap below threshold?
                && lruCachedApp != null                         // If no cached app, let LMKD decide
                // If swap is non-decreasing, give reclaim a chance to catch up
                && freeSwapPercent < mLastFreeSwapPercent) {
            lruCachedApp.killLocked("swap low and too many cached",
                    ApplicationExitInfo.REASON_OTHER,
                    ApplicationExitInfo.SUBREASON_TOO_MANY_CACHED,
                    true);
        }

        mLastFreeSwapPercent = freeSwapPercent;

        mService.mAppProfiler.updateLowMemStateLSP(numCached, numEmpty, numTrimming, now);
    }

    @GuardedBy({"mService", "mProcLock"})
    protected void updateAppUidRecIfNecessaryLSP(final ProcessRecord app) {
        if (!app.isKilledByAm() && app.getThread() != null) {
            if (app.isolated && app.mServices.numberOfRunningServices() <= 0
                    && app.getIsolatedEntryPoint() == null) {
                // No op.
            } else {
                // Keeping this process, update its uid.
                updateAppUidRecLSP(app);
            }
        }
    }

    @GuardedBy({"mService", "mProcLock"})
    private void updateAppUidRecLSP(ProcessRecord app) {
        final UidRecord uidRec = app.getUidRecord();
        if (uidRec != null) {
            final ProcessStateRecord state = app.mState;
            uidRec.setEphemeral(app.info.isInstantApp());
            if (uidRec.getCurProcState() > state.getCurProcState()) {
                uidRec.setCurProcState(state.getCurProcState());
            }
            if (app.mServices.hasForegroundServices()) {
                uidRec.setForegroundServices(true);
            }
            uidRec.setCurCapability(uidRec.getCurCapability() | state.getCurCapability());
        }
    }

    @GuardedBy({"mService", "mProcLock"})
    protected void updateUidsLSP(ActiveUids activeUids, final long nowElapsed) {
        // This compares previously set procstate to the current procstate in regards to whether
        // or not the app's network access will be blocked. So, this needs to be called before
        // we update the UidRecord's procstate by calling {@link UidRecord#setSetProcState}.
        mProcessList.incrementProcStateSeqAndNotifyAppsLOSP(activeUids);

        ArrayList<UidRecord> becameIdle = mTmpBecameIdle;
        becameIdle.clear();

        // Update from any uid changes.
        if (mService.mLocalPowerManager != null) {
            mService.mLocalPowerManager.startUidChanges();
        }
        for (int i = activeUids.size() - 1; i >= 0; i--) {
            final UidRecord uidRec = activeUids.valueAt(i);
            if (uidRec.getCurProcState() != PROCESS_STATE_NONEXISTENT) {
                if (uidRec.getSetProcState() != uidRec.getCurProcState()
                        || uidRec.getSetCapability() != uidRec.getCurCapability()
                        || uidRec.isSetAllowListed() != uidRec.isCurAllowListed()
                        || uidRec.getProcAdjChanged()) {
                    int uidChange = 0;
                    final boolean shouldLog = mLogger.shouldLog(uidRec.getUid());
                    if (DEBUG_UID_OBSERVERS) {
                        Slog.i(TAG_UID_OBSERVERS, "Changes in " + uidRec
                                + ": proc state from " + uidRec.getSetProcState() + " to "
                                + uidRec.getCurProcState() + ", capability from "
                                + uidRec.getSetCapability() + " to " + uidRec.getCurCapability()
                                + ", allowlist from " + uidRec.isSetAllowListed()
                                + " to " + uidRec.isCurAllowListed()
                                + ", procAdjChanged: " + uidRec.getProcAdjChanged());
                    }
                    if (ActivityManager.isProcStateBackground(uidRec.getCurProcState())
                            && !uidRec.isCurAllowListed()) {
                        // UID is now in the background (and not on the temp allowlist).  Was it
                        // previously in the foreground (or on the temp allowlist)?
                        // Or, it wasn't in the foreground / allowlist, but its last background
                        // timestamp is also 0, this means it's never been in the
                        // foreground / allowlist since it's born at all.
                        if (!ActivityManager.isProcStateBackground(uidRec.getSetProcState())
                                || uidRec.isSetAllowListed()
                                || uidRec.getLastBackgroundTime() == 0) {
                            uidRec.setLastBackgroundTime(nowElapsed);
                            if (shouldLog) {
                                mLogger.logSetLastBackgroundTime(uidRec.getUid(), nowElapsed);
                            }
                            if (mService.mDeterministicUidIdle
                                    || !mService.mHandler.hasMessages(IDLE_UIDS_MSG)) {
                                // Note: the background settle time is in elapsed realtime, while
                                // the handler time base is uptime.  All this means is that we may
                                // stop background uids later than we had intended, but that only
                                // happens because the device was sleeping so we are okay anyway.
                                if (shouldLog) {
                                    mLogger.logScheduleUidIdle1(uidRec.getUid(),
                                            mConstants.BACKGROUND_SETTLE_TIME);
                                }
                                mService.mHandler.sendEmptyMessageDelayed(IDLE_UIDS_MSG,
                                        mConstants.BACKGROUND_SETTLE_TIME); // XXX
                            }
                        }
                        if (uidRec.isIdle() && !uidRec.isSetIdle()) {
                            uidChange |= UidRecord.CHANGE_IDLE;
                            if (uidRec.getSetProcState() != PROCESS_STATE_NONEXISTENT) {
                                // don't stop the bg services if it's just started.
                                becameIdle.add(uidRec);
                            }
                        }
                    } else {
                        if (uidRec.isIdle()) {
                            uidChange |= UidRecord.CHANGE_ACTIVE;
                            EventLogTags.writeAmUidActive(uidRec.getUid());
                            uidRec.setIdle(false);
                        }
                        uidRec.setLastBackgroundTime(0);
                        uidRec.setLastIdleTime(0);
                        if (shouldLog) {
                            mLogger.logClearLastBackgroundTime(uidRec.getUid());
                        }
                    }
                    final boolean wasCached = uidRec.getSetProcState()
                            > ActivityManager.PROCESS_STATE_RECEIVER;
                    final boolean isCached = uidRec.getCurProcState()
                            > ActivityManager.PROCESS_STATE_RECEIVER;
                    if (wasCached != isCached
                            || uidRec.getSetProcState() == PROCESS_STATE_NONEXISTENT) {
                        uidChange |= isCached ? UidRecord.CHANGE_CACHED :
                                UidRecord.CHANGE_UNCACHED;
                    }
                    if (uidRec.getSetCapability() != uidRec.getCurCapability()) {
                        uidChange |= UidRecord.CHANGE_CAPABILITY;
                    }
                    if (uidRec.getSetProcState() != uidRec.getCurProcState()) {
                        uidChange |= UidRecord.CHANGE_PROCSTATE;
                    }
                    if (uidRec.getProcAdjChanged()) {
                        uidChange |= UidRecord.CHANGE_PROCADJ;
                    }
                    int oldProcState = uidRec.getSetProcState();
                    int oldCapability = uidRec.getSetCapability();
                    uidRec.setSetProcState(uidRec.getCurProcState());
                    uidRec.setSetCapability(uidRec.getCurCapability());
                    uidRec.setSetAllowListed(uidRec.isCurAllowListed());
                    uidRec.setSetIdle(uidRec.isIdle());
                    uidRec.clearProcAdjChanged();
                    if (shouldLog
                            && ((uidRec.getSetProcState() != oldProcState)
                            || (uidRec.getSetCapability() != oldCapability))) {
                        int flags = 0;
                        if (uidRec.isSetAllowListed()) {
                            flags |= 1;
                        }
                        mLogger.logUidStateChanged(uidRec.getUid(),
                                uidRec.getSetProcState(), oldProcState,
                                uidRec.getSetCapability(), oldCapability,
                                flags);
                    }
                    if ((uidChange & UidRecord.CHANGE_PROCSTATE) != 0
                            || (uidChange & UidRecord.CHANGE_CAPABILITY) != 0) {
                        mService.mAtmInternal.onUidProcStateChanged(
                                uidRec.getUid(), uidRec.getSetProcState());
                    }
                    if (uidChange != 0) {
                        mService.enqueueUidChangeLocked(uidRec, -1, uidChange);
                    }
                    if ((uidChange & UidRecord.CHANGE_PROCSTATE) != 0
                            || (uidChange & UidRecord.CHANGE_CAPABILITY) != 0) {
                        mService.noteUidProcessStateAndCapability(uidRec.getUid(),
                                uidRec.getCurProcState(), uidRec.getCurCapability());
                    }
                    if ((uidChange & UidRecord.CHANGE_PROCSTATE) != 0) {
                        mService.noteUidProcessState(uidRec.getUid(), uidRec.getCurProcState());
                    }
                    if (uidRec.hasForegroundServices()) {
                        mService.mServices.foregroundServiceProcStateChangedLocked(uidRec);
                    }
                }
            }
            mService.mInternal.deletePendingTopUid(uidRec.getUid(), nowElapsed);
        }
        if (mService.mLocalPowerManager != null) {
            mService.mLocalPowerManager.finishUidChanges();
        }

        int size = becameIdle.size();
        if (size > 0) {
            // If we have any new uids that became idle this time, we need to make sure
            // they aren't left with running services.
            for (int i = size - 1; i >= 0; i--) {
                mService.mServices.stopInBackgroundLocked(becameIdle.get(i).getUid());
            }
        }
    }

    /**
     * Return true if we should kill excessive cached/empty processes.
     */
    private boolean shouldKillExcessiveProcesses(long nowUptime) {
        final long lastUserUnlockingUptime = mService.mUserController.getLastUserUnlockingUptime();

        if (lastUserUnlockingUptime == 0) {
            // No users have been unlocked.
            return !mConstants.mNoKillCachedProcessesUntilBootCompleted;
        }
        final long noKillCachedProcessesPostBootCompletedDurationMillis =
                mConstants.mNoKillCachedProcessesPostBootCompletedDurationMillis;
        if ((lastUserUnlockingUptime + noKillCachedProcessesPostBootCompletedDurationMillis)
                > nowUptime) {
            return false;
        }
        return true;
    }

    protected final ComputeOomAdjWindowCallback mTmpComputeOomAdjWindowCallback =
            new ComputeOomAdjWindowCallback();

    /** These methods are called inline during computeOomAdjLSP(), on the same thread */
    final class ComputeOomAdjWindowCallback {

        ProcessRecord app;
        int adj;
        boolean foregroundActivities;
        boolean mHasVisibleActivities;
        int procState;
        int schedGroup;
        int appUid;
        int logUid;
        int processStateCurTop;
        String mAdjType;
        ProcessStateRecord mState;

        void initialize(ProcessRecord app, int adj, boolean foregroundActivities,
                boolean hasVisibleActivities, int procState, int schedGroup, int appUid,
                int logUid, int processStateCurTop) {
            this.app = app;
            this.adj = adj;
            this.foregroundActivities = foregroundActivities;
            this.mHasVisibleActivities = hasVisibleActivities;
            this.procState = procState;
            this.schedGroup = schedGroup;
            this.appUid = appUid;
            this.logUid = logUid;
            this.processStateCurTop = processStateCurTop;
            mAdjType = app.mState.getAdjType();
            this.mState = app.mState;
        }

        void onVisibleActivity(int flags) {
            // App has a visible activity; only upgrade adjustment.
            if (adj > VISIBLE_APP_ADJ) {
                adj = VISIBLE_APP_ADJ;
                mAdjType = "vis-activity";
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise adj to vis-activity: " + app);
                }
            }
            if (procState > processStateCurTop) {
                procState = processStateCurTop;
                mAdjType = "vis-activity";
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ,
                            "Raise procstate to vis-activity (top): " + app);
                }
            }
            if (schedGroup < SCHED_GROUP_DEFAULT) {
                schedGroup = SCHED_GROUP_DEFAULT;
            }
            if ((flags & WindowProcessController.ACTIVITY_STATE_FLAG_RESUMED_SPLIT_SCREEN) != 0) {
                // Another side of split should be the current global top. Use the same top
                // priority for this non-top split.
                schedGroup = SCHED_GROUP_TOP_APP;
                mAdjType = "resumed-split-screen-activity";
            } else if ((flags
                    & WindowProcessController.ACTIVITY_STATE_FLAG_PERCEPTIBLE_FREEFORM) != 0) {
                // The recently used non-top visible freeform app.
                schedGroup = SCHED_GROUP_TOP_APP;
                mAdjType = "perceptible-freeform-activity";
            } else if ((flags
                    & WindowProcessController.ACTIVITY_STATE_FLAG_VISIBLE_MULTI_WINDOW_MODE) != 0) {
                // Currently the only case is from freeform apps which are not close to top.
                schedGroup = SCHED_GROUP_FOREGROUND_WINDOW;
                mAdjType = "vis-multi-window-activity";
            }
            foregroundActivities = true;
            mHasVisibleActivities = true;
        }

        void onPausedActivity() {
            if (adj > PERCEPTIBLE_APP_ADJ) {
                adj = PERCEPTIBLE_APP_ADJ;
                mAdjType = "pause-activity";
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise adj to pause-activity: "  + app);
                }
            }
            if (procState > processStateCurTop) {
                procState = processStateCurTop;
                mAdjType = "pause-activity";
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ,
                            "Raise procstate to pause-activity (top): "  + app);
                }
            }
            if (schedGroup < SCHED_GROUP_DEFAULT) {
                schedGroup = SCHED_GROUP_DEFAULT;
            }
            foregroundActivities = true;
            mHasVisibleActivities = false;
        }

        void onStoppingActivity(boolean finishing) {
            if (adj > PERCEPTIBLE_APP_ADJ) {
                adj = PERCEPTIBLE_APP_ADJ;
                mAdjType = "stop-activity";
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ,
                            "Raise adj to stop-activity: "  + app);
                }
            }

            // For the process state, we will at this point consider the process to be cached. It
            // will be cached either as an activity or empty depending on whether the activity is
            // finishing. We do this so that we can treat the process as cached for purposes of
            // memory trimming (determining current memory level, trim command to send to process)
            // since there can be an arbitrary number of stopping processes and they should soon all
            // go into the cached state.
            if (!finishing) {
                if (procState > PROCESS_STATE_LAST_ACTIVITY) {
                    procState = PROCESS_STATE_LAST_ACTIVITY;
                    mAdjType = "stop-activity";
                    if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                        reportOomAdjMessageLocked(TAG_OOM_ADJ,
                                "Raise procstate to stop-activity: " + app);
                    }
                }
            }
            foregroundActivities = true;
            mHasVisibleActivities = false;
        }

        void onOtherActivity(long perceptibleTaskStoppedTimeMillis) {
            if (procState > PROCESS_STATE_CACHED_ACTIVITY) {
                procState = PROCESS_STATE_CACHED_ACTIVITY;
                mAdjType = "cch-act";
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ,
                            "Raise procstate to cached activity: " + app);
                }
            }
            if (Flags.perceptibleTasks() && adj > PERCEPTIBLE_MEDIUM_APP_ADJ) {
                if (perceptibleTaskStoppedTimeMillis >= 0) {
                    final long now = mInjector.getUptimeMillis();
                    if (now - perceptibleTaskStoppedTimeMillis < PERCEPTIBLE_TASK_TIMEOUT_MILLIS) {
                        adj = PERCEPTIBLE_MEDIUM_APP_ADJ;
                        mAdjType = "perceptible-act";
                        if (procState > PROCESS_STATE_IMPORTANT_BACKGROUND) {
                            procState = PROCESS_STATE_IMPORTANT_BACKGROUND;
                        }

                        maybeSetProcessFollowUpUpdateLocked(app,
                                perceptibleTaskStoppedTimeMillis + PERCEPTIBLE_TASK_TIMEOUT_MILLIS,
                                now);
                    } else if (adj > PREVIOUS_APP_ADJ) {
                        adj = PREVIOUS_APP_ADJ;
                        mAdjType = "stale-perceptible-act";
                        if (procState > PROCESS_STATE_LAST_ACTIVITY) {
                            procState = PROCESS_STATE_LAST_ACTIVITY;
                        }
                    }
                }
            }
            mHasVisibleActivities = false;
        }
    }

    private boolean isDeviceFullyAwake() {
        if (Flags.pushGlobalStateToOomadjuster()) {
            return mGlobalState.isAwake();
        } else {
            return mService.mWakefulness.get() == PowerManagerInternal.WAKEFULNESS_AWAKE;
        }
    }

    private boolean isScreenOnOrAnimatingLocked(ProcessStateRecord state) {
        return isDeviceFullyAwake() || state.isRunningRemoteAnimation();
    }

    private boolean isBackupProcess(ProcessRecord app) {
        if (Flags.pushGlobalStateToOomadjuster()) {
            return app == mGlobalState.getBackupTarget(app.userId);
        } else {
            final BackupRecord backupTarget = mService.mBackupTargets.get(app.userId);
            if (backupTarget == null) {
                return false;
            }
            return app == backupTarget.app;
        }
    }

    private boolean isLastMemoryLevelNormal() {
        if (Flags.pushGlobalStateToOomadjuster()) {
            return mGlobalState.isLastMemoryLevelNormal();
        } else {
            return mService.mAppProfiler.isLastMemoryLevelNormal();
        }
    }

    @GuardedBy({"mService", "mProcLock"})
    protected boolean computeOomAdjLSP(ProcessRecord app, int cachedAdj,
            ProcessRecord topApp, boolean doingAll, long now, boolean cycleReEval,
            boolean computeClients, int oomAdjReason, boolean couldRecurse) {
        final ProcessStateRecord state = app.mState;
        if (couldRecurse) {
            if (mAdjSeq == state.getAdjSeq()) {
                if (state.getAdjSeq() == state.getCompletedAdjSeq()) {
                    // This adjustment has already been computed successfully.
                    return false;
                } else {
                    // The process is being computed, so there is a cycle. We cannot
                    // rely on this process's state.
                    state.setContainsCycle(true);
                    mProcessesInCycle.add(app);

                    return false;
                }
            }
        }

        int prevAppAdj = getInitialAdj(app);
        int prevProcState = getInitialProcState(app);
        int prevCapability = getInitialCapability(app);

        // Remove any follow up update this process might have. It will be rescheduled if still
        // needed.
        app.mState.setFollowupUpdateUptimeMs(NO_FOLLOW_UP_TIME);

        if (app.getThread() == null) {
            state.setAdjSeq(mAdjSeq);
            state.setCurrentSchedulingGroup(SCHED_GROUP_BACKGROUND);
            state.setCurProcState(PROCESS_STATE_CACHED_EMPTY);
            state.setCurRawProcState(PROCESS_STATE_CACHED_EMPTY);
            state.setCurAdj(CACHED_APP_MAX_ADJ);
            state.setCurRawAdj(CACHED_APP_MAX_ADJ);
            state.setCompletedAdjSeq(state.getAdjSeq());
            state.setCurCapability(PROCESS_CAPABILITY_NONE);
            return false;
        }

        state.setAdjTypeCode(ActivityManager.RunningAppProcessInfo.REASON_UNKNOWN);
        state.setAdjSource(null);
        state.setAdjTarget(null);
        if (!couldRecurse || !cycleReEval) {
            // Don't reset this flag when doing cycles re-evaluation.
            state.setNoKillOnBgRestrictedAndIdle(false);
            // If this UID is currently allowlisted, it should not be frozen.
            final UidRecord uidRec = app.getUidRecord();
            app.mOptRecord.setShouldNotFreeze(uidRec != null && uidRec.isCurAllowListed(),
                    ProcessCachedOptimizerRecord.SHOULD_NOT_FREEZE_REASON_UID_ALLOWLISTED, mAdjSeq);
        }

        final int appUid = app.info.uid;
        final int logUid = mService.mCurOomAdjUid;

        final ProcessServiceRecord psr = app.mServices;

        if (state.getMaxAdj() <= FOREGROUND_APP_ADJ) {
            // The max adjustment doesn't allow this app to be anything
            // below foreground, so it is not worth doing work for it.
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Making fixed: " + app);
            }
            state.setAdjType("fixed");
            state.setAdjSeq(mAdjSeq);
            state.setCurRawAdj(state.getMaxAdj());
            state.setHasForegroundActivities(false);
            state.setCurrentSchedulingGroup(SCHED_GROUP_DEFAULT);
            state.setCurCapability(PROCESS_CAPABILITY_ALL); // BFSL allowed
            state.setCurProcState(ActivityManager.PROCESS_STATE_PERSISTENT);
            // System processes can do UI, and when they do we want to have
            // them trim their memory after the user leaves the UI.  To
            // facilitate this, here we need to determine whether or not it
            // is currently showing UI.
            state.setSystemNoUi(true);
            if (app == topApp) {
                state.setSystemNoUi(false);
                state.setCurrentSchedulingGroup(SCHED_GROUP_TOP_APP);
                state.setAdjType("pers-top-activity");
            } else if (state.hasTopUi()) {
                // sched group/proc state adjustment is below
                state.setSystemNoUi(false);
                state.setAdjType("pers-top-ui");
            } else if (state.getCachedHasVisibleActivities()) {
                state.setSystemNoUi(false);
            }
            if (!state.isSystemNoUi()) {
                if (isScreenOnOrAnimatingLocked(state)) {
                    // screen on or animating, promote UI
                    state.setCurProcState(ActivityManager.PROCESS_STATE_PERSISTENT_UI);
                    state.setCurrentSchedulingGroup(SCHED_GROUP_TOP_APP);
                } else if (!app.getWindowProcessController().isShowingUiWhileDozing()) {
                    // screen off, restrict UI scheduling
                    state.setCurProcState(PROCESS_STATE_BOUND_FOREGROUND_SERVICE);
                    state.setCurrentSchedulingGroup(SCHED_GROUP_RESTRICTED);
                }
            }
            state.setCurRawProcState(state.getCurProcState());
            state.setCurAdj(state.getMaxAdj());
            state.setCompletedAdjSeq(state.getAdjSeq());
            // if curAdj is less than prevAppAdj, then this process was promoted
            return state.getCurAdj() < prevAppAdj || state.getCurProcState() < prevProcState;
        }

        state.setSystemNoUi(false);

        final int PROCESS_STATE_CUR_TOP = mProcessStateCurTop;

        // Determine the importance of the process, starting with most
        // important to least, and assign an appropriate OOM adjustment.
        int adj;
        int schedGroup;
        int procState;
        int capability = cycleReEval ? getInitialCapability(app) : 0;

        boolean hasVisibleActivities = false;
        if (app == topApp && PROCESS_STATE_CUR_TOP == PROCESS_STATE_TOP) {
            // The last app on the list is the foreground app.
            adj = FOREGROUND_APP_ADJ;
            if (mService.mAtmInternal.useTopSchedGroupForTopProcess()) {
                schedGroup = SCHED_GROUP_TOP_APP;
                state.setAdjType("top-activity");
            } else {
                // Demote the scheduling group to avoid CPU contention if there is another more
                // important process which also uses top-app, such as if SystemUI is animating.
                schedGroup = SCHED_GROUP_DEFAULT;
                state.setAdjType("intermediate-top-activity");
            }
            hasVisibleActivities = true;
            procState = PROCESS_STATE_TOP;
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Making top: " + app);
            }
        } else if (state.isRunningRemoteAnimation()) {
            adj = VISIBLE_APP_ADJ;
            schedGroup = SCHED_GROUP_TOP_APP;
            state.setAdjType("running-remote-anim");
            procState = PROCESS_STATE_CUR_TOP;
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Making running remote anim: " + app);
            }
        } else if (app.getActiveInstrumentation() != null) {
            // Don't want to kill running instrumentation.
            adj = FOREGROUND_APP_ADJ;
            schedGroup = SCHED_GROUP_DEFAULT;
            state.setAdjType("instrumentation");
            procState = PROCESS_STATE_FOREGROUND_SERVICE;
            capability |= PROCESS_CAPABILITY_BFSL;
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Making instrumentation: " + app);
            }
        } else if (state.getCachedIsReceivingBroadcast(mTmpSchedGroup)) {
            // An app that is currently receiving a broadcast also
            // counts as being in the foreground for OOM killer purposes.
            // It's placed in a sched group based on the nature of the
            // broadcast as reflected by which queue it's active in.
            adj = FOREGROUND_APP_ADJ;
            schedGroup = mTmpSchedGroup[0];
            state.setAdjType("broadcast");
            procState = ActivityManager.PROCESS_STATE_RECEIVER;
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Making broadcast: " + app);
            }
        } else if (psr.numberOfExecutingServices() > 0) {
            // An app that is currently executing a service callback also
            // counts as being in the foreground.
            adj = FOREGROUND_APP_ADJ;
            schedGroup = psr.shouldExecServicesFg()
                    ? SCHED_GROUP_DEFAULT : SCHED_GROUP_BACKGROUND;
            state.setAdjType("exec-service");
            procState = PROCESS_STATE_SERVICE;
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Making exec-service: " + app);
            }
        } else if (app == topApp) {
            adj = FOREGROUND_APP_ADJ;
            schedGroup = SCHED_GROUP_BACKGROUND;
            state.setAdjType("top-sleeping");
            procState = PROCESS_STATE_CUR_TOP;
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Making top (sleeping): " + app);
            }
        } else {
            // As far as we know the process is empty.  We may change our mind later.
            schedGroup = SCHED_GROUP_BACKGROUND;
            // At this point we don't actually know the adjustment.  Use the cached adj
            // value that the caller wants us to.
            adj = cachedAdj;
            procState = PROCESS_STATE_CACHED_EMPTY;
            if (!couldRecurse || !state.containsCycle()) {
                state.setAdjType("cch-empty");
            }
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Making empty: " + app);
            }
        }

        // Examine all non-top activities.
        boolean foregroundActivities = app == topApp;
        if (!foregroundActivities && state.getCachedHasActivities()) {
            state.computeOomAdjFromActivitiesIfNecessary(mTmpComputeOomAdjWindowCallback,
                    adj, foregroundActivities, hasVisibleActivities, procState, schedGroup,
                    appUid, logUid, PROCESS_STATE_CUR_TOP);

            adj = state.getCachedAdj();
            foregroundActivities = state.getCachedForegroundActivities();
            hasVisibleActivities = state.getCachedHasVisibleActivities();
            procState = state.getCachedProcState();
            schedGroup = state.getCachedSchedGroup();
            state.setAdjType(state.getCachedAdjType());
        }

        if (procState > PROCESS_STATE_CACHED_RECENT && state.getCachedHasRecentTasks()) {
            procState = PROCESS_STATE_CACHED_RECENT;
            state.setAdjType("cch-rec");
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise procstate to cached recent: " + app);
            }
        }

        int capabilityFromFGS = 0; // capability from foreground service.

        final boolean hasForegroundServices = psr.hasForegroundServices();
        final boolean hasNonShortForegroundServices = psr.hasNonShortForegroundServices();
        final boolean hasShortForegroundServices = hasForegroundServices
                && !psr.areAllShortForegroundServicesProcstateTimedOut(now);

        // Adjust for FGS or "has-overlay-ui".
        if (adj > PERCEPTIBLE_APP_ADJ
                || procState > PROCESS_STATE_FOREGROUND_SERVICE) {
            String adjType = null;
            int newAdj = 0;
            int newProcState = 0;

            if (hasForegroundServices && hasNonShortForegroundServices) {
                // For regular (non-short) FGS.
                adjType = "fg-service";
                newAdj = PERCEPTIBLE_APP_ADJ;
                newProcState = PROCESS_STATE_FOREGROUND_SERVICE;
                capabilityFromFGS |= PROCESS_CAPABILITY_BFSL;

            } else if (hasShortForegroundServices) {

                // For short FGS.
                adjType = "fg-service-short";

                // We use MEDIUM_APP_ADJ + 1 so we can tell apart EJ
                // (which uses MEDIUM_APP_ADJ + 1)
                // from short-FGS.
                // (We use +1 and +2, not +0 and +1, to be consistent with the following
                // RECENT_FOREGROUND_APP_ADJ tweak)
                newAdj = PERCEPTIBLE_MEDIUM_APP_ADJ + 1;

                // We give the FGS procstate, but not PROCESS_CAPABILITY_BFSL, so
                // short-fgs can't start FGS from the background.
                newProcState = PROCESS_STATE_FOREGROUND_SERVICE;

            } else if (state.hasOverlayUi()) {
                adjType = "has-overlay-ui";
                newAdj = PERCEPTIBLE_APP_ADJ;
                newProcState = PROCESS_STATE_IMPORTANT_FOREGROUND;
            }

            if (adjType != null) {
                adj = newAdj;
                procState = newProcState;
                state.setAdjType(adjType);
                schedGroup = SCHED_GROUP_DEFAULT;

                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise to " + adjType + ": "
                            + app + " ");
                }
            }
        }

        // If the app was recently in the foreground and moved to a foreground service status,
        // allow it to get a higher rank in memory for some time, compared to other foreground
        // services so that it can finish performing any persistence/processing of in-memory state.
        if (psr.hasForegroundServices() && adj > PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ
                && (state.getLastTopTime() + mConstants.TOP_TO_FGS_GRACE_DURATION > now
                || state.getSetProcState() <= PROCESS_STATE_TOP)) {
            if (psr.hasNonShortForegroundServices()) {
                adj = PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ;
                state.setAdjType("fg-service-act");
            } else {
                // For short-service FGS, we +1 the value, so we'll be able to detect it in
                // various dashboards.
                adj = PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ + 1;
                state.setAdjType("fg-service-short-act");
            }
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise to recent fg: " + app);
            }
            maybeSetProcessFollowUpUpdateLocked(app,
                    state.getLastTopTime() + mConstants.TOP_TO_FGS_GRACE_DURATION, now);
        }

        // If the app was recently in the foreground and has expedited jobs running,
        // allow it to get a higher rank in memory for some time, compared to other EJS and even
        // foreground services so that it can finish performing any persistence/processing of
        // in-memory state.
        if (psr.hasTopStartedAlmostPerceptibleServices()
                && (adj > PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ + 2)
                && (state.getLastTopTime()
                        + mConstants.TOP_TO_ALMOST_PERCEPTIBLE_GRACE_DURATION > now
                || state.getSetProcState() <= PROCESS_STATE_TOP)) {
            // For EJ, we +2 the value, so we'll be able to detect it in
            // various dashboards.
            adj = PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ + 2;
            // This shall henceforth be called the "EJ" exemption, despite utilizing the
            // ALMOST_PERCEPTIBLE flag to work.
            state.setAdjType("top-ej-act");
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise to recent fg for EJ: " + app);
            }
            maybeSetProcessFollowUpUpdateLocked(app,
                    state.getLastTopTime() + mConstants.TOP_TO_ALMOST_PERCEPTIBLE_GRACE_DURATION,
                    now);
        }

        if (adj > PERCEPTIBLE_APP_ADJ
                || procState > PROCESS_STATE_TRANSIENT_BACKGROUND) {
            if (state.getForcingToImportant() != null) {
                // This is currently used for toasts...  they are not interactive, and
                // we don't want them to cause the app to become fully foreground (and
                // thus out of background check), so we yes the best background level we can.
                adj = PERCEPTIBLE_APP_ADJ;
                procState = PROCESS_STATE_TRANSIENT_BACKGROUND;
                state.setAdjType("force-imp");
                state.setAdjSource(state.getForcingToImportant());
                schedGroup = SCHED_GROUP_DEFAULT;
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise to force imp: " + app);
                }
            }
        }

        if (state.getCachedIsHeavyWeight()) {
            if (adj > HEAVY_WEIGHT_APP_ADJ) {
                // We don't want to kill the current heavy-weight process.
                adj = HEAVY_WEIGHT_APP_ADJ;
                schedGroup = SCHED_GROUP_BACKGROUND;
                state.setAdjType("heavy");
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise adj to heavy: " + app);
                }
            }
            if (procState > ActivityManager.PROCESS_STATE_HEAVY_WEIGHT) {
                procState = ActivityManager.PROCESS_STATE_HEAVY_WEIGHT;
                state.setAdjType("heavy");
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise procstate to heavy: " + app);
                }
            }
        }

        if (state.getCachedIsHomeProcess()) {
            if (adj > HOME_APP_ADJ) {
                // This process is hosting what we currently consider to be the
                // home app, so we don't want to let it go into the background.
                adj = HOME_APP_ADJ;
                schedGroup = SCHED_GROUP_BACKGROUND;
                state.setAdjType("home");
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise adj to home: " + app);
                }
            }
            if (procState > ActivityManager.PROCESS_STATE_HOME) {
                procState = ActivityManager.PROCESS_STATE_HOME;
                state.setAdjType("home");
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise procstate to home: " + app);
                }
            }
        }
        if (state.getCachedIsPreviousProcess() && state.getCachedHasActivities()) {
            // This was the previous process that showed UI to the user.  We want to
            // try to keep it around more aggressively, to give a good experience
            // around switching between two apps. However, we don't want to keep the
            // process in this privileged state indefinitely. Eventually, allow the
            // app to be demoted to cached.
            if (procState >= PROCESS_STATE_LAST_ACTIVITY
                    && state.getSetProcState() == PROCESS_STATE_LAST_ACTIVITY
                    && (state.getLastStateTime() + mConstants.MAX_PREVIOUS_TIME) <= now) {
                procState = PROCESS_STATE_LAST_ACTIVITY;
                schedGroup = SCHED_GROUP_BACKGROUND;
                state.setAdjType("previous-expired");
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Expire prev adj: " + app);
                }
            } else {
                if (adj > PREVIOUS_APP_ADJ) {
                    adj = PREVIOUS_APP_ADJ;
                    schedGroup = SCHED_GROUP_BACKGROUND;
                    state.setAdjType("previous");
                    if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                        reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise adj to prev: " + app);
                    }
                }
                if (procState > PROCESS_STATE_LAST_ACTIVITY) {
                    procState = PROCESS_STATE_LAST_ACTIVITY;
                    state.setAdjType("previous");
                    if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                        reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise procstate to prev: " + app);
                    }
                }
                final long lastStateTime;
                if (state.getSetProcState() == PROCESS_STATE_LAST_ACTIVITY) {
                    lastStateTime = state.getLastStateTime();
                } else {
                    lastStateTime = now;
                }
                maybeSetProcessFollowUpUpdateLocked(app,
                        lastStateTime + mConstants.MAX_PREVIOUS_TIME, now);
            }
        }

        if (false) Slog.i(TAG, "OOM " + app + ": initial adj=" + adj
                + " reason=" + state.getAdjType());

        // By default, we use the computed adjustment.  It may be changed if
        // there are applications dependent on our services or providers, but
        // this gives us a baseline and makes sure we don't get into an
        // infinite recursion. If we're re-evaluating due to cycles, use the previously computed
        // values.
        if (cycleReEval) {
            procState = Math.min(procState, state.getCurRawProcState());
            adj = Math.min(adj, state.getCurRawAdj());
            schedGroup = Math.max(schedGroup, state.getCurrentSchedulingGroup());
        }
        state.setCurRawAdj(adj);
        state.setCurRawProcState(procState);

        state.setHasStartedServices(false);
        state.setAdjSeq(mAdjSeq);

        if (isBackupProcess(app)) {
            // If possible we want to avoid killing apps while they're being backed up
            if (adj > BACKUP_APP_ADJ) {
                if (DEBUG_BACKUP) Slog.v(TAG_BACKUP, "oom BACKUP_APP_ADJ for " + app);
                adj = BACKUP_APP_ADJ;
                if (procState > PROCESS_STATE_TRANSIENT_BACKGROUND) {
                    procState = PROCESS_STATE_TRANSIENT_BACKGROUND;
                }
                state.setAdjType("backup");
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise adj to backup: " + app);
                }
            }
            if (procState > ActivityManager.PROCESS_STATE_BACKUP) {
                procState = ActivityManager.PROCESS_STATE_BACKUP;
                state.setAdjType("backup");
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise procstate to backup: " + app);
                }
            }
        }

        state.setCurBoundByNonBgRestrictedApp(getInitialIsCurBoundByNonBgRestrictedApp(app));

        state.setScheduleLikeTopApp(false);

        for (int is = psr.numberOfRunningServices() - 1;
                is >= 0 && (adj > FOREGROUND_APP_ADJ
                        || schedGroup == SCHED_GROUP_BACKGROUND
                        || procState > PROCESS_STATE_TOP);
                is--) {
            ServiceRecord s = psr.getRunningServiceAt(is);
            if (s.startRequested) {
                state.setHasStartedServices(true);
                if (procState > PROCESS_STATE_SERVICE) {
                    procState = PROCESS_STATE_SERVICE;
                    state.setAdjType("started-services");
                    if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                        reportOomAdjMessageLocked(TAG_OOM_ADJ,
                                "Raise procstate to started service: " + app);
                    }
                }
                if (!s.mKeepWarming && state.hasShownUi() && !state.getCachedIsHomeProcess()) {
                    // If this process has shown some UI, let it immediately
                    // go to the LRU list because it may be pretty heavy with
                    // UI stuff.  We'll tag it with a label just to help
                    // debug and understand what is going on.
                    if (adj > SERVICE_ADJ) {
                        state.setAdjType("cch-started-ui-services");
                    }
                } else {
                    if (s.mKeepWarming
                            || now < (s.lastActivity + mConstants.MAX_SERVICE_INACTIVITY)) {
                        // This service has seen some activity within
                        // recent memory, so we will keep its process ahead
                        // of the background processes. This does not apply
                        // to the SDK sandbox process since it should never
                        // be more important than its corresponding app.
                        if (!app.isSdkSandbox && adj > SERVICE_ADJ) {
                            adj = SERVICE_ADJ;
                            state.setAdjType("started-services");
                            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                                reportOomAdjMessageLocked(TAG_OOM_ADJ,
                                        "Raise adj to started service: " + app);
                            }
                            maybeSetProcessFollowUpUpdateLocked(app,
                                    s.lastActivity + mConstants.MAX_SERVICE_INACTIVITY, now);
                        }
                    }
                    // If we have let the service slide into the background
                    // state, still have some text describing what it is doing
                    // even though the service no longer has an impact.
                    if (adj > SERVICE_ADJ) {
                        state.setAdjType("cch-started-services");
                    }
                }
            }

            if (s.isForeground) {
                final int fgsType = s.foregroundServiceType;
                if (s.isFgsAllowedWiu_forCapabilities()) {
                    capabilityFromFGS |=
                            (fgsType & FOREGROUND_SERVICE_TYPE_LOCATION)
                                    != 0 ? PROCESS_CAPABILITY_FOREGROUND_LOCATION : 0;

                    if (roForegroundAudioControl()) { // flag check
                        // TODO(b/335373208) - revisit restriction of FOREGROUND_AUDIO_CONTROL
                        //  when it can be limited to specific FGS types
                        capabilityFromFGS |= PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;
                    }

                    final boolean enabled = state.getCachedCompatChange(
                            CACHED_COMPAT_CHANGE_CAMERA_MICROPHONE_CAPABILITY);
                    if (enabled) {
                        capabilityFromFGS |=
                                (fgsType & FOREGROUND_SERVICE_TYPE_CAMERA)
                                        != 0 ? PROCESS_CAPABILITY_FOREGROUND_CAMERA : 0;
                        capabilityFromFGS |=
                                (fgsType & FOREGROUND_SERVICE_TYPE_MICROPHONE)
                                        != 0 ? PROCESS_CAPABILITY_FOREGROUND_MICROPHONE : 0;
                    } else {
                        capabilityFromFGS |= PROCESS_CAPABILITY_FOREGROUND_CAMERA
                                | PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
                    }
                }
            }

            if (!couldRecurse) {
                // We're entering recursive functions below, if we're told it's not a recursive
                // loop, abort here.
                continue;
            }


            state.setCurRawAdj(adj);
            state.setCurRawProcState(procState);
            state.setCurrentSchedulingGroup(schedGroup);
            state.setCurCapability(capability);

            ArrayMap<IBinder, ArrayList<ConnectionRecord>> serviceConnections = s.getConnections();
            for (int conni = serviceConnections.size() - 1;
                    conni >= 0 && (adj > FOREGROUND_APP_ADJ
                            || schedGroup == SCHED_GROUP_BACKGROUND
                            || procState > PROCESS_STATE_TOP);
                    conni--) {
                ArrayList<ConnectionRecord> clist = serviceConnections.valueAt(conni);
                for (int i = 0;
                        i < clist.size() && (adj > FOREGROUND_APP_ADJ
                                || schedGroup == SCHED_GROUP_BACKGROUND
                                || procState > PROCESS_STATE_TOP);
                        i++) {
                    // XXX should compute this based on the max of
                    // all connected clients.
                    ConnectionRecord cr = clist.get(i);
                    if (cr.binding.client == app) {
                        // Binding to oneself is not interesting.
                        continue;
                    }

                    computeServiceHostOomAdjLSP(cr, app, cr.binding.client, now, topApp, doingAll,
                            cycleReEval, computeClients, oomAdjReason, cachedAdj, true, false);

                    adj = state.getCurRawAdj();
                    procState = state.getCurRawProcState();
                    schedGroup = state.getCurrentSchedulingGroup();
                    capability = state.getCurCapability();
                }
            }
        }

        final ProcessProviderRecord ppr = app.mProviders;
        for (int provi = ppr.numberOfProviders() - 1;
                provi >= 0 && (adj > FOREGROUND_APP_ADJ
                        || schedGroup == SCHED_GROUP_BACKGROUND
                        || procState > PROCESS_STATE_TOP);
                provi--) {
            ContentProviderRecord cpr = ppr.getProviderAt(provi);
            if (couldRecurse) {
                // We're entering recursive functions below.
                state.setCurRawAdj(adj);
                state.setCurRawProcState(procState);
                state.setCurrentSchedulingGroup(schedGroup);
                state.setCurCapability(capability);

                for (int i = cpr.connections.size() - 1;
                        i >= 0 && (adj > FOREGROUND_APP_ADJ
                                || schedGroup == SCHED_GROUP_BACKGROUND
                                || procState > PROCESS_STATE_TOP);
                        i--) {
                    ContentProviderConnection conn = cpr.connections.get(i);
                    ProcessRecord client = conn.client;
                    computeProviderHostOomAdjLSP(conn, app, client, now, topApp, doingAll,
                            cycleReEval, computeClients, oomAdjReason, cachedAdj, true, false);

                    adj = state.getCurRawAdj();
                    procState = state.getCurRawProcState();
                    schedGroup = state.getCurrentSchedulingGroup();
                    capability = state.getCurCapability();
                }
            }
            // If the provider has external (non-framework) process
            // dependencies, ensure that its adjustment is at least
            // FOREGROUND_APP_ADJ.
            if (cpr.hasExternalProcessHandles()) {
                if (adj > FOREGROUND_APP_ADJ) {
                    adj = FOREGROUND_APP_ADJ;
                    state.setCurRawAdj(adj);
                    schedGroup = SCHED_GROUP_DEFAULT;
                    state.setAdjType("ext-provider");
                    state.setAdjTarget(cpr.name);
                    if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                        reportOomAdjMessageLocked(TAG_OOM_ADJ,
                                "Raise adj to external provider: " + app);
                    }
                }
                if (procState > PROCESS_STATE_IMPORTANT_FOREGROUND) {
                    procState = PROCESS_STATE_IMPORTANT_FOREGROUND;
                    state.setCurRawProcState(procState);
                    if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                        reportOomAdjMessageLocked(TAG_OOM_ADJ,
                                "Raise procstate to external provider: " + app);
                    }
                }
            }
        }

        if ((ppr.getLastProviderTime() + mConstants.CONTENT_PROVIDER_RETAIN_TIME) > now) {
            if (adj > PREVIOUS_APP_ADJ) {
                adj = PREVIOUS_APP_ADJ;
                schedGroup = SCHED_GROUP_BACKGROUND;
                state.setAdjType("recent-provider");
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ,
                            "Raise adj to recent provider: " + app);
                }
                maybeSetProcessFollowUpUpdateLocked(app,
                        ppr.getLastProviderTime() + mConstants.CONTENT_PROVIDER_RETAIN_TIME, now);
            }
            if (procState > PROCESS_STATE_LAST_ACTIVITY) {
                procState = PROCESS_STATE_LAST_ACTIVITY;
                state.setAdjType("recent-provider");
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ,
                            "Raise procstate to recent provider: " + app);
                }
                maybeSetProcessFollowUpUpdateLocked(app,
                        ppr.getLastProviderTime() + mConstants.CONTENT_PROVIDER_RETAIN_TIME, now);
            }
        }

        if (procState >= PROCESS_STATE_CACHED_EMPTY) {
            if (psr.hasClientActivities()) {
                // This is a cached process, but with client activities.  Mark it so.
                procState = PROCESS_STATE_CACHED_ACTIVITY_CLIENT;
                state.setAdjType("cch-client-act");
            } else if (psr.isTreatedLikeActivity()) {
                // This is a cached process, but somebody wants us to treat it like it has
                // an activity, okay!
                procState = PROCESS_STATE_CACHED_ACTIVITY;
                state.setAdjType("cch-as-act");
            }
        }

        if (adj == SERVICE_ADJ) {
            if (doingAll && !cycleReEval) {
                state.setServiceB(mNewNumAServiceProcs > (mNumServiceProcs / 3));
                mNewNumServiceProcs++;
                if (!state.isServiceB()) {
                    // This service isn't far enough down on the LRU list to
                    // normally be a B service, but if we are low on RAM and it
                    // is large we want to force it down since we would prefer to
                    // keep launcher over it.
                    long lastPssOrRss = mService.mAppProfiler.isProfilingPss()
                            ? app.mProfile.getLastPss() : app.mProfile.getLastRss();

                    // RSS is larger than PSS, but the RSS/PSS ratio varies per-process based on how
                    // many shared pages a process uses. The threshold is increased if the flag for
                    // reading RSS instead of PSS is enabled.
                    //
                    // TODO(b/296454553): Tune the second value so that the relative number of
                    // service B is similar before/after this flag is enabled.
                    double thresholdModifier = mService.mAppProfiler.isProfilingPss()
                            ? 1 : mConstants.PSS_TO_RSS_THRESHOLD_MODIFIER;
                    double cachedRestoreThreshold =
                            mProcessList.getCachedRestoreThresholdKb() * thresholdModifier;

                    if (!isLastMemoryLevelNormal() && lastPssOrRss >= cachedRestoreThreshold) {
                        state.setServiceHighRam(true);
                        state.setServiceB(true);
                        //Slog.i(TAG, "ADJ " + app + " high ram!");
                    } else {
                        mNewNumAServiceProcs++;
                        //Slog.i(TAG, "ADJ " + app + " not high ram!");
                    }
                } else {
                    state.setServiceHighRam(false);
                }
            }
            if (state.isServiceB()) {
                adj = SERVICE_B_ADJ;
            }
        }

        // apply capability from FGS.
        if (psr.hasForegroundServices()) {
            capability |= capabilityFromFGS;
        }

        capability |= getDefaultCapability(app, procState);
        capability |= getCpuCapability(app, now, foregroundActivities);

        // Procstates below BFGS should never have this capability.
        if (procState > PROCESS_STATE_BOUND_FOREGROUND_SERVICE) {
            capability &= ~PROCESS_CAPABILITY_BFSL;
        }

        if (app.isPendingFinishAttach()) {
            // If the app is still starting up. We reset the computations to the
            // hardcoded values in setAttachingProcessStatesLSP. This ensures that the app keeps
            // hard-coded default 'startup' oom scores while starting up. When it finishes startup,
            // we'll recompute oom scores based on it's actual hosted compoenents.
            setAttachingProcessStatesLSP(app);
            state.setAdjSeq(mAdjSeq);
            state.setCompletedAdjSeq(state.getAdjSeq());
            return false;
        }

        // Do final modification to adj.  Everything we do between here and applying
        // the final setAdj must be done in this function, because we will also use
        // it when computing the final cached adj later.  Note that we don't need to
        // worry about this for max adj above, since max adj will always be used to
        // keep it out of the cached vaues.
        state.setCurCapability(capability);
        state.updateLastInvisibleTime(hasVisibleActivities);
        state.setHasForegroundActivities(foregroundActivities);
        state.setCompletedAdjSeq(mAdjSeq);

        schedGroup = setIntermediateAdjLSP(app, adj, prevAppAdj, schedGroup);
        setIntermediateProcStateLSP(app, procState, prevProcState);
        setIntermediateSchedGroupLSP(state, schedGroup);

        // if curAdj or curProcState improved, then this process was promoted
        return state.getCurAdj() < prevAppAdj || state.getCurProcState() < prevProcState
                || state.getCurCapability() != prevCapability;
    }

    /**
     * @return The proposed change to the schedGroup.
     */
    @GuardedBy({"mService", "mProcLock"})
    protected int setIntermediateAdjLSP(ProcessRecord app, int adj, int prevRawAppAdj,
            int schedGroup) {
        final ProcessStateRecord state = app.mState;
        state.setCurRawAdj(adj);

        adj = app.mServices.modifyRawOomAdj(adj);
        if (adj > state.getMaxAdj()) {
            adj = state.getMaxAdj();
            if (adj <= PERCEPTIBLE_LOW_APP_ADJ) {
                schedGroup = SCHED_GROUP_DEFAULT;
            }
        }

        state.setCurAdj(adj);

        return schedGroup;
    }

    @GuardedBy({"mService", "mProcLock"})
    protected void setIntermediateProcStateLSP(ProcessRecord app, int procState,
            int prevProcState) {
        final ProcessStateRecord state = app.mState;
        state.setCurProcState(procState);
        state.setCurRawProcState(procState);
    }

    @GuardedBy({"mService", "mProcLock"})
    protected void setIntermediateSchedGroupLSP(ProcessStateRecord state, int schedGroup) {
        // Put bound foreground services in a special sched group for additional
        // restrictions on screen off
        if (state.getCurProcState() >= PROCESS_STATE_BOUND_FOREGROUND_SERVICE
                && !isDeviceFullyAwake()
                && !state.shouldScheduleLikeTopApp()) {
            if (schedGroup > SCHED_GROUP_RESTRICTED) {
                schedGroup = SCHED_GROUP_RESTRICTED;
            }
        }

        state.setCurrentSchedulingGroup(schedGroup);
    }

    @GuardedBy({"mService", "mProcLock"})
    public boolean computeServiceHostOomAdjLSP(ConnectionRecord cr, ProcessRecord app,
            ProcessRecord client, long now, ProcessRecord topApp, boolean doingAll,
            boolean cycleReEval, boolean computeClients, int oomAdjReason, int cachedAdj,
            boolean couldRecurse, boolean dryRun) {
        if (app.isPendingFinishAttach()) {
            // We've set the attaching process state in the computeInitialOomAdjLSP. Skip it here.
            return false;
        }

        final ProcessStateRecord state = app.mState;
        ProcessStateRecord cstate = client.mState;
        boolean updated = false;

        if (couldRecurse) {
            if (app.isSdkSandbox && cr.binding.attributedClient != null) {
                // For SDK sandboxes, use the attributed client (eg the app that
                // requested the sandbox)
                client = cr.binding.attributedClient;
                cstate = client.mState;
            }
            if (computeClients) {
                computeOomAdjLSP(client, cachedAdj, topApp, doingAll, now, cycleReEval, true,
                        oomAdjReason, true);
            } else {
                cstate.setCurRawAdj(cstate.getCurAdj());
                cstate.setCurRawProcState(cstate.getCurProcState());
            }
        }

        int clientAdj = cstate.getCurRawAdj();
        int clientProcState = cstate.getCurRawProcState();

        final boolean clientIsSystem = clientProcState < PROCESS_STATE_TOP;

        int adj = state.getCurRawAdj();
        int procState = state.getCurRawProcState();
        int schedGroup = state.getCurrentSchedulingGroup();
        int capability = state.getCurCapability();

        final int prevRawAdj = adj;
        final int prevProcState = procState;
        final int prevSchedGroup = schedGroup;
        final int prevCapability = capability;

        final int appUid = app.info.uid;
        final int logUid = mService.mCurOomAdjUid;

        if (!dryRun) {
            state.setCurBoundByNonBgRestrictedApp(state.isCurBoundByNonBgRestrictedApp()
                    || cstate.isCurBoundByNonBgRestrictedApp()
                    || clientProcState <= PROCESS_STATE_BOUND_TOP
                    || (clientProcState == PROCESS_STATE_FOREGROUND_SERVICE
                            && !cstate.isBackgroundRestricted()));
        }

        if (client.mOptRecord.shouldNotFreeze()) {
            // Propagate the shouldNotFreeze flag down the bindings.
            if (app.mOptRecord.setShouldNotFreeze(true, dryRun,
                    app.mOptRecord.shouldNotFreezeReason()
                    | client.mOptRecord.shouldNotFreezeReason(), mAdjSeq)) {
                if (Flags.useCpuTimeCapability()) {
                    // Do nothing, capability updated check will handle the dryrun output.
                } else {
                    // Bail out early, as we only care about the return value for a dryrun.
                    return true;
                }
            }
        }

        boolean trackedProcState = false;

        // We always propagate PROCESS_CAPABILITY_BFSL over bindings here,
        // but, right before actually setting it to the process,
        // we check the final procstate, and remove it if the procsate is below BFGS.
        capability |= getBfslCapabilityFromClient(client);

        capability |= getCpuCapabilityFromClient(cr, client);

        if (cr.notHasFlag(Context.BIND_WAIVE_PRIORITY)) {
            if (cr.hasFlag(Context.BIND_INCLUDE_CAPABILITIES)) {
                capability |= cstate.getCurCapability();
            }

            // If an app has network capability by default
            // (by having procstate <= BFGS), then the apps it binds to will get
            // elevated to a high enough procstate anyway to get network unless they
            // request otherwise, so don't propagate the network capability by default
            // in this case unless they explicitly request it.
            if ((cstate.getCurCapability()
                    & PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK) != 0) {
                if (clientProcState <= PROCESS_STATE_BOUND_FOREGROUND_SERVICE) {
                    // This is used to grant network access to Expedited Jobs.
                    if (cr.hasFlag(Context.BIND_BYPASS_POWER_NETWORK_RESTRICTIONS)) {
                        capability |= PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK;
                    }
                } else {
                    capability |= PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK;
                }
            }
            if ((cstate.getCurCapability()
                    & PROCESS_CAPABILITY_USER_RESTRICTED_NETWORK) != 0) {
                if (clientProcState <= PROCESS_STATE_IMPORTANT_FOREGROUND) {
                    // This is used to grant network access to User Initiated Jobs.
                    if (cr.hasFlag(Context.BIND_BYPASS_USER_NETWORK_RESTRICTIONS)) {
                        capability |= PROCESS_CAPABILITY_USER_RESTRICTED_NETWORK;
                    }
                }
            }

            // Sandbox should be able to control audio only when bound client
            // has this capability.
            if ((cstate.getCurCapability()
                    & PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL) != 0) {
                if (app.isSdkSandbox) {
                    capability |= PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;
                }
            }

            if (couldRecurse && shouldSkipDueToCycle(app, cstate, procState, adj, cycleReEval)) {
                return false;
            }

            if (clientProcState >= PROCESS_STATE_CACHED_ACTIVITY) {
                // If the other app is cached for any reason, for purposes here
                // we are going to consider it empty.  The specific cached state
                // doesn't propagate except under certain conditions.
                clientProcState = PROCESS_STATE_CACHED_EMPTY;
            }
            String adjType = null;
            if (cr.hasFlag(Context.BIND_ALLOW_OOM_MANAGEMENT)) {
                // Similar to BIND_WAIVE_PRIORITY, keep it unfrozen.
                if (clientAdj < CACHED_APP_MIN_ADJ) {
                    if (app.mOptRecord.setShouldNotFreeze(true, dryRun,
                            app.mOptRecord.shouldNotFreezeReason()
                            | ProcessCachedOptimizerRecord
                            .SHOULD_NOT_FREEZE_REASON_BINDER_ALLOW_OOM_MANAGEMENT, mAdjSeq)) {
                        if (Flags.useCpuTimeCapability()) {
                            // Do nothing, capability updated check will handle the dryrun output.
                        } else {
                            // Bail out early, as we only care about the return value for a dryrun.
                            return true;
                        }
                    }
                }
                // Not doing bind OOM management, so treat
                // this guy more like a started service.
                if (state.hasShownUi() && !state.getCachedIsHomeProcess()) {
                    // If this process has shown some UI, let it immediately
                    // go to the LRU list because it may be pretty heavy with
                    // UI stuff.  We'll tag it with a label just to help
                    // debug and understand what is going on.
                    if (adj > clientAdj) {
                        adjType = "cch-bound-ui-services";
                    }

                    if (state.isCached() && dryRun) {
                        // Bail out early, as we only care about the return value for a dryrun.
                        return true;
                    }

                    clientAdj = adj;
                    clientProcState = procState;
                } else {
                    if (now >= (cr.binding.service.lastActivity
                            + mConstants.MAX_SERVICE_INACTIVITY)) {
                        // This service has not seen activity within
                        // recent memory, so allow it to drop to the
                        // LRU list if there is no other reason to keep
                        // it around.  We'll also tag it with a label just
                        // to help debug and undertand what is going on.
                        if (adj > clientAdj) {
                            adjType = "cch-bound-services";
                        }
                        clientAdj = adj;
                    }
                }
            }
            if (adj > clientAdj) {
                // If this process has recently shown UI, and the process that is binding to it
                // is less important than a state that can be actively running, then we don't
                // care about the binding as much as we care about letting this process get into
                // the LRU list to be killed and restarted if needed for memory.
                if (state.hasShownUi() && !state.getCachedIsHomeProcess()
                        && clientAdj > CACHING_UI_SERVICE_CLIENT_ADJ_THRESHOLD) {
                    if (adj >= CACHED_APP_MIN_ADJ) {
                        adjType = "cch-bound-ui-services";
                    }
                } else {
                    int newAdj;
                    int lbAdj = VISIBLE_APP_ADJ; // lower bound of adj.
                    if (cr.hasFlag(Context.BIND_ABOVE_CLIENT
                            | Context.BIND_IMPORTANT)) {
                        if (clientAdj >= PERSISTENT_SERVICE_ADJ) {
                            newAdj = clientAdj;
                        } else {
                            // make this service persistent
                            newAdj = PERSISTENT_SERVICE_ADJ;
                            schedGroup = SCHED_GROUP_DEFAULT;
                            procState = ActivityManager.PROCESS_STATE_PERSISTENT;
                            if (!dryRun) {
                                cr.trackProcState(procState, mAdjSeq);
                            }
                            trackedProcState = true;
                        }
                    } else if (cr.hasFlag(Context.BIND_NOT_PERCEPTIBLE)
                            && clientAdj <= PERCEPTIBLE_APP_ADJ
                            && adj >= (lbAdj = PERCEPTIBLE_LOW_APP_ADJ)) {
                        newAdj = PERCEPTIBLE_LOW_APP_ADJ;
                    } else if (cr.hasFlag(Context.BIND_ALMOST_PERCEPTIBLE)
                            && cr.notHasFlag(Context.BIND_NOT_FOREGROUND)
                            && clientAdj < PERCEPTIBLE_APP_ADJ
                            && adj >= (lbAdj = PERCEPTIBLE_APP_ADJ)) {
                        // This is for user-initiated jobs.
                        // We use APP_ADJ + 1 here, so we can tell them apart from FGS.
                        newAdj = PERCEPTIBLE_APP_ADJ + 1;
                    } else if (cr.hasFlag(Context.BIND_ALMOST_PERCEPTIBLE)
                            && cr.hasFlag(Context.BIND_NOT_FOREGROUND)
                            && clientAdj < PERCEPTIBLE_APP_ADJ
                            && adj >= (lbAdj = (PERCEPTIBLE_MEDIUM_APP_ADJ + 2))) {
                        // This is for expedited jobs.
                        // We use MEDIUM_APP_ADJ + 2 here, so we can tell apart
                        // EJ and short-FGS.
                        newAdj = PERCEPTIBLE_MEDIUM_APP_ADJ + 2;
                    } else if (cr.hasFlag(Context.BIND_NOT_VISIBLE)
                            && clientAdj < PERCEPTIBLE_APP_ADJ
                            && adj >= (lbAdj = PERCEPTIBLE_APP_ADJ)) {
                        newAdj = PERCEPTIBLE_APP_ADJ;
                    } else if (clientAdj >= PERCEPTIBLE_APP_ADJ) {
                        newAdj = clientAdj;
                    } else if (cr.hasFlag(BIND_TREAT_LIKE_VISIBLE_FOREGROUND_SERVICE)
                            && clientAdj <= VISIBLE_APP_ADJ
                            && adj > VISIBLE_APP_ADJ) {
                        newAdj = VISIBLE_APP_ADJ;
                    } else {
                        if (adj > VISIBLE_APP_ADJ) {
                            // TODO: Is this too limiting for apps bound from TOP?
                            newAdj = Math.max(clientAdj, lbAdj);
                        } else {
                            newAdj = adj;
                        }
                    }

                    if (!cstate.isCached()) {
                        if (state.isCached() && dryRun) {
                            // Bail out early, as we only care about the return value for a dryrun.
                            return true;
                        }
                    }

                    if (newAdj == clientAdj && app.isolated) {
                        // Make bound isolated processes have slightly worse score than their client
                        newAdj = clientAdj + 1;
                    }

                    if (adj >  newAdj) {
                        adj = newAdj;
                        if (state.setCurRawAdj(adj, dryRun)) {
                            // Bail out early, as we only care about the return value for a dryrun.
                        }
                        adjType = "service";
                    }
                }
            }
            if (cr.notHasFlag(Context.BIND_NOT_FOREGROUND
                    | Context.BIND_IMPORTANT_BACKGROUND)) {
                // This will treat important bound services identically to
                // the top app, which may behave differently than generic
                // foreground work.
                final int curSchedGroup = cstate.getCurrentSchedulingGroup();
                if (curSchedGroup > schedGroup) {
                    if (cr.hasFlag(Context.BIND_IMPORTANT)) {
                        schedGroup = curSchedGroup;
                    } else {
                        schedGroup = SCHED_GROUP_DEFAULT;
                    }
                }
                if (clientProcState < PROCESS_STATE_TOP) {
                    // Special handling for above-top states (persistent
                    // processes).  These should not bring the current process
                    // into the top state, since they are not on top.  Instead
                    // give them the best bound state after that.
                    if (cr.hasFlag(BIND_TREAT_LIKE_VISIBLE_FOREGROUND_SERVICE)) {
                        clientProcState = PROCESS_STATE_FOREGROUND_SERVICE;
                    } else if (cr.hasFlag(Context.BIND_FOREGROUND_SERVICE)) {
                        clientProcState = PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
                    } else if (isDeviceFullyAwake()
                            && cr.hasFlag(Context.BIND_FOREGROUND_SERVICE_WHILE_AWAKE)) {
                        clientProcState = PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
                    } else {
                        clientProcState =
                                PROCESS_STATE_IMPORTANT_FOREGROUND;
                    }
                } else if (clientProcState == PROCESS_STATE_TOP) {
                    // Go at most to BOUND_TOP, unless requested to elevate
                    // to client's state.
                    clientProcState = PROCESS_STATE_BOUND_TOP;
                    final boolean enabled = cstate.getCachedCompatChange(
                            CACHED_COMPAT_CHANGE_PROCESS_CAPABILITY);
                    if (enabled) {
                        if (cr.hasFlag(Context.BIND_INCLUDE_CAPABILITIES)) {
                            // TOP process passes all capabilities to the service.
                            capability |= cstate.getCurCapability();
                        } else {
                            // TOP process passes no capability to the service.
                        }
                    } else {
                        // TOP process passes all capabilities to the service.
                        capability |= cstate.getCurCapability();
                    }
                }
            } else if (cr.notHasFlag(Context.BIND_IMPORTANT_BACKGROUND)) {
                if (clientProcState < PROCESS_STATE_TRANSIENT_BACKGROUND) {
                    clientProcState =
                            PROCESS_STATE_TRANSIENT_BACKGROUND;
                }
            } else {
                if (clientProcState < PROCESS_STATE_IMPORTANT_BACKGROUND) {
                    clientProcState =
                            PROCESS_STATE_IMPORTANT_BACKGROUND;
                }
            }

            if (cr.hasFlag(Context.BIND_SCHEDULE_LIKE_TOP_APP) && clientIsSystem) {
                schedGroup = SCHED_GROUP_TOP_APP;
                if (dryRun) {
                    if (prevSchedGroup < schedGroup) {
                        // Bail out early, as we only care about the return value for a dryrun.
                        return true;
                    }
                } else {
                    state.setScheduleLikeTopApp(true);
                }
            }

            if (!trackedProcState && !dryRun) {
                cr.trackProcState(clientProcState, mAdjSeq);
            }

            if (procState > clientProcState) {
                procState = clientProcState;
                if (state.setCurRawProcState(procState, dryRun)) {
                    // Bail out early, as we only care about the return value for a dryrun.
                    return true;
                }
                if (adjType == null) {
                    adjType = "service";
                }
            }
            if (procState < PROCESS_STATE_IMPORTANT_BACKGROUND
                    && cr.hasFlag(Context.BIND_SHOWING_UI) && !dryRun) {
                app.setPendingUiClean(true);
            }
            if (adjType != null && !dryRun) {
                state.setAdjType(adjType);
                state.setAdjTypeCode(ActivityManager.RunningAppProcessInfo
                        .REASON_SERVICE_IN_USE);
                state.setAdjSource(client);
                state.setAdjSourceProcState(clientProcState);
                state.setAdjTarget(cr.binding.service.instanceName);
                if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                    reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise to " + adjType
                            + ": " + app + ", due to " + client
                            + " adj=" + adj + " procState="
                            + ProcessList.makeProcStateString(procState));
                }
            }
        } else { // BIND_WAIVE_PRIORITY == true
            // BIND_WAIVE_PRIORITY bindings are special when it comes to the
            // freezer. Processes bound via WPRI are expected to be running,
            // but they are not promoted in the LRU list to keep them out of
            // cached. As a result, they can freeze based on oom_adj alone.
            // Normally, bindToDeath would fire when a cached app would die
            // in the background, but nothing will fire when a running process
            // pings a frozen process. Accordingly, any cached app that is
            // bound by an unfrozen app via a WPRI binding has to remain
            // unfrozen.
            if (clientAdj < CACHED_APP_MIN_ADJ) {
                if (app.mOptRecord.setShouldNotFreeze(true, dryRun,
                        app.mOptRecord.shouldNotFreezeReason()
                        | ProcessCachedOptimizerRecord
                        .SHOULD_NOT_FREEZE_REASON_BIND_WAIVE_PRIORITY, mAdjSeq)) {
                    if (Flags.useCpuTimeCapability()) {
                        // Do nothing, capability updated check will handle the dryrun output.
                    } else {
                        // Bail out early, as we only care about the return value for a dryrun.
                        return true;
                    }
                }
            }
        }
        if (cr.hasFlag(Context.BIND_TREAT_LIKE_ACTIVITY)) {
            if (!dryRun) {
                app.mServices.setTreatLikeActivity(true);
            }
            if (clientProcState <= PROCESS_STATE_CACHED_ACTIVITY
                    && procState > PROCESS_STATE_CACHED_ACTIVITY) {
                // This is a cached process, but somebody wants us to treat it like it has
                // an activity, okay!
                procState = PROCESS_STATE_CACHED_ACTIVITY;
                state.setAdjType("cch-as-act");
            }
        }
        final ActivityServiceConnectionsHolder a = cr.activity;
        if (cr.hasFlag(Context.BIND_ADJUST_WITH_ACTIVITY)) {
            if (a != null && adj > FOREGROUND_APP_ADJ
                    && a.isActivityVisible()) {
                adj = FOREGROUND_APP_ADJ;
                if (state.setCurRawAdj(adj, dryRun)) {
                    return true;
                }
                if (cr.notHasFlag(Context.BIND_NOT_FOREGROUND)) {
                    if (cr.hasFlag(Context.BIND_IMPORTANT)) {
                        schedGroup = SCHED_GROUP_TOP_APP_BOUND;
                    } else {
                        schedGroup = SCHED_GROUP_DEFAULT;
                    }
                }

                if (!dryRun) {
                    state.setAdjType("service");
                    state.setAdjTypeCode(ActivityManager.RunningAppProcessInfo
                            .REASON_SERVICE_IN_USE);
                    state.setAdjSource(a);
                    state.setAdjSourceProcState(procState);
                    state.setAdjTarget(cr.binding.service.instanceName);
                    if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                        reportOomAdjMessageLocked(TAG_OOM_ADJ,
                                "Raise to service w/activity: " + app);
                    }
                }
            }
        }

        capability |= getDefaultCapability(app, procState);

        // Procstates below BFGS should never have this capability.
        if (procState > PROCESS_STATE_BOUND_FOREGROUND_SERVICE) {
            capability &= ~PROCESS_CAPABILITY_BFSL;
        }
        if (!updated) {
            if (adj < prevRawAdj || procState < prevProcState || schedGroup > prevSchedGroup) {
                updated = true;
            }

            if (Flags.useCpuTimeCapability()) {
                if ((capability != prevCapability)
                        && ((capability & prevCapability) == prevCapability)) {
                    updated = true;
                }
            } else {
                // Ignore PROCESS_CAPABILITY_CPU_TIME in capability comparison
                final int curFiltered = capability & ~PROCESS_CAPABILITY_CPU_TIME;
                final int prevFiltered = prevCapability & ~PROCESS_CAPABILITY_CPU_TIME;
                if ((curFiltered != prevFiltered)
                        && ((curFiltered & prevFiltered) == prevFiltered)) {
                    updated = true;
                }
            }
        }

        if (dryRun) {
            return updated;
        }
        if (adj < prevRawAdj) {
            schedGroup = setIntermediateAdjLSP(app, adj, prevRawAdj, schedGroup);
        }
        if (procState < prevProcState) {
            setIntermediateProcStateLSP(app, procState, prevProcState);
        }
        if (schedGroup > prevSchedGroup) {
            setIntermediateSchedGroupLSP(state, schedGroup);
        }
        state.setCurCapability(capability);

        return updated;
    }

    /**
     * Computes the impact on {@code app} the provider connections from {@code client} has.
     */
    @GuardedBy({"mService", "mProcLock"})
    public boolean computeProviderHostOomAdjLSP(ContentProviderConnection conn,
            ProcessRecord app, ProcessRecord client, long now, ProcessRecord topApp,
            boolean doingAll, boolean cycleReEval, boolean computeClients, int oomAdjReason,
            int cachedAdj, boolean couldRecurse, boolean dryRun) {
        if (app.isPendingFinishAttach()) {
            // We've set the attaching process state in the computeInitialOomAdjLSP. Skip it here.
            return false;
        }

        final ProcessStateRecord state = app.mState;
        final ProcessStateRecord cstate = client.mState;

        if (client == app) {
            // Being our own client is not interesting.
            return false;
        }
        if (couldRecurse) {
            if (computeClients) {
                computeOomAdjLSP(client, cachedAdj, topApp, doingAll, now, cycleReEval, true,
                        oomAdjReason, true);
            } else if (couldRecurse) {
                cstate.setCurRawAdj(cstate.getCurAdj());
                cstate.setCurRawProcState(cstate.getCurProcState());
            }

            if (shouldSkipDueToCycle(app, cstate, state.getCurRawProcState(), state.getCurRawAdj(),
                    cycleReEval)) {
                return false;
            }
        }

        int clientAdj = cstate.getCurRawAdj();
        int clientProcState = cstate.getCurRawProcState();

        int adj = state.getCurRawAdj();
        int procState = state.getCurRawProcState();
        int schedGroup = state.getCurrentSchedulingGroup();
        int capability = state.getCurCapability();

        final int prevRawAdj = adj;
        final int prevProcState = procState;
        final int prevSchedGroup = schedGroup;
        final int prevCapability = capability;

        final int appUid = app.info.uid;
        final int logUid = mService.mCurOomAdjUid;

        // We always propagate PROCESS_CAPABILITY_BFSL to providers here,
        // but, right before actually setting it to the process,
        // we check the final procstate, and remove it if the procsate is below BFGS.
        capability |= getBfslCapabilityFromClient(client);

        capability |= getCpuCapabilityFromClient(conn, client);

        if (clientProcState >= PROCESS_STATE_CACHED_ACTIVITY) {
            // If the other app is cached for any reason, for purposes here
            // we are going to consider it empty.
            clientProcState = PROCESS_STATE_CACHED_EMPTY;
        }
        if (client.mOptRecord.shouldNotFreeze()) {
            // Propagate the shouldNotFreeze flag down the bindings.
            if (app.mOptRecord.setShouldNotFreeze(true, dryRun,
                    app.mOptRecord.shouldNotFreezeReason()
                    | client.mOptRecord.shouldNotFreezeReason(), mAdjSeq)) {
                if (Flags.useCpuTimeCapability()) {
                    // Do nothing, capability updated check will handle the dryrun output.
                } else {
                    // Bail out early, as we only care about the return value for a dryrun.
                    return true;
                }
            }
        }

        if (!dryRun) {
            state.setCurBoundByNonBgRestrictedApp(state.isCurBoundByNonBgRestrictedApp()
                    || cstate.isCurBoundByNonBgRestrictedApp()
                    || clientProcState <= PROCESS_STATE_BOUND_TOP
                    || (clientProcState == PROCESS_STATE_FOREGROUND_SERVICE
                            && !cstate.isBackgroundRestricted()));
        }

        String adjType = null;
        if (adj > clientAdj) {
            if (state.hasShownUi() && !state.getCachedIsHomeProcess()
                    && clientAdj > PERCEPTIBLE_APP_ADJ) {
                adjType = "cch-ui-provider";
            } else {
                adj = Math.max(clientAdj, FOREGROUND_APP_ADJ);
                if (state.setCurRawAdj(adj, dryRun)) {
                    // Bail out early, as we only care about the return value for a dryrun.
                    return true;
                }
                adjType = "provider";
            }

            if (state.isCached() && !cstate.isCached() && dryRun) {
                // Bail out early, as we only care about the return value for a dryrun.
                return true;
            }
        }

        if (clientProcState <= PROCESS_STATE_FOREGROUND_SERVICE) {
            if (adjType == null) {
                adjType = "provider";
            }
            if (clientProcState == PROCESS_STATE_TOP) {
                clientProcState = PROCESS_STATE_BOUND_TOP;
            } else {
                clientProcState = PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
            }
        }

        if (!dryRun) {
            conn.trackProcState(clientProcState, mAdjSeq);
        }
        if (procState > clientProcState) {
            procState = clientProcState;
            if (state.setCurRawProcState(procState, dryRun)) {
                // Bail out early, as we only care about the return value for a dryrun.
                return true;
            }
        }
        if (cstate.getCurrentSchedulingGroup() > schedGroup) {
            schedGroup = SCHED_GROUP_DEFAULT;
        }
        if (adjType != null && !dryRun) {
            state.setAdjType(adjType);
            state.setAdjTypeCode(ActivityManager.RunningAppProcessInfo
                    .REASON_PROVIDER_IN_USE);
            state.setAdjSource(client);
            state.setAdjSourceProcState(clientProcState);
            state.setAdjTarget(conn.provider.name);
            if (DEBUG_OOM_ADJ_REASON || logUid == appUid) {
                reportOomAdjMessageLocked(TAG_OOM_ADJ, "Raise to " + adjType
                        + ": " + app + ", due to " + client
                        + " adj=" + adj + " procState="
                        + ProcessList.makeProcStateString(procState));
            }
        }

        // Procstates below BFGS should never have this capability.
        if (procState > PROCESS_STATE_BOUND_FOREGROUND_SERVICE) {
            capability &= ~PROCESS_CAPABILITY_BFSL;
        }

        if (dryRun) {
            if (adj < prevRawAdj || procState < prevProcState || schedGroup > prevSchedGroup) {
                return true;
            }

            if (Flags.useCpuTimeCapability()) {
                if ((capability != prevCapability)
                        && ((capability & prevCapability) == prevCapability)) {
                    return true;
                }
            } else {
                // Ignore PROCESS_CAPABILITY_CPU_TIME in capability comparison
                final int curFiltered = capability & ~PROCESS_CAPABILITY_CPU_TIME;
                final int prevFiltered = prevCapability & ~PROCESS_CAPABILITY_CPU_TIME;
                if ((curFiltered != prevFiltered)
                        && ((curFiltered & prevFiltered) == prevFiltered)) {
                    return true;
                }
            }
        }

        if (adj < prevRawAdj) {
            schedGroup = setIntermediateAdjLSP(app, adj, prevRawAdj, schedGroup);
        }
        if (procState < prevProcState) {
            setIntermediateProcStateLSP(app, procState, prevProcState);
        }
        if (schedGroup > prevSchedGroup) {
            setIntermediateSchedGroupLSP(state, schedGroup);
        }
        state.setCurCapability(capability);

        return false;
    }

    protected int getDefaultCapability(ProcessRecord app, int procState) {
        final int networkCapabilities =
                NetworkPolicyManager.getDefaultProcessNetworkCapabilities(procState);
        final int baseCapabilities;
        switch (procState) {
            case PROCESS_STATE_PERSISTENT:
            case PROCESS_STATE_PERSISTENT_UI:
            case PROCESS_STATE_TOP:
                baseCapabilities = PROCESS_CAPABILITY_ALL; // BFSL allowed
                break;
            case PROCESS_STATE_BOUND_TOP:
                if (app.getActiveInstrumentation() != null) {
                    baseCapabilities = PROCESS_CAPABILITY_BFSL |
                            PROCESS_CAPABILITY_ALL_IMPLICIT;
                } else {
                    baseCapabilities = PROCESS_CAPABILITY_BFSL;
                }
                break;
            case PROCESS_STATE_FOREGROUND_SERVICE:
                if (app.getActiveInstrumentation() != null) {
                    baseCapabilities = PROCESS_CAPABILITY_ALL_IMPLICIT;
                } else {
                    // Capability from foreground service is conditional depending on
                    // foregroundServiceType in the manifest file and the
                    // mAllowWhileInUsePermissionInFgs flag.
                    baseCapabilities = PROCESS_CAPABILITY_NONE;
                }
                break;
            default:
                baseCapabilities = PROCESS_CAPABILITY_NONE;
                break;
        }
        return baseCapabilities | networkCapabilities;
    }

    private static int getCpuCapability(ProcessRecord app, long nowUptime,
            boolean hasForegroundActivities) {
        // Note: persistent processes get all capabilities, including CPU_TIME.
        final UidRecord uidRec = app.getUidRecord();
        if (uidRec != null && uidRec.isCurAllowListed()) {
            // Process is in the power allowlist.
            return PROCESS_CAPABILITY_CPU_TIME;
        }
        if (hasForegroundActivities) {
            // TODO: b/402987519 - This grants the Top Sleeping process CPU_TIME but eventually
            //  should not.
            // Process has user perceptible activities.
            return PROCESS_CAPABILITY_CPU_TIME;
        }
        if (Flags.prototypeAggressiveFreezing()) {
            if (app.mServices.hasUndemotedShortForegroundService(nowUptime)) {
                // Grant cpu time for short FGS even when aggressively freezing.
                return PROCESS_CAPABILITY_CPU_TIME;
            }
        } else if (app.mServices.hasForegroundServices()) {
            return PROCESS_CAPABILITY_CPU_TIME;
        }
        if (app.mReceivers.numberOfCurReceivers() > 0) {
            return PROCESS_CAPABILITY_CPU_TIME;
        }
        if (app.hasActiveInstrumentation()) {
            return PROCESS_CAPABILITY_CPU_TIME;
        }
        // TODO(b/370817323): Populate this method with all of the reasons to keep a process
        //  unfrozen.
        return 0;
    }

    /**
     * @return the BFSL capability from a client (of a service binding or provider).
     */
    protected int getBfslCapabilityFromClient(ProcessRecord client) {
        // Procstates above FGS should always have this flag. We shouldn't need this logic,
        // but let's do it just in case.
        if (client.mState.getCurProcState() < PROCESS_STATE_FOREGROUND_SERVICE) {
            return PROCESS_CAPABILITY_BFSL;
        }
        // Otherwise, use the process's cur capability.

        // Note: BFSL is a per-UID check, not per-process, but here, the BFSL capability is still
        // propagated on a per-process basis.
        //
        // For example, consider this case:
        // - There are App 1 and App 2.
        // - App 1 has two processes
        //   Proc #1A, procstate BFGS with CAPABILITY_BFSL
        //   Proc #1B, procstate FGS with no CAPABILITY_BFSL (i.e. process has a short FGS)
        //        And this process binds to Proc #2 of App 2.
        //
        //       (Note because #1A has CAPABILITY_BFSL, App 1's UidRecord has CAPABILITY_BFSL.)
        //
        // - App 2 has one process:
        //   Proc #2, procstate FGS due to the above binding, _with no CAPABILITY_BFSL_.
        //
        // In this case, #2 will not get CAPABILITY_BFSL because the binding client (#1B)
        // doesn't have this capability. (Even though App 1's UidRecord has it.)
        //
        // This may look weird, because App 2 _is_ still BFSL allowed, because "it's bound by
        // an app that is BFSL-allowed". (See [bookmark: 61867f60-007c-408c-a2c4-e19e96056135]
        // in ActiveServices.)
        //
        // So why don't we propagate PROCESS_CAPABILITY_BFSL from App 1's UID record?
        // This is because short-FGS acts like "below BFGS" as far as BFSL is concerned,
        // similar to how JobScheduler jobs are below BFGS and apps can't start FGS from there.
        //
        // If #1B was running a job instead of a short-FGS, then its procstate would be below BFGS.
        // Then #2's procstate would also be below BFGS. So #2 wouldn't get CAPABILITY_BFSL.
        // Similarly, if #1B has a short FGS, even though the procstate of #1B and #2 would be FGS,
        // they both still wouldn't get CAPABILITY_BFSL.
        //
        // However, again, because #2 is bound by App 1, which is BFSL-allowed (because of #1A)
        // App 2 would still BFSL-allowed, due to the aforementioned check in ActiveServices.
        return client.mState.getCurCapability() & PROCESS_CAPABILITY_BFSL;
    }

    /**
     * @return the CPU capability from a client (of a service binding or provider).
     */
    private static int getCpuCapabilityFromClient(OomAdjusterModernImpl.Connection conn,
            ProcessRecord client) {
        if (conn == null || conn.transmitsCpuTime()) {
            return client.mState.getCurCapability() & PROCESS_CAPABILITY_CPU_TIME;
        } else {
            return 0;
        }
    }

    /**
     * Checks if for the given app and client, there's a cycle that should skip over the client
     * for now or use partial values to evaluate the effect of the client binding.
     * @param app
     * @param client
     * @param procState procstate evaluated so far for this app
     * @param adj oom_adj evaluated so far for this app
     * @param cycleReEval whether we're currently re-evaluating due to a cycle, and not the first
     *                    evaluation.
     * @return whether to skip using the client connection at this time
     */
    private boolean shouldSkipDueToCycle(ProcessRecord app, ProcessStateRecord client,
            int procState, int adj, boolean cycleReEval) {
        if (client.containsCycle()) {
            // We've detected a cycle. We should retry computeOomAdjLSP later in
            // case a later-checked connection from a client  would raise its
            // priority legitimately.
            app.mState.setContainsCycle(true);
            mProcessesInCycle.add(app);
            // If the client has not been completely evaluated, check if it's worth
            // using the partial values.
            if (client.getCompletedAdjSeq() < mAdjSeq) {
                if (cycleReEval) {
                    // If the partial values are no better, skip until the next
                    // attempt
                    if (client.getCurRawProcState() >= procState
                            && client.getCurRawAdj() >= adj
                            && (client.getCurCapability() & app.mState.getCurCapability())
                            == client.getCurCapability()) {
                        return true;
                    }
                    // Else use the client's partial procstate and adj to adjust the
                    // effect of the binding
                } else {
                    return false;
                }
            }
        }
        return false;
    }

    /** Inform the oomadj observer of changes to oomadj. Used by tests. */
    @GuardedBy("mService")
    protected void reportOomAdjMessageLocked(String tag, String msg) {
        Slog.d(tag, msg);
        synchronized (mService.mOomAdjObserverLock) {
            if (mService.mCurOomAdjObserver != null) {
                mService.mUiHandler.obtainMessage(DISPATCH_OOM_ADJ_OBSERVER_MSG, msg)
                        .sendToTarget();
            }
        }
    }

    void onWakefulnessChanged(int wakefulness) {
        mCachedAppOptimizer.onWakefulnessChanged(wakefulness);
    }

    @GuardedBy({"mService", "mProcLock"})
    protected boolean applyOomAdjLSP(ProcessRecord app, boolean doingAll, long now,
            long nowElapsed, @OomAdjReason int oomAdjReason) {
        return applyOomAdjLSP(app, doingAll, now, nowElapsed, oomAdjReason, false);
    }

    /** Applies the computed oomadj, procstate and sched group values and freezes them in set* */
    @GuardedBy({"mService", "mProcLock"})
    protected boolean applyOomAdjLSP(ProcessRecord app, boolean doingAll, long now,
            long nowElapsed, @OomAdjReason int oomAdjReson, boolean isBatchingOomAdj) {
        boolean success = true;
        final ProcessStateRecord state = app.mState;
        final UidRecord uidRec = app.getUidRecord();

        if (state.getCurRawAdj() != state.getSetRawAdj()) {
            state.setSetRawAdj(state.getCurRawAdj());
        }

        int changes = 0;

        if (state.getCurAdj() != state.getSetAdj()) {
            mCachedAppOptimizer.onOomAdjustChanged(state.getSetAdj(), state.getCurAdj(), app);
        }

        final int oldOomAdj = state.getSetAdj();
        if (state.getCurAdj() != state.getSetAdj()) {
            if (isBatchingOomAdj && mConstants.ENABLE_BATCHING_OOM_ADJ) {
                mProcsToOomAdj.add(app);
            } else {
                mInjector.setOomAdj(app.getPid(), app.uid, state.getCurAdj());
            }

            if (DEBUG_SWITCH || DEBUG_OOM_ADJ || mService.mCurOomAdjUid == app.info.uid) {
                String msg = "Set " + app.getPid() + " " + app.processName + " adj "
                        + state.getCurAdj() + ": " + state.getAdjType();
                reportOomAdjMessageLocked(TAG_OOM_ADJ, msg);
            }
            state.setSetAdj(state.getCurAdj());
            if (uidRec != null) {
                uidRec.noteProcAdjChanged();
            }
            state.setVerifiedAdj(INVALID_ADJ);
        }

        final int curSchedGroup = state.getCurrentSchedulingGroup();
        if (app.getWaitingToKill() != null && app.mReceivers.numberOfCurReceivers() == 0
                && ActivityManager.isProcStateBackground(state.getCurProcState())
                && !state.hasStartedServices()) {
            app.killLocked(app.getWaitingToKill(), ApplicationExitInfo.REASON_USER_REQUESTED,
                    ApplicationExitInfo.SUBREASON_REMOVE_TASK, true);
            success = false;
        } else if (state.getSetSchedGroup() != curSchedGroup) {
            int oldSchedGroup = state.getSetSchedGroup();
            state.setSetSchedGroup(curSchedGroup);
            if (DEBUG_SWITCH || DEBUG_OOM_ADJ || mService.mCurOomAdjUid == app.uid) {
                String msg = "Setting sched group of " + app.processName
                        + " to " + curSchedGroup + ": " + state.getAdjType();
                reportOomAdjMessageLocked(TAG_OOM_ADJ, msg);
            }
            int processGroup;
            switch (curSchedGroup) {
                case SCHED_GROUP_BACKGROUND:
                    processGroup = THREAD_GROUP_BACKGROUND;
                    break;
                case SCHED_GROUP_TOP_APP:
                case SCHED_GROUP_TOP_APP_BOUND:
                    processGroup = THREAD_GROUP_TOP_APP;
                    break;
                case SCHED_GROUP_RESTRICTED:
                    processGroup = THREAD_GROUP_RESTRICTED;
                    break;
                case SCHED_GROUP_FOREGROUND_WINDOW:
                    processGroup = THREAD_GROUP_FOREGROUND_WINDOW;
                    break;
                default:
                    processGroup = THREAD_GROUP_DEFAULT;
                    break;
            }
            setAppAndChildProcessGroup(app, processGroup);
            try {
                final int renderThreadTid = app.getRenderThreadTid();
                if (curSchedGroup == SCHED_GROUP_TOP_APP) {
                    // do nothing if we already switched to RT
                    if (oldSchedGroup != SCHED_GROUP_TOP_APP) {
                        app.getWindowProcessController().onTopProcChanged();
                        if (app.useFifoUiScheduling()) {
                            // Switch UI pipeline for app to SCHED_FIFO
                            state.setSavedPriority(Process.getThreadPriority(app.getPid()));
                            ActivityManagerService.setFifoPriority(app, true /* enable */);
                        } else {
                            // Boost priority for top app UI and render threads
                            mInjector.setThreadPriority(app.getPid(),
                                    THREAD_PRIORITY_TOP_APP_BOOST);
                            if (renderThreadTid != 0) {
                                try {
                                    mInjector.setThreadPriority(renderThreadTid,
                                            THREAD_PRIORITY_TOP_APP_BOOST);
                                } catch (IllegalArgumentException e) {
                                    // thread died, ignore
                                }
                            }
                        }
                    }
                } else if (oldSchedGroup == SCHED_GROUP_TOP_APP
                        && curSchedGroup != SCHED_GROUP_TOP_APP) {
                    app.getWindowProcessController().onTopProcChanged();
                    if (app.useFifoUiScheduling()) {
                        // Reset UI pipeline to SCHED_OTHER
                        ActivityManagerService.setFifoPriority(app, false /* enable */);
                        mInjector.setThreadPriority(app.getPid(), state.getSavedPriority());
                    } else {
                        // Reset priority for top app UI and render threads
                        mInjector.setThreadPriority(app.getPid(), 0);
                    }

                    if (renderThreadTid != 0) {
                        mInjector.setThreadPriority(renderThreadTid, THREAD_PRIORITY_DISPLAY);
                    }
                }
            } catch (Exception e) {
                if (DEBUG_ALL) {
                    Slog.w(TAG, "Failed setting thread priority of " + app.getPid(), e);
                }
            }
        }
        if (state.hasRepForegroundActivities() != state.hasForegroundActivities()) {
            state.setRepForegroundActivities(state.hasForegroundActivities());
            changes |= ActivityManagerService.ProcessChangeItem.CHANGE_ACTIVITIES;
        }

        updateAppFreezeStateLSP(app, oomAdjReson, false, oldOomAdj);

        if (state.getReportedProcState() != state.getCurProcState()) {
            state.setReportedProcState(state.getCurProcState());
            if (app.getThread() != null) {
                try {
                    if (false) {
                        //RuntimeException h = new RuntimeException("here");
                        Slog.i(TAG, "Sending new process state " + state.getReportedProcState()
                                + " to " + app /*, h*/);
                    }
                    app.getThread().setProcessState(state.getReportedProcState());
                } catch (RemoteException e) {
                }
            }
        }
        boolean forceUpdatePssTime = false;
        if (state.getSetProcState() == PROCESS_STATE_NONEXISTENT
                || ProcessList.procStatesDifferForMem(
                        state.getCurProcState(), state.getSetProcState())) {
            state.setLastStateTime(now);
            forceUpdatePssTime = true;
            if (DEBUG_PSS) {
                Slog.d(TAG_PSS, "Process state change from "
                        + ProcessList.makeProcStateString(state.getSetProcState()) + " to "
                        + ProcessList.makeProcStateString(state.getCurProcState()) + " next pss in "
                        + (app.mProfile.getNextPssTime() - now) + ": " + app);
            }
        }
        synchronized (mService.mAppProfiler.mProfilerLock) {
            app.mProfile.updateProcState(app.mState);
            mService.mAppProfiler.updateNextPssTimeLPf(
                    state.getCurProcState(), app.mProfile, now, forceUpdatePssTime);
        }
        int oldProcState = state.getSetProcState();
        if (state.getSetProcState() != state.getCurProcState()) {
            if (DEBUG_SWITCH || DEBUG_OOM_ADJ || mService.mCurOomAdjUid == app.uid) {
                String msg = "Proc state change of " + app.processName
                        + " to " + ProcessList.makeProcStateString(state.getCurProcState())
                        + " (" + state.getCurProcState() + ")" + ": " + state.getAdjType();
                reportOomAdjMessageLocked(TAG_OOM_ADJ, msg);
            }
            boolean setImportant = state.getSetProcState() < PROCESS_STATE_SERVICE;
            boolean curImportant = state.getCurProcState() < PROCESS_STATE_SERVICE;
            if (setImportant && !curImportant) {
                // This app is no longer something we consider important enough to allow to use
                // arbitrary amounts of battery power. Note its current CPU time to later know to
                // kill it if it is not behaving well.
                state.setWhenUnimportant(now);
                app.mProfile.mLastCpuTime.set(0);
            }
            // Inform UsageStats of important process state change
            // Must be called before updating setProcState
            maybeUpdateUsageStatsLSP(app, nowElapsed);

            maybeUpdateLastTopTime(state, now);

            state.setSetProcState(state.getCurProcState());
            if (state.getSetProcState() >= ActivityManager.PROCESS_STATE_HOME) {
                state.setNotCachedSinceIdle(false);
            }
            if (!doingAll) {
                synchronized (mService.mProcessStats.mLock) {
                    mService.setProcessTrackerStateLOSP(app,
                            mService.mProcessStats.getMemFactorLocked());
                }
            } else {
                state.setProcStateChanged(true);
            }
        } else if (state.hasReportedInteraction()) {
            final boolean fgsInteractionChangeEnabled = state.getCachedCompatChange(
                    CACHED_COMPAT_CHANGE_USE_SHORT_FGS_USAGE_INTERACTION_TIME);
            final long interactionThreshold = fgsInteractionChangeEnabled
                    ? mConstants.USAGE_STATS_INTERACTION_INTERVAL_POST_S
                    : mConstants.USAGE_STATS_INTERACTION_INTERVAL_PRE_S;
            // For apps that sit around for a long time in the interactive state, we need
            // to report this at least once a day so they don't go idle.
            if ((nowElapsed - state.getInteractionEventTime()) > interactionThreshold) {
                maybeUpdateUsageStatsLSP(app, nowElapsed);
            }
        } else {
            final boolean fgsInteractionChangeEnabled = state.getCachedCompatChange(
                    CACHED_COMPAT_CHANGE_USE_SHORT_FGS_USAGE_INTERACTION_TIME);
            final long interactionThreshold = fgsInteractionChangeEnabled
                    ? mConstants.SERVICE_USAGE_INTERACTION_TIME_POST_S
                    : mConstants.SERVICE_USAGE_INTERACTION_TIME_PRE_S;
            // For foreground services that sit around for a long time but are not interacted with.
            if ((nowElapsed - state.getFgInteractionTime()) > interactionThreshold) {
                maybeUpdateUsageStatsLSP(app, nowElapsed);
            }
        }

        if (state.getCurCapability() != state.getSetCapability()) {
            state.setSetCapability(state.getCurCapability());
        }

        final boolean curBoundByNonBgRestrictedApp = state.isCurBoundByNonBgRestrictedApp();
        if (curBoundByNonBgRestrictedApp != state.isSetBoundByNonBgRestrictedApp()) {
            state.setSetBoundByNonBgRestrictedApp(curBoundByNonBgRestrictedApp);
            if (!curBoundByNonBgRestrictedApp && state.isBackgroundRestricted()) {
                mService.mHandler.post(() -> {
                    synchronized (mService) {
                        mService.mServices.stopAllForegroundServicesLocked(
                                app.uid, app.info.packageName);
                    }
                });
            }
        }

        if (changes != 0) {
            if (DEBUG_PROCESS_OBSERVERS) Slog.i(TAG_PROCESS_OBSERVERS,
                    "Changes in " + app + ": " + changes);
            mProcessList.enqueueProcessChangeItemLocked(app.getPid(), app.info.uid,
                    changes, state.hasRepForegroundActivities());
            if (DEBUG_PROCESS_OBSERVERS) Slog.i(TAG_PROCESS_OBSERVERS,
                    "Enqueued process change item for "
                            + app.toShortString() + ": changes=" + changes
                            + " foreground=" + state.hasRepForegroundActivities()
                            + " type=" + state.getAdjType() + " source=" + state.getAdjSource()
                            + " target=" + state.getAdjTarget());
        }

        if (state.isCached() && !state.shouldNotKillOnBgRestrictedAndIdle()) {
            // It's eligible to get killed when in UID idle and bg restricted mode,
            // check if these states are just flipped.
            if (!state.isSetCached() || state.isSetNoKillOnBgRestrictedAndIdle()) {
                // Take the timestamp, we'd hold the killing for the background settle time
                // (for states debouncing to avoid from thrashing).
                state.setLastCanKillOnBgRestrictedAndIdleTime(nowElapsed);
                // Kick off the delayed checkup message if needed.
                if (mService.mDeterministicUidIdle
                        || !mService.mHandler.hasMessages(IDLE_UIDS_MSG)) {
                    if (mLogger.shouldLog(app.uid)) {
                        mLogger.logScheduleUidIdle2(
                                uidRec.getUid(), app.getPid(),
                                mConstants.mKillBgRestrictedAndCachedIdleSettleTimeMs);
                    }
                    mService.mHandler.sendEmptyMessageDelayed(IDLE_UIDS_MSG,
                            mConstants.mKillBgRestrictedAndCachedIdleSettleTimeMs);
                }
            }
        }
        state.setSetCached(state.isCached());
        state.setSetNoKillOnBgRestrictedAndIdle(state.shouldNotKillOnBgRestrictedAndIdle());
        if (((oldProcState != state.getSetProcState()) || (oldOomAdj != state.getSetAdj()))
                && mLogger.shouldLog(app.uid)) {
            mLogger.logProcStateChanged(app.uid, app.getPid(),
                    state.getSetProcState(), oldProcState,
                    state.getSetAdj(), oldOomAdj);
        }

        return success;
    }

    @GuardedBy({"mService", "mProcLock"})
    void setAttachingProcessStatesLSP(ProcessRecord app) {
        int initialSchedGroup = SCHED_GROUP_DEFAULT;
        int initialProcState = PROCESS_STATE_CACHED_EMPTY;
        int initialCapability =  PROCESS_CAPABILITY_NONE;
        boolean initialCached = true;
        final ProcessStateRecord state = app.mState;
        final int prevProcState = state.getCurProcState();
        final int prevAdj = state.getCurRawAdj();
        // If the process has been marked as foreground, it is starting as the top app (with
        // Zygote#START_AS_TOP_APP_ARG), so boost the thread priority of its default UI thread.
        if (state.hasForegroundActivities()) {
            try {
                // The priority must be the same as how does {@link #applyOomAdjLSP} set for
                // {@link SCHED_GROUP_TOP_APP}. We don't check render thread because it
                // is not ready when attaching.
                app.getWindowProcessController().onTopProcChanged();
                if (app.useFifoUiScheduling()) {
                    mService.scheduleAsFifoPriority(app.getPid(), true);
                } else {
                    mInjector.setThreadPriority(app.getPid(), THREAD_PRIORITY_TOP_APP_BOOST);
                }
                if (isScreenOnOrAnimatingLocked(state)) {
                    initialSchedGroup = SCHED_GROUP_TOP_APP;
                    initialProcState = PROCESS_STATE_TOP;
                }
                initialCapability = PROCESS_CAPABILITY_ALL;
                initialCached = false;
            } catch (Exception e) {
                Slog.w(TAG, "Failed to pre-set top priority to " + app + " " + e);
            }
        }

        state.setCurrentSchedulingGroup(initialSchedGroup);
        state.setCurProcState(initialProcState);
        state.setCurRawProcState(initialProcState);
        state.setCurCapability(initialCapability);

        state.setCurAdj(ProcessList.FOREGROUND_APP_ADJ);
        state.setCurRawAdj(ProcessList.FOREGROUND_APP_ADJ);
        state.setForcingToImportant(null);
        state.setHasShownUi(false);

        onProcessStateChanged(app, prevProcState);
        onProcessOomAdjChanged(app, prevAdj);
    }

    // ONLY used for unit testing in OomAdjusterTests.java
    @VisibleForTesting
    void maybeUpdateUsageStats(ProcessRecord app, long nowElapsed) {
        synchronized (mService) {
            synchronized (mProcLock) {
                maybeUpdateUsageStatsLSP(app, nowElapsed);
            }
        }
    }

    @GuardedBy({"mService", "mProcLock"})
    private void maybeUpdateUsageStatsLSP(ProcessRecord app, long nowElapsed) {
        final ProcessStateRecord state = app.mState;
        if (DEBUG_USAGE_STATS) {
            Slog.d(TAG, "Checking proc [" + Arrays.toString(app.getPackageList())
                    + "] state changes: old = " + state.getSetProcState() + ", new = "
                    + state.getCurProcState());
        }
        if (mService.mUsageStatsService == null) {
            return;
        }
        final boolean fgsInteractionChangeEnabled = state.getCachedCompatChange(
                CACHED_COMPAT_CHANGE_USE_SHORT_FGS_USAGE_INTERACTION_TIME);
        boolean isInteraction;
        // To avoid some abuse patterns, we are going to be careful about what we consider
        // to be an app interaction.  Being the top activity doesn't count while the display
        // is sleeping, nor do short foreground services.
        if (ActivityManager.isProcStateConsideredInteraction(state.getCurProcState())) {
            isInteraction = true;
            state.setFgInteractionTime(0);
        } else if (state.getCurProcState() <= PROCESS_STATE_FOREGROUND_SERVICE) {
            if (state.getFgInteractionTime() == 0) {
                state.setFgInteractionTime(nowElapsed);
                isInteraction = false;
            } else {
                final long interactionTime = fgsInteractionChangeEnabled
                        ? mConstants.SERVICE_USAGE_INTERACTION_TIME_POST_S
                        : mConstants.SERVICE_USAGE_INTERACTION_TIME_PRE_S;
                isInteraction = nowElapsed > state.getFgInteractionTime() + interactionTime;
            }
        } else {
            isInteraction =
                    state.getCurProcState() <= PROCESS_STATE_IMPORTANT_FOREGROUND;
            state.setFgInteractionTime(0);
        }
        final long interactionThreshold = fgsInteractionChangeEnabled
                ? mConstants.USAGE_STATS_INTERACTION_INTERVAL_POST_S
                : mConstants.USAGE_STATS_INTERACTION_INTERVAL_PRE_S;
        if (isInteraction
                && (!state.hasReportedInteraction()
                    || (nowElapsed - state.getInteractionEventTime()) > interactionThreshold)) {
            state.setInteractionEventTime(nowElapsed);
            String[] packages = app.getPackageList();
            if (packages != null) {
                for (int i = 0; i < packages.length; i++) {
                    mService.mUsageStatsService.reportEvent(packages[i], app.userId,
                            UsageEvents.Event.SYSTEM_INTERACTION);
                }
            }
        }
        state.setReportedInteraction(isInteraction);
        if (!isInteraction) {
            state.setInteractionEventTime(0);
        }
    }

    private void maybeUpdateLastTopTime(ProcessStateRecord state, long nowUptime) {
        if (state.getSetProcState() <= PROCESS_STATE_TOP
                && state.getCurProcState() > PROCESS_STATE_TOP) {
            state.setLastTopTime(nowUptime);
        }
    }

    /**
     * Look for recently inactive apps and mark them idle after a grace period. If idled, stop
     * any background services and inform listeners.
     */
    @GuardedBy("mService")
    void idleUidsLocked() {
        final int N = mActiveUids.size();
        mService.mHandler.removeMessages(IDLE_UIDS_MSG);
        if (N <= 0) {
            return;
        }
        final long nowElapsed = mInjector.getElapsedRealtimeMillis();
        final long maxBgTime = nowElapsed - mConstants.BACKGROUND_SETTLE_TIME;
        long nextTime = 0;
        if (mService.mLocalPowerManager != null) {
            mService.mLocalPowerManager.startUidChanges();
        }
        boolean shouldLogMisc = false;
        for (int i = N - 1; i >= 0; i--) {
            final UidRecord uidRec = mActiveUids.valueAt(i);
            final long bgTime = uidRec.getLastBackgroundTime();
            final long idleTime = uidRec.getLastIdleTimeIfStillIdle();
            if (bgTime > 0 && (!uidRec.isIdle() || idleTime == 0)) {
                if (bgTime <= maxBgTime) {
                    EventLogTags.writeAmUidIdle(uidRec.getUid());
                    synchronized (mProcLock) {
                        uidRec.setIdle(true);
                        uidRec.setSetIdle(true);
                        uidRec.setLastIdleTime(nowElapsed);
                    }
                    mService.doStopUidLocked(uidRec.getUid(), uidRec);
                } else {
                    if (nextTime == 0 || nextTime > bgTime) {
                        nextTime = bgTime;
                    }
                    if (mLogger.shouldLog(uidRec.getUid())) {
                        shouldLogMisc = true;
                    }
                }
            }
        }
        if (mService.mLocalPowerManager != null) {
            mService.mLocalPowerManager.finishUidChanges();
        }
        // Also check if there are any apps in cached and background restricted mode,
        // if so, kill it if it's been there long enough, or kick off a msg to check
        // it later.
        if (mService.mConstants.mKillBgRestrictedAndCachedIdle) {
            final ArraySet<ProcessRecord> apps = mProcessList.mAppsInBackgroundRestricted;
            for (int i = 0, size = apps.size(); i < size; i++) {
                // Check to see if needs to be killed.
                final long bgTime = mProcessList.killAppIfBgRestrictedAndCachedIdleLocked(
                        apps.valueAt(i), nowElapsed) - mConstants.BACKGROUND_SETTLE_TIME;
                if (bgTime > 0 && (nextTime == 0 || nextTime > bgTime)) {
                    nextTime = bgTime;
                }
            }
        }
        if (nextTime > 0) {
            long delay = nextTime + mConstants.BACKGROUND_SETTLE_TIME - nowElapsed;
            if (shouldLogMisc) {
                mLogger.logScheduleUidIdle3(delay);
            }
            mService.mHandler.sendEmptyMessageDelayed(IDLE_UIDS_MSG, delay);
        }
    }

    @GuardedBy({"mService", "mProcLock"})
    void setUidTempAllowlistStateLSP(int uid, boolean onAllowlist) {
        final UidRecord uidRec = mActiveUids.get(uid);
        if (uidRec != null && uidRec.isCurAllowListed() != onAllowlist) {
            uidRec.setCurAllowListed(onAllowlist);
            if (Flags.migrateFullOomadjUpdates()) {
                for (int i = uidRec.getNumOfProcs() - 1; i >= 0; i--) {
                    enqueueOomAdjTargetLocked(uidRec.getProcessRecordByIndex(i));
                }
                updateOomAdjPendingTargetsLocked(OOM_ADJ_REASON_ALLOWLIST);
            } else {
                updateOomAdjLSP(OOM_ADJ_REASON_ALLOWLIST);
            }
        }
    }

    @GuardedBy("mService")
    void dumpProcessListVariablesLocked(ProtoOutputStream proto) {
        proto.write(ActivityManagerServiceDumpProcessesProto.ADJ_SEQ, mAdjSeq);
        proto.write(ActivityManagerServiceDumpProcessesProto.LRU_SEQ, mProcessList.getLruSeqLOSP());
        proto.write(ActivityManagerServiceDumpProcessesProto.NUM_NON_CACHED_PROCS,
                mNumNonCachedProcs);
        proto.write(ActivityManagerServiceDumpProcessesProto.NUM_SERVICE_PROCS, mNumServiceProcs);
        proto.write(ActivityManagerServiceDumpProcessesProto.NEW_NUM_SERVICE_PROCS,
                mNewNumServiceProcs);

    }

    @GuardedBy("mService")
    void dumpSequenceNumbersLocked(PrintWriter pw) {
        pw.println("  mAdjSeq=" + mAdjSeq + " mLruSeq=" + mProcessList.getLruSeqLOSP());
    }

    @GuardedBy("mService")
    void dumpProcCountsLocked(PrintWriter pw) {
        pw.println("  mNumNonCachedProcs=" + mNumNonCachedProcs
                + " (" + mProcessList.getLruSizeLOSP() + " total)"
                + " mNumCachedHiddenProcs=" + mNumCachedHiddenProcs
                + " mNumServiceProcs=" + mNumServiceProcs
                + " mNewNumServiceProcs=" + mNewNumServiceProcs);
    }

    void dumpCachedAppOptimizerSettings(PrintWriter pw) {
        mCachedAppOptimizer.dump(pw);
    }

    @GuardedBy("mService")
    void dumpCacheOomRankerSettings(PrintWriter pw) {
        mCacheOomRanker.dump(pw);
    }

    /**
     * Return whether or not a process should be frozen.
     */
    boolean getFreezePolicy(ProcessRecord proc) {
        // Reasons to not freeze:
        if (Flags.useCpuTimeCapability()) {
            if ((proc.mState.getCurCapability() & PROCESS_CAPABILITY_CPU_TIME) != 0) {
                /// App is important enough (see {@link #getCpuCapability}) or bound by something
                /// important enough to not be frozen.
                return false;
            }
        } else {
            // The CPU capability handling covers all setShouldNotFreeze paths. Must check
            // shouldNotFreeze, if the CPU capability is not being used.
            if (proc.mOptRecord.shouldNotFreeze()) {
                return false;
            }
        }

        if (proc.mOptRecord.isFreezeExempt()) {
            return false;
        }

        // Reasons to freeze:
        if (proc.mState.getCurAdj() >= mConstants.FREEZER_CUTOFF_ADJ) {
            // Oomscore is in a high enough state, it is safe to freeze.
            return true;
        }

        // Default, do not freeze a process.
        return false;
    }

    @GuardedBy({"mService", "mProcLock"})
    void updateAppFreezeStateLSP(ProcessRecord app, @OomAdjReason int oomAdjReason,
            boolean immediate, int oldOomAdj) {
        if (!mCachedAppOptimizer.useFreezer()) {
            return;
        }

        final boolean freezePolicy = getFreezePolicy(app);
        final ProcessCachedOptimizerRecord opt = app.mOptRecord;
        final ProcessStateRecord state = app.mState;
        if (Flags.traceUpdateAppFreezeStateLsp()) {
            final boolean oomAdjChanged = (state.getCurAdj() >= mConstants.FREEZER_CUTOFF_ADJ
                    ^ oldOomAdj >= mConstants.FREEZER_CUTOFF_ADJ) || oldOomAdj == UNKNOWN_ADJ;
            final boolean shouldNotFreezeChanged = opt.shouldNotFreezeAdjSeq() == mAdjSeq;
            final boolean hasCpuCapability =
                    (PROCESS_CAPABILITY_CPU_TIME & app.mState.getCurCapability())
                            == PROCESS_CAPABILITY_CPU_TIME;
            final boolean usedToHaveCpuCapability =
                    (PROCESS_CAPABILITY_CPU_TIME & app.mState.getSetCapability())
                            == PROCESS_CAPABILITY_CPU_TIME;
            final boolean cpuCapabilityChanged = hasCpuCapability != usedToHaveCpuCapability;
            if ((oomAdjChanged || shouldNotFreezeChanged || cpuCapabilityChanged)
                    && Trace.isTagEnabled(Trace.TRACE_TAG_ACTIVITY_MANAGER)) {
                Trace.instantForTrack(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                        "FreezeLite",
                        (opt.isFrozen() ? "F" : "-")
                        + (opt.isPendingFreeze() ? "P" : "-")
                        + (opt.isFreezeExempt() ? "E" : "-")
                        + (opt.shouldNotFreeze() ? "N" : "-")
                        + (hasCpuCapability ? "T" : "-")
                        + (immediate ? "I" : "-")
                        + (freezePolicy ? "Z" : "-")
                        + (Flags.useCpuTimeCapability() ? "t" : "-")
                        + (Flags.prototypeAggressiveFreezing() ? "a" : "-")
                        + "/" + app.getPid()
                        + "/" + state.getCurAdj()
                        + "/" + oldOomAdj
                        + "/" + opt.shouldNotFreezeReason());
                Trace.instantForTrack(Trace.TRACE_TAG_ACTIVITY_MANAGER,
                        CachedAppOptimizer.ATRACE_FREEZER_TRACK,
                        "updateAppFreezeStateLSP " + app.processName
                        + " pid: " + app.getPid()
                        + " isFreezeExempt: " + opt.isFreezeExempt()
                        + " isFrozen: " + opt.isFrozen()
                        + " shouldNotFreeze: " + opt.shouldNotFreeze()
                        + " shouldNotFreezeReason: " + opt.shouldNotFreezeReason()
                        + " curAdj: " + state.getCurAdj()
                        + " oldOomAdj: " + oldOomAdj
                        + " immediate: " + immediate
                        + " cpuCapability: " + hasCpuCapability);
            }
        }

        if (freezePolicy) {
            // This process should be frozen.
            if (immediate && !opt.isFrozen()) {
                // And it will be frozen immediately.
                mCachedAppOptimizer.freezeAppAsyncAtEarliestLSP(app);
            } else if (!opt.isFrozen() || !opt.isPendingFreeze()) {
                mCachedAppOptimizer.freezeAppAsyncLSP(app);
            }
        } else {
            // This process should not be frozen.
            if (opt.isFrozen() || opt.isPendingFreeze()) {
                mCachedAppOptimizer.unfreezeAppLSP(app,
                        CachedAppOptimizer.getUnfreezeReasonCodeFromOomAdjReason(oomAdjReason));
            }
        }
    }

    @GuardedBy("mService")
    void unfreezeTemporarily(ProcessRecord app, @OomAdjReason int reason) {
        if (!mCachedAppOptimizer.useFreezer()) {
            return;
        }

        final ProcessCachedOptimizerRecord opt = app.mOptRecord;
        if (!opt.isFrozen() && !opt.isPendingFreeze()) {
            return;
        }

        final ArrayList<ProcessRecord> processes = mTmpProcessList;
        final ActiveUids uids = mTmpUidRecords;
        mTmpProcessSet.add(app);
        collectReachableProcessesLocked(mTmpProcessSet, processes, uids);
        mTmpProcessSet.clear();
        // Now processes contains app's downstream and app
        final int size = processes.size();
        for (int i = 0; i < size; i++) {
            ProcessRecord proc = processes.get(i);
            mCachedAppOptimizer.unfreezeTemporarily(proc,
                    CachedAppOptimizer.getUnfreezeReasonCodeFromOomAdjReason(reason));
        }
        processes.clear();
    }

    @GuardedBy("mService")
    void onProcessEndLocked(@NonNull ProcessRecord app) {
        // Empty, the OomAdjusterModernImpl will have an implementation.
    }

    /**
     * Called when the process state is changed outside of the OomAdjuster.
     */
    @GuardedBy("mService")
    void onProcessStateChanged(@NonNull ProcessRecord app, int prevProcState) {
        // Empty, the OomAdjusterModernImpl will have an implementation.
    }

    /**
     * Called when the oom adj is changed outside of the OomAdjuster.
     */
    @GuardedBy("mService")
    void onProcessOomAdjChanged(@NonNull ProcessRecord app, int prevAdj) {
        // Empty, the OomAdjusterModernImpl will have an implementation.
    }

    @VisibleForTesting
    void resetInternal() {
        // Empty, the OomAdjusterModernImpl will have an implementation.
    }

    @GuardedBy("mService")
    protected int getInitialAdj(@NonNull ProcessRecord app) {
        return app.mState.getCurAdj();
    }

    @GuardedBy("mService")
    protected int getInitialProcState(@NonNull ProcessRecord app) {
        return app.mState.getCurProcState();
    }

    @GuardedBy("mService")
    protected int getInitialCapability(@NonNull ProcessRecord app) {
        return app.mState.getCurCapability();
    }

    @GuardedBy("mService")
    protected boolean getInitialIsCurBoundByNonBgRestrictedApp(@NonNull ProcessRecord app) {
        // The caller will set the initial value in this implementation.
        return app.mState.isCurBoundByNonBgRestrictedApp();
    }

    /**
     * Evaluate the service connection, return {@code true} if the client will change any state
     * (ie. ProcessState, oomAdj, capability, etc) of the service host process by the given
     * connection.
     */
    @GuardedBy("mService")
    boolean evaluateServiceConnectionAdd(ProcessRecord client, ProcessRecord app,
            ConnectionRecord cr) {
        if (evaluateConnectionPrelude(client, app)) {
            return true;
        }

        boolean needDryRun = false;
        if (app.getSetAdj() > client.getSetAdj()) {
            // The connection might elevate the importance of the service's oom adj score.
            needDryRun = true;
        } else if (app.getSetProcState() > client.getSetProcState()) {
            // The connection might elevate the importance of the service's process state.
            needDryRun = true;
        } else if (cr.hasFlag(Context.BIND_INCLUDE_CAPABILITIES
                            | Context.BIND_BYPASS_USER_NETWORK_RESTRICTIONS)
                && (app.getSetCapability() & client.getSetCapability())
                        != client.getSetCapability()) {
            // The connection might elevate the importance of the service's capabilities.
            needDryRun = true;
        } else if (Flags.useCpuTimeCapability()
                && (client.getSetCapability() & ~app.getSetCapability()
                    & PROCESS_CAPABILITY_CPU_TIME) != 0) {
            // The connection might grant PROCESS_CAPABILITY_CPU_TIME to the service.
            needDryRun = true;
        } else if (Flags.unfreezeBindPolicyFix()
                && cr.hasFlag(Context.BIND_WAIVE_PRIORITY
                            | Context.BIND_ALLOW_OOM_MANAGEMENT)) {
            // These bind flags can grant the shouldNotFreeze state to the service.
            needDryRun = true;
        } else if (Flags.unfreezeBindPolicyFix()
                && client.mOptRecord.shouldNotFreeze()
                && !app.mOptRecord.shouldNotFreeze()) {
            // The shouldNotFreeze state can be propagated and needs to be checked.
            needDryRun = true;
        }

        if (needDryRun) {
            // Take a dry run of the computeServiceHostOomAdjLSP, this would't be expensive
            // since it's only evaluating one service connection.
            return computeServiceHostOomAdjLSP(cr, app, client, mInjector.getUptimeMillis(),
                    mService.getTopApp(), false, false, false, OOM_ADJ_REASON_NONE,
                    CACHED_APP_MIN_ADJ, false, true /* dryRun */);
        }
        return false;
    }

    @GuardedBy("mService")
    boolean evaluateServiceConnectionRemoval(ProcessRecord client, ProcessRecord app,
            ConnectionRecord cr) {
        if (evaluateConnectionPrelude(client, app)) {
            return true;
        }

        if (app.getSetAdj() >= client.getSetAdj()) {
            return true;
        } else if (app.getSetProcState() >= client.getSetProcState()) {
            return true;
        } else if (cr.hasFlag(Context.BIND_INCLUDE_CAPABILITIES
                            | Context.BIND_BYPASS_USER_NETWORK_RESTRICTIONS)
                && (app.getSetCapability() & client.getSetCapability())
                            != PROCESS_CAPABILITY_NONE) {
            return true;
        } else if (Flags.unfreezeBindPolicyFix()
                && cr.hasFlag(Context.BIND_WAIVE_PRIORITY
                            | Context.BIND_ALLOW_OOM_MANAGEMENT)) {
            return true;
        } else if (Flags.unfreezeBindPolicyFix()
                && app.mOptRecord.shouldNotFreeze()
                && client.mOptRecord.shouldNotFreeze()) {
            // Process has shouldNotFreeze and it could have gotten it from the client.
            return true;
        } else if (Flags.useCpuTimeCapability()
                && (client.getSetCapability() & app.getSetCapability()
                    & PROCESS_CAPABILITY_CPU_TIME) != 0) {
            return true;
        }
        return false;
    }

    @GuardedBy("mService")
    boolean evaluateProviderConnectionAdd(ProcessRecord client, ProcessRecord app) {
        if (evaluateConnectionPrelude(client, app)) {
            return true;
        }

        boolean needDryRun = false;
        if (app.getSetAdj() > client.getSetAdj()) {
            needDryRun = true;
        } else if (app.getSetProcState() > client.getSetProcState()) {
            needDryRun = true;
        } else if (Flags.unfreezeBindPolicyFix()
                && client.mOptRecord.shouldNotFreeze()
                && !app.mOptRecord.shouldNotFreeze()) {
            needDryRun = true;
        } else if (Flags.useCpuTimeCapability()
                && (client.getSetCapability() & ~app.getSetCapability()
                    & PROCESS_CAPABILITY_CPU_TIME) != 0) {
            // The connection might grant PROCESS_CAPABILITY_CPU_TIME to the provider.
            needDryRun = true;
        }

        if (needDryRun) {
            return computeProviderHostOomAdjLSP(null, app, client, mInjector.getUptimeMillis(),
                    mService.getTopApp(), false, false, false, OOM_ADJ_REASON_NONE,
                    CACHED_APP_MIN_ADJ,
                    false, true /* dryRun */);
        }
        return false;
    }

    @GuardedBy("mService")
    boolean evaluateProviderConnectionRemoval(ProcessRecord client, ProcessRecord app) {
        if (evaluateConnectionPrelude(client, app)) {
            return true;
        }

        if (app.getSetAdj() >= client.getSetAdj()) {
            return true;
        } else if (app.getSetProcState() >= client.getSetProcState()) {
            return true;
        } else if (Flags.unfreezeBindPolicyFix()
                && app.mOptRecord.shouldNotFreeze()
                && client.mOptRecord.shouldNotFreeze()) {
            // Process has shouldNotFreeze and it could have gotten it from the client.
            return true;
        } else if (Flags.useCpuTimeCapability()
                && (client.getSetCapability() & app.getSetCapability()
                    & PROCESS_CAPABILITY_CPU_TIME) != 0) {
            return true;
        }

        return false;
    }

    private boolean evaluateConnectionPrelude(ProcessRecord client, ProcessRecord app) {
        if (client == null || app == null) {
            return true;
        }
        if (app.isSdkSandbox || app.isolated || app.isKilledByAm() || app.isKilled()) {
            // Let's always re-evaluate them for now.
            return true;
        }
        return false;
    }

    @GuardedBy("mService")
    private void maybeSetProcessFollowUpUpdateLocked(ProcessRecord proc,
            long updateUptimeMs, long now) {
        if (!Flags.followUpOomadjUpdates()) {
            return;
        }
        if (updateUptimeMs <= now) {
            // Time sensitive period has already passed. No need to schedule a follow up.
            return;
        }

        mFollowUpUpdateSet.add(proc);
        proc.mState.setFollowupUpdateUptimeMs(updateUptimeMs);

        scheduleFollowUpOomAdjusterUpdateLocked(updateUptimeMs, now);
    }


    @GuardedBy("mService")
    private void scheduleFollowUpOomAdjusterUpdateLocked(long updateUptimeMs,
            long now) {
        if (updateUptimeMs + mConstants.FOLLOW_UP_OOMADJ_UPDATE_WAIT_DURATION
                >= mNextFollowUpUpdateUptimeMs) {
            // Update time is too close or later than the next follow up update.
            return;
        }
        if (updateUptimeMs < now + mConstants.FOLLOW_UP_OOMADJ_UPDATE_WAIT_DURATION) {
            // Use a minimum delay for the follow up to possibly batch multiple process
            // evaluations and avoid rapid updates.
            updateUptimeMs = now + mConstants.FOLLOW_UP_OOMADJ_UPDATE_WAIT_DURATION;
        }

        // Schedulate a follow up update. Don't bother deleting existing handler messages, they
        // will be cleared during the message while no locks are being held.
        mNextFollowUpUpdateUptimeMs = updateUptimeMs;
        mService.mHandler.sendEmptyMessageAtTime(FOLLOW_UP_OOMADJUSTER_UPDATE_MSG,
                mNextFollowUpUpdateUptimeMs);
    }
}
