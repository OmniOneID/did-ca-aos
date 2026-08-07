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

package org.omnione.did.ca.ui.proximity;

import android.os.Bundle;

import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import org.omnione.did.ca.databinding.ActivityProximityBinding;
import org.omnione.did.ca.ui.common.BaseActivity;

public class ProximityActivity extends BaseActivity {

    private static final int FOOTER_BASE_PADDING_BOTTOM_DP = 20;

    private ActivityProximityBinding binding;
    private ProximityViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProximityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applySystemBarInsets();

        viewModel = new ViewModelProvider(this, new ProximityViewModel.Factory())
                .get(ProximityViewModel.class);

        binding.backButton.setOnClickListener(v -> finish());

        viewModel.getState().observe(this, state -> {
            if (state == null) return;
            binding.qrImage.setImageBitmap(state.qrBitmap);
        });
    }

    private void applySystemBarInsets() {
        applySystemBarInsets(binding.getRoot(), bars -> {
            applyTopInsetPadding(binding.appBar, bars.top);
            applyBottomInsetPadding(binding.footer,
                    dp(FOOTER_BASE_PADDING_BOTTOM_DP), bars.bottom);
        });
    }
}
