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

package android.location;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.location.GnssAssistance.GnssSatelliteCorrections;
import android.location.flags.Flags;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A class contains Galileo assistance.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_GNSS_ASSISTANCE_INTERFACE)
@SystemApi
public final class GalileoAssistance implements Parcelable {

    /** The Galileo almanac. */
    @Nullable private final GnssAlmanac mAlmanac;

    /** The Klobuchar ionospheric model. */
    @Nullable private final KlobucharIonosphericModel mIonosphericModel;

    /** The UTC model. */
    @Nullable private final UtcModel mUtcModel;

    /** The leap seconds model. */
    @Nullable private final LeapSecondsModel mLeapSecondsModel;

    /** The auxiliary information. */
    @Nullable private final AuxiliaryInformation mAuxiliaryInformation;

    /** The list of time models. */
    @NonNull private final List<TimeModel> mTimeModels;

    /** The list of Galileo ephemeris. */
    @NonNull private final List<GalileoSatelliteEphemeris> mSatelliteEphemeris;

    /** The list of real time integrity models. */
    @NonNull private final List<RealTimeIntegrityModel> mRealTimeIntegrityModels;

    /** The list of Galileo satellite corrections. */
    @NonNull private final List<GnssSatelliteCorrections> mSatelliteCorrections;

    private GalileoAssistance(Builder builder) {
        mAlmanac = builder.mAlmanac;
        mIonosphericModel = builder.mIonosphericModel;
        mUtcModel = builder.mUtcModel;
        mLeapSecondsModel = builder.mLeapSecondsModel;
        mAuxiliaryInformation = builder.mAuxiliaryInformation;
        if (builder.mTimeModels != null) {
            mTimeModels = Collections.unmodifiableList(new ArrayList<>(builder.mTimeModels));
        } else {
            mTimeModels = new ArrayList<>();
        }
        if (builder.mSatelliteEphemeris != null) {
            mSatelliteEphemeris =
                    Collections.unmodifiableList(new ArrayList<>(builder.mSatelliteEphemeris));
        } else {
            mSatelliteEphemeris = new ArrayList<>();
        }
        if (builder.mRealTimeIntegrityModels != null) {
            mRealTimeIntegrityModels =
                    Collections.unmodifiableList(new ArrayList<>(builder.mRealTimeIntegrityModels));
        } else {
            mRealTimeIntegrityModels = new ArrayList<>();
        }
        if (builder.mSatelliteCorrections != null) {
            mSatelliteCorrections =
                    Collections.unmodifiableList(new ArrayList<>(builder.mSatelliteCorrections));
        } else {
            mSatelliteCorrections = new ArrayList<>();
        }
    }

    /** Returns the Galileo almanac. */
    @Nullable
    public GnssAlmanac getAlmanac() {
        return mAlmanac;
    }

    /** Returns the Klobuchar ionospheric model. */
    @Nullable
    public KlobucharIonosphericModel getIonosphericModel() {
        return mIonosphericModel;
    }

    /** Returns the UTC model. */
    @Nullable
    public UtcModel getUtcModel() {
        return mUtcModel;
    }

    /** Returns the leap seconds model. */
    @Nullable
    public LeapSecondsModel getLeapSecondsModel() {
        return mLeapSecondsModel;
    }

    /** Returns the auxiliary information. */
    @Nullable
    public AuxiliaryInformation getAuxiliaryInformation() {
        return mAuxiliaryInformation;
    }

    /** Returns the list of time models. */
    @NonNull
    public List<TimeModel> getTimeModels() {
        return mTimeModels;
    }

    /** Returns the list of Galileo ephemeris. */
    @NonNull
    public List<GalileoSatelliteEphemeris> getSatelliteEphemeris() {
        return mSatelliteEphemeris;
    }

    /** Returns the list of real time integrity models. */
    @NonNull
    public List<RealTimeIntegrityModel> getRealTimeIntegrityModels() {
        return mRealTimeIntegrityModels;
    }

    /** Returns the list of Galileo satellite corrections. */
    @NonNull
    public List<GnssSatelliteCorrections> getSatelliteCorrections() {
        return mSatelliteCorrections;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeTypedObject(mAlmanac, flags);
        dest.writeTypedObject(mIonosphericModel, flags);
        dest.writeTypedObject(mUtcModel, flags);
        dest.writeTypedObject(mLeapSecondsModel, flags);
        dest.writeTypedObject(mAuxiliaryInformation, flags);
        dest.writeTypedList(mTimeModels);
        dest.writeTypedList(mSatelliteEphemeris);
        dest.writeTypedList(mRealTimeIntegrityModels);
        dest.writeTypedList(mSatelliteCorrections);
    }

