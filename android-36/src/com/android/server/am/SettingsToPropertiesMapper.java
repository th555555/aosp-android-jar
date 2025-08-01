/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.aconfig_new_storage.Flags.enableAconfigStorageDaemon;
import static com.android.aconfig_new_storage.Flags.enableAconfigdFromMainline;
import static com.android.aconfig_new_storage.Flags.supportClearLocalOverridesImmediately;
import static com.android.aconfig_new_storage.Flags.supportImmediateLocalOverrides;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

import android.aconfigd.Aconfigd.StorageRequestMessage;
import android.aconfigd.Aconfigd.StorageRequestMessages;
import android.aconfigd.Aconfigd.StorageReturnMessage;
import android.aconfigd.Aconfigd.StorageReturnMessages;
import android.annotation.NonNull;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.SystemProperties;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;

import com.android.internal.annotations.VisibleForTesting;
import com.android.providers.settings.Flags;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;

/**
 * Maps system settings to system properties.
 * <p>The properties are dynamically updated when settings change.
 * @hide
 */
public class SettingsToPropertiesMapper {

    private static final String TAG = "SettingsToPropertiesMapper";

    private static final String SYSTEM_PROPERTY_PREFIX = "persist.device_config.";

    private static final String RESET_PERFORMED_PROPERTY = "device_config.reset_performed";

    private static final String RESET_RECORD_FILE_PATH =
            "/data/server_configurable_flags/reset_flags";

    private static final String SYSTEM_PROPERTY_VALID_CHARACTERS_REGEX = "^[\\w\\.\\-@:]*$";

    private static final String SYSTEM_PROPERTY_INVALID_SUBSTRING = "..";

    private static final int SYSTEM_PROPERTY_MAX_LENGTH = 92;

    // experiment flags added to Global.Settings(before new "Config" provider table is available)
    // will be added under this category.
    private static final String GLOBAL_SETTINGS_CATEGORY = "global_settings";

    // Add the global setting you want to push to native level as experiment flag into this list.
    //
    // NOTE: please grant write permission system property prefix
    // with format persist.device_config.global_settings.[flag_name] in system_server.te and grant
    // read permission in the corresponding .te file your feature belongs to.
    @VisibleForTesting
    static final String[] sGlobalSettings = new String[] {
            Settings.Global.NATIVE_FLAGS_HEALTH_CHECK_ENABLED,
    };

    // TODO(b/282593625): Move this constant to DeviceConfig module
    private static final String NAMESPACE_TETHERING_U_OR_LATER_NATIVE =
            "tethering_u_or_later_native";

    // All the flags under the listed DeviceConfig scopes will be synced to native level.
    //
    // NOTE: please grant write permission system property prefix
    // with format persist.device_config.[device_config_scope]. in system_server.te and grant read
    // permission in the corresponding .te file your feature belongs to.
    @VisibleForTesting
    static final String[] sDeviceConfigScopes = new String[] {
        DeviceConfig.NAMESPACE_ACTIVITY_MANAGER_NATIVE_BOOT,
        DeviceConfig.NAMESPACE_CAMERA_NATIVE,
        DeviceConfig.NAMESPACE_CONFIGURATION,
        DeviceConfig.NAMESPACE_CONNECTIVITY,
        DeviceConfig.NAMESPACE_EDGETPU_NATIVE,
        DeviceConfig.NAMESPACE_INPUT_NATIVE_BOOT,
        DeviceConfig.NAMESPACE_INTELLIGENCE_CONTENT_SUGGESTIONS,
        DeviceConfig.NAMESPACE_LMKD_NATIVE,
        DeviceConfig.NAMESPACE_MEDIA_NATIVE,
        DeviceConfig.NAMESPACE_MGLRU_NATIVE,
        DeviceConfig.NAMESPACE_MMD_NATIVE,
        DeviceConfig.NAMESPACE_NETD_NATIVE,
        DeviceConfig.NAMESPACE_NNAPI_NATIVE,
        DeviceConfig.NAMESPACE_PROFCOLLECT_NATIVE_BOOT,
        DeviceConfig.NAMESPACE_REMOTE_KEY_PROVISIONING_NATIVE,
        DeviceConfig.NAMESPACE_RUNTIME_NATIVE,
        DeviceConfig.NAMESPACE_RUNTIME_NATIVE_BOOT,
        DeviceConfig.NAMESPACE_STATSD_NATIVE,
        DeviceConfig.NAMESPACE_STATSD_NATIVE_BOOT,
        DeviceConfig.NAMESPACE_STORAGE_NATIVE_BOOT,
        DeviceConfig.NAMESPACE_SURFACE_FLINGER_NATIVE_BOOT,
        DeviceConfig.NAMESPACE_SWCODEC_NATIVE,
        DeviceConfig.NAMESPACE_VENDOR_SYSTEM_NATIVE,
        DeviceConfig.NAMESPACE_VENDOR_SYSTEM_NATIVE_BOOT,
        DeviceConfig.NAMESPACE_VIRTUALIZATION_FRAMEWORK_NATIVE,
        DeviceConfig.NAMESPACE_WINDOW_MANAGER_NATIVE_BOOT,
        DeviceConfig.NAMESPACE_MEMORY_SAFETY_NATIVE_BOOT,
        DeviceConfig.NAMESPACE_MEMORY_SAFETY_NATIVE,
        DeviceConfig.NAMESPACE_HDMI_CONTROL,
        NAMESPACE_TETHERING_U_OR_LATER_NATIVE
    };

