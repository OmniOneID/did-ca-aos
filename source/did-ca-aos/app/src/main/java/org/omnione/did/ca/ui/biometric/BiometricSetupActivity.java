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

import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Outline;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.biometric.BiometricManager;
import androidx.lifecycle.ViewModelProvider;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.BioResult;
import org.omnione.did.ca.databinding.ActivityBiometricSetupBinding;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.ui.common.NoticeDialogFragment;
import org.omnione.did.ca.util.IntentRouter;

@dagger.hilt.android.AndroidEntryPoint
public class BiometricSetupActivity extends BaseActivity {

    public static final String EXTRA_FROM_SETTINGS = "extra_bio_from_settings";
    public static final String EXTRA_ONBOARDING_NEXT = "extra_bio_onboarding_next";

    private static final int BIOMETRIC_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_WEAK
                    | BiometricManager.Authenticators.BIOMETRIC_STRONG;

    private static final long BIO_SLIDE_DURATION_MS = 5000L;
    private static final int PANEL_WIDTH_DP = 200;

    private ActivityBiometricSetupBinding binding;
    private BiometricSetupViewModel viewModel;
    private ObjectAnimator bioSlideAnimator;
    private ActivityResultLauncher<Intent> pinAuthLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBiometricSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(BiometricSetupViewModel.class);

        pinAuthLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() != Activity.RESULT_OK) return;
                    String pin = result.getData() == null
                            ? null
                            : result.getData().getStringExtra(
                                    org.omnione.did.ca.ui.pin.PinActivity.RESULT_PIN);
                    if (pin == null || pin.isEmpty()) {
                        Toast.makeText(this, R.string.biometric_auth_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    viewModel.onSettingsBioWithPin(this, pin);
                });

        viewModel.getState().observe(this, state -> {
            if (state == null) return;
            binding.secondaryButton.setText(state.secondaryButtonLabelRes);
        });

        viewModel.getInFlight().observe(this, inFlight -> {
            boolean busy = Boolean.TRUE.equals(inFlight);
            binding.enableButton.setEnabled(!busy);
            binding.secondaryButton.setEnabled(!busy);
            if (busy) {
                showProgress();
            } else {
                dismissProgress();
            }
        });

        viewModel.getResultEvent().observe(this, event -> {
            BioResult result = (event == null) ? null : event.getIfNotHandled();
            if (result == null) return;
            switch (result.status) {
                case SUCCESS:
                    if (viewModel.isFromSettings()) {
                        finishWithRouting();
                    } else {

                        IntentRouter.startOnboarding(this, 3, true);
                        finish();
                    }
                    break;
                case CANCEL:
                    break;
                case ERROR:
                case FAIL:
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();
                    break;
            }
        });

        viewModel.getCheckEvent().observe(this, event -> {
            BiometricSetupViewModel.BiometricCheckResult check =
                    (event == null) ? null : event.getIfNotHandled();
            if (check == null) return;
            switch (check) {
                case ALREADY_REGISTERED:
                    showBiometricNotice(R.string.biometric_already_registered);
                    break;
                case NONE_ENROLLED:
                    showBiometricNotice(R.string.biometric_none_enrolled);
                    break;
                case HW_UNAVAILABLE:
                    showBiometricNotice(R.string.biometric_hw_unavailable);
                    break;
                case READY:
                    pinAuthLauncher.launch(IntentRouter.pinAuthIntent(this, null));
                    break;
            }
        });

        binding.enableButton.setOnClickListener(v -> {
            BiometricManager manager = BiometricManager.from(this);
            int canAuth = manager.canAuthenticate(BIOMETRIC_AUTHENTICATORS);
            if (viewModel.isFromSettings()) {
                viewModel.checkBioStateForSettings(canAuth);
                return;
            }
            if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
                Toast.makeText(this, R.string.biometric_unavailable, Toast.LENGTH_SHORT).show();

                IntentRouter.startOnboarding(this, 3, false);
                finish();
                return;
            }
            viewModel.onEnableClicked(this);
        });
        binding.secondaryButton.setOnClickListener(v -> {
            if (viewModel.isFromSettings()) {
                finishWithRouting();
            } else {

                IntentRouter.startOnboarding(this, 3, false);
                finish();
            }
        });

        setupBioIllustration();
    }

    @Override
    protected void onDestroy() {
        if (bioSlideAnimator != null) {
            bioSlideAnimator.cancel();
            bioSlideAnimator = null;
        }
        super.onDestroy();
    }

    private void setupBioIllustration() {

        binding.bioClip.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        binding.bioClip.setClipToOutline(true);

        float panelWidthPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, PANEL_WIDTH_DP,
                getResources().getDisplayMetrics());

        Keyframe k0 = Keyframe.ofFloat(0f,    0f);
        Keyframe k1 = Keyframe.ofFloat(0.40f, 0f);
        Keyframe k2 = Keyframe.ofFloat(0.50f, -panelWidthPx);
        Keyframe k3 = Keyframe.ofFloat(0.90f, -panelWidthPx);
        Keyframe k4 = Keyframe.ofFloat(1f,    -panelWidthPx * 2f);

        k1.setInterpolator(new LinearInterpolator());
        k2.setInterpolator(new AccelerateDecelerateInterpolator());
        k3.setInterpolator(new LinearInterpolator());
        k4.setInterpolator(new AccelerateDecelerateInterpolator());

        PropertyValuesHolder pvh = PropertyValuesHolder.ofKeyframe(
                "translationX", k0, k1, k2, k3, k4);

        bioSlideAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.bioRow, pvh);
        bioSlideAnimator.setDuration(BIO_SLIDE_DURATION_MS);
        bioSlideAnimator.setRepeatCount(ValueAnimator.INFINITE);
        bioSlideAnimator.setRepeatMode(ValueAnimator.RESTART);
        bioSlideAnimator.start();
    }

    private void showBiometricNotice(int messageRes) {
        NoticeDialogFragment.newInstance(this,
                        R.string.biometric_dialog_title,
                        messageRes,
                        R.string.common_ok)
                .show(getSupportFragmentManager(), "bio_notice");
    }

    private void finishWithRouting() {
        Intent intent = getIntent();
        int onboardingNext = intent.getIntExtra(EXTRA_ONBOARDING_NEXT, -1);
        if (onboardingNext > 0) {
            IntentRouter.startOnboarding(this, onboardingNext);
            finish();
            return;
        }
        if (viewModel.isFromSettings()) {
            setResult(Activity.RESULT_OK);
            finish();
            return;
        }

        IntentRouter.startDocs(this);
        finish();
    }
}
