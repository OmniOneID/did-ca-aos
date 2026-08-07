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
import androidx.annotation.Nullable;

import org.omnione.did.sdk.datamodel.common.enums.VerifyAuthType;

import java.util.Collections;
import java.util.List;

public final class VpScenario {

    @NonNull
    public final String id;
    @NonNull
    public final VpVerifier verifier;
    @NonNull
    public final VpRequestMode mode;
    @NonNull
    public final List<VpRequestedDoc> openDidVcDocs;
    @Nullable
    public final VpZkpRequest zkpRequest;

    @NonNull
    public final VerifyAuthType.VERIFY_AUTH_TYPE authType;

    private VpScenario(@NonNull String id,
                       @NonNull VpVerifier verifier,
                       @NonNull VpRequestMode mode,
                       @NonNull List<VpRequestedDoc> openDidVcDocs,
                       @Nullable VpZkpRequest zkpRequest,
                       @NonNull VerifyAuthType.VERIFY_AUTH_TYPE authType) {
        this.id = id;
        this.verifier = verifier;
        this.mode = mode;
        this.openDidVcDocs = openDidVcDocs;
        this.zkpRequest = zkpRequest;
        this.authType = authType;
    }

    @NonNull
    public static VpScenario openDidVc(@NonNull String id,
                                @NonNull VpVerifier verifier,
                                @NonNull List<VpRequestedDoc> openDidVcDocs,
                                @NonNull VerifyAuthType.VERIFY_AUTH_TYPE authType) {
        return new VpScenario(id, verifier, VpRequestMode.OPENDID_VC, openDidVcDocs, null, authType);
    }

    @NonNull
    public static VpScenario zkp(@NonNull String id,
                                 @NonNull VpVerifier verifier,
                                 @NonNull VpZkpRequest zkpRequest,
                                 @NonNull VerifyAuthType.VERIFY_AUTH_TYPE authType) {
        return new VpScenario(id, verifier, VpRequestMode.ZKP, Collections.emptyList(), zkpRequest, authType);
    }

    @NonNull
    public static VpScenario oid4vp(@NonNull String id,
                                    @NonNull VpVerifier verifier,
                                    @NonNull List<VpRequestedDoc> docs,
                                    @NonNull VerifyAuthType.VERIFY_AUTH_TYPE authType) {
        return new VpScenario(id, verifier, VpRequestMode.OID4VP, docs, null, authType);
    }
}
