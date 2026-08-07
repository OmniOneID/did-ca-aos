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

package org.omnione.did.ca.data.sdk;

import android.content.Context;

import androidx.annotation.Nullable;

import org.omnione.did.ca.data.config.AppConfig;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.bioprompthelper.BioPromptHelper;
import org.omnione.did.sdk.core.vcmanager.datamodel.ClaimInfo;
import org.omnione.did.sdk.datamodel.common.ProofContainer;
import org.omnione.did.sdk.datamodel.common.enums.VerifyAuthType;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.did.DIDDocument;
import org.omnione.did.sdk.datamodel.did.SignedDidDoc;
import org.omnione.did.sdk.datamodel.oid4vc.SdJwtCredentialItem;
import org.omnione.did.sdk.datamodel.profile.IssueProfile;
import org.omnione.did.sdk.datamodel.profile.ProofRequestProfile;
import org.omnione.did.sdk.datamodel.profile.VerifyProfile;
import org.omnione.did.sdk.datamodel.protocol.P311RequestVo;
import org.omnione.did.sdk.datamodel.security.DIDAuth;
import org.omnione.did.sdk.datamodel.token.SignedWalletInfo;
import org.omnione.did.sdk.datamodel.token.WalletTokenSeed;
import org.omnione.did.sdk.datamodel.vc.VerifiableCredential;
import org.omnione.did.sdk.datamodel.vc.issue.ReturnEncVP;
import org.omnione.did.sdk.datamodel.zkp.AvailableReferent;
import org.omnione.did.sdk.datamodel.zkp.Credential;
import org.omnione.did.sdk.datamodel.zkp.ProofParam;
import org.omnione.did.sdk.datamodel.zkp.ProofRequest;
import org.omnione.did.sdk.datamodel.zkp.ReferentInfo;
import org.omnione.did.sdk.datamodel.zkp.UserReferent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class WalletGateway {

    private final WalletApi walletApi;
    private final AppConfig config;
    private final Executor executor;

    @Inject
    public WalletGateway(WalletApi walletApi, AppConfig config) {
        this.walletApi = walletApi;
        this.config = config;
        this.executor = Executors.newCachedThreadPool();
    }

    private <T> CompletableFuture<T> async(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<Boolean> isExistWallet() {
        return CompletableFuture.supplyAsync(walletApi::isExistWallet, executor);
    }

    public CompletableFuture<Boolean> isSavedKey(String keyId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return walletApi.isSavedKey(keyId);
            } catch (Exception e) {
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> createWallet() {
        return async(() -> {
            boolean created = walletApi.createWallet(config.walletUrl(), config.tasUrl());
            walletApi.isLock();
            return created;
        });
    }

    public CompletableFuture<Boolean> isLocked() {
        return CompletableFuture.supplyAsync(walletApi::isLock, executor);
    }

    public CompletableFuture<Void> deleteWallet() {
        return async(() -> {
            walletApi.deleteWallet(true);
            return null;
        });
    }

    public CompletableFuture<Void> registerLock(String walletToken, String pin, boolean isLock) {
        return async(() -> {
            walletApi.registerLock(walletToken, pin, isLock);
            return null;
        });
    }

    public CompletableFuture<Void> authenticateLock(String pin) {
        return async(() -> {
            walletApi.authenticateLock(pin);
            return null;
        });
    }

    public CompletableFuture<Void> authenticatePin(String keyId, String pin) {
        return async(() -> {
            walletApi.authenticatePin(keyId, pin.getBytes());
            return null;
        });
    }

    public CompletableFuture<Void> changeLock(String oldPin, String newPin) {
        return async(() -> {
            walletApi.changeLock(oldPin, newPin);
            return null;
        });
    }

    public CompletableFuture<Void> changePin(String keyId, String oldPin, String newPin) {
        return async(() -> {
            walletApi.changePin(keyId, oldPin, newPin);
            return null;
        });
    }

    public void setBioPromptListener(BioPromptHelper.BioPromptInterface listener) {
        walletApi.setBioPromptListener(listener);
    }

    public void registerBioKey(Context ctx) {
        try {
            walletApi.registerBioKey(ctx);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void authenticateBioKey(Context ctx) {
        try {
            walletApi.authenticateBioKey(ctx);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public CompletableFuture<WalletTokenSeed> createWalletTokenSeed(
            WalletTokenPurpose.WALLET_TOKEN_PURPOSE purpose, String pkgName, String userId) {
        return async(() -> walletApi.createWalletTokenSeed(purpose, pkgName, userId));
    }

    public CompletableFuture<Boolean> bindUser(String hWalletToken) {
        return async(() -> walletApi.bindUser(hWalletToken));
    }

    public CompletableFuture<Void> generateKeyPair(String hWalletToken, String pin) {
        return async(() -> {
            walletApi.generateKeyPair(hWalletToken, pin);
            return null;
        });
    }

    public CompletableFuture<SignedWalletInfo> getSignedWalletInfo() {
        return async(() -> walletApi.getSignedWalletInfo());
    }

    public CompletableFuture<DIDDocument> getDIDDocument(int didType) {
        return async(() -> walletApi.getDIDDocument(didType));
    }

    public CompletableFuture<DIDDocument> createHolderDIDDoc(String walletToken) {
        return async(() -> walletApi.createHolderDIDDoc(walletToken));
    }

    public CompletableFuture<DIDDocument> updateHolderDIDDoc(String walletToken) {
        return async(() -> walletApi.updateHolderDIDDoc(walletToken));
    }

    public CompletableFuture<ProofContainer> addProofsToDocument(ProofContainer document,
                                                                 List<String> keyIds,
                                                                 String idOrWalletToken,
                                                                 int didType,
                                                                 String pin,
                                                                 boolean isLock) {
        return async(() -> walletApi.addProofsToDocument(document, keyIds, idOrWalletToken, didType, pin, isLock));
    }

    public CompletableFuture<SignedDidDoc> createSignedDIDDoc(DIDDocument document) {
        return async(() -> walletApi.createSignedDIDDoc(document));
    }

    public CompletableFuture<Void> saveDocument() {
        return async(() -> {
            walletApi.saveDocument();
            return null;
        });
    }

    public CompletableFuture<Void> deleteKey(String walletToken, List<String> keyIds) {
        return async(() -> {
            walletApi.deleteKey(walletToken, keyIds);
            return null;
        });
    }

    public CompletableFuture<String> requestRegisterUser(String hWalletToken,
                                                         String tasBaseUrl,
                                                         String txId,
                                                         String serverToken,
                                                         SignedDidDoc signedDidDoc) {
        return async(() -> walletApi.requestRegisterUser(
                hWalletToken, tasBaseUrl, txId, serverToken, signedDidDoc).get());
    }

    public CompletableFuture<String> requestUpdateUser(String hWalletToken,
                                                       String tasBaseUrl,
                                                       String serverToken,
                                                       org.omnione.did.sdk.datamodel.security.DIDAuth didAuth,
                                                       SignedDidDoc signedDidDoc,
                                                       String txId) {
        return async(() -> walletApi.requestUpdateUser(
                hWalletToken, tasBaseUrl, serverToken, didAuth, signedDidDoc, txId).get());
    }

    public CompletableFuture<List<VerifiableCredential>> getAllCredentials(String walletToken) {
        return async(() -> {
            List<VerifiableCredential> result = walletApi.getAllCredentials(walletToken);
            return result == null ? java.util.Collections.<VerifiableCredential>emptyList() : result;
        });
    }

    public CompletableFuture<VerifiableCredential> getCredential(String walletToken, String vcId) {
        return async(() -> {
            List<VerifiableCredential> list = walletApi.getCredentials(walletToken, java.util.List.of(vcId));
            if (list == null || list.isEmpty()) {
                throw new IllegalStateException("vc not found: " + vcId);
            }
            return list.get(0);
        });
    }

    public CompletableFuture<Void> deleteCredentials(String hWalletToken, String vcId) {
        return async(() -> {
            walletApi.deleteCredentials(hWalletToken, vcId);
            return null;
        });
    }

    public CompletableFuture<Boolean> isAnyZkpCredentialsSaved() {
        return CompletableFuture.supplyAsync(() -> {
            try { return walletApi.isAnyZkpCredentialsSaved(); }
            catch (Exception e) { return false; }
        }, executor);
    }

    public CompletableFuture<Boolean> isZkpCredentialsSaved(String vcId) {
        return CompletableFuture.supplyAsync(() -> {
            try { return walletApi.isZkpCredentialsSaved(vcId); }
            catch (Exception e) { return false; }
        }, executor);
    }

    public CompletableFuture<Credential> getZkpCredential(String walletToken, String vcId) {
        return async(() -> {
            List<Credential> list = walletApi.getZkpCredentials(walletToken, java.util.List.of(vcId));
            if (list == null || list.isEmpty()) {
                throw new IllegalStateException("zkp credential not found: " + vcId);
            }
            return list.get(0);
        });
    }

    public CompletableFuture<List<SdJwtCredentialItem>> getAllOID4VCs(String walletToken) {
        return async(() -> {
            List<SdJwtCredentialItem> result = walletApi.getAllOID4VCs(walletToken);
            return result == null ? java.util.Collections.<SdJwtCredentialItem>emptyList() : result;
        });
    }

    public CompletableFuture<List<SdJwtCredentialItem>> getOID4VCs(String walletToken, List<String> ids) {
        return async(() -> {
            List<SdJwtCredentialItem> result = walletApi.getOID4VCs(walletToken, ids);
            return result == null ? java.util.Collections.<SdJwtCredentialItem>emptyList() : result;
        });
    }

    public CompletableFuture<Void> deleteOID4VCs(String hWalletToken, List<String> ids) {
        return async(() -> {
            walletApi.deleteOID4VCs(hWalletToken, ids);
            return null;
        });
    }

    public CompletableFuture<DIDAuth> getSignedDIDAuth(String authNonce, @Nullable String pin) {
        return async(() -> walletApi.getSignedDIDAuth(authNonce, pin));
    }

    public CompletableFuture<String> requestIssueVc(String hWalletToken,
                                                    String tasBaseUrl,
                                                    String apiGwBaseUrl,
                                                    String serverToken,
                                                    String refId,
                                                    IssueProfile profile,
                                                    DIDAuth signedDIDAuth,
                                                    String txId) {
        return async(() -> walletApi.requestIssueVc(hWalletToken, tasBaseUrl, apiGwBaseUrl,
                serverToken, refId, profile, signedDIDAuth, txId).get());
    }

    public CompletableFuture<String> requestRevokeVc(String hWalletToken,
                                                     String baseUrl,
                                                     @Nullable String hServerToken,
                                                     String txId,
                                                     String vcId,
                                                     String issuerNonce,
                                                     String pin,
                                                     VerifyAuthType.VERIFY_AUTH_TYPE authType) {
        return async(() -> walletApi.requestRevokeVc(hWalletToken, baseUrl, hServerToken,
                txId, vcId, issuerNonce, pin, authType).get());
    }

    public CompletableFuture<ReturnEncVP> createEncVp(String hWalletToken,
                                                      List<ClaimInfo> claimInfos,
                                                      VerifyProfile verifyProfile,
                                                      String apiGatewayUrl,
                                                      String passcode) {
        return async(() -> walletApi.createEncVp(hWalletToken, claimInfos, verifyProfile, apiGatewayUrl, passcode));
    }

    public CompletableFuture<AvailableReferent> searchZkpCredentials(String hWalletToken,
                                                                     ProofRequest proofRequest) {
        return async(() -> walletApi.searchZkpCredentials(hWalletToken, proofRequest));
    }

    public CompletableFuture<ReferentInfo> createZkpReferent(List<UserReferent> userReferents) {
        return async(() -> walletApi.createZkpReferent(userReferents));
    }

    public CompletableFuture<P311RequestVo> createEncZkpProof(String hWalletToken,
                                                              List<ProofParam> proofParams,
                                                              Map<String, String> selfAttributes,
                                                              ProofRequestProfile profile,
                                                              String txId,
                                                              String apiGatewayUrl) {
        return async(() -> walletApi.createEncZkpProof(hWalletToken, proofParams, selfAttributes, profile, txId, apiGatewayUrl));
    }
}
