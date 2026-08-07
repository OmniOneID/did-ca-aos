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
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.BioResult;
import org.omnione.did.ca.data.model.IssuableCredential;
import org.omnione.did.ca.databinding.ActivityIssuerListBinding;
import org.omnione.did.ca.ui.biometric.AuthMethodActivity;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.ui.common.ProgressDialogFragment;
import org.omnione.did.ca.ui.pin.PinActivity;
import org.omnione.did.ca.ui.web.Oid4vciUserInitWebActivity;
import org.omnione.did.ca.util.IntentRouter;

@dagger.hilt.android.AndroidEntryPoint
public class IssuerListActivity extends BaseActivity {

    public static final String EXTRA_PROTOCOL = "extra_protocol";

    private static final int RECYCLER_BASE_PADDING_BOTTOM_DP = 20;

    private ActivityIssuerListBinding binding;
    private IssuerListViewModel viewModel;
    private IssuerListAdapter adapter;

    @Nullable private String pendingAfterAuthTag;

    private final ActivityResultLauncher<Intent> userInitWebLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                            viewModel.onCancelled();
                            return;
                        }
                        String offerUri = result.getData().getStringExtra(
                                Oid4vciUserInitWebActivity.RESULT_OFFER_URI);
                        String expectedIssuer = result.getData().getStringExtra(
                                Oid4vciUserInitWebActivity.EXTRA_EXPECTED_ISSUER);
                        if (offerUri == null || offerUri.isEmpty()) {
                            viewModel.onCancelled();
                            return;
                        }
                        startActivity(IntentRouter.issueByOid4vciIntent(this, offerUri, expectedIssuer));
                    });

    private final ActivityResultLauncher<Intent> pinAuthLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK) {
                            viewModel.onCancelled();
                            return;
                        }
                        Intent data = result.getData();
                        if (data == null) {
                            viewModel.onCancelled();
                            return;
                        }
                        String afterAuth = data.getStringExtra(PinActivity.RESULT_AFTER_AUTH);
                        String pin = data.getStringExtra(PinActivity.RESULT_PIN);
                        if (afterAuth == null
                                || !afterAuth.startsWith(IssuerListViewModel.AFTER_AUTH_TAG_PREFIX)) {
                            viewModel.onCancelled();
                            return;
                        }
                        viewModel.onAuthDone(pin);
                    });

    private final ActivityResultLauncher<Intent> authMethodLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                            viewModel.onCancelled();
                            return;
                        }
                        String method = result.getData().getStringExtra(AuthMethodActivity.RESULT_METHOD);
                        if (AuthMethodActivity.METHOD_PIN.equals(method)) {
                            pinAuthLauncher.launch(IntentRouter.pinAuthIntent(this, pendingAfterAuthTag));
                        } else if (AuthMethodActivity.METHOD_BIOMETRIC.equals(method)) {
                            launchBio();
                        } else {
                            viewModel.onCancelled();
                        }
                    });

    private final ActivityResultLauncher<Intent> claimWebLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            viewModel.onClaimWebDone();
                        } else {
                            viewModel.onClaimWebCancelled();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityIssuerListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applySystemBarInsets();

        binding.backButton.setOnClickListener(v -> finish());

        viewModel = new ViewModelProvider(this).get(IssuerListViewModel.class);

        IssueProtocol protocol = IssueProtocol.OPEN_DID;
        String protoName = getIntent().getStringExtra(EXTRA_PROTOCOL);
        if (protoName != null) {
            try { protocol = IssueProtocol.valueOf(protoName); }
            catch (IllegalArgumentException ignored) {  }
        }
        viewModel.setProtocol(protocol);

        adapter = new IssuerListAdapter(item -> viewModel.onCardSelected(item));
        binding.issuerRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.issuerRecycler.setAdapter(adapter);

        viewModel.getState().observe(this, this::render);
        viewModel.getEvent().observe(this, evt -> {
            IssuerListEvent value = evt == null ? null : evt.getIfNotHandled();
            if (value != null) handleEvent(value);
        });

        viewModel.loadCatalog();
    }

    private void applySystemBarInsets() {
        applySystemBarInsets(binding.getRoot(), bars -> {
            applyTopInsetPadding(binding.appBar, bars.top);
            applyBottomInsetPadding(binding.issuerRecycler,
                    dp(RECYCLER_BASE_PADDING_BOTTOM_DP), bars.bottom);
        });
    }

    private void render(IssuerListUiState state) {
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
            return;
        }

        if (!state.loading && !state.inFlight) {
            adapter.submit(state.items);
            boolean empty = state.items.isEmpty();
            binding.issuerEmpty.setText(viewModel.getProtocol() == IssueProtocol.OID4VCI
                    ? R.string.oid4vci_select_empty
                    : R.string.issuer_list_all_added);
            binding.issuerEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.issuerRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);

            String issuerName = firstNonEmptyIssuerName(state.items);
            binding.sectionLabel.setText(issuerName != null
                    ? issuerName
                    : getString(R.string.issuer_list_section));
        }
    }

    private static String firstNonEmptyIssuerName(@NonNull java.util.List<IssuableCredential> items) {
        for (IssuableCredential c : items) {
            if (c.issuerName != null && !c.issuerName.trim().isEmpty()) {
                return c.issuerName;
            }
        }
        return null;
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

    private void handleEvent(@NonNull IssuerListEvent value) {
        if (value instanceof IssuerListEvent.LaunchAuthMethodChooser) {
            IssuerListEvent.LaunchAuthMethodChooser lc =
                    (IssuerListEvent.LaunchAuthMethodChooser) value;
            pendingAfterAuthTag = lc.afterAuthTag;
            authMethodLauncher.launch(IntentRouter.authMethodChooserIntent(this));
        } else if (value instanceof IssuerListEvent.LaunchBio) {
            launchBio();
        } else if (value instanceof IssuerListEvent.LaunchPin) {
            IssuerListEvent.LaunchPin lp = (IssuerListEvent.LaunchPin) value;
            Intent intent = IntentRouter.pinAuthIntent(this, lp.afterAuthTag);
            pinAuthLauncher.launch(intent);
        } else if (value instanceof IssuerListEvent.LaunchClaimWeb) {
            IssuerListEvent.LaunchClaimWeb cw = (IssuerListEvent.LaunchClaimWeb) value;
            Intent intent = IntentRouter.claimWebIntent(this, cw.url, cw.afterAuthTag);
            claimWebLauncher.launch(intent);
        } else if (value instanceof IssuerListEvent.LaunchUserInitWeb) {
            IssuerListEvent.LaunchUserInitWeb uw = (IssuerListEvent.LaunchUserInitWeb) value;
            userInitWebLauncher.launch(IntentRouter.oid4vciUserInitWebIntent(
                    this, uw.startUrl, uw.expectedIssuer));
        } else if (value instanceof IssuerListEvent.ShowToast) {
            IssuerListEvent.ShowToast t = (IssuerListEvent.ShowToast) value;
            Toast.makeText(this, t.messageRes, Toast.LENGTH_SHORT).show();
        } else if (value instanceof IssuerListEvent.ShowError) {
            IssuerListEvent.ShowError e = (IssuerListEvent.ShowError) value;
            showErrorNotice(e.titleRes, e.messageRes, e.detailMessage, () -> {
                if (e.fatalReturnToDocs) finish();
            });
        }
    }
}
