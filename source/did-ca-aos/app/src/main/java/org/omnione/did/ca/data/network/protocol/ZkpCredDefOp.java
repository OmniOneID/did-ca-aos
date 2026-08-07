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

import org.omnione.did.ca.data.network.NetworkManager;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.datamodel.zkp.CredentialDefinition;
import org.omnione.did.sdk.datamodel.zkp.CredentialDefinitionVo;
import org.omnione.did.sdk.utility.MultibaseUtils;

public final class ZkpCredDefOp {

    private ZkpCredDefOp() {}

    public static CredentialDefinition call(NetworkManager net, String apiGwBaseUrl,
                                            String credDefId) throws Exception {
        String resp = net.send(
                apiGwBaseUrl + "/api-gateway/api/v1/zkp-cred-def?id=" + credDefId,
                "GET", "");
        CredentialDefinitionVo vo = MessageUtil.deserialize(resp, CredentialDefinitionVo.class);
        String decoded = new String(MultibaseUtils.decode(vo.getCredDef()));
        return MessageUtil.deserialize(decoded, CredentialDefinition.class);
    }
}
