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

package org.omnione.did.ca.ui.vc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.Credential;
import org.omnione.did.ca.data.model.CredentialBadge;
import org.omnione.did.ca.data.model.CredentialClaim;
import org.omnione.did.ca.data.model.BioResult;
import org.omnione.did.ca.databinding.ActivityVcDetailBinding;
import org.omnione.did.ca.ui.biometric.AuthMethodActivity;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.ui.common.ConfirmDialogFragment;
import org.omnione.did.ca.ui.common.CredentialBadgeBinder;
import org.omnione.did.ca.ui.common.CredentialStatusBinder;
import org.omnione.did.ca.ui.common.NoticeDialogFragment;
import org.omnione.did.ca.ui.pin.PinActivity;
import org.omnione.did.ca.util.IntentRouter;

import java.util.List;

@dagger.hilt.android.AndroidEntryPoint
public class VcDetailActivity extends BaseActivity {

    public static final String EXTRA_CREDENTIAL_ID = "extra_credential_id";

    private static final String AFTER_AUTH_REVOKE_PREFIX = "revoke:";

    private ActivityVcDetailBinding binding;
    private VcDetailViewModel viewModel;
    private String credentialId;

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
                                || !afterAuth.startsWith(AFTER_AUTH_REVOKE_PREFIX)) {
                            viewModel.onCancelled();
                            return;
                        }
                        viewModel.onAuthDone(pin);
                    });

    private final ActivityResultLauncher<Intent> authMethodLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK) {
                            viewModel.onCancelled();
                            return;
                        }
                        Intent data = result.getData();
                        String method = data == null ? null
                                : data.getStringExtra(AuthMethodActivity.RESULT_METHOD);
                        if (AuthMethodActivity.METHOD_PIN.equals(method)) {
                            pinAuthLauncher.launch(IntentRouter.pinAuthIntent(this,
                                    AFTER_AUTH_REVOKE_PREFIX + credentialId));
                        } else if (AuthMethodActivity.METHOD_BIOMETRIC.equals(method)) {
                            launchBio();
                        } else {
                            viewModel.onCancelled();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVcDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applySystemBarInsets();

        credentialId = getIntent().getStringExtra(EXTRA_CREDENTIAL_ID);
        if (credentialId == null) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(VcDetailViewModel.class);
        viewModel.getState().observe(this, this::render);
        viewModel.getDeleteCompleted().observe(this, event -> {
            if (event != null && Boolean.TRUE.equals(event.getIfNotHandled())) {

                setResult(RESULT_OK);
                finish();
            }
        });
        viewModel.getDeleteError().observe(this, event -> {
            if (event == null) return;
            Integer messageRes = event.getIfNotHandled();
            if (messageRes == null) return;
            NoticeDialogFragment.newInstance(
                            getText(R.string.common_error),
                            getText(messageRes),
                            getText(R.string.common_ok))
                    .show(getSupportFragmentManager());
        });
        viewModel.getEvent().observe(this, evt -> {
            VcDetailEvent value = evt == null ? null : evt.getIfNotHandled();
            if (value != null) handleEvent(value);
        });
        viewModel.getStatusWarning().observe(this, event -> {
            if (event == null) return;
            Integer messageRes = event.getIfNotHandled();
            if (messageRes == null) return;
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
        });
        viewModel.loadDetail(credentialId);

        binding.backButton.setOnClickListener(v -> finish());
        binding.deleteButton.setOnClickListener(v -> showDeleteDialog());
    }

    private void applySystemBarInsets() {
        applySystemBarInsets(binding.getRoot(), bars -> {
            applyTopInsetPadding(binding.heroAppBar, bars.top);
            applyBottomInsetPadding(binding.bodyScroll, 0, bars.bottom);
        });
    }

    private void render(VcDetailUiState state) {
        if (state == null) return;

        if (state.loading) {
            showProgress();
            binding.sectionsContainer.removeAllViews();
            return;
        }
        if (state.deleting) {
            showProgress();
            return;
        }
        dismissProgress();

        if (state.errorMessageRes != null) {
            NoticeDialogFragment.newInstance(
                            getText(R.string.notice_vc_load_failed_title),
                            getText(state.errorMessageRes),
                            getText(R.string.common_ok))
                    .setOnConfirmed(this::finish)
                    .show(getSupportFragmentManager());
            return;
        }

        Credential cred = state.credential;
        if (cred == null) {
            finish();
            return;
        }

        bindHero(cred);

        binding.sectionsContainer.removeAllViews();
        if (state.zkpProofs == null) {
            addSection(getString(R.string.vc_detail_section_certificate),
                    R.drawable.bg_section_marker_pale,
                    R.drawable.bg_settings_info_card,
                    state.certificateClaims);
        } else {
            addSection(getString(R.string.vc_detail_section_certificate),
                    R.drawable.bg_section_marker_pale,
                    R.drawable.bg_settings_info_card,
                    state.certificateClaims);

            View gap = new View(this);
            gap.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(20)));
            binding.sectionsContainer.addView(gap);

            addSection(getString(R.string.vc_detail_section_zkp),
                    R.drawable.bg_section_marker_zkp,
                    R.drawable.bg_vc_zkp_card,
                    state.zkpProofs);
        }
    }

    private void bindHero(@NonNull Credential cred) {
        binding.heroName.setText(cred.name);
        binding.credentialIssuer.setText(cred.issuer);

        CredentialStatusBinder.bind(binding.statusBadge, binding.statusDot, binding.statusText, cred.status);
        bindFormat(cred.badge);
        binding.zkpBadge.setVisibility(cred.zkp ? View.VISIBLE : View.GONE);

        String placeholder = getString(R.string.docs_placeholder);
        binding.credentialIssued.setText(cred.issued != null ? cred.issued : placeholder);
        binding.credentialValid.setText(cred.valid != null ? cred.valid : placeholder);
    }

    private void bindFormat(@NonNull CredentialBadge badge) {
        CredentialBadgeBinder.bind(binding.formatBadge, badge);
    }

    private void addSection(@NonNull String title,
                            int markerRes,
                            int cardBgRes,
                            @NonNull List<CredentialClaim> rows) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View section = inflater.inflate(R.layout.view_vc_section,
                binding.sectionsContainer, false);
        ((TextView) section.findViewById(R.id.sectionTitle)).setText(title);
        section.findViewById(R.id.sectionMarker).setBackgroundResource(markerRes);
        LinearLayout card = section.findViewById(R.id.sectionCard);
        card.setBackgroundResource(cardBgRes);
        binding.sectionsContainer.addView(section);

        for (int i = 0; i < rows.size(); i++) {
            CredentialClaim row = rows.get(i);
            boolean isLast = i == rows.size() - 1;
            View child;
            if (row.kind == CredentialClaim.Kind.FLAT) {
                child = buildFlatRow(inflater, card, row);
            } else {
                child = buildGroupRow(inflater, card, row);
            }
            card.addView(child);
            if (!isLast) {
                card.addView(buildDivider(card));
            }
        }
    }

    @NonNull
    private View buildFlatRow(@NonNull LayoutInflater inflater,
                              @NonNull LinearLayout parent,
                              @NonNull CredentialClaim row) {
        View v = inflater.inflate(R.layout.item_vc_claim_row, parent, false);
        TextView labelView = v.findViewById(R.id.claimLabel);
        if (row.label.isEmpty()) {
            labelView.setVisibility(View.GONE);
        } else {
            labelView.setText(row.label);
        }
        ((TextView) v.findViewById(R.id.claimValue)).setText(row.value);
        return v;
    }

    @NonNull
    private View buildGroupRow(@NonNull LayoutInflater inflater,
                               @NonNull LinearLayout parent,
                               @NonNull CredentialClaim row) {
        View v = inflater.inflate(R.layout.item_vc_claim_group, parent, false);
        TextView label = v.findViewById(R.id.groupLabel);
        ImageView chev = v.findViewById(R.id.groupChevron);
        LinearLayout itemsContainer = v.findViewById(R.id.groupItems);

        label.setText(row.label);

        for (int i = 0; i < row.items.size(); i++) {
            CredentialClaim child = row.items.get(i);
            boolean isLast = i == row.items.size() - 1;
            View childView = buildFlatRow(inflater, itemsContainer, child);
            itemsContainer.addView(childView);
            if (!isLast) {
                itemsContainer.addView(buildDivider(itemsContainer));
            }
        }

        v.findViewById(R.id.groupHeader).setOnClickListener(view -> {
            boolean expanded = itemsContainer.getVisibility() == View.VISIBLE;
            itemsContainer.setVisibility(expanded ? View.GONE : View.VISIBLE);
            chev.setImageResource(expanded ? R.drawable.ic_chev_d : R.drawable.ic_chev_u);
        });

        return v;
    }

    @NonNull
    private View buildDivider(@NonNull LinearLayout parent) {
        View divider = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divider.setLayoutParams(lp);
        divider.setBackgroundResource(R.color.brand_divider);
        return divider;
    }

    private void showDeleteDialog() {
        ConfirmDialogFragment.newInstance(
                        getText(R.string.vc_detail_delete_title),
                        getText(R.string.vc_detail_delete_message),
                        getText(R.string.common_cancel),
                        getText(R.string.vc_detail_delete_confirm))
                .setOnConfirmed(this::performDelete)
                .show(getSupportFragmentManager(), "delete_vc");
    }

    private void performDelete() {
        viewModel.delete(credentialId);
    }

    private void handleEvent(@NonNull VcDetailEvent value) {
        if (value instanceof VcDetailEvent.LaunchBio) {
            launchBio();
        } else if (value instanceof VcDetailEvent.LaunchPin) {
            VcDetailEvent.LaunchPin lp = (VcDetailEvent.LaunchPin) value;
            Intent intent = IntentRouter.pinAuthIntent(this, lp.afterAuthTag);
            pinAuthLauncher.launch(intent);
        } else if (value instanceof VcDetailEvent.LaunchAuthMethodChooser) {
            authMethodLauncher.launch(IntentRouter.authMethodChooserIntent(this));
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

}
