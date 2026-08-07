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

package org.omnione.did.ca.data.network.protocol;

import android.content.Context;

import org.omnione.did.ca.data.network.NetworkManager;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.datamodel.protocol.P220RequestVo;
import org.omnione.did.sdk.utility.DataModels.EcKeyPair;
import org.omnione.did.sdk.wallet.walletservice.config.Constants;

public final class RevokeRequestEcdhOp {

    public static final class Result {
        public final String rawResponseJson;
        public final byte[] clientNonce;
        public final EcKeyPair dhKeyPair;

        Result(String raw, byte[] n, EcKeyPair kp) {
            this.rawResponseJson = raw;
            this.clientNonce = n;
            this.dhKeyPair = kp;
        }
    }

    private RevokeRequestEcdhOp() {}

    public static Result call(NetworkManager net, Context ctx, String tasBaseUrl,
                              WalletApi walletApi, String txId) throws Exception {
        P220RequestVo req = new P220RequestVo(CaUtil.createMessageId(ctx), txId);
        EcdhRequestBuilder.Built built =
                EcdhRequestBuilder.build(walletApi, Constants.DID_DOC_TYPE_HOLDER);
        req.setReqEcdh(built.reqEcdh);
        String resp = net.send(tasBaseUrl + EcdhRequestBuilder.ENDPOINT, "POST", req.toJson());
        return new Result(resp, built.clientNonce, built.dhKeyPair);
    }
}
