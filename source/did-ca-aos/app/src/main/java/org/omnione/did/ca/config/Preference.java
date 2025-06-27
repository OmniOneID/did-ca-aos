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

import android.content.Context;
import android.content.SharedPreferences;

public class Preference {

    private static SharedPreferences getPreference(Context context){
        return context.getSharedPreferences(Constants.PREFERENCE, Context.MODE_PRIVATE);
    }
    public static void setInit(Context context, boolean isInit) {
        SharedPreferences prefs = getPreference(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Constants.PREFERENCE_INIT, isInit);
        editor.apply();
    }
    public static boolean getInit(Context context) {
        SharedPreferences prefs = getPreference(context);
        return prefs.getBoolean(Constants.PREFERENCE_INIT, false);
    }
    public static void saveTasUrl(Context context, String url) {
        SharedPreferences prefs = getPreference(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Constants.PREFERENCE_TAS_URL, url);
        editor.apply();
    }
    public static String loadTasUrl(Context context) {
        SharedPreferences prefs = getPreference(context);
        return prefs.getString(Constants.PREFERENCE_TAS_URL, Config.TAS_URL);
    }
    public static void saveVerifierUrl(Context context, String url) {
        SharedPreferences prefs = getPreference(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Constants.PREFERENCE_VERIFIER_URL, url);
        editor.apply();
    }

    public static String loadVerifierUrl(Context context) {
        SharedPreferences prefs = getPreference(context);
        return prefs.getString(Constants.PREFERENCE_VERIFIER_URL, Config.VERIFIER_URL);
    }

    public static void saveCaAppId(Context context, String caAppId) {
        SharedPreferences prefs = getPreference(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Constants.PREFERENCE_CA_APP_ID, caAppId);
        editor.apply();
    }

    public static String getCaAppId(Context context) {
        SharedPreferences prefs = getPreference(context);
        return prefs.getString(Constants.PREFERENCE_CA_APP_ID, "");
    }

    public static void savePicture(Context context, String picture) {
        SharedPreferences prefs = getPreference(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("picture", picture);
        editor.apply();
    }
    public static String loadPicture(Context context) {
        SharedPreferences prefs = getPreference(context);
        return prefs.getString("picture", "");
    }

    public static void setUsernameForDemo(Context context, String username) {
        SharedPreferences prefs = getPreference(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Constants.PREFERENCE_USER_NAME_FOR_DEMO, username);
        editor.apply();
    }
    public static String getUsernameForDemo(Context context) {
        SharedPreferences prefs = getPreference(context);
        return prefs.getString(Constants.PREFERENCE_USER_NAME_FOR_DEMO, "");
    }
    public static void setUserIdForDemo(Context context, String userId) {
        SharedPreferences prefs = getPreference(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Constants.PREFERENCE_USER_ID_FOR_KYC, userId);
        editor.apply();
    }
    public static String getUserIdForDemo(Context context) {
        SharedPreferences prefs = getPreference(context);
        return prefs.getString(Constants.PREFERENCE_USER_ID_FOR_KYC,"");
    }

    public static void setDID(Context context, String did) {
        SharedPreferences prefs = getPreference(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Constants.PREFERENCE_DID, did);
        editor.apply();
    }
    public static String getDID(Context context) {
        SharedPreferences prefs = getPreference(context);
        return prefs.getString(Constants.PREFERENCE_DID,"");
    }

    public static void setProfile(Context context, String profile) {
        SharedPreferences prefs = getPreference(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Constants.PREFERENCE_PROFILE, profile);
        editor.apply();
    }
    public static String getProfile(Context context) {
        SharedPreferences prefs = getPreference(context);
        return prefs.getString(Constants.PREFERENCE_PROFILE,"");
    }
    public static void setPushToken(Context context, String pushToken) {
        SharedPreferences prefs = getPreference(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Constants.PREFERENCE_PUSH_TOKEN, pushToken);
        editor.apply();
    }
    public static String getPushToken(Context context) {
        SharedPreferences prefs = getPreference(context);
        return prefs.getString(Constants.PREFERENCE_PUSH_TOKEN,"");
    }
    public static void deleteAllPref(Context context){
        SharedPreferences pref = getPreference(context);
        SharedPreferences.Editor editor = pref.edit();
        editor.clear();
        editor.apply();
    }
}
