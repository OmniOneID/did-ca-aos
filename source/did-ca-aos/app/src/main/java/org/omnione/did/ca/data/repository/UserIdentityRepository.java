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

import org.omnione.did.ca.data.datasource.PreferencesDataSource;
import org.omnione.did.ca.util.CaUtil;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class UserIdentityRepository {

    private static final String KEY_CA_APP_ID  = "ca_app_id";
    private static final String KEY_USER_ID    = "user_id";
    private static final String KEY_DID        = "did";
    private static final String KEY_INIT       = "init";

    private final PreferencesDataSource prefs;

    @Inject
    public UserIdentityRepository(PreferencesDataSource prefs) {
        this.prefs = prefs;
    }

        public String getOrCreateCaAppId() {
        String id = prefs.getString(KEY_CA_APP_ID, null);
        if (id == null) {

            id = CaUtil.createCaAppId();
            prefs.putString(KEY_CA_APP_ID, id);
        }
        return id;
    }

    public String  getUserId()                     { return prefs.getString(KEY_USER_ID, null); }
    public void    setUserId(String userId)        { prefs.putString(KEY_USER_ID, userId); }
    public void    setDid(String did)              { prefs.putString(KEY_DID, did); }
    public boolean isInitialized()                 { return prefs.getBoolean(KEY_INIT, false); }
    public void    setInitialized(boolean init)    { prefs.putBoolean(KEY_INIT, init); }
}
