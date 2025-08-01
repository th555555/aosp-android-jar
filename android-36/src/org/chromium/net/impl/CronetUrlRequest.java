// Copyright 2014 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net.impl;

import static java.lang.Math.max;
import static org.chromium.net.UrlRequest.Builder.REQUEST_PRIORITY_IDLE;
import static org.chromium.net.UrlRequest.Builder.REQUEST_PRIORITY_LOWEST;
import static org.chromium.net.UrlRequest.Builder.REQUEST_PRIORITY_LOW;
import static org.chromium.net.UrlRequest.Builder.REQUEST_PRIORITY_MEDIUM;
import static org.chromium.net.UrlRequest.Builder.REQUEST_PRIORITY_HIGHEST;

import android.os.Build;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import org.jni_zero.CalledByNative;
import org.jni_zero.JNINamespace;
import org.jni_zero.NativeClassQualifiedName;
import org.jni_zero.NativeMethods;

import org.chromium.base.Log;
import org.chromium.base.metrics.ScopedSysTraceEvent;
import org.chromium.net.CallbackException;
import org.chromium.net.ConnectionCloseSource;
import org.chromium.net.CronetException;
import org.chromium.net.ExperimentalUrlRequest;
import org.chromium.net.Idempotency;
import org.chromium.net.InlineExecutionProhibitedException;
import org.chromium.net.NetworkException;
import org.chromium.net.RequestFinishedInfo;
import org.chromium.net.RequestPriority;
import org.chromium.net.UploadDataProvider;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo.HeaderBlock;
import org.chromium.net.impl.CronetLogger.CronetTrafficInfo;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import javax.annotation.concurrent.GuardedBy;

/**
 * UrlRequest using Chromium HTTP stack implementation. Could be accessed from any thread on
 * Executor. Cancel can be called from any thread. All @CallByNative methods are called on native
 * network thread and post tasks with listener calls onto Executor. Upon return from listener
 * callback native request adapter is called on executive thread and posts native tasks to native
 * network thread. Because Cancel could be called from any thread it is protected by
 * mUrlRequestAdapterLock.
 */
@JNINamespace("cronet")
@VisibleForTesting
public final class CronetUrlRequest extends ExperimentalUrlRequest {
    private final boolean mAllowDirectExecutor;

    /* Native adapter object, owned by UrlRequest. */
    @GuardedBy("mUrlRequestAdapterLock")
    private long mUrlRequestAdapter;

    @GuardedBy("mUrlRequestAdapterLock")
    private boolean mStarted;

    @GuardedBy("mUrlRequestAdapterLock")
    private boolean mWaitingOnRedirect;

    @GuardedBy("mUrlRequestAdapterLock")
    private boolean mWaitingOnRead;

    /*
     * Synchronize access to mUrlRequestAdapter, mStarted, mWaitingOnRedirect,
     * and mWaitingOnRead.
     */
    private final Object mUrlRequestAdapterLock = new Object();
    private final CronetUrlRequestContext mRequestContext;
    private final Executor mExecutor;

    /*
     * URL chain contains the URL currently being requested, and
     * all URLs previously requested. New URLs are added before
     * mCallback.onRedirectReceived is called.
     */
    private final List<String> mUrlChain = new ArrayList<>();

    private final VersionSafeCallbacks.UrlRequestCallback mCallback;
    private final String mInitialUrl;
    private final int mPriority;
    private final int mIdempotency;
    private final String mInitialMethod;
    private final List<Map.Entry<String, String>> mRequestHeaders;
    private final Collection<Object> mRequestAnnotations;
    private final boolean mDisableCache;
    private final boolean mDisableConnectionMigration;
    private final boolean mTrafficStatsTagSet;
    private final int mTrafficStatsTag;
    private final boolean mTrafficStatsUidSet;
    private final int mTrafficStatsUid;
    private final VersionSafeCallbacks.RequestFinishedInfoListener mRequestFinishedListener;
    // See {@link org.chromium.net.UrlRequest.Builder#setRawCompressionDictionary}.
    private byte[] mDictionarySha256Hash;
    private ByteBuffer mDictionary;
    private final String mDictionaryId;
    private final long mNetworkHandle;
    private final CronetLogger mLogger;

    private final CronetUploadDataStream mUploadDataStream;

    private UrlResponseInfoImpl mResponseInfo;

    // These three should only be updated once with mUrlRequestAdapterLock held. They are read on
    // UrlRequest.Callback's and RequestFinishedInfo.Listener's executors after the last update.
    @RequestFinishedInfoImpl.FinishedReason private int mFinishedReason;
    private CronetException mException;
    private CronetMetrics mMetrics;
    private boolean mQuicConnectionMigrationAttempted;
    private boolean mQuicConnectionMigrationSuccessful;
    private int mReadCount;
    private int mNonfinalUserCallbackExceptionCount;
    private boolean mFinalUserCallbackThrew;

    /*
     * Listener callback is repeatedly invoked when each read is completed, so it
     * is cached as a member variable.
     */
    private OnReadCompletedRunnable mOnReadCompletedTask;

    @GuardedBy("mUrlRequestAdapterLock")
    private Runnable mOnDestroyedCallbackForTesting;

    private final class OnReadCompletedRunnable implements Runnable {
        // Buffer passed back from current invocation of onReadCompleted.
        ByteBuffer mByteBuffer;

        @Override
        public void run() {
            checkCallingThread();
            // Null out mByteBuffer, to pass buffer ownership to callback or release if done.
            ByteBuffer buffer = mByteBuffer;
            mByteBuffer = null;

            try {
                synchronized (mUrlRequestAdapterLock) {
                    if (isDoneLocked()) {
                        return;
                    }
                    mWaitingOnRead = true;
                }
                mCallback.onReadCompleted(CronetUrlRequest.this, mResponseInfo, buffer);
            } catch (Exception e) {
                onNonfinalCallbackException(e);
            }
        }
    }

