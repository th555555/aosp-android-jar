/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.companion.virtual.sensor;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.companion.virtual.IVirtualDevice;
import android.companion.virtualdevice.flags.Flags;
import android.hardware.Sensor;
import android.hardware.SensorAdditionalInfo;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;

/**
 * Representation of a sensor on a remote device, capable of sending events, such as an
 * accelerometer or a gyroscope.
 *
 * <p>A virtual sensor device is registered with the sensor framework as a runtime sensor.
 *
 * @hide
 */
@SystemApi
public final class VirtualSensor implements Parcelable {

    private final int mHandle;
    private final int mType;
    private final String mName;
    private final int mFlags;
    private final IVirtualDevice mVirtualDevice;
    private final IBinder mToken;
    // Only one additional info frame set at a time.
    private final Object mAdditionalInfoLock = new Object();

    /**
     * @hide
     */
    public VirtualSensor(int handle, int type, String name, IVirtualDevice virtualDevice,
            IBinder token) {
        this(handle, type, name, /*flags=*/0, virtualDevice, token);
    }

    /**
     * @hide
     */
    public VirtualSensor(int handle, int type, String name, int flags, IVirtualDevice virtualDevice,
            IBinder token) {
        mHandle = handle;
        mType = type;
        mName = name;
        mFlags = flags;
        mVirtualDevice = virtualDevice;
        mToken = token;
    }

    /**
     * @hide
     */
    @SuppressLint("UnflaggedApi") // @TestApi without associated feature.
    @TestApi
    public VirtualSensor(int handle, int type, @NonNull String name) {
        this(handle, type, name, /*flags=*/0, /*virtualDevice=*/null, /*token=*/null);
    }

    private VirtualSensor(Parcel parcel) {
        mHandle = parcel.readInt();
        mType = parcel.readInt();
        mName = parcel.readString8();
        mFlags = parcel.readInt();
        mVirtualDevice = IVirtualDevice.Stub.asInterface(parcel.readStrongBinder());
        mToken = parcel.readStrongBinder();
    }

    /**
     * Returns the unique handle of the sensor.
     *
     * @hide
     */
    @SuppressLint("UnflaggedApi") // @TestApi without associated feature.
    @TestApi
    public int getHandle() {
        return mHandle;
    }

    /**
     * Returns the type of the sensor.
     *
     * @see Sensor#getType()
     * @see <a href="https://source.android.com/devices/sensors/sensor-types">Sensor types</a>
     */
    public int getType() {
        return mType;
    }

    /**
     * Returns the name of the sensor.
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Returns the identifier of the
     * {@link android.companion.virtual.VirtualDeviceManager.VirtualDevice} this sensor belongs to.
     */
    public int getDeviceId() {
        try {
            return mVirtualDevice.getDeviceId();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int flags) {
        parcel.writeInt(mHandle);
        parcel.writeInt(mType);
        parcel.writeString8(mName);
        parcel.writeInt(mFlags);
        parcel.writeStrongBinder(mVirtualDevice.asBinder());
        parcel.writeStrongBinder(mToken);
    }

    @Override
    public String toString() {
        return "VirtualSensor{ mType=" + mType + ", mName='" + mName + "' }";
    }

    /**
     * Send a sensor event to the system.
     */
    public void sendEvent(@NonNull VirtualSensorEvent event) {
        try {
            mVirtualDevice.sendSensorEvent(mToken, event);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Send additional information about the sensor to the system.
     *
     * @param info the additional sensor information to send.
     * @throws UnsupportedOperationException if the sensor does not support sending additional info.
     * @see Sensor#isAdditionalInfoSupported()
     * @see VirtualSensorConfig.Builder#setAdditionalInfoSupported(boolean)
     * @see SensorAdditionalInfo
     * @see VirtualSensorAdditionalInfo
     */
    @FlaggedApi(Flags.FLAG_VIRTUAL_SENSOR_ADDITIONAL_INFO)
    public void sendAdditionalInfo(@NonNull VirtualSensorAdditionalInfo info) {
        if (!Flags.virtualSensorAdditionalInfo()) {
            throw new UnsupportedOperationException("Sensor additional info not supported.");
        }
        if ((mFlags & VirtualSensorConfig.ADDITIONAL_INFO_MASK) == 0) {
            throw new UnsupportedOperationException("Sensor additional info not supported.");
        }
        try {
            synchronized (mAdditionalInfoLock) {
                mVirtualDevice.sendSensorAdditionalInfo(mToken, info);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @NonNull
    public static final Parcelable.Creator<VirtualSensor> CREATOR =
            new Parcelable.Creator<VirtualSensor>() {
                public VirtualSensor createFromParcel(Parcel in) {
                    return new VirtualSensor(in);
                }

                public VirtualSensor[] newArray(int size) {
                    return new VirtualSensor[size];
                }
            };
}
