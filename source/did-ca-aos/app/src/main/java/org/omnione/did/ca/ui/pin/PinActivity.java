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

package org.omnione.did.ca.ui.pin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.GridLayout;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.BioResult;
import org.omnione.did.ca.databinding.ActivityPinBinding;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.ui.common.NoticeDialogFragment;
import org.omnione.did.ca.util.IntentRouter;

@dagger.hilt.android.AndroidEntryPoint
public class PinActivity extends BaseActivity {

    public static final String EXTRA_MODE = "extra_pin_mode";
    public static final String EXTRA_AFTER_AUTH = "extra_after_auth";
    public static final String EXTRA_ONBOARDING_NEXT = "extra_onboarding_next";
    public static final String EXTRA_ONBOARDING_DONE = "extra_onboarding_done";
    public static final String EXTRA_VIA_BIOMETRIC = "extra_via_biometric";

    public static final String RESULT_PIN = "result_pin";

    public static final String RESULT_AFTER_AUTH = EXTRA_AFTER_AUTH;

    private static final long ADVANCE_DELAY_MS = 180L;

    private ActivityPinBinding binding;
    private PinViewModel viewModel;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPinBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(PinViewModel.class);

        binding.closeButton.setOnClickListener(v -> finish());

        View.OnClickListener keyListener = v -> {
            Object tag = v.getTag();
            if (tag == null) return;
            String value = tag.toString();
            if (value.isEmpty()) return;
            char key = value.charAt(0);
            boolean reachedFull = viewModel.onKey(key);
            if (reachedFull) handler.postDelayed(viewModel::advance, ADVANCE_DELAY_MS);
        };
        GridLayout numPad = binding.numPad;
        for (int i = 0; i < numPad.getChildCount(); i++) {
            View child = numPad.getChildAt(i);
            if (child.getTag() != null) child.setOnClickListener(keyListener);
        }

        viewModel.getState().observe(this, this::render);
        viewModel.getDoneEvent().observe(this, event -> {
            PinDoneEvent value = event == null ? null : event.getIfNotHandled();
            if (value != null) handleDone(value);
        });
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void render(PinUiState state) {
        if (state == null) return;
        binding.pinTitle.setText(state.titleRes);
        binding.pinSubtitle.setText(state.stepDescRes);
        binding.pinDots.setFilled(state.filled);
        binding.pinDots.setError(state.error);
        if (state.error) {
            binding.pinError.setText(state.errorMessageRes);
            binding.pinError.setVisibility(View.VISIBLE);
        } else {
            binding.pinError.setVisibility(View.INVISIBLE);
        }
        binding.numPad.setEnabled(!state.inFlight);
        binding.numPad.setAlpha(state.inFlight ? 0.5f : 1.0f);
        if (state.inFlight) {
            showProgress();
        } else {
            dismissProgress();
        }
    }

    private void handleDone(PinDoneEvent event) {
        Intent intent = getIntent();
        switch (event.type) {
            case REGISTER_DONE:
                handleRegisterDone(intent);
                break;
            case CHANGE_DONE:
                setResult(Activity.RESULT_OK);
                finish();
                break;
            case AUTH_DONE:
                handleAuthDone(intent, event.pin);
                break;
            case REGISTER_SIGN_NEEDS_BIO:

                viewModel.getBioRepo().authenticateBiometric(this).whenCompleteAsync((result, err) -> {
                    if (err == null && result != null && result.status == BioResult.Status.SUCCESS) {
                        viewModel.onBioAuthSuccess();
                    } else {
                        if (result != null && result.message != null) {
                            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();
                        }
                        viewModel.onBioAuthFailed();
                    }
                }, ContextCompat.getMainExecutor(this));
                break;
            case REGISTER_SIGN_DONE:

                IntentRouter.startDocs(this);
                finish();
                break;
            case REGISTRATION_FAILED:

                NoticeDialogFragment.newInstance(
                                this,
                                R.string.notice_registration_failed_title,
                                R.string.notice_registration_failed_message,
                                R.string.common_ok)
                        .setOnConfirmed(() -> {
                            IntentRouter.startOnboarding(this, 1);
                            finish();
                        })
                        .show(getSupportFragmentManager());
                break;
            case UNLOCK_REGISTER_DONE:
                handleUnlockRegisterDone(intent);
                break;
            case UNLOCK_CHANGE_DONE:
                setResult(Activity.RESULT_OK);
                finish();
                break;
            case UNLOCK_AUTH_DONE:
                if (intent.getBooleanExtra(EXTRA_ONBOARDING_DONE, false)) {

                    IntentRouter.startDocs(this);
                } else {

                    setResult(Activity.RESULT_OK);
                }
                finish();
                break;
        }
    }

    private void handleUnlockRegisterDone(Intent intent) {

        int onboardingNext = intent.getIntExtra(EXTRA_ONBOARDING_NEXT, -1);
        if (onboardingNext > 0) {
            IntentRouter.startOnboarding(this, onboardingNext);
            finish();
            return;
        }
        setResult(Activity.RESULT_OK);
        finish();
    }

    private void handleRegisterDone(Intent intent) {

        boolean viaBiometric = intent.getBooleanExtra(EXTRA_VIA_BIOMETRIC, false);
        int onboardingNext = intent.getIntExtra(EXTRA_ONBOARDING_NEXT, -1);
        if (viaBiometric) {

            startActivity(IntentRouter.biometricSetupIntent(this, onboardingNext, false));
            finish();
        } else if (onboardingNext > 0) {
            IntentRouter.startOnboarding(this, onboardingNext);
            finish();
        } else {
            setResult(Activity.RESULT_OK);
            finish();
        }
    }

    private void handleAuthDone(Intent intent, String pinValue) {
        boolean onboardingDone = intent.getBooleanExtra(EXTRA_ONBOARDING_DONE, false);
        int onboardingNext = intent.getIntExtra(EXTRA_ONBOARDING_NEXT, -1);
        String afterAuth = intent.getStringExtra(EXTRA_AFTER_AUTH);

        if (onboardingDone) {
            IntentRouter.startDocs(this);
            finish();
            return;
        }
        if (onboardingNext > 0) {
            IntentRouter.startOnboarding(this, onboardingNext);
            finish();
            return;
        }
        Intent result = new Intent();
        if (afterAuth != null) result.putExtra(RESULT_AFTER_AUTH, afterAuth);
        if (pinValue != null)  result.putExtra(RESULT_PIN, pinValue);
        setResult(Activity.RESULT_OK, result);
        finish();
    }
}
