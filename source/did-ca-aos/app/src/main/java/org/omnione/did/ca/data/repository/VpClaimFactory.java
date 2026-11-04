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
import androidx.annotation.Nullable;

import org.omnione.did.ca.data.model.VpRequestedClaim;
import org.omnione.did.ca.util.ClaimImages;

final class VpClaimFactory {

    private VpClaimFactory() {}

    @NonNull
    static VpRequestedClaim leaf(@NonNull String label, @Nullable Object value,
                                 @NonNull String code, boolean locked) {
        String text = leafText(value);
        if (ClaimImages.isImageElement(label)) {
            byte[] imageBytes = ClaimImages.toImageBytes(value);
            if (imageBytes != null) {
                return new VpRequestedClaim(label, text, code, locked, imageBytes);
            }
        }
        return new VpRequestedClaim(label, text, code, locked);
    }

    @NonNull
    private static String leafText(@Nullable Object value) {
        if (value instanceof byte[]) {
            return "(" + ((byte[]) value).length + " bytes)";
        }
        return value == null ? "" : String.valueOf(value);
    }
}
