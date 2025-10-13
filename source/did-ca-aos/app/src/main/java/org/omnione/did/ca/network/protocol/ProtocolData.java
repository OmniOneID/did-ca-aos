package org.omnione.did.ca.network.protocol;

import com.google.gson.Gson;

import org.omnione.did.ca.ui.viewmodel.BaseModel;
import org.omnione.did.sdk.utility.DataModels.EcKeyPair;

import java.util.List;
import java.util.Map;


public class ProtocolData extends BaseModel {
    private static volatile ProtocolData instance;

    private ProtocolData() {

    }

    public static ProtocolData getInstance() {
        if (instance == null) {
            synchronized (ProtocolData.class) {
                if (instance == null) {
                    instance = new ProtocolData();
                }
            }
        }
        return instance;
    }

    private String vpPayloadType;
    private String txId;
    private String refId;
    private String issueProfile;
    private String verifyProfile;
    private String hServerToken;
    private String hWalletToken;
    private byte[] clientNonce;
    private String priKey;
    private String requestCreateToken;

    private String qrInfo;
    private String issueNonce;
    private String vcId;
    private String authNonce;
    private String attestedResult ;

    private String ecdhResult;
    private EcKeyPair dhKeyPair;
    private List<String> allowedIssuers;
    private String vcPlanId;

    private String offerId;

    private String pin;

    public String getTxId() {
        return txId;
    }

    public void setTxId(String txId) {
        this.txId = txId;
    }

    public String getRefId() {
        return refId;
    }

    public void setRefId(String refId) {
        this.refId = refId;
    }

    public String getIssueProfile() {
        return issueProfile;
    }

    public void setIssueProfile(String issueProfile) {
        this.issueProfile = issueProfile;
    }

    public String getVerifyProfile() {
        return verifyProfile;
    }

    public void setVerifyProfile(String verifyProfile) {
        this.verifyProfile = verifyProfile;
    }

    public String gethServerToken() {
        return hServerToken;
    }

    public void sethServerToken(String hServerToken) {
        this.hServerToken = hServerToken;
    }

    public String gethWalletToken() {
        return hWalletToken;
    }

    public void sethWalletToken(String hWalletToken) {
        this.hWalletToken = hWalletToken;
    }

    public byte[] getClientNonce() {
        return clientNonce;
    }

    public void setClientNonce(byte[] clientNonce) {
        this.clientNonce = clientNonce;
    }

    public String getPriKey() {
        return priKey;
    }

    public void setPriKey(String priKey) {
        this.priKey = priKey;
    }

    public String getRequestCreateToken() {
        return requestCreateToken;
    }

    public void setRequestCreateToken(String requestCreateToken) {
        this.requestCreateToken = requestCreateToken;
    }

    public String getIssueNonce() {
        return issueNonce;
    }

    public void setIssueNonce(String issueNonce) {
        this.issueNonce = issueNonce;
    }

    public String getVcId() {
        return vcId;
    }

    public void setVcId(String vcId) {
        this.vcId = vcId;
    }

    public String getAuthNonce() {
        return authNonce;
    }

    public void setAuthNonce(String authNonce) {
        this.authNonce = authNonce;
    }

    public String getAttestedResult() {
        return attestedResult;
    }

    public void setAttestedResult(String attestedResult) {
        this.attestedResult = attestedResult;
    }

    public String getEcdhResult() {
        return ecdhResult;
    }

    public void setEcdhResult(String ecdhResult) {
        this.ecdhResult = ecdhResult;
    }

    public EcKeyPair getDhKeyPair() {
        return dhKeyPair;
    }

    public void setDhKeyPair(EcKeyPair dhKeyPair) {
        this.dhKeyPair = dhKeyPair;
    }

    public void setIssuer(List<String> allowedIssuers) {
        this.allowedIssuers = allowedIssuers;
    }

    public void setVcPlanId(String vcPlanId) {
        this.vcPlanId = vcPlanId;
    }

    public String getVcPlanId() {
        return this.vcPlanId;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public String getPin() {
        return this.pin;
    }

    public List<String> getIssuer() {
        return this.allowedIssuers;
    }
    public void setOfferId(String offerId) {
        this.offerId = offerId;
    }

    public String getOfferId() {
        return offerId;
    }

    public void setVpPayloadType(String vpPayloadType) {
        this.vpPayloadType = vpPayloadType;
    }

    public String getVpPayloadType() {
        return vpPayloadType;
    }

    public String getQrInfo() {
        return qrInfo;
    }

    public void setQrInfo(String qrInfo) {
        this.qrInfo = qrInfo;
    }

    @Override
    public void fromJson(String value) {
        Gson gson = new Gson();
        ProtocolData tempObj = gson.fromJson(value, ProtocolData.class);
        if (tempObj != null) {
            this.vpPayloadType = tempObj.vpPayloadType;
            this.txId = tempObj.txId;
            this.refId = tempObj.refId;
            this.issueProfile = tempObj.issueProfile;
            this.verifyProfile = tempObj.verifyProfile;
            this.hServerToken = tempObj.hServerToken;
            this.hWalletToken = tempObj.hWalletToken;
            this.clientNonce = tempObj.clientNonce;
            this.priKey = tempObj.priKey;
            this.requestCreateToken = tempObj.requestCreateToken;

            this.qrInfo = tempObj.qrInfo;
            this.issueNonce = tempObj.issueNonce;
            this.vcId = tempObj.vcId;
            this.authNonce = tempObj.authNonce;
            this.attestedResult = tempObj.attestedResult;

            this.ecdhResult = tempObj.ecdhResult;
            this.dhKeyPair = tempObj.dhKeyPair;
            this.allowedIssuers = tempObj.allowedIssuers;
            this.vcPlanId = tempObj.vcPlanId;

            this.offerId = tempObj.offerId;
        }
    }

    public void resetData() {
        synchronized (ProtocolData.class) {
            this.vpPayloadType = null;
            this.txId = null;
            this.refId = null;
            this.issueProfile = null;
            this.verifyProfile = null;
            this.hServerToken = null;
            this.hWalletToken = null;
            this.clientNonce = null;
            this.priKey = null;
            this.requestCreateToken = null;
            this.qrInfo = null;
            this.issueNonce = null;
            this.vcId = null;
            this.authNonce = null;
            this.attestedResult = null;
            this.ecdhResult = null;
            this.dhKeyPair = null;
            this.allowedIssuers = null;
            this.vcPlanId = null;
            this.offerId = null;
        }
    }


}
