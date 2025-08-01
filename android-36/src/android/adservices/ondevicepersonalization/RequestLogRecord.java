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

package android.adservices.ondevicepersonalization;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.os.Parcelable;

import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

// TODO(b/289102463): Add a link to the public doc for the REQUESTS table when available.
/**
 * Contains data that will be written to the REQUESTS table at the end of a call to
 * {@link IsolatedWorker#onExecute(ExecuteInput, android.os.OutcomeReceiver)}.
 * A single {@link RequestLogRecord} is appended to the
 * REQUESTS table if it is present in the output of one of the methods in {@link IsolatedWorker}.
 * The contents of the REQUESTS table can be consumed by Federated Learning facilitated model
 * training, or Federated Analytics facilitated cross-device statistical analysis.
 */
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class RequestLogRecord implements Parcelable {
    /**
     * A List of rows, each containing a {@link ContentValues}.
     **/
    @DataClass.PluralOf("row")
    @NonNull List<ContentValues> mRows = Collections.emptyList();

    /**
     * Internal id for the RequestLogRecord.
     * @hide
     */
    private long mRequestId = 0;

    /**
     * Time of the request in milliseconds
     * @hide
     */
    private long mTimeMillis = 0;

    /**
     * Returns the timestamp of this record.
     */
    @NonNull public Instant getTime() {
        return Instant.ofEpochMilli(getTimeMillis());
    }

    abstract static class BaseBuilder {
        /**
         * @hide
         */
        public abstract Builder setTimeMillis(long value);
    }



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/RequestLogRecord.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ RequestLogRecord(
            @NonNull List<ContentValues> rows,
            long requestId,
            long timeMillis) {
        this.mRows = rows;
        AnnotationValidations.validate(
                NonNull.class, null, mRows);
        this.mRequestId = requestId;
        this.mTimeMillis = timeMillis;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * A List of rows, each containing a {@link ContentValues}.
     */
    @DataClass.Generated.Member
    public @NonNull List<ContentValues> getRows() {
        return mRows;
    }

    /**
     * Internal id for the RequestLogRecord.
     *
     * @hide
     */
    @DataClass.Generated.Member
    public long getRequestId() {
        return mRequestId;
    }

    /**
     * Time of the request in milliseconds
     *
     * @hide
     */
    @DataClass.Generated.Member
    public long getTimeMillis() {
        return mTimeMillis;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(RequestLogRecord other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        RequestLogRecord that = (RequestLogRecord) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Objects.equals(mRows, that.mRows)
                && mRequestId == that.mRequestId
                && mTimeMillis == that.mTimeMillis;
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mRows);
        _hash = 31 * _hash + Long.hashCode(mRequestId);
        _hash = 31 * _hash + Long.hashCode(mTimeMillis);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeParcelableList(mRows, flags);
        dest.writeLong(mRequestId);
        dest.writeLong(mTimeMillis);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ RequestLogRecord(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        List<ContentValues> rows = new java.util.ArrayList<>();
        in.readParcelableList(rows, ContentValues.class.getClassLoader());
        long requestId = in.readLong();
        long timeMillis = in.readLong();

        this.mRows = rows;
        AnnotationValidations.validate(
                NonNull.class, null, mRows);
        this.mRequestId = requestId;
        this.mTimeMillis = timeMillis;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<RequestLogRecord> CREATOR
            = new Parcelable.Creator<RequestLogRecord>() {
        @Override
        public RequestLogRecord[] newArray(int size) {
            return new RequestLogRecord[size];
        }

        @Override
        public RequestLogRecord createFromParcel(@NonNull android.os.Parcel in) {
            return new RequestLogRecord(in);
        }
    };

    /**
     * A builder for {@link RequestLogRecord}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder extends BaseBuilder {

        private @NonNull List<ContentValues> mRows;
        private long mRequestId;
        private long mTimeMillis;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * A List of rows, each containing a {@link ContentValues}.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setRows(@NonNull List<ContentValues> value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mRows = value;
            return this;
        }

        /** @see #setRows */
        @DataClass.Generated.Member
        public @NonNull Builder addRow(@NonNull ContentValues value) {
            if (mRows == null) setRows(new java.util.ArrayList<>());
            mRows.add(value);
            return this;
        }

        /**
         * Internal id for the RequestLogRecord.
         *
         * @hide
         */
        @DataClass.Generated.Member
        public @NonNull Builder setRequestId(long value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mRequestId = value;
            return this;
        }

        /**
         * Time of the request in milliseconds
         *
         * @hide
         */
        @DataClass.Generated.Member
        @Override
        public @NonNull Builder setTimeMillis(long value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mTimeMillis = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull RequestLogRecord build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x8; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mRows = Collections.emptyList();
            }
            if ((mBuilderFieldsSet & 0x2) == 0) {
                mRequestId = 0;
            }
            if ((mBuilderFieldsSet & 0x4) == 0) {
                mTimeMillis = 0;
            }
            RequestLogRecord o = new RequestLogRecord(
                    mRows,
                    mRequestId,
                    mTimeMillis);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x8) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @DataClass.Generated(
            time = 1698962042612L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/RequestLogRecord.java",
            inputSignatures = " @com.android.ondevicepersonalization.internal.util.DataClass.PluralOf(\"row\") @android.annotation.NonNull java.util.List<android.content.ContentValues> mRows\nprivate  long mRequestId\nprivate  long mTimeMillis\npublic @android.annotation.NonNull java.time.Instant getTime()\nclass RequestLogRecord extends java.lang.Object implements [android.os.Parcelable]\npublic abstract  android.adservices.ondevicepersonalization.RequestLogRecord.Builder setTimeMillis(long)\nclass BaseBuilder extends java.lang.Object implements []\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)\npublic abstract  android.adservices.ondevicepersonalization.RequestLogRecord.Builder setTimeMillis(long)\nclass BaseBuilder extends java.lang.Object implements []")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
