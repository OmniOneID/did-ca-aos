package org.omnione.did.ca.network.protocol;

import android.content.Context;

import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.HttpUrlConnection;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.datamodel.profile.IssueProfile;
import org.omnione.did.sdk.datamodel.protocol.P210RequestVo;
import org.omnione.did.sdk.datamodel.protocol.P210ResponseVo;
import org.omnione.did.sdk.datamodel.security.DIDAuth;
import org.omnione.did.sdk.datamodel.util.GsonWrapper;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.datamodel.vc.issue.VCPlanList;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class VcIssuance extends BaseOperation {
    public VcIssuance(Context context, Map<String, String> requestData) {
        super(context, BaseOperation.OperationType.VC_ISSUERANCE, requestData);
    }

    private CompletableFuture<ProtocolData> initIssueVcData() {

        CompletableFuture<ProtocolData> completableFuture = new CompletableFuture<>();
        ProtocolData protocolData = ProtocolData.getInstance();
        protocolData.setPin(requestDataMap.get("pin"));
        completableFuture.complete(protocolData);
        return completableFuture;
    }

    private CompletableFuture<ProtocolData> fetchVcPlanList() {
        CaLog.d("fetchVcPlanList");
        CompletableFuture<ProtocolData> completableFuture = new CompletableFuture<>();
        ProtocolData protocolData = ProtocolData.getInstance();
        String result = new HttpUrlConnection().send(context, Config.TAS.VC_PLAN_LIST, "GET", "");
        VCPlanList vcPlanList = MessageUtil.deserialize(result, VCPlanList.class);
        protocolData.setIssuer(vcPlanList.getItems().get(0).getAllowedIssuers());
        protocolData.setVcPlanId(vcPlanList.getItems().get(0).getVcPlanId());
        CaLog.d("fetchVpPlanList Res : " + GsonWrapper.getGson().toJson(vcPlanList));
        completableFuture.complete(protocolData);
        return completableFuture;
    }

    private CompletableFuture<ProtocolData> proposeIssueVc(ProtocolData protocolData) {

        CaLog.d("proposeIssueVc");
        CompletableFuture<ProtocolData> completableFuture = new CompletableFuture<>();
        P210RequestVo req = new P210RequestVo(CaUtil.createMessageId(context));
        req.setVcPlanId(protocolData.getVcPlanId());
        req.setIssuer(protocolData.getIssuer().get(0));
        req.setOfferId(null);

        String result = new HttpUrlConnection().send(context, Config.TAS.PROPOSE_ISSUE_VC, "POST", req.toJson());
        protocolData.setTxId(MessageUtil.deserialize(result, P210ResponseVo.class).getTxId());
        protocolData.setRefId(MessageUtil.deserialize(result, P210ResponseVo.class).getRefId());
        CaLog.d("Success : " + protocolData.getTxId());
        completableFuture.complete(protocolData);
        return completableFuture;
    }

    private CompletableFuture<ProtocolData> requestIssueProfile(ProtocolData protocolData) {

        CaLog.d("requestIssueProfile");
        CompletableFuture<ProtocolData> completableFuture = new CompletableFuture<>();
        P210RequestVo req = new P210RequestVo(CaUtil.createMessageId(context));
        req.setTxId(protocolData.getTxId());
        req.setServerToken(protocolData.gethServerToken());
        String result = new HttpUrlConnection().send(context, Config.TAS.REQUEST_ISSUE_PROFILE, "POST", req.toJson());
        protocolData.setIssueProfile(GsonWrapper.getGson().fromJson(result, P210ResponseVo.class).getProfile().toJson());
        protocolData.setAuthNonce(GsonWrapper.getGson().fromJson(result, P210ResponseVo.class).getAuthNonce());
        CaLog.d("Success : " + protocolData.getTxId());
        completableFuture.complete(protocolData);
        return completableFuture;
    }
    private CompletableFuture<ProtocolData> requestIssueVc(ProtocolData protocolData) {

        CaLog.d("requestIssueVc");
        CompletableFuture<ProtocolData> completableFuture = new CompletableFuture<>();
        try {
            DIDAuth signedDidAuth;

            CaLog.d(String.valueOf("Request : " + (requestDataMap == null)));

            String pin = (requestDataMap != null) ? requestDataMap.get("pin") : null;
            signedDidAuth = WalletApi.getInstance(context).getSignedDIDAuth(protocolData.getAuthNonce(), pin);

            CaLog.d("issueProfile: " + protocolData.getIssueProfile());
            IssueProfile issueProfile = MessageUtil.deserialize(protocolData.getIssueProfile(), IssueProfile.class);

            WalletApi walletApi = WalletApi.getInstance(context);
            String result = walletApi.requestIssueVc(protocolData.gethWalletToken(), Config.TAS.BASE_URL, Config.ApiGW.BASE_URL, protocolData.gethServerToken(), protocolData.getRefId(), issueProfile, signedDidAuth, protocolData.getTxId()).get();
            CaLog.d("Success : " + result);
            completableFuture.complete(protocolData);
        } catch (WalletException | InterruptedException | ExecutionException | WalletCoreException | UtilityException e) {
            CaLog.d("requestRegisterUser ex: " + e.getMessage());
            throw new CompletionException(e);
        }
        return completableFuture;
    }
    private CompletableFuture<ProtocolData> confirmIssueVc(ProtocolData protocolData) {
        CaLog.d("confirmIssueVc");
        CompletableFuture<ProtocolData> completableFuture = new CompletableFuture<>();
        return completableFuture;

    }
    @Override
    public void preExecute() {
        fetchVcPlanList()
        .thenComposeAsync(this::proposeIssueVc)
        .thenComposeAsync(this::requestECDH)
        .thenComposeAsync(this::requestAttestedAppInfo)
        .thenComposeAsync(this::requestWalletTokenData)
        .thenComposeAsync(this::requestCreateToken)
        .thenComposeAsync(this::requestIssueProfile)
        .thenAccept(this::finish)
        .exceptionally(ex -> {
            super.onError(ex);
            return null;
        });
    }
    @Override
    public void execute() {
        initIssueVcData()
        .thenComposeAsync(this::requestIssueVc)
        .thenComposeAsync(this::confirmIssueVc)
        .thenAccept(this::finish)
        .exceptionally(ex -> {
            super.onError(ex);
            return null;
        });
    }
}
