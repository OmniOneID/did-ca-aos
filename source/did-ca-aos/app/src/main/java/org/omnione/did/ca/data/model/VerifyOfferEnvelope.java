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

package org.omnione.did.ca.data.model;

import androidx.annotation.NonNull;

public final class VerifyOfferEnvelope {

    public final boolean isZkp;
    public final boolean isOid4vp;
    @NonNull public final String offerId;
    @NonNull public final String txId;
    @NonNull public final String oid4vpUrl;

    private VerifyOfferEnvelope(boolean isZkp,
                                boolean isOid4vp,
                                @NonNull String offerId,
                                @NonNull String txId,
                                @NonNull String oid4vpUrl) {
        this.isZkp = isZkp;
        this.isOid4vp = isOid4vp;
        this.offerId = offerId;
        this.txId = txId;
        this.oid4vpUrl = oid4vpUrl;
    }

    @NonNull
    public static VerifyOfferEnvelope openDidVc(@NonNull String offerId, @NonNull String txId) {
        return new VerifyOfferEnvelope(false, false, offerId, txId, "");
    }

    @NonNull
    public static VerifyOfferEnvelope zkp(@NonNull String offerId, @NonNull String txId) {
        return new VerifyOfferEnvelope(true, false, offerId, txId, "");
    }

    @NonNull
    public static VerifyOfferEnvelope oid4vp(@NonNull String url) {
        return new VerifyOfferEnvelope(false, true, "", "", url);
    }
}
