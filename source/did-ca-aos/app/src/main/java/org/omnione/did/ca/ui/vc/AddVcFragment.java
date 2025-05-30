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

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import org.omnione.did.ca.R;
import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.HttpUrlConnection;
import org.omnione.did.ca.network.protocol.vc.IssueVc;
import org.omnione.did.ca.ui.common.ProgressCircle;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.datamodel.vc.issue.VCPlan;
import org.omnione.did.sdk.datamodel.vc.issue.VCPlanList;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class AddVcFragment extends Fragment {
    NavController navController;
    Activity activity;
    VcListAdapter adapter;
    String vcPlanStr = "";
    VCPlanList vcPlanList;
    ActivityResultLauncher<Intent> qrActivityResultLauncher;

    private ProgressCircle progressCircle;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_vc, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        navController = Navigation.findNavController(view);

        progressCircle = new ProgressCircle(activity);

        try {
            vcPlanStr = getVcPlan().get();
        } catch (ExecutionException | InterruptedException e) {
            CaLog.e("getVcPlan error " + e.getMessage());
            ContextCompat.getMainExecutor(activity).execute(()  -> {
                CaUtil.showErrorDialog(activity, e.getMessage());
            });
        }
        vcPlanList = MessageUtil.deserialize(vcPlanStr, VCPlanList.class);
        GridView gridView = (GridView) view.findViewById(R.id.gridView);
        String vcForm = "data:image/png;base64,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";
        vcForm = CaUtil.drawableToBase64(activity,R.drawable.default_vc);

        adapter = new VcListAdapter();
        //adapter.addItem(new VcListItem(vcPlan.getName(), "15 Feb 2024", vcForm));
        for(VCPlan vcPlan : vcPlanList.getItems()) {
            if(vcPlan.getName().contains("National"))
                vcForm = CaUtil.drawableToBase64(activity,R.drawable.nation_id_vc);
            else if(vcPlan.getName().contains("Driver"))
                vcForm = CaUtil.drawableToBase64(activity,R.drawable.default_vc);
            else
                vcForm = CaUtil.drawableToBase64(activity,R.drawable.default_vc);
            adapter.addItem(new AddVcListItem(vcPlan.getName(), vcPlan.getDescription(), vcForm));
        }
        gridView.setAdapter(adapter);
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                progressCircle.show();

                IssueVc issueVc = IssueVc.getInstance(activity);
                String issueProfile;
                try {
                    issueProfile = issueVc.issueVcPreProcess(vcPlanList.getItems().get(position).getVcPlanId(), vcPlanList.getItems().get(position).getAllowedIssuers().get(0), null).get();
                } catch (ExecutionException | InterruptedException e) {
                    ContextCompat.getMainExecutor(activity).execute(()  -> {
                        CaUtil.showErrorDialog(activity, e.getMessage());
                    });
                    progressCircle.dismiss();
                    return;
                }
                Bundle bundle = new Bundle();
                bundle.putString("result", issueProfile);
                bundle.putString("type", "user_init");
                bundle.putString("vcSchemaId", vcPlanList.getItems().get(position).getCredentialSchema().getId());

                progressCircle.dismiss();

                navController.navigate(R.id.action_addVcFragment_to_profileFragment, bundle);
            }
        });
        Button cancelBtn = (Button) view.findViewById(R.id.cancelBtn);
        cancelBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                navController.navigate(R.id.action_addVcFragment_to_vcListFragment
                );
            }
        });

    }

    static class VcListAdapter extends BaseAdapter {
        ArrayList<AddVcListItem> items = new ArrayList<>();

        @Override
        public int getCount() {
            return items.size();
        }

        public void addItem(AddVcListItem item) {
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
            AddVcListItemView view = null;
            if (convertView == null) {
                view = new AddVcListItemView(parent.getContext());
            } else {
                view = (AddVcListItemView) convertView;
            }

            AddVcListItem item = items.get(position);

            view.setTitle(item.getTitle());
            view.setDescription(item.getDescription());
            view.setImage(item.getImg());

            return view;
        }

    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (Activity) context;
    }

    public CompletableFuture<String> getVcPlan(){
        String api = "/list/api/v1/vcplan/list"; //VC Plan
        HttpUrlConnection httpUrlConnection = new HttpUrlConnection();

        return CompletableFuture.supplyAsync(() -> httpUrlConnection.send(activity, Config.TAS_URL + api, "GET", ""))
                .thenCompose(CompletableFuture::completedFuture)
                .exceptionally(ex -> {
                    throw new CompletionException(ex);
                });
    }

}
