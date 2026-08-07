/*
 * Copyright 2026 OmniOne.
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

package org.omnione.did.ca.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.omnione.did.ca.R;

public class DotSpinnerView extends View {

    private static final int DEFAULT_SIZE_DP = 48;
    private static final float DOT_RATIO = 0.18f;
    private static final int DOT_COUNT = 8;
    private static final long STEP_MS = 125L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int activeColor;
    private final int trackColor;
    private int activeIndex = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            activeIndex = (activeIndex + 1) % DOT_COUNT;
            invalidate();
            handler.postDelayed(this, STEP_MS);
        }
    };

    public DotSpinnerView(Context context) {
        this(context, null);
    }

    public DotSpinnerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DotSpinnerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        activeColor = ContextCompat.getColor(context, R.color.brand_primary);
        trackColor = ContextCompat.getColor(context, R.color.brand_gray300);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.TRANSPARENT);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultPx = dp(DEFAULT_SIZE_DP);
        int w = resolveSize(defaultPx, widthMeasureSpec);
        int h = resolveSize(defaultPx, heightMeasureSpec);
        int size = Math.min(w, h);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;

        float dotRadius = (size * DOT_RATIO) / 2f;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float ringRadius = size / 2f - dotRadius;

        for (int i = 0; i < DOT_COUNT; i++) {
            double angle = Math.toRadians(i * 45 - 90);
            float dx = (float) (Math.cos(angle) * ringRadius);
            float dy = (float) (Math.sin(angle) * ringRadius);
            paint.setColor(i == activeIndex ? activeColor : trackColor);
            canvas.drawCircle(cx + dx, cy + dy, dotRadius, paint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isInEditMode()) return;
        activeIndex = 0;
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, STEP_MS);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
