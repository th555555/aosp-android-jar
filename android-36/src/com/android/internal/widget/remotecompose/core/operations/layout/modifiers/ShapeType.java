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
package com.android.internal.widget.remotecompose.core.operations.layout.modifiers;

/** Known shapes, used for modifiers (clip/background/border) */
public class ShapeType {
    public static final int RECTANGLE = 0;
    public static final int CIRCLE = 1;
    public static final int ROUNDED_RECTANGLE = 2;

    /**
     * Returns a string representation of the value. Used during serialization.
     *
     * @param value
     * @return
     */
    public static String getString(int value) {
        switch (value) {
            case ShapeType.RECTANGLE:
                return "RECTANGLE";
            case ShapeType.CIRCLE:
                return "CIRCLE";
            case ShapeType.ROUNDED_RECTANGLE:
                return "ROUNDED_RECTANGLE";
            default:
                return "INVALID_SHAPE_TYPE[" + value + "]";
        }
    }
}
