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

import org.omnione.did.ca.data.model.VpRequestMode;
import org.omnione.did.ca.data.model.VpDocEntry;
import org.omnione.did.ca.data.model.VpRequestedClaim;
import org.omnione.did.ca.data.model.VpRequestedClaimGroup;
import org.omnione.did.ca.data.model.VpRequestedDoc;
import org.omnione.did.ca.data.model.VpRevealAttribute;
import org.omnione.did.ca.data.model.VpScenario;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public final class VpRequestUiState {

    public final boolean loading;
    public final boolean inFlight;
    @Nullable public final VpScenario scenario;

    @NonNull public final Set<String> uncheckedClaimKeys;
    @NonNull public final Set<String> revealedAttrLabels;
    @NonNull public final Set<String> collapsedSections;
    @NonNull public final Set<String> expandedGroups;
    @NonNull public final Map<String, String> selectedReferents;
    @NonNull public final Map<String, String> selectedReferentCredIds;

    public VpRequestUiState(boolean loading,
                            boolean inFlight,
                            @Nullable VpScenario scenario,
                            @NonNull Set<String> uncheckedClaimKeys,
                            @NonNull Set<String> revealedAttrLabels,
                            @NonNull Set<String> collapsedSections,
                            @NonNull Set<String> expandedGroups,
                            @NonNull Map<String, String> selectedReferents,
                            @NonNull Map<String, String> selectedReferentCredIds) {
        this.loading = loading;
        this.inFlight = inFlight;
        this.scenario = scenario;
        this.uncheckedClaimKeys = uncheckedClaimKeys;
        this.revealedAttrLabels = revealedAttrLabels;
        this.collapsedSections = collapsedSections;
        this.expandedGroups = expandedGroups;
        this.selectedReferents = selectedReferents;
        this.selectedReferentCredIds = selectedReferentCredIds;
    }

    @NonNull
    public static VpRequestUiState initialLoading() {
        return new VpRequestUiState(true, false, null,
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptyMap(),
                Collections.emptyMap());
    }

    @NonNull
    public VpRequestUiState withUncheckedClaimKeys(@NonNull Set<String> value) {
        return new VpRequestUiState(loading, inFlight, scenario,
                value, revealedAttrLabels, collapsedSections,
                expandedGroups, selectedReferents, selectedReferentCredIds);
    }

    @NonNull
    public VpRequestUiState withRevealedAttrLabels(@NonNull Set<String> value) {
        return new VpRequestUiState(loading, inFlight, scenario,
                uncheckedClaimKeys, value, collapsedSections,
                expandedGroups, selectedReferents, selectedReferentCredIds);
    }

    @NonNull
    public VpRequestUiState withCollapsedSections(@NonNull Set<String> value) {
        return new VpRequestUiState(loading, inFlight, scenario,
                uncheckedClaimKeys, revealedAttrLabels, value,
                expandedGroups, selectedReferents, selectedReferentCredIds);
    }

    @NonNull
    public VpRequestUiState withExpandedGroups(@NonNull Set<String> value) {
        return new VpRequestUiState(loading, inFlight, scenario,
                uncheckedClaimKeys, revealedAttrLabels, collapsedSections,
                value, selectedReferents, selectedReferentCredIds);
    }

    @NonNull
    public VpRequestUiState withSelectedReferents(@NonNull Map<String, String> value) {
        return new VpRequestUiState(loading, inFlight, scenario,
                uncheckedClaimKeys, revealedAttrLabels, collapsedSections,
                expandedGroups, value, selectedReferentCredIds);
    }

    @NonNull
    public VpRequestUiState withSelectedReferentCredIds(@NonNull Map<String, String> value) {
        return new VpRequestUiState(loading, inFlight, scenario,
                uncheckedClaimKeys, revealedAttrLabels, collapsedSections,
                expandedGroups, selectedReferents, value);
    }

    public boolean canSubmit() {
        if (scenario == null) return false;
        if (scenario.mode == VpRequestMode.OID4VP || scenario.mode == VpRequestMode.OPENDID_VC) {

            boolean anyClaim = false;
            for (VpRequestedDoc doc : scenario.openDidVcDocs) {
                for (VpDocEntry entry : doc.entries) {
                    if (entry.isGroup()) {
                        VpRequestedClaimGroup group = entry.group;
                        for (VpRequestedClaim claim : group.items) {
                            anyClaim = true;
                            if (claim.locked || !uncheckedClaimKeys.contains(
                                    doc.docId + ":" + group.title + ":" + claim.code)) {
                                return true;
                            }
                        }
                    } else {
                        VpRequestedClaim claim = entry.claim;
                        anyClaim = true;
                        if (claim.locked
                                || !uncheckedClaimKeys.contains(doc.docId + ":" + claim.code)) {
                            return true;
                        }
                    }
                }
            }

            return !anyClaim;
        }
        if (scenario.mode != VpRequestMode.ZKP) return true;
        if (scenario.zkpRequest == null) return true;
        for (VpRevealAttribute attr : scenario.zkpRequest.attributes) {
            if (!attr.selectable) continue;
            if (attr.value != null) continue;
            if (selectedReferents.get(attr.label) == null) return false;
        }
        return true;
    }
}
