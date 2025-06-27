/*
 * Copyright 2024-2025 OmniOne.
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

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import androidx.core.content.ContextCompat;

import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.config.Preference;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.HttpUrlConnection;
import org.omnione.did.ca.push.UpdatePushTokenVo;
import org.omnione.did.ca.ui.common.ErrorDialog;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.util.GsonWrapper;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.datamodel.vc.issue.VcMeta;
import org.omnione.did.sdk.datamodel.vc.issue.VcStatus;
import org.omnione.did.sdk.datamodel.vc.issue.VcStatusVo;
import org.omnione.did.sdk.datamodel.zkp.AttributeDef;
import org.omnione.did.sdk.datamodel.zkp.AttributeInfo;
import org.omnione.did.sdk.datamodel.zkp.AttributeType;
import org.omnione.did.sdk.datamodel.zkp.AttributeValue;
import org.omnione.did.sdk.datamodel.zkp.CredentialDefinition;
import org.omnione.did.sdk.datamodel.zkp.CredentialDefinitionVo;
import org.omnione.did.sdk.datamodel.zkp.CredentialSchema;
import org.omnione.did.sdk.datamodel.zkp.CredentialSchemaVo;
import org.omnione.did.sdk.datamodel.zkp.PredicateInfo;
import org.omnione.did.sdk.utility.CryptoUtils;
import org.omnione.did.sdk.utility.Encodings.Base16;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.utility.MultibaseUtils;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

import java.io.ByteArrayOutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
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

public class CaUtil {
    public static Bitmap drawableFromImgStr(Context context) {
        byte[] bytes=Base64.decode(Preference.loadPicture(context),Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes,0,bytes.length);
    }

    public static String drawableToBase64(Context context, int resId){
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resId);
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        byte[] byteArray = byteStream.toByteArray();
        return "data:image/png;base64," + Base64.encodeToString(byteArray,Base64.DEFAULT);
    }

    public static String getPackageName(Context context) {
        return context.getPackageName();
    }

    public static String convertDate(String targetDate){
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Date date = null;
        try {
            date = format.parse(targetDate);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        return format.format(date);
    }

    public static String createMessageId(Context context) {
        Date today = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmssSSSSSS");
        String messageId = "";
        try {
            messageId = dateFormat.format(today) + Base16.toHex(CryptoUtils.generateNonce(4));
        } catch (UtilityException e) {
            CaLog.e("createMessageId error : " + e.getMessage());
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        return messageId;
    }
    public static String createCaAppId() throws UtilityException{
        Date today = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMM");
        return "AID" + dateFormat.format(today) + "a" + Base16.toHex(CryptoUtils.generateNonce(5));
    }
    public static boolean isLock(Context context) {
        final boolean[] resultHolder = new boolean[1];
        final CountDownLatch latch = new CountDownLatch(1);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WalletApi walletApi = WalletApi.getInstance(context);
                    boolean isLock = walletApi.isLock();
                    CaLog.d("wallet lock type : " + isLock);
                    resultHolder[0] = isLock;
                } catch (WalletCoreException e) {
                    CaLog.e("personalize error : " + e.getMessage());
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
    public static boolean personalize(String hWalletToken, Context context, WalletTokenPurpose.WALLET_TOKEN_PURPOSE purpose) throws WalletCoreException {
        WalletApi walletApi = WalletApi.getInstance(context);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    CaLog.d("user bind: " + walletApi.bindUser(hWalletToken));
                } catch (WalletException e) {
                    CaLog.e("personalize error : " + e.getMessage());
                    ContextCompat.getMainExecutor(context).execute(()  -> {
                        CaUtil.showErrorDialog(context, e.getMessage());
                    });
                }
            }
        }).start();
        return true;
    }
    public static void showErrorDialog(Context context, String message){
        ErrorDialog errorDialog = new ErrorDialog(context);
        errorDialog.setMessage(message);
        errorDialog.setDialogListener(new ErrorDialog.ErrorDialogInterface() {
            @Override
            public void okBtnClicked(String btnName) {
            }
        });
        errorDialog.show();
    }

    public static void showErrorDialog(Context context, String message, Activity activity){
        ErrorDialog errorDialog = new ErrorDialog(context);
        errorDialog.setMessage(message);
        errorDialog.setDialogListener(new ErrorDialog.ErrorDialogInterface() {
            @Override
            public void okBtnClicked(String btnName) {
                activity.finish();
            }
        });
        errorDialog.show();
    }

    public static CompletableFuture<String> getVcSchema(Context context, String schemaId){
        HttpUrlConnection httpUrlConnection = new HttpUrlConnection();

        return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context, schemaId, "GET", ""))
                .thenCompose(CompletableFuture::completedFuture)
                .exceptionally(ex -> {
                    throw new CompletionException(ex);
                });
    }

    public static CompletableFuture<String> updatePushToken(Context context){
        String api = "/tas/api/v1/update-push-token";

        UpdatePushTokenVo updatePushTokenVo = new UpdatePushTokenVo();
        updatePushTokenVo.setId(CaUtil.createMessageId(context));
        updatePushTokenVo.setDid(Preference.getDID(context));
        updatePushTokenVo.setAppId(Preference.getCaAppId(context));
        CaLog.d("push token : " + Preference.getPushToken(context));
        updatePushTokenVo.setPushToken(Preference.getPushToken(context));
        String updatePushToken = updatePushTokenVo.toJson();
        CaLog.d("Update push token : " + updatePushToken);
        HttpUrlConnection httpUrlConnection = new HttpUrlConnection();

        return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(context, Config.TAS_URL + api, "POST", updatePushToken))
                .thenCompose(CompletableFuture::completedFuture)
                .exceptionally(ex -> {
                    throw new CompletionException(ex);
                });
    }

    public static String getVcMeta(Context context, final String vcId) {
        ExecutorService es = Executors.newCachedThreadPool();
        Future<String> future = es.submit(new Callable<String>() {
            @Override
            public String call() {
                try {
                    String vcStatusJson = new HttpUrlConnection().send(context, Config.API_GATEWAY_URL+"/api-gateway/api/v1/vc-meta?vcId="+vcId, "GET","");
                    CaLog.d("vcStatusJson >>>>>>>>>> " + vcStatusJson);

                    VcStatusVo vcStatusVo = MessageUtil.deserialize(vcStatusJson, VcStatusVo.class);
                    VcMeta vcMeta = MessageUtil.deserialize(new String(MultibaseUtils.decode(vcStatusVo.getVcMeta())), VcMeta.class);

                    CaLog.d("vcStatus >>>>>>>>>> " + vcMeta.getStatus());
                    return vcMeta.getStatus();
                } catch (UtilityException e) {
                    ContextCompat.getMainExecutor(context).execute(()  -> {
                        CaUtil.showErrorDialog(context, e.getMessage());
                    });
                }
                return null;
            }
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

    public static CredentialSchema getCredentialSchema(Context context, final String schemaId) {
        ExecutorService es = Executors.newCachedThreadPool();
        Future<CredentialSchema> future = es.submit(new Callable<CredentialSchema>() {
            @Override
            public CredentialSchema call() {
                try {
                    String schema = new HttpUrlConnection().send(context, Config.API_GATEWAY_URL + "/api-gateway/api/v1/zkp-cred-schema?id="+schemaId, "GET","");
                    CaLog.d("getSchema >>>>>>>>>> " + schema);
                    CredentialSchemaVo credentialSchemaVo = MessageUtil.deserialize(schema, CredentialSchemaVo.class);
                    CredentialSchema credentialSchema = MessageUtil.deserialize(new String(MultibaseUtils.decode(credentialSchemaVo.getCredSchema())), CredentialSchema.class);
                    CaLog.d("credentialSchema: "+ GsonWrapper.getGson().toJson(credentialSchema));
                    return credentialSchema;
                } catch (UtilityException e) {
                    ContextCompat.getMainExecutor(context).execute(()  -> {
                        CaUtil.showErrorDialog(context, e.getMessage());
                    });
                }
                return null;
            }
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


    public static CredentialDefinition getCredentialDefinition(Context context, final String credDefId) {
        ExecutorService es = Executors.newCachedThreadPool();
        Future<CredentialDefinition> future = es.submit(new Callable<CredentialDefinition>() {
            @Override
            public CredentialDefinition call() {
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
            }
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

    public static String findAttributeNameByCredDefId(Map<String, AttributeInfo> requestedAttributes) {
        for (Map.Entry<String, AttributeInfo> entry : requestedAttributes.entrySet()) {
            AttributeInfo attributeInfo = entry.getValue();
            List<Map<String, String>> restrictions = attributeInfo.getRestrictions();
            for (Map<String, String> restriction : restrictions) {
                return restriction.get("credDefId");
            }
        }
        return null;
    }

    public static String findPredicateNameByCredDefId(Map<String, PredicateInfo> requestedPredicates) {
        for (Map.Entry<String, PredicateInfo> entry : requestedPredicates.entrySet()) {
            PredicateInfo predicateInfo = entry.getValue();
            List<Map<String, String>> restrictions = predicateInfo.getRestrictions();
            for (Map<String, String> restriction : restrictions) {
                return restriction.get("credDefId");
            }
        }
        return null;
    }

    public static String extractSchemaName(String input) {
        CaLog.d("extractSchemaName: "+input);
        String[] parts = input.split(":");

        if (parts.length >= 3) {
            return parts[parts.length - 3];

        } else {
            return "NONE";
        }
    }

    public static String getAttributeCaptionValue(Context context, final String schemaId, String attrName) {
        ExecutorService es = Executors.newCachedThreadPool();
        Future<String> future = es.submit(new Callable<String>() {
            @Override
            public String call() {
                try {
                    String schema = new HttpUrlConnection().send(context, Config.API_GATEWAY_URL + "/api-gateway/api/v1/zkp-cred-schema?id="+schemaId, "GET","");
                    CaLog.d("getSchema >>>>>>>>>> " + schema);

                    CredentialSchemaVo credentialSchemaVo = MessageUtil.deserialize(schema, CredentialSchemaVo.class);
                    CredentialSchema credentialSchema = MessageUtil.deserialize(new String(MultibaseUtils.decode(credentialSchemaVo.getCredSchema())), CredentialSchema.class);
                    CaLog.d("credentialSchema: "+ GsonWrapper.getGson().toJson(credentialSchema));

                    for (AttributeType type: credentialSchema.getAttrTypes()) {
                        String namespace = type.getNamespace().getId();

                            CaLog.d("attrName: "+attrName);
                            if (attrName.startsWith(namespace) && attrName.length() > namespace.length()) {
                                String label = attrName.substring(namespace.length() + 1); // +1은 '.' 문자 제거용
                                String nmId = type.getNamespace().getId();
                                CaLog.d("label: "+label);
                                CaLog.d("nmId: "+nmId);
                                if (nmId.equals(namespace)) {
                                    for (AttributeDef attrDef : type.getItems()) {
                                        if (attrDef.getLabel().equals(label)) {
                                            return attrDef.getCaption();
                                        }
                                    }
                                }
                            }

                    }

                } catch (UtilityException e) {
                    ContextCompat.getMainExecutor(context).execute(()  -> {
                        CaUtil.showErrorDialog(context, e.getMessage());
                    });
                }
                return null;
            }
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
