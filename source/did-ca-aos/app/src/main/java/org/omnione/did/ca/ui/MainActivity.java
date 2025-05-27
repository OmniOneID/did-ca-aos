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

package org.omnione.did.ca.ui;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.fragment.NavHostFragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.omnione.did.ca.R;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.config.Preference;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.protocol.vc.IssueVc;
import org.omnione.did.ca.ui.common.PayloadData;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.datamodel.offer.IssueOfferPayload;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.utility.MultibaseUtils;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private Menu mMenu;
    NavController navController;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if(Preference.getInit(this)) {
            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
            navController = navHostFragment.getNavController();
            NavGraph navGraph = navController.getNavInflater().inflate(R.navigation.nav_graph);

            Intent intent = getIntent();
            if (intent != null && intent.hasExtra("pushdata")) {
                pushProcess(navGraph, intent);
            } else {
                navGraph.setStartDestination(R.id.vcListFragment);
                navController.setGraph(navGraph, null);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.mMenu = menu;
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.settings, menu);
        mMenu.setGroupVisible(R.id.menu_group, true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if(itemId == R.id.url){
            Intent intent = new Intent( getApplicationContext(), SettingsActivity.class );
            startActivityResult.launch(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    ActivityResultLauncher<Intent> startActivityResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        boolean res = result.getData().getBooleanExtra("result", false);
                    }
                }
            });

    private void pushProcess(NavGraph navGraph, Intent intent){
        navGraph.setStartDestination(R.id.profileFragment);

        PayloadData payloadData = MessageUtil.deserialize(intent.getStringExtra("pushdata"), PayloadData.class);
        String payload = "";
        try {
            payload = new String(MultibaseUtils.decode(payloadData.getPayload()));
        } catch (UtilityException e) {
            CaLog.e( "payload decode error : " + e.getMessage());
            CaUtil.showErrorDialog(this, e.getMessage());
        }
        IssueOfferPayload offer = MessageUtil.deserialize(payload, IssueOfferPayload.class);
        IssueVc issueVc = IssueVc.getInstance(this);
        try {
            String issueProfile = issueVc.issueVcPreProcess(offer.getVcPlanId(), offer.getIssuer(), offer.getOfferId()).get();
            Bundle bundle2 = new Bundle();
            bundle2.putString("result", issueProfile);
            bundle2.putString("type", Constants.TYPE_ISSUE);
            navController.setGraph(navGraph, bundle2);
        } catch (ExecutionException | InterruptedException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CompletionException && cause.getCause() instanceof CommunicationException) {
                ContextCompat.getMainExecutor(this).execute(() -> {
                    CaUtil.showErrorDialog(this, cause.getCause().getMessage());
                });
            }
        }
    }
}