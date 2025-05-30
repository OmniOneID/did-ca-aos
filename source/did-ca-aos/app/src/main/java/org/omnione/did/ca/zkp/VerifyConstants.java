/*
 * Copyright 2025 OmniOne.
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

package org.omnione.did.ca.zkp;

import org.omnione.did.sdk.datamodel.protocol.P310ZkpRequestVo;
import org.omnione.did.sdk.datamodel.protocol.P310ZkpResponseVo;
import org.omnione.did.sdk.datamodel.zkp.AvailableReferent;
import org.omnione.did.sdk.datamodel.zkp.CredentialDefinition;
import org.omnione.did.sdk.datamodel.zkp.CredentialSchema;
import org.omnione.did.sdk.datamodel.zkp.ProofRequest;
import org.omnione.did.sdk.datamodel.zkp.UserReferent;

import java.util.List;
import java.util.Map;

public interface VerifyConstants {
    interface Model {
        boolean verifyProof(P310ZkpRequestVo proof, ProofRequest proofRequest);

        CredentialDefinition getCredentialDefinition(String credDefId);

        CredentialSchema getCredentialSchema(String schemaId);
    }

    interface View {
        void showError(String errorCode, String errorMessage);

        void showToast(String message);

        void showLoading();

        void hideLoading();

        void moveToMainView();

        void drawAvailableReferent();

        Map<String, String> getSelfAttribute();
    }

    interface Presenter {
        AvailableReferent getAvailableReferent();
        void setProofRequestProfile(P310ZkpResponseVo proofRequestProfile);
        void requestVerify(List<UserReferent> selectedUserReferent);
    }
}
