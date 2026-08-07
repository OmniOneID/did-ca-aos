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

package org.omnione.did.ca.ui.docs.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.omnione.did.ca.R;

public class DashedDividerView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float dashWidth;
    private float dashGap;
    private float thickness;

    public DashedDividerView(Context context) {
        this(context, null);
    }

    public DashedDividerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DashedDividerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        float density = getResources().getDisplayMetrics().density;
        int defaultColor = ContextCompat.getColor(context, R.color.brand_divider);
        dashWidth = 2f * density;
        dashGap = 2f * density;
        thickness = 1f * density;

        int color = defaultColor;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DashedDividerView);
            color = a.getColor(R.styleable.DashedDividerView_dashColor, defaultColor);
            dashWidth = a.getDimension(R.styleable.DashedDividerView_dashWidth, dashWidth);
            dashGap = a.getDimension(R.styleable.DashedDividerView_dashGap, dashGap);
            thickness = a.getDimension(R.styleable.DashedDividerView_dashThickness, thickness);
            a.recycle();
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(thickness);
        paint.setPathEffect(new DashPathEffect(new float[]{dashWidth, dashGap}, 0f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float y = getHeight() / 2f;
        canvas.drawLine(0f, y, getWidth(), y, paint);
    }
}
