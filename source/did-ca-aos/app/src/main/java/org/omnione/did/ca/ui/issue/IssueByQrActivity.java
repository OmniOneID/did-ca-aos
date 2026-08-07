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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.BioResult;
import org.omnione.did.ca.databinding.ActivityIssueByQrBinding;
import org.omnione.did.ca.ui.biometric.AuthMethodActivity;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.ui.common.NoticeDialogFragment;
import org.omnione.did.ca.ui.common.ProgressDialogFragment;
import org.omnione.did.ca.ui.pin.PinActivity;
import org.omnione.did.ca.util.IntentRouter;

@dagger.hilt.android.AndroidEntryPoint
public class IssueByQrActivity extends BaseActivity {

    public static final String EXTRA_PAYLOAD = "extra_payload";

    private ActivityIssueByQrBinding binding;
    private IssueByQrViewModel viewModel;

    @Nullable private String pendingAfterAuthTag;

    private final ActivityResultLauncher<Intent> pinAuthLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK) {
                            viewModel.onCancelled();
                            finish();
                            return;
                        }
                        Intent data = result.getData();
                        if (data == null) {
                            viewModel.onCancelled();
                            finish();
                            return;
                        }
                        String afterAuth = data.getStringExtra(PinActivity.RESULT_AFTER_AUTH);
                        String pin = data.getStringExtra(PinActivity.RESULT_PIN);
                        if (afterAuth == null
                                || !afterAuth.startsWith(IssueByQrViewModel.AFTER_AUTH_TAG_PREFIX)) {
                            viewModel.onCancelled();
                            finish();
                            return;
                        }
                        viewModel.onAuthDone(pin);
                    });

    private final ActivityResultLauncher<Intent> authMethodLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                            viewModel.onCancelled();
                            finish();
                            return;
                        }
                        String method = result.getData().getStringExtra(AuthMethodActivity.RESULT_METHOD);
                        if (AuthMethodActivity.METHOD_PIN.equals(method)) {
                            pinAuthLauncher.launch(IntentRouter.pinAuthIntent(this, pendingAfterAuthTag));
                        } else if (AuthMethodActivity.METHOD_BIOMETRIC.equals(method)) {
                            launchBio();
                        } else {
                            viewModel.onCancelled();
                            finish();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityIssueByQrBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        viewModel = new ViewModelProvider(this).get(IssueByQrViewModel.class);

        viewModel.getState().observe(this, this::render);
        viewModel.getEvent().observe(this, evt -> {
            IssueByQrEvent value = evt == null ? null : evt.getIfNotHandled();
            if (value != null) handleEvent(value);
        });

        if (savedInstanceState == null && viewModel.getState().getValue() == null) {
            String payload = getIntent().getStringExtra(EXTRA_PAYLOAD);
            if (payload == null) {
                showInvalidPayload();
                return;
            }
            viewModel.start(payload);
        }
    }

    private void render(IssueByQrUiState state) {
        if (state == null) return;
        if (state.loading || state.inFlight) {
            ProgressDialogFragment.showIfAbsent(getSupportFragmentManager());
        } else {
            ProgressDialogFragment.dismissIfShown(getSupportFragmentManager());
        }
        if (state.completedVcId != null) {
            startActivity(IntentRouter.issueSuccessIntent(
                    this, state.completedVcId, state.completedDisplayName));
            finish();
        }
    }

    private void handleEvent(@NonNull IssueByQrEvent value) {
        if (value instanceof IssueByQrEvent.LaunchAuthMethodChooser) {
            IssueByQrEvent.LaunchAuthMethodChooser lc =
                    (IssueByQrEvent.LaunchAuthMethodChooser) value;
            pendingAfterAuthTag = lc.afterAuthTag;
            authMethodLauncher.launch(IntentRouter.authMethodChooserIntent(this));
        } else if (value instanceof IssueByQrEvent.LaunchBio) {
            launchBio();
        } else if (value instanceof IssueByQrEvent.LaunchPin) {
            IssueByQrEvent.LaunchPin lp = (IssueByQrEvent.LaunchPin) value;
            pinAuthLauncher.launch(IntentRouter.pinAuthIntent(this, lp.afterAuthTag));
        } else if (value instanceof IssueByQrEvent.ShowError) {
            IssueByQrEvent.ShowError e = (IssueByQrEvent.ShowError) value;
            showErrorNotice(e.titleRes, e.messageRes, e.detailMessage, this::finish);
        }
    }

    private void launchBio() {
        viewModel.getBioRepo().authenticateBiometric(this).whenCompleteAsync((result, err) -> {
            if (err == null && result != null && result.status == BioResult.Status.SUCCESS) {
                viewModel.onAuthDone(null);
            } else {
                viewModel.onBioAuthFailed();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void showInvalidPayload() {
        NoticeDialogFragment.newInstance(this,
                        R.string.notice_qr_issue_invalid_title,
                        R.string.notice_qr_issue_invalid_message,
                        R.string.common_ok)
                .setOnConfirmed(this::finish)
                .show(getSupportFragmentManager());
    }
}
