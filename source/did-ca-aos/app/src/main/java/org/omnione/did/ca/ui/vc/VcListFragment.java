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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
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
import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.config.Preference;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.HttpUrlConnection;
import org.omnione.did.ca.network.protocol.token.GetWalletToken;
import org.omnione.did.ca.network.protocol.vc.IssueVc;
import org.omnione.did.ca.network.protocol.vp.VerifyProof;
import org.omnione.did.ca.network.protocol.vp.VerifyVp;
import org.omnione.did.ca.ui.ScanQrActivity;
import org.omnione.did.ca.ui.common.CustomDialog;
import org.omnione.did.ca.ui.common.PayloadData;
import org.omnione.did.ca.ui.common.ProgressCircle;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.datamodel.offer.IssueOfferPayload;
import org.omnione.did.sdk.datamodel.offer.VerifyOfferPayload;
import org.omnione.did.sdk.datamodel.vc.Claim;
import org.omnione.did.sdk.datamodel.vc.VerifiableCredential;
import org.omnione.did.sdk.datamodel.vcschema.VCSchema;
import org.omnione.did.sdk.datamodel.zkp.Credential;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.utility.MultibaseUtils;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class VcListFragment extends Fragment {
    NavController navController;
    Activity activity;
    VcListAdapter adapter;
    String vcSchema = "";
    WalletApi walletApi;
    ActivityResultLauncher<Intent> qrActivityResultLauncher;
    String hWalletToken;
    GridView gridView;

    ProgressCircle progressCircle;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_vc_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);
        TextView nameView = view.findViewById(R.id.name);
        nameView.setText(Preference.getUsernameForDemo(activity));

        progressCircle = new ProgressCircle(activity);

        Button addVcBtn = view.findViewById(R.id.addVcBtn);
        addVcBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                navController.navigate(R.id.action_vcListFragment_to_addVcFragment);
            }
        });

        Button qrBtn = view.findViewById(R.id.qrBtn);
        qrBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(activity, ScanQrActivity.class);
                qrActivityResultLauncher.launch(intent);
            }
        });

        qrActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            String qr = result.getData().getStringExtra("offer");
                            PayloadData payloadData = MessageUtil.deserialize(qr, PayloadData.class);

                            String payload = "";
                            try {
                                payload = new String(MultibaseUtils.decode(payloadData.getPayload()));
                            } catch (UtilityException e) {
                                CaLog.e("payload decode error : " + e.getMessage());
                                CaUtil.showErrorDialog(activity, e.getMessage());
                            }
                            if(payloadData.getPayloadType().equals("ISSUE_VC")){
                                CaLog.d("Issue VC Profile");
                                IssueOfferPayload offer = MessageUtil.deserialize(payload, IssueOfferPayload.class);
                                IssueVc issueVc = IssueVc.getInstance(activity);

                                String issueProfile = null;
                                try {
                                    issueProfile = issueVc.issueVcPreProcess(offer.getVcPlanId(), offer.getIssuer(), offer.getOfferId()).get();
                                } catch (ExecutionException | InterruptedException e) {
                                    ContextCompat.getMainExecutor(activity).execute(()  -> {
                                        CaUtil.showErrorDialog(activity, e.getMessage());
                                    });
                                }
                                Bundle bundle = new Bundle();
                                bundle.putString("result", issueProfile);
                                bundle.putString("type",Constants.TYPE_ISSUE);
                                bundle.putString("offerType", offer.getType().getValue());
                                navController.navigate(R.id.action_vcListFragment_to_profileFragment, bundle);

                            } else if(payloadData.getPayloadType().equals("SUBMIT_VP")){
                                CaLog.d("Submit VP Profile");
                                VerifyOfferPayload offer = MessageUtil.deserialize(payload, VerifyOfferPayload.class);
                                try {
                                    String verifyProfile;

                                    if (offer.getType().getValue().toString().equals(VerifyOfferPayload.OFFER_TYPE.VerifyProofOffer.toString())) {
                                        verifyProfile = VerifyProof.getInstance(activity).verifyProofPreProcess(offer.getOfferId(),payloadData.getTxId()).get();
                                        CaLog.d("verifyProofPreProcess verifyProfile: "+verifyProfile);


                                    } else {
                                        verifyProfile = VerifyVp.getInstance(activity).verifyVpPreProcess(offer.getOfferId(),payloadData.getTxId()).get();
                                        CaLog.d("verifyVpPreProcess verifyProfile: "+verifyProfile);
                                    }

                                    Bundle bundle = new Bundle();
                                    bundle.putString("result", verifyProfile);
                                    bundle.putString("type",Constants.TYPE_VERIFY);
                                    bundle.putString("offerType", offer.getType().getValue());
                                    navController.navigate(R.id.action_vcListFragment_to_profileFragment, bundle);

                                } catch (ExecutionException | InterruptedException e) {
                                    CaLog.e("Vp profile error : " + e.getMessage());
                                    ContextCompat.getMainExecutor(activity).execute(()  -> {
                                        CaUtil.showErrorDialog(activity, e.getMessage());
                                    });
                                }
                            }
                        } else if(result.getResultCode() == Activity.RESULT_CANCELED){
                            CaUtil.showErrorDialog(activity,"[Information] canceled by user");
                        }
                    }
                }
        );

        gridView = (GridView) view.findViewById(R.id.gridView);
        gridView.setVisibility(View.VISIBLE);
        String vcForm = CaUtil.drawableToBase64(activity,R.drawable.default_vc);
        adapter = new VcListAdapter();
        List<VcDetail> vcDetails = new ArrayList<>();
        GetWalletToken getWalletToken = GetWalletToken.getInstance(activity);

        new Thread(() -> requireActivity().runOnUiThread(() -> progressCircle.show())).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    hWalletToken = getWalletToken.getWalletTokenDataAPI(WalletTokenPurpose.WALLET_TOKEN_PURPOSE.LIST_VC).get();
                    walletApi = WalletApi.getInstance(activity);
                    List<VerifiableCredential> vcList = walletApi.getAllCredentials(hWalletToken);
                    if(vcList == null) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView emptyTextView = (TextView) view.findViewById(R.id.emptyText);
                                emptyTextView.setVisibility(View.VISIBLE);
                                gridView.setVisibility(View.GONE);
                                progressCircle.dismiss();
                            }
                        });
                        return;
                    }

                    for(VerifiableCredential vc : vcList){
                        vcSchema = getVcSchema(vc.getCredentialSchema().getId()).get();
                        if(vcSchema.isEmpty()){
                            ContextCompat.getMainExecutor(activity).execute(()  -> {
                                CaUtil.showErrorDialog(activity, "[CA error] VC Schema is null" , activity);
                            });
                        }
                        vcDetails.add(setVcInfo(vcSchema));
                    }
                    if (WalletApi.getInstance(activity).isAnyZkpCredentialsSaved()) {
                        List<Credential> zkpVcList = walletApi.getAllZkpCredentials(hWalletToken);
                        CaLog.d("Zkp list size : " + zkpVcList.size());
                        for (Credential credential : zkpVcList) {
                            CaLog.d("Zkp list id : " + credential.getCredentialId());
                        }
                    }

                    List<String> ids = new ArrayList<>();
                    for(VcDetail vcDetail : vcDetails) {
                        ids.add(vcDetail.getVcId());
                        boolean hasZkp = false;
                        if (WalletApi.getInstance(activity).isZkpCredentialsSaved(vcDetail.getVcId())) {
                            List<Credential> credentials = WalletApi.getInstance(activity).getZkpCredentials(hWalletToken, ids);
                            if (credentials.size() > 0) {
                                hasZkp = true;
                            }
                        }
                        // VCMeta조회 후 화면 표시 vcStatus
                        String vcStatus = CaUtil.getVcMeta(activity, vcDetail.getVcId());

                        adapter.addItem(new VcListItem(vcDetail.getTitle(), vcDetail.getValidUntil(), vcDetail.getIssuanceDate(), vcStatus, hasZkp, vcDetail.getImage()));
                        ids.clear();
                    }

                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            gridView.setAdapter(adapter);
                            new Thread(() -> requireActivity().runOnUiThread(() -> progressCircle.dismiss())).start();
                        }
                    });
                } catch (WalletException | WalletCoreException | UtilityException e){
                    CaLog.e("getVcItem error : " + e.getMessage());
                    ContextCompat.getMainExecutor(activity).execute(()  -> {
                        CaUtil.showErrorDialog(activity, e.getMessage(), activity);
                    });
                } catch (ExecutionException | InterruptedException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof CompletionException && cause.getCause() instanceof CommunicationException) {
                        ContextCompat.getMainExecutor(activity).execute(()  -> {
                            CaUtil.showErrorDialog(activity, cause.getCause().getMessage(), activity);
                        });
                    }
                } finally {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressCircle.dismiss();
                        }
                    });
                }
            }
        }).start();

        //String vcForm = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMoAAACZCAYAAABuf6XxAAAAAXNSR0IArs4c6QAAC8dJREFUeF7tnX1sldUdx7/n3tLe0pa+WBw4IVMQKLPjxbkNRGylzGwmZMmyF8CMZCr7A+eWTbJEp5S4kEXNljiNWyZL5oJ1f4x1i4nZ0LXEThlvsom0oAuO+oKstOsrt+2991keWKWycu/veb33POfbv9r0d94+5/fJOeee+9yrwB8SIIGcBFTOCAaQAAmAojAJSEBAgKIIIDGEBCgKc4AEBAQoigASQ0iAojAHSEBAgKIIIDGEBCgKc4AEBAQoigASQ0iAojAHSEBAgKIIIDGEBCgKc4AEBAQoigASQ0iAojAHSEBAgKIIIDGEBCgKc4AEBAQoigASQ0ggUFGszpYGKGz7KGargdhJwBsB1f5heSuz1/5d1W1s9lZn9tK+ivJRMShEkBPHuqcioNphZfYGIY0volwUhHIwgQuEgGVt91MYz6JYnbuaodQl26sCgcVukIBPwngSxepqaQO4ijAbC5yAD7K4FoWSFHhysHuXEFDtatH6RrdYXInC7ZZb3CyXXwLuZXEsCiXJ71SzdY8EXG7DHIly4dUtq81jV1mcBPJLwFKNqm79xbsYQW8cisJXuARMGVLwBJxvwZyJ0vWsVfAM2EESkBBwuKqIReHZREKfMdoQcHhWkYvC1USbHGBHJQScbb8oioQpYyJJQC3aIM5/USC3XZHMEw7KwTmFojBdzCXg4JwiE4Xv6TI3mSI9cvk5haJEOhE4uOwEKAozhAQEBHwXhReNAuoM0ZCA9JUv4daLomiYA+yygABFEUBiCAlQFOYACQgIUBQBJIaQAEVhDpCAgABFEUBiCAlQFOYACQgI+CuKZfGBLQF0huhHQCkluiIRBVkURb8MYI9FBCiKCBODTCdAUUzPAI5fRICiiDAxyHQCFMX0DOD4RQQoiggTg0wnQFFMzwCOX0SAoogwMch0AhTF9Azg+EUE/BWFH34ngs4g/Qj4+xYWiqJfBrDHIgIURYSJQaYToCimZwDHLyJAUUSYGGQ6AYpiegZw/CICFEWEiUGmE6AopmcAxy8iQFFEmBhkOgGKYnoGcPwiAhRFhIlBphOgKKZnAMcvIkBRRJgYZDoBimJ6BnD8IgKRFuWFjjdwOnMVis+9g/EZC1DWfwTDlUsxbeAEqkpSSFsK6xqXikAxyGwCkRJlPJVGJpPB1x/6PVpbW0UzW1lZidWrV2PzHV/CbZ8sxrSiuKgcg8wiEBlR/t07gKtWb0EqlXY9g9OmFeHdvU9gZs0M13WwYDQJaC9K24ETeODxVuw7+Hf48UGV9idnrvzMMjx8zzo03rhAn1mvrQdKrwSmX6lPn8Po6cgZ4NwZoOd1T61pLcpIchTLv7wNx/95yhOEqQovnDcXh3+3HdMTJb7X7XuFc9ZQkFxQbWG6X8oVddn/ayuKvdW6du33MDQ84nrwuQqWl03HyRd/itrqilyh+fu/vZJccX3+2tep5bNHXa8sWopyLjmGuU33oaenJ/Bpqq2txakXH0Npojjwtlw1wNVEjs3DqqKlKA/ufAU/evQJOSAAsVgMVVVVGBwcRGlpKQYGBpBIJDA2Nnb+lbJsPz/ceg8evnOlo/ZCC164PrSmItHQ8RZXw9BOlPYDXbj9W49hZES25bJlSCaTruBMFJo5cyZ2P/5trFo231M9gRSmKM6wmiLKpvufxjO7/+IMjg/Rmzd8Eb946A4favK5Cm695EBN2nrNbdqK7nfevfyrE0r58jLxpQ0sXHAduv64XT4pYUXyMC8nbcphPpOxUPKpTUilUnI4PkUWFRVh9B+/Riwm+vIxn1oVVsNVJTcoD6uJXblWZxT7QjFWtzE3lIAiMp27IPwqv4B6kKVaXjhODcfEC0d7RYkvzp8o6WO7CnNFCV9L41rUakWxZ+e7O55BcuQ/qB08CHsXNDh64YuIK0oUMhYwmrIwlgYqEwppC7C/p3ho1Prw77gC+pMWyksU7O95nfi7OA6UFKnL1pmc1YSfPbjJuAThgC8Q0E4Uu9OZN3fDOvl8qHO4v+Y7WPHpJaG2ycYKh4CWoqT33A1Y7t8l7Ab/3yo2Y+WKz7kpyjIRIKCnKH/+Zujo95XfhZtWFujtfOg0zGtQT1Ha7gXGh0KdrX3lm3HTSq4ooUIvoMa0FCVz8BFYvV2hYtxfuxUrlteF2iYbKxwCWoqy78Ah3Nj3ZKgU45//VajtOW6M9yhTIzPxHmWCxNHOt1DXvcNxLrkt8HLRBjTc2uS2ePDleDOfm7FJN/MTNN4704ePHfl+bjg+RRyb+wDqF83zqTafq+F7veRATXmv12Qi5w4/heKeA3JIbiNVHJ3XNOP6+R93W0Ow5biayPl6WFW0PKPYZMbGU4jv3QJkxuWgXESeWf4TzK6tclEypCJ8HsUZaFOeR5lMZWhoCKWv3OsMlIPoMRSjpOmpwn5/F0VxMKMATBTFJvRB+yOoHQvgpeJpZehf/mPUVJY5m4iwo7n1khM3ces1QafjwBu45uxzmBW7/MNccpL/iyyegY7xW3HL2nWOi4ZegId5OXITD/OT6fT2D6P84A8QT8ueo89GNo04Ds28DyuWLZRPQL4juarkngEPq4ldubaH+UvJDI8kUdyxBTFceNu9m58MFMZWPYmy6Qk3xfNbhheOU/M3+cJxKiLpdAZ46S7vybrmacTjMe/1sIZIEYjMimI/JpzZc6fnyYmt3Vm4j/t6Hh0rcEtAa1EGhkYQj8dhZSz09PVjzrH7AQ9bL0Che/EO1FZXQsUU0uk0ZpRPd8uW5SJEQDtRPjg7gK63TuHm0g5Yp/cHPhXq6tV4dWgJltYvRlmpBh/YHTgRMxvQQpTDR0+g5tRvMKekB0iP5m+mimeg+1w1emsasPyzt+SvH2w5dAIFKYp93jh07F+Iv/0HLK44g6Lk+6GDydVgKjELx4bmIn31GtxQP5/nmlzANP9/QYnSfboXfX39WDjUiqI+b1/8Eua8pOZ9FcdHr0N1dRXmzL4izKbZVkgECkaUI50nUd+7Exh+L6ShB9BMogaDS5pRVVkeQOU5quQ9SrTvUf762ptY2vtzJNJ94SdXQC0mYxWIrXo0vO9U4c187pnU+Wa+44VdWBbfhwSGcw9Us4ikqsBrYzdg1e3fCLbnfK+XnK+O7/Vq3/M8brZ2ywepaeTLsa+goekLwfWeq4mcrYdVJS9nlNTrv4R6/1X5ADWPtGavQFH93cGMgs+jOOOqy/Mombf/BOvEb50NLgLRasHXEPvEbf6PhKI4Y6qLKOk8fMqjM5LBRQfykUfcesknTKetF0WRz6sokod5EabzQTod5imKfF7FkVxVcqPysJrYlYd+mKcouefUVQQvHKfGpuuDWxTFlQYslGcCXFFCnIBADvMh9t/kpihKiLNPUUKE7XNTFMVnoNmqoyghwva5KZ9FaWkDrIZsfeQZxecZZHUhEFDtatH6RklDShJkdVEUriiSTNEthqKEOmPceoWK28fGKIqPMHNXRVFyMyrICMvaruo2Nkv6Jtt6dbY0QFltPKNMTYCiSFKtAGP8FsUeotX1bNbPNOVhvgATgV3KSkD6ipddiWhFuSBK9gM9RWFW6kVAfj7xVRS9ILG3xhNwsO1yJorgnGI8fALQhoCTbZcjUSTbL20osaNmE3C4mjgXhauK2QkWkdE7XU0ci3J+Venc1QyltkWEGYdhGgEXq4krUbgFMy2zIjRel5K4FoWyRCh5jBmKs5eDL8UivkeZiie3YcZkmeYD9SaJpxVlghxl0TyHot59D9utyWg8rSgXZWlpADINPORHPet0Gp9qh4Xtqm59ux+99kWUyR3hCuPHtLAOdwRsOTJ7gVi7X4JM9MN3Uf5PmvMbvNik73vL/qSkO0AsZRYBdXGVCEgMXw/zZk0OR2sygUBXFJPBcuzRIkBRojWfHE1ABChKQGBZbbQIUJRozSdHExABihIQWFYbLQIUJVrzydEERICiBASW1UaLAEWJ1nxyNAERoCgBgWW10SJAUaI1nxxNQAQoSkBgWW20CFCUaM0nRxMQAYoSEFhWGy0C/wUIXIHWMnKwUwAAAABJRU5ErkJggg==";

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Bundle bundle = new Bundle();
                bundle.putString("vcId", vcDetails.get(position).getVcId());
                navController.navigate(R.id.action_vcListFragment_to_detailVcFragment, bundle);
            }
        });
    }

    static class VcListAdapter extends BaseAdapter {
        ArrayList<VcListItem> items = new ArrayList<>();

        @Override
        public int getCount() {
            return items.size();
        }

        public void addItem(VcListItem item) {
            items.add(item);
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            VcListItemView view = null;
            if (convertView == null) {
                view = new VcListItemView(parent.getContext());
            } else {
                view = (VcListItemView) convertView;
            }

            VcListItem item = items.get(position);

            view.setTitle(item.getTitle());
            view.setValidUntil(item.getValidUntil());
            view.setIssuanceDate(item.getIssuanceDate());
            view.setImage(item.getImg());
            view.setVcStatus(item.getVcStatus());
            view.setIsZkp(item.isZkp());

            return view;
        }

    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (Activity) context;
        requireActivity().getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
    }


    public CompletableFuture<String> getVcSchema(String schemaId){
        HttpUrlConnection httpUrlConnection = new HttpUrlConnection();

        return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(activity, schemaId, "GET", ""))
            .thenCompose(CompletableFuture::completedFuture)
            .exceptionally(ex -> {
                throw new CompletionException(ex);
        });
    }

    private VcDetail setVcInfo(String schemaData) {
        VCSchema schema = MessageUtil.deserialize(schemaData, VCSchema.class);

        VcDetail vcDetail = new VcDetail();
        try {
//            List<Credential> zkpVcList = walletApi.getAllZkpCredentials(hWalletToken);
//            CaLog.d("Zkp list size : " + zkpVcList.size());
//            for (Credential credential : zkpVcList) {
//                CaLog.d("Zkp list id : " + credential.getCredentialId());
//            }

            List<VerifiableCredential> vcList = walletApi.getAllCredentials(hWalletToken);
            CaLog.d("VC list size : " + vcList.size());
            for (VerifiableCredential vc : vcList) {
                CaLog.d("VC list id : " + vc.getId());
                if(vc.getCredentialSchema().getId().equals(schema.getId())) {
                    CaLog.d("get VC in Wallet : " + vc.toJson());
                    vcDetail = new VcDetail();
                    vcDetail.setVcId(vc.getId());
                    vcDetail.setTitle(schema.getTitle());
                    vcDetail.setValidUntil(vc.getValidUntil());
                    vcDetail.setIssuanceDate(vc.getIssuanceDate());

                    if(schema.getTitle().contains("Driver"))
                        vcDetail.setImage(CaUtil.drawableToBase64(activity,R.drawable.nation_id_vc));
                    else
                        vcDetail.setImage(CaUtil.drawableToBase64(activity,R.drawable.default_vc));

                    for (int i = 0; i < vc.getCredentialSubject().getClaims().size(); i++) {
                        Claim claim = vc.getCredentialSubject().getClaims().get(i);
                        if (claim.getCaption().equals("성")) {
                            vcDetail.setName(claim.getValue());
                        } else if (claim.getCaption().equals("이름")) {
                            String firstName = vcDetail.getName();
                            vcDetail.setName(firstName + claim.getValue());
                        } else if (claim.getCaption().equals("생년월일")) {
                            vcDetail.setBirth(claim.getValue());
                        } else if (claim.getCaption().equals("증명사진")) {
                            //vcDetail.setImage(claim.getValue());
                        }
                    }
                }
            }
        } catch (WalletException | UtilityException | WalletCoreException e) {
            CaLog.e("get vc fail : " + e.getMessage());
            ContextCompat.getMainExecutor(activity).execute(()  -> {
                CaUtil.showErrorDialog(activity, e.getMessage(), activity);
            });
        }
        return vcDetail;
    }

    private final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            showExitDialog();
        }
    };

    private void showExitDialog() {
        CustomDialog customDialog = new CustomDialog(activity, Constants.DIALOG_CONFIRM_TYPE);
        customDialog.setMessage("Do you want to close the app?");
        customDialog.setCancelable(false);
        customDialog.setDialogListener(new CustomDialog.CustomDialogInterface() {
            @Override
            public void yesBtnClicked(String btnName) {
                activity.finish();
            }
            @Override
            public void noBtnClicked(String btnName) {

            }
        });
        customDialog.show();
    }
}
