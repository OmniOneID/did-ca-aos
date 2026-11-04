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

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;

import org.omnione.did.ca.R;

public class NoticeDialogFragment extends CenterDialogFragment {

    public interface OnConfirmed {
        void onConfirmed();
    }

    private static final String ARG_TITLE = "title";
    private static final String ARG_MESSAGE = "message";
    private static final String ARG_BUTTON = "button";

    private static final String TAG = "NoticeDialogFragment";

    @Nullable
    private OnConfirmed onConfirmed;

    @Nullable
    private Runnable onLink;

    @StringRes
    private int linkLabelRes;

    @Nullable
    private Runnable onCancelled;

    @NonNull
    public static NoticeDialogFragment newInstance(@NonNull CharSequence title,
                                                   @NonNull CharSequence message,
                                                   @NonNull CharSequence buttonLabel) {
        NoticeDialogFragment f = new NoticeDialogFragment();
        Bundle args = new Bundle();
        args.putCharSequence(ARG_TITLE, title);
        args.putCharSequence(ARG_MESSAGE, message);
        args.putCharSequence(ARG_BUTTON, buttonLabel);
        f.setArguments(args);
        return f;
    }

    @NonNull
    public static NoticeDialogFragment newInstance(@NonNull android.content.Context ctx,
                                                   @StringRes int titleRes,
                                                   @StringRes int messageRes,
                                                   @StringRes int buttonRes) {
        return newInstance(ctx.getText(titleRes), ctx.getText(messageRes), ctx.getText(buttonRes));
    }

    public NoticeDialogFragment setOnConfirmed(@Nullable OnConfirmed onConfirmed) {
        this.onConfirmed = onConfirmed;
        return this;
    }

    public NoticeDialogFragment setLink(@StringRes int labelRes, @NonNull Runnable onLink) {
        this.linkLabelRes = labelRes;
        this.onLink = onLink;
        return this;
    }

    public NoticeDialogFragment setOnCancelled(@Nullable Runnable onCancelled) {
        this.onCancelled = onCancelled;
        return this;
    }

    @Override
    public void onCancel(@NonNull android.content.DialogInterface dialog) {
        super.onCancel(dialog);
        if (onCancelled != null) onCancelled.run();
    }

    public void show(@NonNull FragmentManager fm) {
        show(fm, TAG);
    }

    public static void dismissRestored(@NonNull FragmentManager fm) {
        androidx.fragment.app.Fragment restored = fm.findFragmentByTag(TAG);
        if (restored instanceof androidx.fragment.app.DialogFragment) {
            ((androidx.fragment.app.DialogFragment) restored).dismissAllowingStateLoss();
        }
    }

    @Override
    protected int getContentLayoutRes() {
        return R.layout.dialog_notice_content;
    }

    @Override
    protected void onContentInflated(@NonNull View card) {
        Bundle args = requireArguments();
        ((TextView) card.findViewById(R.id.dialogTitle))
                .setText(args.getCharSequence(ARG_TITLE));
        ((TextView) card.findViewById(R.id.dialogMessage))
                .setText(args.getCharSequence(ARG_MESSAGE));

        MaterialButton button = card.findViewById(R.id.dialogButton);
        button.setText(args.getCharSequence(ARG_BUTTON));
        button.setOnClickListener(v -> {
            if (onConfirmed != null) onConfirmed.onConfirmed();
            dismissAllowingStateLoss();
        });

        MaterialButton link = card.findViewById(R.id.dialogLink);
        if (onLink == null) {
            link.setVisibility(View.GONE);
        } else {
            link.setVisibility(View.VISIBLE);
            link.setText(linkLabelRes);
            link.setOnClickListener(v -> {
                Runnable action = onLink;
                dismissAllowingStateLoss();
                action.run();
            });
        }
    }
}
