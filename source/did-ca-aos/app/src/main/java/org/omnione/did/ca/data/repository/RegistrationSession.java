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

import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class RegistrationSession {

    private volatile CompletableFuture<String> hWalletTokenFuture = new CompletableFuture<>();
    private volatile String  txId;
    private volatile String  serverToken;

    private volatile boolean personalizeDone;

    @Inject
    public RegistrationSession() {}

    public synchronized void setPreExecuteResult(
            String txId, String hWalletToken, String serverToken) {
        this.txId = txId;
        this.serverToken = serverToken;
        if (hWalletTokenFuture.isDone()) {
            hWalletTokenFuture = new CompletableFuture<>();
        }
        hWalletTokenFuture.complete(hWalletToken);
    }

    public synchronized CompletableFuture<String> awaitHWalletToken() {
        return hWalletTokenFuture;
    }

    public synchronized String getHWalletTokenIfReady() {
        if (!hWalletTokenFuture.isDone()) return null;
        try { return hWalletTokenFuture.getNow(null); }
        catch (Throwable t) { return null; }
    }

    public String getTxId()                                  { return txId; }
    public String getServerToken()                           { return serverToken; }

    public synchronized void markPersonalizeDone()           { this.personalizeDone = true; }
    public boolean isPersonalizeDone()                       { return personalizeDone; }

    public synchronized void failPreExecute(Throwable t) {
        if (!hWalletTokenFuture.isDone()) {
            hWalletTokenFuture.completeExceptionally(t);
        }
    }

    public synchronized void clear() {
        hWalletTokenFuture = new CompletableFuture<>();
        txId = null;
        serverToken = null;
        personalizeDone = false;
    }
}
