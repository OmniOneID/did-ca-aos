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

import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.datamodel.common.enums.EllipticCurveType;
import org.omnione.did.sdk.datamodel.common.enums.SymmetricCipherType;
import org.omnione.did.sdk.datamodel.security.ReqEcdh;
import org.omnione.did.sdk.utility.CryptoUtils;
import org.omnione.did.sdk.utility.DataModels.EcKeyPair;
import org.omnione.did.sdk.utility.DataModels.EcType;
import org.omnione.did.sdk.utility.DataModels.MultibaseType;
import org.omnione.did.sdk.utility.MultibaseUtils;
import org.omnione.did.sdk.wallet.walletservice.config.Constants;

import java.util.List;

final class EcdhRequestBuilder {

    static final String ENDPOINT = "/tas/api/v1/request-ecdh";

    static final class Built {
        final ReqEcdh reqEcdh;
        final byte[] clientNonce;
        final EcKeyPair dhKeyPair;

        Built(ReqEcdh reqEcdh, byte[] clientNonce, EcKeyPair dhKeyPair) {
            this.reqEcdh = reqEcdh;
            this.clientNonce = clientNonce;
            this.dhKeyPair = dhKeyPair;
        }
    }

    private EcdhRequestBuilder() {}

    static Built build(WalletApi walletApi, int didDocType) throws Exception {
        String did = walletApi.getDIDDocument(didDocType).getId();

        byte[] clientNonce = CryptoUtils.generateNonce(16);
        EcKeyPair dhKeyPair = CryptoUtils.generateECKeyPair(EcType.EC_TYPE.SECP256_R1);

        ReqEcdh reqEcdh = new ReqEcdh();
        reqEcdh.setClient(did);
        reqEcdh.setClientNonce(
                MultibaseUtils.encode(MultibaseType.MULTIBASE_TYPE.BASE_58_BTC, clientNonce));
        reqEcdh.setCurve(EllipticCurveType.ELLIPTIC_CURVE_TYPE.SECP256R1);
        reqEcdh.setPublicKey(dhKeyPair.getPublicKey());
        reqEcdh.setCandidate(new ReqEcdh.Ciphers(
                List.of(SymmetricCipherType.SYMMETRIC_CIPHER_TYPE.AES256CBC)));

        reqEcdh = (ReqEcdh) walletApi.addProofsToDocument(
                reqEcdh, List.of(Constants.KEY_ID_KEY_AGREE), did, didDocType, null, false);

        return new Built(reqEcdh, clientNonce, dhKeyPair);
    }
}
