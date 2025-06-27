/*
 * Copyright 2024 OmniOne.
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

package org.omnione.did.ca.network.protocol.vp;

import android.content.Context;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.config.Preference;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.HttpUrlConnection;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.util.TokenUtil;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.datamodel.profile.ProofRequestProfile;
import org.omnione.did.sdk.datamodel.protocol.P311RequestVo;
import org.omnione.did.sdk.datamodel.protocol.P311ResponseVo;
import org.omnione.did.sdk.datamodel.util.GsonWrapper;
import org.omnione.did.sdk.datamodel.vc.issue.ReturnEncVP;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.token.WalletTokenSeed;
import org.omnione.did.sdk.datamodel.vc.VerifiableCredential;
import org.omnione.did.sdk.datamodel.zkp.CredentialDefinition;
import org.omnione.did.sdk.datamodel.zkp.CredentialDefinitionVo;
import org.omnione.did.sdk.datamodel.zkp.CredentialSchema;
import org.omnione.did.sdk.datamodel.zkp.CredentialSchemaVo;
import org.omnione.did.sdk.datamodel.zkp.ProofParam;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.utility.MultibaseUtils;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class VerifyProof {
    private static VerifyProof instance;
    private Context context;
    private String txId;
    public String hWalletToken;
    private String proofRequestProfileVo;
    public VerifyProof(){}
    public VerifyProof(Context context){
        this.context = context;
    }
    public static VerifyProof getInstance(Context context) {
        if(instance == null) {
            instance = new VerifyProof(context);
        }
        return instance;
    }

    public CompletableFuture<String> verifyProofPreProcess(String offerId, final String txId) {
        String api1 = "/verifier/api/v1/request-proof-request-profile";
        String api_cas1 = "/cas/api/v1/request-wallet-tokendata";

        HttpUrlConnection httpUrlConnection = new HttpUrlConnection();

        return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context, Config.VERIFIER_URL + api1, "POST", M311_RequestProofProfile(offerId, txId)))
                .thenCompose(_M310_RequestProfile -> {
                    this.txId = MessageUtil.deserialize(_M310_RequestProfile, P311ResponseVo.class).getTxId();
                    proofRequestProfileVo = _M310_RequestProfile;
                    return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context, Config.CAS_URL + api_cas1, "POST", M000_GetWalletTokenData()));
                })
                .thenApply(_M000_GetWalletTokenData -> {
                    try {
                        hWalletToken = TokenUtil.createHashWalletToken(_M000_GetWalletTokenData, context);
                    } catch (WalletException | WalletCoreException | UtilityException |
                             ExecutionException | InterruptedException e) {
                        ContextCompat.getMainExecutor(context).execute(()  -> {
                            CaUtil.showErrorDialog(context, e.getMessage());
                        });
                    }
                    return proofRequestProfileVo;
                })
                .exceptionally(ex -> {
                    throw new CompletionException(ex);
                });

    }


    public String verifyProofProcess(List<ProofParam> proofParams, Map<String, String> selfAttr) {
        ExecutorService es = Executors.newCachedThreadPool();
        Future<String> future = es.submit(() -> {
            String api1 = "/verifier/api/v1/request-verify-proof";
            P311ResponseVo profile = MessageUtil.deserialize(proofRequestProfileVo, P311ResponseVo.class);
            String requestProof = M311_RequestVerify(profile.getProofRequestProfile(), proofParams, selfAttr);
            String result = new HttpUrlConnection().send(context, Config.VERIFIER_URL + api1, "POST", requestProof);
            CaLog.d("verifyProofProcess >>>>>>>>>> " + result);
            return result;
        });

        try {
            return future.get();

        } catch (ExecutionException | InterruptedException e) {
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        return null;
    }


    private String M311_RequestProofProfile(String offerId, String txId){
        P311RequestVo requestVo = new P311RequestVo(CaUtil.createMessageId(context), txId);
        requestVo.setOfferId(offerId);
        return requestVo.toJson();
    }

    private String M311_RequestVerify(ProofRequestProfile vpProfile, List<ProofParam> proofParams, Map<String, String> selfAttr){
        final String[] result = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            try {
                P311RequestVo requestVo = WalletApi.getInstance(context).createZkpProof(hWalletToken, vpProfile, proofParams, selfAttr, txId);
                requestVo.setId(CaUtil.createMessageId(context));
                CaLog.d("P311RequestVo requestVo.toJson(): "+GsonWrapper.getGson().toJson(requestVo.toJson()));
                result[0] = requestVo.toJson();
                CaLog.d("holder[0]: "+GsonWrapper.getGson().toJson(result[0]));
            } catch (WalletException | UtilityException | WalletCoreException e){
                CaLog.e(" vp error : " + e.getMessage());
                ContextCompat.getMainExecutor(context).execute(()  -> {
                    CaUtil.showErrorDialog(context, e.getMessage());
                });
                } finally {
                    latch.countDown();
                }
            }).start();

            try {
                latch.await();
            } catch (InterruptedException e) {
                ContextCompat.getMainExecutor(context).execute(()  -> {
                    CaUtil.showErrorDialog(context, e.getMessage());
                });
            }
        return result[0];
    }

    private String createWalletTokenSeed(WalletTokenPurpose.WALLET_TOKEN_PURPOSE purpose) {
        WalletTokenSeed walletTokenSeed = new WalletTokenSeed();
        try {
            WalletApi walletApi = WalletApi.getInstance(context);
            walletTokenSeed = walletApi.createWalletTokenSeed(purpose, CaUtil.getPackageName(context), Preference.getUserIdForDemo(context));
        } catch (WalletException | UtilityException | WalletCoreException e) {
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        return walletTokenSeed.toJson();
    }

    private String M000_GetWalletTokenData(){
        return createWalletTokenSeed(WalletTokenPurpose.WALLET_TOKEN_PURPOSE.LIST_VC_AND_PRESENT_VP);
    }

    public CredentialDefinition getCredentialDefinition(final String credDefId) {
        ExecutorService es = Executors.newCachedThreadPool();
        Future<CredentialDefinition> future = es.submit(() -> {
            try {
                String credDef = new HttpUrlConnection().send(context, Config.API_GATEWAY_URL + "/api-gateway/api/v1/zkp-cred-def?id="+credDefId, "GET","");
                CaLog.d("getCredDef >>>>>>>>>> " + credDef);
                CredentialDefinitionVo credentialDefinitionVo = MessageUtil.deserialize(credDef, CredentialDefinitionVo.class);
                CredentialDefinition credentialDefinition = MessageUtil.deserialize(new String(MultibaseUtils.decode(credentialDefinitionVo.getCredDef())), CredentialDefinition.class);
                CaLog.d("credentialDefinition: "+GsonWrapper.getGson().toJson(credentialDefinition));
                return credentialDefinition;
            } catch (UtilityException e) {
                ContextCompat.getMainExecutor(context).execute(()  -> {
                    CaUtil.showErrorDialog(context, e.getMessage());
                });
            }
            return null;
        });

        try {
            return future.get();

        } catch (ExecutionException | InterruptedException e) {
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        return null;
    }

    public CredentialSchema getCredentialSchema(final String schemaId) {
        ExecutorService es = Executors.newCachedThreadPool();
        Future<CredentialSchema> future = es.submit(() -> {
            try {
                String schema = new HttpUrlConnection().send(context, Config.API_GATEWAY_URL + "/api-gateway/api/v1/zkp-cred-schema?id=" + schemaId, "GET", "");
                CredentialSchemaVo credentialSchemaVo = MessageUtil.deserialize(schema, CredentialSchemaVo.class);
                CredentialSchema credentialSchema = MessageUtil.deserialize(new String(MultibaseUtils.decode(credentialSchemaVo.getCredSchema())), CredentialSchema.class);
                CaLog.d("credentialSchema: " + GsonWrapper.getGson().toJson(credentialSchema));
                return credentialSchema;
            } catch (UtilityException e) {
                ContextCompat.getMainExecutor(context).execute(() -> {
                    CaUtil.showErrorDialog(context, e.getMessage());
                });
            }
            return null;
        });

        try {
            return future.get();

        } catch (ExecutionException | InterruptedException e) {
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        return null;
    }
}