    // All the aconfig flags under the listed DeviceConfig scopes will be synced to native level.
    // The list is sorted.
    @VisibleForTesting
    static final String[] sDeviceConfigAconfigScopes = new String[] {
        "tv_os",
        "aaos_carframework_triage",
        "aaos_performance_triage",
        "aaos_input_triage",
        "aaos_user_triage",
        "aaos_window_triage",
        "aaos_audio_triage",
        "aaos_power_triage",
        "aaos_storage_triage",
        "aaos_sdv",
        "aaos_vac_triage",
        "accessibility",
        "android_core_networking",
        "android_health_services",
        "android_sdk",
        "android_kernel",
        "aoc",
        "app_widgets",
        "arc_next",
        "art_cloud",
        "art_mainline",
        "art_performance",
        "attack_tools",
        "automotive_cast",
        "avic",
        "desktop_firmware",
        "biometrics",
        "biometrics_framework",
        "biometrics_integration",
        "bluetooth",
        "brownout_mitigation_audio",
        "brownout_mitigation_modem",
        "build",
        "camera_hal",
        "camera_platform",
        "car_framework",
        "car_perception",
        "car_security",
        "car_telemetry",
        "codec_fwk",
        "companion",
        "com_android_adbd",
        "content_protection",
        "context_hub",
        "core_experiments_team_internal",
        "core_graphics",
        "core_libraries",
        "crumpet",
        "dck_framework",
        "desktop_apps",
        "desktop_audio",
        "desktop_better_together",
        "desktop_bsp",
        "desktop_camera",
        "desktop_connectivity",
        "desktop_dev_experience",
        "desktop_display",
        "desktop_commercial",
        "desktop_firmware",
        "desktop_graphics",
        "desktop_hwsec",
        "desktop_input",
        "desktop_kernel",
        "desktop_ml",
        "desktop_networking",
        "desktop_serviceability",
        "desktop_oobe",
        "desktop_peripherals",
        "desktop_personalization",
        "desktop_pnp",
        "desktop_privacy",
        "desktop_release",
        "desktop_security",
        "desktop_stats",
        "desktop_sysui",
        "desktop_users_and_accounts",
        "desktop_video",
        "desktop_wifi",
        "devoptions_settings",
        "game",
        "gpu",
        "haptics",
        "hardware_backed_security_mainline",
        "input",
        "incremental",
        "llvm_and_toolchains",
        "lse_desktop_experience",
        "machine_learning",
        "mainline_modularization",
        "mainline_sdk",
        "make_pixel_haptics",
        "media_audio",
        "media_drm",
        "media_projection",
        "media_reliability",
        "media_solutions",
        "media_tv",
        "microxr",
        "nearby",
        "nfc",
        "pdf_viewer",
        "perfetto",
        "pixel_audio_android",
        "pixel_biometrics_face",
        "pixel_bluetooth",
        "pixel_connectivity_gps",
        "pixel_continuity",
        "pixel_display",
        "pixel_fingerprint",
        "pixel_gsc",
        "pixel_perf",
        "pixel_sensai",
        "pixel_sensors",
        "pixel_state_server",
        "pixel_system_sw_video",
        "pixel_video_sw",
        "pixel_vpn",
        "pixel_watch",
        "pixel_watch_debug_trace",
        "pixel_watch_watchfaces",
        "pixel_wifi",
        "platform_compat",
        "platform_security",
        "pmw",
        "power",
        "preload_safety",
        "printing",
        "privacy_infra_policy",
        "psap_ai",
        "ravenwood",
        "resource_manager",
        "responsible_apis",
        "rust",
        "safety_center",
        "sensors",
        "spoon",
        "stability",
        "statsd",
        "system_performance",
        "system_sw_battery",
        "system_sw_touch",
        "system_sw_usb",
        "test_suites",
        "text",
        "threadnetwork",
        "treble",
        "tv_os_media",
        "tv_system_ui",
        "usb",
        "vibrator",
        "virtual_devices",
        "virtualization",
        "wallet_integration",
        "wear_calling_messaging",
        "wear_connectivity",
        "wear_esim_carriers",
        "wear_frameworks",
        "wear_media",
        "wear_offload",
        "wear_security",
        "wear_system_health",
        "wear_systems",
        "wear_sysui",
        "wear_system_managed_surfaces",
        "wear_watchfaces",
        "web_apps_on_chromeos_and_android",
        "window_surfaces",
        "windowing_frontend",
        "xr",
    };

