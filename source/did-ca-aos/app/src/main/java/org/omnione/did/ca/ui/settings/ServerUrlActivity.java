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

import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.repository.ServerUrlRepository;
import org.omnione.did.ca.databinding.ActivityServerUrlBinding;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.ui.common.NoticeDialogFragment;

import java.util.List;

@dagger.hilt.android.AndroidEntryPoint
public class ServerUrlActivity extends BaseActivity {

    public static final String EXTRA_FROM_SPLASH = "extra_from_splash";

    private ActivityServerUrlBinding binding;
    private ServerUrlViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityServerUrlBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            applyTopInsetPadding(binding.appBar, bars.top);
            applyBottomInsetPadding(binding.bottomBar, 0, Math.max(bars.bottom, ime));
            return WindowInsetsCompat.CONSUMED;
        });

        if (getIntent().getBooleanExtra(EXTRA_FROM_SPLASH, false)) {
            binding.backButton.setVisibility(View.GONE);
        }

        viewModel = new ViewModelProvider(this).get(ServerUrlViewModel.class);

        viewModel.getCustomKeys().observe(this, this::render);
        viewModel.getSaveEnabled().observe(this, enabled ->
                binding.saveButton.setEnabled(Boolean.TRUE.equals(enabled)));

        binding.backButton.setOnClickListener(v -> finish());
        binding.cancelButton.setOnClickListener(v -> finish());
        binding.saveButton.setOnClickListener(v -> onSaveClicked());
        binding.allDefaultButton.setOnClickListener(v -> viewModel.allToDefault());
        binding.allCustomButton.setOnClickListener(v -> viewModel.allToCustom());

        viewModel.init();
    }

    private void onSaveClicked() {
        if (viewModel.save()) {
            setResult(RESULT_OK);
            finish();
            return;
        }
        NoticeDialogFragment.newInstance(this,
                        R.string.server_url_save_failed_title,
                        R.string.server_url_save_failed_message,
                        R.string.common_ok)
                .show(getSupportFragmentManager());
    }

    private void render(@NonNull List<String> customKeys) {
        List<String> defaultKeys = viewModel.defaultKeys();

        binding.allDefaultButton.setEnabled(!customKeys.isEmpty());
        binding.allCustomButton.setEnabled(!defaultKeys.isEmpty());

        binding.customEmptyNote.setVisibility(customKeys.isEmpty() ? View.VISIBLE : View.GONE);
        binding.defaultEmptyNote.setVisibility(defaultKeys.isEmpty() ? View.VISIBLE : View.GONE);

        clearRows(binding.customCard, binding.customEmptyNote);
        clearRows(binding.defaultCard, binding.defaultEmptyNote);

        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < customKeys.size(); i++) {
            binding.customCard.addView(
                    customRow(inflater, binding.customCard, customKeys.get(i),
                            i < customKeys.size() - 1));
        }
        for (int i = 0; i < defaultKeys.size(); i++) {
            binding.defaultCard.addView(
                    defaultRow(inflater, binding.defaultCard, defaultKeys.get(i),
                            i < defaultKeys.size() - 1));
        }
    }

    private static void clearRows(@NonNull LinearLayout card, @NonNull View keep) {
        for (int i = card.getChildCount() - 1; i >= 0; i--) {
            if (card.getChildAt(i) != keep) card.removeViewAt(i);
        }
    }

    @NonNull
    private View customRow(@NonNull LayoutInflater inflater, @NonNull LinearLayout parent,
                           @NonNull String key, boolean showDivider) {
        View row = inflater.inflate(R.layout.item_server_url_custom, parent, false);
        ((TextView) row.findViewById(R.id.rowLabel)).setText(labelRes(key));
        row.findViewById(R.id.rowDivider)
                .setVisibility(showDivider ? View.VISIBLE : View.GONE);

        EditText input = row.findViewById(R.id.rowInput);
        TextView error = row.findViewById(R.id.rowError);
        input.setSaveEnabled(false);
        input.setText(viewModel.customUrl(key));
        applyError(input, error, viewModel.errorOf(key));
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                applyError(input, error, viewModel.onUrlChanged(key, s.toString()));
            }
        });

        MaterialButton action = row.findViewById(R.id.rowActionButton);
        action.setOnClickListener(v -> viewModel.toDefault(key));
        return row;
    }

    @NonNull
    private View defaultRow(@NonNull LayoutInflater inflater, @NonNull LinearLayout parent,
                            @NonNull String key, boolean showDivider) {
        View row = inflater.inflate(R.layout.item_server_url_default, parent, false);
        ((TextView) row.findViewById(R.id.rowLabel)).setText(labelRes(key));
        ((TextView) row.findViewById(R.id.rowUrl)).setText(viewModel.defaultUrl(key));
        row.findViewById(R.id.rowDivider)
                .setVisibility(showDivider ? View.VISIBLE : View.GONE);

        MaterialButton action = row.findViewById(R.id.rowActionButton);
        action.setOnClickListener(v -> viewModel.toCustom(key));
        return row;
    }

    private static void applyError(@NonNull EditText input, @NonNull TextView error,
                                   @StringRes int messageRes) {
        if (messageRes == 0) {
            error.setVisibility(View.GONE);
            input.setBackgroundResource(R.drawable.bg_text_field);
        } else {
            error.setText(messageRes);
            error.setVisibility(View.VISIBLE);
            input.setBackgroundResource(R.drawable.bg_text_field_error);
        }
    }

    @StringRes
    private static int labelRes(@NonNull String key) {
        switch (key) {
            case ServerUrlRepository.KEY_TAS_URL: return R.string.server_url_label_tas;
            case ServerUrlRepository.KEY_CAS_URL: return R.string.server_url_label_cas;
            case ServerUrlRepository.KEY_API_GW_URL: return R.string.server_url_label_api_gw;
            case ServerUrlRepository.KEY_WALLET_URL: return R.string.server_url_label_wallet;
            case ServerUrlRepository.KEY_VERIFIER_URL: return R.string.server_url_label_verifier;
            case ServerUrlRepository.KEY_DEMO_URL: return R.string.server_url_label_demo;
            default: throw new IllegalArgumentException("unknown server url key: " + key);
        }
    }
}
