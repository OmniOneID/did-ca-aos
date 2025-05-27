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

import android.content.Context;

import androidx.core.content.ContextCompat;

import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.protocol.token.GetWalletToken;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.exception.WalletCoreException;

import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.protocol.P310ZkpRequestVo;
import org.omnione.did.sdk.datamodel.protocol.P310ZkpResponseVo;
import org.omnione.did.sdk.datamodel.zkp.AvailableReferent;
import org.omnione.did.sdk.datamodel.zkp.ProofParam;
import org.omnione.did.sdk.datamodel.zkp.Referent;
import org.omnione.did.sdk.datamodel.zkp.ReferentInfo;
import org.omnione.did.sdk.datamodel.zkp.UserReferent;
import org.omnione.did.sdk.datamodel.util.GsonWrapper;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class VerifyPresenter implements VerifyConstants.Presenter {

    private static final String ZKP_TAG = VerifyPresenter.class.getName();

    private AvailableReferent availableReferent;
    private VerifyConstants.View view;
    private VerifyConstants.Model model;
    private Context context;
    private P310ZkpResponseVo proofRequestProfileVo;

    private GetWalletToken getWalletToken;

    public VerifyPresenter(Context context, VerifyConstants.View view) {

        this.view = view;
        this.context = context;
        this.model = new VerifyModel(context, this);
        getWalletToken = GetWalletToken.getInstance(context);
    }

    private void setAvailableReferent(AvailableReferent availableReferent) {
        this.availableReferent = availableReferent;
    }

    @Override
    public AvailableReferent getAvailableReferent() {
        return availableReferent;
    }

    @Override
    public void setProofRequestProfile(P310ZkpResponseVo proofRequestProfileVo) {
        this.proofRequestProfileVo = proofRequestProfileVo;
        searchCredential(proofRequestProfileVo);
    }

    @Override
    public void requestVerify(final List<UserReferent> selectedUserReferent) {

        new Thread(new Runnable() {
            @Override
            public void run() {
               createReferent(selectedUserReferent);
            }
        }).start();
    }

    private void createReferent(List<UserReferent> selectedUserReferent) {

        try {
            ReferentInfo referentInfo = WalletApi.getInstance(context).createZkpReferent(selectedUserReferent);

            CaLog.d("referentInfo: " + GsonWrapper.getGsonPrettyPrinting().toJson(referentInfo));
            createProofParam(referentInfo.getReferents());

        } catch (WalletCoreException | UtilityException e) {
            CaUtil.showErrorDialog(context, e.getMessage());
        }
    }

    private void createProofParam(HashMap<String, Referent> referents) {

        final List<ProofParam> proofParams = new LinkedList<>();

        for (String key: referents.keySet()) {
            Referent referent = referents.get(key);
            proofParams.add(new ProofParam.Builder().
                    setCredDef(model.getCredentialDefinition(referent.getCredDefId())).
                    setSchema(model.getCredentialSchema(referent.getSchemaId())).
                    setReferentInfo(new ReferentInfo(key, referent))
                    .build());
        }
        createProof(proofParams, view.getSelfAttribute());
    }

    private void createProof(List<ProofParam> proofParams, Map<String, String> selfAttr) {

        new Thread(() -> {
            try {
                String hWalletToken = getWalletToken.getWalletTokenDataAPI(WalletTokenPurpose.WALLET_TOKEN_PURPOSE.LIST_VC_AND_PRESENT_VP).get();

                P310ZkpRequestVo proof = WalletApi.getInstance(context).createZkpProof(hWalletToken, proofRequestProfileVo, proofParams, selfAttr);
                // 서버 검증 로직
                boolean result = model.verifyProof(proof, proofRequestProfileVo.getProofRequestProfile().getProfile().proofRequest);
                view.showError("", "성공");
                view.moveToMainView();

            } catch (WalletException | InterruptedException | UtilityException | ExecutionException | WalletCoreException e) {
                view.showError("", e.getMessage());
            }
        }).start();
    }


    private void searchCredential(P310ZkpResponseVo proofRequestProfileVo) {

        new Thread(() -> {
            try {
                CaLog.d("proofRequest: "+GsonWrapper.getGson().toJson(proofRequestProfileVo.getProofRequestProfile().getProfile().proofRequest));
                String hWalletToken = getWalletToken.getWalletTokenDataAPI(WalletTokenPurpose.WALLET_TOKEN_PURPOSE.LIST_VC).get();
                AvailableReferent availableReferent = WalletApi.getInstance(context).searchZkpCredentials(hWalletToken, proofRequestProfileVo.getProofRequestProfile().getProfile().proofRequest);
                CaLog.d("availableReferent >>>>>>>>>> " + GsonWrapper.getGsonPrettyPrinting().toJson(availableReferent));
                setAvailableReferent(availableReferent);
                view.drawAvailableReferent();

            } catch (WalletCoreException | UtilityException | WalletException | ExecutionException | InterruptedException e) {
                view.showError("", e.getMessage());
            }
        }).start();
    }
}
