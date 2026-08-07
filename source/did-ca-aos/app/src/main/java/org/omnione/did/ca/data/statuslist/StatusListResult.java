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

package org.omnione.did.ca.data.statuslist;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.omnione.did.sdk.datamodel.vc.issue.VcStatus;

public final class StatusListResult {

    @NonNull public final VcStatus status;
    public final boolean checkFailed;
    @Nullable @StringRes public final Integer failMessageRes;

    public StatusListResult(@NonNull VcStatus status,
                            boolean checkFailed,
                            @Nullable Integer failMessageRes) {
        this.status = status;
        this.checkFailed = checkFailed;
        this.failMessageRes = failMessageRes;
    }
}
