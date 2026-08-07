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

package org.omnione.did.ca.data.datasource;

import android.content.Context;
import android.content.SharedPreferences;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public final class PreferencesDataSource {

    private static final String FILE_NAME = "did_ca_prefs";

    private final SharedPreferences prefs;

    @Inject
    public PreferencesDataSource(@ApplicationContext Context ctx) {
        this.prefs = ctx.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public String getString(String key, String def) {
        return prefs.getString(key, def);
    }

    public void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    public boolean getBoolean(String key, boolean def) {
        return prefs.getBoolean(key, def);
    }

    public void putBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    public void remove(String key) {
        prefs.edit().remove(key).apply();
    }

    public void removeByPrefix(String prefix) {
        SharedPreferences.Editor e = prefs.edit();
        for (String k : prefs.getAll().keySet()) {
            if (k.startsWith(prefix)) e.remove(k);
        }
        e.apply();
    }
}
