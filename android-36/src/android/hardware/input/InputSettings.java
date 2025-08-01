/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.hardware.input;

import static com.android.hardware.input.Flags.FLAG_KEYBOARD_A11Y_MOUSE_KEYS;
import static com.android.hardware.input.Flags.enableCustomizableInputGestures;
import static com.android.hardware.input.Flags.keyboardA11yMouseKeys;
import static com.android.hardware.input.Flags.mouseScrollingAcceleration;
import static com.android.hardware.input.Flags.mouseReverseVerticalScrolling;
import static com.android.hardware.input.Flags.mouseSwapPrimaryButton;
import static com.android.hardware.input.Flags.pointerAcceleration;
import static com.android.hardware.input.Flags.touchpadSystemGestureDisable;
import static com.android.hardware.input.Flags.touchpadThreeFingerTapShortcut;
import static com.android.hardware.input.Flags.touchpadVisualizer;
import static com.android.hardware.input.Flags.useKeyGestureEventHandler;
import static com.android.hardware.input.Flags.useKeyGestureEventHandlerMultiKeyGestures;
import static com.android.input.flags.Flags.FLAG_KEYBOARD_REPEAT_KEYS;
import static com.android.input.flags.Flags.keyboardRepeatKeys;

import android.Manifest;
import android.annotation.FlaggedApi;
import android.annotation.FloatRange;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SuppressLint;
import android.annotation.TestApi;
import android.app.AppGlobals;
import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;
import android.sysprop.InputProperties;
import android.view.ViewConfiguration;

/**
 * InputSettings encapsulates reading and writing settings related to input
 *
 * @hide
 */
@TestApi
public class InputSettings {
    /**
     * Pointer Speed: The minimum (slowest) pointer speed (-7).
     * @hide
     */
    public static final int MIN_POINTER_SPEED = -7;

    /**
     * Pointer Speed: The maximum (fastest) pointer speed (7).
     * @hide
     */
    public static final int MAX_POINTER_SPEED = 7;

    /**
     * Pointer Speed: The default pointer speed (0).
     */
    @SuppressLint("UnflaggedApi") // TestApi without associated feature.
    public static final int DEFAULT_POINTER_SPEED = 0;

    /**
     * Pointer Speed: The minimum (slowest) mouse scrolling speed (-7).
     * @hide
     */
    public static final int MIN_MOUSE_SCROLLING_SPEED = -7;

    /**
     * Pointer Speed: The maximum (fastest) mouse scrolling speed (7).
     * @hide
     */
    public static final int MAX_MOUSE_SCROLLING_SPEED = 7;

    /**
     * Pointer Speed: The default mouse scrolling speed (0).
     * @hide
     */
    public static final int DEFAULT_MOUSE_SCROLLING_SPEED = 0;

    /**
     * Bounce Keys Threshold: The default value of the threshold (500 ms).
     *
     * @hide
     */
    public static final int DEFAULT_BOUNCE_KEYS_THRESHOLD_MILLIS = 500;

    /**
     * Slow Keys Threshold: The default value of the threshold (500 ms).
     *
     * @hide
     */
    public static final int DEFAULT_SLOW_KEYS_THRESHOLD_MILLIS = 500;

    /**
     * The maximum allowed obscuring opacity by UID to propagate touches (0 <= x <= 1).
     * @hide
     */
    public static final float DEFAULT_MAXIMUM_OBSCURING_OPACITY_FOR_TOUCH = .8f;

    /**
     * The maximum allowed Accessibility bounce keys threshold.
     * @hide
     */
    public static final int MAX_ACCESSIBILITY_BOUNCE_KEYS_THRESHOLD_MILLIS = 5000;

    /**
     * The maximum allowed Accessibility slow keys threshold.
     * @hide
     */
    public static final int MAX_ACCESSIBILITY_SLOW_KEYS_THRESHOLD_MILLIS = 5000;

    /**
     * Default value for {@link Settings.Secure#STYLUS_POINTER_ICON_ENABLED}.
     * @hide
     */
    public static final int DEFAULT_STYLUS_POINTER_ICON_ENABLED = 1;

    /**
     * The minimum allowed repeat keys timeout before starting key repeats.
     * @hide
     */
    public static final int MIN_KEY_REPEAT_TIMEOUT_MILLIS = 150;

    /**
     * The maximum allowed repeat keys timeout before starting key repeats.
     * @hide
     */
    public static final int MAX_KEY_REPEAT_TIMEOUT_MILLIS = 2000;

    /**
     * The minimum allowed repeat keys delay between successive key repeats.
     * @hide
     */
    public static final int MIN_KEY_REPEAT_DELAY_MILLIS = 20;

    /**
     * The maximum allowed repeat keys delay between successive key repeats.
     * @hide
     */
    public static final int MAX_KEY_REPEAT_DELAY_MILLIS = 2000;

    private InputSettings() {
    }

