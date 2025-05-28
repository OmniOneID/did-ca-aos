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

package org.omnione.did.ca.ui.profile;

import static org.omnione.did.sdk.datamodel.offer.VerifyOfferPayload.OFFER_TYPE.VerifyProofOffer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

import org.omnione.did.ca.R;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.config.Preference;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.protocol.vc.IssueVc;
import org.omnione.did.ca.network.protocol.vp.VerifyVp;
import org.omnione.did.ca.network.protocol.token.GetWalletToken;
import org.omnione.did.ca.ui.PinActivity;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.datamodel.common.enums.VerifyAuthType;
import org.omnione.did.sdk.datamodel.protocol.P310ZkpResponseVo;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.datamodel.protocol.P210ResponseVo;
import org.omnione.did.sdk.datamodel.protocol.P310ResponseVo;
import org.omnione.did.sdk.datamodel.profile.IssueProfile;
import org.omnione.did.sdk.datamodel.profile.VerifyProfile;
import org.omnione.did.sdk.datamodel.vcschema.VCSchema;
import org.omnione.did.sdk.datamodel.zkp.AttributeDef;
import org.omnione.did.sdk.datamodel.zkp.AttributeInfo;
import org.omnione.did.sdk.datamodel.zkp.AttributeType;
import org.omnione.did.sdk.datamodel.zkp.CredentialDefinition;
import org.omnione.did.sdk.datamodel.zkp.CredentialSchema;
import org.omnione.did.sdk.datamodel.zkp.PredicateInfo;
import org.omnione.did.sdk.datamodel.zkp.ProofRequest;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class ProfileFragment extends Fragment {
    NavController navController;
    Activity activity;
    String type;
    String offerType;
    String profileData;
    private IssueProfile issueProfile;
    private VerifyProfile verifyProfile;

    private P310ZkpResponseVo proofRequestProfileVo;
    private String authNonce;
    ActivityResultLauncher<Intent> pinActivityIssueResultLauncher;
    ActivityResultLauncher<Intent> pinActivityVerifyResultLauncher;
    GetWalletToken getWalletToken;

    TextView title, message, textView, textView2, description, requireClaim;
    ImageView imageView;
    LinearLayout issueDsc, verifyDsc;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pinActivityIssueResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                    }
                }
        );
    }
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState){
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getWalletToken = GetWalletToken.getInstance(activity);

        navController = Navigation.findNavController(view);
        title = view.findViewById(R.id.title);
        message = view.findViewById(R.id.message);
        textView = view.findViewById(R.id.textView);
        textView2 = view.findViewById(R.id.textView2);

        description = view.findViewById(R.id.description);
        requireClaim = view.findViewById(R.id.requiredClaims);
        imageView = view.findViewById(R.id.imageView);
        issueDsc = view.findViewById(R.id.issueDsc);
        verifyDsc = view.findViewById(R.id.verifyDsc);
        Button okButton = (Button) view.findViewById(R.id.okBtn);

        type = requireArguments().getString("type");
        profileData = requireArguments().getString("result");
        offerType = requireArguments().getString("offerType");

        if(type.equals("user_init") || type.equals(Constants.TYPE_ISSUE)) {
            String profileData = requireArguments().getString("result");
            Preference.setProfile(getContext(), profileData);
            P210ResponseVo vcPofile = MessageUtil.deserialize(requireArguments().getString("result"), P210ResponseVo.class);
            issueProfile = vcPofile.getProfile();
            authNonce = vcPofile.getAuthNonce();
            title.setText("Issuance certificate Information");
            message.setText("The certificate will be issued by " + issueProfile.getProfile().issuer.getName());
            textView.setText(issueProfile.getTitle());
            textView2.setText("Issuance Date : " + CaUtil.convertDate(issueProfile.getProof().getCreated()));
            description.setText("The Identity certificate issued by " + issueProfile.getProfile().issuer.getName() + " is stored In the certificate.");
            issueDsc.setVisibility(View.VISIBLE);
            verifyDsc.setVisibility(View.GONE);
        } else if(requireArguments().getString("type").equals("webview")) {


        } else {
            Preference.setProfile(getContext(), requireArguments().getString("result"));
            if (offerType.equals(VerifyProofOffer.getValue())) {
                proofRequestProfileVo = MessageUtil.deserialize(requireArguments().getString("result"), P310ZkpResponseVo.class);
                title.setText("ZKP submission guide\n");
                imageView.setImageResource(R.drawable.user_icon);
                textView.setText(proofRequestProfileVo.getProofRequestProfile().getProfile().getProofRequest().getName());
                textView2.setVisibility(View.GONE);
                verifyDsc.setVisibility(View.VISIBLE);

                ProofRequest proofRequest = proofRequestProfileVo.getProofRequestProfile().getProfile().getProofRequest();
                String attrCredDefId = CaUtil.findAttributeNameByCredDefId(proofRequest.getRequestedAttributes());
                CredentialDefinition credentialDefinitionForAttr = CaUtil.getCredentialDefinition(activity, attrCredDefId);
                CredentialSchema schemaForAttr = CaUtil.getCredentialSchema(activity, credentialDefinitionForAttr.getSchemaId());

                for (Map.Entry<String, AttributeInfo> entry : proofRequest.getRequestedAttributes().entrySet()) {
                    AttributeInfo value = entry.getValue();
                    String[] parts = value.getName().split("\\.");
                    String namespace = parts[0];
                    String key = parts[1];
                    for (AttributeType type : schemaForAttr.getAttrTypes()) {
                        String nmId = type.getNamespace().getId();
                        if (nmId.equals(namespace)) {
                            for (AttributeDef attrDef : type.getItems()) {
                                if (attrDef.getLabel().equals(key)) {
                                    requireClaim.append(attrDef.getCaption()+"\n");
                                }
                            }
                        }
                    }
                }

                String predCredDefId = CaUtil.findPredicateNameByCredDefId(proofRequest.getRequestedPredicates());
                CredentialDefinition credentialDefinitionForPred = CaUtil.getCredentialDefinition(activity, predCredDefId);
                CredentialSchema schemaForPred = CaUtil.getCredentialSchema(activity, credentialDefinitionForPred.getSchemaId());

                for (Map.Entry<String, PredicateInfo> entry : proofRequest.getRequestedPredicates().entrySet()) {
                    PredicateInfo value = entry.getValue();
                    String[] parts = value.getName().split("\\.");
                    String namespace = parts[0];
                    String key = parts[1];
                    for (AttributeType type : schemaForPred.getAttrTypes()) {
                        String nmId = type.getNamespace().getId();
                        if (nmId.equals(namespace)) {
                            for (AttributeDef attrDef : type.getItems()) {
                                if (attrDef.getLabel().equals(key)) {
                                    requireClaim.append(attrDef.getCaption()+"\n");
                                }
                            }
                        }
                    }
                }

            } else {
                P310ResponseVo vpProfile = MessageUtil.deserialize(requireArguments().getString("result"), P310ResponseVo.class);
                verifyProfile = vpProfile.getProfile();
                title.setText("Certificate submission guide\n");
                imageView.setImageResource(R.drawable.user_icon);
                message.setText("The following certificate is submitted to the " + verifyProfile.getProfile().verifier.getName());
                try {
                    String vcSchemaStr = CaUtil.getVcSchema(activity, verifyProfile.getProfile().filter.getCredentialSchemas().get(0).getId()).get();
                    VCSchema vcSchema = MessageUtil.deserialize(vcSchemaStr, VCSchema.class);
                    if(vcSchemaStr.isEmpty()) {
                        ContextCompat.getMainExecutor(activity).execute(() -> {
                            CaUtil.showErrorDialog(activity, "[CA error] VC schema is null");
                        });
                    }
                    textView.setText(vcSchema.getTitle());
                } catch (ExecutionException | InterruptedException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof CompletionException && cause.getCause() instanceof CommunicationException) {
                        CaLog.e("get vc schema error : " + e.getMessage());
                        ContextCompat.getMainExecutor(activity).execute(()  -> {
                            CaUtil.showErrorDialog(activity, cause.getCause().getMessage());
                        });
                    } else {
                        CaUtil.showErrorDialog(activity, cause.getCause().getMessage(), activity);
                    }
                }
                textView2.setVisibility(View.GONE);
                issueDsc.setVisibility(View.GONE);
                verifyDsc.setVisibility(View.VISIBLE);
                for(String reqClaim : verifyProfile.getProfile().filter.getCredentialSchemas().get(0).requiredClaims){
                    requireClaim.append("* " + reqClaim + "\n");
                }
            }
        }

        okButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(type.equals("user_init")) {
                    Bundle bundle = new Bundle();
                    bundle.putInt("type", Constants.WEBVIEW_VC_INFO);
                    bundle.putString("vcSchemaId", requireArguments().getString("vcSchemaId"));
                    navController.navigate(R.id.action_profileFragment_to_webviewFragment, bundle);
                }
                else if(type.equals(Constants.TYPE_ISSUE)) {

                    imageView.setImageResource(R.drawable.issuer_logo);
                    try {
                        IssueVc issueVc = IssueVc.getInstance(activity);
                        if (issueVc.isBioKey()) {
                            issueVc.authenticateBio(authNonce, ProfileFragment.this, navController);
                        } else {
                            Intent intent = new Intent(getContext(), PinActivity.class);
                            intent.putExtra(Constants.INTENT_IS_REGISTRATION, false);
                            intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_USE_KEY);
                            pinActivityIssueResultLauncher.launch(intent);
                        }
                    } catch (WalletCoreException | UtilityException | WalletException e) {
                        CaLog.e("pin key authentication fail : " + e.getMessage());
                        CaUtil.showErrorDialog(activity, e.getMessage());
                    }

                } else {
                    imageView.setImageResource(R.drawable.user_icon);

                    if (offerType.equals(VerifyProofOffer.getValue())) {
                        Bundle bundle = new Bundle();
                        bundle.putString("proofRequestProfileVo", proofRequestProfileVo.toJson());
                        navController.navigate(R.id.action_profileFragment_to_VerifyFragment, bundle);
                    }
                    else {

                        VerifyVp verifyVp = VerifyVp.getInstance(activity);
                        if (verifyProfile.getProfile().process.authType == VerifyAuthType.VERIFY_AUTH_TYPE.PIN) {
                            Intent intent = new Intent(getContext(), PinActivity.class);
                            intent.putExtra(Constants.INTENT_IS_REGISTRATION, false);
                            intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_USE_KEY);
                            pinActivityVerifyResultLauncher.launch(intent);
                        } else if (verifyProfile.getProfile().process.authType == VerifyAuthType.VERIFY_AUTH_TYPE.BIO) {
                            verifyVp.authenticateBio(ProfileFragment.this, navController);
                        } else if (verifyProfile.getProfile().process.authType == VerifyAuthType.VERIFY_AUTH_TYPE.PIN_OR_BIO) {
                            try {
                                if (verifyVp.isBioKey()) {
                                    Bundle bundle = new Bundle();
                                    bundle.putString("type", Constants.TYPE_VERIFY);
                                    navController.navigate(R.id.action_profileFragment_to_selectAuthTypetFragment, bundle);
                                } else {
                                    Intent intent = new Intent(getContext(), PinActivity.class);
                                    intent.putExtra(Constants.INTENT_IS_REGISTRATION, false);
                                    intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_USE_KEY);
                                    pinActivityVerifyResultLauncher.launch(intent);
                                }
                            } catch (WalletCoreException | UtilityException | WalletException e) {
                                CaLog.e("Bio Key not Register : " + e.getMessage());
                                CaUtil.showErrorDialog(activity, e.getMessage());
                            }
                        } else if (verifyProfile.getProfile().process.authType == VerifyAuthType.VERIFY_AUTH_TYPE.ANY
                                || verifyProfile.getProfile().process.authType == VerifyAuthType.VERIFY_AUTH_TYPE.PIN_AND_BIO) {
                            Bundle bundle = new Bundle();
                            bundle.putString("type", Constants.TYPE_VERIFY);
                            navController.navigate(R.id.action_profileFragment_to_selectAuthTypetFragment, bundle);
                        }

                    }
                }
            }
        });
        Button cancelButton = (Button) view.findViewById(R.id.cancelBtn);
        cancelButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                navController.navigate(R.id.action_profileFragment_to_vcListFragment);
            }
        });

        // activity callback
        pinActivityIssueResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            try {
                                CaLog.e("onViewCreated pinActivityIssueResultLauncher");
                                String pin = result.getData().getStringExtra("pin");
                                IssueVc issueVc = IssueVc.getInstance(activity);
                                issueVc.getSignedDIDAuthByPin(authNonce, pin, navController);
                            } catch (WalletCoreException | WalletException | UtilityException e){
                                CaLog.e("signing error : " + e.getMessage());
                                CaUtil.showErrorDialog(activity, e.getMessage());
                            }
                        } else if(result.getResultCode() == Activity.RESULT_CANCELED){
                            CaUtil.showErrorDialog(activity,"[Information] canceled by user");
                        }
                    }
                }
        );

        pinActivityVerifyResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            String pin = result.getData().getStringExtra("pin");
                            if(result.getData().getIntExtra("reg", 0) == Constants.PIN_TYPE_USE_KEY) {
                                submitVp(pin);
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
    @Override
    public void onResume() {
        super.onResume();
        if(type.equals("webview")) {
            type = "user_init";
            P210ResponseVo vcProfile = MessageUtil.deserialize(Preference.getProfile(getContext()), P210ResponseVo.class);
            issueProfile = vcProfile.getProfile();
            authNonce = vcProfile.getAuthNonce();
            title.setText("Issuance certificate Information");
            message.setText("The certificate will be issued by " + issueProfile.getProfile().issuer.getName());
            textView.setText(issueProfile.getTitle());
            textView2.setText("Issuance Application Date : " + CaUtil.convertDate(issueProfile.getProof().getCreated()));
            description.setText("The Identity certificate issued by " + issueProfile.getProfile().issuer.getName() + " is stored In the certificate.");
            issueDsc.setVisibility(View.VISIBLE);
            verifyDsc.setVisibility(View.GONE);

            try {
                IssueVc issueVc = IssueVc.getInstance(activity);
                if (issueVc.isBioKey()) {
                    issueVc.authenticateBio(authNonce, ProfileFragment.this, navController);
                } else {
                    Intent intent = new Intent(activity, PinActivity.class);
                    intent.putExtra(Constants.INTENT_IS_REGISTRATION, false);
                    intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_USE_KEY);
                    pinActivityIssueResultLauncher.launch(intent);
                }
            } catch (WalletCoreException | UtilityException | WalletException e) {
                CaLog.e("authentication fail : " + e.getMessage());
                CaUtil.showErrorDialog(activity, e.getMessage());
            }

        } else if(type.equals("user_init")){
            title.setText("Issuance certificate Information");
            message.setText("The certificate will be issued by " + issueProfile.getProfile().issuer.getName());
            textView.setText(issueProfile.getTitle());

            description.setText("The Identity certificate issued by " + issueProfile.getProfile().issuer.getName() + " is stored In the certificate.");
            issueDsc.setVisibility(View.VISIBLE);
            verifyDsc.setVisibility(View.GONE);
        }
    }

    private void submitVp(String pin){
        VerifyVp verifyVp = VerifyVp.getInstance(activity);
        try {
            verifyVp.verifyVpProcess(pin).get();
        } catch (ExecutionException | InterruptedException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CompletionException && cause.getCause() instanceof CommunicationException) {
                CaLog.e("submit vp error : " + e.getMessage());
                ContextCompat.getMainExecutor(activity).execute(()  -> {
                    CaUtil.showErrorDialog(activity, cause.getCause().getMessage());
                });
            } else {
                CaUtil.showErrorDialog(activity, cause.getCause().getMessage(), activity);
            }
        }
        Bundle bundle = new Bundle();
        bundle.putString("type",Constants.TYPE_VERIFY);
        navController.navigate(R.id.action_profileFragment_to_resultFragment, bundle);
    }
}
