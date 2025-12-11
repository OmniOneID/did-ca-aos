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

package org.omnione.did.ca.network.protocol;

import android.content.Context;

import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.HttpUrlConnection;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.util.UserAuthHelper;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.datamodel.profile.VerifyProfile;
import org.omnione.did.sdk.datamodel.protocol.P310RequestVo;
import org.omnione.did.sdk.datamodel.protocol.P310ResponseVo;
import org.omnione.did.sdk.datamodel.util.GsonWrapper;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.datamodel.vc.Claim;
import org.omnione.did.sdk.datamodel.vc.VerifiableCredential;
import org.omnione.did.sdk.datamodel.vc.issue.ReturnEncVP;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class VpSubmission extends BaseOperation {
    public VpSubmission(Context context, Map<String, String> requestData) {
        super(context, OperationType.VP_SUBMISSION, requestData);
    }

    private CompletableFuture<ProtocolData> initVpSubmissionData() {

        CompletableFuture<ProtocolData> completableFuture = new CompletableFuture<>();
        ProtocolData protocolData = ProtocolData.getInstance();
        protocolData.setPin(requestDataMap.get("pin"));
        completableFuture.complete(protocolData);
        return completableFuture;
    }

    private CompletableFuture<ProtocolData> requestVerifyProfile() {

        CaLog.d("requestVerifyProfile");
        CompletableFuture<ProtocolData> completableFuture = new CompletableFuture<>();
        ProtocolData protocolData = ProtocolData.getInstance();
        P310RequestVo req = new P310RequestVo(CaUtil.createMessageId(context));
        protocolData.setVpPayloadType(requestDataMap.get("payloadType"));
        req.setOfferId(requestDataMap.get("offerId"));
        String result = new HttpUrlConnection().send(context, Config.Verifier.REQUEST_VERIFY_PROFILE, "POST", req.toJson());
        protocolData.setTxId(MessageUtil.deserialize(result, P310ResponseVo.class).getTxId());
        VerifyProfile verifyProfile = MessageUtil.deserialize(result, P310ResponseVo.class).getProfile();
        protocolData.setVerifyProfile(verifyProfile.toJson());
        CaLog.d("requestVerifyProfile Success : " + result);
        completableFuture.complete(protocolData);
        return completableFuture;
    }
    private CompletableFuture<ProtocolData> requestVerify(ProtocolData protocolData) {

        CaLog.d("requestVerify");
        CompletableFuture<ProtocolData> completableFuture = new CompletableFuture<>();
        try {
            WalletApi walletApi = WalletApi.getInstance(context);
            String vcId = "";
            List<VerifiableCredential> vcList = walletApi.getAllCredentials(protocolData.gethWalletToken());
            List<String> claimCode = new ArrayList<>();
            VerifyProfile vpProfile = MessageUtil.deserialize(protocolData.getVerifyProfile(), VerifyProfile.class);
            CaLog.d("vpProfile : " + vpProfile.toJson());

            for (VerifiableCredential vc : vcList) {
                if (vpProfile.getProfile().filter.getCredentialSchemas().get(0).getId().equals(vc.getCredentialSchema().getId())) {
                    vcId = vc.getId();
                    CaLog.d("submit to VC ID : " + vcId);
                    if (vpProfile.getProfile().filter.getCredentialSchemas().get(0).isPresentAll()) {
                        for (Claim claim : vc.getCredentialSubject().getClaims()) {
                            claimCode.add(claim.getCode());
                        }
                    }
                }
            }

            if (claimCode.isEmpty()) {
                claimCode = vpProfile.getProfile().filter.getCredentialSchemas().get(0).requiredClaims;
            }

            CaLog.d("submit to claimCode : " + GsonWrapper.getGson().toJson(claimCode));
            String nonce = vpProfile.getProfile().process.verifierNonce;

            UserAuthHelper.AuthMethod authMethod = UserAuthHelper.getDefaultAuthMethod(context);
            ReturnEncVP returnEncVP;
            if (authMethod.getAuthMethod().equals("pin")) {
                returnEncVP = walletApi.createEncVp(protocolData.gethWalletToken(), vcId, claimCode, vpProfile.getProfile().process.reqE2e, requestDataMap.get("pin"), nonce, vpProfile.getProfile().process.authType);
            } else {
                returnEncVP = walletApi.createEncVp(protocolData.gethWalletToken(), vcId, claimCode, vpProfile.getProfile().process.reqE2e, "", nonce, vpProfile.getProfile().process.authType);
            }

            CaLog.d("returnEncVP: " + GsonWrapper.getGson().toJson(returnEncVP));
            P310RequestVo requestVo = new P310RequestVo(CaUtil.createMessageId(context));
            requestVo.setTxId(protocolData.getTxId());
            requestVo.setEncVp(returnEncVP.getEncVp());
            requestVo.setAccE2e(returnEncVP.getAccE2e());

            String result = new HttpUrlConnection().send(context, Config.Verifier.REQUEST_VERIFY_VP, "POST", requestVo.toJson());
            CaLog.d("requestVerify result: " + result);
            completableFuture.complete(protocolData);

        } catch (WalletException | WalletCoreException | UtilityException e) {
            CaLog.d("requestVerify ex: " + e.getMessage());
            throw new CompletionException(e);
        }
        return completableFuture;
    }

    @Override
    public void preExecute() {
        requestVerifyProfile()
        .thenComposeAsync(this::requestWalletTokenData)
        .thenAccept(this::finish)
        .exceptionally(ex -> {
            super.onError(ex);
            return null;
        });
    }
    @Override
    public void execute() {
        initVpSubmissionData()
        .thenComposeAsync(this::requestVerify)
        .thenAccept(this::finish)
        .exceptionally(ex -> {
            super.onError(ex);
            return null;
        });
    }
}
