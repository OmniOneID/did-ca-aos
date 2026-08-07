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

import android.widget.TextView;

import androidx.annotation.NonNull;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.CredentialBadge;

public final class CredentialBadgeBinder {

    private CredentialBadgeBinder() {
    }

    public static void bind(@NonNull TextView badge, @NonNull CredentialBadge kind) {
        int bgRes;
        int fgRes;
        int labelRes;
        switch (kind) {
            case SD_JWT:
                bgRes = R.drawable.bg_badge_sdjwt;
                fgRes = R.color.badge_sdjwt_fg;
                labelRes = R.string.badge_sdjwt;
                break;
            case MDOC:
                bgRes = R.drawable.bg_badge_mdoc;
                fgRes = R.color.badge_mdoc_fg;
                labelRes = R.string.badge_mdoc;
                break;
            case VC:
            default:
                bgRes = R.drawable.bg_badge_vc;
                fgRes = R.color.badge_vc_fg;
                labelRes = R.string.badge_vc;
                break;
        }
        badge.setBackgroundResource(bgRes);
        badge.setTextColor(badge.getResources().getColor(fgRes, null));
        badge.setText(labelRes);
    }
}