    /**
     * Gets the mouse pointer speed.
     * <p>
     * Only returns the permanent mouse pointer speed.  Ignores any temporary pointer
     * speed set by {@link InputManager#tryPointerSpeed}.
     * </p>
     *
     * @param context The application context.
     * @return The pointer speed as a value between {@link #MIN_POINTER_SPEED} and
     * {@link #MAX_POINTER_SPEED}, or the default value {@link #DEFAULT_POINTER_SPEED}.
     *
     * @hide
     */
    @SuppressLint("NonUserGetterCalled")
    public static int getPointerSpeed(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.POINTER_SPEED, DEFAULT_POINTER_SPEED);
    }

    /**
     * Sets the mouse pointer speed.
     * <p>
     * Requires {@link android.Manifest.permission#WRITE_SETTINGS}.
     * </p>
     *
     * @param context The application context.
     * @param speed The pointer speed as a value between {@link #MIN_POINTER_SPEED} and
     * {@link #MAX_POINTER_SPEED}, or the default value {@link #DEFAULT_POINTER_SPEED}.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setPointerSpeed(Context context, int speed) {
        if (speed < MIN_POINTER_SPEED || speed > MAX_POINTER_SPEED) {
            throw new IllegalArgumentException("speed out of range");
        }

        Settings.System.putInt(context.getContentResolver(),
                Settings.System.POINTER_SPEED, speed);
    }

    /**
     * Returns the maximum allowed obscuring opacity per UID to propagate touches.
     *
     * <p>For certain window types (e.g. {@link LayoutParams#TYPE_APPLICATION_OVERLAY}),
     * the decision of honoring {@link LayoutParams#FLAG_NOT_TOUCHABLE} or not depends on
     * the combined obscuring opacity of the windows above the touch-consuming window, per
     * UID. Check documentation of {@link LayoutParams#FLAG_NOT_TOUCHABLE} for more details.
     *
     * <p>The value returned is between 0 (inclusive) and 1 (inclusive).
     *
     * @see LayoutParams#FLAG_NOT_TOUCHABLE
     *
     * @hide
     */
    @FloatRange(from = 0, to = 1)
    public static float getMaximumObscuringOpacityForTouch(Context context) {
        return Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.MAXIMUM_OBSCURING_OPACITY_FOR_TOUCH,
                DEFAULT_MAXIMUM_OBSCURING_OPACITY_FOR_TOUCH);
    }

    /**
     * Sets the maximum allowed obscuring opacity by UID to propagate touches.
     *
     * <p>For certain window types (e.g. SAWs), the decision of honoring {@link LayoutParams
     * #FLAG_NOT_TOUCHABLE} or not depends on the combined obscuring opacity of the windows
     * above the touch-consuming window.
     *
     * <p>For a certain UID:
     * <ul>
     *     <li>If it's the same as the UID of the touch-consuming window, allow it to propagate
     *     the touch.
     *     <li>Otherwise take all its windows of eligible window types above the touch-consuming
     *     window, compute their combined obscuring opacity considering that {@code
     *     opacity(A, B) = 1 - (1 - opacity(A))*(1 - opacity(B))}. If the computed value is
     *     less than or equal to this setting and there are no other windows preventing the
     *     touch, allow the UID to propagate the touch.
     * </ul>
     *
     * <p>This value should be between 0 (inclusive) and 1 (inclusive).
     *
     * @see #getMaximumObscuringOpacityForTouch(Context)
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
    public static void setMaximumObscuringOpacityForTouch(
            @NonNull Context context,
            @FloatRange(from = 0, to = 1) float opacity) {
        if (opacity < 0 || opacity > 1) {
            throw new IllegalArgumentException(
                    "Maximum obscuring opacity for touch should be >= 0 and <= 1");
        }
        Settings.Global.putFloat(context.getContentResolver(),
                Settings.Global.MAXIMUM_OBSCURING_OPACITY_FOR_TOUCH, opacity);
    }

    /**
     * Whether stylus has ever been used on device (false by default).
     * @hide
     */
    public static boolean isStylusEverUsed(@NonNull Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                        Settings.Global.STYLUS_EVER_USED, 0) == 1;
    }

    /**
     * Set whether stylus has ever been used on device.
     * Should only ever be set to true once after stylus first usage.
     * @hide
     */
    @RequiresPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
    public static void setStylusEverUsed(@NonNull Context context, boolean stylusEverUsed) {
        Settings.Global.putInt(context.getContentResolver(),
                Settings.Global.STYLUS_EVER_USED, stylusEverUsed ? 1 : 0);
    }


    /**
     * Gets the touchpad pointer speed.
     *
     * The returned value only applies to gesture-compatible touchpads.
     *
     * @param context The application context.
     * @return The pointer speed as a value between {@link #MIN_POINTER_SPEED} and
     * {@link #MAX_POINTER_SPEED}, or the default value {@link #DEFAULT_POINTER_SPEED}.
     *
     * @hide
     */
    public static int getTouchpadPointerSpeed(@NonNull Context context) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.TOUCHPAD_POINTER_SPEED, DEFAULT_POINTER_SPEED,
                UserHandle.USER_CURRENT);
    }

    /**
     * Sets the touchpad pointer speed, and saves it in the settings.
     *
     * The new speed will only apply to gesture-compatible touchpads.
     *
     * @param context The application context.
     * @param speed The pointer speed as a value between {@link #MIN_POINTER_SPEED} and
     * {@link #MAX_POINTER_SPEED}, or the default value {@link #DEFAULT_POINTER_SPEED}.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setTouchpadPointerSpeed(@NonNull Context context, int speed) {
        if (speed < MIN_POINTER_SPEED || speed > MAX_POINTER_SPEED) {
            throw new IllegalArgumentException("speed out of range");
        }

        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.TOUCHPAD_POINTER_SPEED, speed, UserHandle.USER_CURRENT);
    }

    /**
     * Returns true if moving two fingers upwards on the touchpad should
     * scroll down, which is known as natural scrolling.
     *
     * The returned value only applies to gesture-compatible touchpads.
     *
     * @param context The application context.
     * @return Whether the touchpad should use natural scrolling.
     *
     * @hide
     */
    public static boolean useTouchpadNaturalScrolling(@NonNull Context context) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.TOUCHPAD_NATURAL_SCROLLING, 1, UserHandle.USER_CURRENT) == 1;
    }

    /**
     * Sets the natural scroll behavior for the touchpad.
     *
     * If natural scrolling is enabled, moving two fingers upwards on the
     * touchpad will scroll down.
     *
     * @param context The application context.
     * @param enabled Will enable natural scroll if true, disable it if false
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setTouchpadNaturalScrolling(@NonNull Context context, boolean enabled) {
        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.TOUCHPAD_NATURAL_SCROLLING, enabled ? 1 : 0,
                UserHandle.USER_CURRENT);
    }

    /**
     * Returns true if the touchpad should use tap to click.
     *
     * The returned value only applies to gesture-compatible touchpads.
     *
     * @param context The application context.
     * @return Whether the touchpad should use tap to click.
     *
     * @hide
     */
    public static boolean useTouchpadTapToClick(@NonNull Context context) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.TOUCHPAD_TAP_TO_CLICK, 1, UserHandle.USER_CURRENT) == 1;
    }

    /**
     * Sets the tap to click behavior for the touchpad.
     *
     * The new behavior is only applied to gesture-compatible touchpads.
     *
     * @param context The application context.
     * @param enabled Will enable tap to click if true, disable it if false
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setTouchpadTapToClick(@NonNull Context context, boolean enabled) {
        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.TOUCHPAD_TAP_TO_CLICK, enabled ? 1 : 0,
                UserHandle.USER_CURRENT);
    }

    /**
     * Whether touchpad acceleration is enabled or not.
     *
     * @param context The application context.
     *
     * @hide
     */
    public static boolean isTouchpadAccelerationEnabled(@NonNull Context context) {
        if (!isPointerAccelerationFeatureFlagEnabled()) {
            return true;
        }

        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.TOUCHPAD_ACCELERATION_ENABLED, 1, UserHandle.USER_CURRENT)
                == 1;
    }

   /**
    * Enables or disables touchpad acceleration.
    *
    * @param context The application context.
    * @param enabled Will enable touchpad acceleration if true, disable it if
    *                false.
    * @hide
    */
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setTouchpadAccelerationEnabled(@NonNull Context context,
            boolean enabled) {
        if (!isPointerAccelerationFeatureFlagEnabled()) {
            return;
        }
        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.TOUCHPAD_ACCELERATION_ENABLED, enabled ? 1 : 0,
                UserHandle.USER_CURRENT);
    }

    /**
     * Returns true if the feature flag for disabling system gestures on touchpads is enabled.
     *
     * @hide
     */
    public static boolean isTouchpadSystemGestureDisableFeatureFlagEnabled() {
        return touchpadSystemGestureDisable();
    }

    /**
     * Returns true if the feature flag for touchpad visualizer is enabled.
     *
     * @hide
     */
    public static boolean isTouchpadVisualizerFeatureFlagEnabled() {
        return touchpadVisualizer();
    }

    /**
     * Returns true if the feature flag for the touchpad three-finger tap shortcut is enabled.
     *
     * @hide
     */
    public static boolean isTouchpadThreeFingerTapShortcutFeatureFlagEnabled() {
        return isCustomizableInputGesturesFeatureFlagEnabled() && touchpadThreeFingerTapShortcut();
    }

    /**
     * Returns true if the feature flag for toggling the mouse scrolling acceleration is enabled.
     *
     * @hide
     */
    public static boolean isMouseScrollingAccelerationFeatureFlagEnabled() {
        return mouseScrollingAcceleration();
    }

    /**
     * Returns true if the feature flag for mouse reverse vertical scrolling is enabled.
     * @hide
     */
    public static boolean isMouseReverseVerticalScrollingFeatureFlagEnabled() {
        return mouseReverseVerticalScrolling();
    }

    /**
     * Returns true if the feature flag for mouse swap primary button is enabled.
     * @hide
     */
    public static boolean isMouseSwapPrimaryButtonFeatureFlagEnabled() {
        return mouseSwapPrimaryButton();
    }

    /**
     * Returns true if the feature flag for the pointer acceleration toggle is
     * enabled.
     * @hide
     */
    public static boolean isPointerAccelerationFeatureFlagEnabled() {
        return pointerAcceleration();
    }

    /**
     * Returns true if the touchpad visualizer is allowed to appear.
     *
     * @param context The application context.
     * @return Whether it is allowed to show touchpad visualizer or not.
     *
     * @hide
     */
    public static boolean useTouchpadVisualizer(@NonNull Context context) {
        if (!isTouchpadVisualizerFeatureFlagEnabled()) {
            return false;
        }
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.TOUCHPAD_VISUALIZER, 0, UserHandle.USER_CURRENT) == 1;
    }

    /**
     * Sets the touchpad visualizer behaviour.
     *
     * @param context The application context.
     * @param enabled Will enable touchpad visualizer if true, disable it if false
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setTouchpadVisualizer(@NonNull Context context, boolean enabled) {
        if (!isTouchpadVisualizerFeatureFlagEnabled()) {
            return;
        }
        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.TOUCHPAD_VISUALIZER, enabled ? 1 : 0, UserHandle.USER_CURRENT);
    }

    /**
     * Returns true if the touchpad should allow tap dragging.
     *
     * The returned value only applies to gesture-compatible touchpads.
     *
     * @param context The application context.
     * @return Whether the touchpad should allow tap dragging.
     *
     * @hide
     */
    public static boolean useTouchpadTapDragging(@NonNull Context context) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.TOUCHPAD_TAP_DRAGGING, 0, UserHandle.USER_CURRENT) == 1;
    }

    /**
     * Sets the tap dragging behavior for the touchpad.
     *
     * The new behavior is only applied to gesture-compatible touchpads.
     *
     * @param context The application context.
     * @param enabled Will enable tap dragging if true, disable it if false
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setTouchpadTapDragging(@NonNull Context context, boolean enabled) {
        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.TOUCHPAD_TAP_DRAGGING, enabled ? 1 : 0,
                UserHandle.USER_CURRENT);
    }

    /**
     * Returns true if the touchpad should use the right click zone.
     *
     * The returned value only applies to gesture-compatible touchpads.
     *
     * @param context The application context.
     * @return Whether the touchpad should use the right click zone.
     *
     * @hide
     */
    public static boolean useTouchpadRightClickZone(@NonNull Context context) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.TOUCHPAD_RIGHT_CLICK_ZONE, 0, UserHandle.USER_CURRENT) == 1;
    }

    /**
     * Sets the right click zone behavior for the touchpad.
     *
     * The new behavior is only applied to gesture-compatible touchpads.
     *
     * @param context The application context.
     * @param enabled Will enable the right click zone if true, disable it if false
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setTouchpadRightClickZone(@NonNull Context context, boolean enabled) {
        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.TOUCHPAD_RIGHT_CLICK_ZONE, enabled ? 1 : 0,
                UserHandle.USER_CURRENT);
    }

    /**
     * Returns true if three-finger taps on the touchpad should trigger a customizable shortcut
     * rather than a middle click.
     *
     * The returned value only applies to gesture-compatible touchpads.
     *
     * @param context The application context.
     * @return Whether three-finger taps should trigger the shortcut.
     *
     * @hide
     */
    public static boolean useTouchpadThreeFingerTapShortcut(@NonNull Context context) {
        int customizedShortcut = Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.TOUCHPAD_THREE_FINGER_TAP_CUSTOMIZATION,
                KeyGestureEvent.KEY_GESTURE_TYPE_UNSPECIFIED, UserHandle.USER_CURRENT);
        return customizedShortcut != KeyGestureEvent.KEY_GESTURE_TYPE_UNSPECIFIED
                && isTouchpadThreeFingerTapShortcutFeatureFlagEnabled();
    }

    /**
     * Returns true if system gestures (three- and four-finger swipes) should be enabled for
     * touchpads.
     *
     * @param context The application context.
     * @return Whether system gestures on touchpads are enabled
     *
     * @hide
     */
    public static boolean useTouchpadSystemGestures(@NonNull Context context) {
        if (!isTouchpadSystemGestureDisableFeatureFlagEnabled()) {
            return true;
        }
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.TOUCHPAD_SYSTEM_GESTURES, 1, UserHandle.USER_CURRENT) == 1;
    }

    /**
     * Sets whether system gestures are enabled for touchpads.
     *
     * @param context The application context.
     * @param enabled True to enable system gestures.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setTouchpadSystemGesturesEnabled(@NonNull Context context, boolean enabled) {
        if (!isTouchpadSystemGestureDisableFeatureFlagEnabled()) {
            return;
        }
        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.TOUCHPAD_SYSTEM_GESTURES, enabled ? 1 : 0, UserHandle.USER_CURRENT);
    }

    /**
     * Whether a pointer icon will be shown over the location of a stylus pointer.
     *
     * @hide
     */
    public static boolean isStylusPointerIconEnabled(@NonNull Context context,
            boolean forceReloadSetting) {
        if (InputProperties.force_enable_stylus_pointer_icon().orElse(false)) {
            // Sysprop override is set
            return true;
        }
        if (!context.getResources().getBoolean(
                com.android.internal.R.bool.config_enableStylusPointerIcon)) {
            // Stylus pointer icons are disabled for the build
            return false;
        }
        if (forceReloadSetting) {
            return Settings.Secure.getIntForUser(context.getContentResolver(),
                    Settings.Secure.STYLUS_POINTER_ICON_ENABLED,
                    DEFAULT_STYLUS_POINTER_ICON_ENABLED, UserHandle.USER_CURRENT_OR_SELF) != 0;
        }
        return AppGlobals.getIntCoreSetting(Settings.Secure.STYLUS_POINTER_ICON_ENABLED,
                DEFAULT_STYLUS_POINTER_ICON_ENABLED) != 0;
    }

    /**
     * Whether a pointer icon will be shown over the location of a stylus pointer.
     *
     * @hide
     * @see #isStylusPointerIconEnabled(Context, boolean)
     */
    public static boolean isStylusPointerIconEnabled(@NonNull Context context) {
        return isStylusPointerIconEnabled(context, false /* forceReloadSetting */);
    }

    /**
     * Whether mouse scrolling acceleration is enabled. This applies only to connected mice.
     *
     * @param context The application context.
     * @return Whether the mouse scrolling is accelerated based on the user's scrolling speed.
     *
     * @hide
     */
    public static boolean isMouseScrollingAccelerationEnabled(@NonNull Context context) {
        if (!isMouseScrollingAccelerationFeatureFlagEnabled()) {
            return true;
        }

        return Settings.System.getIntForUser(context.getContentResolver(),
            Settings.System.MOUSE_SCROLLING_ACCELERATION, 0, UserHandle.USER_CURRENT) != 0;
    }

    /**
     * Sets whether the connected mouse scrolling acceleration is enabled.
     *
     * @param context The application context.
     * @param scrollingAcceleration Whether mouse scrolling acceleration is enabled.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setMouseScrollingAcceleration(@NonNull Context context,
            boolean scrollingAcceleration) {
        if (!isMouseScrollingAccelerationFeatureFlagEnabled()) {
            return;
        }

        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.MOUSE_SCROLLING_ACCELERATION, scrollingAcceleration ? 1 : 0,
                UserHandle.USER_CURRENT);
    }

    /**
     * Gets the mouse scrolling speed.
     *
     * The returned value only applies when mouse scrolling acceleration is not enabled.
     *
     * @param context The application context.
     * @return The mouse scrolling speed as a value between {@link #MIN_MOUSE_SCROLLING_SPEED} and
     *         {@link #MAX_MOUSE_SCROLLING_SPEED}, or the default value
     *         {@link #DEFAULT_MOUSE_SCROLLING_SPEED}.
     *
     * @hide
     */
    public static int getMouseScrollingSpeed(@NonNull Context context) {
        if (!isMouseScrollingAccelerationFeatureFlagEnabled()) {
            return 0;
        }

        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.MOUSE_SCROLLING_SPEED, DEFAULT_MOUSE_SCROLLING_SPEED,
                UserHandle.USER_CURRENT);
    }

    /**
     * Sets the mouse scrolling speed, and saves it in the settings.
     *
     * The new speed will only apply when mouse scrolling acceleration is not enabled.
     *
     * @param context The application context.
     * @param speed The mouse scrolling speed as a value between {@link #MIN_MOUSE_SCROLLING_SPEED}
     *              and {@link #MAX_MOUSE_SCROLLING_SPEED}, or the default value
     *              {@link #DEFAULT_MOUSE_SCROLLING_SPEED}.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setMouseScrollingSpeed(@NonNull Context context, int speed) {
        if (isMouseScrollingAccelerationEnabled(context)) {
            return;
        }

        if (speed < MIN_MOUSE_SCROLLING_SPEED || speed > MAX_MOUSE_SCROLLING_SPEED) {
            throw new IllegalArgumentException("speed out of range");
        }

        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.MOUSE_SCROLLING_SPEED, speed, UserHandle.USER_CURRENT);
    }

    /**
     * Whether mouse vertical scrolling is reversed. This applies only to connected mice.
     *
     * @param context The application context.
     * @return Whether the mouse will have its vertical scrolling reversed
     * (scroll down to move up).
     *
     * @hide
     */
    public static boolean isMouseReverseVerticalScrollingEnabled(@NonNull Context context) {
        if (!isMouseReverseVerticalScrollingFeatureFlagEnabled()) {
            return false;
        }

        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.MOUSE_REVERSE_VERTICAL_SCROLLING, 0, UserHandle.USER_CURRENT)
                != 0;
    }

    /**
     * Sets whether the connected mouse will have its vertical scrolling reversed.
     *
     * @param context The application context.
     * @param reverseScrolling Whether reverse scrolling is enabled.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setMouseReverseVerticalScrolling(@NonNull Context context,
            boolean reverseScrolling) {
        if (!isMouseReverseVerticalScrollingFeatureFlagEnabled()) {
            return;
        }

        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.MOUSE_REVERSE_VERTICAL_SCROLLING, reverseScrolling ? 1 : 0,
                UserHandle.USER_CURRENT);
    }

    /**
     * Whether the primary mouse button is swapped on connected mice.
     *
     * @param context The application context.
     * @return Whether mice will have their primary buttons swapped, so that left clicking will
     * perform the secondary action (e.g. show menu) and right clicking will perform the primary
     * action.
     *
     * @hide
     */
    public static boolean isMouseSwapPrimaryButtonEnabled(@NonNull Context context) {
        if (!isMouseSwapPrimaryButtonFeatureFlagEnabled()) {
            return false;
        }

        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.MOUSE_SWAP_PRIMARY_BUTTON, 0, UserHandle.USER_CURRENT)
                != 0;
    }

    /**
     * Sets whether mice will have their primary buttons swapped between left and right
     * clicks.
     *
     * @param context The application context.
     * @param swapPrimaryButton Whether swapping the primary button is enabled.
     *
     * @hide
     */
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setMouseSwapPrimaryButton(@NonNull Context context,
            boolean swapPrimaryButton) {
        if (!isMouseSwapPrimaryButtonFeatureFlagEnabled()) {
            return;
        }

        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.MOUSE_SWAP_PRIMARY_BUTTON, swapPrimaryButton ? 1 : 0,
                UserHandle.USER_CURRENT);
    }

    /**
     * Whether cursor acceleration is enabled or not for connected mice.
     *
     * @param context The application context.
     *
     * @hide
     */
    public static boolean isMousePointerAccelerationEnabled(@NonNull Context context) {
        if (!isPointerAccelerationFeatureFlagEnabled()) {
            return true;
        }

        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.MOUSE_POINTER_ACCELERATION_ENABLED, 1, UserHandle.USER_CURRENT)
                == 1;
    }

   /**
    * Sets whether mouse acceleration is enabled.
    *
    * When enabled, the mouse cursor moves farther when it is moved faster.
    * When disabled, the mouse cursor speed becomes directly proportional to
    * the speed at which the mouse is moved.
    *
    * @param context The application context.
    * @param enabled Will enable mouse acceleration if true, disable it if
    *                false.
    * @hide
    */
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setMouseAccelerationEnabled(@NonNull Context context,
            boolean enabled) {
        if (!isPointerAccelerationFeatureFlagEnabled()) {
            return;
        }
        Settings.System.putIntForUser(context.getContentResolver(),
                Settings.System.MOUSE_POINTER_ACCELERATION_ENABLED, enabled ? 1 : 0,
                UserHandle.USER_CURRENT);
    }

    /**
     * Whether Accessibility bounce keys is enabled.
     *
     * <p>
     * ‘Bounce keys’ is an accessibility feature to aid users who have physical disabilities,
     * that allows the user to configure the device to ignore rapid, repeated keypresses of the
     * same key.
     * </p>
     *
     * @hide
     */
    public static boolean isAccessibilityBounceKeysEnabled(@NonNull Context context) {
        return getAccessibilityBounceKeysThreshold(context) != 0;
    }

    /**
     * Get Accessibility bounce keys threshold duration in milliseconds.
     *
     * <p>
     * ‘Bounce keys’ is an accessibility feature to aid users who have physical disabilities,
     * that allows the user to configure the device to ignore rapid, repeated keypresses of the
     * same key.
     * </p>
     *
     * @hide
     */
    @TestApi
    public static int getAccessibilityBounceKeysThreshold(@NonNull Context context) {
        return Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_BOUNCE_KEYS, 0, UserHandle.USER_CURRENT);
    }

    /**
     * Set Accessibility bounce keys threshold duration in milliseconds.
     * @param thresholdTimeMillis time duration for which a key down will be ignored after a
     *                            previous key up for the same key on the same device between 0 and
     *                            {@link MAX_ACCESSIBILITY_BOUNCE_KEYS_THRESHOLD_MILLIS}
     *
     * <p>
     * ‘Bounce keys’ is an accessibility feature to aid users who have physical disabilities,
     * that allows the user to configure the device to ignore rapid, repeated keypresses of the
     * same key.
     * </p>
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setAccessibilityBounceKeysThreshold(@NonNull Context context,
            int thresholdTimeMillis) {
        if (thresholdTimeMillis < 0
                || thresholdTimeMillis > MAX_ACCESSIBILITY_BOUNCE_KEYS_THRESHOLD_MILLIS) {
            throw new IllegalArgumentException(
                    "Provided Bounce keys threshold should be in range [0, "
                            + MAX_ACCESSIBILITY_BOUNCE_KEYS_THRESHOLD_MILLIS + "]");
        }
        Settings.Secure.putIntForUser(context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_BOUNCE_KEYS, thresholdTimeMillis,
                UserHandle.USER_CURRENT);
    }

    /**
     * Whether Accessibility slow keys is enabled.
     *
     * <p>
     * 'Slow keys' is an accessibility feature to aid users who have physical disabilities, that
     * allows the user to specify the duration for which one must press-and-hold a key before the
     * system accepts the keypress.
     * </p>
     *
     * @hide
     */
    public static boolean isAccessibilitySlowKeysEnabled(@NonNull Context context) {
        return getAccessibilitySlowKeysThreshold(context) != 0;
    }

    /**
     * Get Accessibility slow keys threshold duration in milliseconds.
     *
     * <p>
     * 'Slow keys' is an accessibility feature to aid users who have physical disabilities, that
     * allows the user to specify the duration for which one must press-and-hold a key before the
     * system accepts the keypress.
     * </p>
     *
     * @hide
     */
    @TestApi
    public static int getAccessibilitySlowKeysThreshold(@NonNull Context context) {
        return Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_SLOW_KEYS, 0, UserHandle.USER_CURRENT);
    }

    /**
     * Set Accessibility slow keys threshold duration in milliseconds.
     * @param thresholdTimeMillis time duration for which a key should be pressed to be registered
     *                            in the system. The threshold must be between 0 and
     *                            {@link MAX_ACCESSIBILITY_SLOW_KEYS_THRESHOLD_MILLIS}
     *
     * <p>
     * 'Slow keys' is an accessibility feature to aid users who have physical disabilities, that
     * allows the user to specify the duration for which one must press-and-hold a key before the
     * system accepts the keypress.
     * </p>
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setAccessibilitySlowKeysThreshold(@NonNull Context context,
            int thresholdTimeMillis) {
        if (thresholdTimeMillis < 0
                || thresholdTimeMillis > MAX_ACCESSIBILITY_SLOW_KEYS_THRESHOLD_MILLIS) {
            throw new IllegalArgumentException(
                    "Provided Slow keys threshold should be in range [0, "
                            + MAX_ACCESSIBILITY_SLOW_KEYS_THRESHOLD_MILLIS + "]");
        }
        Settings.Secure.putIntForUser(context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_SLOW_KEYS, thresholdTimeMillis,
                UserHandle.USER_CURRENT);
    }

    /**
     * Whether Accessibility sticky keys is enabled.
     *
     * <p>
     * 'Sticky keys' is an accessibility feature that assists users who have physical
     * disabilities or help users reduce repetitive strain injury. It serializes keystrokes
     * instead of pressing multiple keys at a time, allowing the user to press and release a
     * modifier key, such as Shift, Ctrl, Alt, or any other modifier key, and have it remain
     * active until any other key is pressed.
     * </p>
     *
     * @hide
     */
    @TestApi
    public static boolean isAccessibilityStickyKeysEnabled(@NonNull Context context) {
        return Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_STICKY_KEYS, 0, UserHandle.USER_CURRENT) != 0;
    }

    /**
     * Set Accessibility sticky keys feature enabled/disabled.
     *
     *  <p>
     * 'Sticky keys' is an accessibility feature that assists users who have physical
     * disabilities or help users reduce repetitive strain injury. It serializes keystrokes
     * instead of pressing multiple keys at a time, allowing the user to press and release a
     * modifier key, such as Shift, Ctrl, Alt, or any other modifier key, and have it remain
     * active until any other key is pressed.
     * </p>
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setAccessibilityStickyKeysEnabled(@NonNull Context context,
            boolean enabled) {
        Settings.Secure.putIntForUser(context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_STICKY_KEYS, enabled ? 1 : 0,
                UserHandle.USER_CURRENT);
    }

    /**
     * Whether Accessibility mouse keys feature flag is enabled.
     *
     * <p>
     * ‘Mouse keys’ is an accessibility feature to aid users who have physical disabilities,
     * that allows the user to use the keys on the keyboard to control the mouse pointer and
     * other perform other mouse functionality.
     * </p>
     *
     * @hide
     */
    public static boolean isAccessibilityMouseKeysFeatureFlagEnabled() {
        return keyboardA11yMouseKeys();
    }

    /**
     * Whether Accessibility mouse keys is enabled.
     *
     * <p>
     * ‘Mouse keys’ is an accessibility feature to aid users who have physical disabilities,
     * that allows the user to use the keys on the keyboard to control the mouse pointer and
     * other perform other mouse functionality.
     * </p>
     *
     * @hide
     */
    @TestApi
    @FlaggedApi(FLAG_KEYBOARD_A11Y_MOUSE_KEYS)
    public static boolean isAccessibilityMouseKeysEnabled(@NonNull Context context) {
        if (!isAccessibilityMouseKeysFeatureFlagEnabled()) {
            return false;
        }
        return Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_MOUSE_KEYS_ENABLED, 0, UserHandle.USER_CURRENT)
                != 0;
    }

    /**
     * Set Accessibility mouse keys feature enabled/disabled.
     *
     *  <p>
     * ‘Mouse keys’ is an accessibility feature to aid users who have physical disabilities,
     * that allows the user to use the keys on the keyboard to control the mouse pointer and
     * other perform other mouse functionality.
     * </p>
     *
     * @hide
     */
    @TestApi
    @FlaggedApi(FLAG_KEYBOARD_A11Y_MOUSE_KEYS)
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setAccessibilityMouseKeysEnabled(@NonNull Context context,
            boolean enabled) {
        if (!isAccessibilityMouseKeysFeatureFlagEnabled()) {
            return;
        }
        Settings.Secure.putIntForUser(context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_MOUSE_KEYS_ENABLED, enabled ? 1 : 0,
                UserHandle.USER_CURRENT);
    }

    /**
     * Whether "Repeat keys" feature flag is enabled.
     *
     * <p>
     * ‘Repeat keys’ is a feature which allows users to generate key repeats when a particular
     * key on the physical keyboard is held down. This feature allows the user
     * to configure the timeout before the key repeats begin as well as the delay
     * between successive key repeats.
     * </p>
     *
     * @hide
     */
    public static boolean isRepeatKeysFeatureFlagEnabled() {
        return keyboardRepeatKeys();
    }

    /**
     * Whether "Repeat keys" feature is enabled.
     * Repeat keys is ON by default.
     * The repeat keys timeout and delay would have the default values in the default ON case.
     *
     * <p>
     * 'Repeat keys’ is a feature which allows users to generate key repeats when a particular
     * key on the physical keyboard is held down. This feature allows the user
     * to configure the timeout before the key repeats begin as well as the delay
     * between successive key repeats.
     * </p>
     *
     * @hide
     */
    @TestApi
    @FlaggedApi(FLAG_KEYBOARD_REPEAT_KEYS)
    public static boolean isRepeatKeysEnabled(@NonNull Context context) {
        if (!isRepeatKeysFeatureFlagEnabled()) {
            return true;
        }
        return Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.KEY_REPEAT_ENABLED, 1, UserHandle.USER_CURRENT) != 0;
    }

    /**
     * Get repeat keys timeout duration in milliseconds.
     * The default key repeat timeout is {@link ViewConfiguration#DEFAULT_KEY_REPEAT_TIMEOUT_MS}.
     *
     * @param context The application context
     * @return The time duration for which a key should be pressed after
     *         which the pressed key will be repeated. The timeout must be between
     *         {@link #MIN_KEY_REPEAT_TIMEOUT_MILLIS} and
     *         {@link #MAX_KEY_REPEAT_TIMEOUT_MILLIS}
     *
     * <p>
     * ‘Repeat keys’ is a feature which allows users to generate key repeats when a particular
     * key on the physical keyboard is held down. This feature allows the user
     * to configure the timeout before the key repeats begin as well as the delay
     * between successive key repeats.
     * </p>
     *
     * @hide
     */
    @TestApi
    @FlaggedApi(FLAG_KEYBOARD_REPEAT_KEYS)
    public static int getRepeatKeysTimeout(@NonNull Context context) {
        if (!isRepeatKeysFeatureFlagEnabled()) {
            return ViewConfiguration.getKeyRepeatTimeout();
        }
        return Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.KEY_REPEAT_TIMEOUT_MS, ViewConfiguration.getKeyRepeatTimeout(),
                UserHandle.USER_CURRENT);
    }

    /**
     * Get repeat keys delay rate in milliseconds.
     * The default key repeat delay is {@link ViewConfiguration#DEFAULT_KEY_REPEAT_DELAY_MS}.
     *
     * @param context The application context
     * @return Time duration between successive key repeats when a key is
     *         pressed down. The delay duration must be between
     *         {@link #MIN_KEY_REPEAT_DELAY_MILLIS} and
     *         {@link #MAX_KEY_REPEAT_DELAY_MILLIS}
     *
     * <p>
     * ‘Repeat keys’ is a feature which allows users to generate key repeats when a particular
     * key on the physical keyboard is held down. This feature allows the user
     * to configure the timeout before the key repeats begin as well as the delay
     * between successive key repeats.
     * </p>
     *
     * @hide
     */
    @TestApi
    @FlaggedApi(FLAG_KEYBOARD_REPEAT_KEYS)
    public static int getRepeatKeysDelay(@NonNull Context context) {
        if (!isRepeatKeysFeatureFlagEnabled()) {
            return ViewConfiguration.getKeyRepeatDelay();
        }
        return Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.KEY_REPEAT_DELAY_MS, ViewConfiguration.getKeyRepeatDelay(),
                UserHandle.USER_CURRENT);
    }

    /**
     * Set repeat keys feature enabled/disabled.
     *
     * <p>
     * 'Repeat keys’ is a feature which allows users to generate key repeats when a particular
     * key on the physical keyboard is held down. This feature allows the user
     * to configure the timeout before the key repeats begin as well as the delay
     * between successive key repeats.
     * </p>
     *
     * @hide
     */
    @TestApi
    @FlaggedApi(FLAG_KEYBOARD_REPEAT_KEYS)
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setRepeatKeysEnabled(@NonNull Context context,
            boolean enabled) {
        if (!isRepeatKeysFeatureFlagEnabled()) {
            return;
        }
        Settings.Secure.putIntForUser(context.getContentResolver(),
                Settings.Secure.KEY_REPEAT_ENABLED, enabled ? 1 : 0, UserHandle.USER_CURRENT);
    }

    /**
     * Set repeat keys timeout duration in milliseconds.
     *
     * @param timeoutTimeMillis time duration for which a key should be pressed after which the
     *                          pressed key will be repeated. The timeout must be between
     *                          {@link #MIN_KEY_REPEAT_TIMEOUT_MILLIS} and
     *                          {@link #MAX_KEY_REPEAT_TIMEOUT_MILLIS}
     *
     *  <p>
     * ‘Repeat keys’ is a feature which allows users to generate key repeats when a particular
     * key on the physical keyboard is held down. This feature allows the user
     * to configure the timeout before the key repeats begin as well as the delay
     *  between successive key repeats.
     * </p>
     *
     * @hide
     */
    @TestApi
    @FlaggedApi(FLAG_KEYBOARD_REPEAT_KEYS)
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setRepeatKeysTimeout(@NonNull Context context,
            int timeoutTimeMillis) {
        if (!isRepeatKeysFeatureFlagEnabled()
                && !isRepeatKeysEnabled(context)) {
            return;
        }
        if (timeoutTimeMillis < MIN_KEY_REPEAT_TIMEOUT_MILLIS
                || timeoutTimeMillis > MAX_KEY_REPEAT_TIMEOUT_MILLIS) {
            throw new IllegalArgumentException(
                    "Provided repeat keys timeout should be in range ("
                            + MIN_KEY_REPEAT_TIMEOUT_MILLIS + ","
                            + MAX_KEY_REPEAT_TIMEOUT_MILLIS + ")");
        }
        Settings.Secure.putIntForUser(context.getContentResolver(),
                Settings.Secure.KEY_REPEAT_TIMEOUT_MS, timeoutTimeMillis,
                UserHandle.USER_CURRENT);
    }

    /**
     * Set repeat key delay duration in milliseconds.
     *
     * @param delayTimeMillis Time duration between successive key repeats when a key is
     *                        pressed down. The delay duration must be between
     *                        {@link #MIN_KEY_REPEAT_DELAY_MILLIS} and
     *                        {@link #MAX_KEY_REPEAT_DELAY_MILLIS}
     * <p>
     * ‘Repeat keys’ is a feature which allows users to generate key repeats when a particular
     * key on the physical keyboard is held down. This feature allows the user
     * to configure the timeout before the key repeats begin as well as the delay
     * between successive key repeats.
     * </p>
     *
     * @hide
     */
    @TestApi
    @FlaggedApi(FLAG_KEYBOARD_REPEAT_KEYS)
    @RequiresPermission(Manifest.permission.WRITE_SETTINGS)
    public static void setRepeatKeysDelay(@NonNull Context context,
            int delayTimeMillis) {
        if (!isRepeatKeysFeatureFlagEnabled()
                && !isRepeatKeysEnabled(context)) {
            return;
        }
        if (delayTimeMillis < MIN_KEY_REPEAT_DELAY_MILLIS
                || delayTimeMillis > MAX_KEY_REPEAT_DELAY_MILLIS) {
            throw new IllegalArgumentException(
                    "Provided repeat keys delay should be in range ("
                            + MIN_KEY_REPEAT_DELAY_MILLIS + ","
                            + MAX_KEY_REPEAT_DELAY_MILLIS + ")");
        }
        Settings.Secure.putIntForUser(context.getContentResolver(),
                Settings.Secure.KEY_REPEAT_DELAY_MS, delayTimeMillis,
                UserHandle.USER_CURRENT);
    }

    /**
     * Whether "Customizable key gestures" feature flag is enabled.
     *
     * <p>
     * ‘Customizable key gestures’ is a feature which allows users to customize key based
     * shortcuts on the physical keyboard.
     * </p>
     *
     * @hide
     */
    public static boolean isCustomizableInputGesturesFeatureFlagEnabled() {
        return enableCustomizableInputGestures() && useKeyGestureEventHandler();
    }

    /**
     * Whether multi-key gestures are supported using {@code KeyGestureEventHandler}
     *
     * @hide
     */
    public static boolean doesKeyGestureEventHandlerSupportMultiKeyGestures() {
        return useKeyGestureEventHandler() && useKeyGestureEventHandlerMultiKeyGestures();
    }
}
