/*
 * Copyright 2024 OmniOne.
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

public class Constants {
    public final static String TITLE = "OpenDID";
    public final static String PREFERENCE_INIT = "init";
    public final static String PREFERENCE = "PREFERENCE";
    public final static String PREFERENCE_TAS_URL = "tas";
    public final static String PREFERENCE_VERIFIER_URL = "verifier";
    public final static String PREFERENCE_LOCK_PIN = "lock_pin";
    public final static String PREFERENCE_KEY_PIN = "key_pin";
    public final static String PREFERENCE_CA_APP_ID = "ca_app_id";
    public final static String PREFERENCE_USER_ID_FOR_KYC = "user_id_for_kyc";
    public final static String PREFERENCE_USER_NAME_FOR_DEMO = "user_name_for_demo";
    public final static String PREFERENCE_DID = "holder_did";
    public final static String PREFERENCE_DEVICE_DID = "device_did";
    public final static String PREFERENCE_PROFILE = "profile";
    public final static String PREFERENCE_PUSH_TOKEN = "push_token";
    
    public final static int DIALOG_CONFIRM_TYPE = 1;
    public final static int DIALOG_INPUT_TYPE = 2;
    public final static int DIALOG_ERROR_TYPE = 3;
    public final static int STEP1 = 1;
    public final static int STEP2 = 2;
    public final static int STEP3 = 3;
    public final static int WEBVIEW_USER_INFO = 1;
    public final static int WEBVIEW_VC_INFO = 2;

    // pin message
    public final static String PIN_REGISTER_TEXT = "Please register a PIN";
    public final static String PIN_INPUT_TEXT = "Please input a PIN";
    public final static String PIN_REGISTER_LOCK_TEXT = "Please register a Unlock PIN";
    public final static String PIN_INPUT_LOCK_TEXT = "Please input a Unlock PIN";
    public final static String PIN_NOT_MATCH_TEXT = "PIN does not match";
    public final static String PIN_CHANGE_TEXT = "Please input new PIN";
    public final static String PIN_RE_ENTER_TEXT = "Please re-enter your PIN";
    // dialog message
    public final static String DIALOG_MESSAGE_SET_LOCK = "Would you like to set the Wallet for lock type?";

    // intent Key
    public final static String INTENT_IS_REGISTRATION = "IS_REGISTRATION"; // true: reg, false: auth
    public final static String INTENT_TYPE_AUTHENTICATION = "TYPE_AUTHENTICATION"; // 1: set to lock type, 2: set to unlock type, 3: unlock status, 4: reg sign key, 5: use sign key
    public final static String INTENT_WALLET_TOKEN = "WALLET_TOKEN";

    public final static int PIN_TYPE_SET_LOCK = 1;
    public final static int PIN_TYPE_SET_UNLOCK = 2;
    public final static int PIN_TYPE_STATUS_UNLOCK = 3;
    public final static int PIN_TYPE_REG_KEY = 4;
    public final static int PIN_TYPE_USE_KEY = 5;
    public final static int PIN_TYPE_CHANGE_SIGNING_PIN = 6;
    public final static int PIN_TYPE_CHANGE_UNLOCK_PIN = 7;

    // did type
    public final static int DID_TYPE_DEVICE = 1;
    public final static int DID_TYPE_HOLDER = 2;

    // ca pkg name
    public final static String CA_PACKAGE_NAME = "org.omnione.did.ca";
    
    // type
    public final static String TYPE_ISSUE = "issue";

    public final static String TYPE_VERIFY = "verify";

    //key id
    public final static String KEY_ID_PIN = "pin";
    public final static String KEY_ID_BIO = "bio";
}