    CronetUrlRequest(
            CronetUrlRequestContext requestContext,
            String url,
            int priority,
            UrlRequest.Callback callback,
            Executor executor,
            Collection<Object> requestAnnotations,
            boolean disableCache,
            boolean disableConnectionMigration,
            boolean allowDirectExecutor,
            boolean trafficStatsTagSet,
            int trafficStatsTag,
            boolean trafficStatsUidSet,
            int trafficStatsUid,
            RequestFinishedInfo.Listener requestFinishedListener,
            int idempotency,
            long networkHandle,
            String method,
            ArrayList<Map.Entry<String, String>> requestHeaders,
            UploadDataProvider uploadDataProvider,
            Executor uploadDataProviderExecutor,
            byte[] dictionarySha256Hash,
            ByteBuffer dictionary,
            @NonNull String dictionaryId) {
        Objects.requireNonNull(url, "URL is required");
        Objects.requireNonNull(callback, "Listener is required");
        Objects.requireNonNull(executor, "Executor is required");
        Objects.requireNonNull(
                dictionaryId, "Dictionary ID is expect to be an empty string if not specified");

        mAllowDirectExecutor = allowDirectExecutor;
        mRequestContext = requestContext;
        mLogger = requestContext.getCronetLogger();
        mInitialUrl = url;
        mUrlChain.add(url);
        mPriority = convertRequestPriority(priority);
        mCallback = new VersionSafeCallbacks.UrlRequestCallback(callback);
        mExecutor = executor;
        mRequestAnnotations = requestAnnotations;
        mDisableCache = disableCache;
        mDisableConnectionMigration = disableConnectionMigration;
        mTrafficStatsTagSet = trafficStatsTagSet;
        mTrafficStatsTag = trafficStatsTag;
        mTrafficStatsUidSet = trafficStatsUidSet;
        mTrafficStatsUid = trafficStatsUid;
        mRequestFinishedListener =
                requestFinishedListener != null
                        ? new VersionSafeCallbacks.RequestFinishedInfoListener(
                                requestFinishedListener)
                        : null;
        mDictionarySha256Hash = dictionarySha256Hash;
        mDictionary = dictionary;
        mDictionaryId = dictionaryId;
        mIdempotency = convertIdempotency(idempotency);
        mNetworkHandle = networkHandle;
        mInitialMethod = method;
        mRequestHeaders = Collections.unmodifiableList(new ArrayList<>(requestHeaders));
        mUploadDataStream =
                uploadDataProvider == null
                        ? null
                        : new CronetUploadDataStream(
                                uploadDataProvider, uploadDataProviderExecutor, this);
    }

    @Override
    public String getHttpMethod() {
        return mInitialMethod;
    }

    @Override
    public boolean isDirectExecutorAllowed() {
        return mAllowDirectExecutor;
    }

    @Override
    public boolean isCacheDisabled() {
        return mDisableCache;
    }

    @Override
    public boolean hasTrafficStatsTag() {
        return mTrafficStatsTagSet;
    }

    @Override
    public int getTrafficStatsTag() {
        if (!hasTrafficStatsTag()) {
            throw new IllegalStateException("TrafficStatsTag is not set");
        }
        return mTrafficStatsTag;
    }

    @Override
    public boolean hasTrafficStatsUid() {
        return mTrafficStatsUidSet;
    }

    @Override
    public int getTrafficStatsUid() {
        if (!hasTrafficStatsUid()) {
            throw new IllegalStateException("TrafficStatsUid is not set");
        }
        return mTrafficStatsUid;
    }
    @Override
    public int getPriority() {
        switch (mPriority) {
            case RequestPriority.IDLE:
                return REQUEST_PRIORITY_IDLE;
            case RequestPriority.LOWEST:
                return REQUEST_PRIORITY_LOWEST;
            case RequestPriority.LOW:
                return REQUEST_PRIORITY_LOW;
            case RequestPriority.MEDIUM:
                return REQUEST_PRIORITY_MEDIUM;
            case RequestPriority.HIGHEST:
                return REQUEST_PRIORITY_HIGHEST;
            default:
                throw new IllegalStateException("Invalid request priority: " + mPriority);
        }
    }

    @Override
    public HeaderBlock getHeaders() {
        return new UrlResponseInfoImpl.HeaderBlockImpl(mRequestHeaders);
    }