    public static final String NAMESPACE_REBOOT_STAGING = "staged";
    public static final String NAMESPACE_REBOOT_STAGING_DELIMITER = "*";

    public static final String NAMESPACE_LOCAL_OVERRIDES = "device_config_overrides";

    private final String[] mGlobalSettings;

    private final String[] mDeviceConfigScopes;

    private final ContentResolver mContentResolver;

    @VisibleForTesting
    protected SettingsToPropertiesMapper(ContentResolver contentResolver,
            String[] globalSettings,
            String[] deviceConfigScopes,
            String[] deviceConfigAconfigScopes) {
        mContentResolver = contentResolver;
        mGlobalSettings = globalSettings;
        mDeviceConfigScopes = deviceConfigScopes;
    }

    @VisibleForTesting
    void updatePropertiesFromSettings() {
        for (String globalSetting : mGlobalSettings) {
            Uri settingUri = Settings.Global.getUriFor(globalSetting);
            String propName = makePropertyName(GLOBAL_SETTINGS_CATEGORY, globalSetting);
            if (settingUri == null) {
                logErr("setting uri is null for globalSetting " + globalSetting);
                continue;
            }
            if (propName == null) {
                logErr("invalid prop name for globalSetting " + globalSetting);
                continue;
            }

            ContentObserver co = new ContentObserver(null) {
                @Override
                public void onChange(boolean selfChange) {
                    updatePropertyFromSetting(globalSetting, propName);
                }
            };

            // only updating on starting up when no native flags reset is performed during current
            // booting.
            if (!isNativeFlagsResetPerformed()) {
                updatePropertyFromSetting(globalSetting, propName);
            }
            mContentResolver.registerContentObserver(settingUri, false, co);
        }

        for (String deviceConfigScope : mDeviceConfigScopes) {
            DeviceConfig.addOnPropertiesChangedListener(
                    deviceConfigScope,
                    AsyncTask.THREAD_POOL_EXECUTOR,
                    (DeviceConfig.Properties properties) -> {
                        String scope = properties.getNamespace();
                        for (String key : properties.getKeyset()) {
                            String propertyName = makePropertyName(scope, key);
                            if (propertyName == null) {
                                logErr("unable to construct system property for " + scope + "/"
                                        + key);
                                return;
                            }
                            setProperty(propertyName, properties.getString(key, null));
                        }
                    });
        }

        // add sys prop sync callback for staged flag values
        DeviceConfig.addOnPropertiesChangedListener(
            NAMESPACE_REBOOT_STAGING,
            newSingleThreadScheduledExecutor(),
            (DeviceConfig.Properties properties) -> {

              // send prop stage request to new storage
              if (enableAconfigStorageDaemon()) {
                  stageFlagsInNewStorage(properties);
                  return;
              }
        });

        // add prop sync callback for flag local overrides
        DeviceConfig.addOnPropertiesChangedListener(
            NAMESPACE_LOCAL_OVERRIDES,
            AsyncTask.THREAD_POOL_EXECUTOR,
            (DeviceConfig.Properties properties) -> {
                if (enableAconfigStorageDaemon()) {
                    setLocalOverridesInNewStorage(properties);
                    return;
                }
        });
    }

