/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.location.contexthub;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.content.Context;
import android.hardware.contexthub.ContextHubInfo;
import android.hardware.contexthub.EndpointInfo;
import android.hardware.contexthub.ErrorCode;
import android.hardware.contexthub.HubEndpointInfo;
import android.hardware.contexthub.HubInfo;
import android.hardware.contexthub.HubMessage;
import android.hardware.contexthub.IContextHubEndpoint;
import android.hardware.contexthub.IContextHubEndpointCallback;
import android.hardware.contexthub.IEndpointCommunication;
import android.hardware.contexthub.MessageDeliveryStatus;
import android.hardware.contexthub.Reason;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Consumer;

/**
 * A class that manages registration/unregistration of clients and manages messages to/from clients.
 *
 * @hide
 */
/* package */ class ContextHubEndpointManager
        implements ContextHubHalEndpointCallback.IEndpointSessionCallback {
    /** The range of session IDs to use for endpoints */
    public static final int SERVICE_SESSION_RANGE = 1024;

    private static final String TAG = "ContextHubEndpointManager";

    /** The hub ID of the Context Hub Service. */
    private static final long SERVICE_HUB_ID = 0x416e64726f696400L;

    /** The length of the array that should be returned by HAL requestSessionIdRange */
    private static final int SERVICE_SESSION_RANGE_LENGTH = 2;

    /** The context of the service. */
    private final Context mContext;

    /** The proxy to talk to the Context Hub. */
    private final IContextHubWrapper mContextHubProxy;

    private final HubInfoRegistry mHubInfoRegistry;

    private final ContextHubTransactionManager mTransactionManager;

    /** A map of endpoint IDs to brokers currently registered. */
    private final Map<Long, ContextHubEndpointBroker> mEndpointMap = new ConcurrentHashMap<>();

    /** Variables for managing endpoint ID creation */
    private final Object mEndpointLock = new Object();

    /**
     * The next available endpoint ID to register. Per EndpointId.aidl definition, dynamic
     * endpoint IDs must have the left-most bit as 1, and the values 0/-1 are invalid.
     */
    @GuardedBy("mEndpointLock")
    private long mNextEndpointId = -2;

    /** The minimum session ID reservable by endpoints (retrieved from HAL in init()) */
    private int mMinSessionId = -1;

    /** The minimum session ID reservable by endpoints (retrieved from HAL in init()) */
    private int mMaxSessionId = -1;

    /** Variables for managing session ID creation */
    private final Object mSessionIdLock = new Object();

    /** A set of session IDs that have been reserved by an endpoint. */
    @GuardedBy("mSessionIdLock")
    private final Set<Integer> mReservedSessionIds =
            Collections.newSetFromMap(new HashMap<Integer, Boolean>());

    @GuardedBy("mSessionIdLock")
    private int mNextSessionId = 0;

    /** Set true if init() succeeds */
    private boolean mSessionIdsValid = false;

    /** The interface for endpoint communication (retrieved from HAL in init()) */
    private IEndpointCommunication mHubInterface = null;

    /** Thread pool executor for handling timeout */
    private final ScheduledExecutorService mSessionTimeoutExecutor;

    /*
     * The list of previous registration records.
     */
    private static final int NUM_CLIENT_RECORDS = 20;
    private final ConcurrentLinkedEvictingDeque<RegistrationRecord> mRegistrationRecordDeque =
            new ConcurrentLinkedEvictingDeque<>(NUM_CLIENT_RECORDS);

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            prefix = {"ACTION_"},
            value = {
                ACTION_REGISTERED,
                ACTION_UNREGISTERED,
            })
    public @interface Action {}

    public static final int ACTION_REGISTERED = 0;
    public static final int ACTION_UNREGISTERED = 1;

    /** A container class to store a record of ContextHubEndpointBroker registrations. */
    private class RegistrationRecord {
        private final String mBroker;
        private final int mAction;
        private final long mTimestamp;

        RegistrationRecord(String broker, @Action int action) {
            mBroker = broker;
            mAction = action;
            mTimestamp = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(ContextHubServiceUtil.formatDateFromTimestamp(mTimestamp));
            sb.append(" ");
            sb.append(mAction == ACTION_REGISTERED ? "+ " : "- ");
            sb.append(mBroker);
            return sb.toString();
        }
    }

    @VisibleForTesting
    ContextHubEndpointManager(
            Context context,
            IContextHubWrapper contextHubProxy,
            HubInfoRegistry hubInfoRegistry,
            ContextHubTransactionManager transactionManager,
            ScheduledExecutorService scheduledExecutorService) {
        mContext = context;
        mContextHubProxy = contextHubProxy;
        mHubInfoRegistry = hubInfoRegistry;
        mTransactionManager = transactionManager;
        mSessionTimeoutExecutor = scheduledExecutorService;
    }

    /* package */ ContextHubEndpointManager(
            Context context,
            IContextHubWrapper contextHubProxy,
            HubInfoRegistry hubInfoRegistry,
            ContextHubTransactionManager transactionManager) {
        this(
                context,
                contextHubProxy,
                hubInfoRegistry,
                transactionManager,
                new ScheduledThreadPoolExecutor(1));
    }

    /**
     * Initializes this class.
     *
     * This is separate from the constructor so that this may be passed into the callback registered
     * with the HAL.
     *
     * @throws InstantiationException on unexpected failure
     * @throws UnsupportedOperationException if not supported by the HAL
     */
    /* package */ void init() throws InstantiationException, UnsupportedOperationException {
        if (mSessionIdsValid) {
            throw new IllegalStateException("Already initialized");
        }
        try {
            HubInfo info = new HubInfo();
            info.hubId = SERVICE_HUB_ID;
            // TODO(b/387291125): Populate the ContextHubInfo with real values.
            ContextHubInfo contextHubInfo = new ContextHubInfo();
            contextHubInfo.name = "";
            contextHubInfo.vendor = "";
            contextHubInfo.toolchain = "";
            contextHubInfo.supportedPermissions = new String[0];
            info.hubDetails = HubInfo.HubDetails.contextHubInfo(contextHubInfo);
            mHubInterface = mContextHubProxy.registerEndpointHub(
                    new ContextHubHalEndpointCallback(mHubInfoRegistry, this),
                    info);
            if (mHubInterface == null) {
                throw new IllegalStateException("Received null IEndpointCommunication");
            }
        } catch (RemoteException | IllegalStateException | ServiceSpecificException e) {
            String error = "Failed to register ContextHubService as message hub";
            Log.e(TAG, error, e);
            throw new InstantiationException(error);
        }  // Forward UnsupportedOperationException to caller

        int[] range = null;
        try {
            range = mHubInterface.requestSessionIdRange(SERVICE_SESSION_RANGE);
            if (range != null && range.length < SERVICE_SESSION_RANGE_LENGTH) {
                String error = "Invalid session ID range: range array size = " + range.length;
                Log.e(TAG, error);
                unregisterHub();
                throw new InstantiationException(error);
            }
        } catch (RemoteException | IllegalArgumentException | ServiceSpecificException e) {
            String error = "Exception while calling HAL requestSessionIdRange";
            Log.e(TAG, error, e);
            unregisterHub();
            throw new InstantiationException(error);
        }

        mMinSessionId = range[0];
        mMaxSessionId = range[1];
        if (!isSessionIdRangeValid(mMinSessionId, mMaxSessionId)) {
            String error =
                    "Invalid session ID range: max=" + mMaxSessionId + " min=" + mMinSessionId;
            Log.e(TAG, error);
            unregisterHub();
            throw new InstantiationException(error);
        }

        synchronized (mSessionIdLock) {
            mNextSessionId = mMinSessionId;
        }
        mSessionIdsValid = true;
    }

    /**
     * Registers a new endpoint with the service.
     *
     * @param pendingEndpointInfo the object describing the endpoint being registered
     * @param callback the callback interface of the endpoint to register
     * @param packageName the name of the package of the calling client
     * @return the endpoint interface
     * @throws IllegalStateException if max number of endpoints have already registered
     */
    /* package */ IContextHubEndpoint registerEndpoint(
            HubEndpointInfo pendingEndpointInfo,
            @NonNull IContextHubEndpointCallback callback,
            String packageName,
            String attributionTag)
            throws RemoteException {
        if (!mSessionIdsValid) {
            throw new IllegalStateException("ContextHubEndpointManager failed to initialize");
        }
        ContextHubEndpointBroker broker;
        long endpointId = getNewEndpointId();
        EndpointInfo halEndpointInfo =
                ContextHubServiceUtil.createHalEndpointInfo(
                        pendingEndpointInfo, endpointId, SERVICE_HUB_ID);
        broker =
                new ContextHubEndpointBroker(
                        mContext,
                        mHubInterface,
                        this /* endpointManager */,
                        halEndpointInfo,
                        callback,
                        packageName,
                        attributionTag,
                        mTransactionManager,
                        mSessionTimeoutExecutor);
        broker.register();
        mEndpointMap.put(endpointId, broker);

        try {
            broker.attachDeathRecipient();
        } catch (RemoteException e) {
            // The client process has died, so we close the connection and return null
            Log.e(TAG, "Failed to attach death recipient to client", e);
            broker.unregister();
            return null;
        }

        mRegistrationRecordDeque.add(new RegistrationRecord(broker.toString(), ACTION_REGISTERED));
        Log.d(TAG, "Registered endpoint with ID = " + endpointId);
        return IContextHubEndpoint.Stub.asInterface(broker);
    }

    /**
     * Reserves an available session ID for an endpoint.
     *
     * @throws IllegalStateException if no session ID was available.
     * @return The reserved session ID.
     */
    /* package */ int reserveSessionId() {
        int id = -1;
        synchronized (mSessionIdLock) {
            final int maxCapacity = mMaxSessionId - mMinSessionId + 1;
            if (mReservedSessionIds.size() >= maxCapacity) {
                throw new IllegalStateException("Too many sessions reserved");
            }

            id = mNextSessionId;
            for (int i = mMinSessionId; i <= mMaxSessionId; i++) {
                if (!mReservedSessionIds.contains(id)) {
                    mNextSessionId = (id == mMaxSessionId) ? mMinSessionId : id + 1;
                    break;
                }

                id = (id == mMaxSessionId) ? mMinSessionId : id + 1;
            }

            mReservedSessionIds.add(id);
        }
        return id;
    }

    /** Returns a previously reserved session ID through {@link #reserveSessionId()}. */
    /* package */ void returnSessionId(int sessionId) {
        synchronized (mSessionIdLock) {
            mReservedSessionIds.remove(sessionId);
        }
    }

    /** Returns if a sessionId can be allocated for the service hub. */
    private boolean isSessionIdAllocatedForService(int sessionId) {
        return sessionId > mMaxSessionId || sessionId < mMinSessionId;
    }

    /**
     * Unregisters an endpoint given its ID.
     *
     * @param endpointId The ID of the endpoint to unregister.
     */
    /* package */ void unregisterEndpoint(long endpointId) {
        ContextHubEndpointBroker broker = mEndpointMap.remove(endpointId);
        if (broker != null) {
            mRegistrationRecordDeque.add(
                    new RegistrationRecord(broker.toString(), ACTION_UNREGISTERED));
        }
    }

    /** Invoked by the service when the Context Hub HAL restarts. */
    /* package */ void onHalRestart() {
        for (ContextHubEndpointBroker broker : mEndpointMap.values()) {
            // The broker will close existing sessions and re-register itself
            broker.onHalRestart();
        }
    }

    private Optional<Byte> onEndpointSessionOpenRequestInternal(
            int sessionId,
            HubEndpointInfo.HubEndpointIdentifier destination,
            HubEndpointInfo.HubEndpointIdentifier initiator,
            String serviceDescriptor) {
        if (destination.getHub() != SERVICE_HUB_ID) {
            Log.e(
                    TAG,
                    "onEndpointSessionOpenRequest: invalid destination hub ID: "
                            + destination.getHub());
            return Optional.of(Reason.ENDPOINT_INVALID);
        }
        ContextHubEndpointBroker broker = mEndpointMap.get(destination.getEndpoint());
        if (broker == null) {
            Log.e(
                    TAG,
                    "onEndpointSessionOpenRequest: unknown destination endpoint ID: "
                            + destination.getEndpoint());
            return Optional.of(Reason.ENDPOINT_INVALID);
        }
        HubEndpointInfo initiatorInfo = mHubInfoRegistry.getEndpointInfo(initiator);
        if (initiatorInfo == null) {
            Log.e(
                    TAG,
                    "onEndpointSessionOpenRequest: unknown initiator endpoint ID: "
                            + initiator.getEndpoint());
            return Optional.of(Reason.ENDPOINT_INVALID);
        }
        if (!isSessionIdAllocatedForService(sessionId)) {
            Log.e(
                    TAG,
                    "onEndpointSessionOpenRequest: invalid session ID, rejected:"
                            + " sessionId="
                            + sessionId);
            return Optional.of(Reason.OPEN_ENDPOINT_SESSION_REQUEST_REJECTED);
        }
        return broker.onEndpointSessionOpenRequest(sessionId, initiatorInfo, serviceDescriptor);
    }

    @Override
    public void onEndpointSessionOpenRequest(
            int sessionId,
            HubEndpointInfo.HubEndpointIdentifier destination,
            HubEndpointInfo.HubEndpointIdentifier initiator,
            String serviceDescriptor) {
        Optional<Byte> errorOptional =
                onEndpointSessionOpenRequestInternal(
                        sessionId, destination, initiator, serviceDescriptor);
        errorOptional.ifPresent((error) -> halCloseEndpointSessionNoThrow(sessionId, error));
    }

    @Override
    public void onCloseEndpointSession(int sessionId, byte reason) {
        boolean callbackInvoked =
                invokeCallbackForMatchingSession(
                        sessionId, (broker) -> broker.onCloseEndpointSession(sessionId, reason));
        if (!callbackInvoked) {
            Log.w(TAG, "onCloseEndpointSession: unknown session ID " + sessionId);
        }
    }

    @Override
    public void onEndpointSessionOpenComplete(int sessionId) {
        boolean callbackInvoked =
                invokeCallbackForMatchingSession(
                        sessionId, (broker) -> broker.onEndpointSessionOpenComplete(sessionId));
        if (!callbackInvoked) {
            Log.w(TAG, "onEndpointSessionOpenComplete: unknown session ID " + sessionId);
        }
    }

    @Override
    public void onMessageReceived(int sessionId, HubMessage message) {
        boolean callbackInvoked =
                invokeCallbackForMatchingSession(
                        sessionId, (broker) -> broker.onMessageReceived(sessionId, message));
        if (!callbackInvoked) {
            Log.w(TAG, "onMessageReceived: unknown session ID " + sessionId);
            if (message.isResponseRequired()) {
                sendMessageDeliveryStatus(
                        sessionId,
                        message.getMessageSequenceNumber(),
                        ErrorCode.DESTINATION_NOT_FOUND);
            }
        }
    }

    @Override
    public void onMessageDeliveryStatusReceived(int sessionId, int sequenceNumber, byte errorCode) {
        boolean callbackInvoked =
                invokeCallbackForMatchingSession(
                        sessionId,
                        (broker) ->
                                broker.onMessageDeliveryStatusReceived(
                                        sessionId, sequenceNumber, errorCode));
        if (!callbackInvoked) {
            Log.w(TAG, "onMessageDeliveryStatusReceived: unknown session ID " + sessionId);
        }
    }

    /**
     * Calls the HAL closeEndpointSession API.
     *
     * @param sessionId The session ID to close
     * @param halReason The HAL reason
     */
    /* package */ void halCloseEndpointSession(int sessionId, byte halReason)
            throws RemoteException {
        try {
            mHubInterface.closeEndpointSession(sessionId, halReason);
        } catch (RemoteException | IllegalArgumentException | UnsupportedOperationException e) {
            throw e;
        }
    }

    /** Same as halCloseEndpointSession but does not throw the exception */
    /* package */ void halCloseEndpointSessionNoThrow(int sessionId, byte halReason) {
        try {
            halCloseEndpointSession(sessionId, halReason);
        } catch (RemoteException | IllegalArgumentException | UnsupportedOperationException e) {
            Log.e(TAG, "Exception while calling HAL closeEndpointSession", e);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int count = 1;
        for (ContextHubEndpointBroker broker : mEndpointMap.values()) {
            sb.append(count + ". " + broker);
            sb.append(System.lineSeparator());
            count++;
        }

        sb.append(System.lineSeparator());
        sb.append("Registration History:");
        sb.append(System.lineSeparator());
        Iterator<RegistrationRecord> it = mRegistrationRecordDeque.descendingIterator();
        while (it.hasNext()) {
            sb.append(it.next());
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    /**
     * Invokes a callback for a session with matching ID.
     *
     * @param callback The callback to execute
     * @return true if a callback was executed
     */
    private boolean invokeCallbackForMatchingSession(
            int sessionId, Consumer<ContextHubEndpointBroker> callback) {
        for (ContextHubEndpointBroker broker : mEndpointMap.values()) {
            if (broker.hasSessionId(sessionId)) {
                try {
                    callback.accept(broker);
                } catch (RuntimeException e) {
                    Log.e(TAG, "Exception while invoking callback", e);
                }
                return true;
            }
        }
        return false;
    }

    /** Unregister the hub (called during init() failure). Silence errors. */
    private void unregisterHub() {
        try {
            mHubInterface.unregister();
        } catch (RemoteException | IllegalStateException e) {
            Log.e(TAG, "Failed to unregister from HAL on init failure", e);
        }
    }

    /** @return an available endpoint ID */
    private long getNewEndpointId() {
        synchronized (mEndpointLock) {
            if (mNextEndpointId >= 0) {
                throw new IllegalStateException("Too many endpoints connected");
            }
            return mNextEndpointId--;
        }
    }

    /**
     * @return true if the provided session ID range is valid
     */
    private boolean isSessionIdRangeValid(int minId, int maxId) {
        return (minId <= maxId) && (minId >= 0) && (maxId >= 0);
    }

    private void sendMessageDeliveryStatus(
            int sessionId, int messageSequenceNumber, byte errorCode) {
        MessageDeliveryStatus status = new MessageDeliveryStatus();
        status.messageSequenceNumber = messageSequenceNumber;
        status.errorCode = errorCode;
        try {
            mHubInterface.sendMessageDeliveryStatusToEndpoint(sessionId, status);
        } catch (RemoteException e) {
            Log.w(
                    TAG,
                    "Exception while sending message delivery status on session " + sessionId,
                    e);
        }
    }

    @VisibleForTesting
    /* package */ int getNumAvailableSessions() {
        synchronized (mSessionIdLock) {
            return (mMaxSessionId - mMinSessionId + 1) - mReservedSessionIds.size();
        }
    }

    @VisibleForTesting
    /* package */ int getNumRegisteredClients() {
        return mEndpointMap.size();
    }
}
