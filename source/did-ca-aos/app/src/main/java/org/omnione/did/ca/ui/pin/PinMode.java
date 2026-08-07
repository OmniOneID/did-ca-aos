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

package org.omnione.did.ca.ui.pin;

import org.omnione.did.ca.R;

public enum PinMode {
    REGISTER(R.string.pin_title_register, R.string.pin_desc_register_0, R.string.pin_error_mismatch) {
        @Override
        int descRes(int step) {
            return step == 0 ? R.string.pin_desc_register_0 : R.string.pin_desc_register_1;
        }
    },
    REGISTER_SIGN(R.string.pin_title_auth, R.string.pin_desc_auth_0, R.string.pin_error_wrong),
    CHANGE(R.string.pin_title_change, R.string.pin_desc_change_0, R.string.pin_error_wrong) {
        @Override
        int descRes(int step) {
            if (step == 0) return R.string.pin_desc_change_0;
            if (step == 1) return R.string.pin_desc_change_1;
            return R.string.pin_desc_change_2;
        }

        @Override
        int errorRes(int step) {
            if (step == 0) return R.string.pin_error_wrong;
            if (step == 1) return R.string.pin_error_same_as_old;
            return R.string.pin_error_mismatch;
        }
    },
    AUTH(R.string.pin_title_auth, R.string.pin_desc_auth_0, R.string.pin_error_wrong),
    UNLOCK_REGISTER(R.string.pin_title_unlock_register, R.string.pin_desc_unlock_register_0, R.string.pin_error_unlock_mismatch) {
        @Override
        int descRes(int step) {
            return step == 0 ? R.string.pin_desc_unlock_register_0 : R.string.pin_desc_unlock_register_1;
        }
    },
    UNLOCK_AUTH(R.string.pin_title_unlock_auth, R.string.pin_desc_unlock_auth_0, R.string.pin_error_unlock_wrong),
    UNLOCK_CHANGE(R.string.pin_title_unlock_change, R.string.pin_desc_unlock_change_0, R.string.pin_error_unlock_wrong) {
        @Override
        int descRes(int step) {
            if (step == 0) return R.string.pin_desc_unlock_change_0;
            if (step == 1) return R.string.pin_desc_unlock_change_1;
            return R.string.pin_desc_unlock_change_2;
        }

        @Override
        int errorRes(int step) {
            if (step == 0) return R.string.pin_error_unlock_wrong;
            if (step == 1) return R.string.pin_error_unlock_same_as_old;
            return R.string.pin_error_unlock_mismatch;
        }
    };

    private final int titleRes;
    private final int descRes;
    private final int errorRes;

    PinMode(int titleRes, int descRes, int errorRes) {
        this.titleRes = titleRes;
        this.descRes = descRes;
        this.errorRes = errorRes;
    }

    int titleRes() {
        return titleRes;
    }

    int descRes(int step) {
        return descRes;
    }

    int errorRes(int step) {
        return errorRes;
    }
}
