/*
 * Copyright 2025 OmniOne.
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

package org.omnione.did.ca.ui.sign;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import org.omnione.did.ca.R;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.ui.BaseActivity;
import org.omnione.did.ca.ui.MainActivity;
import org.omnione.did.ca.ui.PinActivity;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.exception.WalletCoreException;

/**
 * Login / registration screen.
 *
 * Launch modes:
 *  - From {@link org.omnione.did.ca.ui.user.StepFragment} via startActivityForResult:
 *      {@link #EXTRA_IS_FROM_REGISTRATION} = true
 *      On success → setResult(OK) + finish; caller navigates to setLockFragment.
 *
 *  - From {@link org.omnione.did.ca.ui.SplashActivity} (returning user, no tokens):
 *      {@link #EXTRA_IS_FROM_REGISTRATION} = false (default)
 *      On signin  → start PinActivity + finish.
 *      On signup  → start MainActivity + finish (re-registration flow).
 */
public class SignActivity extends BaseActivity {

    /** Set true when launched from StepFragment (new-user registration flow). */
    public static final String EXTRA_IS_FROM_REGISTRATION = "is_from_registration";

    private SignViewModel viewModel;
    private EditText etLoginId, etPassword;
    private TextView tvError;
    private Button btnContinue;

    private boolean isFromRegistration;
    private boolean isOtherDevicePath;
    private String pendingLoginId;
    private String pendingPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign);

        isFromRegistration = getIntent().getBooleanExtra(EXTRA_IS_FROM_REGISTRATION, false);

        etLoginId   = findViewById(R.id.et_login_id);
        etPassword  = findViewById(R.id.et_password);
        tvError     = findViewById(R.id.tv_error);
        btnContinue = findViewById(R.id.btn_continue);

        viewModel = new ViewModelProvider(this).get(SignViewModel.class);
        viewModel.getResult().observe(this, this::onResult);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { updateButtonState(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        etLoginId.addTextChangedListener(watcher);
        etPassword.addTextChangedListener(watcher);

        btnContinue.setOnClickListener(v -> {
            tvError.setVisibility(View.GONE);
            pendingLoginId  = etLoginId.getText().toString().trim();
            pendingPassword = etPassword.getText().toString();
            viewModel.checkRegistrationStatus(this, pendingLoginId);
        });
    }

    // ── ViewModel observation ─────────────────────────────────────────────────

    private void onResult(SignViewModel.SignResult result) {
        switch (result.status) {
            case LOADING        -> showProgress();
            case ERROR          -> { dismissProgress(); showError(result.errorMessage); }
            case USED_BY_OTHER  -> { dismissProgress(); showError("This login ID is already in use by another user."); }

            case NEW -> {
                dismissProgress();
                viewModel.signup(this, pendingLoginId, pendingPassword);
            }
            case CURRENT -> {
                dismissProgress();
                viewModel.signin(this, pendingLoginId, pendingPassword);
            }
            case OTHER_DEVICE -> {
                dismissProgress();
                isOtherDevicePath = true;
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Device Change Detected")
                        .setMessage("This account is registered on another device. " +
                                "Do you want to sign in on this device?")
                        .setCancelable(false)
                        .setPositiveButton("Yes", (d, w) -> {
                            new Thread(() -> {
                                try {
                                    WalletApi walletApi = WalletApi.getInstance(SignActivity.this);
                                    if (walletApi.isExistWallet()) {
                                        walletApi.deleteWallet(false);
                                    }
                                } catch (WalletCoreException e) {
                                    CaLog.e("deleteWallet error: " + e.getMessage());
                                }
                                viewModel.signin(SignActivity.this, pendingLoginId, pendingPassword);
                            }).start();
                        })
                        .setNegativeButton("No", (d, w) -> isOtherDevicePath = false)
                        .show();
            }
            case SIGNUP_SUCCESS -> {
                dismissProgress();
                navigateAfterSuccess(true);
            }
            case SIGNIN_SUCCESS -> {
                dismissProgress();
                navigateAfterSuccess(isOtherDevicePath);
            }
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    /**
     * @param isNewUser true = signup or OTHER_DEVICE (wallet setup needed)
     *                  false = signin of existing user (wallet ready)
     */
    private void navigateAfterSuccess(boolean isNewUser) {
        if (isFromRegistration) {
            // Return to StepFragment; it will navigate to setLockFragment
            setResult(Activity.RESULT_OK);
            finish();
            return;
        }

        if (isNewUser) {
            // Returning user who needs re-registration (OTHER_DEVICE from SplashActivity)
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            // Existing user: go to PIN auth
            Intent intent = new Intent(this, PinActivity.class);
            intent.putExtra(Constants.INTENT_IS_REGISTRATION, false);
            intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_STATUS_UNLOCK);
            startActivity(intent);
        }
        finish();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updateButtonState() {
        boolean hasInput = !etLoginId.getText().toString().trim().isEmpty()
                && !etPassword.getText().toString().isEmpty();
        btnContinue.setEnabled(hasInput);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }
}
