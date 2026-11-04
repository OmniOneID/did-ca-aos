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

package org.omnione.did.ca.ui.vp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.ReferentOption;
import org.omnione.did.ca.data.model.VpPlainAttribute;
import org.omnione.did.ca.data.model.VpClaimKeys;
import org.omnione.did.ca.data.model.VpRequestMode;
import org.omnione.did.ca.data.model.VpDocEntry;
import org.omnione.did.ca.data.model.VpRequestedClaim;
import org.omnione.did.ca.data.model.VpRequestedClaimGroup;
import org.omnione.did.ca.data.model.VpRequestedDoc;
import org.omnione.did.ca.data.model.VpRevealAttribute;
import org.omnione.did.ca.data.model.VpScenario;
import org.omnione.did.ca.data.model.VpZkpRequest;
import org.omnione.did.ca.data.model.ZkpAttributeReferent;
import org.omnione.did.ca.data.model.BioResult;
import org.omnione.did.ca.databinding.ActivityVpRequestBinding;
import org.omnione.did.ca.ui.Event;
import org.omnione.did.ca.ui.biometric.AuthMethodActivity;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.ui.common.NoticeDialogFragment;
import org.omnione.did.ca.ui.common.ProgressDialogFragment;
import org.omnione.did.ca.ui.pin.PinActivity;
import org.omnione.did.ca.ui.referent.ReferentListActivity;
import org.omnione.did.ca.util.ClaimImages;
import org.omnione.did.ca.util.IntentRouter;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@dagger.hilt.android.AndroidEntryPoint
public class VpRequestActivity extends BaseActivity {

    public static final String EXTRA_PAYLOAD = "extra_payload";

    private static final int BOTTOM_BAR_BASE_PADDING_BOTTOM_DP = 16;

    private static final int DOC_CARD_GAP_DP = 12;
    private static final int CARD_PADDING_H_DP = 16;
    private static final int SECTION_HEAD_TOP_FIRST_DP = 8;
    private static final int SECTION_HEAD_TOP_DP = 14;

    private static final int CLAIM_IMAGE_INSET_DP = 110;

    private static final int CLAIM_IMAGE_INSET_MULTI_DP = 126;

    private static final int RADIO_TOUCH_INSET_START_DP = 14;
    private static final int RADIO_TOUCH_INSET_END_DP = 12;
    private static final int RADIO_TOUCH_INSET_V_DP = 14;

    private ActivityVpRequestBinding binding;
    private VpRequestViewModel viewModel;
    private boolean proximityMode;

    private final Map<VpRequestedClaim, Bitmap> claimImages = new IdentityHashMap<>();

