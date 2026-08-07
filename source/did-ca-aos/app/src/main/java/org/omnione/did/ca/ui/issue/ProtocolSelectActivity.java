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

package org.omnione.did.ca.ui.issue;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.StringRes;
import androidx.core.view.WindowCompat;

import org.omnione.did.ca.R;
import org.omnione.did.ca.databinding.ActivityProtocolSelectBinding;
import org.omnione.did.ca.databinding.ItemProtocolOptionBinding;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.util.IntentRouter;

@dagger.hilt.android.AndroidEntryPoint
public class ProtocolSelectActivity extends BaseActivity {

    private ActivityProtocolSelectBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProtocolSelectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applySystemBarInsets();

        binding.backButton.setOnClickListener(v -> finish());

        bindOption(
                binding.openDidOption,
                R.string.protocol_open_did_title,
                R.string.protocol_open_did_desc,
                v -> startActivity(IntentRouter.issuerListIntent(this)));

        bindOption(
                binding.oid4vciOption,
                R.string.protocol_oid4vci_title,
                R.string.protocol_oid4vci_desc,
                v -> startActivity(IntentRouter.issuerListIntent(this, IssueProtocol.OID4VCI)));
    }

    private void applySystemBarInsets() {
        applySystemBarInsets(binding.getRoot(),
                bars -> applyTopInsetPadding(binding.appBar, bars.top));
    }

    private void bindOption(ItemProtocolOptionBinding option,
                            @StringRes int titleRes,
                            @StringRes int descRes,
                            View.OnClickListener click) {
        option.optionTitle.setText(titleRes);
        option.optionDesc.setText(descRes);
        option.getRoot().setOnClickListener(click);
    }
}
