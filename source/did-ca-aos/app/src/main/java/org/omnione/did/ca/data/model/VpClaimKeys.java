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

package org.omnione.did.ca.data.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class VpClaimKeys {

    private VpClaimKeys() {}

    @NonNull
    public static String flat(@NonNull String optionId, @NonNull String code) {
        return optionId + ":" + code;
    }

    @NonNull
    public static String groupChild(@NonNull String optionId, @NonNull String groupTitle,
                                    @NonNull String code) {
        return optionId + ":" + groupTitle + ":" + code;
    }

    @NonNull
    public static List<String> groupChildren(@NonNull String optionId,
                                             @NonNull VpRequestedClaimGroup group) {
        List<String> keys = new ArrayList<>(group.items.size());
        for (VpRequestedClaim claim : group.items) {
            keys.add(groupChild(optionId, group.title, claim.code));
        }
        return keys;
    }

    @NonNull
    public static List<String> entryKeys(@NonNull String optionId, @NonNull VpDocEntry entry) {
        if (entry.isGroup()) {
            return groupChildren(optionId, entry.group);
        }
        List<String> keys = new ArrayList<>(1);
        keys.add(flat(optionId, entry.claim.code));
        return keys;
    }
}
