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
package com.android.internal.widget.remotecompose.core.operations;

import android.annotation.NonNull;

import com.android.internal.widget.remotecompose.core.Operation;
import com.android.internal.widget.remotecompose.core.Operations;
import com.android.internal.widget.remotecompose.core.PaintContext;
import com.android.internal.widget.remotecompose.core.WireBuffer;
import com.android.internal.widget.remotecompose.core.documentation.DocumentationBuilder;
import com.android.internal.widget.remotecompose.core.documentation.DocumentedOperation;
import com.android.internal.widget.remotecompose.core.serialize.MapSerializer;

import java.util.List;

/** Support clip with a rectangle */
public class ClipRect extends DrawBase4 {
    private static final int OP_CODE = Operations.CLIP_RECT;
    private static final String CLASS_NAME = "ClipRect";

    /**
     * Read this operation and add it to the list of operations
     *
     * @param buffer the buffer to read
     * @param operations the list of operations that will be added to
     */
    public static void read(@NonNull WireBuffer buffer, @NonNull List<Operation> operations) {
        Maker m = ClipRect::new;
        read(m, buffer, operations);
    }

    /**
     * The OP_CODE for this command
     *
     * @return the opcode
     */
    public static int id() {
        return OP_CODE;
    }

    /**
     * The name of the class
     *
     * @return the name
     */
    @NonNull
    public static String name() {
        return CLASS_NAME;
    }

    @Override
    protected void write(@NonNull WireBuffer buffer, float v1, float v2, float v3, float v4) {
        apply(buffer, v1, v2, v3, v4);
    }

    /**
     * Populate the documentation with a description of this operation
     *
     * @param doc to append the description to.
     */
    public static void documentation(@NonNull DocumentationBuilder doc) {
        doc.operation("Expressions Operations", OP_CODE, CLASS_NAME)
                .description("Intersect the current clip with rectangle")
                .field(
                        DocumentedOperation.FLOAT,
                        "left",
                        "The left side of the rectangle to intersect with the current clip")
                .field(
                        DocumentedOperation.FLOAT,
                        "top",
                        "The top of the rectangle to intersect with the current clip")
                .field(
                        DocumentedOperation.FLOAT,
                        "right",
                        "The right side of the rectangle to intersect with the current clip")
                .field(
                        DocumentedOperation.FLOAT,
                        "bottom",
                        "The bottom of the rectangle to intersect with the current clip");
    }

    public ClipRect(float left, float top, float right, float bottom) {
        super(left, top, right, bottom);
        mName = CLASS_NAME;
    }

    @Override
    public void paint(@NonNull PaintContext context) {
        context.clipRect(mX1, mY1, mX2, mY2);
    }

    /**
     * Writes out the clipRect to the buffer
     *
     * @param buffer buffer to write to
     * @param x1 start x of DrawOval
     * @param y1 start y of the DrawOval
     * @param x2 end x of the DrawOval
     * @param y2 end y of the DrawOval
     */
    public static void apply(@NonNull WireBuffer buffer, float x1, float y1, float x2, float y2) {
        write(buffer, OP_CODE, x1, y1, x2, y2);
    }

    @Override
    public void serialize(MapSerializer serializer) {
        serialize(serializer, "left", "top", "right", "bottom").addType(CLASS_NAME);
    }
}
