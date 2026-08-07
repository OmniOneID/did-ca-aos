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

import org.json.JSONObject;
import org.omnione.did.ca.data.network.NetworkManager;
import org.omnione.did.sdk.communication.exception.CommunicationException;

public final class SignupOp {

    public static String call(NetworkManager net, String casBaseUrl,
                              String userId, String walletId) throws CommunicationException {
        JSONObject body = new JSONObject();
        try {
            body.put("userId", userId);
            body.put("walletId", walletId);
        } catch (Exception ignore) {}
        return net.send(casBaseUrl + "/cas/api/v1/user/signup", "POST", body.toString());
    }

    private SignupOp() {}
}
