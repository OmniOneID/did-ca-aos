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

package org.omnione.did.ca.ui.issuebyoid4vci;

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
import org.omnione.did.ca.databinding.ActivityIssueByOid4vciBinding;
import org.omnione.did.ca.ui.biometric.AuthMethodActivity;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.ui.common.ProgressDialogFragment;
import org.omnione.did.ca.ui.pin.PinActivity;
import org.omnione.did.ca.ui.txcode.TxCodeActivity;
import org.omnione.did.ca.util.IntentRouter;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class IssueByOid4vciActivity extends BaseActivity {

    public static final String EXTRA_PAYLOAD = "extra_payload";

    public static final String EXTRA_EXPECTED_ISSUER = "extra_expected_issuer";

    private ActivityIssueByOid4vciBinding binding;
    private IssueByOid4vciViewModel viewModel;

    @Nullable private String pendingAfterAuthTag;

    private final ActivityResultLauncher<Intent> pinAuthLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                            viewModel.onAuthCancelled();
                            finish();
                            return;
                        }
                        String afterAuth = result.getData().getStringExtra(PinActivity.RESULT_AFTER_AUTH);
                        String pin = result.getData().getStringExtra(PinActivity.RESULT_PIN);
                        if (afterAuth == null || !afterAuth.startsWith(IssueByOid4vciViewModel.AFTER_AUTH_TAG_PREFIX)) {
                            viewModel.onAuthCancelled();
                            finish();
                            return;
                        }
                        viewModel.onAuthDone(pin);
                    });

    private final ActivityResultLauncher<Intent> authMethodLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                            viewModel.onAuthCancelled();
                            finish();
                            return;
                        }
                        String method = result.getData().getStringExtra(AuthMethodActivity.RESULT_METHOD);
                        if (AuthMethodActivity.METHOD_PIN.equals(method)) {
                            pinAuthLauncher.launch(IntentRouter.pinAuthIntent(this, pendingAfterAuthTag));
                        } else if (AuthMethodActivity.METHOD_BIOMETRIC.equals(method)) {
                            launchBio();
                        } else {
                            viewModel.onAuthCancelled();
                            finish();
                        }
                    });

    private final ActivityResultLauncher<Intent> txCodeLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                            viewModel.onTxCodeCancelled();
                            finish();
                            return;
                        }
                        String code = result.getData().getStringExtra(TxCodeActivity.RESULT_TX_CODE);
                        if (code == null || code.isEmpty()) {
                            viewModel.onTxCodeCancelled();
                            finish();
                            return;
                        }
                        viewModel.onTxCodeEntered(code);
                    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityIssueByOid4vciBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        viewModel = new ViewModelProvider(this).get(IssueByOid4vciViewModel.class);

        viewModel.getState().observe(this, this::render);
        viewModel.getEvent().observe(this, evt -> {
            IssueByOid4vciEvent value = evt == null ? null : evt.getIfNotHandled();
            if (value != null) handleEvent(value);
        });

        if (savedInstanceState == null && viewModel.getState().getValue() == null) {
            String payload = getIntent().getStringExtra(EXTRA_PAYLOAD);
            if (payload == null || payload.isEmpty()) {
                showFatalError(null);
                return;
            }
            String expectedIssuer = getIntent().getStringExtra(EXTRA_EXPECTED_ISSUER);
            viewModel.start(payload, expectedIssuer);
        }
    }

    private void render(@Nullable IssueByOid4vciUiState state) {
        if (state == null) return;
        if (state.loading) {
            ProgressDialogFragment.showIfAbsent(getSupportFragmentManager());
        } else {
            ProgressDialogFragment.dismissIfShown(getSupportFragmentManager());
        }
        if (state.completedVcId != null) {

            startActivity(IntentRouter.issueSuccessDisplayOnlyIntent(
                    this, state.completedVcId, state.completedDisplayName));
            finish();
        }
    }

    private void handleEvent(@NonNull IssueByOid4vciEvent value) {
        if (value instanceof IssueByOid4vciEvent.LaunchTxCode) {
            IssueByOid4vciEvent.LaunchTxCode lt = (IssueByOid4vciEvent.LaunchTxCode) value;
            txCodeLauncher.launch(IntentRouter.txCodeIntent(
                    this, lt.length, lt.inputMode, lt.description));
        } else if (value instanceof IssueByOid4vciEvent.LaunchAuthMethodChooser) {
            IssueByOid4vciEvent.LaunchAuthMethodChooser lc =
                    (IssueByOid4vciEvent.LaunchAuthMethodChooser) value;
            pendingAfterAuthTag = lc.afterAuthTag;
            authMethodLauncher.launch(IntentRouter.authMethodChooserIntent(this));
        } else if (value instanceof IssueByOid4vciEvent.LaunchBio) {
            launchBio();
        } else if (value instanceof IssueByOid4vciEvent.LaunchPin) {
            IssueByOid4vciEvent.LaunchPin lp = (IssueByOid4vciEvent.LaunchPin) value;
            pinAuthLauncher.launch(IntentRouter.pinAuthIntent(this, lp.afterAuthTag));
        } else if (value instanceof IssueByOid4vciEvent.ShowError) {
            IssueByOid4vciEvent.ShowError e = (IssueByOid4vciEvent.ShowError) value;
            showErrorNotice(e.titleRes, e.messageRes, e.detailMessage, this::finish);
        }
    }

    private void launchBio() {
        viewModel.getBioRepo().authenticateBiometric(this).whenCompleteAsync((res, err) -> {
            if (err == null && res != null && res.status == BioResult.Status.SUCCESS) {
                viewModel.onAuthDone(null);
            } else {
                viewModel.onBioAuthFailed();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void showFatalError(@Nullable String detail) {
        showErrorNotice(R.string.oid4vci_failed, R.string.oid4vci_failed, detail, this::finish);
    }
}