    @Override
    @NonNull
    public String toString() {
        StringBuilder builder = new StringBuilder("GalileoAssistance[");
        builder.append("almanac = ").append(mAlmanac);
        builder.append(", ionosphericModel = ").append(mIonosphericModel);
        builder.append(", utcModel = ").append(mUtcModel);
        builder.append(", leapSecondsModel = ").append(mLeapSecondsModel);
        builder.append(", auxiliaryInformation = ").append(mAuxiliaryInformation);
        builder.append(", timeModels = ").append(mTimeModels);
        builder.append(", satelliteEphemeris = ").append(mSatelliteEphemeris);
        builder.append(", realTimeIntegrityModels = ").append(mRealTimeIntegrityModels);
        builder.append(", satelliteCorrections = ").append(mSatelliteCorrections);
        builder.append("]");
        return builder.toString();
    }

    public static final @android.annotation.NonNull Creator<GalileoAssistance> CREATOR =
            new Creator<GalileoAssistance>() {
                @Override
                public GalileoAssistance createFromParcel(Parcel in) {
                    return new GalileoAssistance.Builder()
                            .setAlmanac(in.readTypedObject(GnssAlmanac.CREATOR))
                            .setIonosphericModel(
                                    in.readTypedObject(KlobucharIonosphericModel.CREATOR))
                            .setUtcModel(in.readTypedObject(UtcModel.CREATOR))
                            .setLeapSecondsModel(in.readTypedObject(LeapSecondsModel.CREATOR))
                            .setAuxiliaryInformation(
                                    in.readTypedObject(AuxiliaryInformation.CREATOR))
                            .setTimeModels(in.createTypedArrayList(TimeModel.CREATOR))
                            .setSatelliteEphemeris(
                                    in.createTypedArrayList(GalileoSatelliteEphemeris.CREATOR))
                            .setRealTimeIntegrityModels(
                                    in.createTypedArrayList(RealTimeIntegrityModel.CREATOR))
                            .setSatelliteCorrections(
                                    in.createTypedArrayList(GnssSatelliteCorrections.CREATOR))
                            .build();
                }

                @Override
                public GalileoAssistance[] newArray(int size) {
                    return new GalileoAssistance[size];
                }
            };

    /** Builder for {@link GalileoAssistance}. */
    public static final class Builder {
        private GnssAlmanac mAlmanac;
        private KlobucharIonosphericModel mIonosphericModel;
        private UtcModel mUtcModel;
        private LeapSecondsModel mLeapSecondsModel;
        private AuxiliaryInformation mAuxiliaryInformation;
        private List<TimeModel> mTimeModels;
        private List<GalileoSatelliteEphemeris> mSatelliteEphemeris;
        private List<RealTimeIntegrityModel> mRealTimeIntegrityModels;
        private List<GnssSatelliteCorrections> mSatelliteCorrections;

        /** Sets the Galileo almanac. */
        @NonNull
        public Builder setAlmanac(@Nullable GnssAlmanac almanac) {
            mAlmanac = almanac;
            return this;
        }

        /** Sets the Klobuchar ionospheric model. */
        @NonNull
        public Builder setIonosphericModel(@Nullable KlobucharIonosphericModel ionosphericModel) {
            mIonosphericModel = ionosphericModel;
            return this;
        }

        /** Sets the UTC model. */
        @NonNull
        public Builder setUtcModel(@Nullable UtcModel utcModel) {
            mUtcModel = utcModel;
            return this;
        }

        /** Sets the leap seconds model. */
        @NonNull
        public Builder setLeapSecondsModel(@Nullable LeapSecondsModel leapSecondsModel) {
            mLeapSecondsModel = leapSecondsModel;
            return this;
        }

        /** Sets the auxiliary information. */
        @NonNull
        public Builder setAuxiliaryInformation(
                @Nullable AuxiliaryInformation auxiliaryInformation) {
            mAuxiliaryInformation = auxiliaryInformation;
            return this;
        }

        /** Sets the list of time models. */
        @NonNull
        public Builder setTimeModels(@NonNull List<TimeModel> timeModels) {
            mTimeModels = timeModels;
            return this;
        }

        /** Sets the list of Galileo ephemeris. */
        @NonNull
        public Builder setSatelliteEphemeris(
                @NonNull List<GalileoSatelliteEphemeris> satelliteEphemeris) {
            mSatelliteEphemeris = satelliteEphemeris;
            return this;
        }

        /** Sets the list of real time integrity models. */
        @NonNull
        public Builder setRealTimeIntegrityModels(
                @NonNull List<RealTimeIntegrityModel> realTimeIntegrityModels) {
            mRealTimeIntegrityModels = realTimeIntegrityModels;
            return this;
        }

        /** Sets the list of Galileo satellite corrections. */
        @NonNull
        public Builder setSatelliteCorrections(
                @NonNull List<GnssSatelliteCorrections> satelliteCorrections) {
            mSatelliteCorrections = satelliteCorrections;
            return this;
        }

        /** Builds the {@link GalileoAssistance}. */
        @NonNull
        public GalileoAssistance build() {
            return new GalileoAssistance(this);
        }
    }
}
