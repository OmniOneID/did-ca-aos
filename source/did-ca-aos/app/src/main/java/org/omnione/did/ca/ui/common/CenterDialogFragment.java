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

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import org.omnione.did.ca.R;

public abstract class CenterDialogFragment extends DialogFragment {

    @LayoutRes
    protected abstract int getContentLayoutRes();

    protected abstract void onContentInflated(@NonNull View card);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setStyle(STYLE_NO_FRAME, 0);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.dialog_center, container, false);
        ViewGroup card = root.findViewById(R.id.dialogCard);
        inflater.inflate(getContentLayoutRes(), card, true);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View scrim = view.findViewById(R.id.dialogScrim);
        scrim.setOnClickListener(v -> {
            if (isCancelable()) dismissAllowingStateLoss();
        });
        onContentInflated(view.findViewById(R.id.dialogCard));
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = requireDialog().getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            window.setDimAmount(0f);
        }

        View root = getView();
        if (root == null) return;
        View scrim = root.findViewById(R.id.dialogScrim);
        View card = root.findViewById(R.id.dialogCard);

        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(200L).start();

        card.setScaleX(0.7f);
        card.setScaleY(0.7f);
        card.setAlpha(0f);
        card.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(240L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }
}
