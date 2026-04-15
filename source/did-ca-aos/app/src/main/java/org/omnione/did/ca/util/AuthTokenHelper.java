/*
 * Copyright 2025 OmniOne.
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

import org.json.JSONObject;
import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.config.Preference;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.AuthTokenRefreshCallback;
import org.omnione.did.ca.network.vo.CasTokenResVO;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.communication.urlconnection.HttpUrlConnectionTask;
import org.omnione.did.sdk.core.common.KeystoreManager;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.utility.DataModels.MultibaseType;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.utility.MultibaseUtils;

/**
 * Stores and retrieves CAS access / refresh tokens.
 * Refresh token is encrypted via Android Keystore before being saved to SharedPreferences.
 */
public class AuthTokenHelper {

    private static final String KEYSTORE_ALIAS_CAS_REFRESH = "alias_cas_refresh_token";

    // ── Access token ──────────────────────────────────────────────────────────

    public static void saveAccessToken(Context context, String accessToken) {
        CaLog.d("saveAccessToken: " + accessToken);
        Preference.getPreference(context)
                .edit()
                .putString(Constants.PREFERENCE_CAS_ACCESS_TOKEN, accessToken)
                .apply();
    }

    public static String getAccessToken(Context context) {
        String token = Preference.getPreference(context)
                .getString(Constants.PREFERENCE_CAS_ACCESS_TOKEN, "");
        CaLog.d("getAccessToken: " + token);
        return token;
    }

    public static boolean hasAccessToken(Context context) {
        return !getAccessToken(context).isEmpty();
    }

    // ── Refresh token (Keystore-encrypted) ────────────────────────────────────

    public static void saveRefreshToken(Context context, String refreshToken) {
        CaLog.d("saveRefreshToken");
        try {
            byte[] encrypted = KeystoreManager.encrypt(KEYSTORE_ALIAS_CAS_REFRESH,
                    refreshToken.getBytes(), context);
            String encoded = MultibaseUtils.encode(MultibaseType.MULTIBASE_TYPE.BASE_58_BTC, encrypted);
            Preference.getPreference(context)
                    .edit()
                    .putString(Constants.PREFERENCE_CAS_REFRESH_TOKEN, encoded)
                    .apply();
        } catch (WalletCoreException e) {
            CaLog.e("saveRefreshToken error: " + e.getMessage());
        }
    }

    public static String getRefreshToken(Context context) {
        try {
            String encoded = Preference.getPreference(context)
                    .getString(Constants.PREFERENCE_CAS_REFRESH_TOKEN, "");
            if (encoded.isEmpty()) return "";
            byte[] decrypted = KeystoreManager.decrypt(KEYSTORE_ALIAS_CAS_REFRESH,
                    MultibaseUtils.decode(encoded));
            String token = new String(decrypted);
            CaLog.d("getRefreshToken: " + token);
            return token;
        } catch (WalletCoreException | UtilityException e) {
            CaLog.e("getRefreshToken error: " + e.getMessage());
            return "";
        }
    }

    public static boolean hasRefreshToken(Context context) {
        return !getRefreshToken(context).isEmpty();
    }

    // ── Clear ──────────────────────────────────────────────────────────────────

    public static void clearAllTokens(Context context) {
        Preference.getPreference(context)
                .edit()
                .putString(Constants.PREFERENCE_CAS_ACCESS_TOKEN, "")
                .putString(Constants.PREFERENCE_CAS_REFRESH_TOKEN, "")
                .apply();
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    /**
     * Calls /cas/api/v1/jwt/refresh in a background thread and notifies via callback.
     */
    public static void requestTokenRefresh(Context context, AuthTokenRefreshCallback callback) {
        new Thread(() -> {
            try {
                CaLog.d("requestTokenRefresh start");
                JSONObject body = new JSONObject();
                body.put("refresh_token", getRefreshToken(context));

                String result = new HttpUrlConnectionTask()
                        .makeHttpRequest(Config.CAS.JWT_REFRESH, "POST", body.toString(), null);

                CasTokenResVO res = MessageUtil.deserialize(result, CasTokenResVO.class);
                saveAccessToken(context, res.getAccessToken());
                saveRefreshToken(context, res.getRefreshToken());

                CaLog.d("requestTokenRefresh success");
                callback.onSuccess(res.getAccessToken());
            } catch (Exception e) {
                CaLog.e("requestTokenRefresh error: " + e.getMessage());
                callback.onFailure(e);
            }
        }).start();
    }
}
