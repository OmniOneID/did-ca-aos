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

import org.omnione.did.ca.data.datasource.PreferencesDataSource;
import org.omnione.did.ca.data.model.BleTransferMode;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class BleTransferModeRepository {

    static final BleTransferMode DEFAULT_MODE = BleTransferMode.BOTH;

    private static final String KEY_BLE_TRANSFER_MODE = "ble_transfer_mode";

    private final PreferencesDataSource prefs;

    @Inject
    public BleTransferModeRepository(@NonNull PreferencesDataSource prefs) {
        this.prefs = prefs;
    }

    @NonNull
    public BleTransferMode get() {
        return BleTransferMode.fromId(
                prefs.getString(KEY_BLE_TRANSFER_MODE, null), DEFAULT_MODE);
    }

    public void set(@NonNull BleTransferMode mode) {
        prefs.putString(KEY_BLE_TRANSFER_MODE, mode.id);
    }
}
