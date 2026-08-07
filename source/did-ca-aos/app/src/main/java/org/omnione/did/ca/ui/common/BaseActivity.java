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

import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.omnione.did.ca.R;

public abstract class BaseActivity extends AppCompatActivity {

    @FunctionalInterface
    protected interface OnSystemBarInsets {
        void apply(Insets bars);
    }

    protected final void applySystemBarInsets(View root, OnSystemBarInsets applier) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            applier.apply(bars);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    protected static void applyTopInsetPadding(View view, int topInset) {
        view.setPadding(view.getPaddingLeft(), topInset,
                view.getPaddingRight(), view.getPaddingBottom());
    }

    protected static void applyBottomInsetPadding(View view, int basePx, int bottomInset) {
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                view.getPaddingRight(), basePx + bottomInset);
    }

    protected final void showProgress() {
        ProgressDialogFragment.showIfAbsent(getSupportFragmentManager());
    }

    protected final void dismissProgress() {
        ProgressDialogFragment.dismissIfShown(getSupportFragmentManager());
    }

    protected final int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    protected final void showErrorNotice(@StringRes int titleRes,
                                          @StringRes int messageRes,
                                          @Nullable CharSequence detail,
                                          @Nullable NoticeDialogFragment.OnConfirmed onConfirmed) {
        NoticeDialogFragment dialog = (detail != null)
                ? NoticeDialogFragment.newInstance(
                        getText(titleRes), detail, getText(R.string.common_ok))
                : NoticeDialogFragment.newInstance(
                        this, titleRes, messageRes, R.string.common_ok);
        if (onConfirmed != null) dialog.setOnConfirmed(onConfirmed);
        dialog.show(getSupportFragmentManager());
    }
}
