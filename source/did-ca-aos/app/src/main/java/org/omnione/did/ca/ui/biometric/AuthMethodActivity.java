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

package org.omnione.did.ca.ui.biometric;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import org.omnione.did.ca.R;
import org.omnione.did.ca.databinding.ActivityAuthMethodBinding;
import org.omnione.did.ca.databinding.ItemAuthMethodBinding;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.util.IntentRouter;

@dagger.hilt.android.AndroidEntryPoint
public class AuthMethodActivity extends BaseActivity {

    public static final String EXTRA_AFTER_AUTH = "extra_am_after_auth";

    public static final String EXTRA_RETURN_METHOD = "extra_return_method";

    public static final String RESULT_METHOD = "result_auth_method";
    public static final String METHOD_PIN = "pin";
    public static final String METHOD_BIOMETRIC = "biometric";

    private ActivityAuthMethodBinding binding;
    private AuthMethodViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthMethodBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applySystemBarInsets();

        viewModel = new ViewModelProvider(this).get(AuthMethodViewModel.class);
        viewModel.getInFlight().observe(this, busy -> {
            boolean b = Boolean.TRUE.equals(busy);
            binding.pinOption.getRoot().setEnabled(!b);
            binding.biometricOption.getRoot().setEnabled(!b);
        });
        viewModel.getResultEvent().observe(this, event -> {
            AuthMethodResult result = (event == null) ? null : event.getIfNotHandled();
            if (result == null) return;
            switch (result) {
                case BIO_AUTH_SUCCESS:
                    routeAfterAuth();
                    break;
                case BIO_AUTH_FAILED:
                    Toast.makeText(this, R.string.biometric_auth_failed, Toast.LENGTH_SHORT).show();
                    break;
            }
        });

        binding.backButton.setOnClickListener(v -> finish());

        bindOption(
                binding.pinOption,
                R.drawable.ic_keypad,
                R.string.auth_method_pin_title,
                R.string.auth_method_pin_desc,
                v -> proceed(METHOD_PIN));

        bindOption(
                binding.biometricOption,
                R.drawable.ic_finger_large,
                R.string.auth_method_bio_title,
                R.string.auth_method_bio_desc,
                v -> proceed(METHOD_BIOMETRIC));
    }

    private void applySystemBarInsets() {
        applySystemBarInsets(binding.getRoot(),
                bars -> applyTopInsetPadding(binding.appBar, bars.top));
    }

    private void bindOption(ItemAuthMethodBinding option,
                            @DrawableRes int iconRes,
                            @StringRes int titleRes,
                            @StringRes int descRes,
                            View.OnClickListener click) {
        option.optionIcon.setImageResource(iconRes);
        option.optionTitle.setText(titleRes);
        option.optionDesc.setText(descRes);
        option.getRoot().setOnClickListener(click);
    }

    private void proceed(String method) {

        if (getIntent().getBooleanExtra(EXTRA_RETURN_METHOD, false)) {
            android.content.Intent result = new android.content.Intent();
            result.putExtra(RESULT_METHOD, method);
            setResult(android.app.Activity.RESULT_OK, result);
            finish();
            return;
        }
        if (METHOD_PIN.equals(method)) {
            String afterAuth = getIntent().getStringExtra(EXTRA_AFTER_AUTH);
            startActivity(IntentRouter.pinAuthIntent(this, afterAuth));
            finish();
            return;
        }
        if (METHOD_BIOMETRIC.equals(method)) {
            viewModel.onBiometricSelected(this);
        }
    }

    private void routeAfterAuth() {
        String afterAuth = getIntent().getStringExtra(EXTRA_AFTER_AUTH);
        android.content.Intent result = new android.content.Intent();
        if (afterAuth != null) result.putExtra(RESULT_METHOD, METHOD_BIOMETRIC);
        if (afterAuth != null) result.putExtra(EXTRA_AFTER_AUTH, afterAuth);
        setResult(android.app.Activity.RESULT_OK, result);
        finish();
    }
}
