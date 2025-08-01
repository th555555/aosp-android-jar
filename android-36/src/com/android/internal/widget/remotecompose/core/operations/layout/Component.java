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
package com.android.internal.widget.remotecompose.core.operations.layout;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.internal.widget.remotecompose.core.CoreDocument;
import com.android.internal.widget.remotecompose.core.Operation;
import com.android.internal.widget.remotecompose.core.PaintContext;
import com.android.internal.widget.remotecompose.core.PaintOperation;
import com.android.internal.widget.remotecompose.core.RemoteContext;
import com.android.internal.widget.remotecompose.core.SerializableToString;
import com.android.internal.widget.remotecompose.core.TouchListener;
import com.android.internal.widget.remotecompose.core.VariableSupport;
import com.android.internal.widget.remotecompose.core.WireBuffer;
import com.android.internal.widget.remotecompose.core.operations.BitmapData;
import com.android.internal.widget.remotecompose.core.operations.ComponentValue;
import com.android.internal.widget.remotecompose.core.operations.TextData;
import com.android.internal.widget.remotecompose.core.operations.TouchExpression;
import com.android.internal.widget.remotecompose.core.operations.layout.animation.AnimateMeasure;
import com.android.internal.widget.remotecompose.core.operations.layout.animation.AnimationSpec;
import com.android.internal.widget.remotecompose.core.operations.layout.measure.ComponentMeasure;
import com.android.internal.widget.remotecompose.core.operations.layout.measure.Measurable;
import com.android.internal.widget.remotecompose.core.operations.layout.measure.MeasurePass;
import com.android.internal.widget.remotecompose.core.operations.paint.PaintBundle;
import com.android.internal.widget.remotecompose.core.operations.utilities.StringSerializer;
import com.android.internal.widget.remotecompose.core.serialize.MapSerializer;
import com.android.internal.widget.remotecompose.core.serialize.Serializable;
import com.android.internal.widget.remotecompose.core.serialize.SerializeTags;

import java.util.ArrayList;
import java.util.HashSet;

