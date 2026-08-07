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

package org.omnione.did.ca.ui.txcode;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.GridLayout;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import org.omnione.did.ca.R;
import org.omnione.did.ca.databinding.ActivityTxCodeBinding;
import org.omnione.did.ca.ui.Event;
import org.omnione.did.ca.ui.common.BaseActivity;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class TxCodeActivity extends BaseActivity {

    public static final String EXTRA_LENGTH = "extra_tx_code_length";

    public static final String EXTRA_INPUT_MODE = "extra_tx_code_input_mode";

    public static final String EXTRA_DESCRIPTION = "extra_tx_code_description";

    public static final String EXTRA_TITLE = "extra_tx_code_title";

    public static final String RESULT_TX_CODE = "result_tx_code";

    private ActivityTxCodeBinding binding;
    private TxCodeViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTxCodeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(TxCodeViewModel.class);

        Intent intent = getIntent();
        int length = intent.getIntExtra(EXTRA_LENGTH, 6);
        String description = intent.getStringExtra(EXTRA_DESCRIPTION);
        String title = intent.getStringExtra(EXTRA_TITLE);
        viewModel.init(
                length,
                title,
                description,
                getString(R.string.oid4vci_enter_tx_code),
                getString(R.string.oid4vci_tx_code_default_description));

        binding.closeButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        View.OnClickListener keyListener = v -> {
            Object tag = v.getTag();
            if (tag == null) return;
            String value = tag.toString();
            if (value.isEmpty()) return;
            viewModel.onKey(value.charAt(0));
        };
        GridLayout numPad = binding.numPad;
        for (int i = 0; i < numPad.getChildCount(); i++) {
            View child = numPad.getChildAt(i);
            if (child.getTag() != null) child.setOnClickListener(keyListener);
        }

        viewModel.uiState().observe(this, state -> {
            if (state == null) return;
            binding.txCodeTitle.setText(state.title);
            binding.txCodeDescription.setText(state.description);
            binding.txCodeDots.setLength(state.length);
            binding.txCodeDots.setFilled(state.code.length());
        });

        viewModel.onTxCodeReady().observe(this, this::handleReady);
    }

    private void handleReady(@Nullable Event<String> event) {
        String code = event == null ? null : event.getIfNotHandled();
        if (code == null) return;
        Intent data = new Intent();
        data.putExtra(RESULT_TX_CODE, code);
        setResult(RESULT_OK, data);
        finish();
    }
}
