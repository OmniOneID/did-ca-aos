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
import androidx.annotation.Nullable;

import org.omnione.did.ca.data.datasource.PreferencesDataSource;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class ProxyEndpointRepository {

    private static final String KEY_PREFIX = "proxy::";

    private final PreferencesDataSource prefs;

    @Inject
    public ProxyEndpointRepository(PreferencesDataSource prefs) {
        this.prefs = prefs;
    }

    public void put(@NonNull String vcId, @NonNull String endpoint) {
        prefs.putString(KEY_PREFIX + vcId, endpoint);
    }

    @Nullable
    public String get(@NonNull String vcId) {
        return prefs.getString(KEY_PREFIX + vcId, null);
    }

    public void remove(@NonNull String vcId) {
        prefs.remove(KEY_PREFIX + vcId);
    }

    public void clear() {
        prefs.removeByPrefix(KEY_PREFIX);
    }
}
