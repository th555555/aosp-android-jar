/* GENERATED SOURCE. DO NOT MODIFY. */
/*
 * Copyright 2016 The Android Open Source Project
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

package com.android.org.conscrypt;

import static com.android.org.conscrypt.SSLUtils.EngineStates.STATE_CLOSED;
import static com.android.org.conscrypt.SSLUtils.EngineStates.STATE_HANDSHAKE_COMPLETED;
import static com.android.org.conscrypt.SSLUtils.EngineStates.STATE_HANDSHAKE_STARTED;
import static com.android.org.conscrypt.SSLUtils.EngineStates.STATE_NEW;
import static com.android.org.conscrypt.SSLUtils.EngineStates.STATE_READY;
import static com.android.org.conscrypt.SSLUtils.EngineStates.STATE_READY_HANDSHAKE_CUT_THROUGH;

import static javax.net.ssl.SSLEngineResult.Status.CLOSED;
import static javax.net.ssl.SSLEngineResult.Status.OK;

import com.android.org.conscrypt.metrics.StatsLog;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

/**
 * Implements crypto handling by delegating to {@link ConscryptEngine}.
 */
class ConscryptEngineSocket extends OpenSSLSocketImpl implements SSLParametersImpl.AliasChooser {
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private final ConscryptEngine engine;
    private final Object stateLock = new Object();
    private final Object handshakeLock = new Object();

    private SSLOutputStream out;
    private SSLInputStream in;

    private long handshakeStartedMillis = 0;

    private BufferAllocator bufferAllocator = ConscryptEngine.getDefaultBufferAllocator();

    // @GuardedBy("stateLock");
    private int state = STATE_NEW;

    // The constructors should not be called except from the Platform class, because we may
    // want to construct a subclass instead.
    ConscryptEngineSocket(SSLParametersImpl sslParameters) throws IOException {
        engine = newEngine(sslParameters, this);
    }

    ConscryptEngineSocket(String hostname, int port, SSLParametersImpl sslParameters)
            throws IOException {
        super(hostname, port);
        engine = newEngine(sslParameters, this);
    }

    ConscryptEngineSocket(InetAddress address, int port, SSLParametersImpl sslParameters)
            throws IOException {
        super(address, port);
        engine = newEngine(sslParameters, this);
    }

    ConscryptEngineSocket(String hostname, int port, InetAddress clientAddress, int clientPort,
            SSLParametersImpl sslParameters) throws IOException {
        super(hostname, port, clientAddress, clientPort);
        engine = newEngine(sslParameters, this);
    }

    ConscryptEngineSocket(InetAddress address, int port, InetAddress clientAddress, int clientPort,
            SSLParametersImpl sslParameters) throws IOException {
        super(address, port, clientAddress, clientPort);
        engine = newEngine(sslParameters, this);
    }

    ConscryptEngineSocket(Socket socket, String hostname, int port, boolean autoClose,
            SSLParametersImpl sslParameters) throws IOException {
        super(socket, hostname, port, autoClose);
        engine = newEngine(sslParameters, this);
    }

    private static ConscryptEngine newEngine(
            SSLParametersImpl sslParameters, final ConscryptEngineSocket socket) {
        SSLParametersImpl modifiedParams;
        if (sslParameters.isSpake()) {
            modifiedParams = sslParameters.cloneWithSpake();
        } else if (Platform.supportsX509ExtendedTrustManager()) {
            modifiedParams = sslParameters.cloneWithTrustManager(
                    getDelegatingTrustManager(sslParameters.getX509TrustManager(), socket));
        } else {
            modifiedParams = sslParameters;
        }
        ConscryptEngine engine =
                new ConscryptEngine(modifiedParams, socket.peerInfoProvider(), socket);

        // When the handshake completes, notify any listeners.
        engine.setHandshakeListener(new HandshakeListener() {
            /**
             * Protected by {@code stateLock}
             */
            @Override
            public void onHandshakeFinished() {
                // Just call the outer class method.
                socket.onEngineHandshakeFinished();
            }
        });

        // Transition the engine state to MODE_SET
        engine.setUseClientMode(sslParameters.getUseClientMode());
        return engine;
    }

