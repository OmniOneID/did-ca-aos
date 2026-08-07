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

package org.omnione.did.ca.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;

import androidx.lifecycle.ViewModelProvider;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.OnboardingStep;
import org.omnione.did.ca.databinding.ActivityOnboardingBinding;
import org.omnione.did.ca.databinding.ItemOnboardingBinding;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.ui.common.ConfirmDialogFragment;
import org.omnione.did.ca.util.IntentRouter;

import java.util.Locale;

@dagger.hilt.android.AndroidEntryPoint
public class OnboardingActivity extends BaseActivity {

    public static final String EXTRA_STEP = "extra_step";
    public static final String EXTRA_VIA_BIO = "extra_via_bio";

    private ActivityOnboardingBinding binding;
    private OnboardingViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(OnboardingViewModel.class);

        viewModel.getState().observe(this, state -> {
            render(state);
            if (state != null && state.step == 2) {
                viewModel.firePreExecute();
            }
        });
        viewModel.getEvents().observe(this, event -> {
            OnboardingEvent value = event == null ? null : event.getIfNotHandled();
            if (value != null) handleEvent(value);
        });

        binding.nextButton.setOnClickListener(v -> viewModel.onNext());

        int step = getIntent().getIntExtra(EXTRA_STEP, 1);
        boolean viaBio = getIntent().getBooleanExtra(EXTRA_VIA_BIO, false);
        viewModel.setViaBio(viaBio);
        viewModel.setStep(step);
    }

    private void render(OnboardingUiState state) {
        if (state == null) return;

        binding.iconImage.setImageResource(state.current.iconRes);
        binding.titleText.setText(state.current.titleRes);
        binding.stepLabel.setText(String.format(
                Locale.getDefault(),
                getString(R.string.onboarding_step_label),
                state.step,
                state.totalSteps));

        renderItems(state.current);
        renderProgressBars(state.step, state.totalSteps);
        playSlideIn();
    }

    private void renderItems(OnboardingStep current) {
        LinearLayout container = binding.itemsContainer;
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < current.items.size(); i++) {
            OnboardingStep.Item item = current.items.get(i);
            ItemOnboardingBinding itemBinding = ItemOnboardingBinding.inflate(inflater, container, false);
            itemBinding.itemNumber.setText(String.valueOf(i + 1));
            itemBinding.itemTitle.setText(item.titleRes);
            itemBinding.itemDesc.setText(item.descRes);
            View v = itemBinding.getRoot();
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            if (i > 0) lp.topMargin = dp(10);
            container.addView(v, lp);
        }
    }

    private void renderProgressBars(int step, int total) {
        LinearLayout bars = binding.progressBars;
        bars.removeAllViews();
        for (int i = 1; i <= total; i++) {
            View segment = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i > 1) lp.leftMargin = dp(6);
            segment.setLayoutParams(lp);
            segment.setBackgroundResource(i <= step
                    ? R.drawable.bg_progress_segment
                    : R.drawable.bg_progress_segment_inactive);
            bars.addView(segment);
        }
    }

    private void playSlideIn() {
        binding.iconCircle.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right));
        binding.titleText.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right));
        binding.itemsContainer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right));
    }

    private void handleEvent(OnboardingEvent event) {
        switch (event) {
            case SHOW_LOCK_DIALOG:
                showLockDialog();
                break;
            case GO_PIN_REGISTER:

                startActivity(IntentRouter.pinRegisterIntent(this, 3, true));
                break;
            case GO_PIN_REGISTER_SIGN:

                startActivity(IntentRouter.pinRegisterSignIntent(this, viewModel.isViaBio()));
                break;
        }
    }

    private void showLockDialog() {

        ConfirmDialogFragment.newInstance(
                        this,
                        R.string.onboarding_lock_dialog_title,
                        R.string.onboarding_lock_dialog_message,
                        R.string.common_no,
                        R.string.common_yes)
                .setOnCancelled(viewModel::advanceFromLockDialog)
                .setOnConfirmed(this::onLockDialogYes)
                .show(getSupportFragmentManager());
    }

    private void onLockDialogYes() {

        startActivity(IntentRouter.pinUnlockRegisterIntent(this, 2));
    }

}
