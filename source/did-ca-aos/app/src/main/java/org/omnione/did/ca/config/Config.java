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

package org.omnione.did.ca.config;

public class Config {
    //////////// SERVER URL ////////////////
//    public final static String CAS_URL = "http://192.168.3.130:8094"; //dev server
//    public final static String TAS_URL = "http://192.168.3.130:8090"; //dev server
//    public final static String VERIFIER_URL = "http://192.168.3.130:8092"; //dev server
//    public final static String DEMO_URL = "http://192.168.3.130:8099"; //dev
//    public final static String WALLET_URL = "http://192.168.3.130:8095"; //dev
//    public final static String API_GATEWAY_URL = "http://192.168.3.130:8093"; //dev

    // 진환
    public final static String CAS_URL = "http://10.48.17.129:8094"; //dev server
    public final static String TAS_URL = "http://10.48.17.129:8090"; //dev server
    public final static String ISSUE_URL = "http://10.48.17.129:8091"; //dev server
    public final static String VERIFIER_URL = "http://10.48.17.129:8092"; //dev server
    public final static String DEMO_URL = "http://10.48.17.129:8099"; //dev
    public final static String WALLET_URL = "http://10.48.17.129:8095"; //dev
    public final static String API_GATEWAY_URL = "http://10.48.17.129:8093"; //dev

    // 길우
//    public final static String CAS_URL = "http://10.48.17.128:8094"; //dev server
//    public final static String TAS_URL = "http://10.48.17.128:8090"; //dev server
//    public final static String VERIFIER_URL = "http://10.48.17.129:8092"; //dev server
//    public final static String DEMO_URL = "http://10.48.17.128:8099"; //dev
//    public final static String WALLET_URL = "http://10.48.17.128:8095"; //dev
//    public final static String API_GATEWAY_URL = "http://10.48.17.128:8093"; //dev

    public static String blockChainHost = "http://10.48.17.128:8091";

    //////////// pin config ////////////////
    public final static int PIN_MAX_VALUE = 6;
    public final static int PIN_FAIL_DELAY = 2000;

    //////////// Splash config  ////////////////
    public final static int SPLASH_DELAY = 2000;







    // ZKP
    public static String issuerHost = "http://10.48.202.157:40001";
    public static String spHost = "http://10.48.202.157:40001";

    // Protocol
    public static String credentialOffer = "/omniapi/zkp/requestCredentialOffer";
//    public static String getTableRows = "/v1/chain/get_table_rows";
    public static String getTableRows = "/issuer/api/v1/v1/chain/get_table_rows";
    public static String credentialRequest = "/omniapi/zkp/getCredential";
    public static String proofRequest = "/omniapi/zkp/createProofRequest";
    public static String verifyProof = "/omniapi/zkp/verifyProof";

    public static String requestVerifyProof = "/verifier/api/v1/request-verify-proof";

}
