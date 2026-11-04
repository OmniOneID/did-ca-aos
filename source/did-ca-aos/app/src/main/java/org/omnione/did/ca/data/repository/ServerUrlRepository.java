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
package org.omnione.did.ca.data.repository;

import androidx.annotation.NonNull;

import org.omnione.did.ca.BuildConfig;
import org.omnione.did.ca.data.datasource.PreferencesDataSource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ServerUrlRepository {

    public static final String KEY_TAS_URL = "TAS_URL";
    public static final String KEY_CAS_URL = "CAS_URL";
    public static final String KEY_API_GW_URL = "API_GW_URL";
    public static final String KEY_WALLET_URL = "WALLET_URL";
    public static final String KEY_VERIFIER_URL = "VERIFIER_URL";
    public static final String KEY_DEMO_URL = "DEMO_URL";

    public static final List<String> KEYS = Collections.unmodifiableList(java.util.Arrays.asList(
            KEY_TAS_URL, KEY_CAS_URL, KEY_API_GW_URL,
            KEY_WALLET_URL, KEY_VERIFIER_URL, KEY_DEMO_URL));

    static final String PREFIX = "server_url_";

    private final PreferencesDataSource prefs;

    @Inject
    public ServerUrlRepository(@NonNull PreferencesDataSource prefs) {
        this.prefs = prefs;
    }

    @NonNull
    public String defaultUrl(@NonNull String key) {
        switch (key) {
            case KEY_TAS_URL: return BuildConfig.TAS_URL;
            case KEY_CAS_URL: return BuildConfig.CAS_URL;
            case KEY_API_GW_URL: return BuildConfig.API_GW_URL;
            case KEY_WALLET_URL: return BuildConfig.WALLET_URL;
            case KEY_VERIFIER_URL: return BuildConfig.VERIFIER_URL;
            case KEY_DEMO_URL: return BuildConfig.DEMO_URL;
            default: throw new IllegalArgumentException("unknown server url key: " + key);
        }
    }

    @NonNull
    public String url(@NonNull String key) {
        return prefs.getString(PREFIX + key, defaultUrl(key));
    }

    @NonNull
    public Map<String, String> custom() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : KEYS) {
            String value = prefs.getString(PREFIX + key, null);
            if (value != null) out.put(key, value);
        }
        return out;
    }

    public boolean save(@NonNull Map<String, String> custom) {
        return prefs.replacePrefix(PREFIX, custom);
    }
}
