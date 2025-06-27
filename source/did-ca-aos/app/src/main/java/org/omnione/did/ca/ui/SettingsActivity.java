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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.omnione.did.ca.R;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.config.Preference;
import org.omnione.did.ca.settings.SettingListViewAdapter;
import org.omnione.did.ca.ui.common.CustomDialog;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.exception.WalletCoreException;

public class SettingsActivity extends AppCompatActivity {
//    int cnt = 0;
    ListView listView;
    SettingListViewAdapter adapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        listView = findViewById(R.id.listView);

        adapter = new SettingListViewAdapter();
        setListView();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if(i == 0){
                    //wallet delete : hidden
//                    cnt++;
//                    if(cnt == 2) {
//                        try {
//                            WalletApi walletApi = WalletApi.getInstance(SettingsActivity.this);
//                            walletApi.deleteWallet();
//                            Preference.deleteAllPref(SettingsActivity.this);
//                            Toast.makeText(SettingsActivity.this, "wallet delete",Toast.LENGTH_SHORT).show();
//                            cnt = 0;
//                        } catch (WalletCoreException e) {
//                            CaUtil.showErrorDialog(SettingsActivity.this, "[error] Wallet instance creation fail");
//                        }
//                    }
                }
                else if (i == 1 ){
                    //showDialog(Preference.loadVerifierUrl(SettingsActivity.this), Constants.PREFERENCE_VERIFIER_URL);
                } else if (i == 2) {
                    String copyText = Preference.getDID(SettingsActivity.this);
                    ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    ClipData clipData = ClipData.newPlainText("copyText",copyText);
                    clipboardManager.setPrimaryClip(clipData);
                    Toast.makeText(getApplicationContext(),"The DID has been copied to the clipboard.", Toast.LENGTH_SHORT).show();
                } else if (i == 3) {
                    startActivity(new Intent(SettingsActivity.this, SettingsExpandableActivity.class));
                    finish();
                } else if (i == 4) {
                    Toast.makeText(SettingsActivity.this,"did document update", Toast.LENGTH_SHORT).show();
                } else if (i == 5) {
                    Toast.makeText(SettingsActivity.this,"did document restore", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
    private void setListView() {
        listView.setAdapter(adapter);
        adapter.addItem("TAS URL", Preference.loadTasUrl(this));
        adapter.addItem("Verifier URL", Preference.loadVerifierUrl(this));
        if(Preference.getDID(this).isEmpty())
            adapter.addItem("DID","not registered");
        else
            adapter.addItem("DID",Preference.getDID(this));

        adapter.addItem("User Authentication settings","Provides management of authentication methods.");
//        adapter.addItem("DID Document Update","");
//        adapter.addItem("DID Document Restore","");
    }
    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void showDialog(String url, String entity){
        CustomDialog customDialog = new CustomDialog(SettingsActivity.this, Constants.DIALOG_INPUT_TYPE);
        customDialog.setMessage("Input a Url.");
        customDialog.setInput(url);
        customDialog.setDialogListener(new CustomDialog.CustomDialogInterface() {
            @Override
            public void yesBtnClicked(String input) {
                if(entity.equals(Constants.PREFERENCE_TAS_URL)){
                    Preference.saveTasUrl(SettingsActivity.this, input);
                }
                if(entity.equals(Constants.PREFERENCE_VERIFIER_URL)){
                    Preference.saveVerifierUrl(SettingsActivity.this, input);
                }
                adapter.removeAll();
                listView.setAdapter(adapter);
                setListView();
            }
            @Override
            public void noBtnClicked(String input) {
            }
        });

        customDialog.show();
    }


}