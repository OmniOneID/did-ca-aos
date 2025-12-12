package org.omnione.did.ca.network.protocol;

import android.content.Context;

import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.HttpUrlConnection;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.datamodel.did.DIDDocument;
import org.omnione.did.sdk.datamodel.did.SignedDidDoc;
import org.omnione.did.sdk.datamodel.protocol.P141RequestVo;
import org.omnione.did.sdk.datamodel.protocol.P141ResponseVo;
import org.omnione.did.sdk.datamodel.security.DIDAuth;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class UserDidUpdate extends BaseOperation {
    public UserDidUpdate(Context context, Map<String, String> requestData) {
        super(context, OperationType.USER_UPDATE, requestData);
    }
    private CompletableFuture<ProtocolData> proposeUpdateUser() {

        CaLog.d("proposeUpdateUser");
        CompletableFuture<ProtocolData> completableFuture = new CompletableFuture<>();
        try {
            ProtocolData protocolData = ProtocolData.getInstance();
            P141RequestVo req = new P141RequestVo(CaUtil.createMessageId(context));
            req.setDid(WalletApi.getInstance(context).getDIDDocument(2).getId());
            String result = new HttpUrlConnection().send(context, Config.TAS.PROPOSE_UPDATE_USER, "POST", req.toJson());
            protocolData.setTxId(MessageUtil.deserialize(result, P141ResponseVo.class).getTxId());
            protocolData.setAuthNonce(MessageUtil.deserialize(result, P141ResponseVo.class).getAuthNonce());
            CaLog.d("Success : " + protocolData.getTxId());
            completableFuture.complete(protocolData);
        } catch (WalletException | WalletCoreException | UtilityException e) {
            throw new CompletionException(e);
        }
        return completableFuture;
    }

    private CompletableFuture<ProtocolData> initUpdateUserData() {

        CompletableFuture<ProtocolData> completableFuture = new CompletableFuture<>();
        ProtocolData protocolData = ProtocolData.getInstance();
        protocolData.setPin(requestDataMap.get("pin"));
        completableFuture.complete(protocolData);
        return completableFuture;
    }
    private CompletableFuture<ProtocolData> requestUpdateUser(ProtocolData protocolData) {

        CaLog.d("requestUpdateUser");
        CompletableFuture<ProtocolData> completableFuture = new CompletableFuture<>();
        try {
            WalletApi walletApi = WalletApi.getInstance(context);
            DIDDocument holderDIDDoc = walletApi.updateHolderDIDDoc(protocolData.gethWalletToken());
            CaLog.d("holderDIDDoc: " + holderDIDDoc.toJson());
            DIDDocument ownerDIDDoc = (DIDDocument) walletApi.addProofsToDocument(holderDIDDoc, List.of("pin", "bio"), holderDIDDoc.getId(), 2, protocolData.getPin(), false);
            CaLog.d("ownerDIDDoc: " + ownerDIDDoc.toJson());
            SignedDidDoc signedDidDoc = walletApi.createSignedDIDDoc(ownerDIDDoc);

            DIDAuth didAuth = walletApi.getSignedDIDAuth(protocolData.getAuthNonce(), protocolData.getPin());
            CaLog.d("didAuth: " + didAuth.toJson());
            CaLog.d("signedDidDoc: " + signedDidDoc.toJson());

            String result = walletApi.requestUpdateUser(protocolData.gethWalletToken(), Config.TAS.BASE_URL, protocolData.gethServerToken(), didAuth, signedDidDoc, protocolData.getTxId()).get();
            CaLog.d("Success : " + result);

            completableFuture.complete(protocolData);

        } catch (WalletException | InterruptedException | ExecutionException | WalletCoreException | UtilityException e) {
            CaLog.d("requestUpdateUser ex: " + e.getMessage());
            throw new CompletionException(e);
        }
        return completableFuture;
    }

    private CompletableFuture<ProtocolData> confirmUpdateUser(ProtocolData protocolData) {

        CaLog.d("confirmUpdateUser");
        CompletableFuture<ProtocolData> completableFuture = new CompletableFuture<>();
        P141RequestVo req = new P141RequestVo(CaUtil.createMessageId(context));
        req.setTxId(protocolData.getTxId());
        req.setServerToken(protocolData.gethServerToken());
        new HttpUrlConnection().send(context, Config.TAS.CONFIRM_UPDATE_USER, "POST", req.toJson());
        completableFuture.complete(protocolData);
        return completableFuture;
    }

    @Override
    public void preExecute() {
        proposeUpdateUser()
        .thenComposeAsync(super::requestECDH)
        .thenComposeAsync(super::requestAttestedAppInfo)
        .thenComposeAsync(super::requestWalletTokenData)
        .thenComposeAsync(super::requestCreateToken)
        .thenAccept(this::finish)
        .exceptionally(ex -> {
            onError(ex);
            return null;
        });
    }

    @Override
    public void execute() {
        initUpdateUserData()
        .thenComposeAsync(this::requestUpdateUser)
        .thenComposeAsync(this::confirmUpdateUser)
        .thenAccept(this::finish)
        .exceptionally(ex -> {
            onError(ex);
            return null;
        });
    }
}
