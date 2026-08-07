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
import org.omnione.did.sdk.datamodel.profile.IssueProfile;
import org.omnione.did.sdk.datamodel.protocol.P210RequestVo;
import org.omnione.did.sdk.datamodel.protocol.P210ResponseVo;
import org.omnione.did.sdk.datamodel.util.MessageUtil;

public final class RequestIssueProfileProxyOp {

    public static final class Result {
        public final IssueProfile profile;
        public final String authNonce;
        public final String rawResponseJson;

        Result(IssueProfile p, String n, String raw) {
            this.profile = p;
            this.authNonce = n;
            this.rawResponseJson = raw;
        }
    }

    private RequestIssueProfileProxyOp() {}

    public static Result call(NetworkManager net, Context ctx, String proxyBaseUrl,
                              String txId, String userId) throws Exception {
        P210RequestVo req = new P210RequestVo(CaUtil.createMessageId(ctx), txId);
        req.setUserId(userId != null ? userId : "");
        String resp = net.send(proxyBaseUrl + "/proxy/api/v1/request-issue-profile",
                "POST", req.toJson());
        P210ResponseVo vo = MessageUtil.deserialize(resp, P210ResponseVo.class);
        return new Result(vo.getProfile(), vo.getAuthNonce(), resp);
    }
}
