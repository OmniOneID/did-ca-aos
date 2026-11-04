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

package org.omnione.did.ca.data.statuslist;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.omnione.did.ca.util.CredentialDateFormatter;
import org.omnione.did.sdk.datamodel.oid4vc.Mdoc;
import org.omnione.did.sdk.datamodel.oid4vc.MdocCredentialItem;
import org.omnione.did.sdk.datamodel.oid4vc.MdocMso;
import org.omnione.did.sdk.datamodel.oid4vc.MdocValidityInfo;
import org.omnione.did.sdk.datamodel.oid4vc.SdJwt;
import org.omnione.did.sdk.datamodel.oid4vc.SdJwtCredentialItem;
import org.omnione.did.sdk.datamodel.vc.VerifiableCredential;

public final class CredentialStatusRefs {

    private CredentialStatusRefs() {}

    @NonNull
    public static CredentialStatusRef fromOpenDidVc(@NonNull VerifiableCredential vc) {
        Long expSec = CredentialDateFormatter.toEpochSeconds(vc.getValidUntil());
        return CredentialStatusRef.openDid(vc.getId(), expSec);
    }

    @NonNull
    public static CredentialStatusRef fromSdJwtItem(@NonNull SdJwtCredentialItem item) {
        SdJwt sdjwt = item.getSdjwt();
        StatusListReference listRef =
                sdjwt != null ? StatusListReference.parse(sdjwt.getJwtPayload()) : null;
        return CredentialStatusRef.sdJwt(item.getId(), listRef, expEpochSeconds(sdjwt));
    }

    @NonNull
    public static CredentialStatusRef fromMdocItem(@NonNull MdocCredentialItem item) {
        Mdoc mdoc = item.getMdoc();
        MdocMso mso = mdoc != null ? mdoc.getMso() : null;
        MdocValidityInfo validity = mso != null ? mso.getValidityInfo() : null;
        Long validUntilSec = validity != null ? validity.getValidUntilEpochSec() : null;
        StatusListReference listRef = MdocStatusListReader.parse(item.getRawCredential());
        return CredentialStatusRef.mdoc(item.getId(), listRef, validUntilSec);
    }

    @Nullable
    public static Long expEpochSeconds(@Nullable SdJwt sdjwt) {
        if (sdjwt == null || sdjwt.getJwtPayload() == null) return null;
        if (!sdjwt.getJwtPayload().has("exp")) return null;
        try {
            return sdjwt.getJwtPayload().get("exp").getAsLong();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
