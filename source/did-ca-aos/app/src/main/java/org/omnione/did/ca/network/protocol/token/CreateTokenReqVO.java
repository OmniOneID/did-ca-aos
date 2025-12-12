package org.omnione.did.ca.network.protocol.token;

import com.google.gson.Gson;

import org.omnione.did.ca.ui.viewmodel.BaseModel;
import org.omnione.did.sdk.datamodel.token.ServerTokenSeed;

public class CreateTokenReqVO extends BaseModel {
    private String id;
    private String txId;
    private ServerTokenSeed seed;

    public CreateTokenReqVO() {

    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTxId() {
        return txId;
    }

    public void setTxId(String txId) {
        this.txId = txId;
    }

    public ServerTokenSeed getSeed() {
        return seed;
    }

    public void setSeed(ServerTokenSeed seed) {
        this.seed = seed;
    }

    @Override
    public void fromJson(String value) {
        Gson gson = new Gson();
        CreateTokenReqVO obj = gson.fromJson(value, CreateTokenReqVO.class);
        this.id = obj.getId();
        this.txId = obj.getTxId();
        this.seed = obj.getSeed();
    }
}
