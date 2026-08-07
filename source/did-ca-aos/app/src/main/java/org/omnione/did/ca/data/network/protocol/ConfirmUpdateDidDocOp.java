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
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.datamodel.protocol.P141RequestVo;

public final class ConfirmUpdateDidDocOp {

    private ConfirmUpdateDidDocOp() {}

    public static String call(NetworkManager net, Context ctx, String tasBaseUrl,
                              String txId, String serverToken) throws CommunicationException {
        P141RequestVo req = new P141RequestVo(CaUtil.createMessageId(ctx), txId);
        req.setServerToken(serverToken);
        return net.send(tasBaseUrl + "/tas/api/v1/confirm-update-diddoc", "POST", req.toJson());
    }
}
