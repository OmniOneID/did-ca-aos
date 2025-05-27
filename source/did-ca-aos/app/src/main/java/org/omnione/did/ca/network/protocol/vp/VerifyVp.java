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
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;

import org.omnione.did.ca.R;
import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.config.Preference;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.HttpUrlConnection;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.util.TokenUtil;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.datamodel.offer.VerifyOfferPayload;
import org.omnione.did.sdk.datamodel.protocol.P310ResponseVo;
import org.omnione.did.sdk.datamodel.protocol.P310ZkpResponseVo;
import org.omnione.did.sdk.datamodel.util.GsonWrapper;
import org.omnione.did.sdk.datamodel.vc.issue.ReturnEncVP;
import org.omnione.did.sdk.datamodel.profile.VerifyProfile;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.datamodel.protocol.P310RequestVo;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.token.WalletTokenSeed;
import org.omnione.did.sdk.datamodel.vc.VerifiableCredential;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.core.bioprompthelper.BioPromptHelper;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

public class VerifyVp {
    private static VerifyVp instance;
    private Context context;
    private String txId;
    private String hWalletToken;
    private String verifyProfile;
    public VerifyVp(){}
    public VerifyVp(Context context){
        this.context = context;
    }
    public static VerifyVp getInstance(Context context) {
        if(instance == null) {
            instance = new VerifyVp(context);
        }
        return instance;
    }

    public CompletableFuture<String> verifyVpPreProcess(String offerId, final String txId, String type) {
        String api1;

        if (type.equals(VerifyOfferPayload.OFFER_TYPE.VerifyProofOffer.toString())) {
            api1 = "/verifier/api/v1/request-proof-request-profile";
        } else {
            api1 = "/verifier/api/v1/request-profile";
        }

        String api_cas1 = "/cas/api/v1/request-wallet-tokendata";

        HttpUrlConnection httpUrlConnection = new HttpUrlConnection();

        return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context, Config.VERIFIER_URL + api1, "POST", M310_RequestProfile(offerId, txId)))
                .thenCompose(_M310_RequestProfile -> {
                    this.txId = MessageUtil.deserialize(_M310_RequestProfile, P310ZkpResponseVo.class).getTxId();
                    verifyProfile = _M310_RequestProfile;
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
                    return verifyProfile;
                })
                .exceptionally(ex -> {
                    throw new CompletionException(ex);
                });

    }
    public CompletableFuture<String> verifyVpProcess(String pin) {
        String api1 = "/verifier/api/v1/request-verify";

        HttpUrlConnection httpUrlConnection = new HttpUrlConnection();
        P310ResponseVo profile = MessageUtil.deserialize(verifyProfile, P310ResponseVo.class);
        VerifyProfile vpProfile2 = profile.getProfile();
        String requestVp = M310_RequestVerify(vpProfile2, pin);
        return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context, Config.VERIFIER_URL + api1, "POST", requestVp))
                .thenCompose(CompletableFuture::completedFuture)
                .exceptionally(ex -> {
                    throw new CompletionException(ex);
                });
    }

    private String M310_RequestProfile(String offerId, String txId){
        P310RequestVo requestVo = new P310RequestVo(CaUtil.createMessageId(context), txId);
        requestVo.setOfferId(offerId);
        String request = requestVo.toJson();
        return request;
    }

    private String M310_RequestVerify(VerifyProfile vpProfile, String pin){
        final String[] resultHolder = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);

        new Thread(new Runnable() {
            @Override
            public void run() {

            try {
                WalletApi walletApi = WalletApi.getInstance(context);
                String vcId = "";
                List<VerifiableCredential> vcList = walletApi.getAllCredentials(hWalletToken);
                for(VerifiableCredential vc : vcList){
                    if(vpProfile.getProfile().filter.getCredentialSchemas().get(0).getId().equals(vc.getCredentialSchema().getId())){
                        vcId = vc.getId();
                        CaLog.d("submit to VC ID : " + vcId);
                    }
                }
                List<String> claimCode = vpProfile.getProfile().filter.getCredentialSchemas().get(0).requiredClaims;
                CaLog.d("submit to VC ID : " + GsonWrapper.getGson().toJson(claimCode));
                String nonce = vpProfile.getProfile().process.verifierNonce;
                ReturnEncVP returnEncVP = walletApi.createEncVp(hWalletToken, vcId, claimCode, vpProfile.getProfile().process.reqE2e, pin, nonce, vpProfile.getProfile().process.authType);
                CaLog.d("returnEncVP: "+GsonWrapper.getGson().toJson(returnEncVP));
                P310RequestVo requestVo = new P310RequestVo(CaUtil.createMessageId(context));
                requestVo.setTxId(txId);
                requestVo.setEncVp(returnEncVP.getEncVp());
                requestVo.setAccE2e(returnEncVP.getAccE2e());
                String request = requestVo.toJson();
                resultHolder[0] = request;
                CaLog.d("holder[0]: "+GsonWrapper.getGson().toJson(resultHolder[0]));
            } catch (WalletException | UtilityException | WalletCoreException e){
                CaLog.e(" vp error : " + e.getMessage());
                ContextCompat.getMainExecutor(context).execute(()  -> {
                    CaUtil.showErrorDialog(context, e.getMessage());
                });
                } finally {
                    latch.countDown();
                }
            }
            }).start();

            try {
                latch.await();
            } catch (InterruptedException e) {
                ContextCompat.getMainExecutor(context).execute(()  -> {
                    CaUtil.showErrorDialog(context, e.getMessage());
                });
            }
        return resultHolder[0];
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
        String request = createWalletTokenSeed(WalletTokenPurpose.WALLET_TOKEN_PURPOSE.LIST_VC_AND_PRESENT_VP);
        return request;
    }

    public boolean isBioKey() throws WalletCoreException, UtilityException, WalletException {
        WalletApi walletApi = WalletApi.getInstance(context);
        return walletApi.isSavedKey(Constants.KEY_ID_BIO);
    }

    public void authenticateBio(Fragment fragment, NavController navController) {
        try {
            WalletApi walletApi = WalletApi.getInstance(context);
            walletApi.setBioPromptListener(new BioPromptHelper.BioPromptInterface() {
                @Override
                public void onSuccess(String result) {
                    try {
                        verifyVpProcess("").get();
                    } catch (ExecutionException | InterruptedException e) {
                        CaLog.e("bio authentication fail" + e.getMessage());
                        ContextCompat.getMainExecutor(context).execute(()  -> {
                            CaUtil.showErrorDialog(context, e.getMessage());
                        });
                    }
                    Bundle bundle = new Bundle();
                    bundle.putString("type", Constants.TYPE_VERIFY);
                    navController.navigate(R.id.action_selectAuthTypetFragment_to_resultFragment, bundle);
                }
                @Override
                public void onError(String result) {
                    ContextCompat.getMainExecutor(context).execute(()  -> {
                        CaUtil.showErrorDialog(context,"[Error] Authentication failed.\nPlease try again later.");
                    });
                }
                @Override
                public void onCancel(String result) {
                    ContextCompat.getMainExecutor(context).execute(()  -> {
                        CaUtil.showErrorDialog(context,"[Information] canceled by user");
                    });
                }
                @Override
                public void onFail(String result) {
                    CaLog.e("bio onFail");
                }
            });
            walletApi.authenticateBioKey(fragment, context);
        } catch (WalletException | WalletCoreException e) {
            CaLog.e("bio authentication fail");
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
    }
}
