/*
 * Copyright 2024-2025 OmniOne.
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

package org.omnione.did.ca.util;

import android.content.Context;


import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.datamodel.protocol.P210ResponseVo;
import org.omnione.did.sdk.datamodel.security.AccEcdh;
import org.omnione.did.sdk.datamodel.token.WalletTokenData;
import org.omnione.did.sdk.datamodel.common.enums.SymmetricCipherType;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.utility.CryptoUtils;
import org.omnione.did.sdk.utility.DataModels.CipherInfo;
import org.omnione.did.sdk.utility.DataModels.DigestEnum;
import org.omnione.did.sdk.utility.DataModels.EcKeyPair;
import org.omnione.did.sdk.utility.DataModels.EcType;
import org.omnione.did.sdk.utility.DataModels.MultibaseType;
import org.omnione.did.sdk.utility.DigestUtils;
import org.omnione.did.sdk.utility.Encodings.Base16;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.utility.MultibaseUtils;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;
import org.omnione.did.sdk.core.exception.WalletCoreException;

import java.util.concurrent.ExecutionException;

public class TokenUtil {
    public static String createHashWalletToken(String walletTokenDataStr, Context context) throws WalletException, UtilityException, WalletCoreException, ExecutionException, InterruptedException {
        WalletApi walletApi = WalletApi.getInstance(context);
        WalletTokenData walletTokenData = MessageUtil.deserialize(walletTokenDataStr, WalletTokenData.class);
        String resultNonce = walletApi.createNonceForWalletToken(Config.API_GATEWAY_URL, walletTokenData);
        String walletToken = walletTokenData.toJson() + resultNonce;
        walletToken = Base16.toHex(DigestUtils.getDigest(walletToken.getBytes(), DigestEnum.DIGEST_ENUM.SHA_256));
        CaLog.d("walletToken by CA : " + walletToken + " / " + walletTokenData.getSeed().getPurpose().toString());
        return walletToken;
    }

    public static String createServerToken(String std, String ecdh, byte[] clientNonce, EcKeyPair dhKeyPair) throws UtilityException{
        String iv = MessageUtil.deserialize(std, P210ResponseVo.class).getIv();
        String encStd = MessageUtil.deserialize(std, P210ResponseVo.class).getEncStd();
        AccEcdh accEcdh = MessageUtil.deserialize(ecdh, P210ResponseVo.class).getAccEcdh();
        String[] cipher = accEcdh.getCipher().getValue().split("-");
        String type = cipher[0];
        String size = cipher[1];
        String mode = cipher[2];
        CipherInfo info = new CipherInfo
                (CipherInfo.ENCRYPTION_TYPE.fromValue(cipher[0]),
                        CipherInfo.ENCRYPTION_MODE.fromValue(cipher[2]),
                        CipherInfo.SYMMETRIC_KEY_SIZE.fromValue(cipher[1]),
                        CipherInfo.SYMMETRIC_PADDING_TYPE.fromKey(accEcdh.getPadding().getValue()));

        byte[] sessKey = createDhSecret(clientNonce, accEcdh, dhKeyPair);
        byte[] plain = CryptoUtils.decrypt(MultibaseUtils.decode(encStd), info, sessKey, MultibaseUtils.decode(iv));
        String serverToken = MultibaseUtils.encode(MultibaseType.MULTIBASE_TYPE.BASE_58_BTC, DigestUtils.getDigest(new String(plain).getBytes(), DigestEnum.DIGEST_ENUM.SHA_256));
        CaLog.d("serverToken by CA : " + serverToken);
        return serverToken;

    }

    public static byte[] createDhSecret(byte[] clientNonce, AccEcdh accEcdh, EcKeyPair dhKeyPair) throws UtilityException {
        byte[] privKey = MultibaseUtils.decode(dhKeyPair.getPrivateKey());

        String multibasePrivKey = MultibaseUtils.encode(MultibaseType.MULTIBASE_TYPE.BASE_58_BTC, privKey);
        String multibasePubKey = accEcdh.getPublicKey();
        CaLog.d("getECDHSharedSecret client private key " + multibasePrivKey + " / " + privKey.length);
        CaLog.d("getECDHSharedSecret server public key " + multibasePubKey + " / " + MultibaseUtils.decode(multibasePubKey).length);

        byte[] serverPubKey = MultibaseUtils.decode(multibasePubKey);

        byte[] secretKey = CryptoUtils.generateSharedSecret(EcType.EC_TYPE.SECP256_R1, privKey, serverPubKey);
        String secretKeyStr = MultibaseUtils.encode(MultibaseType.MULTIBASE_TYPE.BASE_16, secretKey);

        byte[] serverNonce = MultibaseUtils.decode(accEcdh.getServerNonce());
        byte[] mergedNonce = mergeNonce(clientNonce, serverNonce);
        return mergeSharedSecretAndNonce(secretKey, mergedNonce, SymmetricCipherType.SYMMETRIC_CIPHER_TYPE.AES256CBC);
    }

    public static byte[] mergeNonce(byte[] clientNonce, byte[] serverNonce) throws UtilityException {
        int length = clientNonce.length + serverNonce.length;
        byte[] combinedData = new byte[length];
        System.arraycopy(clientNonce, 0, combinedData, 0, clientNonce.length);
        System.arraycopy(serverNonce, 0, combinedData, clientNonce.length, serverNonce.length);
        return DigestUtils.getDigest(combinedData, DigestEnum.DIGEST_ENUM.SHA_256);
    }

    public static byte[] mergeSharedSecretAndNonce(byte[] sharedSecret, byte[] nonce, SymmetricCipherType.SYMMETRIC_CIPHER_TYPE cipherType) throws UtilityException {
        byte[] clientMergedSharedSecret = null;
        int length = sharedSecret.length + nonce.length;
        byte[] digest = new byte[length];
        System.arraycopy(sharedSecret, 0, digest, 0, sharedSecret.length);
        System.arraycopy(nonce, 0, digest, sharedSecret.length, nonce.length);
        byte[] combinedResult = DigestUtils.getDigest(digest, DigestEnum.DIGEST_ENUM.SHA_256);

        if(cipherType.equals(SymmetricCipherType.SYMMETRIC_CIPHER_TYPE.AES128CBC) ||
                cipherType.equals(SymmetricCipherType.SYMMETRIC_CIPHER_TYPE.AES128ECB)) {
            clientMergedSharedSecret = new byte[16];
            System.arraycopy(combinedResult, 0, clientMergedSharedSecret, 0, 16);
        } else if (cipherType.equals(SymmetricCipherType.SYMMETRIC_CIPHER_TYPE.AES256CBC) ||
                cipherType.equals(SymmetricCipherType.SYMMETRIC_CIPHER_TYPE.AES256ECB)) {
            clientMergedSharedSecret = new byte[32];
            System.arraycopy(combinedResult, 0, clientMergedSharedSecret, 0, 32);
        }
        return clientMergedSharedSecret;
    }
}
