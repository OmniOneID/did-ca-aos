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

package org.omnione.did.ca.ui.common;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.omnione.did.ca.R;
import org.omnione.did.sdk.datamodel.vc.issue.VcStatus;

public final class CredentialStatusBinder {

    private CredentialStatusBinder() {
    }

    public static void bind(@NonNull View statusBadge,
                            @NonNull View statusDot,
                            @NonNull TextView statusText,
                            @NonNull VcStatus status) {
        int bgRes;
        int dotRes;
        int fgRes;
        int labelRes;
        switch (status) {
            case INACTIVE:
                bgRes = R.drawable.bg_status_inactive;
                dotRes = R.drawable.bg_status_dot_inactive;
                fgRes = R.color.status_inactive_fg;
                labelRes = R.string.status_inactive;
                break;
            case REVOKED:
                bgRes = R.drawable.bg_status_revoked;
                dotRes = R.drawable.bg_status_dot_revoked;
                fgRes = R.color.status_revoked_fg;
                labelRes = R.string.status_revoked;
                break;
            case ACTIVE:
            default:
                bgRes = R.drawable.bg_status_active;
                dotRes = R.drawable.bg_status_dot_active;
                fgRes = R.color.status_active_fg;
                labelRes = R.string.status_active;
                break;
        }
        statusBadge.setBackgroundResource(bgRes);
        statusDot.setBackgroundResource(dotRes);
        statusText.setTextColor(statusText.getResources().getColor(fgRes, null));
        statusText.setText(labelRes);
    }
}
