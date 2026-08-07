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
import org.omnione.did.sdk.datamodel.common.enums.VerifyAuthType;
import org.omnione.did.sdk.datamodel.protocol.P220RequestVo;
import org.omnione.did.sdk.datamodel.protocol.P220ResponseVo;
import org.omnione.did.sdk.datamodel.util.MessageUtil;

public final class ProposeRevokeVcProxyOp {

    public static final class Result {
        public final String txId;
        public final String issuerNonce;
        public final VerifyAuthType.VERIFY_AUTH_TYPE authType;

        Result(String txId, String issuerNonce, VerifyAuthType.VERIFY_AUTH_TYPE authType) {
            this.txId = txId;
            this.issuerNonce = issuerNonce;
            this.authType = authType;
        }
    }

    private ProposeRevokeVcProxyOp() {}

    public static Result call(NetworkManager net, Context ctx, String proxyBaseUrl, String vcId)
            throws Exception {
        P220RequestVo req = new P220RequestVo(CaUtil.createMessageId(ctx));
        req.setVcId(vcId);
        String resp = net.send(proxyBaseUrl + "/proxy/api/v1/propose-revoke-vc",
                "POST", req.toJson());
        P220ResponseVo vo = MessageUtil.deserialize(resp, P220ResponseVo.class);
        return new Result(vo.getTxId(), vo.getIssuerNonce(), vo.getAuthType());
    }
}
