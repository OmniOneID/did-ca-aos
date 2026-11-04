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

package org.omnione.did.ca.data.repository;

import androidx.annotation.NonNull;

import org.omnione.did.ca.data.model.VpClaimKeys;
import org.omnione.did.ca.data.model.VpDocEntry;
import org.omnione.did.ca.data.model.VpRequestedClaim;
import org.omnione.did.ca.data.model.VpRequestedClaimGroup;
import org.omnione.did.ca.data.model.VpRequestedDoc;
import org.omnione.did.sdk.core.oid4vc.format.mdoc.MdocClaimCodes;
import org.omnione.did.sdk.datamodel.oid4vc.Mdoc;
import org.omnione.did.sdk.datamodel.oid4vc.MdocCredentialItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MdocVpDocBuilder {

    private MdocVpDocBuilder() {}

    static final class Result {
        @NonNull final VpRequestedDoc doc;
        @NonNull final Map<String, List<String>> claimKeyToCode;
        @NonNull final List<String> allCodes;

        Result(@NonNull VpRequestedDoc doc,
               @NonNull Map<String, List<String>> claimKeyToCode,
               @NonNull List<String> allCodes) {
            this.doc = doc;
            this.claimKeyToCode = claimKeyToCode;
            this.allCodes = allCodes;
        }
    }

    @NonNull
    static Result build(@NonNull String docId,
                        MdocCredentialItem item,
                        @NonNull Set<String> requestedCodes) {
        String docTitle = (item != null) ? safe(item.getConfigurationId()) : "";
        if (docTitle.isEmpty()) {
            docTitle = "Requested credentials";
        }

        Mdoc mdoc = (item != null) ? item.getMdoc() : null;
        Map<String, Map<String, Object>> nameSpaces = (mdoc != null)
                ? mdoc.getNameSpaces() : Collections.<String, Map<String, Object>>emptyMap();
        boolean flatten = nameSpaces.size() <= 1;

        Map<String, List<String>> claimKeyToCode = new LinkedHashMap<>();
        List<String> allCodes = new ArrayList<>();
        List<VpDocEntry> entries = new ArrayList<>();

        for (Map.Entry<String, Map<String, Object>> ns : nameSpaces.entrySet()) {
            String nameSpace = ns.getKey();
            List<VpRequestedClaim> subItems = new ArrayList<>();
            for (Map.Entry<String, Object> element : ns.getValue().entrySet()) {
                String code = MdocClaimCodes.of(nameSpace, element.getKey());
                String label = MdocClaimCodes.stripNameSpacePrefix(nameSpace, element.getKey());
                boolean locked = requestedCodes.contains(code);
                Object value = element.getValue();
                List<VpRequestedClaim> nested = flatten
                        ? expand(value, code, locked) : Collections.<VpRequestedClaim>emptyList();

                if (!nested.isEmpty()) {
                    entries.add(VpDocEntry.of(new VpRequestedClaimGroup(label, nested, locked)));
                    claimKeyToCode.put(VpClaimKeys.groupChild(docId, label, code),
                            Collections.singletonList(code));
                } else if (flatten) {
                    entries.add(VpDocEntry.of(VpClaimFactory.leaf(label, value, code, locked)));
                    claimKeyToCode.put(VpClaimKeys.flat(docId, code),
                            Collections.singletonList(code));
                } else {
                    subItems.add(VpClaimFactory.leaf(label, value, code, locked));
                    claimKeyToCode.put(VpClaimKeys.groupChild(docId, nameSpace, code),
                            Collections.singletonList(code));
                }
                allCodes.add(code);
            }
            if (!flatten && !subItems.isEmpty()) {
                boolean groupLocked = false;
                for (VpRequestedClaim sub : subItems) {
                    if (requestedCodes.contains(sub.code)) {
                        groupLocked = true;
                        break;
                    }
                }
                entries.add(VpDocEntry.of(
                        new VpRequestedClaimGroup(nameSpace, subItems, groupLocked)));
            }
        }
        return new Result(new VpRequestedDoc(docId, docTitle, entries), claimKeyToCode, allCodes);
    }

    @NonNull
    private static List<VpRequestedClaim> expand(Object value, @NonNull String code,
                                                 boolean locked) {
        List<VpRequestedClaim> subItems = new ArrayList<>();
        if (value instanceof Map) {
            for (Map.Entry<?, ?> f : ((Map<?, ?>) value).entrySet()) {
                subItems.add(VpClaimFactory.leaf(
                        String.valueOf(f.getKey()), f.getValue(), code, locked));
            }
        } else if (value instanceof List) {
            for (Object el : (List<?>) value) {
                if (el instanceof Map) {
                    for (Map.Entry<?, ?> f : ((Map<?, ?>) el).entrySet()) {
                        subItems.add(VpClaimFactory.leaf(
                                String.valueOf(f.getKey()), f.getValue(), code, locked));
                    }
                } else {
                    subItems.add(VpClaimFactory.leaf("", el, code, locked));
                }
            }
        }
        return subItems;
    }

    @NonNull
    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
