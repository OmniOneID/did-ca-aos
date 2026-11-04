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

package org.omnione.did.ca.util;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.repository.ProximitySessionEndedException;
import org.omnione.did.proximity.sdk.core.ProximityException;

import java.util.concurrent.CompletionException;

public final class ProximityErrors {

    public static final int NOT_A_SESSION_ERROR = 0;

    private ProximityErrors() {}

    @StringRes
    public static int messageFor(@Nullable Throwable err) {
        Throwable root = err;
        while (root instanceof CompletionException && root.getCause() != null) {
            root = root.getCause();
        }
        if (root instanceof ProximitySessionEndedException) {
            return R.string.notice_proximity_verifier_ended_message;
        }
        if (root instanceof ProximityException) {
            ProximityException.Reason reason = ((ProximityException) root).reason();
            if (reason == ProximityException.Reason.ENGAGEMENT_TIMEOUT) {
                return R.string.notice_proximity_engagement_timeout_message;
            }
            if (reason == ProximityException.Reason.RESPONSE_TIMEOUT) {
                return R.string.notice_proximity_response_timeout_message;
            }
            return R.string.notice_proximity_failed_message;
        }
        return NOT_A_SESSION_ERROR;
    }
}
