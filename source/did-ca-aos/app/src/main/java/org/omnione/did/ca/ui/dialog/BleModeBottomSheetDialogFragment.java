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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.BleTransferMode;

public class BleModeBottomSheetDialogFragment extends BottomSheetDialogFragment {

    public interface OnModeSelected {
        void onSelected(@NonNull BleTransferMode mode);
    }

    private static final String TAG = "BleModeBottomSheet";
    private static final String ARG_SELECTED = "selected";

    private static final BleTransferMode[] OPTIONS = {
            BleTransferMode.BOTH,
            BleTransferMode.CENTRAL,
            BleTransferMode.PERIPHERAL
    };

    private OnModeSelected listener;

    @NonNull
    public static BleModeBottomSheetDialogFragment newInstance(@NonNull BleTransferMode selected) {
        BleModeBottomSheetDialogFragment fragment = new BleModeBottomSheetDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SELECTED, selected.id);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    public BleModeBottomSheetDialogFragment setOnModeSelected(@Nullable OnModeSelected listener) {
        this.listener = listener;
        return this;
    }

    public void show(@NonNull FragmentManager manager) {
        show(manager, TAG);
    }

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
        TextView description = view.findViewById(R.id.sheetDescription);
        LinearLayout container = view.findViewById(R.id.actionContainer);

        title.setText(R.string.settings_ble_transfer_mode);
        description.setText(R.string.settings_ble_transfer_mode_description);

        Bundle args = getArguments();
        BleTransferMode selected = BleTransferMode.fromId(
                args == null ? null : args.getString(ARG_SELECTED), BleTransferMode.BOTH);

        LayoutInflater inflater = LayoutInflater.from(view.getContext());
        for (int i = 0; i < OPTIONS.length; i++) {
            if (i > 0) {
                inflater.inflate(R.layout.item_action_sheet_divider, container, true);
            }
            BleTransferMode option = OPTIONS[i];
            View row = inflater.inflate(R.layout.item_select_sheet, container, false);
            TextView label = row.findViewById(R.id.selectLabel);
            ImageView check = row.findViewById(R.id.selectCheck);

            label.setText(labelRes(option));
            boolean isSelected = option == selected;
            check.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
            row.setBackgroundColor(isSelected
                    ? ContextCompat.getColor(view.getContext(), R.color.brand_primary_pale)
                    : ContextCompat.getColor(view.getContext(), android.R.color.transparent));

            row.setOnClickListener(v -> {
                OnModeSelected l = listener;
                if (l != null) l.onSelected(option);
                dismiss();
            });
            container.addView(row);
        }
    }

    @StringRes
    public static int labelRes(@NonNull BleTransferMode mode) {
        switch (mode) {
            case CENTRAL:
                return R.string.settings_ble_mode_central;
            case PERIPHERAL:
                return R.string.settings_ble_mode_peripheral;
            case BOTH:
            default:
                return R.string.settings_ble_mode_both;
        }
    }
}
