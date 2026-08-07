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

package org.omnione.did.ca.ui.qr.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ScanDimOverlayView extends View {

    private static final int DIM_COLOR = 0x80000000;

    private static final float HOLE_SIZE_DP = 220f;
    private static final float BRACKET_LENGTH_DP = 44f;
    private static final float BRACKET_STROKE_DP = 2f;
    private static final float BRACKET_RADIUS_DP = 6f;

    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bracketPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path dimPath = new Path();
    private final Path bracketPath = new Path();

    private final float holeSizePx;
    private final float bracketLengthPx;
    private final float bracketRadiusPx;

    public ScanDimOverlayView(@NonNull Context context) {
        this(context, null);
    }

    public ScanDimOverlayView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        float density = context.getResources().getDisplayMetrics().density;
        holeSizePx = HOLE_SIZE_DP * density;
        bracketLengthPx = BRACKET_LENGTH_DP * density;
        bracketRadiusPx = BRACKET_RADIUS_DP * density;
        float bracketStrokePx = BRACKET_STROKE_DP * density;

        dimPaint.setColor(DIM_COLOR);
        dimPaint.setStyle(Paint.Style.FILL);

        bracketPaint.setColor(
                context.getResources().getColor(
                        org.omnione.did.ca.R.color.qr_scan_bracket, null));
        bracketPaint.setStyle(Paint.Style.STROKE);
        bracketPaint.setStrokeWidth(bracketStrokePx);
        bracketPaint.setStrokeCap(Paint.Cap.BUTT);
        bracketPaint.setStrokeJoin(Paint.Join.MITER);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float cx = w / 2f;
        float cy = h / 2f;
        float half = holeSizePx / 2f;
        float l = cx - half;
        float t = cy - half;
        float r = cx + half;
        float b = cy + half;

        dimPath.rewind();
        dimPath.setFillType(Path.FillType.EVEN_ODD);
        dimPath.addRect(0f, 0f, w, h, Path.Direction.CW);
        dimPath.addRect(l, t, r, b, Path.Direction.CW);
        canvas.drawPath(dimPath, dimPaint);

        drawBracket(canvas, l, t, +1f, +1f);
        drawBracket(canvas, r, t, -1f, +1f);
        drawBracket(canvas, l, b, +1f, -1f);
        drawBracket(canvas, r, b, -1f, -1f);
    }

    private void drawBracket(@NonNull Canvas canvas,
                             float cornerX, float cornerY,
                             float dirX, float dirY) {
        bracketPath.rewind();

        bracketPath.moveTo(cornerX, cornerY + dirY * bracketLengthPx);
        bracketPath.lineTo(cornerX, cornerY + dirY * bracketRadiusPx);
        bracketPath.quadTo(cornerX, cornerY,
                cornerX + dirX * bracketRadiusPx, cornerY);
        bracketPath.lineTo(cornerX + dirX * bracketLengthPx, cornerY);
        canvas.drawPath(bracketPath, bracketPaint);
    }
}
