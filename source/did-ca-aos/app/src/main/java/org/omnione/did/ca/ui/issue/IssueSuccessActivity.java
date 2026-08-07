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
import android.view.animation.PathInterpolator;

import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.Credential;
import org.omnione.did.ca.databinding.ActivityIssueSuccessBinding;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.ui.common.NoticeDialogFragment;
import org.omnione.did.ca.util.IntentRouter;

@dagger.hilt.android.AndroidEntryPoint
public class IssueSuccessActivity extends BaseActivity {

    private ActivityIssueSuccessBinding binding;
    private IssueSuccessViewModel viewModel;
    private boolean successAnimationPlayed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityIssueSuccessBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applySystemBarInsets();

        viewModel = new ViewModelProvider(this).get(IssueSuccessViewModel.class);
        viewModel.getState().observe(this, this::render);

        binding.closeButton.setOnClickListener(v -> {
            IntentRouter.startDocs(this);
            finish();
        });

        String vcId = getIntent().getStringExtra(IntentRouter.EXTRA_VC_ID);
        if (vcId != null) {
            String displayName = getIntent().getStringExtra(IntentRouter.EXTRA_DISPLAY_NAME);
            if (displayName != null && !displayName.isEmpty()) {
                binding.issuedItemName.setText(displayName);
            }

            if (getIntent().getBooleanExtra(IntentRouter.EXTRA_DISPLAY_ONLY, false)) {
                if (!successAnimationPlayed) {
                    successAnimationPlayed = true;
                    playSuccessAnimation();
                }
            } else {
                viewModel.loadIssued(vcId);
            }
        } else {
            NoticeDialogFragment.newInstance(this,
                            R.string.notice_issue_failed_title,
                            R.string.notice_issue_failed_message,
                            R.string.common_ok)
                    .setOnConfirmed(this::finish)
                    .show(getSupportFragmentManager());
        }
    }

    private void applySystemBarInsets() {
        applySystemBarInsets(binding.getRoot(), bars -> {
            applyTopInsetPadding(binding.appBar, bars.top);
            applyBottomInsetPadding(binding.bottomBar, dp(16), bars.bottom);
        });
    }

    private void render(IssueSuccessUiState state) {
        if (state == null) return;
        if (state.loading) {
            showProgress();
            return;
        }
        dismissProgress();
        if (state.errorMessageRes != null) {
            NoticeDialogFragment.newInstance(this,
                            R.string.notice_issue_failed_title,
                            state.errorMessageRes,
                            R.string.common_ok)
                    .setOnConfirmed(this::finish)
                    .show(getSupportFragmentManager());
            return;
        }
        Credential c = state.credential;
        if (c == null) return;
        binding.issuedItemName.setText(c.name);
        if (!successAnimationPlayed) {
            successAnimationPlayed = true;
            playSuccessAnimation();
        }
    }

    private void playSuccessAnimation() {
        PathInterpolator overshoot = new PathInterpolator(0.34f, 1.56f, 0.64f, 1f);

        View halo = binding.successHalo;
        View inner = binding.successInnerCircle;
        View check = binding.successCheck;

        halo.setAlpha(0f);
        halo.setScaleX(0.4f);
        halo.setScaleY(0.4f);
        inner.setAlpha(0f);
        inner.setScaleX(0f);
        inner.setScaleY(0f);
        check.setAlpha(0f);
        check.setScaleX(0.5f);
        check.setScaleY(0.5f);

        halo.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(460L)
                .setInterpolator(overshoot)
                .start();

        inner.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(160L)
                .setDuration(560L)
                .setInterpolator(overshoot)
                .start();

        check.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(460L)
                .setDuration(340L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }
}