    // Returns a trust manager that delegates to the given trust manager, but maps SSLEngine
    // references to the given ConscryptEngineSocket.  Our internal engine will call
    // the SSLEngine-receiving methods, but our callers expect the SSLSocket-receiving
    // methods to get called.
    private static X509TrustManager getDelegatingTrustManager(
            final X509TrustManager delegate, final ConscryptEngineSocket socket) {
        if (delegate instanceof X509ExtendedTrustManager) {
            final X509ExtendedTrustManager extendedDelegate = (X509ExtendedTrustManager) delegate;
            return new X509ExtendedTrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] x509Certificates, String s,
                        Socket socket) throws CertificateException {
                    throw new AssertionError("Should not be called");
                }
                @Override
                public void checkServerTrusted(X509Certificate[] x509Certificates, String s,
                        Socket socket) throws CertificateException {
                    throw new AssertionError("Should not be called");
                }
                @Override
                public void checkClientTrusted(X509Certificate[] x509Certificates, String s,
                        SSLEngine sslEngine) throws CertificateException {
                    extendedDelegate.checkClientTrusted(x509Certificates, s, socket);
                }
                @Override
                public void checkServerTrusted(X509Certificate[] x509Certificates, String s,
                        SSLEngine sslEngine) throws CertificateException {
                    extendedDelegate.checkServerTrusted(x509Certificates, s, socket);
                }
                @Override
                public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
                        throws CertificateException {
                    extendedDelegate.checkClientTrusted(x509Certificates, s);
                }
                @Override
                public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
                        throws CertificateException {
                    extendedDelegate.checkServerTrusted(x509Certificates, s);
                }
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return extendedDelegate.getAcceptedIssuers();
                }
            };
        }
        return delegate;
    }

    @Override
    public final SSLParameters getSSLParameters() {
        return engine.getSSLParameters();
    }

    @Override
    public final void setSSLParameters(SSLParameters sslParameters) {
        engine.setSSLParameters(sslParameters);
    }

    @Override
    public final void startHandshake() throws IOException {
        checkOpen();

        try {
            synchronized (handshakeLock) {
                // Only lock stateLock when we begin the handshake. This is done so that we don't
                // hold the stateLock when we invoke the handshake completion listeners.
                synchronized (stateLock) {
                    // Initialize the handshake if we haven't already.
                    if (state == STATE_NEW) {
                        transitionTo(STATE_HANDSHAKE_STARTED);
                        engine.beginHandshake();
                        createInputStream();
                        createOutputStream();
                    } else {
                        // We've either started the handshake already or have been closed.
                        // Do nothing in both cases.
                        //
                        // NOTE: BoringSSL does not support initiating renegotiation, so we always
                        // ignore addition handshake calls.
                        return;
                    }
                }
                doHandshake();
            }
        } catch (IOException e) {
            close();
            throw e;
        } catch (Exception e) {
            close();
            // Convert anything else to a handshake exception.
            throw SSLUtils.toSSLHandshakeException(e);
        }
    }

    private void doHandshake() throws IOException {
        try {
            boolean finished = false;
            while (!finished) {
                switch (engine.getHandshakeStatus()) {
                    case NEED_UNWRAP:
                        if (in.processDataFromSocket(EmptyArray.BYTE, 0, 0) < 0) {
                            // Can't complete the handshake due to EOF.
                            close();
                            throw SSLUtils.toSSLHandshakeException(
                                    new EOFException("connection closed"));
                        }
                        break;
                    case NEED_WRAP: {
                        out.writeInternal(EMPTY_BUFFER);
                        // Always flush handshake frames immediately.
                        out.flushInternal();
                        break;
                    }
                    case NEED_TASK: {
                        // Should never get here, since our engine never provides tasks.
                        close();
                        throw new IllegalStateException("Engine tasks are unsupported");
                    }
                    case NOT_HANDSHAKING:
                    case FINISHED: {
                        // Handshake is complete.
                        finished = true;
                        break;
                    }
                    default: {
                        throw new IllegalStateException(
                            "Unknown handshake status: " + engine.getHandshakeStatus());
                    }
                }
            }
            if (isState(STATE_HANDSHAKE_COMPLETED)) {
                // STATE_READY_HANDSHAKE_CUT_THROUGH will wake up any waiting threads which can
                // race with the listeners, but that's OK.
                transitionTo(STATE_READY_HANDSHAKE_CUT_THROUGH);
                notifyHandshakeCompletedListeners();
                transitionTo(STATE_READY);
            }
        } catch (SSLException e) {
            drainOutgoingQueue();
            close();
            throw e;
        } catch (IOException e) {
            close();
            throw e;
        } catch (Exception e) {
            close();
            // Convert anything else to a handshake exception.
            throw SSLUtils.toSSLHandshakeException(e);
        }
    }

    private boolean isState(int desiredState) {
        synchronized (stateLock) {
            return state == desiredState;
        }
    }

    private int transitionTo(int newState) {
        synchronized (stateLock) {
            if (state == newState) {
                return state;
            }

            int previousState = state;
            boolean notify = false;
            switch (newState) {
                case STATE_HANDSHAKE_STARTED:
                    handshakeStartedMillis = Platform.getMillisSinceBoot();
                    break;

                case STATE_READY_HANDSHAKE_CUT_THROUGH:
                    if (handshakeStartedMillis > 0) {
                        StatsLog statsLog = Platform.getStatsLog();
                        statsLog.countTlsHandshake(true, engine.getSession().getProtocol(),
                                engine.getSession().getCipherSuite(),
                                Platform.getMillisSinceBoot() - handshakeStartedMillis);
                        handshakeStartedMillis = 0;
                    }
                    notify = true;
                    break;

                case STATE_READY:
                    notify = true;
                    break;

                case STATE_CLOSED:
                    if (handshakeStartedMillis > 0) {
                        StatsLog statsLog = Platform.getStatsLog();
                        // Handshake was in progress and so must have failed.
                        statsLog.countTlsHandshake(false, "TLS_PROTO_FAILED", "TLS_CIPHER_FAILED",
                                Platform.getMillisSinceBoot() - handshakeStartedMillis);
                        handshakeStartedMillis = 0;
                    }
                    notify = true;
                    break;

                default:
                    break;
            }

            state = newState;
            if (notify) {
                stateLock.notifyAll();
            }
            return previousState;
        }
    }

    @Override
    public final InputStream getInputStream() throws IOException {
        checkOpen();
        return createInputStream();
    }

    private SSLInputStream createInputStream() {
        synchronized (stateLock) {
            if (in == null) {
                in = new SSLInputStream();
            }
        }
        return in;
    }

    @Override
    public final OutputStream getOutputStream() throws IOException {
        checkOpen();
        return createOutputStream();
    }

    private SSLOutputStream createOutputStream() {
        synchronized (stateLock) {
            if (out == null) {
                out = new SSLOutputStream();
            }
        }
        return out;
    }

    @Override
    public final SSLSession getHandshakeSession() {
        return engine.handshakeSession();
    }

    @Override
    public final SSLSession getSession() {
        if (isConnected()) {
            try {
                waitForHandshake();
            } catch (IOException e) {
                // Fall through
            }
        }
        return engine.getSession();
    }

    @Override
    final SSLSession getActiveSession() {
        return engine.getSession();
    }

    @Override
    public final boolean getEnableSessionCreation() {
        return engine.getEnableSessionCreation();
    }

    @Override
    public final void setEnableSessionCreation(boolean flag) {
        engine.setEnableSessionCreation(flag);
    }

    @Override
    public final String[] getSupportedCipherSuites() {
        return engine.getSupportedCipherSuites();
    }

    @Override
    public final String[] getEnabledCipherSuites() {
        return engine.getEnabledCipherSuites();
    }

    @Override
    public final void setEnabledCipherSuites(String[] suites) {
        engine.setEnabledCipherSuites(suites);
    }

    @Override
    public final String[] getSupportedProtocols() {
        return engine.getSupportedProtocols();
    }

    @Override
    public final String[] getEnabledProtocols() {
        return engine.getEnabledProtocols();
    }

    @Override
    public final void setEnabledProtocols(String[] protocols) {
        engine.setEnabledProtocols(protocols);
    }

    /**
     * This method enables Server Name Indication.  If the hostname is not a valid SNI hostname,
     * the SNI extension will be omitted from the handshake.
     *
     * @param hostname the desired SNI hostname, or null to disable
     */
    @android.compat.annotation.
    UnsupportedAppUsage(maxTargetSdk = dalvik.annotation.compat.VersionCodes.Q,
            publicAlternatives = "Use {@code javax.net.ssl.SSLParameters#setServerNames}.")
    @Override
    public final void
    setHostname(String hostname) {
        engine.setHostname(hostname);
        super.setHostname(hostname);
    }

    @android.compat.annotation.
    UnsupportedAppUsage(maxTargetSdk = dalvik.annotation.compat.VersionCodes.Q,
            publicAlternatives = "Use {@link android.net.ssl.SSLSockets#setUseSessionTickets}.")
    @Override
    public final void
    setUseSessionTickets(boolean useSessionTickets) {
        engine.setUseSessionTickets(useSessionTickets);
    }

    @Override
    public final void setChannelIdEnabled(boolean enabled) {
        engine.setChannelIdEnabled(enabled);
    }

    @Override
    public final byte[] getChannelId() throws SSLException {
        return engine.getChannelId();
    }

    @Override
    public final void setChannelIdPrivateKey(PrivateKey privateKey) {
        engine.setChannelIdPrivateKey(privateKey);
    }

    @Override
    byte[] getTlsUnique() {
        return engine.getTlsUnique();
    }

    @Override
    byte[] exportKeyingMaterial(String label, byte[] context, int length) throws SSLException {
        return engine.exportKeyingMaterial(label, context, length);
    }

    @Override
    public final boolean getUseClientMode() {
        return engine.getUseClientMode();
    }

    @Override
    public final void setUseClientMode(boolean mode) {
        engine.setUseClientMode(mode);
    }

    @Override
    public final boolean getWantClientAuth() {
        return engine.getWantClientAuth();
    }

    @Override
    public final boolean getNeedClientAuth() {
        return engine.getNeedClientAuth();
    }

    @Override
    public final void setNeedClientAuth(boolean need) {
        engine.setNeedClientAuth(need);
    }

    @Override
    public final void setWantClientAuth(boolean want) {
        engine.setWantClientAuth(want);
    }

    @Override
    @SuppressWarnings("UnsynchronizedOverridesSynchronized")
    public final void close() throws IOException {
        // TODO: Close SSL sockets using a background thread so they close gracefully.

        if (stateLock == null) {
            // Constructor failed, e.g. superclass constructor called close()
            return;
        }

        int previousState = transitionTo(STATE_CLOSED);
        if (previousState == STATE_CLOSED) {
            return;
        }
        try {
            // Close the engine.
            engine.closeInbound();
            engine.closeOutbound();

            // Closing the outbound direction of a connected engine will trigger a TLS close
            // notify, which we should try and send.
            // If we don't, then closeOutbound won't be able to free resources because there are
            // bytes queued for transmission so drain the queue those and call closeOutbound a
            // second time.
            if (previousState >= STATE_HANDSHAKE_STARTED) {
                drainOutgoingQueue();
                engine.closeOutbound();
            }
        } finally {
            // In case of an exception thrown while closing the engine, we still need to close the
            // underlying socket and release any resources the input stream is holding.
            try {
                super.close();
            } finally {
                if (in != null) {
                    in.release();
                }
            }
        }
    }

    @Override
    public void setHandshakeTimeout(int handshakeTimeoutMilliseconds) throws SocketException {
        // Not supported but ignored rather than throwing for compatibility: b/146041327
    }

    @Override
    final void setApplicationProtocols(String[] protocols) {
        engine.setApplicationProtocols(protocols);
    }

    @Override
    final String[] getApplicationProtocols() {
        return engine.getApplicationProtocols();
    }

    @Override
    public final String getApplicationProtocol() {
        return engine.getApplicationProtocol();
    }

    @Override
    public final String getHandshakeApplicationProtocol() {
        return engine.getHandshakeApplicationProtocol();
    }

    @Override
    public final void setApplicationProtocolSelector(ApplicationProtocolSelector selector) {
        setApplicationProtocolSelector(
                selector == null ? null : new ApplicationProtocolSelectorAdapter(this, selector));
    }

    @Override
    final void setApplicationProtocolSelector(ApplicationProtocolSelectorAdapter selector) {
        engine.setApplicationProtocolSelector(selector);
    }

    void setBufferAllocator(BufferAllocator bufferAllocator) {
        engine.setBufferAllocator(bufferAllocator);
        this.bufferAllocator = bufferAllocator;
    }

    private void onEngineHandshakeFinished() {
        // Don't do anything here except change state.  This method will be called from
        // e.g. wrap() which is non re-entrant so we can't call anything that might do
        // IO until after it exits, e.g. in doHandshake().
        if (isState(STATE_HANDSHAKE_STARTED)) {
            transitionTo(STATE_HANDSHAKE_COMPLETED);
        }
    }

    /**
     * Waits for the handshake to complete.
     */
    private void waitForHandshake() throws IOException {
        startHandshake();

        synchronized (stateLock) {
            while (state != STATE_READY
                    // Waiting threads are allowed to compete with handshake listeners for access.
                    && state != STATE_READY_HANDSHAKE_CUT_THROUGH && state != STATE_CLOSED) {
                try {
                    stateLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting for handshake", e);
                }
            }

            if (state == STATE_CLOSED) {
                throw new SocketException("Socket is closed");
            }
        }
    }

    private void drainOutgoingQueue() {
        try {
            while (engine.pendingOutboundEncryptedBytes() > 0) {
                out.writeInternal(EMPTY_BUFFER);
                // Always flush handshake frames immediately.
                out.flushInternal();
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    private OutputStream getUnderlyingOutputStream() throws IOException {
        return super.getOutputStream();
    }

    private InputStream getUnderlyingInputStream() throws IOException {
        return super.getInputStream();
    }

    @Override
    public final String chooseServerAlias(X509KeyManager keyManager, String keyType) {
        return keyManager.chooseServerAlias(keyType, null, this);
    }

    @Override
    public final String chooseClientAlias(
            X509KeyManager keyManager, X500Principal[] issuers, String[] keyTypes) {
        return keyManager.chooseClientAlias(keyTypes, issuers, this);
    }

    /**
     * Wrap bytes written to the underlying socket.
     */
    private final class SSLOutputStream extends OutputStream {
        private final Object writeLock = new Object();
        private final ByteBuffer target;
        private final int targetArrayOffset;
        private OutputStream socketOutputStream;

        SSLOutputStream() {
            target = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
            targetArrayOffset = target.arrayOffset();
        }

        @Override
        public void close() throws IOException {
            ConscryptEngineSocket.this.close();
        }

        @Override
        public void write(int b) throws IOException {
            waitForHandshake();
            synchronized (writeLock) {
                write(new byte[] {(byte) b});
            }
        }

        @Override
        public void write(byte[] b) throws IOException {
            waitForHandshake();
            synchronized (writeLock) {
                writeInternal(ByteBuffer.wrap(b));
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            waitForHandshake();
            synchronized (writeLock) {
                writeInternal(ByteBuffer.wrap(b, off, len));
            }
        }

        private void writeInternal(ByteBuffer buffer) throws IOException {
            Platform.blockGuardOnNetwork();
            checkOpen();
            init();

            // Need to loop through at least once to enable handshaking where no application
            // bytes are processed.
            int len = buffer.remaining();
            SSLEngineResult engineResult;
            do {
                target.clear();
                engineResult = engine.wrap(buffer, target);
                if (engineResult.getStatus() != OK && engineResult.getStatus() != CLOSED) {
                    throw new SSLException("Unexpected engine result " + engineResult.getStatus());
                }
                if (target.position() != engineResult.bytesProduced()) {
                    throw new SSLException("Engine bytesProduced " + engineResult.bytesProduced()
                            + " does not match bytes written " + target.position());
                }
                len -= engineResult.bytesConsumed();
                if (len != buffer.remaining()) {
                    throw new SSLException("Engine did not read the correct number of bytes");
                }
                if (engineResult.getStatus() == CLOSED && engineResult.bytesProduced() == 0) {
                    if (len > 0) {
                        throw new SocketException("Socket closed");
                    }
                    break;
                }

                target.flip();

                // Write the data to the socket.
                writeToSocket();
            } while (len > 0);
        }

        @Override
        public void flush() throws IOException {
            waitForHandshake();
            synchronized (writeLock) {
                flushInternal();
            }
        }

        private void flushInternal() throws IOException {
            checkOpen();
            init();
            socketOutputStream.flush();
        }

        private void init() throws IOException {
            if (socketOutputStream == null) {
                socketOutputStream = getUnderlyingOutputStream();
            }
        }

        private void writeToSocket() throws IOException {
            // Write the data to the socket.
            socketOutputStream.write(target.array(), targetArrayOffset, target.limit());
        }
    }

    /**
     * Unwrap bytes read from the underlying socket.
     */
    private final class SSLInputStream extends InputStream {
        private final Object readLock = new Object();
        private final byte[] singleByte = new byte[1];
        private final ByteBuffer fromEngine;
        private final ByteBuffer fromSocket;
        private final int fromSocketArrayOffset;
        private final AllocatedBuffer allocatedBuffer;
        private InputStream socketInputStream;

        SSLInputStream() {
            if (bufferAllocator != null) {
                allocatedBuffer = bufferAllocator.allocateDirectBuffer(
                        engine.getSession().getApplicationBufferSize());
                fromEngine = allocatedBuffer.nioBuffer();
            } else {
                allocatedBuffer = null;
                fromEngine = ByteBuffer.allocateDirect(engine.getSession().getApplicationBufferSize());
            }
            // Initially fromEngine.remaining() == 0.
            fromEngine.flip();
            fromSocket = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
            fromSocketArrayOffset = fromSocket.arrayOffset();
        }

        @Override
        public void close() throws IOException {
            ConscryptEngineSocket.this.close();
        }

        void release() {
            synchronized (readLock) {
                if (allocatedBuffer != null) {
                    allocatedBuffer.release();
                }
            }
        }

        @Override
        public int read() throws IOException {
            waitForHandshake();
            synchronized (readLock) {
                // Handle returning of -1 if EOF is reached.
                int count = read(singleByte, 0, 1);
                if (count == -1) {
                    // Handle EOF.
                    return -1;
                }
                if (count != 1) {
                    throw new SSLException("read incorrect number of bytes " + count);
                }
                return singleByte[0] & 0xff;
            }
        }

        @Override
        public int read(byte[] b) throws IOException {
            waitForHandshake();
            synchronized (readLock) {
                return read(b, 0, b.length);
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            waitForHandshake();
            if (len == 0) {
                return 0;
            }
            synchronized (readLock) {
                return readUntilDataAvailable(b, off, len);
            }
        }

        @Override
        public int available() throws IOException {
            waitForHandshake();
            synchronized (readLock) {
                init();
                return fromEngine.remaining();
            }
        }

        private boolean isHandshaking(HandshakeStatus status) {
            switch(status) {
                case NEED_TASK:
                case NEED_WRAP:
                case NEED_UNWRAP:
                    return true;
                default:
                    return false;
            }
        }

        private int readUntilDataAvailable(byte[] b, int off, int len) throws IOException {
            int count;
            do {
                count = processDataFromSocket(b, off, len);
            } while (count == 0);
            return count;
        }

        // Returns any decrypted data from the engine.  If no data is currently present in the
        // engine's output buffer, reads from the input socket until the engine has processed
        // at least one TLS record, then returns any data in the output buffer or 0 if no
        // data is available.  This is used both during handshaking (in which case, the records
        // will produce no data and this method will return 0) and by the InputStream read()
        // methods that expect records to produce application data.
        private int processDataFromSocket(byte[] b, int off, int len) throws IOException {
            Platform.blockGuardOnNetwork();
            checkOpen();

            // Make sure the input stream has been created.
            init();

            for (;;) {
                // Serve any remaining data from the engine first.
                if (fromEngine.remaining() > 0) {
                    int readFromEngine = Math.min(fromEngine.remaining(), len);
                    fromEngine.get(b, off, readFromEngine);
                    return readFromEngine;
                }

                // Try to unwrap any data already in the socket buffer.
                boolean needMoreDataFromSocket = true;

                // Unwrap the unencrypted bytes into the engine buffer.
                fromSocket.flip();
                fromEngine.clear();

                boolean engineHandshaking = isHandshaking(engine.getHandshakeStatus());
                SSLEngineResult engineResult = engine.unwrap(fromSocket, fromEngine);

                // Shift any remaining data to the beginning of the buffer so that
                // we can accommodate the next full packet. After this is called,
                // limit will be restored to capacity and position will point just
                // past the end of the data.
                fromSocket.compact();
                fromEngine.flip();

                switch (engineResult.getStatus()) {
                    case BUFFER_UNDERFLOW: {
                        if (engineResult.bytesProduced() == 0) {
                            // Need to read more data from the socket.
                            break;
                        }
                        // Also serve the data that was produced.
                        needMoreDataFromSocket = false;
                        break;
                    }
                    case OK: {
                        // We processed the entire packet successfully...

                        if (!engineHandshaking && isHandshaking(engineResult.getHandshakeStatus())
                            && isHandshakeFinished()) {
                            // The received packet is the beginning of a renegotiation handshake.
                            // Perform another handshake.
                            renegotiate();
                            return 0;
                        }

                        needMoreDataFromSocket = false;
                        break;
                    }
                    case CLOSED: {
                        // EOF
                        return -1;
                    }
                    default: {
                        // Anything else is an error.
                        throw new SSLException(
                                "Unexpected engine result " + engineResult.getStatus());
                    }
                }

                if (!needMoreDataFromSocket && engineResult.bytesProduced() == 0) {
                    // Read successfully, but produced no data. Possibly part of a
                    // handshake.
                    return 0;
                }

                // Read more data from the socket.
                if (needMoreDataFromSocket && readFromSocket() == -1) {
                    // Failed to read the next encrypted packet before reaching EOF.
                    return -1;
                }

                // Continue the loop and return the data from the engine buffer.
            }
        }

        private boolean isHandshakeFinished() {
            synchronized (stateLock) {
                return state > STATE_HANDSHAKE_STARTED;
            }
        }

        /**
         * Processes a renegotiation received from the remote peer.
         */
        private void renegotiate() throws IOException {
            synchronized (handshakeLock) {
                doHandshake();
            }
        }

        private void init() throws IOException {
            if (socketInputStream == null) {
                socketInputStream = getUnderlyingInputStream();
            }
        }

        private int readFromSocket() throws IOException {
            try {
                // Read directly to the underlying array and increment the buffer position if
                // appropriate.
                int pos = fromSocket.position();
                int lim = fromSocket.limit();
                int read = socketInputStream.read(
                    fromSocket.array(), fromSocketArrayOffset + pos, lim - pos);

                if (read > 0) {
                    fromSocket.position(pos + read);
                }
                return read;
            } catch (EOFException e) {
                return -1;
            }
        }
    }
}
