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

import androidx.annotation.Nullable;

import org.omnione.did.ca.data.network.NetworkManager;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.datamodel.protocol.P210RequestVo;
import org.omnione.did.sdk.datamodel.protocol.P210ResponseVo;
import org.omnione.did.sdk.datamodel.util.MessageUtil;

public final class ProposeIssueVcProxyOp {

    public static final class Result {
        public final String txId;
        public final String refId;
        public final String rawResponseJson;

        Result(String txId, String refId, String raw) {
            this.txId = txId;
            this.refId = refId;
            this.rawResponseJson = raw;
        }
    }

    private ProposeIssueVcProxyOp() {}

    public static Result call(NetworkManager net, Context ctx, String proxyBaseUrl,
                              String vcPlanId, String issuerDid, @Nullable String offerId)
            throws Exception {
        P210RequestVo req = new P210RequestVo(CaUtil.createMessageId(ctx));
        req.setVcPlanId(vcPlanId);
        req.setIssuer(issuerDid);
        req.setOfferId(offerId);
        String resp = net.send(proxyBaseUrl + "/proxy/api/v1/propose-issue-vc",
                "POST", req.toJson());
        P210ResponseVo vo = MessageUtil.deserialize(resp, P210ResponseVo.class);
        return new Result(vo.getTxId(), vo.getRefId(), resp);
    }
}
