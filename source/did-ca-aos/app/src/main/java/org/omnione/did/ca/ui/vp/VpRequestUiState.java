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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.omnione.did.ca.data.model.VpClaimKeys;
import org.omnione.did.ca.data.model.VpRequestMode;
import org.omnione.did.ca.data.model.VpDocEntry;
import org.omnione.did.ca.data.model.VpRequestedDoc;
import org.omnione.did.ca.data.model.VpRevealAttribute;
import org.omnione.did.ca.data.model.VpScenario;
import org.omnione.did.ca.data.model.ZkpAttributeReferent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VpRequestUiState {

    public final boolean loading;
    public final boolean inFlight;
    @Nullable public final VpScenario scenario;

    @NonNull public final Set<String> checkedClaimKeys;
    @NonNull public final Set<String> revealedAttrLabels;
    @NonNull public final Set<String> expandedOptionIds;
    @NonNull public final Set<String> collapsedGroupKeys;
    @NonNull public final Map<String, String> selectedReferents;
    @NonNull public final Map<String, String> selectedReferentCredIds;
    @NonNull public final Map<String, String> selectedPredicateCredIds;
    @Nullable public final String selectedOptionId;

    public VpRequestUiState(boolean loading,
                            boolean inFlight,
                            @Nullable VpScenario scenario,
                            @NonNull Set<String> checkedClaimKeys,
                            @NonNull Set<String> revealedAttrLabels,
                            @NonNull Set<String> expandedOptionIds,
                            @NonNull Set<String> collapsedGroupKeys,
                            @NonNull Map<String, String> selectedReferents,
                            @NonNull Map<String, String> selectedReferentCredIds,
                            @NonNull Map<String, String> selectedPredicateCredIds,
                            @Nullable String selectedOptionId) {
        this.loading = loading;
        this.inFlight = inFlight;
        this.scenario = scenario;
        this.checkedClaimKeys = checkedClaimKeys;
        this.revealedAttrLabels = revealedAttrLabels;
        this.expandedOptionIds = expandedOptionIds;
        this.collapsedGroupKeys = collapsedGroupKeys;
        this.selectedReferents = selectedReferents;
        this.selectedReferentCredIds = selectedReferentCredIds;
        this.selectedPredicateCredIds = selectedPredicateCredIds;
        this.selectedOptionId = selectedOptionId;
    }

    @NonNull
    public static VpRequestUiState initialLoading() {
        return new VpRequestUiState(true, false, null,
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                null);
    }

    @NonNull
    public VpRequestUiState withCheckedClaimKeys(@NonNull Set<String> value) {
        return new VpRequestUiState(loading, inFlight, scenario,
                value, revealedAttrLabels, expandedOptionIds,
                collapsedGroupKeys, selectedReferents, selectedReferentCredIds, selectedPredicateCredIds, selectedOptionId);
    }

    @NonNull
    public VpRequestUiState withRevealedAttrLabels(@NonNull Set<String> value) {
        return new VpRequestUiState(loading, inFlight, scenario,
                checkedClaimKeys, value, expandedOptionIds,
                collapsedGroupKeys, selectedReferents, selectedReferentCredIds, selectedPredicateCredIds, selectedOptionId);
    }

    @NonNull
    public VpRequestUiState withExpandedOptionIds(@NonNull Set<String> value) {
        return new VpRequestUiState(loading, inFlight, scenario,
                checkedClaimKeys, revealedAttrLabels, value,
                collapsedGroupKeys, selectedReferents, selectedReferentCredIds, selectedPredicateCredIds, selectedOptionId);
    }

    @NonNull
    public VpRequestUiState withCollapsedGroupKeys(@NonNull Set<String> value) {
        return new VpRequestUiState(loading, inFlight, scenario,
                checkedClaimKeys, revealedAttrLabels, expandedOptionIds,
                value, selectedReferents, selectedReferentCredIds, selectedPredicateCredIds, selectedOptionId);
    }

    @NonNull
    public VpRequestUiState withSelectedReferents(@NonNull Map<String, String> value) {
        return new VpRequestUiState(loading, inFlight, scenario,
                checkedClaimKeys, revealedAttrLabels, expandedOptionIds,
                collapsedGroupKeys, value, selectedReferentCredIds, selectedPredicateCredIds, selectedOptionId);
    }

    @NonNull
    public VpRequestUiState withSelectedReferentCredIds(@NonNull Map<String, String> value) {
        return new VpRequestUiState(loading, inFlight, scenario,
                checkedClaimKeys, revealedAttrLabels, expandedOptionIds,
                collapsedGroupKeys, selectedReferents, value, selectedPredicateCredIds, selectedOptionId);
    }

    @NonNull
    public VpRequestUiState withSelectedPredicateCredIds(@NonNull Map<String, String> value) {
        return new VpRequestUiState(loading, inFlight, scenario,
                checkedClaimKeys, revealedAttrLabels, expandedOptionIds,
                collapsedGroupKeys, selectedReferents, selectedReferentCredIds, value, selectedOptionId);
    }

    @Nullable
    public VpRequestedDoc selectedCandidate() {
        if (scenario == null || scenario.candidates.isEmpty()) return null;
        if (selectedOptionId != null) {
            for (VpRequestedDoc doc : scenario.candidates) {
                if (doc.docId.equals(selectedOptionId)) return doc;
            }
        }
        return scenario.candidates.get(0);
    }

    public boolean isChecked(@NonNull String claimKey) {
        return checkedClaimKeys.contains(claimKey);
    }

    public boolean isOptionExpanded(@NonNull String optionId) {
        return expandedOptionIds.contains(optionId);
    }

    public boolean isGroupExpanded(@NonNull String groupKey) {
        return !collapsedGroupKeys.contains(groupKey);
    }

    @NonNull
    public static List<VpDocEntry> requiredEntries(@NonNull VpRequestedDoc doc) {
        List<VpDocEntry> out = new ArrayList<>();
        for (VpDocEntry entry : doc.entries) {
            if (entry.isRequired()) out.add(entry);
        }
        return out;
    }

    @NonNull
    public static List<VpDocEntry> optionalEntries(@NonNull VpRequestedDoc doc) {
        List<VpDocEntry> out = new ArrayList<>();
        for (VpDocEntry entry : doc.entries) {
            if (!entry.isRequired()) out.add(entry);
        }
        return out;
    }

    @NonNull
    public static List<String> optionalClaimKeys(@NonNull VpRequestedDoc doc) {
        List<String> keys = new ArrayList<>();
        for (VpDocEntry entry : doc.entries) {
            if (!entry.isRequired()) {
                keys.addAll(VpClaimKeys.entryKeys(doc.docId, entry));
            }
        }
        return keys;
    }

    public boolean allOptionalChecked(@NonNull VpRequestedDoc doc) {
        List<String> keys = optionalClaimKeys(doc);
        if (keys.isEmpty()) return false;
        for (String key : keys) {
            if (!checkedClaimKeys.contains(key)) return false;
        }
        return true;
    }

    @NonNull
    public Set<String> submissionClaimKeys() {
        VpRequestedDoc doc = selectedCandidate();
        if (doc == null) return Collections.emptySet();
        Set<String> keys = new LinkedHashSet<>();
        for (VpDocEntry entry : doc.entries) {
            List<String> entryKeys = VpClaimKeys.entryKeys(doc.docId, entry);
            if (entry.isRequired()) {
                keys.addAll(entryKeys);
            } else {
                for (String key : entryKeys) {
                    if (checkedClaimKeys.contains(key)) keys.add(key);
                }
            }
        }
        return keys;
    }

    public boolean canSubmit() {
        if (scenario == null) return false;
        if (scenario.mode == VpRequestMode.ZKP) {
            if (scenario.zkpRequest == null) return true;
            for (VpRevealAttribute attr : scenario.zkpRequest.attributes) {
                if (!attr.selectable) continue;
                if (attr.value != null) continue;
                if (selectedReferents.get(attr.label) == null) return false;
            }
            for (ZkpAttributeReferent pred : scenario.zkpRequest.predicates) {
                if (selectedPredicateCredIds.get(pred.referentKey) == null) return false;
            }
            return true;
        }

        VpRequestedDoc doc = selectedCandidate();
        if (doc == null) return false;

        for (VpDocEntry entry : doc.entries) {
            if (entry.isRequired()) return true;
        }
        for (String key : optionalClaimKeys(doc)) {
            if (checkedClaimKeys.contains(key)) return true;
        }
        return false;
    }
}
