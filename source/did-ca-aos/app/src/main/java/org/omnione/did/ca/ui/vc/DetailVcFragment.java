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

package org.omnione.did.ca.ui.vc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import org.omnione.did.ca.network.HttpUrlConnection;
import org.omnione.did.ca.ui.PinActivity;
import org.omnione.did.ca.R;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.config.Preference;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.protocol.token.GetWalletToken;
import org.omnione.did.ca.network.protocol.vc.RevokeVc;
import org.omnione.did.ca.ui.common.CustomDialog;
import org.omnione.did.ca.util.CaUtil;

import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.datamodel.common.enums.VerifyAuthType;
import org.omnione.did.sdk.datamodel.util.GsonWrapper;
import org.omnione.did.sdk.datamodel.vc.Claim;
import org.omnione.did.sdk.datamodel.vc.VerifiableCredential;
import org.omnione.did.sdk.datamodel.zkp.AttributeDef;

import org.omnione.did.sdk.datamodel.zkp.AttributeType;
import org.omnione.did.sdk.datamodel.zkp.AttributeValue;
import org.omnione.did.sdk.datamodel.zkp.Credential;
import org.omnione.did.sdk.datamodel.zkp.CredentialSchema;

import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class DetailVcFragment extends Fragment {
    NavController navController;
    Activity activity;
    VerifiableCredential vc;

    Credential credential;
    String vcId;
    ImageView imageView;
    String hWalletToken = "";
    GetWalletToken getWalletToken;

    HttpUrlConnection httpClient;
    ActivityResultLauncher<Intent> pinActivityRevokeResultLauncher;
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detail_vc, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);
        getWalletToken = GetWalletToken.getInstance(activity);
        TextView name = (TextView) view.findViewById(R.id.textView);
        name.setText(Preference.getUsernameForDemo(activity));
        TextView textView = (TextView) view.findViewById(R.id.textView2);
        imageView = (ImageView) view.findViewById(R.id.claimImg);
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (requireArguments().getString("vcId") != null) {
                    vcId = requireArguments().getString("vcId");
                    try {
                        hWalletToken = getWalletToken.getWalletTokenDataAPI(WalletTokenPurpose.WALLET_TOKEN_PURPOSE.DETAIL_VC).get();
                        WalletApi walletApi = WalletApi.getInstance(activity);
                        List<VerifiableCredential> vcList = walletApi.getCredentials(hWalletToken, List.of(vcId));
                        vc = vcList.get(0);
                        textView.setText(displayVc(vc));

                        // add zkp info
                        if (WalletApi.getInstance(activity).isZkpCredentialsSaved(vcId)) {
                            List<Credential> credentialList = walletApi.getZkpCredentials(hWalletToken, List.of(vcId));
                            credential = credentialList.get(0);
                            textView.append(displayZkpCredential(credential));
                        }

                    } catch (WalletCoreException | WalletException | UtilityException e) {
                        CaLog.e("get detail vc error : " + e.getMessage());
                        ContextCompat.getMainExecutor(activity).execute(()  -> {
                            CaUtil.showErrorDialog(activity, e.getMessage());
                        });
                    } catch (ExecutionException | InterruptedException e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof CompletionException && cause.getCause() instanceof CommunicationException) {
                            CaLog.e( "get detail VC failed : " + e.getMessage());
                            ContextCompat.getMainExecutor(activity).execute(()  -> {
                                CaUtil.showErrorDialog(activity, cause.getCause().getMessage());
                            });
                        }
                    }

                }
            }
        }).start();

        Button button = (Button) view.findViewById(R.id.button);
        button.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                navController.navigate(R.id.action_detailVcFragment_to_vcListFragment);
            }
        });
        Button trashButton = (Button) view.findViewById(R.id.trashBtn);
        trashButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog();
            }
        });

        pinActivityRevokeResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            String pin = result.getData().getStringExtra("pin");
                            if(result.getData().getIntExtra("reg", 0) == Constants.PIN_TYPE_USE_KEY) {
                                revokeVc(pin);
                            }
                        } else if(result.getResultCode() == Activity.RESULT_CANCELED){
                            CaUtil.showErrorDialog(activity,"[Information] canceled by user");
                        }
                    }
                }
        );
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (Activity) context;
    }

    private CharSequence displayVc(VerifiableCredential vc) {
        SpannableStringBuilder sb = new SpannableStringBuilder();

        String title = "Verifiable Credential\n\n";
        int start = sb.length();
        sb.append(title);
        int end = sb.length();

        sb.setSpan(new ForegroundColorSpan(Color.parseColor("#FF9800")), start, end - 2/*"\n\n" 제외*/, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, end - 2/*"\n\n" 제외*/, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        for (Claim claim : vc.getCredentialSubject().getClaims()) {
            sb.append(claim.getCaption());
            sb.append("\n");

            if (claim.getValue().contains("data:image")) {
                byte[] decoded = Base64.decode(claim.getValue().split(",")[1], Base64.DEFAULT);
                imageView.setImageBitmap(BitmapFactory.decodeByteArray(decoded, 0, decoded.length));
            } else {
                sb.append(claim.getValue());
            }

            sb.append("\n\n");
        }

        return sb;
    }



    private CharSequence displayZkpCredential(Credential credential) throws ExecutionException, InterruptedException {
        SpannableStringBuilder sb = new SpannableStringBuilder();

        sb.append("\n\n");

        String title = "Zero-Knowledge Proof\n\n";
        int start = sb.length(); // 주황색 시작 위치
        sb.append(title);
        int end = sb.length(); // 주황색 끝 위치

        sb.setSpan(new ForegroundColorSpan(Color.parseColor("#FF9800")), start, end - 2, /*"\n\n" 제외*/ Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, end - 2/*"\n\n" 제외*/, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        // credentialSchema 조회
        CredentialSchema schema = CaUtil.getCredentialSchema(activity, credential.getSchemaId());
        Log.d("djpark", "schema: "+GsonWrapper.getGson().toJson(schema));

        for (AttributeType type: schema.getAttrTypes()) {
            String nameSpace = type.getNamespace().getId();
            for (Map.Entry<String, AttributeValue> entry : credential.getValues().entrySet()) {
                String keyEntry = entry.getKey();
                if (keyEntry.startsWith(nameSpace) && keyEntry.length() > nameSpace.length()) {
                    String label = keyEntry.substring(nameSpace.length() + 1); // +1은 '.' 문자 제거용
                    String nmId = type.getNamespace().getId();
                    if (nmId.equals(nameSpace)) {
                        for (AttributeDef attrDef : type.getItems()) {
                            if (attrDef.getLabel().equals(label)) {
                                AttributeValue value = entry.getValue();
                                sb.append(attrDef.getCaption());
                                sb.append("\n");
                                sb.append(value.getRaw());
                                sb.append("\n\n");
                            }
                        }
                    }
                }
            }
        }

        for (Map.Entry<String, AttributeValue> entry : credential.getValues().entrySet()) {
            String keyEntry = entry.getKey();
            String[] parts = keyEntry.split("\\."); // zkp.18013.5.name
            int partsNum = parts.length;
            String namespace = parts[0];
            String key = parts[partsNum-1];

            for (AttributeType type: schema.getAttrTypes()) {
                String nmId = type.getNamespace().getId();
                if (nmId.equals(namespace)) {
                    for (AttributeDef attrDef : type.getItems()) {
                        if (attrDef.getLabel().equals(key)) {
                            AttributeValue value = entry.getValue();
                            sb.append(attrDef.getCaption());
                            sb.append("\n");
                            sb.append(value.getRaw());
                            sb.append("\n\n");
                        }
                    }
                }
            }
        }

        return sb;
    }

    private void showDialog() {

        CustomDialog customDialog = new CustomDialog(activity, Constants.DIALOG_CONFIRM_TYPE);
        customDialog.setMessage("Are you sure you want to delete it?");
        customDialog.setCancelable(false);
        customDialog.setDialogListener(new CustomDialog.CustomDialogInterface() {
            @Override
            public void yesBtnClicked(String btnName) {
                try {
                    revokePreVc(vcId);
                } catch (ExecutionException | InterruptedException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof CompletionException && cause.getCause() instanceof CommunicationException) {
                        CaLog.e("revoke error : " + e.getMessage());
                        ContextCompat.getMainExecutor(activity).execute(()  -> {
                            CaUtil.showErrorDialog(activity, cause.getCause().getMessage());
                        });
                    }
                }
            }

            @Override
            public void noBtnClicked(String btnName) {
            }
        });
        customDialog.show();
    }

    private void revokePreVc(String vcId) throws ExecutionException, InterruptedException {
        RevokeVc revokeVc = RevokeVc.getInstance(activity);
        VerifyAuthType.VERIFY_AUTH_TYPE verifyAuthType = revokeVc.revokeVcPreProcess(vcId).get();
        if(verifyAuthType == VerifyAuthType.VERIFY_AUTH_TYPE.PIN){
            Intent intent = new Intent(getContext(), PinActivity.class);
            intent.putExtra(Constants.INTENT_IS_REGISTRATION, false);
            intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_USE_KEY);
            pinActivityRevokeResultLauncher.launch(intent);
        } else if(verifyAuthType == VerifyAuthType.VERIFY_AUTH_TYPE.BIO){
            revokeVc.authenticateBio(DetailVcFragment.this, navController);
        } else if(verifyAuthType == VerifyAuthType.VERIFY_AUTH_TYPE.PIN_OR_BIO){
            try {
                if (revokeVc.isBioKey()) {
                    Bundle bundle = new Bundle();
                    bundle.putString("type", "revoke");
                    navController.navigate(R.id.action_detailVcFragment_to_selectAuthTypetFragment, bundle);
                }
                else {
                    Intent intent = new Intent(getContext(), PinActivity.class);
                    intent.putExtra(Constants.INTENT_IS_REGISTRATION, false);
                    intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_USE_KEY);
                    pinActivityRevokeResultLauncher.launch(intent);
                }
            } catch (WalletCoreException | UtilityException | WalletException e){
                CaLog.e("Bio Key not Register : " + e.getMessage());
                CaUtil.showErrorDialog(activity, e.getMessage());

            }
        } else if(verifyAuthType == VerifyAuthType.VERIFY_AUTH_TYPE.ANY
                || verifyAuthType == VerifyAuthType.VERIFY_AUTH_TYPE.PIN_AND_BIO){
            Bundle bundle = new Bundle();
            bundle.putString("type", "revoke");
            navController.navigate(R.id.action_detailVcFragment_to_selectAuthTypetFragment, bundle);
        }

    }
    private void revokeVc(String pin){
        RevokeVc revokeVc = RevokeVc.getInstance(activity);
        try {
            revokeVc.revokeVcProcess(pin).get();
            navController.navigate(R.id.action_detailVcFragment_to_vcListFragment);
        } catch (ExecutionException | InterruptedException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CompletionException && cause.getCause() instanceof CommunicationException) {
                CaLog.e("revoke error : " + e.getMessage());
                ContextCompat.getMainExecutor(activity).execute(()  -> {
                    CaUtil.showErrorDialog(activity, cause.getCause().getMessage());
                });
            }
        }
    }
}
