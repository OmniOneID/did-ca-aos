/*
 * Copyright 2025 OmniOne.
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

package org.omnione.did.ca.zkp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import org.omnione.did.ca.R;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.protocol.vp.VerifyProof;
import org.omnione.did.ca.ui.common.ProgressCircle;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.zkp.referent.AttrRefAdapter;
import org.omnione.did.ca.zkp.referent.PredicateRefAdapter;
import org.omnione.did.ca.zkp.referent.SelfAttrRefAdapter;

import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.datamodel.profile.ProofRequestProfile;
import org.omnione.did.sdk.datamodel.zkp.AttrReferent;
import org.omnione.did.sdk.datamodel.zkp.AvailableReferent;
import org.omnione.did.sdk.datamodel.zkp.CredentialDefinition;
import org.omnione.did.sdk.datamodel.zkp.CredentialSchema;
import org.omnione.did.sdk.datamodel.zkp.PredicateReferent;
import org.omnione.did.sdk.datamodel.zkp.ProofParam;
import org.omnione.did.sdk.datamodel.zkp.ProofRequest;
import org.omnione.did.sdk.datamodel.zkp.Referent;
import org.omnione.did.sdk.datamodel.zkp.ReferentInfo;
import org.omnione.did.sdk.datamodel.zkp.UserReferent;
import org.omnione.did.sdk.datamodel.util.GsonWrapper;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VerifyFragment extends Fragment {
    private static final String ZKP_TAG = VerifyFragment.class.getName();
    private TextView textView_attributes, textView_predicates, textView_self_attributes;
    private ListView listView_attr, listView_predicates, listView_self_attr;
    private ArrayList<String> attr_ref_ArrayList, predicate_ref_ArrayList, self_attr_ref_ArrayList;
    private AttrRefAdapter attrRefAdapter;
    private PredicateRefAdapter predicateRefAdapter;
    private SelfAttrRefAdapter selfattrRefAdapter;
    private Button okBtn, cancelBtn;
    private String selectedCredentialId, selectedRaw;
    private int pos;
    private int ATTR_REF_REQUEST_CODE = 1;
    private int PREDICATE_REF_REQUEST_CODE = 2;
    private List<UserReferent> selectedUserReferent = new LinkedList<UserReferent>();
    private List<UserReferent> selectedAttrReferent = new LinkedList<UserReferent>();
    private NavController navController;
    private Map<String, String> selfAttr = new HashMap<String, String>();
    private ProofRequestProfile proofRequestProfile;

    private ProgressCircle progressCircle;

    private AvailableReferent availableReferent;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_verify, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);
        try {
            proofRequestProfile = GsonWrapper.getGson().fromJson(requireArguments().getString("proofRequestProfile"), ProofRequestProfile.class);
            initUI(view);
            drawAvailableReferent();
        } catch (WalletCoreException | WalletException | UtilityException e) {
            CaUtil.showErrorDialog(getContext(), e.getMessage());
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void initUI(View view) {

        progressCircle = new ProgressCircle(getActivity());
        navController = Navigation.findNavController(view);

        textView_attributes = view.findViewById(R.id.textView_attributes);
        textView_predicates = view.findViewById(R.id.textView_predicates);
        textView_self_attributes = view.findViewById(R.id.textView_self_attributes);

        listView_attr = view.findViewById(R.id.listView_attr);
        listView_predicates = view.findViewById(R.id.listView_predicates);
        listView_self_attr = view.findViewById(R.id.listView_self_attr);

        cancelBtn = view.findViewById(R.id.cancelBtn);
        cancelBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                navController.navigate(R.id.action_VerifyFragment_to_vcListFragment);
            }
        });

        // attr_referent listview
        listView_attr.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(getContext(), ReferentListActivity.class);
                intent.putExtra("referent", GsonWrapper.getGsonPrettyPrinting().toJson(attr_ref_ArrayList.get(position)));
                intent.putExtra("pos", position);
                startActivityForResult(intent, ATTR_REF_REQUEST_CODE);
            }
        });

        // predicate_referent listview
        listView_predicates.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(getContext(), ReferentListActivity.class);
                intent.putExtra("referent", GsonWrapper.getGsonPrettyPrinting().toJson(predicate_ref_ArrayList.get(position)));
                intent.putExtra("pos", position);
                startActivityForResult(intent, PREDICATE_REF_REQUEST_CODE);
            }
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();

        okBtn = view.findViewById(R.id.okBtn);
        okBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                showLoading();
                executor.execute(() -> {
                    InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);

                    for (int i = 0; i < self_attr_ref_ArrayList.size(); i++) {
                        View childView = listView_self_attr.getChildAt(i);
                        EditText editText = childView.findViewById(R.id.editText_self_attr_ref_item);
                        String data = editText.getText().toString();
                        AttrReferent selfAttrReferent = GsonWrapper.getGson().fromJson(GsonWrapper.getGsonPrettyPrinting().toJson(self_attr_ref_ArrayList.get(i)), AttrReferent.class);
                        Map self_ref_map = availableReferent.getSelfAttrReferent();
                        List keys = new ArrayList(self_ref_map.keySet());

                        // duplicate remove
                        for (int j = 0; j < selectedUserReferent.size(); j++) {
                            if (selectedUserReferent.get(j).getReferentName().equals(selfAttrReferent.getName())) {
                                selectedUserReferent.remove(j);
                            }
                        }

                        selectedUserReferent.add(new UserReferent.Builder().setReferentKey((String) keys.get(i)).setReferentName(selfAttrReferent.getName()).build());
                        selfAttr.put((String) keys.get(i), data);
                    }

                    // duplicate remove
                    for (int i = 0; i < selectedUserReferent.size(); i++) {
                        for (int j = 0; j < selectedAttrReferent.size(); j++) {
                            if (selectedUserReferent.get(i).getReferentName().equals(selectedAttrReferent.get(j).getReferentName())) {
                                selectedUserReferent.remove(i);
                            }
                        }
                    }

                    // attr_referent checkbox
                    for (int i = 0; i < selectedAttrReferent.size(); i++) {
                        CaLog.d("isRevealed : " + selectedAttrReferent.get(i).isRevealed());
                        View childView = listView_attr.getChildAt(i);
                        CheckBox checkBox = childView.findViewById(R.id.checkBox_attr_ref);
                        CaLog.d("selectedAttrReferent " + i + " : " + checkBox.isChecked());
                        selectedUserReferent.add(new UserReferent.Builder()
                                .setReferentKey(selectedAttrReferent.get(i).getReferentKey())
                                .setReferentName(selectedAttrReferent.get(i).getReferentName())
                                .setRaw(selectedAttrReferent.get(i).getRaw())
                                .setCredentialId(selectedAttrReferent.get(i).getCredentialId())
                                .setRevealed(!checkBox.isChecked())
                                .build());
                    }

                    CaLog.d("selectedUserReferent >>>>>>> " + GsonWrapper.getGsonPrettyPrinting().toJson(selectedUserReferent));

                    try {
                        ReferentInfo referentInfo = WalletApi.getInstance(getContext()).createZkpReferent(selectedUserReferent);
                        CaLog.d("referentInfo: " + GsonWrapper.getGsonPrettyPrinting().toJson(referentInfo));

                        final List<ProofParam> proofParams = new LinkedList<>();

                        for (String key : referentInfo.getReferents().keySet()) {
                            Referent referent = referentInfo.getReferents().get(key);
                            proofParams.add(new ProofParam.Builder().
                                    setCredDef(VerifyProof.getInstance(getContext()).getCredentialDefinition(referent.getCredDefId())).
                                    setSchema(VerifyProof.getInstance(getContext()).getCredentialSchema(referent.getSchemaId())).
                                    setReferentInfo(new ReferentInfo(key, referent))
                                    .build());
                        }

                        String result = VerifyProof.getInstance(getContext()).verifyProofProcess(proofParams, selfAttr);
                        CaLog.d("result: " + result);
                        hideLoading();
                        if (result != null) {
                            moveToMainView();
                        }

                    } catch (WalletCoreException | UtilityException e) {
                        CaUtil.showErrorDialog(getContext(), e.getMessage());
                    }
                });
            }
        });
    }

    public void showLoading() {
        new Thread(() -> getActivity().runOnUiThread(() -> {
            progressCircle.show();
        })).start();
    }

    public void hideLoading() {
        new Thread(() -> getActivity().runOnUiThread(() -> {
            progressCircle.dismiss();
        })).start();
    }

    public void moveToMainView() {
        new Thread(() -> getActivity().runOnUiThread(() -> {
            Bundle bundle = new Bundle();
            bundle.putString("type", Constants.TYPE_VERIFY);
            navController.navigate(R.id.action_VerifyFragment_to_resultFragment2, bundle);
        })).start();
    }

    public void drawAvailableReferent() throws WalletCoreException, WalletException, UtilityException, ExecutionException, InterruptedException {

        new Thread(()->{
            try {
                availableReferent = WalletApi.getInstance(getContext()).searchZkpCredentials(VerifyProof.getInstance(getContext()).hWalletToken, proofRequestProfile.getProfile().proofRequest);
            } catch (WalletCoreException | UtilityException | WalletException e) {
                ContextCompat.getMainExecutor(getActivity()).execute(()  -> {
                    CaUtil.showErrorDialog(getActivity(), e.getMessage());
                });
                return;
            }

            CaLog.d("availableReferent >>>>>>>>>> " + GsonWrapper.getGsonPrettyPrinting().toJson(availableReferent));

            Map attr_ref_map = availableReferent.getAttrReferent();
            Map predicate_ref_map = availableReferent.getPredicateReferent();
            Map self_ref_map = availableReferent.getSelfAttrReferent();

            attr_ref_ArrayList = new ArrayList<>(attr_ref_map.values());
            if (!attr_ref_ArrayList.isEmpty()) {
                new Thread(() -> getActivity().runOnUiThread(() -> {
                    textView_attributes.setVisibility(View.VISIBLE);
                })).start();
            }
            predicate_ref_ArrayList = new ArrayList<>(predicate_ref_map.values());
            if (!predicate_ref_ArrayList.isEmpty()) {
                new Thread(() -> getActivity().runOnUiThread(() -> {
                    textView_predicates.setVisibility(View.VISIBLE);
                })).start();
            }
            self_attr_ref_ArrayList = new ArrayList<>(self_ref_map.values());
            if (!self_attr_ref_ArrayList.isEmpty()) {
                new Thread(() -> getActivity().runOnUiThread(() -> {
                    textView_self_attributes.setVisibility(View.VISIBLE);
                })).start();
            }
            ProofRequest proofRequest = proofRequestProfile.getProfile().getProofRequest();

            String attrCredDefId = CaUtil.findAttributeNameByCredDefId(proofRequest.getRequestedAttributes());
            CredentialDefinition credentialDefinitionForAttr = CaUtil.getCredentialDefinition(getActivity().getApplicationContext(), attrCredDefId);
            CredentialSchema schemaForAttr = CaUtil.getCredentialSchema(getActivity().getApplicationContext(), credentialDefinitionForAttr.getSchemaId());

            attrRefAdapter = new AttrRefAdapter();
            for (int i = 0; i < attr_ref_ArrayList.size(); i++) {
                AttrReferent attrReferent = GsonWrapper.getGson().fromJson(GsonWrapper.getGsonPrettyPrinting().toJson(attr_ref_ArrayList.get(i)), AttrReferent.class);
                attrRefAdapter.addItem(CaUtil.getAttributeCaptionValue(getActivity().getApplicationContext(), schemaForAttr.getId(), attrReferent.getName()), attrReferent.isCheckRevealed());
            }

            predicateRefAdapter = new PredicateRefAdapter();
            for (int i = 0; i < predicate_ref_ArrayList.size(); i++) {
                PredicateReferent predicateReferent = GsonWrapper.getGson().fromJson(GsonWrapper.getGsonPrettyPrinting().toJson(predicate_ref_ArrayList.get(i)), PredicateReferent.class);
                predicateRefAdapter.addItem(CaUtil.getAttributeCaptionValue(getActivity().getApplicationContext(), schemaForAttr.getId(), predicateReferent.getName()), predicateReferent.isCheckRevealed());
            }

            selfattrRefAdapter = new SelfAttrRefAdapter();
            for (int i = 0; i < self_attr_ref_ArrayList.size(); i++) {
                AttrReferent selfAttrReferent = GsonWrapper.getGson().fromJson(GsonWrapper.getGsonPrettyPrinting().toJson(self_attr_ref_ArrayList.get(i)), AttrReferent.class);
                selfattrRefAdapter.addItem(selfAttrReferent.getName());
            }
            getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    attrRefAdapter.notifyDataSetChanged();
                    listView_attr.setAdapter(attrRefAdapter);

                    predicateRefAdapter.notifyDataSetChanged();
                    listView_predicates.setAdapter(predicateRefAdapter);

                    selfattrRefAdapter.notifyDataSetChanged();
                    listView_self_attr.setAdapter(selfattrRefAdapter);
                }
            });
        }).start();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ATTR_REF_REQUEST_CODE) {

            if (resultCode == Activity.RESULT_OK) {
                // callback data
                selectedCredentialId = data.getStringExtra("credentialId");
                selectedRaw = data.getStringExtra("raw");
                pos = data.getIntExtra("pos", 0);

                AttrReferent attrReferent = GsonWrapper.getGson().fromJson(GsonWrapper.getGsonPrettyPrinting().toJson(attr_ref_ArrayList.get(pos)), AttrReferent.class);
                Map attr_ref_map = availableReferent.getAttrReferent();

                View view = listView_attr.getChildAt(pos);
                TextView textViewTitle = view.findViewById(R.id.textView_attr_ref_item_title);

                ProofRequest proofRequest = proofRequestProfile.getProfile().getProofRequest();
                String attrCredDefId = CaUtil.findAttributeNameByCredDefId(proofRequest.getRequestedAttributes());
                CredentialDefinition credentialDefinitionForAttr = CaUtil.getCredentialDefinition(getActivity().getApplicationContext(), attrCredDefId);
                CredentialSchema schemaForAttr = CaUtil.getCredentialSchema(getActivity().getApplicationContext(), credentialDefinitionForAttr.getSchemaId());

                textViewTitle.setText(CaUtil.getAttributeCaptionValue(getActivity().getApplicationContext(), schemaForAttr.getId(), attrReferent.getName()));

                TextView textViewSubTitle = view.findViewById(R.id.textView_attr_ref_item_subtitle);

                CaLog.d("selectedRaw: "+selectedRaw);
                textViewSubTitle.setTextColor(Color.parseColor("#212121"));

                textViewSubTitle.setText(selectedRaw);
                CheckBox checkBox = view.findViewById(R.id.checkBox_attr_ref);
                List keys = new ArrayList(attr_ref_map.keySet());
                for (int i = 0; i < selectedAttrReferent.size(); i++) {
                    if (selectedAttrReferent.get(i).getReferentName().equals(attrReferent.getName())) {
                        selectedAttrReferent.remove(i);
                    }
                }

                selectedAttrReferent.add(new UserReferent.Builder().
                        setReferentKey((String) keys.get(pos)).
                        setReferentName(attrReferent.getName()).
                        setRaw(selectedRaw).
                        setCredentialId(selectedCredentialId).build());
            }
        } else if (requestCode == PREDICATE_REF_REQUEST_CODE) {

            if (resultCode == Activity.RESULT_OK) {

                selectedCredentialId = data.getStringExtra("credentialId");
                selectedRaw = data.getStringExtra("raw");
                pos = data.getIntExtra("pos", 0);

                PredicateReferent predicateReferent = GsonWrapper.getGson().fromJson(GsonWrapper.getGsonPrettyPrinting().toJson(predicate_ref_ArrayList.get(pos)), PredicateReferent.class);
                Map predicate_ref_map = availableReferent.getPredicateReferent();
                List keys = new ArrayList(predicate_ref_map.keySet());

                View view = listView_predicates.getChildAt(pos);
                TextView textViewTitle = view.findViewById(R.id.textView_predicate_ref_item_title);


                ProofRequest proofRequest = proofRequestProfile.getProfile().getProofRequest();

                String attrCredDefId = CaUtil.findAttributeNameByCredDefId(proofRequest.getRequestedAttributes());
                CredentialDefinition credentialDefinitionForAttr = CaUtil.getCredentialDefinition(getActivity().getApplicationContext(), attrCredDefId);
                CredentialSchema schemaForAttr = CaUtil.getCredentialSchema(getActivity().getApplicationContext(), credentialDefinitionForAttr.getSchemaId());

                textViewTitle.setText(CaUtil.getAttributeCaptionValue(getActivity().getApplicationContext(), schemaForAttr.getId(), predicateReferent.getName()));

                TextView textViewSubTitle = view.findViewById(R.id.textView_predicate_ref_item_subtitle);
                if (selectedRaw.equals("* Tap to select")) {
                    textViewSubTitle.setTextColor(Color.parseColor("#FF0000"));
                } else {
                    textViewSubTitle.setTextColor(Color.parseColor("#212121"));
                }

                textViewSubTitle.setText(selectedRaw);

                for (int i = 0; i < selectedUserReferent.size(); i++) {
                    if (selectedUserReferent.get(i).getReferentName().equals(predicateReferent.getName())) {
                        selectedUserReferent.remove(i);
                    }
                }

                selectedUserReferent.add(new UserReferent.Builder().
                        setReferentKey((String) keys.get(pos)).
                        setReferentName(predicateReferent.getName()).
                        setRaw(selectedRaw).
                        setCredentialId(selectedCredentialId).build());
            }
        }
    }
}