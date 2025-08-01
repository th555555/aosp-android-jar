/* GENERATED SOURCE. DO NOT MODIFY. */
/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.org.conscrypt.ct;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.org.conscrypt.ByteArray;
import com.android.org.conscrypt.Internal;
import com.android.org.conscrypt.OpenSSLKey;
import com.android.org.conscrypt.Platform;
import com.android.org.conscrypt.metrics.StatsLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @hide This class is not part of the Android public SDK API
 */
@Internal
public class LogStoreImpl implements LogStore {
    private static final Logger logger = Logger.getLogger(LogStoreImpl.class.getName());
    private static final int COMPAT_VERSION = 1;
    private static final Path DEFAULT_LOG_LIST;
    private static final long LOG_LIST_CHECK_INTERVAL_IN_NS =
            10L * 60 * 1_000 * 1_000_000; // 10 minutes

    static {
        String androidData = System.getenv("ANDROID_DATA");
        String compatVersion = String.format("v%d", COMPAT_VERSION);
        // /data/misc/keychain/ct/v1/current/log_list.json
        DEFAULT_LOG_LIST = Paths.get(
                androidData, "misc", "keychain", "ct", compatVersion, "current", "log_list.json");
    }

    private final Path logList;
    private StatsLog metrics;
    private State state;
    private Policy policy;
    private int majorVersion;
    private int minorVersion;
    private long timestamp;
    private Map<ByteArray, LogInfo> logs;
    private long logListLastModified;
    private Supplier<Long> clock;
    private long logListLastChecked;

    /* We do not have access to InstantSource. Implement a similar pattern using Supplier. */
    static class SystemTimeSupplier implements Supplier<Long> {
        @Override
        public Long get() {
            return System.nanoTime();
        }
    }

    public LogStoreImpl(Policy policy) {
        this(policy, DEFAULT_LOG_LIST);
    }

    public LogStoreImpl(Policy policy, Path logList) {
        this(policy, logList, Platform.getStatsLog());
    }

    public LogStoreImpl(Policy policy, Path logList, StatsLog metrics) {
        this(policy, logList, metrics, new SystemTimeSupplier());
    }

