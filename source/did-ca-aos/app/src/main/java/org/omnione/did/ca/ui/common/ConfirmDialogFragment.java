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

public class ConfirmDialogFragment extends CenterDialogFragment {

    public interface OnConfirmed {
        void onConfirmed();
    }

    public interface OnCancelled {
        void onCancelled();
    }

    private static final String ARG_TITLE = "title";
    private static final String ARG_MESSAGE = "message";
    private static final String ARG_CANCEL = "cancel";
    private static final String ARG_CONFIRM = "confirm";

    @Nullable
    private OnConfirmed onConfirmed;
    @Nullable
    private OnCancelled onCancelled;

    @NonNull
    public static ConfirmDialogFragment newInstance(@NonNull CharSequence title,
                                                    @NonNull CharSequence message,
                                                    @NonNull CharSequence cancelLabel,
                                                    @NonNull CharSequence confirmLabel) {
        ConfirmDialogFragment f = new ConfirmDialogFragment();
        Bundle args = new Bundle();
        args.putCharSequence(ARG_TITLE, title);
        args.putCharSequence(ARG_MESSAGE, message);
        args.putCharSequence(ARG_CANCEL, cancelLabel);
        args.putCharSequence(ARG_CONFIRM, confirmLabel);
        f.setArguments(args);
        return f;
    }

    @NonNull
    public static ConfirmDialogFragment newInstance(@NonNull android.content.Context ctx,
                                                    @StringRes int titleRes,
                                                    @StringRes int messageRes,
                                                    @StringRes int cancelRes,
                                                    @StringRes int confirmRes) {
        return newInstance(ctx.getText(titleRes), ctx.getText(messageRes),
                ctx.getText(cancelRes), ctx.getText(confirmRes));
    }

    public ConfirmDialogFragment setOnConfirmed(@Nullable OnConfirmed onConfirmed) {
        this.onConfirmed = onConfirmed;
        return this;
    }

    public ConfirmDialogFragment setOnCancelled(@Nullable OnCancelled onCancelled) {
        this.onCancelled = onCancelled;
        return this;
    }

    public void show(@NonNull FragmentManager fm) {
        show(fm, "ConfirmDialogFragment");
    }

    @Override
    protected int getContentLayoutRes() {
        return R.layout.dialog_confirm_content;
    }

    @Override
    protected void onContentInflated(@NonNull View card) {
        Bundle args = requireArguments();
        ((TextView) card.findViewById(R.id.dialogTitle))
                .setText(args.getCharSequence(ARG_TITLE));
        ((TextView) card.findViewById(R.id.dialogMessage))
                .setText(args.getCharSequence(ARG_MESSAGE));

        MaterialButton cancelBtn = card.findViewById(R.id.dialogCancelButton);
        cancelBtn.setText(args.getCharSequence(ARG_CANCEL));
        cancelBtn.setOnClickListener(v -> {
            if (onCancelled != null) onCancelled.onCancelled();
            dismissAllowingStateLoss();
        });

        MaterialButton confirmBtn = card.findViewById(R.id.dialogConfirmButton);
        confirmBtn.setText(args.getCharSequence(ARG_CONFIRM));
        confirmBtn.setOnClickListener(v -> {
            if (onConfirmed != null) onConfirmed.onConfirmed();
            dismissAllowingStateLoss();
        });
    }
}
