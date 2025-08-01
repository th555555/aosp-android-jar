/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.content.pm;

import android.annotation.MainThread;
import android.annotation.NonNull;
import android.util.ArrayMap;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.Collection;

/**
 * A simple cache for SDK-defined system feature versions.
 *
 * The dense representation minimizes any per-process memory impact (<1KB). The tradeoff is that
 * custom, non-SDK defined features are not captured by the cache, for which we can rely on the
 * usual IPC cache for related queries.
 *
 * @hide
 */
public final class SystemFeaturesCache {

    // Sentinel value used for SDK-declared features that are unavailable on the current device.
    private static final int UNAVAILABLE_FEATURE_VERSION = Integer.MIN_VALUE;

    // This will be initialized just once, from the process main thread, but ready from any thread.
    private static volatile SystemFeaturesCache sInstance;

    // An array of versions for SDK-defined features, from [0, PackageManager.SDK_FEATURE_COUNT).
    @NonNull
    private final int[] mSdkFeatureVersions;

    /**
     * Installs the process-global cache instance.
     *
     * <p>Note: Usage should be gated on android.content.pm.Flags.cacheSdkSystemFeature(). In
     * practice, this should only be called from 1) SystemServer init, or 2) bindApplication.
     */
    @MainThread
    public static void setInstance(SystemFeaturesCache instance) {
        if (sInstance != null) {
            throw new IllegalStateException("SystemFeaturesCache instance already initialized.");
        }
        sInstance = instance;
    }

    /**
     * Gets the process-global cache instance.
     *
     * Note: Usage should be gated on android.content.pm.Flags.cacheSdkSystemFeature(), and should
     * always occur after the instance has been installed early in the process lifecycle.
     */
    public static @NonNull SystemFeaturesCache getInstance() {
        SystemFeaturesCache instance = sInstance;
        if (instance == null) {
            throw new IllegalStateException("SystemFeaturesCache not initialized");
        }
        return instance;
    }

    /** Checks for existence of the process-global instance. */
    public static boolean hasInstance() {
        return sInstance != null;
    }

    /** Clears the process-global cache instance for testing. */
    @VisibleForTesting
    public static void clearInstance() {
        sInstance = null;
    }

    /**
     * Populates the cache from the set of all available {@link FeatureInfo} definitions.
     *
     * System features declared in {@link PackageManager} will be entered into the cache based on
     * availability in this feature set. Other custom system features will be ignored.
     */
    public SystemFeaturesCache(@NonNull ArrayMap<String, FeatureInfo> availableFeatures) {
        this(availableFeatures.values());
    }

    @VisibleForTesting
    public SystemFeaturesCache(@NonNull Collection<FeatureInfo> availableFeatures) {
        // First set all SDK-defined features as unavailable.
        mSdkFeatureVersions = new int[PackageManager.SDK_FEATURE_COUNT];
        Arrays.fill(mSdkFeatureVersions, UNAVAILABLE_FEATURE_VERSION);

        // Then populate SDK-defined feature versions from the full set of runtime features.
        for (FeatureInfo fi : availableFeatures) {
            int sdkFeatureIndex = PackageManager.maybeGetSdkFeatureIndex(fi.name);
            if (sdkFeatureIndex >= 0) {
                mSdkFeatureVersions[sdkFeatureIndex] = fi.version;
            }
        }
    }

    /**
     * Populates the cache from an array of SDK feature versions originally obtained via {@link
     * #getSdkFeatureVersions()} from another instance.
     */
    public SystemFeaturesCache(@NonNull int[] sdkFeatureVersions) {
        if (sdkFeatureVersions.length != PackageManager.SDK_FEATURE_COUNT) {
            throw new IllegalArgumentException(
                    String.format(
                            "Unexpected cached SDK feature count: %d (expected %d)",
                            sdkFeatureVersions.length, PackageManager.SDK_FEATURE_COUNT));
        }
        mSdkFeatureVersions = sdkFeatureVersions;
    }

    /**
     * Gets the raw cached feature versions.
     *
     * <p>Note: This should generally only be neded for (de)serialization purposes.
     */
    // TODO(b/375000483): Consider reusing the ApplicationSharedMemory mapping for version lookup.
    public int[] getSdkFeatureVersions() {
        return mSdkFeatureVersions;
    }

    /**
     * @return Whether the given feature is available (for SDK-defined features), otherwise null.
     */
    public Boolean maybeHasFeature(@NonNull String featureName, int version) {
        // Features defined outside of the SDK aren't cached.
        int sdkFeatureIndex = PackageManager.maybeGetSdkFeatureIndex(featureName);
        if (sdkFeatureIndex < 0) {
            return null;
        }

        // As feature versions can in theory collide with our sentinel value, in the (extremely)
        // unlikely event that the queried version matches the sentinel value, we can't distinguish
        // between an unavailable feature and a feature with the defined sentinel value.
        if (version == UNAVAILABLE_FEATURE_VERSION
                && mSdkFeatureVersions[sdkFeatureIndex] == UNAVAILABLE_FEATURE_VERSION) {
            return null;
        }

        return mSdkFeatureVersions[sdkFeatureIndex] >= version;
    }
}
