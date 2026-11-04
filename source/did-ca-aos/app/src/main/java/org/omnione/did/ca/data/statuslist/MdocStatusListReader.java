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

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;

import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.core.oid4vc.format.mdoc.MdocCborReader;

public final class MdocStatusListReader {

    private static final String STATUS = "status";
    private static final String STATUS_LIST = "status_list";
    private static final String URI = "uri";
    private static final String IDX = "idx";

    private MdocStatusListReader() {}

    @Nullable
    public static StatusListReference parse(@Nullable String compact) {
        if (compact == null || compact.isEmpty()) return null;
        try {
            CBORObject mso = MdocCborReader.mso(
                    MdocCborReader.issuerAuth(
                            MdocCborReader.toIssuerSigned(MdocCborReader.decode(compact))));
            return fromMso(mso);
        } catch (WalletCoreException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static StatusListReference fromMso(@NonNull CBORObject mso) {
        CBORObject status = untag(mso.get(STATUS));
        if (status == null || status.getType() != CBORType.Map) return null;

        CBORObject statusList = untag(status.get(STATUS_LIST));
        if (statusList == null || statusList.getType() != CBORType.Map) return null;

        CBORObject uri = untag(statusList.get(URI));
        CBORObject idx = untag(statusList.get(IDX));
        if (uri == null || uri.getType() != CBORType.TextString) return null;
        if (idx == null || idx.getType() != CBORType.Integer) return null;

        String uriText = uri.AsString();
        long idxValue = idx.AsInt64Value();
        if (uriText.isEmpty() || idxValue < 0) return null;

        return new StatusListReference(uriText, idxValue);
    }

    @Nullable
    private static CBORObject untag(@Nullable CBORObject value) {
        if (value == null || value.isNull()) return null;
        return value.isTagged() ? value.Untag() : value;
    }
}