    private final ActivityResultLauncher<Intent> pinAuthLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK) {
                            viewModel.onAuthCancelled();
                            return;
                        }
                        Intent data = result.getData();
                        if (data == null) {
                            viewModel.onAuthCancelled();
                            return;
                        }
                        String afterAuth = data.getStringExtra(PinActivity.RESULT_AFTER_AUTH);
                        String pin = data.getStringExtra(PinActivity.RESULT_PIN);
                        if (!VpRequestEvent.LaunchPin.AFTER_AUTH_TAG.equals(afterAuth)) {
                            viewModel.onAuthCancelled();
                            return;
                        }
                        viewModel.onAuthDone(pin);
                    });

    private final ActivityResultLauncher<Intent> authMethodLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK) {
                            viewModel.onAuthCancelled();
                            return;
                        }
                        Intent data = result.getData();
                        String method = data == null ? null
                                : data.getStringExtra(AuthMethodActivity.RESULT_METHOD);
                        if (AuthMethodActivity.METHOD_PIN.equals(method)) {
                            pinAuthLauncher.launch(IntentRouter.pinAuthIntent(this,
                                    VpRequestEvent.LaunchPin.AFTER_AUTH_TAG));
                        } else if (AuthMethodActivity.METHOD_BIOMETRIC.equals(method)) {
                            launchBio();
                        } else {
                            viewModel.onAuthCancelled();
                        }
                    });

    private final ActivityResultLauncher<Intent> referentListLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK) return;
                        Intent data = result.getData();
                        if (data == null) return;
                        String referentKey = data.getStringExtra(
                                ReferentListActivity.EXTRA_REFERENT_KEY);
                        String value = data.getStringExtra(
                                ReferentListActivity.EXTRA_SELECTED_VALUE);
                        String credentialId = data.getStringExtra(
                                ReferentListActivity.EXTRA_SELECTED_CRED_ID);
                        if (referentKey == null || value == null || credentialId == null) return;
                        VpRequestUiState s = viewModel.getState().getValue();
                        if (s == null || s.scenario == null || s.scenario.zkpRequest == null) return;
                        for (java.util.Map.Entry<String, ZkpAttributeReferent> e
                                : s.scenario.zkpRequest.referentsByLabel.entrySet()) {
                            if (e.getValue().referentKey.equals(referentKey)) {
                                viewModel.setReferentSelection(e.getKey(), value, credentialId);
                                return;
                            }
                        }
                        for (ZkpAttributeReferent pred : s.scenario.zkpRequest.predicates) {
                            if (pred.referentKey.equals(referentKey)) {
                                viewModel.setPredicateSelection(referentKey, credentialId);
                                return;
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVpRequestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applySystemBarInsets();

        viewModel = new ViewModelProvider(this).get(VpRequestViewModel.class);
        proximityMode = IntentRouter.MODE_PROXIMITY.equals(
                getIntent().getStringExtra(IntentRouter.EXTRA_MODE));

        binding.backButton.setOnClickListener(v -> finishConsent(false));
        binding.rejectButton.setOnClickListener(v -> finishConsent(false));
        binding.acceptButton.setOnClickListener(v -> viewModel.onSubmitClicked());

        viewModel.getState().observe(this, this::render);
        viewModel.getEvent().observe(this, this::onEvent);

        if (savedInstanceState == null && viewModel.getState().getValue() != null
                && viewModel.getState().getValue().scenario == null
                && viewModel.getState().getValue().loading) {
            if (proximityMode) {
                viewModel.startProximity();
            } else {
                String payload = getIntent().getStringExtra(EXTRA_PAYLOAD);
                if (payload == null && getIntent().getData() != null) {
                    payload = getIntent().getDataString();
                }
                if (payload == null) {
                    showInvalidPayload();
                    return;
                }
                viewModel.start(payload);
            }
        }
    }

    private void applySystemBarInsets() {
        applySystemBarInsets(binding.getRoot(), bars -> {
            applyTopInsetPadding(binding.appBar, bars.top);
            applyBottomInsetPadding(binding.bottomBar,
                    dp(BOTTOM_BAR_BASE_PADDING_BOTTOM_DP), bars.bottom);
        });
    }

    private void render(@NonNull VpRequestUiState state) {
        if (state.loading || state.inFlight) {
            ProgressDialogFragment.showIfAbsent(getSupportFragmentManager());
        } else {
            ProgressDialogFragment.dismissIfShown(getSupportFragmentManager());
        }

        VpScenario scenario = state.scenario;
        if (scenario == null) {
            binding.verifierName.setText("");
            binding.detailsContainer.removeAllViews();
            binding.acceptButton.setEnabled(false);
            return;
        }

        if (scenario.mode == VpRequestMode.PROXIMITY) {
            binding.verifierName.setText(R.string.vp_request_proximity_verifier);
        } else {
            binding.verifierName.setText(scenario.verifier.name);
        }
        renderDetails(state, scenario);
        binding.acceptButton.setEnabled(!state.inFlight && state.canSubmit());
    }

    private void onEvent(@NonNull Event<VpRequestEvent> evt) {
        VpRequestEvent value = evt.getIfNotHandled();
        if (value == null) return;
        if (value instanceof VpRequestEvent.LaunchPin) {
            pinAuthLauncher.launch(IntentRouter.pinAuthIntent(this,
                    VpRequestEvent.LaunchPin.AFTER_AUTH_TAG));
        } else if (value instanceof VpRequestEvent.LaunchBio) {
            launchBio();
        } else if (value instanceof VpRequestEvent.LaunchAuthMethodChooser) {
            authMethodLauncher.launch(IntentRouter.authMethodChooserIntent(this));
        } else if (value instanceof VpRequestEvent.ShowSuccess) {
            showSuccessDialog();
        } else if (value instanceof VpRequestEvent.ShowError) {
            VpRequestEvent.ShowError e = (VpRequestEvent.ShowError) value;
            showErrorNotice(e.titleRes, e.messageRes, e.detailMessage,
                    () -> finishConsent(false), false);
        }
    }

    private void finishConsent(boolean success) {
        if (proximityMode) {
            setResult(success ? Activity.RESULT_OK : Activity.RESULT_CANCELED);
        }
        finish();
    }

    private void launchBio() {
        viewModel.getBioRepo().authenticateBiometric(this).whenCompleteAsync((result, err) -> {
            if (err == null && result != null && result.status == BioResult.Status.SUCCESS) {
                viewModel.onAuthDone( null);
            } else {
                viewModel.onBioAuthFailed();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void renderDetails(@NonNull VpRequestUiState state, @NonNull VpScenario scenario) {
        binding.detailsContainer.removeAllViews();
        if (scenario.mode == VpRequestMode.OPENDID_VC || scenario.mode == VpRequestMode.OID4VP
                || scenario.mode == VpRequestMode.PROXIMITY) {
            List<VpRequestedDoc> candidates = scenario.candidates;
            boolean multi = scenario.isMultiCandidate();
            for (int i = 0; i < candidates.size(); i++) {
                VpRequestedDoc doc = candidates.get(i);
                View card = multi
                        ? buildOptionCard(doc, i, candidates.size(), state)
                        : buildSingleCard(doc, state);
                attachCard(card, i > 0);
            }
        } else if (scenario.zkpRequest != null) {
            View card = buildZkpDocCard(scenario.zkpRequest, state);
            attachCard(card, false);
        }
    }

    private void attachCard(@NonNull View card, boolean withTopMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = withTopMargin ? dp(DOC_CARD_GAP_DP) : 0;
        card.setLayoutParams(lp);
        binding.detailsContainer.addView(card);
    }

    private View buildSingleCard(@NonNull VpRequestedDoc doc, @NonNull VpRequestUiState state) {
        View card = buildDocSection(binding.detailsContainer, doc, state,
                true, CLAIM_IMAGE_INSET_DP);
        card.setBackgroundResource(R.drawable.bg_vp_section_card);
        card.setPadding(dp(CARD_PADDING_H_DP), 0, dp(CARD_PADDING_H_DP), 0);
        return card;
    }

    private View buildOptionCard(@NonNull VpRequestedDoc doc, int index, int total,
                                 @NonNull VpRequestUiState state) {
        View card = LayoutInflater.from(this)
                .inflate(R.layout.item_vp_option_card, binding.detailsContainer, false);

        boolean selected = doc.docId.equals(state.selectedOptionId);
        View header = card.findViewById(R.id.optionHeader);
        View radio = card.findViewById(R.id.optionRadio);
        TextView title = card.findViewById(R.id.optionTitle);
        FrameLayout bodySlot = card.findViewById(R.id.optionBodySlot);

        card.setBackgroundResource(selected
                ? R.drawable.bg_vp_option_card_selected
                : R.drawable.bg_vp_option_card);
        header.setBackgroundResource(selected
                ? R.drawable.bg_vp_option_header_selected
                : R.drawable.bg_vp_option_header);
        radio.setBackgroundResource(selected
                ? R.drawable.bg_vp_radio_on
                : R.drawable.bg_vp_radio_off);
        title.setText(getString(R.string.vp_request_option_title, index + 1, total));
        radio.setOnClickListener(v -> viewModel.selectOption(doc.docId));
        expandRadioTouchArea(header, radio);

        bodySlot.addView(buildDocSection(bodySlot, doc, state,
                selected, CLAIM_IMAGE_INSET_MULTI_DP));
        return card;
    }

    private void expandRadioTouchArea(@NonNull View header, @NonNull View radio) {
        header.post(() -> {
            Rect hit = new Rect();
            radio.getHitRect(hit);
            hit.left -= dp(RADIO_TOUCH_INSET_START_DP);
            hit.right += dp(RADIO_TOUCH_INSET_END_DP);
            hit.top -= dp(RADIO_TOUCH_INSET_V_DP);
            hit.bottom += dp(RADIO_TOUCH_INSET_V_DP);
            header.setTouchDelegate(new TouchDelegate(hit, radio));
        });
    }

    private View buildDocSection(@NonNull ViewGroup parent, @NonNull VpRequestedDoc doc,
                                 @NonNull VpRequestUiState state, boolean selectable,
                                 int imageInsetDp) {
        View section = LayoutInflater.from(this)
                .inflate(R.layout.item_vp_doc_section, parent, false);

        TextView title = section.findViewById(R.id.docTitle);
        ImageView chev = section.findViewById(R.id.sectionChevron);
        View header = section.findViewById(R.id.headerRow);
        LinearLayout body = section.findViewById(R.id.bodyContainer);

        title.setText(doc.docTitle);
        boolean expanded = state.isOptionExpanded(doc.docId);
        chev.setImageResource(expanded ? R.drawable.ic_chev_u : R.drawable.ic_chev_d);
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        header.setOnClickListener(v -> viewModel.toggleOption(doc.docId));

        if (expanded) {
            appendClaimSections(body, doc, state, selectable, imageInsetDp);
        }
        return section;
    }

    private void appendClaimSections(@NonNull LinearLayout body, @NonNull VpRequestedDoc doc,
                                     @NonNull VpRequestUiState state, boolean selectable,
                                     int imageInsetDp) {
        List<VpDocEntry> required = VpRequestUiState.requiredEntries(doc);
        List<VpDocEntry> optional = VpRequestUiState.optionalEntries(doc);

        if (!required.isEmpty()) {
            body.addView(buildSectionHead(body, R.string.vp_request_section_required,
                    true, null, false));
            appendEntries(body, doc, required, state, false, imageInsetDp);
        }
        if (!optional.isEmpty()) {
            Runnable onSelectAll = selectable ? () -> viewModel.toggleSelectAll(doc.docId) : null;
            body.addView(buildSectionHead(body, R.string.vp_request_section_optional,
                    required.isEmpty(), onSelectAll, state.allOptionalChecked(doc)));
            appendEntries(body, doc, optional, state, selectable, imageInsetDp);
        }
    }

    private void appendEntries(@NonNull LinearLayout body, @NonNull VpRequestedDoc doc,
                               @NonNull List<VpDocEntry> entries, @NonNull VpRequestUiState state,
                               boolean showControl, int imageInsetDp) {
        for (int i = 0; i < entries.size(); i++) {
            VpDocEntry entry = entries.get(i);
            View row;
            if (entry.isGroup()) {
                row = buildSdGroup(body, doc.docId, entry.group, state, showControl, imageInsetDp);
            } else {
                String claimKey = VpClaimKeys.flat(doc.docId, entry.claim.code);
                row = buildSdClaimRow(body, entry.claim, claimKey, showControl,
                        state.isChecked(claimKey), imageInsetDp);
            }
            appendWithDivider(body, row, i < entries.size() - 1);
        }
    }

    private View buildSectionHead(@NonNull ViewGroup parent, @StringRes int labelRes,
                                  boolean isFirst, @Nullable Runnable onSelectAll, boolean allOn) {
        View head = LayoutInflater.from(this)
                .inflate(R.layout.item_vp_claim_section_head, parent, false);

        ((TextView) head.findViewById(R.id.sectionHeadLabel)).setText(labelRes);
        head.findViewById(R.id.sectionHeadTopDivider)
                .setVisibility(isFirst ? View.GONE : View.VISIBLE);

        View row = head.findViewById(R.id.sectionHeadRow);
        row.setPadding(row.getPaddingLeft(),
                dp(isFirst ? SECTION_HEAD_TOP_FIRST_DP : SECTION_HEAD_TOP_DP),
                row.getPaddingRight(), row.getPaddingBottom());

        View selectAllRow = head.findViewById(R.id.selectAllRow);
        if (onSelectAll == null) {
            selectAllRow.setVisibility(View.GONE);
        } else {
            selectAllRow.setVisibility(View.VISIBLE);
            head.findViewById(R.id.selectAllBox).setBackgroundResource(allOn
                    ? R.drawable.bg_vp_checkbox_small_on
                    : R.drawable.bg_vp_checkbox_small_off);
            head.findViewById(R.id.selectAllCheckIcon)
                    .setVisibility(allOn ? View.VISIBLE : View.GONE);
            selectAllRow.setOnClickListener(v -> onSelectAll.run());
        }
        return head;
    }

    private View buildSdClaimRow(@NonNull ViewGroup parent,
                                 @NonNull VpRequestedClaim claim,
                                 @NonNull String claimKey,
                                 boolean showControl,
                                 boolean checked,
                                 int imageInsetDp) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_vp_claim_sd, parent, false);
        TextView labelView = row.findViewById(R.id.claimLabel);
        if (claim.label.isEmpty()) {
            labelView.setVisibility(View.GONE);
        } else {
            labelView.setText(claim.label);
        }
        ((TextView) row.findViewById(R.id.claimValue)).setText(claim.value);
        bindClaimImage(row, claim, imageInsetDp);

        View checkboxFrame = row.findViewById(R.id.checkboxFrame);

        if (!showControl) {
            checkboxFrame.setVisibility(View.GONE);
            return row;
        }
        View checkIcon = row.findViewById(R.id.checkIcon);
        checkboxFrame.setBackgroundResource(
                checked ? R.drawable.bg_vp_checkbox_on : R.drawable.bg_vp_checkbox_off);
        checkIcon.setVisibility(checked ? View.VISIBLE : View.GONE);
        checkboxFrame.setOnClickListener(v -> viewModel.toggleClaim(claimKey));
        return row;
    }

    private void bindClaimImage(@NonNull View row, @NonNull VpRequestedClaim claim,
                                int imageInsetDp) {
        if (claim.imageBytes == null) {
            return;
        }
        Bitmap bitmap = claimImages.get(claim);
        if (bitmap == null) {
            bitmap = ClaimImages.decode(this, claim.imageBytes);
            if (bitmap == null) {
                return;
            }
            claimImages.put(claim, bitmap);
        }
        ImageView image = row.findViewById(R.id.claimImage);
        image.setImageBitmap(bitmap);
        image.setMaxWidth(ClaimImages.maxWidthPx(this, imageInsetDp));
        image.setContentDescription(claim.label + " image");
        image.setVisibility(View.VISIBLE);
        row.findViewById(R.id.claimValue).setVisibility(View.GONE);
    }

    private View buildSdGroup(@NonNull ViewGroup parent,
                              @NonNull String optionId,
                              @NonNull VpRequestedClaimGroup group,
                              @NonNull VpRequestUiState state,
                              boolean showControl,
                              int imageInsetDp) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_vp_group_sd, parent, false);
        TextView title = view.findViewById(R.id.groupTitle);
        ImageView chev = view.findViewById(R.id.groupChevron);
        View header = view.findViewById(R.id.groupHeader);
        View groupCheckbox = view.findViewById(R.id.groupCheckbox);
        View groupCheckIcon = view.findViewById(R.id.groupCheckIcon);
        LinearLayout body = view.findViewById(R.id.groupBody);

        title.setText(group.title);
        String groupKey = optionId + ":" + group.title;
        boolean expanded = state.isGroupExpanded(groupKey);
        chev.setImageResource(expanded ? R.drawable.ic_chev_u : R.drawable.ic_chev_d);
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        header.setOnClickListener(v -> viewModel.toggleGroup(groupKey));

        List<String> groupClaimKeys = VpClaimKeys.groupChildren(optionId, group);
        if (!showControl) {
            groupCheckbox.setVisibility(View.GONE);
        } else {
            groupCheckbox.setVisibility(View.VISIBLE);
            boolean groupChecked = !groupClaimKeys.isEmpty()
                    && state.isChecked(groupClaimKeys.get(0));
            groupCheckbox.setBackgroundResource(
                    groupChecked ? R.drawable.bg_vp_checkbox_on : R.drawable.bg_vp_checkbox_off);
            groupCheckIcon.setVisibility(groupChecked ? View.VISIBLE : View.GONE);
            groupCheckbox.setOnClickListener(v -> viewModel.toggleGroupClaims(groupClaimKeys));
        }

        if (expanded) {
            int total = group.items.size();
            int idx = 0;
            for (VpRequestedClaim claim : group.items) {
                String claimKey = VpClaimKeys.groupChild(optionId, group.title, claim.code);
                View row = buildSdClaimRow(body, claim, claimKey, false, false, imageInsetDp);
                appendWithDivider(body, row, idx < total - 1);
                idx++;
            }
        }
        return view;
    }

    private View buildZkpDocCard(@NonNull VpZkpRequest req, @NonNull VpRequestUiState state) {
        View card = LayoutInflater.from(this)
                .inflate(R.layout.item_vp_zkp_section, binding.detailsContainer, false);
        LinearLayout body = card.findViewById(R.id.bodyContainer);

        boolean hasAttrs = !req.attributes.isEmpty();
        boolean hasPredicates = !req.predicates.isEmpty();
        boolean hasSelfAttrs = !req.selfAttributes.isEmpty();
        if (hasAttrs) {
            addZkpAttributeGroup(body, getString(R.string.vp_request_zkp_attributes), req.attributes, state,
                    hasPredicates || hasSelfAttrs);
        }
        if (hasPredicates) {
            addZkpPredicateGroup(body, req.predicates, state, hasSelfAttrs);
        }
        if (hasSelfAttrs) {
            addZkpPlainGroup(body, getString(R.string.vp_request_zkp_self_attributes), req.selfAttributes, false);
        }
        return card;
    }

    private void addZkpAttributeGroup(@NonNull LinearLayout parent,
                                      @NonNull String groupTitle,
                                      @NonNull List<VpRevealAttribute> items,
                                      @NonNull VpRequestUiState state,
                                      boolean appendDivider) {
        if (items.isEmpty()) return;
        parent.addView(buildZkpGroupHeader(parent, groupTitle));
        LinearLayout rows = buildZkpRowContainer();
        for (int i = 0; i < items.size(); i++) {
            View row = buildZkpAttributeRow(rows, items.get(i), state);
            appendWithDivider(rows, row, i < items.size() - 1);
        }
        parent.addView(rows);
        if (appendDivider) parent.addView(buildGroupDivider(parent));
    }

    private void addZkpPredicateGroup(@NonNull LinearLayout parent,
                                      @NonNull List<ZkpAttributeReferent> items,
                                      @NonNull VpRequestUiState state,
                                      boolean appendDivider) {
        parent.addView(buildZkpGroupHeader(parent, getString(R.string.vp_request_zkp_predicates)));
        LinearLayout rows = buildZkpRowContainer();
        for (int i = 0; i < items.size(); i++) {
            View row = buildZkpPredicateRow(rows, items.get(i), state);
            appendWithDivider(rows, row, i < items.size() - 1);
        }
        parent.addView(rows);
        if (appendDivider) parent.addView(buildGroupDivider(parent));
    }

    private View buildZkpPredicateRow(@NonNull ViewGroup parent,
                                      @NonNull ZkpAttributeReferent pred,
                                      @NonNull VpRequestUiState state) {
        String selectedCredId = state.selectedPredicateCredIds.get(pred.referentKey);
        String selectedValue = null;
        for (ReferentOption opt : pred.options) {
            if (opt.credentialId.equals(selectedCredId)) {
                selectedValue = opt.value;
                break;
            }
        }
        View row = LayoutInflater.from(this).inflate(R.layout.item_vp_attribute_zkp, parent, false);
        ((TextView) row.findViewById(R.id.attrLabel)).setText(pred.label);
        row.findViewById(R.id.eyeIcon).setVisibility(View.GONE);
        TextView value = row.findViewById(R.id.attrValue);
        if (selectedValue == null) {
            value.setText(R.string.vp_request_referent_placeholder);
            value.setTextColor(getResources().getColor(R.color.brand_primary, null));
        } else {
            value.setText(selectedValue);
        }
        String initialValue = selectedValue;
        value.setOnClickListener(v -> launchReferentList(pred, initialValue));
        return row;
    }

    private void addZkpPlainGroup(@NonNull LinearLayout parent,
                                  @NonNull String groupTitle,
                                  @NonNull List<VpPlainAttribute> items,
                                  boolean appendDivider) {
        if (items.isEmpty()) return;
        parent.addView(buildZkpGroupHeader(parent, groupTitle));
        LinearLayout rows = buildZkpRowContainer();
        for (int i = 0; i < items.size(); i++) {
            View row = buildPlainRow(rows, items.get(i).label, items.get(i).value);
            appendWithDivider(rows, row, i < items.size() - 1);
        }
        parent.addView(rows);
        if (appendDivider) parent.addView(buildGroupDivider(parent));
    }

    private LinearLayout buildZkpRowContainer() {
        LinearLayout container = new LinearLayout(this);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(12), 0, 0, dp(6));
        return container;
    }

    private View buildZkpGroupHeader(@NonNull ViewGroup parent, @NonNull String title) {
        TextView t = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        t.setLayoutParams(lp);
        int pad = dp(12);
        t.setPadding(0, pad, 0, pad);
        t.setText(title);
        t.setTextSize(13f);
        t.setTextColor(getResources().getColor(R.color.brand_text_primary, null));
        t.setTypeface(getResources().getFont(R.font.pretendard_bold));
        return t;
    }

    private View buildZkpAttributeRow(@NonNull ViewGroup parent,
                                      @NonNull VpRevealAttribute attr,
                                      @NonNull VpRequestUiState state) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_vp_attribute_zkp, parent, false);
        TextView label = row.findViewById(R.id.attrLabel);
        TextView value = row.findViewById(R.id.attrValue);
        ImageView eye = row.findViewById(R.id.eyeIcon);

        label.setText(attr.label);

        String selectedValue = state.selectedReferents.get(attr.label);
        boolean isPlaceholder = attr.selectable && (attr.value == null) && (selectedValue == null);

        if (isPlaceholder) {
            value.setText(R.string.vp_request_referent_placeholder);
            value.setTextColor(getResources().getColor(R.color.brand_primary, null));
            value.setOnClickListener(v -> launchReferentList(attr.label, null));
        } else if (attr.selectable && selectedValue != null) {
            value.setText(selectedValue);
            value.setTextColor(getResources().getColor(R.color.brand_text_primary, null));
            value.setOnClickListener(v -> launchReferentList(attr.label, selectedValue));
        } else {
            value.setText(attr.value);
            value.setTextColor(getResources().getColor(R.color.brand_text_primary, null));
            value.setOnClickListener(null);
            value.setClickable(false);
        }

        boolean revealed = state.revealedAttrLabels.contains(attr.label);
        eye.setImageResource(revealed ? R.drawable.ic_eye : R.drawable.ic_eye_off);
        eye.setColorFilter(revealed
                ? getResources().getColor(R.color.brand_primary, null)
                : getResources().getColor(R.color.brand_gray500, null));
        eye.setOnClickListener(v -> viewModel.toggleReveal(attr.label));

        return row;
    }

    private View buildPlainRow(@NonNull ViewGroup parent,
                               @NonNull String label,
                               @NonNull String value) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_vp_plain_row, parent, false);
        ((TextView) row.findViewById(R.id.plainLabel)).setText(label);
        ((TextView) row.findViewById(R.id.plainValue)).setText(value);
        return row;
    }

    private void launchReferentList(@NonNull String attrLabel, String initialValue) {
        VpRequestUiState s = viewModel.getState().getValue();
        if (s == null || s.scenario == null || s.scenario.zkpRequest == null) return;
        ZkpAttributeReferent referent = s.scenario.zkpRequest.referentsByLabel.get(attrLabel);
        if (referent == null) return;
        launchReferentList(referent, initialValue);
    }

    private void launchReferentList(@NonNull ZkpAttributeReferent referent, String initialValue) {
        ArrayList<String> labels = new ArrayList<>(referent.options.size());
        ArrayList<String> values = new ArrayList<>(referent.options.size());
        ArrayList<String> credIds = new ArrayList<>(referent.options.size());
        for (ReferentOption opt : referent.options) {
            labels.add(opt.label);
            values.add(opt.value);
            credIds.add(opt.credentialId);
        }
        referentListLauncher.launch(IntentRouter.referentListIntent(
                this, referent.referentKey, referent.label,
                labels, values, credIds, initialValue));
    }

    private void appendWithDivider(@NonNull LinearLayout parent,
                                   @NonNull View row,
                                   boolean addBottomDivider) {
        parent.addView(row);
        if (addBottomDivider) parent.addView(buildRowDivider(parent));
    }

    private View buildRowDivider(@NonNull ViewGroup parent) {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        v.setLayoutParams(lp);
        v.setBackgroundColor(getResources().getColor(R.color.brand_divider, null));
        return v;
    }

    private View buildGroupDivider(@NonNull ViewGroup parent) {
        return buildRowDivider(parent);
    }

    private void showSuccessDialog() {
        NoticeDialogFragment dialog = NoticeDialogFragment.newInstance(this,
                        R.string.vp_request_success_title,
                        R.string.vp_request_success_message,
                        R.string.vp_request_success_button)
                .setOnConfirmed(() -> finishConsent(true));
        dialog.setCancelable(false);
        dialog.show(getSupportFragmentManager());
    }

    private void showInvalidPayload() {
        NoticeDialogFragment dialog = NoticeDialogFragment.newInstance(this,
                        R.string.notice_vp_invalid_title,
                        R.string.notice_vp_invalid_message,
                        R.string.common_ok)
                .setOnConfirmed(this::finish);
        dialog.setCancelable(false);
        dialog.show(getSupportFragmentManager());
    }

}