    public LogStoreImpl(Policy policy, Path logList, StatsLog metrics, Supplier<Long> clock) {
        this.state = State.UNINITIALIZED;
        this.policy = policy;
        this.logList = logList;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    public State getState() {
        ensureLogListIsLoaded();
        return state;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public int getMajorVersion() {
        return majorVersion;
    }

    @Override
    public int getMinorVersion() {
        return minorVersion;
    }

    @Override
    public int getCompatVersion() {
        // Currently, there is only one compatibility version supported. If we
        // are loaded or initialized, it means the expected compatibility
        // version was found.
        if (state == State.LOADED || state == State.COMPLIANT || state == State.NON_COMPLIANT) {
            return COMPAT_VERSION;
        }
        return 0;
    }

    @Override
    public int getMinCompatVersionAvailable() {
        return getCompatVersion();
    }

    @Override
    public LogInfo getKnownLog(byte[] logId) {
        if (logId == null) {
            return null;
        }
        if (!ensureLogListIsLoaded()) {
            return null;
        }
        ByteArray buf = new ByteArray(logId);
        LogInfo log = logs.get(buf);
        if (log != null) {
            return log;
        }
        return null;
    }

    /* Ensures the log list is loaded.
     * Returns true if the log list is usable.
     */
    private synchronized boolean ensureLogListIsLoaded() {
        resetLogListIfRequired();
        State previousState = state;
        if (state == State.UNINITIALIZED) {
            state = loadLogList();
        }
        if (state == State.LOADED && policy != null) {
            state = policy.isLogStoreCompliant(this) ? State.COMPLIANT : State.NON_COMPLIANT;
        }
        if (state != previousState) {
            metrics.updateCTLogListStatusChanged(this);
        }
        return state == State.COMPLIANT;
    }

    private synchronized void resetLogListIfRequired() {
        long now = clock.get();
        if (this.logListLastChecked + LOG_LIST_CHECK_INTERVAL_IN_NS > now) {
            return;
        }
        this.logListLastChecked = now;
        try {
            long lastModified = Files.getLastModifiedTime(logList).toMillis();
            if (this.logListLastModified == lastModified) {
                // The log list has the same last modified timestamp. Keep our
                // current cached value.
                return;
            }
        } catch (IOException e) {
            if (this.logListLastModified == 0) {
                // The log list is not accessible now and it has never been
                // previously, there is nothing to do.
                return;
            }
        }
        this.state = State.UNINITIALIZED;
        this.logs = null;
        this.timestamp = 0;
        this.majorVersion = 0;
        this.minorVersion = 0;
    }

    private State loadLogList() {
        byte[] content;
        long lastModified;
        try {
            content = Files.readAllBytes(logList);
            lastModified = Files.getLastModifiedTime(logList).toMillis();
        } catch (IOException e) {
            return State.NOT_FOUND;
        }
        if (content == null) {
            return State.NOT_FOUND;
        }
        JSONObject json;
        try {
            json = new JSONObject(new String(content, UTF_8));
        } catch (JSONException e) {
            logger.log(Level.WARNING, "Unable to parse log list", e);
            return State.MALFORMED;
        }
        HashMap<ByteArray, LogInfo> logsMap = new HashMap<>();
        try {
            majorVersion = parseMajorVersion(json.getString("version"));
            minorVersion = parseMinorVersion(json.getString("version"));
            timestamp = json.getLong("log_list_timestamp");
            JSONArray operators = json.getJSONArray("operators");
            for (int i = 0; i < operators.length(); i++) {
                JSONObject operator = operators.getJSONObject(i);
                String operatorName = operator.getString("name");
                JSONArray logs = operator.getJSONArray("logs");
                for (int j = 0; j < logs.length(); j++) {
                    JSONObject log = logs.getJSONObject(j);

                    LogInfo.Builder builder =
                            new LogInfo.Builder()
                                    .setDescription(log.getString("description"))
                                    .setPublicKey(parsePubKey(log.getString("key")))
                                    .setUrl(log.getString("url"))
                                    .setOperator(operatorName);

                    JSONObject stateObject = log.optJSONObject("state");
                    if (stateObject != null) {
                        String state = stateObject.keys().next();
                        long stateTimestamp = stateObject.getJSONObject(state).getLong("timestamp");
                        builder.setState(parseState(state), stateTimestamp);
                    }

                    LogInfo logInfo = builder.build();
                    byte[] logId = Base64.getDecoder().decode(log.getString("log_id"));

                    // The logId computed using the public key should match the log_id field.
                    if (!Arrays.equals(logInfo.getID(), logId)) {
                        throw new IllegalArgumentException("logId does not match publicKey");
                    }

                    logsMap.put(new ByteArray(logId), logInfo);
                }
            }
        } catch (JSONException | IllegalArgumentException e) {
            logger.log(Level.WARNING, "Unable to parse log list", e);
            return State.MALFORMED;
        }
        this.logs = Collections.unmodifiableMap(logsMap);
        this.logListLastModified = lastModified;
        return State.LOADED;
    }

    private static int parseMajorVersion(String version) {
        int pos = version.indexOf(".");
        if (pos == -1) {
            pos = version.length();
        }
        try {
            return Integer.parseInt(version.substring(0, pos));
        } catch (IndexOutOfBoundsException | NumberFormatException e) {
            return 0;
        }
    }

    private static int parseMinorVersion(String version) {
        int pos = version.indexOf(".");
        if (pos != -1 && pos < version.length()) {
            try {
                return Integer.parseInt(version.substring(pos + 1, version.length()));
            } catch (IndexOutOfBoundsException | NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static int parseState(String state) {
        switch (state) {
            case "pending":
                return LogInfo.STATE_PENDING;
            case "qualified":
                return LogInfo.STATE_QUALIFIED;
            case "usable":
                return LogInfo.STATE_USABLE;
            case "readonly":
                return LogInfo.STATE_READONLY;
            case "retired":
                return LogInfo.STATE_RETIRED;
            case "rejected":
                return LogInfo.STATE_REJECTED;
            default:
                throw new IllegalArgumentException("Unknown log state: " + state);
        }
    }

    private static PublicKey parsePubKey(String key) {
        byte[] pem = ("-----BEGIN PUBLIC KEY-----\n" + key + "\n-----END PUBLIC KEY-----")
                             .getBytes(US_ASCII);
        PublicKey pubkey;
        try {
            pubkey = OpenSSLKey.fromPublicKeyPemInputStream(new ByteArrayInputStream(pem))
                             .getPublicKey();
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        }
        return pubkey;
    }
}
