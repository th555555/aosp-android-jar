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

package android.util.configinfrastructure.proto;

/**
 * Thrown when there is an error parsing protobuf data.
 *
 * This is copied from frameworks/base/core/java/android/util/proto/WireTypeMismatchException.java
 * so ConfigInfra can use ProtoInputStream. Any major bugfixes in the original
 * WireTypeMismatchException should be copied here.
 *
 * @hide
 */
@android.ravenwood.annotation.RavenwoodKeepWholeClass
public class WireTypeMismatchException extends ProtoParseException {

    /**
     * Construct a WireTypeMismatchException.
     *
     * @param msg The message.
     */
    public WireTypeMismatchException(String msg) {
        super(msg);
    }
}