    @Override
    public void start() {
        try (var traceEvent = ScopedSysTraceEvent.scoped("CronetUrlRequest#start")) {
            synchronized (mUrlRequestAdapterLock) {
                checkNotStarted();

                try {
                    mUrlRequestAdapter =
                            CronetUrlRequestJni.get()
                                    .createRequestAdapter(
                                            CronetUrlRequest.this,
                                            mRequestContext.getUrlRequestContextAdapter(),
                                            mInitialUrl,
                                            mPriority,
                                            mDisableCache,
                                            mDisableConnectionMigration,
                                            mTrafficStatsTagSet,
                                            mTrafficStatsTag,
                                            mTrafficStatsUidSet,
                                            mTrafficStatsUid,
                                            mIdempotency,
                                            mDictionarySha256Hash,
                                            mDictionary,
                                            mDictionary != null ? mDictionary.position() : 0,
                                            mDictionary != null ? mDictionary.limit() : 0,
                                            mDictionaryId,
                                            mNetworkHandle);
                    mRequestContext.onRequestStarted();
                    if (!CronetUrlRequestJni.get()
                            .setHttpMethod(
                                    mUrlRequestAdapter, CronetUrlRequest.this, mInitialMethod)) {
                        throw new IllegalArgumentException("Invalid http method " + mInitialMethod);
                    }

                    boolean hasContentType = false;
                    for (Map.Entry<String, String> header : mRequestHeaders) {
                        if (header.getKey().equalsIgnoreCase("Content-Type")
                                && !header.getValue().isEmpty()) {
                            hasContentType = true;
                        }
                        if (!CronetUrlRequestJni.get()
                                .addRequestHeader(
                                        mUrlRequestAdapter,
                                        CronetUrlRequest.this,
                                        header.getKey(),
                                        header.getValue())) {
                            throw new IllegalArgumentException(
                                    "Invalid header with headername: " + header.getKey());
                        }
                    }
                    if (mUploadDataStream != null) {
                        if (!hasContentType) {
                            throw new IllegalArgumentException(
                                    "Requests with upload data must have a Content-Type.");
                        }
                        mStarted = true;
                        mUploadDataStream.postTaskToExecutor(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        mUploadDataStream.initializeWithRequest();
                                        synchronized (mUrlRequestAdapterLock) {
                                            if (isDoneLocked()) {
                                                return;
                                            }
                                            mUploadDataStream.attachNativeAdapterToRequest(
                                                    mUrlRequestAdapter);
                                            startInternalLocked();
                                        }
                                    }
                                },
                                "CronetUrlRequest#start");
                        return;
                    }
                } catch (RuntimeException e) {
                    // If there's an exception, cleanup and then throw the exception to the caller.
                    // start() is synchronized so we do not acquire mUrlRequestAdapterLock here.
                    destroyRequestAdapterLocked(RequestFinishedInfo.FAILED);
                    mRequestContext.onRequestFinished();
                    throw e;
                }
                mStarted = true;
                startInternalLocked();
            }
        }
    }

    /*
     * Starts fully configured request. Could execute on UploadDataProvider executor.
     * Caller is expected to ensure that request isn't canceled and mUrlRequestAdapter is valid.
     */
    @GuardedBy("mUrlRequestAdapterLock")
    private void startInternalLocked() {
        CronetUrlRequestJni.get().start(mUrlRequestAdapter, CronetUrlRequest.this);
    }

    @Override
    public void followRedirect() {
        try (var traceEvent = ScopedSysTraceEvent.scoped("CronetUrlRequest#followRedirect")) {
            synchronized (mUrlRequestAdapterLock) {
                if (!mWaitingOnRedirect) {
                    throw new IllegalStateException("No redirect to follow.");
                }
                mWaitingOnRedirect = false;

                if (isDoneLocked()) {
                    return;
                }

                CronetUrlRequestJni.get()
                        .followDeferredRedirect(mUrlRequestAdapter, CronetUrlRequest.this);
            }
        }
    }

    @Override
    public void read(ByteBuffer buffer) {
        try (var traceEvent = ScopedSysTraceEvent.scoped("CronetUrlRequest#read")) {
            Preconditions.checkHasRemaining(buffer);
            Preconditions.checkDirect(buffer);
            synchronized (mUrlRequestAdapterLock) {
                if (!mWaitingOnRead) {
                    throw new IllegalStateException("Unexpected read attempt.");
                }
                mWaitingOnRead = false;

                if (isDoneLocked()) {
                    return;
                }

                if (!CronetUrlRequestJni.get()
                        .readData(
                                mUrlRequestAdapter,
                                CronetUrlRequest.this,
                                buffer,
                                buffer.position(),
                                buffer.limit())) {
                    // Still waiting on read. This is just to have consistent
                    // behavior with the other error cases.
                    mWaitingOnRead = true;
                    throw new IllegalArgumentException("Unable to call native read");
                }
                mReadCount++;
            }
        }
    }

    @Override
    public void cancel() {
        try (var traceEvent = ScopedSysTraceEvent.scoped("CronetUrlRequest#cancel")) {
            synchronized (mUrlRequestAdapterLock) {
                if (isDoneLocked() || !mStarted) {
                    return;
                }
                destroyRequestAdapterLocked(RequestFinishedInfo.CANCELED);
            }
        }
    }

    @Override
    public boolean isDone() {
        synchronized (mUrlRequestAdapterLock) {
            return isDoneLocked();
        }
    }

    @GuardedBy("mUrlRequestAdapterLock")
    private boolean isDoneLocked() {
        return mStarted && mUrlRequestAdapter == 0;
    }

    @Override
    public void getStatus(UrlRequest.StatusListener unsafeListener) {
        final VersionSafeCallbacks.UrlRequestStatusListener listener =
                new VersionSafeCallbacks.UrlRequestStatusListener(unsafeListener);
        synchronized (mUrlRequestAdapterLock) {
            if (mUrlRequestAdapter != 0) {
                CronetUrlRequestJni.get()
                        .getStatus(mUrlRequestAdapter, CronetUrlRequest.this, listener);
                return;
            }
        }
        Runnable task =
                new Runnable() {
                    @Override
                    public void run() {
                        try (var callbackTraceEvent =
                                ScopedSysTraceEvent.scoped(
                                        "CronetUrlRequest#getStatus running callback")) {
                            listener.onStatus(UrlRequest.Status.INVALID);
                        }
                    }
                };
        postTaskToExecutor(task, "getStatus");
    }

    public void setOnDestroyedCallbackForTesting(Runnable onDestroyedCallbackForTesting) {
        synchronized (mUrlRequestAdapterLock) {
            mOnDestroyedCallbackForTesting = onDestroyedCallbackForTesting;
        }
    }

    public void setOnDestroyedUploadCallbackForTesting(
            Runnable onDestroyedUploadCallbackForTesting) {
        mUploadDataStream.setOnDestroyedCallbackForTesting(onDestroyedUploadCallbackForTesting);
    }

    public long getUrlRequestAdapterForTesting() {
        synchronized (mUrlRequestAdapterLock) {
            return mUrlRequestAdapter;
        }
    }

    /**
     * Posts task to application Executor. Used for Listener callbacks and other tasks that should
     * not be executed on network thread.
     */
    private void postTaskToExecutor(Runnable task, String name) {
        try (var traceEvent =
                ScopedSysTraceEvent.scoped("CronetUrlRequest#postTaskToExecutor " + name)) {
            try {
                mExecutor.execute(
                        () -> {
                            try (var callbackTraceEvent =
                                    ScopedSysTraceEvent.scoped(
                                            "CronetUrlRequest#postTaskToExecutor "
                                                    + name
                                                    + " running callback")) {
                                task.run();
                            }
                        });
            } catch (RejectedExecutionException failException) {
                Log.e(
                        CronetUrlRequestContext.LOG_TAG,
                        "Exception posting task to executor",
                        failException);
                // If posting a task throws an exception, then we fail the request. This exception
                // could be permanent (executor shutdown), transient (AbortPolicy, or
                // CallerRunsPolicy with direct execution not permitted), or caused by the runnables
                // we submit if mUserExecutor is a direct executor and direct execution is not
                // permitted. In the latter two cases, there is at least have a chance to inform the
                // embedder of the request's failure, since failWithException does not enforce that
                // onFailed() is not executed inline.
                failWithException(
                        new CronetExceptionImpl(
                                "Exception posting task to executor", failException));
            }
        }
    }

    private static int convertRequestPriority(int priority) {
        switch (priority) {
            case Builder.REQUEST_PRIORITY_IDLE:
                return RequestPriority.IDLE;
            case Builder.REQUEST_PRIORITY_LOWEST:
                return RequestPriority.LOWEST;
            case Builder.REQUEST_PRIORITY_LOW:
                return RequestPriority.LOW;
            case Builder.REQUEST_PRIORITY_MEDIUM:
                return RequestPriority.MEDIUM;
            case Builder.REQUEST_PRIORITY_HIGHEST:
                return RequestPriority.HIGHEST;
            default:
                return RequestPriority.MEDIUM;
        }
    }

    private static int convertIdempotency(int idempotency) {
        switch (idempotency) {
            case Builder.DEFAULT_IDEMPOTENCY:
                return Idempotency.DEFAULT_IDEMPOTENCY;
            case Builder.IDEMPOTENT:
                return Idempotency.IDEMPOTENT;
            case Builder.NOT_IDEMPOTENT:
                return Idempotency.NOT_IDEMPOTENT;
            default:
                return Idempotency.DEFAULT_IDEMPOTENCY;
        }
    }

    private UrlResponseInfoImpl prepareResponseInfoOnNetworkThread(
            int httpStatusCode,
            String httpStatusText,
            String[] headers,
            boolean wasCached,
            String negotiatedProtocol,
            String proxyServer,
            long receivedByteCount) {
        ArrayList<Map.Entry<String, String>> headersList = new ArrayList<>();
        for (int i = 0; i < headers.length; i += 2) {
            headersList.add(new AbstractMap.SimpleImmutableEntry<>(headers[i], headers[i + 1]));
        }
        return new UrlResponseInfoImpl(
                new ArrayList<>(mUrlChain),
                httpStatusCode,
                httpStatusText,
                headersList,
                wasCached,
                negotiatedProtocol,
                proxyServer,
                receivedByteCount);
    }

    private void checkNotStarted() {
        synchronized (mUrlRequestAdapterLock) {
            if (mStarted || isDoneLocked()) {
                throw new IllegalStateException("Request is already started.");
            }
        }
    }

    /**
     * Helper method to set final status of CronetUrlRequest and clean up the
     * native request adapter.
     */
    @GuardedBy("mUrlRequestAdapterLock")
    private void destroyRequestAdapterLocked(
            @RequestFinishedInfoImpl.FinishedReason int finishedReason) {
        assert mException == null || finishedReason == RequestFinishedInfo.FAILED;
        mFinishedReason = finishedReason;
        if (mUrlRequestAdapter == 0) {
            return;
        }
        mRequestContext.onRequestDestroyed();
        // Posts a task to destroy the native adapter.
        CronetUrlRequestJni.get()
                .destroy(
                        mUrlRequestAdapter,
                        CronetUrlRequest.this,
                        finishedReason == RequestFinishedInfo.CANCELED);
        mUrlRequestAdapter = 0;
    }

    /**
     * If a non-final callback method throws an exception, request gets canceled and exception is
     * reported via onFailed listener callback. Only called on the Executor.
     */
    private void onNonfinalCallbackException(Exception e) {
        mNonfinalUserCallbackExceptionCount++;
        CallbackException requestError =
                new CallbackExceptionImpl("Exception received from UrlRequest.Callback", e);
        Log.e(CronetUrlRequestContext.LOG_TAG, "Exception in CalledByNative method", e);
        failWithException(requestError);
    }

    private void onFinalCallbackException(String method, Exception e) {
        mFinalUserCallbackThrew = true;
        Log.e(CronetUrlRequestContext.LOG_TAG, "Exception in " + method + " method", e);
    }

    /** Called when UploadDataProvider encounters an error. */
    void onUploadException(Throwable e) {
        CallbackException uploadError =
                new CallbackExceptionImpl("Exception received from UploadDataProvider", e);
        Log.e(CronetUrlRequestContext.LOG_TAG, "Exception in upload method", e);
        failWithException(uploadError);
    }

    /** Fails the request with an exception on any thread. */
    private void failWithException(final CronetException exception) {
        synchronized (mUrlRequestAdapterLock) {
            if (isDoneLocked()) {
                return;
            }
            assert mException == null;
            mException = exception;
            destroyRequestAdapterLocked(RequestFinishedInfo.FAILED);
        }
        // onFailed will be invoked from onNativeAdapterDestroyed() to ensure metrics collection.
    }

    ////////////////////////////////////////////////
    // Private methods called by the native code.
    // Always called on network thread.
    ////////////////////////////////////////////////

    /**
     * Called before following redirects. The redirect will only be followed if
     * {@link #followRedirect()} is called. If the redirect response has a body, it will be ignored.
     * This will only be called between start and onResponseStarted.
     *
     * @param newLocation Location where request is redirected.
     * @param httpStatusCode from redirect response
     * @param receivedByteCount count of bytes received for redirect response
     * @param headers an array of response headers with keys at the even indices
     *         followed by the corresponding values at the odd indices.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private void onRedirectReceived(
            final String newLocation,
            int httpStatusCode,
            String httpStatusText,
            String[] headers,
            boolean wasCached,
            String negotiatedProtocol,
            String proxyServer,
            long receivedByteCount) {
        final UrlResponseInfoImpl responseInfo =
                prepareResponseInfoOnNetworkThread(
                        httpStatusCode,
                        httpStatusText,
                        headers,
                        wasCached,
                        negotiatedProtocol,
                        proxyServer,
                        receivedByteCount);

        // Have to do this after creating responseInfo.
        mUrlChain.add(newLocation);

        Runnable task =
                new Runnable() {
                    @Override
                    public void run() {
                        checkCallingThread();
                        synchronized (mUrlRequestAdapterLock) {
                            if (isDoneLocked()) {
                                return;
                            }
                            mWaitingOnRedirect = true;
                        }

                        try {
                            mCallback.onRedirectReceived(
                                    CronetUrlRequest.this, responseInfo, newLocation);
                        } catch (Exception e) {
                            onNonfinalCallbackException(e);
                        }
                    }
                };
        postTaskToExecutor(task, "onRedirectReceived");
    }

    /**
     * Called when the final set of headers, after all redirects, is received. Can only be called
     * once for each request.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private void onResponseStarted(
            int httpStatusCode,
            String httpStatusText,
            String[] headers,
            boolean wasCached,
            String negotiatedProtocol,
            String proxyServer,
            long receivedByteCount) {
        mResponseInfo =
                prepareResponseInfoOnNetworkThread(
                        httpStatusCode,
                        httpStatusText,
                        headers,
                        wasCached,
                        negotiatedProtocol,
                        proxyServer,
                        receivedByteCount);
        Runnable task =
                new Runnable() {
                    @Override
                    public void run() {
                        checkCallingThread();
                        synchronized (mUrlRequestAdapterLock) {
                            if (isDoneLocked()) {
                                return;
                            }
                            mWaitingOnRead = true;
                        }

                        try {
                            mCallback.onResponseStarted(CronetUrlRequest.this, mResponseInfo);
                        } catch (Exception e) {
                            onNonfinalCallbackException(e);
                        }
                    }
                };
        postTaskToExecutor(task, "onResponseStarted");
    }

    /**
     * Called whenever data is received. The ByteBuffer remains valid only until listener callback.
     * Or if the callback pauses the request, it remains valid until the request is resumed.
     * Cancelling the request also invalidates the buffer.
     *
     * @param byteBuffer ByteBuffer containing received data, starting at initialPosition.
     *     Guaranteed to have at least one read byte. Its limit has not yet been updated to reflect
     *     the bytes read.
     * @param bytesRead Number of bytes read.
     * @param initialPosition Original position of byteBuffer when passed to read(). Used as a
     *     minimal check that the buffer hasn't been modified while reading from the network.
     * @param initialLimit Original limit of byteBuffer when passed to read(). Used as a minimal
     *     check that the buffer hasn't been modified while reading from the network.
     * @param receivedByteCount number of bytes received.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private void onReadCompleted(
            final ByteBuffer byteBuffer,
            int bytesRead,
            int initialPosition,
            int initialLimit,
            long receivedByteCount) {
        mResponseInfo.setReceivedByteCount(receivedByteCount);
        if (byteBuffer.position() != initialPosition || byteBuffer.limit() != initialLimit) {
            failWithException(
                    new CronetExceptionImpl("ByteBuffer modified externally during read", null));
            return;
        }
        if (mOnReadCompletedTask == null) {
            mOnReadCompletedTask = new OnReadCompletedRunnable();
        }
        byteBuffer.position(initialPosition + bytesRead);
        mOnReadCompletedTask.mByteBuffer = byteBuffer;
        postTaskToExecutor(mOnReadCompletedTask, "onReadCompleted");
    }

    /**
     * Called when request is completed successfully, no callbacks will be called afterwards.
     *
     * @param receivedByteCount number of bytes received.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private void onSucceeded(long receivedByteCount) {
        mResponseInfo.setReceivedByteCount(receivedByteCount);
        Runnable task =
                new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mUrlRequestAdapterLock) {
                            if (isDoneLocked()) {
                                return;
                            }
                            // Destroy adapter first, so request context could be shut
                            // down from the listener.
                            destroyRequestAdapterLocked(RequestFinishedInfo.SUCCEEDED);
                        }
                        try {
                            mCallback.onSucceeded(CronetUrlRequest.this, mResponseInfo);
                        } catch (Exception e) {
                            onFinalCallbackException("onSucceeded", e);
                        }
                        maybeReportMetrics();
                    }
                };
        postTaskToExecutor(task, "onSucceeded");
    }

    /**
     * Called when error has occurred, no callbacks will be called afterwards.
     *
     * @param errorCode Error code represented by {@code UrlRequestError} that should be mapped to
     *     one of {@link NetworkException#ERROR_HOSTNAME_NOT_RESOLVED NetworkException.ERROR_*}.
     * @param nativeError native net error code.
     * @param source Represented by {@code ErrorSource} which is the initiator of the error.
     * @param errorString textual representation of the error code.
     * @param receivedByteCount number of bytes received.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private void onError(
            int errorCode,
            int nativeError,
            int nativeQuicError,
            @ConnectionCloseSource int source,
            String errorString,
            long receivedByteCount) {
        if (mResponseInfo != null) {
            mResponseInfo.setReceivedByteCount(receivedByteCount);
        }
        if (errorCode == NetworkException.ERROR_QUIC_PROTOCOL_FAILED || nativeQuicError != 0) {
            failWithException(
                    new QuicExceptionImpl(
                            "Exception in CronetUrlRequest: " + errorString,
                            errorCode,
                            nativeError,
                            nativeQuicError,
                            source));
        } else {
            int javaError = mapUrlRequestErrorToApiErrorCode(errorCode);
            failWithException(
                    new NetworkExceptionImpl(
                            "Exception in CronetUrlRequest: " + errorString,
                            javaError,
                            nativeError));
        }
    }

    /** Called when request is canceled, no callbacks will be called afterwards. */
    @SuppressWarnings("unused")
    @CalledByNative
    private void onCanceled() {
        Runnable task =
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            mCallback.onCanceled(CronetUrlRequest.this, mResponseInfo);
                        } catch (Exception e) {
                            onFinalCallbackException("onCanceled", e);
                        }
                        maybeReportMetrics();
                    }
                };
        postTaskToExecutor(task, "onCanceled");
    }

    /** Called by the native code when request status is fetched from the native stack. */
    @SuppressWarnings("unused")
    @CalledByNative
    private void onStatus(
            final VersionSafeCallbacks.UrlRequestStatusListener listener, final int loadState) {
        Runnable task =
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onStatus(UrlRequestUtil.convertLoadState(loadState));
                    }
                };
        postTaskToExecutor(task, "onStatus");
    }

    /**
     * Called by the native code on the network thread to report metrics. The native code will call
     * onSucceeded, onError and onCanceled immediately after this method returns.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private void onMetricsCollected(
            long requestStartMs,
            long dnsStartMs,
            long dnsEndMs,
            long connectStartMs,
            long connectEndMs,
            long sslStartMs,
            long sslEndMs,
            long sendingStartMs,
            long sendingEndMs,
            long pushStartMs,
            long pushEndMs,
            long responseStartMs,
            long requestEndMs,
            boolean socketReused,
            long sentByteCount,
            long receivedByteCount,
            boolean quicConnectionMigrationAttempted,
            boolean quicConnectionMigrationSuccessful) {
        if (mMetrics != null) {
            throw new IllegalStateException("Metrics collection should only happen once.");
        }
        mMetrics =
                new CronetMetrics(
                        requestStartMs,
                        dnsStartMs,
                        dnsEndMs,
                        connectStartMs,
                        connectEndMs,
                        sslStartMs,
                        sslEndMs,
                        sendingStartMs,
                        sendingEndMs,
                        pushStartMs,
                        pushEndMs,
                        responseStartMs,
                        requestEndMs,
                        socketReused,
                        sentByteCount,
                        receivedByteCount);
        mQuicConnectionMigrationAttempted = quicConnectionMigrationAttempted;
        mQuicConnectionMigrationSuccessful = quicConnectionMigrationSuccessful;
        // Metrics are reported to RequestFinishedListener when the final UrlRequest.Callback has
        // been invoked.
    }

    /** Called when the native adapter is destroyed. */
    @SuppressWarnings("unused")
    @CalledByNative
    private void onNativeAdapterDestroyed() {
        try (var traceEvent =
                ScopedSysTraceEvent.scoped("CronetUrlRequest#onNativeAdapterDestroyed")) {
            synchronized (mUrlRequestAdapterLock) {
                if (mOnDestroyedCallbackForTesting != null) {
                    mOnDestroyedCallbackForTesting.run();
                }
                // mException is set when an error is encountered (in native code via onError or in
                // Java code). If mException is not null, notify the mCallback and report metrics.
                if (mException == null) {
                    return;
                }
            }
            Runnable task =
                    new Runnable() {
                        @Override
                        public void run() {
                            try (var callbackTraceEvent =
                                    ScopedSysTraceEvent.scoped(
                                            "CronetUrlRequest#onNativeAdapterDestroyed running"
                                                    + " callback")) {
                                try {
                                    mCallback.onFailed(
                                            CronetUrlRequest.this, mResponseInfo, mException);
                                } catch (Exception e) {
                                    onFinalCallbackException("onFailed", e);
                                }
                                maybeReportMetrics();
                            }
                        }
                    };
            try (var callbackTraceEvent =
                    ScopedSysTraceEvent.scoped(
                            "CronetUrlRequest#onNativeAdapterDestroyed scheduling callback")) {
                try {
                    mExecutor.execute(task);
                } catch (RejectedExecutionException e) {
                    Log.e(CronetUrlRequestContext.LOG_TAG, "Exception posting task to executor", e);
                }
            }
        }
    }

    /** Enforces prohibition of direct execution. */
    void checkCallingThread() {
        if (!mAllowDirectExecutor && mRequestContext.isNetworkThread(Thread.currentThread())) {
            throw new InlineExecutionProhibitedException();
        }
    }

    private int mapUrlRequestErrorToApiErrorCode(int errorCode) {
        switch (errorCode) {
            case UrlRequestError.HOSTNAME_NOT_RESOLVED:
                return NetworkException.ERROR_HOSTNAME_NOT_RESOLVED;
            case UrlRequestError.INTERNET_DISCONNECTED:
                return NetworkException.ERROR_INTERNET_DISCONNECTED;
            case UrlRequestError.NETWORK_CHANGED:
                return NetworkException.ERROR_NETWORK_CHANGED;
            case UrlRequestError.TIMED_OUT:
                return NetworkException.ERROR_TIMED_OUT;
            case UrlRequestError.CONNECTION_CLOSED:
                return NetworkException.ERROR_CONNECTION_CLOSED;
            case UrlRequestError.CONNECTION_TIMED_OUT:
                return NetworkException.ERROR_CONNECTION_TIMED_OUT;
            case UrlRequestError.CONNECTION_REFUSED:
                return NetworkException.ERROR_CONNECTION_REFUSED;
            case UrlRequestError.CONNECTION_RESET:
                return NetworkException.ERROR_CONNECTION_RESET;
            case UrlRequestError.ADDRESS_UNREACHABLE:
                return NetworkException.ERROR_ADDRESS_UNREACHABLE;
            case UrlRequestError.QUIC_PROTOCOL_FAILED:
                return NetworkException.ERROR_QUIC_PROTOCOL_FAILED;
            case UrlRequestError.OTHER:
                return NetworkException.ERROR_OTHER;
            default:
                Log.e(CronetUrlRequestContext.LOG_TAG, "Unknown error code: " + errorCode);
                return errorCode;
        }
    }

    /**
     * Builds the {@link CronetTrafficInfo} associated to this request internal state.
     * This helper methods makes strong assumptions about the state of the request. For this reason
     * it should only be called within {@link CronetUrlRequest#maybeReportMetrics} where these
     * assumptions are guaranteed to be true.
     * @return the {@link CronetTrafficInfo} associated to this request internal state
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private CronetTrafficInfo buildCronetTrafficInfo() {
        assert mMetrics != null;
        assert mRequestHeaders != null;

        // Most of the CronetTrafficInfo fields have similar names/semantics. To avoid bugs due to
        // typos everything is final, this means that things have to initialized through an if/else.
        final Map<String, List<String>> responseHeaders;
        final String negotiatedProtocol;
        final int httpStatusCode;
        final boolean wasCached;
        if (mResponseInfo != null) {
            responseHeaders = mResponseInfo.getAllHeaders();
            negotiatedProtocol = mResponseInfo.getNegotiatedProtocol();
            httpStatusCode = mResponseInfo.getHttpStatusCode();
            wasCached = mResponseInfo.wasCached();
        } else {
            responseHeaders = Collections.emptyMap();
            negotiatedProtocol = "";
            httpStatusCode = 0;
            wasCached = false;
        }

        // TODO(stefanoduo): A better approach might be keeping track of the total length of an
        // upload and use that value as the request body size instead.
        final long requestTotalSizeInBytes = mMetrics.getSentByteCount();
        final long requestHeaderSizeInBytes;
        final long requestBodySizeInBytes;
        // Cached responses might still need to be revalidated over the network before being served
        // (from UrlResponseInfo#wasCached documentation).
        if (wasCached && requestTotalSizeInBytes == 0) {
            // Served from cache without the need to revalidate.
            requestHeaderSizeInBytes = 0;
            requestBodySizeInBytes = 0;
        } else {
            // Served from cache with the need to revalidate or served from the network directly.
            requestHeaderSizeInBytes =
                    CronetRequestCommon.estimateHeadersSizeInBytes(mRequestHeaders);
            requestBodySizeInBytes = max(0, requestTotalSizeInBytes - requestHeaderSizeInBytes);
        }

        final long responseTotalSizeInBytes = mMetrics.getReceivedByteCount();
        final long responseBodySizeInBytes;
        final long responseHeaderSizeInBytes;
        // Cached responses might still need to be revalidated over the network before being served
        // (from UrlResponseInfo#wasCached documentation).
        if (wasCached && responseTotalSizeInBytes == 0) {
            // Served from cache without the need to revalidate.
            responseBodySizeInBytes = 0;
            responseHeaderSizeInBytes = 0;
        } else {
            // Served from cache with the need to revalidate or served from the network directly.
            responseHeaderSizeInBytes =
                    CronetRequestCommon.estimateHeadersSizeInBytes(responseHeaders);
            responseBodySizeInBytes = max(0, responseTotalSizeInBytes - responseHeaderSizeInBytes);
        }

        final Duration headersLatency;
        if (mMetrics.getRequestStart() != null && mMetrics.getResponseStart() != null) {
            headersLatency =
                    Duration.ofMillis(
                            mMetrics.getResponseStart().getTime()
                                    - mMetrics.getRequestStart().getTime());
        } else {
            headersLatency = Duration.ofSeconds(0);
        }

        final Duration totalLatency;
        if (mMetrics.getRequestStart() != null && mMetrics.getRequestEnd() != null) {
            totalLatency =
                    Duration.ofMillis(
                            mMetrics.getRequestEnd().getTime()
                                    - mMetrics.getRequestStart().getTime());
        } else {
            totalLatency = Duration.ofSeconds(0);
        }

        int networkInternalErrorCode = 0;
        int quicNetworkErrorCode = 0;
        @ConnectionCloseSource int source = ConnectionCloseSource.UNKNOWN;
        CronetTrafficInfo.RequestFailureReason failureReason =
                CronetTrafficInfo.RequestFailureReason.UNKNOWN;

        // Going through the API layer will lead to NoSuchMethodError exceptions
        // because there is no guarantee that the API will have the method.
        // It's possible to use an old API of Cronet with a new implementation.
        // In order to work around this, only impl classes are mentioned
        // to ensure that the methods will always be found.
        // See b/361725824 for more information.
        if (mException instanceof NetworkExceptionImpl networkException) {
            networkInternalErrorCode = networkException.getCronetInternalErrorCode();
            failureReason = CronetTrafficInfo.RequestFailureReason.NETWORK;
        } else if (mException instanceof QuicExceptionImpl quicException) {
            networkInternalErrorCode = quicException.getCronetInternalErrorCode();
            quicNetworkErrorCode = quicException.getQuicDetailedErrorCode();
            source = quicException.getConnectionCloseSource();
            failureReason = CronetTrafficInfo.RequestFailureReason.NETWORK;
        } else if (mException != null) {
            failureReason = CronetTrafficInfo.RequestFailureReason.OTHER;
        }

        return new CronetTrafficInfo(requestHeaderSizeInBytes, requestBodySizeInBytes,
                responseHeaderSizeInBytes, responseBodySizeInBytes, httpStatusCode, headersLatency,
                totalLatency, negotiatedProtocol, mQuicConnectionMigrationAttempted,
                mQuicConnectionMigrationSuccessful,
                CronetRequestCommon.finishedReasonToCronetTrafficInfoRequestTerminalState(
                        mFinishedReason),
                mNonfinalUserCallbackExceptionCount, mReadCount,
                mUploadDataStream == null ? 0 : mUploadDataStream.getReadCount(),
                /* isBidiStream= */ false, mFinalUserCallbackThrew, Process.myUid(),
                networkInternalErrorCode, quicNetworkErrorCode, source, failureReason,
                mMetrics.getSocketReused());
    }

    // Maybe report metrics. This method should only be called on Callback's executor thread and
    // after Callback's onSucceeded, onFailed and onCanceled.
    private void maybeReportMetrics() {
        final RefCountDelegate inflightCallbackCount =
                new RefCountDelegate(() -> mRequestContext.onRequestFinished());
        try {
            // If the native adapter was never started, onMetricsCollected() was not called and so
            // we have no metrics to report.
            // TODO: https://issuetracker.google.com/328065446 - we should really prevent this from
            // happening because we will end up not logging the metrics, and the user may end up
            // waiting forever for a request finished callback that will never come.
            if (mMetrics == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    mLogger.logCronetTrafficInfo(
                            mRequestContext.getLogId(), buildCronetTrafficInfo());
                } catch (RuntimeException e) {
                    // Handle any issue gracefully, we should never crash due failures while
                    // logging.
                    Log.e(
                            CronetUrlRequestContext.LOG_TAG,
                            "Error while trying to log CronetTrafficInfo: ",
                            e);
                }
            }

            final RequestFinishedInfo requestInfo =
                    new RequestFinishedInfoImpl(
                            mInitialUrl,
                            mRequestAnnotations,
                            mMetrics,
                            mFinishedReason,
                            mResponseInfo,
                            mException);
            mRequestContext.reportRequestFinished(
                    requestInfo, inflightCallbackCount, mRequestFinishedListener);
        } finally {
            inflightCallbackCount.decrement();
        }
    }

    // Native methods are implemented in cronet_url_request_adapter.cc.
    @NativeMethods
    interface Natives {
        long createRequestAdapter(
                CronetUrlRequest caller,
                long urlRequestContextAdapter,
                String url,
                int priority,
                boolean disableCache,
                boolean disableConnectionMigration,
                boolean trafficStatsTagSet,
                int trafficStatsTag,
                boolean trafficStatsUidSet,
                int trafficStatsUid,
                int idempotency,
                byte[] dictionarySha256Hash,
                ByteBuffer dictionary,
                // TODO(b/358568022): Stop passing position and capacity via JNI.
                int dictionaryPosition,
                int dictionaryCapacity,
                String dictionaryId,
                long networkHandle);

        @NativeClassQualifiedName("CronetURLRequestAdapter")
        boolean setHttpMethod(long nativePtr, CronetUrlRequest caller, String method);

        @NativeClassQualifiedName("CronetURLRequestAdapter")
        boolean addRequestHeader(
                long nativePtr, CronetUrlRequest caller, String name, String value);

        @NativeClassQualifiedName("CronetURLRequestAdapter")
        void start(long nativePtr, CronetUrlRequest caller);

        @NativeClassQualifiedName("CronetURLRequestAdapter")
        void followDeferredRedirect(long nativePtr, CronetUrlRequest caller);

        @NativeClassQualifiedName("CronetURLRequestAdapter")
        boolean readData(
                long nativePtr,
                CronetUrlRequest caller,
                ByteBuffer byteBuffer,
                // TODO(b/358568022): Stop passing position and capacity via JNI.
                int position,
                int capacity);

        @NativeClassQualifiedName("CronetURLRequestAdapter")
        void destroy(long nativePtr, CronetUrlRequest caller, boolean sendOnCanceled);

        @NativeClassQualifiedName("CronetURLRequestAdapter")
        void getStatus(
                long nativePtr,
                CronetUrlRequest caller,
                VersionSafeCallbacks.UrlRequestStatusListener listener);
    }
}
