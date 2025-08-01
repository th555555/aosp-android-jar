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
import android.os.Parcelable;

import com.android.ondevicepersonalization.internal.util.AnnotationValidations;
import com.android.ondevicepersonalization.internal.util.DataClass;

import java.util.Collections;
import java.util.List;

/**
 * Information returned by
 * {@link IsolatedWorker#onExecute(ExecuteInput, android.os.OutcomeReceiver)}
 * that is used in a subesequent call to
 * {@link IsolatedWorker#onRender(RenderInput, android.os.OutcomeReceiver)} to identify the
 * content to be displayed in a single {@link android.view.View}.
 *
 */
@DataClass(genBuilder = true, genEqualsHashCode = true)
public final class RenderingConfig implements Parcelable {
    /**
     * A List of keys in the REMOTE_DATA
     * {@link IsolatedService#getRemoteData(RequestToken)}
     * table that identify the content to be rendered.
     **/
    @DataClass.PluralOf("key")
    @NonNull private List<String> mKeys = Collections.emptyList();



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/RenderingConfig.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ RenderingConfig(
            @NonNull List<String> keys) {
        this.mKeys = keys;
        AnnotationValidations.validate(
                NonNull.class, null, mKeys);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * A List of keys in the REMOTE_DATA
     * {@link IsolatedService#getRemoteData(RequestToken)}
     * table that identify the content to be rendered.
     */
    @DataClass.Generated.Member
    public @NonNull List<String> getKeys() {
        return mKeys;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(RenderingConfig other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        RenderingConfig that = (RenderingConfig) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Objects.equals(mKeys, that.mKeys);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mKeys);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeStringList(mKeys);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ RenderingConfig(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        List<String> keys = new java.util.ArrayList<>();
        in.readStringList(keys);

        this.mKeys = keys;
        AnnotationValidations.validate(
                NonNull.class, null, mKeys);

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<RenderingConfig> CREATOR
            = new Parcelable.Creator<RenderingConfig>() {
        @Override
        public RenderingConfig[] newArray(int size) {
            return new RenderingConfig[size];
        }

        @Override
        public RenderingConfig createFromParcel(@NonNull android.os.Parcel in) {
            return new RenderingConfig(in);
        }
    };

    /**
     * A builder for {@link RenderingConfig}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @NonNull List<String> mKeys;

        private long mBuilderFieldsSet = 0L;

        public Builder() {
        }

        /**
         * A List of keys in the REMOTE_DATA
         * {@link IsolatedService#getRemoteData(RequestToken)}
         * table that identify the content to be rendered.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setKeys(@NonNull List<String> value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mKeys = value;
            return this;
        }

        /** @see #setKeys */
        @DataClass.Generated.Member
        public @NonNull Builder addKey(@NonNull String value) {
            if (mKeys == null) setKeys(new java.util.ArrayList<>());
            mKeys.add(value);
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull RenderingConfig build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2; // Mark builder used

            if ((mBuilderFieldsSet & 0x1) == 0) {
                mKeys = Collections.emptyList();
            }
            RenderingConfig o = new RenderingConfig(
                    mKeys);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x2) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @DataClass.Generated(
            time = 1697132616124L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/modules/OnDevicePersonalization/framework/java/android/adservices/ondevicepersonalization/RenderingConfig.java",
            inputSignatures = "private @com.android.ondevicepersonalization.internal.util.DataClass.PluralOf(\"key\") @android.annotation.NonNull java.util.List<java.lang.String> mKeys\nclass RenderingConfig extends java.lang.Object implements [android.os.Parcelable]\n@com.android.ondevicepersonalization.internal.util.DataClass(genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