/** Generic Component class */
public class Component extends PaintOperation
        implements Container, Measurable, SerializableToString, Serializable {

    private static final boolean DEBUG = false;

    protected int mComponentId = -1;
    protected float mX;
    protected float mY;
    protected float mWidth;
    protected float mHeight;
    @Nullable protected Component mParent;
    protected int mAnimationId = -1;
    public int mVisibility = Visibility.VISIBLE;
    public int mScheduledVisibility = Visibility.VISIBLE;
    @NonNull public ArrayList<Operation> mList = new ArrayList<>();
    public PaintOperation mPreTranslate; // todo, can we initialize this here and make it NonNull?
    public boolean mNeedsMeasure = true;
    public boolean mNeedsRepaint = false;
    @Nullable public AnimateMeasure mAnimateMeasure;
    @NonNull public AnimationSpec mAnimationSpec = AnimationSpec.DEFAULT;
    public boolean mFirstLayout = true;
    @NonNull PaintBundle mPaint = new PaintBundle();
    @NonNull protected HashSet<ComponentValue> mComponentValues = new HashSet<>();

    protected float mZIndex = 0f;

    private boolean mNeedsBoundsAnimation = false;

    /** Mark the component as needing a bounds animation pass */
    public void markNeedsBoundsAnimation() {
        mNeedsBoundsAnimation = true;
        if (mParent != null && !mParent.mNeedsBoundsAnimation) {
            mParent.markNeedsBoundsAnimation();
        }
    }

    /** Clear the bounds animation pass flag */
    public void clearNeedsBoundsAnimation() {
        mNeedsBoundsAnimation = false;
    }

    /**
     * True if needs a bounds animation
     *
     * @return true if needs a bounds animation pass
     */
    public boolean needsBoundsAnimation() {
        return mNeedsBoundsAnimation;
    }

    public float getZIndex() {
        return mZIndex;
    }

    @NonNull
    public ArrayList<Operation> getList() {
        return mList;
    }

    public float getX() {
        return mX;
    }

    public float getY() {
        return mY;
    }

    public float getWidth() {
        return mWidth;
    }

    public float getHeight() {
        return mHeight;
    }

    public int getComponentId() {
        return mComponentId;
    }

    public int getAnimationId() {
        return mAnimationId;
    }

    @Nullable
    public Component getParent() {
        return mParent;
    }

    public void setX(float value) {
        mX = value;
    }

    public void setY(float value) {
        mY = value;
    }

    public void setWidth(float value) {
        mWidth = value;
    }

    public void setHeight(float value) {
        mHeight = value;
    }

    @Override
    public void apply(@NonNull RemoteContext context) {
        for (Operation op : mList) {
            if (op instanceof VariableSupport && op.isDirty()) {
                op.markNotDirty();
                ((VariableSupport) op).updateVariables(context);
            }
        }
        super.apply(context);
    }

    /**
     * Utility function to update variables referencing this component dimensions
     *
     * @param context the current context
     */
    private void updateComponentValues(@NonNull RemoteContext context) {
        if (DEBUG) {
            System.out.println(
                    "UPDATE COMPONENT VALUES ("
                            + mComponentValues.size()
                            + ") FOR "
                            + mComponentId);
        }
        for (ComponentValue v : mComponentValues) {
            if (context.getMode() == RemoteContext.ContextMode.DATA) {
                context.loadFloat(v.getValueId(), 1f);
            } else {
                switch (v.getType()) {
                    case ComponentValue.WIDTH:
                        context.loadFloat(v.getValueId(), mWidth);
                        if (DEBUG) {
                            System.out.println(
                                    "Updating WIDTH for " + mComponentId + " to " + mWidth);
                        }
                        break;
                    case ComponentValue.HEIGHT:
                        context.loadFloat(v.getValueId(), mHeight);
                        if (DEBUG) {
                            System.out.println(
                                    "Updating HEIGHT for " + mComponentId + " to " + mHeight);
                        }
                        break;
                }
            }
        }
    }

    public void setComponentId(int id) {
        mComponentId = id;
    }

    public void setAnimationId(int id) {
        mAnimationId = id;
    }

    public Component(
            @Nullable Component parent,
            int componentId,
            int animationId,
            float x,
            float y,
            float width,
            float height) {
        this.mComponentId = componentId;
        this.mX = x;
        this.mY = y;
        this.mWidth = width;
        this.mHeight = height;
        this.mParent = parent;
        this.mAnimationId = animationId;
    }

    public Component(
            int componentId,
            float x,
            float y,
            float width,
            float height,
            @Nullable Component parent) {
        this(parent, componentId, -1, x, y, width, height);
    }

    public Component(@NonNull Component component) {
        this(
                component.mParent,
                component.mComponentId,
                component.mAnimationId,
                component.mX,
                component.mY,
                component.mWidth,
                component.mHeight);
        mList.addAll(component.mList);
        finalizeCreation();
    }

    /** Callback on component creation TODO: replace with inflate() */
    public void finalizeCreation() {
        for (Operation op : mList) {
            if (op instanceof Component) {
                ((Component) op).mParent = this;
            }
            if (op instanceof AnimationSpec) {
                mAnimationSpec = (AnimationSpec) op;
                mAnimationId = mAnimationSpec.getAnimationId();
            }
        }
    }

    @Override
    public boolean needsMeasure() {
        return mNeedsMeasure;
    }

    public void setParent(@Nullable Component parent) {
        mParent = parent;
    }

    /**
     * This traverses the component tree and make sure to update variables referencing the component
     * dimensions as needed.
     *
     * @param context the current context
     */
    public void updateVariables(@NonNull RemoteContext context) {
        Component prev = context.mLastComponent;
        context.mLastComponent = this;

        if (!mComponentValues.isEmpty()) {
            updateComponentValues(context);
        }
        context.mLastComponent = prev;
    }

    /**
     * Add a component value to the component
     *
     * @param v
     */
    public void addComponentValue(@NonNull ComponentValue v) {
        mComponentValues.add(v);
    }

    /**
     * Returns the min intrinsic width of the layout
     *
     * @param context
     * @return the width in pixels
     */
    public float minIntrinsicWidth(@Nullable RemoteContext context) {
        return getWidth();
    }

    /**
     * Returns the max intrinsic width of the layout
     *
     * @param context
     * @return the width in pixels
     */
    public float maxIntrinsicWidth(@Nullable RemoteContext context) {
        return getWidth();
    }

    /**
     * Returns the min intrinsic height of the layout
     *
     * @param context
     * @return the height in pixels
     */
    public float minIntrinsicHeight(@Nullable RemoteContext context) {
        return getHeight();
    }

    /**
     * Returns the max intrinsic height of the layout
     *
     * @param context
     * @return the height in pixels
     */
    public float maxIntrinsicHeight(@Nullable RemoteContext context) {
        return getHeight();
    }

    /**
     * This function is called after a component is created, with its mList initialized. This let
     * the component a chance to do some post-initialization work on its children ops.
     */
    public void inflate() {
        for (Operation op : mList) {
            if (op instanceof TouchListener) {
                // Make sure to set the component of a touch expression that belongs to us!
                TouchListener touchListener = (TouchListener) op;
                touchListener.setComponent(this);
            }
        }
    }

    protected AnimationSpec getAnimationSpec() {
        return mAnimationSpec;
    }

    protected void setAnimationSpec(@NonNull AnimationSpec animationSpec) {
        mAnimationSpec = animationSpec;
    }

    /**
     * If the component contains variables beside mList, make sure to register them here
     *
     * @param context
     */
    public void registerVariables(RemoteContext context) {
        // Nothing here
    }

    public static class Visibility {

        public static final int GONE = 0;
        public static final int VISIBLE = 1;
        public static final int INVISIBLE = 2;
        public static final int OVERRIDE_GONE = 16;
        public static final int OVERRIDE_VISIBLE = 32;
        public static final int OVERRIDE_INVISIBLE = 64;
        public static final int CLEAR_OVERRIDE = 128;

        /**
         * Returns a string representation of the field
         *
         * @param value
         * @return
         */
        public static String toString(int value) {
            switch (value) {
                case GONE:
                    return "GONE";
                case VISIBLE:
                    return "VISIBLE";
                case INVISIBLE:
                    return "INVISIBLE";
            }
            if ((value >> 4) > 0) {
                if ((value & OVERRIDE_GONE) == OVERRIDE_GONE) {
                    return "OVERRIDE_GONE";
                }
                if ((value & OVERRIDE_VISIBLE) == OVERRIDE_VISIBLE) {
                    return "OVERRIDE_VISIBLE";
                }
                if ((value & OVERRIDE_INVISIBLE) == OVERRIDE_INVISIBLE) {
                    return "OVERRIDE_INVISIBLE";
                }
            }
            return "" + value;
        }

        /**
         * Returns true if gone
         *
         * @param value
         * @return
         */
        public static boolean isGone(int value) {
            if ((value >> 4) > 0) {
                return (value & OVERRIDE_GONE) == OVERRIDE_GONE;
            }
            return value == GONE;
        }

        /**
         * Returns true if visible
         *
         * @param value
         * @return
         */
        public static boolean isVisible(int value) {
            if ((value >> 4) > 0) {
                return (value & OVERRIDE_VISIBLE) == OVERRIDE_VISIBLE;
            }
            return value == VISIBLE;
        }

        /**
         * Returns true if invisible
         *
         * @param value
         * @return
         */
        public static boolean isInvisible(int value) {
            if ((value >> 4) > 0) {
                return (value & OVERRIDE_INVISIBLE) == OVERRIDE_INVISIBLE;
            }
            return value == INVISIBLE;
        }

        /**
         * Returns true if the field has an override
         *
         * @param value
         * @return
         */
        public static boolean hasOverride(int value) {
            return (value >> 4) > 0;
        }

        /**
         * Clear the override values
         *
         * @param value
         * @return
         */
        public static int clearOverride(int value) {
            return value & 15;
        }

        /**
         * Add an override value
         *
         * @param value
         * @param visibility
         * @return
         */
        public static int add(int value, int visibility) {
            int v = value & 15;
            v += visibility;
            if ((v & CLEAR_OVERRIDE) == CLEAR_OVERRIDE) {
                v = v & 15;
            }
            return v;
        }
    }

    /**
     * Returns true if the component is visible
     *
     * @return
     */
    public boolean isVisible() {
        if (mParent == null || !Visibility.isVisible(mVisibility)) {
            return Visibility.isVisible(mVisibility);
        }
        return mParent.isVisible();
    }

    /**
     * Returns true if the component is gone
     *
     * @return
     */
    public boolean isGone() {
        return Visibility.isGone(mVisibility);
    }

    /**
     * Returns true if the component is invisible
     *
     * @return
     */
    public boolean isInvisible() {
        return Visibility.isInvisible(mVisibility);
    }

    /**
     * Set the visibility of the component
     *
     * @param visibility can be VISIBLE, INVISIBLE or GONE
     */
    public void setVisibility(int visibility) {
        if (visibility != mVisibility || visibility != mScheduledVisibility) {
            mScheduledVisibility = visibility;
            invalidateMeasure();
        }
    }

    @Override
    public boolean suitableForTransition(@NonNull Operation o) {
        if (!(o instanceof Component)) {
            return false;
        }
        if (mList.size() != ((Component) o).mList.size()) {
            return false;
        }
        for (int i = 0; i < mList.size(); i++) {
            Operation o1 = mList.get(i);
            Operation o2 = ((Component) o).mList.get(i);
            if (o1 instanceof Component && o2 instanceof Component) {
                if (!((Component) o1).suitableForTransition(o2)) {
                    return false;
                }
            }
            if (o1 instanceof PaintOperation && !((PaintOperation) o1).suitableForTransition(o2)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void measure(
            @NonNull PaintContext context,
            float minWidth,
            float maxWidth,
            float minHeight,
            float maxHeight,
            @NonNull MeasurePass measure) {
        ComponentMeasure m = measure.get(this);
        m.setW(mWidth);
        m.setH(mHeight);
    }

    @Override
    public void layout(@NonNull RemoteContext context, @NonNull MeasurePass measure) {
        ComponentMeasure m = measure.get(this);
        if (!mFirstLayout
                && context.isAnimationEnabled()
                && mAnimationSpec.isAnimationEnabled()
                && !(this instanceof LayoutComponentContent)) {
            if (mAnimateMeasure == null) {
                ComponentMeasure origin =
                        new ComponentMeasure(mComponentId, mX, mY, mWidth, mHeight, mVisibility);
                ComponentMeasure target =
                        new ComponentMeasure(
                                mComponentId,
                                m.getX(),
                                m.getY(),
                                m.getW(),
                                m.getH(),
                                m.getVisibility());
                if (!target.same(origin)) {
                    mAnimateMeasure =
                            new AnimateMeasure(
                                    context.currentTime,
                                    this,
                                    origin,
                                    target,
                                    mAnimationSpec.getMotionDuration(),
                                    mAnimationSpec.getVisibilityDuration(),
                                    mAnimationSpec.getEnterAnimation(),
                                    mAnimationSpec.getExitAnimation(),
                                    mAnimationSpec.getMotionEasingType(),
                                    mAnimationSpec.getVisibilityEasingType());
                }
            } else {
                mAnimateMeasure.updateTarget(m, context.currentTime);
            }
        } else {
            mVisibility = m.getVisibility();
        }
        if (mAnimateMeasure == null) {
            setWidth(m.getW());
            setHeight(m.getH());
            setLayoutPosition(m.getX(), m.getY());
            updateComponentValues(context);
            clearNeedsBoundsAnimation();
        } else {
            mAnimateMeasure.apply(context);
            updateComponentValues(context);
            markNeedsBoundsAnimation();
        }
        mFirstLayout = false;
    }

    /**
     * Animate the bounds of the component as needed
     *
     * @param context
     */
    public void animatingBounds(@NonNull RemoteContext context) {
        if (mAnimateMeasure != null) {
            mAnimateMeasure.apply(context);
            updateComponentValues(context);
        } else {
            clearNeedsBoundsAnimation();
        }
        for (Operation op : mList) {
            if (op instanceof Measurable) {
                Measurable m = (Measurable) op;
                m.animatingBounds(context);
            }
        }
    }

    @NonNull public float[] locationInWindow = new float[2];

    /**
     * Hit detection -- returns true if the point (x, y) is inside the component
     *
     * @param x
     * @param y
     * @return
     */
    public boolean contains(float x, float y) {
        locationInWindow[0] = 0f;
        locationInWindow[1] = 0f;
        getLocationInWindow(locationInWindow);
        float lx1 = locationInWindow[0];
        float lx2 = lx1 + mWidth;
        float ly1 = locationInWindow[1];
        float ly2 = ly1 + mHeight;
        return x >= lx1 && x < lx2 && y >= ly1 && y < ly2;
    }

    /**
     * Returns the horizontal scroll value of the content of this component
     *
     * @return 0 if no scroll
     */
    public float getScrollX() {
        return 0;
    }

    /**
     * Returns the vertical scroll value of the content of this component
     *
     * @return 0 if no scroll
     */
    public float getScrollY() {
        return 0;
    }

    /**
     * Click handler
     *
     * @param context
     * @param document
     * @param x x location on screen or -1 if unconditional click
     * @param y y location on screen or -1 if unconditional click
     */
    public void onClick(
            @NonNull RemoteContext context, @NonNull CoreDocument document, float x, float y) {
        boolean isUnconditional = x == -1 & y == -1;
        if (!isUnconditional && !contains(x, y)) {
            return;
        }
        float cx = isUnconditional ? -1 : x - getScrollX();
        float cy = isUnconditional ? -1 : y - getScrollY();
        for (Operation op : mList) {
            if (op instanceof Component) {
                ((Component) op).onClick(context, document, cx, cy);
            }
            if (op instanceof ClickHandler) {
                ((ClickHandler) op).onClick(context, document, this, cx, cy);
            }
        }
    }

    /**
     * Touch down handler
     *
     * @param context
     * @param document
     * @param x
     * @param y
     */
    public void onTouchDown(RemoteContext context, CoreDocument document, float x, float y) {
        if (!contains(x, y)) {
            return;
        }
        float cx = x - getScrollX();
        float cy = y - getScrollY();
        for (Operation op : mList) {
            if (op instanceof Component) {
                ((Component) op).onTouchDown(context, document, cx, cy);
            }
            if (op instanceof TouchHandler) {
                ((TouchHandler) op).onTouchDown(context, document, this, cx, cy);
            }
            if (op instanceof TouchExpression) {
                TouchExpression touchExpression = (TouchExpression) op;
                touchExpression.updateVariables(context);
                touchExpression.touchDown(context, cx, cy);
                document.appliedTouchOperation(this);
            }
        }
    }

    /**
     * Touch Up handler
     *
     * @param context
     * @param document
     * @param x
     * @param y
     * @param dx
     * @param dy
     * @param force
     */
    public void onTouchUp(
            RemoteContext context,
            CoreDocument document,
            float x,
            float y,
            float dx,
            float dy,
            boolean force) {
        if (!force && !contains(x, y)) {
            return;
        }
        float cx = x - getScrollX();
        float cy = y - getScrollY();
        for (Operation op : mList) {
            if (op instanceof Component) {
                ((Component) op).onTouchUp(context, document, cx, cy, dx, dy, force);
            }
            if (op instanceof TouchHandler) {
                ((TouchHandler) op).onTouchUp(context, document, this, cx, cy, dx, dy);
            }
            if (op instanceof TouchExpression) {
                TouchExpression touchExpression = (TouchExpression) op;
                touchExpression.updateVariables(context);
                touchExpression.touchUp(context, cx, cy, dx, dy);
            }
        }
    }

    /**
     * Touch Cancel handler
     *
     * @param context
     * @param document
     * @param x
     * @param y
     * @param force
     */
    public void onTouchCancel(
            RemoteContext context, CoreDocument document, float x, float y, boolean force) {
        if (!force && !contains(x, y)) {
            return;
        }
        float cx = x - getScrollX();
        float cy = y - getScrollY();
        for (Operation op : mList) {
            if (op instanceof Component) {
                ((Component) op).onTouchCancel(context, document, cx, cy, force);
            }
            if (op instanceof TouchHandler) {
                ((TouchHandler) op).onTouchCancel(context, document, this, cx, cy);
            }
            if (op instanceof TouchExpression) {
                TouchExpression touchExpression = (TouchExpression) op;
                touchExpression.updateVariables(context);
                touchExpression.touchUp(context, cx, cy, 0, 0);
            }
        }
    }

    /**
     * Touch Drag handler
     *
     * @param context
     * @param document
     * @param x
     * @param y
     * @param force
     */
    public void onTouchDrag(
            RemoteContext context, CoreDocument document, float x, float y, boolean force) {
        if (!force && !contains(x, y)) {
            return;
        }
        float cx = x - getScrollX();
        float cy = y - getScrollY();
        for (Operation op : mList) {
            if (op instanceof Component) {
                ((Component) op).onTouchDrag(context, document, cx, cy, force);
            }
            if (op instanceof TouchHandler) {
                ((TouchHandler) op).onTouchDrag(context, document, this, cx, cy);
            }
            if (op instanceof TouchExpression) {
                TouchExpression touchExpression = (TouchExpression) op;
                touchExpression.updateVariables(context);
                touchExpression.touchDrag(context, x, y);
            }
        }
    }

    /**
     * Returns the location of the component relative to the root component
     *
     * @param value a 2 dimension float array that will receive the horizontal and vertical position
     *     of the component.
     * @param forSelf whether the location is for this container or a child, relevant for scrollable
     *     items.
     */
    public void getLocationInWindow(@NonNull float[] value, boolean forSelf) {
        value[0] += mX;
        value[1] += mY;
        if (mParent != null) {
            mParent.getLocationInWindow(value, false);
        }
    }

    /**
     * Returns the location of the component relative to the root component
     *
     * @param value a 2 dimension float array that will receive the horizontal and vertical position
     *     of the component.
     */
    public void getLocationInWindow(@NonNull float[] value) {
        getLocationInWindow(value, true);
    }

    @NonNull
    @Override
    public String toString() {
        return "COMPONENT(<"
                + mComponentId
                + "> "
                + getClass().getSimpleName()
                + ") ["
                + mX
                + ","
                + mY
                + " - "
                + mWidth
                + " x "
                + mHeight
                + "] "
                + textContent()
                + " Visibility ("
                + Visibility.toString(mVisibility)
                + ") ";
    }

    @NonNull
    protected String getSerializedName() {
        return "COMPONENT";
    }

    @Override
    public void serializeToString(int indent, @NonNull StringSerializer serializer) {
        String content =
                getSerializedName()
                        + " ["
                        + mComponentId
                        + ":"
                        + mAnimationId
                        + "] = "
                        + "["
                        + mX
                        + ", "
                        + mY
                        + ", "
                        + mWidth
                        + ", "
                        + mHeight
                        + "] "
                        + Visibility.toString(mVisibility);
        //        + " [" + mNeedsMeasure + ", " + mNeedsRepaint + "]"
        serializer.append(indent, content);
    }

    @Override
    public void write(@NonNull WireBuffer buffer) {
        // nothing
    }

    /** Returns the top-level RootLayoutComponent */
    @NonNull
    public RootLayoutComponent getRoot() throws Exception {
        if (this instanceof RootLayoutComponent) {
            return (RootLayoutComponent) this;
        }
        Component p = mParent;
        while (!(p instanceof RootLayoutComponent)) {
            if (p == null) {
                throw new Exception("No RootLayoutComponent found");
            }
            p = p.mParent;
        }
        return (RootLayoutComponent) p;
    }

    @NonNull
    @Override
    public String deepToString(@NonNull String indent) {
        StringBuilder builder = new StringBuilder();
        builder.append(indent);
        builder.append(toString());
        builder.append("\n");
        String indent2 = "  " + indent;
        for (Operation op : mList) {
            builder.append(op.deepToString(indent2));
            builder.append("\n");
        }
        return builder.toString();
    }

    /**
     * Mark itself as needing to be remeasured, and walk back up the tree to mark each parents as
     * well.
     */
    public void invalidateMeasure() {
        needsRepaint();
        mNeedsMeasure = true;
        Component p = mParent;
        while (p != null) {
            p.mNeedsMeasure = true;
            p = p.mParent;
        }
    }

    /** Mark the tree as needing a repaint */
    public void needsRepaint() {
        try {
            getRoot().mNeedsRepaint = true;
        } catch (Exception e) {
            // nothing
        }
    }

    /**
     * Debugging function returning the list of child operations
     *
     * @return a formatted string with the list of operations
     */
    @NonNull
    public String content() {
        StringBuilder builder = new StringBuilder();
        for (Operation op : mList) {
            builder.append("- ");
            builder.append(op);
            builder.append("\n");
        }
        return builder.toString();
    }

    /**
     * Returns a string containing the text operations if any
     *
     * @return
     */
    @NonNull
    public String textContent() {
        StringBuilder builder = new StringBuilder();
        for (Operation ignored : mList) {
            String letter = "";
            // if (op instanceof DrawTextRun) {
            //   letter = "[" + ((DrawTextRun) op).text + "]";
            // }
            builder.append(letter);
        }
        return builder.toString();
    }

    /**
     * Utility debug function
     *
     * @param component
     * @param context
     */
    public void debugBox(@NonNull Component component, @NonNull PaintContext context) {
        float width = component.mWidth;
        float height = component.mHeight;

        context.savePaint();
        mPaint.reset();
        mPaint.setColor(0, 0, 255, 255); // Blue color
        context.applyPaint(mPaint);
        context.drawLine(0f, 0f, width, 0f);
        context.drawLine(width, 0f, width, height);
        context.drawLine(width, height, 0f, height);
        context.drawLine(0f, height, 0f, 0f);
        //        context.setColor(255, 0, 0, 255)
        //        context.drawLine(0f, 0f, width, height)
        //        context.drawLine(0f, height, width, 0f)
        context.restorePaint();
    }

    /**
     * Set the position of this component relative to its parent
     *
     * @param x horizontal position
     * @param y vertical position
     */
    public void setLayoutPosition(float x, float y) {
        this.mX = x;
        this.mY = y;
    }

    /**
     * The vertical position of this component relative to its parent
     *
     * @return
     */
    public float getTranslateX() {
        if (mParent != null) {
            return mX - mParent.mX;
        }
        return 0f;
    }

    /**
     * The horizontal position of this component relative to its parent
     *
     * @return
     */
    public float getTranslateY() {
        if (mParent != null) {
            return mY - mParent.mY;
        }
        return 0f;
    }

    /**
     * Paint the component itself.
     *
     * @param context
     */
    public void paintingComponent(@NonNull PaintContext context) {
        if (mPreTranslate != null) {
            mPreTranslate.paint(context);
        }
        Component prev = context.getContext().mLastComponent;
        context.getContext().mLastComponent = this;
        context.save();
        context.translate(mX, mY);
        if (context.isVisualDebug()) {
            debugBox(this, context);
        }
        for (Operation op : mList) {
            if (op.isDirty() && op instanceof VariableSupport) {
                ((VariableSupport) op).updateVariables(context.getContext());
                op.markNotDirty();
            }
            if (op instanceof PaintOperation) {
                ((PaintOperation) op).paint(context);
                context.getContext().incrementOpCount();
            } else {
                op.apply(context.getContext());
                context.getContext().incrementOpCount();
            }
        }
        context.restore();
        context.getContext().mLastComponent = prev;
    }

    /**
     * If animation is turned on and we need to be animated, we'll apply it.
     *
     * @param context
     * @return
     */
    public boolean applyAnimationAsNeeded(@NonNull PaintContext context) {
        if (context.isAnimationEnabled() && mAnimateMeasure != null) {
            mAnimateMeasure.paint(context);
            if (mAnimateMeasure.isDone()) {
                mAnimateMeasure = null;
                clearNeedsBoundsAnimation();
                needsRepaint();
            } else {
                markNeedsBoundsAnimation();
            }
            return true;
        }
        return false;
    }

    @Override
    public void paint(@NonNull PaintContext context) {
        if (context.isVisualDebug()) {
            context.save();
            context.translate(mX, mY);
            context.savePaint();
            mPaint.reset();
            mPaint.setColor(0, 255, 0, 255); // Green
            context.applyPaint(mPaint);
            context.drawLine(0f, 0f, mWidth, 0f);
            context.drawLine(mWidth, 0f, mWidth, mHeight);
            context.drawLine(mWidth, mHeight, 0f, mHeight);
            context.drawLine(0f, mHeight, 0f, 0f);
            mPaint.setColor(255, 0, 0, 255); // Red
            context.applyPaint(mPaint);
            context.drawLine(0f, 0f, mWidth, mHeight);
            context.drawLine(0f, mHeight, mWidth, 0f);
            context.restorePaint();
            context.restore();
        }
        if (applyAnimationAsNeeded(context)) {
            return;
        }
        if (isGone() || isInvisible()) {
            return;
        }
        paintingComponent(context);
    }

    /**
     * Extract child components
     *
     * @param components an ArrayList that will be populated by child components (if any)
     */
    public void getComponents(@NonNull ArrayList<Component> components) {
        for (Operation op : mList) {
            if (op instanceof Component) {
                components.add((Component) op);
            }
        }
    }

    /**
     * Extract child Data elements
     *
     * @param data an ArrayList that will be populated with the Data elements (if any)
     */
    public void getData(@NonNull ArrayList<Operation> data) {
        for (Operation op : mList) {
            if (op instanceof TextData || op instanceof BitmapData) {
                data.add(op);
            }
        }
    }

    /**
     * Returns the number of children components
     *
     * @return
     */
    public int getComponentCount() {
        int count = 0;
        for (Operation op : mList) {
            if (op instanceof Component) {
                count += 1 + ((Component) op).getComponentCount();
            }
        }
        return count;
    }

    /**
     * Return the id used for painting the component -- either its component id or its animation id
     * (if set)
     *
     * @return
     */
    public int getPaintId() {
        if (mAnimationId != -1) {
            return mAnimationId;
        }
        return mComponentId;
    }

    /**
     * Return true if the needsRepaint flag is set on this component
     *
     * @return
     */
    public boolean doesNeedsRepaint() {
        return mNeedsRepaint;
    }

    /**
     * Utility function to return a component from its id
     *
     * @param cid
     * @return
     */
    @Nullable
    public Component getComponent(int cid) {
        if (mComponentId == cid || mAnimationId == cid) {
            return this;
        }
        for (Operation c : mList) {
            if (c instanceof Component) {
                Component search = ((Component) c).getComponent(cid);
                if (search != null) {
                    return search;
                }
            }
        }
        return null;
    }

    @Override
    public void serialize(MapSerializer serializer) {
        serializer.addTags(SerializeTags.COMPONENT);
        serializer.addType(getSerializedName());
        serializer.add("id", mComponentId);
        serializer.add("x", mX);
        serializer.add("y", mY);
        serializer.add("width", mWidth);
        serializer.add("height", mHeight);
        serializer.add("visibility", Visibility.toString(mVisibility));
        serializer.add("list", mList);
    }

    /**
     * Return ourself or a matching modifier. Used by the semantics / accessibility layer.
     *
     * @param operationClass
     * @return
     * @param <T>
     */
    public <T> @Nullable T selfOrModifier(Class<T> operationClass) {
        if (operationClass.isInstance(this)) {
            return operationClass.cast(this);
        }

        for (Operation op : mList) {
            if (operationClass.isInstance(op)) {
                return operationClass.cast(op);
            }
        }

        return null;
    }
}
