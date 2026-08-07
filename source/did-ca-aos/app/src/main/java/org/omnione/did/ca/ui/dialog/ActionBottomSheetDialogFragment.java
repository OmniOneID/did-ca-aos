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

package org.omnione.did.ca.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.omnione.did.ca.R;

import java.util.List;

public abstract class ActionBottomSheetDialogFragment extends BottomSheetDialogFragment {

    @StringRes
    protected abstract int getTitleRes();

    @StringRes
    protected abstract int getDescriptionRes();

    @NonNull
    protected abstract List<BottomSheetAction> buildActions();

    @Override
    public int getTheme() {
        return R.style.ThemeOverlay_DidCa_BottomSheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_action_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView title = view.findViewById(R.id.sheetTitle);
        TextView desc = view.findViewById(R.id.sheetDescription);
        LinearLayout actions = view.findViewById(R.id.actionContainer);

        title.setText(getTitleRes());
        desc.setText(getDescriptionRes());

        LayoutInflater inflater = LayoutInflater.from(view.getContext());
        List<BottomSheetAction> items = buildActions();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                inflater.inflate(R.layout.item_action_sheet_divider, actions, true);
            }
            View row = inflater.inflate(R.layout.item_action_sheet, actions, false);
            BottomSheetAction item = items.get(i);
            ImageView icon = row.findViewById(R.id.actionIcon);
            TextView label = row.findViewById(R.id.actionLabel);
            icon.setImageResource(item.iconRes);
            label.setText(item.labelRes);
            row.setOnClickListener(v -> {
                item.onClick.onClick(v);
                dismiss();
            });
            actions.addView(row);
        }
    }
}