    /**
     * apply flag local override in aconfig new storage
     * @param requests: request proto output stream
     * @return aconfigd socket return as proto input stream
     */
    static void sendAconfigdRequests(ProtoOutputStream requests) {
        ProtoInputStream returns = sendAconfigdRequests("aconfigd_system", requests);
        try {
          parseAndLogAconfigdReturn(returns);
        } catch (IOException ioe) {
          logErr("failed to parse aconfigd return", ioe);
        }
        if (enableAconfigdFromMainline()) {
            returns = sendAconfigdRequests("aconfigd_mainline", requests);
            try {
              parseAndLogAconfigdReturn(returns);
            } catch (IOException ioe) {
              logErr("failed to parse aconfigd return", ioe);
            }
        }
    }

    /**
     * apply flag local override in aconfig new storage
     * @param socketName: the socket to send to
     * @param requests: request proto output stream
     * @return aconfigd socket return as proto input stream
     */
    static ProtoInputStream sendAconfigdRequests(String socketName, ProtoOutputStream requests) {
        // connect to aconfigd socket
        LocalSocket client = new LocalSocket();
        try {
            client.connect(new LocalSocketAddress(
                socketName, LocalSocketAddress.Namespace.RESERVED));
            Slog.d(TAG, "connected to " + socketName + " socket");
        } catch (IOException ioe) {
            logErr("failed to connect to " + socketName + " socket", ioe);
            return null;
        }

        DataInputStream inputStream = null;
        DataOutputStream outputStream = null;
        try {
            inputStream = new DataInputStream(client.getInputStream());
            outputStream = new DataOutputStream(client.getOutputStream());
        } catch (IOException ioe) {
            logErr("failed to get local socket iostreams", ioe);
            return null;
        }

        // send requests
        try {
            byte[] requests_bytes = requests.getBytes();
            outputStream.writeInt(requests_bytes.length);
            outputStream.write(requests_bytes, 0, requests_bytes.length);
            Slog.d(TAG, "flag override requests sent to " + socketName);
        } catch (IOException ioe) {
            logErr("failed to send requests to " + socketName, ioe);
            return null;
        }

        // read return
        try {
            int num_bytes = inputStream.readInt();
            ProtoInputStream returns = new ProtoInputStream(inputStream);
            Slog.d(TAG, "received " + num_bytes + " bytes back from " + socketName);
            return returns;
        } catch (IOException ioe) {
            logErr("failed to read requests return from " + socketName, ioe);
            return null;
        }
    }

    /**
     * serialize a flag override request
     * @param proto
     */
    static void writeFlagOverrideRequest(
        ProtoOutputStream proto, String packageName, String flagName, String flagValue,
        boolean isLocal) {
      int localOverrideTag = supportImmediateLocalOverrides()
          ? StorageRequestMessage.LOCAL_IMMEDIATE
          : StorageRequestMessage.LOCAL_ON_REBOOT;

      long msgsToken = proto.start(StorageRequestMessages.MSGS);
      long msgToken = proto.start(StorageRequestMessage.FLAG_OVERRIDE_MESSAGE);
      proto.write(StorageRequestMessage.FlagOverrideMessage.PACKAGE_NAME, packageName);
      proto.write(StorageRequestMessage.FlagOverrideMessage.FLAG_NAME, flagName);
      proto.write(StorageRequestMessage.FlagOverrideMessage.FLAG_VALUE, flagValue);
      proto.write(StorageRequestMessage.FlagOverrideMessage.OVERRIDE_TYPE, isLocal
            ? localOverrideTag
            : StorageRequestMessage.SERVER_ON_REBOOT);
      proto.end(msgToken);
      proto.end(msgsToken);
    }

    /**
     * Send a request to aconfig storage to remove a flag local override.
     *
     * @param proto
     * @param packageName the package of the flag
     * @param flagName the name of the flag
     * @param immediate if true, clear immediately; otherwise, clear on next reboot
     */
    @VisibleForTesting
    public static void writeFlagOverrideRemovalRequest(
        ProtoOutputStream proto, String packageName, String flagName, boolean immediate) {
      long msgsToken = proto.start(StorageRequestMessages.MSGS);
      long msgToken = proto.start(StorageRequestMessage.REMOVE_LOCAL_OVERRIDE_MESSAGE);
      proto.write(StorageRequestMessage.RemoveLocalOverrideMessage.PACKAGE_NAME, packageName);
      proto.write(StorageRequestMessage.RemoveLocalOverrideMessage.FLAG_NAME, flagName);
      proto.write(StorageRequestMessage.RemoveLocalOverrideMessage.REMOVE_ALL, false);
      proto.write(StorageRequestMessage.RemoveLocalOverrideMessage.REMOVE_OVERRIDE_TYPE,
          immediate
              ? StorageRequestMessage.REMOVE_LOCAL_IMMEDIATE
              : StorageRequestMessage.REMOVE_LOCAL_ON_REBOOT);
      proto.end(msgToken);
      proto.end(msgsToken);
    }

