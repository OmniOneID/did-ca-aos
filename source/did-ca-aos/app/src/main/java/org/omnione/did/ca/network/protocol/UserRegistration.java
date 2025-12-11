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
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.datamodel.did.SignedDidDoc;
import org.omnione.did.sdk.datamodel.protocol.P132RequestVo;
import org.omnione.did.sdk.datamodel.protocol.P132ResponseVo;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class UserRegistration extends BaseOperation {
    public UserRegistration(Context context, Map<String, String> requestData) {
        super(context, OperationType.USER_REGISTRATION, requestData);
    }

    private CompletableFuture<ProtocolData> initRegisterUserData() {

        CompletableFuture<ProtocolData> completableFuture = new CompletableFuture<>();
        ProtocolData protocolData = ProtocolData.getInstance();
        protocolData.setPin(requestDataMap.get("pin"));
        completableFuture.complete(protocolData);
        return completableFuture;
    }

    private CompletableFuture<ProtocolData> proposeRegisterUser() {

        CaLog.d("proposeRegisterUser");
        CompletableFuture<ProtocolData> completableFuture = new CompletableFuture<>();
        ProtocolData protocolData = ProtocolData.getInstance();
        P132RequestVo req = new P132RequestVo(CaUtil.createMessageId(context));
        String result = new HttpUrlConnection().send(context, Config.TAS.PROPOSE_REGISTER_USER, "POST", req.toJson());
        protocolData.setTxId(MessageUtil.deserialize(result, P132ResponseVo.class).getTxId());
        CaLog.d("Success : " + protocolData.getTxId());
        completableFuture.complete(protocolData);
        return completableFuture;
    }

    private CompletableFuture<ProtocolData> retrieveKyc(ProtocolData protocolData) {

        CaLog.d("retrieveKyc");
        CompletableFuture<ProtocolData> completableFuture = new CompletableFuture<>();
        P132RequestVo req = new P132RequestVo(CaUtil.createMessageId(context));
        req.setId(CaUtil.createMessageId(context));
        req.setServerToken(protocolData.gethServerToken());
        req.setTxId(protocolData.getTxId());
        String result = new HttpUrlConnection().send(context, Config.TAS.RETRIEVE_KYC, "POST", req.toJson());
        protocolData.setTxId(MessageUtil.deserialize(result, P132ResponseVo.class).getTxId());
        CaLog.d("retrieveKyc Success : " + protocolData.getTxId());
        completableFuture.complete(protocolData);
        return completableFuture;
    }

    private CompletableFuture<ProtocolData> requestRegisterUser(ProtocolData protocolData) {

        CaLog.d("requestRegisterUser");
        CompletableFuture<ProtocolData> completableFuture = new CompletableFuture<>();
        try {
            SignedDidDoc signedDidDoc = new SignedDidDoc();
            signedDidDoc.fromJson(requestDataMap.get("signedDidDoc"));

            WalletApi walletApi = WalletApi.getInstance(context);
            String result = walletApi.requestRegisterUser(protocolData.gethWalletToken(), Config.TAS.BASE_URL, protocolData.getTxId(), protocolData.gethServerToken(), signedDidDoc).get();
            CaLog.d("requestRegisterUser Success : " + result);
            completableFuture.complete(protocolData);
        } catch (WalletException | InterruptedException | ExecutionException | WalletCoreException e) {
            CaLog.d("requestRegisterUser ex: " + e.getMessage());
            throw new CompletionException(e);
        }
        return completableFuture;
    }
    private CompletableFuture<ProtocolData> confirmRegisterUser(ProtocolData protocolData) {

        CaLog.d("confirmRegisterUser");
        CompletableFuture<ProtocolData> completableFuture = new CompletableFuture<>();

        P132RequestVo req = new P132RequestVo(CaUtil.createMessageId(context));
        req.setTxId(protocolData.getTxId());
        req.setServerToken(protocolData.gethServerToken());

        String result = new HttpUrlConnection().send(context, Config.TAS.CONFIRM_REGISTER_USER, "POST", req.toJson());
        protocolData.setTxId(MessageUtil.deserialize(result, P132ResponseVo.class).getTxId());
        CaLog.d("confirmRegisterUser Success : " + protocolData.getTxId());
        completableFuture.complete(protocolData);
        return completableFuture;
    }
    @Override
    public void preExecute() {
        proposeRegisterUser()
        .thenComposeAsync(super::requestECDH)
        .thenComposeAsync(super::requestAttestedAppInfo)
        .thenComposeAsync(super::requestWalletTokenData)
        .thenComposeAsync(super::requestCreateToken)
        .thenComposeAsync(this::retrieveKyc)
        .thenAccept(this::finish)
        .exceptionally(ex -> {
            super.onError(ex);
            return null;
        });
    }
    @Override
    public void execute() {
        initRegisterUserData()
        .thenComposeAsync(this::requestRegisterUser)
        .thenComposeAsync(this::confirmRegisterUser)
        .thenAccept(this::finish)
        .exceptionally(ex -> {
            super.onError(ex);
            return null;
        });
    }
}
