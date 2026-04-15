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

import static org.omnione.did.ca.BuildConfig.API_GW_URL;
import static org.omnione.did.ca.BuildConfig.CAS_URL;
import static org.omnione.did.ca.BuildConfig.DEMO_URL;
import static org.omnione.did.ca.BuildConfig.TAS_URL;
import static org.omnione.did.ca.BuildConfig.VERIFIER_URL;
import static org.omnione.did.ca.BuildConfig.WALLET_URL;

import android.net.Uri;

public final class Config {

    //////////// pin config ////////////////
    public final static int PIN_MAX_VALUE = 6;
    public final static int PIN_FAIL_DELAY = 2000;

    //////////// Splash config  ////////////////
    public final static int SPLASH_DELAY = 2000;

    //////////// API ////////////////
    public static final class CAS {
        private CAS() {}
        public static final String BASE_URL = CAS_URL;

        public static final String REQUEST_WALLET_TOKENDATA = BASE_URL + "/cas/api/v1/request-wallet-tokendata";
        public static final String REQUEST_ATTESTED_APPINFO = BASE_URL + "/cas/api/v1/request-attested-appinfo";

        // login simplification
        public static final String CHECK_REGISTRATION_STATUS = BASE_URL + "/cas/api/v1/user/check-registration-status";
        public static final String SIGNUP = BASE_URL + "/cas/api/v1/user/signup";
        public static final String SIGNIN = BASE_URL + "/cas/api/v1/user/signin";
        public static final String SIGNOUT = BASE_URL + "/cas/api/v1/user/signout";
        public static final String WITHDRAW = BASE_URL + "/cas/api/v1/user/withdraw";
        public static final String JWT_VERIFY = BASE_URL + "/cas/api/v1/jwt/verify";
        public static final String JWT_REFRESH = BASE_URL + "/cas/api/v1/jwt/refresh";
    }
    public static final class TAS {
        private TAS() {}
        public static final String BASE_URL = TAS_URL;

        public static final String VC_PLAN_LIST = BASE_URL + "/list/api/v1/vcplan/list";

        // update user
        public static final String PROPOSE_UPDATE_USER = BASE_URL + "/tas/api/v1/propose-update-diddoc";
        public static final String CONFIRM_UPDATE_USER = BASE_URL + "/tas/api/v1/confirm-update-diddoc";


        // reg user
        public static final String RETRIEVE_KYC = BASE_URL + "/tas/api/v1/retrieve-kyc";
        public static final String PROPOSE_REGISTER_USER = BASE_URL + "/tas/api/v1/propose-register-user";
        public static final String REQUEST_ECDH = BASE_URL + "/tas/api/v1/request-ecdh";
        public static final String REQUEST_CREATE_TOKEN = BASE_URL + "/tas/api/v1/request-create-token";
        public static final String CONFIRM_REGISTER_USER = BASE_URL + "/tas/api/v1/confirm-register-user";

        // issue vc
        public static final String PROPOSE_ISSUE_VC = BASE_URL + "/tas/api/v1/propose-issue-vc";
        public static final String REQUEST_ISSUE_PROFILE = BASE_URL + "/tas/api/v1/request-issue-profile";
        public static final String CONFIRM_ISSUE_VC = BASE_URL + "/tas/api/v1/confirm-issue-vc";

    }


    public static final class Verifier {
        private Verifier() {}
        public static final String BASE_URL = VERIFIER_URL;

        public static final String REQUEST_PROOF_REQUEST_PROFILE = BASE_URL + "/verifier/api/v1/request-proof-request-profile";
        public static final String REQUEST_VERIFY_PROOF = BASE_URL + "/verifier/api/v1/request-verify-proof";
        public static final String REQUEST_VERIFY_PROFILE = BASE_URL + "/verifier/api/v1/request-profile";
        public static final String REQUEST_VERIFY_VP = BASE_URL + "/verifier/api/v1/request-verify";
    }

    public static final class WAS {
        private WAS() {}
        public static final String BASE_URL = WALLET_URL;
    }


    public static final class Base_Demo {
        private Base_Demo() {}
        public static final String BASE_URL = DEMO_URL;
    }
    public static final class ApiGW {
        private ApiGW() {}
        public static final String BASE_URL = API_GW_URL;
    }
}