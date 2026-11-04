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

package org.omnione.did.ca.ui.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.BleTransferMode;
import org.omnione.did.ca.databinding.ActivitySettingsBinding;
import org.omnione.did.ca.ui.dialog.BleModeBottomSheetDialogFragment;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.ui.common.ConfirmDialogFragment;
import org.omnione.did.ca.ui.common.NoticeDialogFragment;
import org.omnione.did.ca.util.IntentRouter;

@dagger.hilt.android.AndroidEntryPoint
public class SettingsActivity extends BaseActivity {

    private static final int SCROLL_BASE_PADDING_BOTTOM_DP = 0;

    private ActivitySettingsBinding binding;
    private SettingsViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applySystemBarInsets();

        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        viewModel.init();

        viewModel.getState().observe(this, state -> {
            if (state == null) return;
            binding.userNameRow.setVisibility(state.userName.isEmpty() ? View.GONE : View.VISIBLE);
            binding.userNameText.setText(state.userName);
            binding.didText.setText(state.did);
        });

        viewModel.getBleTransferMode().observe(this, mode -> {
            if (mode == null) return;
            binding.bleTransferModeValue.setText(
                    BleModeBottomSheetDialogFragment.labelRes(mode));
        });

        viewModel.getResetCompleteEvent().observe(this, ev -> {
            if (ev == null) return;
            Boolean handled = ev.getIfNotHandled();
            if (handled == null) return;
            dismissProgress();
            NoticeDialogFragment.newInstance(this,
                            R.string.settings_reset_failed_title,
                            R.string.settings_reset_failed_message,
                            R.string.common_ok)
                    .show(getSupportFragmentManager(), "reset_failed");
        });

        viewModel.getUnlockPinChangeRequest().observe(this, ev -> {
            if (ev == null) return;
            Boolean registered = ev.getIfNotHandled();
            if (registered == null) return;
            dismissProgress();
            if (registered) {
                startActivity(IntentRouter.pinUnlockChangeIntent(this));
            } else {
                NoticeDialogFragment.newInstance(this,
                                R.string.settings_unlock_pin_dialog_title,
                                R.string.settings_unlock_pin_not_set,
                                R.string.common_ok)
                        .show(getSupportFragmentManager(), "set_e_04");
            }
        });

        binding.backButton.setOnClickListener(v -> finish());
        binding.copyDidButton.setOnClickListener(v -> {
            SettingsUiState state = viewModel.getState().getValue();
            copy(state == null ? "" : state.did, R.string.settings_did_label, R.string.settings_did_copied);
        });
        binding.copyUserNameButton.setOnClickListener(v -> {
            SettingsUiState state = viewModel.getState().getValue();
            copy(state == null ? "" : state.userName, R.string.settings_user_name_label, R.string.settings_user_name_copied);
        });

        binding.serverUrlRow.setOnClickListener(v -> IntentRouter.startServerUrl(this));

        binding.changePinRow.setOnClickListener(v ->
                startActivity(IntentRouter.pinChangeIntent(this)));

        binding.changeUnlockPinRow.setOnClickListener(v -> {
            showProgress();
            viewModel.onChangeUnlockPinClicked();
        });

        binding.addBiometricsRow.setOnClickListener(v ->
                startActivity(IntentRouter.biometricSetupIntent(this, 0, true)));

        binding.resetAppRow.setOnClickListener(v -> showResetDialog());
        binding.bleTransferModeRow.setOnClickListener(v -> showBleModeSheet());
    }

    private void applySystemBarInsets() {
        applySystemBarInsets(binding.getRoot(), bars -> {
            applyTopInsetPadding(binding.appBar, bars.top);
            applyBottomInsetPadding(binding.contentScroll,
                    dp(SCROLL_BASE_PADDING_BOTTOM_DP), bars.bottom);
        });
    }

    private void copy(String value, int labelRes, int toastRes) {
        if (value.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText(getString(labelRes), value));
        Toast.makeText(this, toastRes, Toast.LENGTH_SHORT).show();
    }

    private void showBleModeSheet() {
        BleTransferMode current = viewModel.getBleTransferMode().getValue();
        if (current == null) return;
        BleModeBottomSheetDialogFragment.newInstance(current)
                .setOnModeSelected(viewModel::setBleTransferMode)
                .show(getSupportFragmentManager());
    }

    private void showResetDialog() {
        ConfirmDialogFragment.newInstance(
                        getText(R.string.settings_reset_dialog_title),
                        getText(R.string.settings_reset_dialog_message),
                        getText(R.string.common_cancel),
                        getText(R.string.settings_reset_confirm))
                .setOnConfirmed(this::performReset)
                .show(getSupportFragmentManager(), "reset_app");
    }

    private void performReset() {
        showProgress();
        viewModel.resetApp();

    }
}
