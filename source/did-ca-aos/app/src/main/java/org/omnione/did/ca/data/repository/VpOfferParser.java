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

import org.omnione.did.ca.data.model.VerifyOfferEnvelope;
import org.omnione.did.ca.data.network.PayloadData;
import org.omnione.did.sdk.datamodel.offer.VerifyOfferPayload;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.utility.MultibaseUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class VpOfferParser {

    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    @Inject
    public VpOfferParser() {}

        public CompletableFuture<VerifyOfferEnvelope> parseOffer(@NonNull String rawPayload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (rawPayload.startsWith("openid4vp://")) {
                    return VerifyOfferEnvelope.oid4vp(rawPayload);
                }
                PayloadData payloadData = MessageUtil.deserialize(rawPayload, PayloadData.class);
                if (payloadData == null) {
                    throw new IllegalArgumentException("Not a verify offer");
                }
                String type = payloadData.getPayloadType();
                if (!PayloadData.TYPE_SUBMIT_VP.equals(type)) {
                    throw new IllegalArgumentException("Not a verify offer");
                }
                String inner = new String(MultibaseUtils.decode(payloadData.getPayload()));
                VerifyOfferPayload offer = MessageUtil.deserialize(inner, VerifyOfferPayload.class);
                if (offer == null || offer.getOfferId() == null) {
                    throw new IllegalArgumentException("Incomplete verify offer payload");
                }
                String txId = payloadData.getTxId() != null ? payloadData.getTxId() : "";
                return (offer.getType() == VerifyOfferPayload.OFFER_TYPE.VerifyProofOffer)
                        ? VerifyOfferEnvelope.zkp(offer.getOfferId(), txId)
                        : VerifyOfferEnvelope.openDidVc(offer.getOfferId(), txId);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, backgroundExecutor);
    }
}
