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

package org.omnione.did.ca.ui.pin.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import org.omnione.did.ca.R;

import java.util.ArrayList;
import java.util.List;

public class PinDotsView extends LinearLayout {

    private static final int DEFAULT_LENGTH = 6;
    private static final int DOT_WIDTH_DP = 44;
    private static final int DOT_HEIGHT_DP = 52;
    private static final int DOT_GAP_DP = 10;
    private static final int FILL_SIZE_DP = 12;
    private static final float CHAR_TEXT_SIZE_SP = 20f;

    private final List<FrameLayout> containers = new ArrayList<>();
    private final List<View> fills = new ArrayList<>();
    private final List<TextView> chars = new ArrayList<>();

    private int length = DEFAULT_LENGTH;
    private int filled = 0;
    private boolean error = false;
    private boolean masked = true;
    private String text = "";

    public PinDotsView(Context context) {
        this(context, null);
    }

    public PinDotsView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PinDotsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        init();
    }

    private void init() {
        rebuild();
    }

    public void setLength(int length) {
        if (length < 1) length = 1;
        if (this.length == length) return;
        this.length = length;
        rebuild();
    }

    private void rebuild() {
        removeAllViews();
        containers.clear();
        fills.clear();
        chars.clear();

        int gap = dp(DOT_GAP_DP);
        int width = dp(DOT_WIDTH_DP);
        int height = dp(DOT_HEIGHT_DP);
        int fillSize = dp(FILL_SIZE_DP);

        for (int i = 0; i < length; i++) {
            FrameLayout box = new FrameLayout(getContext());
            LayoutParams lp = new LayoutParams(width, height);
            if (i > 0) lp.leftMargin = gap;
            box.setLayoutParams(lp);

            View fill = new View(getContext());
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(fillSize, fillSize);
            flp.gravity = Gravity.CENTER;
            fill.setLayoutParams(flp);
            fill.setVisibility(View.INVISIBLE);

            TextView ch = new TextView(getContext());
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            clp.gravity = Gravity.CENTER;
            ch.setLayoutParams(clp);
            ch.setTextSize(TypedValue.COMPLEX_UNIT_SP, CHAR_TEXT_SIZE_SP);
            ch.setTypeface(ResourcesCompat.getFont(getContext(), R.font.pretendard_semibold));
            ch.setVisibility(View.GONE);

            box.addView(fill);
            box.addView(ch);
            addView(box);
            containers.add(box);
            fills.add(fill);
            chars.add(ch);
        }
        if (filled > length) filled = length;
        applyState();
    }

    public void setMasked(boolean masked) {
        if (this.masked == masked) return;
        this.masked = masked;
        applyState();
    }

    public void setText(@Nullable String value) {
        String next = value == null ? "" : value;
        int nextFilled = Math.min(next.length(), length);
        if (text.equals(next) && filled == nextFilled) return;
        this.text = next;
        this.filled = nextFilled;
        applyState();
    }

    public void setFilled(int filled) {
        if (filled < 0) filled = 0;
        if (filled > length) filled = length;
        if (this.filled == filled) return;
        this.filled = filled;
        applyState();
    }

    public void setError(boolean error) {
        if (this.error == error) return;
        this.error = error;
        applyState();
    }

    public int getMaxLength() {
        return length;
    }

    private void applyState() {
        int focusIndex = filled;
        for (int i = 0; i < length; i++) {
            FrameLayout box = containers.get(i);
            View fill = fills.get(i);

            int containerBg;
            if (error) {
                containerBg = R.drawable.bg_pin_dot_error;
            } else if (i == focusIndex) {
                containerBg = R.drawable.bg_pin_dot_focused;
            } else {
                containerBg = R.drawable.bg_pin_dot_default;
            }
            box.setBackgroundResource(containerBg);

            boolean show = i < filled;
            TextView ch = chars.get(i);
            if (masked) {
                ch.setVisibility(View.GONE);
                fill.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
                fill.setBackgroundResource(error
                        ? R.drawable.bg_pin_dot_filled_error
                        : R.drawable.bg_pin_dot_filled);
            } else {
                fill.setVisibility(View.INVISIBLE);
                ch.setText(show && i < text.length()
                        ? String.valueOf(text.charAt(i))
                        : "");
                ch.setTextColor(ContextCompat.getColor(getContext(),
                        error ? R.color.brand_error : R.color.brand_text_primary));
                ch.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
