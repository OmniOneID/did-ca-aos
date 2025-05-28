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
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import org.omnione.did.ca.R;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.zkp.referent.AttrRefAdapter;
import org.omnione.did.ca.zkp.referent.PredicateRefAdapter;
import org.omnione.did.ca.zkp.referent.SelfAttrRefAdapter;
import org.omnione.did.ca.util.DialogUtil;
import org.omnione.did.sdk.datamodel.protocol.P310ZkpResponseVo;
import org.omnione.did.sdk.datamodel.zkp.AttrReferent;
import org.omnione.did.sdk.datamodel.zkp.AvailableReferent;
import org.omnione.did.sdk.datamodel.zkp.CredentialDefinition;
import org.omnione.did.sdk.datamodel.zkp.CredentialSchema;
import org.omnione.did.sdk.datamodel.zkp.PredicateReferent;
import org.omnione.did.sdk.datamodel.zkp.ProofRequest;
import org.omnione.did.sdk.datamodel.zkp.Referent;
import org.omnione.did.sdk.datamodel.zkp.UserReferent;
import org.omnione.did.sdk.datamodel.util.GsonWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class VerifyFragment extends Fragment implements VerifyConstants.View {

    private static final String ZKP_TAG = VerifyFragment.class.getName();

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

    NavController navController;

    private Map<String, String> selfAttr = new HashMap<String, String>();

    private P310ZkpResponseVo proofRequestProfileVo;

    private RelativeLayout progress;

    private VerifyConstants.Presenter presenter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_verify, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);
        proofRequestProfileVo = GsonWrapper.getGson().fromJson(requireArguments().getString("proofRequestProfileVo"), P310ZkpResponseVo.class);

        presenter = new VerifyPresenter(getActivity().getApplicationContext(), this);
        initUI(view);
        presenter.setProofRequestProfile(proofRequestProfileVo);
    }

    private void initUI(View view) {

        listView_attr = view.findViewById(R.id.listView_attr);
        listView_predicates = view.findViewById(R.id.listView_predicates);
        listView_self_attr = view.findViewById(R.id.listView_self_attr);

        progress = view.findViewById(R.id.main_progress);

        cancelBtn = (Button) view.findViewById(R.id.cancelBtn);
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


        okBtn = view.findViewById(R.id.okBtn);
        okBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AvailableReferent availableReferent = presenter.getAvailableReferent();

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

                    selectedUserReferent.add(new UserReferent.Builder()
                                            .setReferentKey((String) keys.get(i))
                                            .setReferentName(selfAttrReferent.getName())
                                            .build());

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
                    CaLog.d( "isRevealed : " + selectedAttrReferent.get(i).isRevealed());
                    View childView = listView_attr.getChildAt(i);
                    CheckBox checkBox = childView.findViewById(R.id.checkBox_attr_ref);
                    CaLog.d("selectedAttrReferent " + i +" : "+checkBox.isChecked());
                    selectedUserReferent.add(new UserReferent.Builder()
                            .setReferentKey((String) selectedAttrReferent.get(i).getReferentKey())
                            .setReferentName(selectedAttrReferent.get(i).getReferentName())
                            .setRaw(selectedAttrReferent.get(i).getRaw())
                            .setCredentialId(selectedAttrReferent.get(i).getCredentialId())
                            .setRevealed(!checkBox.isChecked())
                            .build());
                }

                CaLog.d("selectedUserReferent >>>>>>> " + GsonWrapper.getGsonPrettyPrinting().toJson(selectedUserReferent));
                presenter.requestVerify(selectedUserReferent);
            }
        });
    }

    @Override
    public void showError(String errorCode, String errorMessage) {
        ContextCompat.getMainExecutor(getActivity()).execute(()  -> {
            CaUtil.showErrorDialog(getActivity(), errorMessage);
        });
    }

    @Override
    public void showToast(final String message) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
            }
        }, 0);
    }

    @Override
    public void showLoading(final String message) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        progress.setVisibility(View.VISIBLE);
                    }
                });
            }
        }).start();
    }

    @Override
    public void hideLoading() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        progress.setVisibility(View.INVISIBLE);
                    }
                });
            }
        }).start();
    }

    @Override
    public void moveToMainView() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        navController.navigate(R.id.action_VerifyFragment_to_vcListFragment);
                    }
                });
            }
        }).start();
    }

    @Override
    public void drawAvailableReferent() {

        AvailableReferent availableReferent = presenter.getAvailableReferent();
        Map attr_ref_map = availableReferent.getAttrReferent();
        Map predicate_ref_map = availableReferent.getPredicateReferent();
        Map self_ref_map = availableReferent.getSelfAttrReferent();

        attr_ref_ArrayList = new ArrayList<>(attr_ref_map.values());
        predicate_ref_ArrayList = new ArrayList<>(predicate_ref_map.values());
        self_attr_ref_ArrayList = new ArrayList<>(self_ref_map.values());


        ProofRequest proofRequest = proofRequestProfileVo.getProofRequestProfile().getProfile().getProofRequest();

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
    }

    @Override
    public Map<String, String> getSelfAttribute() {
        return selfAttr;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ATTR_REF_REQUEST_CODE) {

            if (resultCode == Activity.RESULT_OK) {
                AvailableReferent availableReferent = presenter.getAvailableReferent();
                // callback data
                selectedCredentialId = data.getStringExtra("credentialId");
                selectedRaw = data.getStringExtra("raw");
                pos = data.getIntExtra("pos", 0);

                AttrReferent attrReferent = GsonWrapper.getGson().fromJson(GsonWrapper.getGsonPrettyPrinting().toJson(attr_ref_ArrayList.get(pos)), AttrReferent.class);
                Map attr_ref_map = availableReferent.getAttrReferent();

                View view = listView_attr.getChildAt(pos);
                TextView textViewKey = view.findViewById(R.id.textView_attr_ref_item_title);
                textViewKey.setText(attrReferent.getName());

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

                AvailableReferent availableReferent = presenter.getAvailableReferent();

                selectedCredentialId = data.getStringExtra("credentialId");
                selectedRaw = data.getStringExtra("raw");
                pos = data.getIntExtra("pos", 0);

                PredicateReferent predicateReferent = GsonWrapper.getGson().fromJson(GsonWrapper.getGsonPrettyPrinting().toJson(predicate_ref_ArrayList.get(pos)), PredicateReferent.class);
                Map predicate_ref_map = availableReferent.getPredicateReferent();
                List keys = new ArrayList(predicate_ref_map.keySet());

                View view = listView_predicates.getChildAt(pos);
                TextView textViewTitle = view.findViewById(R.id.textView_predicate_ref_item_title);
                
                textViewTitle.setText(predicateReferent.getName());

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