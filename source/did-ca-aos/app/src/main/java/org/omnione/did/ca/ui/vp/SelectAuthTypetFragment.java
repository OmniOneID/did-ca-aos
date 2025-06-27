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

package org.omnione.did.ca.ui.vp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

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
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.protocol.vc.RevokeVc;
import org.omnione.did.ca.ui.PinActivity;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.network.protocol.vp.VerifyVp;
import org.omnione.did.ca.util.CaUtil;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;


public class SelectAuthTypetFragment extends Fragment {
    NavController navController;
    Activity activity;
    ListView listView;
    SelectAuthTypeListViewAdapter adapter;
    ActivityResultLauncher<Intent> pinActivityVerifyResultLauncher;
    ActivityResultLauncher<Intent> pinActivityRevokeResultLauncher;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_select_auth_type, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);
        listView = view.findViewById(R.id.listView);
        adapter = new SelectAuthTypeListViewAdapter();
        setListView();
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if(i == 0){
                    if(requireArguments().getString("type").equals(Constants.TYPE_VERIFY)) {
                        Intent intent = new Intent(getContext(), PinActivity.class);
                        intent.putExtra(Constants.INTENT_IS_REGISTRATION, false);
                        intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_USE_KEY);
                        pinActivityVerifyResultLauncher.launch(intent);
                    } else {
                        Intent intent = new Intent(getContext(), PinActivity.class);
                        intent.putExtra(Constants.INTENT_IS_REGISTRATION, false);
                        intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_USE_KEY);
                        pinActivityRevokeResultLauncher.launch(intent);
                    }
                } else if (i == 1 ){
                    if(requireArguments().getString("type").equals(Constants.TYPE_VERIFY)) {
                        VerifyVp verifyVp = VerifyVp.getInstance(activity);
                        verifyVp.authenticateBio(SelectAuthTypetFragment.this, navController);
                    } else {
                        RevokeVc revokeVc = RevokeVc.getInstance(activity);
                        revokeVc.authenticateBio(SelectAuthTypetFragment.this, navController);
                    }
                } else if (i == 2 ){
                    // pin and bio
                }
            }
        });

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
                            CaLog.e("pin authentication fail");
                            CaUtil.showErrorDialog(activity,"[Information] canceled by user");
                        }
                    }
                }
        );

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
                            CaLog.e("pin authentication fail");
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

    private void setListView() {
        listView.setAdapter(adapter);
        adapter.addItem("PIN");
        adapter.addItem("BIO");
        //adapter.addItem("PIN + BIO");
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
        navController.navigate(R.id.action_selectAuthTypetFragment_to_resultFragment, bundle);
    }

    private void revokeVc(String pin){
        RevokeVc revokeVc = RevokeVc.getInstance(activity);
        try {
            revokeVc.revokeVcProcess(pin).get();
        } catch (ExecutionException | InterruptedException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CompletionException && cause.getCause() instanceof CommunicationException) {
                CaLog.e("revoke vc error : " + e.getMessage());
                ContextCompat.getMainExecutor(activity).execute(()  -> {
                    CaUtil.showErrorDialog(activity, cause.getCause().getMessage());
                });
            } else {
                CaUtil.showErrorDialog(activity, cause.getCause().getMessage(), activity);
            }
        }
        navController.navigate(R.id.action_selectAuthTypetFragment_to_vcListFragment);
    }
}