    /**
     * deserialize a flag input proto stream and log
     * @param proto
     */
    static void parseAndLogAconfigdReturn(ProtoInputStream proto) throws IOException {
        while (true) {
          switch (proto.nextField()) {
            case (int) StorageReturnMessages.MSGS:
              long msgsToken = proto.start(StorageReturnMessages.MSGS);
              switch (proto.nextField()) {
                case (int) StorageReturnMessage.FLAG_OVERRIDE_MESSAGE:
                  Slog.d(TAG, "successfully handled override requests");
                  long msgToken = proto.start(StorageReturnMessage.FLAG_OVERRIDE_MESSAGE);
                  proto.end(msgToken);
                  break;
                case (int) StorageReturnMessage.ERROR_MESSAGE:
                  String errmsg = proto.readString(StorageReturnMessage.ERROR_MESSAGE);
                  Slog.d(TAG, "override request failed: " + errmsg);
                  break;
                case ProtoInputStream.NO_MORE_FIELDS:
                  break;
                default:
                  logErr("invalid message type, expecting only flag override return or error message");
                  break;
              }
              proto.end(msgsToken);
              break;
            case ProtoInputStream.NO_MORE_FIELDS:
              return;
            default:
              logErr("invalid message type, expect storage return message");
              break;
          }
        }
    }

    /**
     * apply flag local override in aconfig new storage
     * @param props
     */
    @VisibleForTesting
    public static void setLocalOverridesInNewStorage(DeviceConfig.Properties props) {
        int num_requests = 0;
        ProtoOutputStream requests = new ProtoOutputStream();
        for (String flagName : props.getKeyset()) {
            String flagValue = props.getString(flagName, null);

            if (Flags.syncLocalOverridesRemovalNewStorage()) {
                if (flagName == null) {
                    continue;
                }
            } else {
                if (flagName == null || flagValue == null) {
                    continue;
                }
            }

            int idx = flagName.indexOf(":");
            if (idx == -1 || idx == flagName.length() - 1 || idx == 0) {
                logErr("invalid local flag override: " + flagName);
                continue;
            }
            String actualNamespace = flagName.substring(0, idx);
            String fullFlagName = flagName.substring(idx+1);
            idx = fullFlagName.lastIndexOf(".");
            if (idx == -1) {
              logErr("invalid flag name: " + fullFlagName);
              continue;
            }
            String packageName = fullFlagName.substring(0, idx);
            String realFlagName = fullFlagName.substring(idx+1);

            if (Flags.syncLocalOverridesRemovalNewStorage() && flagValue == null) {
              if (supportClearLocalOverridesImmediately()) {
                writeFlagOverrideRemovalRequest(requests, packageName, realFlagName, true);
              } else {
                writeFlagOverrideRemovalRequest(requests, packageName, realFlagName, false);
              }
            } else {
              writeFlagOverrideRequest(requests, packageName, realFlagName, flagValue, true);
            }

            ++num_requests;
        }

        if (num_requests == 0) {
          return;
        }

        // send requests to aconfigd
        sendAconfigdRequests(requests);
    }

    public static SettingsToPropertiesMapper start(ContentResolver contentResolver) {
        SettingsToPropertiesMapper mapper =  new SettingsToPropertiesMapper(
                contentResolver,
                sGlobalSettings,
                sDeviceConfigScopes,
                sDeviceConfigAconfigScopes);
        mapper.updatePropertiesFromSettings();
        return mapper;
    }

    /**
     * If native level flags reset has been performed as an attempt to recover from a crash loop
     * during current device booting.
     * @return
     */
    public static boolean isNativeFlagsResetPerformed() {
        String value = SystemProperties.get(RESET_PERFORMED_PROPERTY);
        return "true".equals(value);
    }

