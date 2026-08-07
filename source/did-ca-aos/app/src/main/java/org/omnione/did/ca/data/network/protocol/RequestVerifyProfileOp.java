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
import org.omnione.did.sdk.datamodel.profile.VerifyProfile;
import org.omnione.did.sdk.datamodel.protocol.P310RequestVo;
import org.omnione.did.sdk.datamodel.protocol.P310ResponseVo;
import org.omnione.did.sdk.datamodel.util.MessageUtil;

public final class RequestVerifyProfileOp {

    public static final class Result {
        public final String txId;
        public final VerifyProfile profile;
        public final String rawResponseJson;

        Result(String txId, VerifyProfile profile, String raw) {
            this.txId = txId;
            this.profile = profile;
            this.rawResponseJson = raw;
        }
    }

    private RequestVerifyProfileOp() {}

    public static Result call(NetworkManager net, Context ctx, String verifierBaseUrl,
                              String offerId, String txId) throws Exception {
        P310RequestVo req = new P310RequestVo(CaUtil.createMessageId(ctx), txId);
        req.setOfferId(offerId);
        String resp = net.send(verifierBaseUrl + "/verifier/api/v1/request-profile",
                "POST", req.toJson());
        P310ResponseVo vo = MessageUtil.deserialize(resp, P310ResponseVo.class);
        return new Result(vo.getTxId(), vo.getProfile(), resp);
    }
}
