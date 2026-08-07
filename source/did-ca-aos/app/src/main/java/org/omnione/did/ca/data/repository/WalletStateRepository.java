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

import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.sdk.datamodel.did.DIDDocument;
import org.omnione.did.sdk.wallet.walletservice.config.Constants;

import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class WalletStateRepository {

    private final WalletGateway gateway;

    @Inject
    public WalletStateRepository(WalletGateway gateway) {
        this.gateway = gateway;
    }

        public CompletableFuture<Boolean> ensureWalletCreated() {
        return gateway.isExistWallet().thenCompose(exists -> {
            if (exists) {
                return CompletableFuture.completedFuture(false);
            }

            return gateway.createWallet().thenApply(success -> {
                if (!success) {
                    throw new IllegalStateException("walletApi.createWallet returned false");
                }
                return true;
            });
        });
    }

        public CompletableFuture<Boolean> isLocked() {
        return gateway.isLocked();
    }

        public CompletableFuture<Void> deleteWallet() {
        return gateway.deleteWallet();
    }

        public CompletableFuture<String> getHolderDid() {
        return gateway.getDIDDocument(Constants.DID_DOC_TYPE_HOLDER).thenApply(DIDDocument::getId);
    }
}