    /**
     * return an array of native flag categories under which flags got reset during current device
     * booting.
     * @return
     */
    public static @NonNull String[] getResetNativeCategories() {
        if (!isNativeFlagsResetPerformed()) {
            return new String[0];
        }

        String content = getResetFlagsFileContent();
        if (TextUtils.isEmpty(content)) {
            return new String[0];
        }

        String[] property_names = content.split(";");
        HashSet<String> categories = new HashSet<>();
        for (String property_name : property_names) {
            String[] segments = property_name.split("\\.");
            if (segments.length < 3) {
                logErr("failed to extract category name from property " + property_name);
                continue;
            }
            categories.add(segments[2]);
        }
        return categories.toArray(new String[0]);
    }

    /**
     * system property name constructing rule: "persist.device_config.[category_name].[flag_name]".
     * If the name contains invalid characters or substrings for system property name,
     * will return null.
     * @param categoryName
     * @param flagName
     * @return
     */
    @VisibleForTesting
    static String makePropertyName(String categoryName, String flagName) {
        String propertyName = SYSTEM_PROPERTY_PREFIX + categoryName + "." + flagName;

        if (!propertyName.matches(SYSTEM_PROPERTY_VALID_CHARACTERS_REGEX)
                || propertyName.contains(SYSTEM_PROPERTY_INVALID_SUBSTRING)) {
            return null;
        }

        return propertyName;
    }


    /**
     * stage flags in aconfig new storage
     * @param propsToStage
     */
    @VisibleForTesting
    static void stageFlagsInNewStorage(DeviceConfig.Properties props) {
        // write aconfigd requests proto to proto output stream
        int num_requests = 0;
        ProtoOutputStream requests = new ProtoOutputStream();
        for (String flagName : props.getKeyset()) {
            String flagValue = props.getString(flagName, null);
            if (flagName == null || flagValue == null) {
                continue;
            }

            int idx = flagName.indexOf("*");
            if (idx == -1 || idx == flagName.length() - 1 || idx == 0) {
                logErr("invalid local flag override: " + flagName);
                continue;
            }
            String actualNamespace = flagName.substring(0, idx);
            String fullFlagName = flagName.substring(idx+1);

            idx = fullFlagName.lastIndexOf(".");
            if (idx == -1) {
                logErr("invalid flag name: " + fullFlagName);
                continue;
            }
            String packageName = fullFlagName.substring(0, idx);
            String realFlagName = fullFlagName.substring(idx+1);
            writeFlagOverrideRequest(requests, packageName, realFlagName, flagValue, false);
            ++num_requests;
        }

        if (num_requests == 0) {
          return;
        }

        // send requests to aconfigd
        sendAconfigdRequests(requests);
    }

    private void setProperty(String key, String value) {
        // Check if need to clear the property
        if (value == null) {
            // It's impossible to remove system property, therefore we check previous value to
            // avoid setting an empty string if the property wasn't set.
            if (TextUtils.isEmpty(SystemProperties.get(key))) {
                return;
            }
            value = "";
        } else if (value.length() > SYSTEM_PROPERTY_MAX_LENGTH) {
            logErr("key=" + key + " value=" + value + " exceeds system property max length.");
            return;
        }

        try {
            SystemProperties.set(key, value);
        } catch (Exception e) {
            // Failure to set a property can be caused by SELinux denial. This usually indicates
            // that the property wasn't allowlisted in sepolicy.
            // No need to report it on all user devices, only on debug builds.
            logErr("Unable to set property " + key + " value '" + value + "'", e);
        }
    }

    private static void logErr(String msg, Exception e) {
        if (Build.IS_DEBUGGABLE) {
            Slog.wtf(TAG, msg, e);
        } else {
            Slog.e(TAG, msg, e);
        }
    }

    private static void logErr(String msg) {
        if (Build.IS_DEBUGGABLE) {
            Slog.wtf(TAG, msg);
        } else {
            Slog.e(TAG, msg);
        }
    }

    @VisibleForTesting
    static String getResetFlagsFileContent() {
        String content = null;
        try {
            File reset_flag_file = new File(RESET_RECORD_FILE_PATH);
            BufferedReader br = new BufferedReader(new FileReader(reset_flag_file));
            content = br.readLine();

            br.close();
        } catch (IOException ioe) {
            logErr("failed to read file " + RESET_RECORD_FILE_PATH, ioe);
        }
        return content;
    }

    @VisibleForTesting
    void updatePropertyFromSetting(String settingName, String propName) {
        String settingValue = Settings.Global.getString(mContentResolver, settingName);
        setProperty(propName, settingValue);
    }
}
