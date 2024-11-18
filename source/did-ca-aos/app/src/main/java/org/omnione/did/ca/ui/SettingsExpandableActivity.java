/*
 * Copyright 2024 OmniOne.
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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.omnione.did.ca.R;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.ui.common.ProgressCircle;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.core.bioprompthelper.BioPromptHelper;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.wallet.WalletApi;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

import java.util.ArrayList;
import java.util.HashMap;

public class SettingsExpandableActivity extends AppCompatActivity {
    int cnt = 0;
    ActivityResultLauncher<Intent> pinActivityChangeSigningPinResultLauncher;
    ActivityResultLauncher<Intent> pinActivityChangeUnlockPinResultLauncher;
    ProgressCircle progressCircle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_expandable);

        ArrayList<HashMap<String, String>> groupData = new ArrayList<>();
        ArrayList<ArrayList<HashMap<String, String>>> childData = new ArrayList<>();

        HashMap<String, String> groupA = new HashMap<>();
        groupA.put("group", "PIN Settings");

        HashMap<String, String> groupB = new HashMap<>();
        groupB.put("group", "Fingerprint Settings");

        groupData.add(groupA);
        groupData.add(groupB);

        ArrayList<HashMap<String, String>> childListA = new ArrayList<>();

        HashMap<String, String> childAA = new HashMap<>();
        childAA.put("data", "Change PIN for Signing");
        childListA.add(childAA);

        HashMap<String, String> childAB = new HashMap<>();
        childAB.put("data", "Change PIN for Unlock");
        childListA.add(childAB);

        childData.add(childListA);

        ArrayList<HashMap<String, String>> childListB = new ArrayList<>();

        HashMap<String, String> childBA = new HashMap<>();
        childBA.put("data", "Setting up a fignerprint for Signing");
        childListB.add(childBA);

        HashMap<String, String> childBB = new HashMap<>();
        childBB.put("data", "Setting up a fignerprint for Unlock");
        childListB.add(childBB);

        childData.add(childListB);

        SimpleExpandableListAdapter adapter = new SimpleExpandableListAdapter(
                this, groupData,
                android.R.layout.simple_expandable_list_item_1,
                new String[] {"group"}, new int[] { android.R.id.text1},

                childData, android.R.layout.simple_expandable_list_item_2,
                new String[] {"data"}, new int[] { android.R.id.text1 } );


        ExpandableListView listView = (ExpandableListView) findViewById(R.id.expandableListView);
        listView.setAdapter(adapter);

        listView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
                if(groupPosition == 0) {
                    if (childPosition == 0) {
                        progressCircle = new ProgressCircle(SettingsExpandableActivity.this);
                        progressCircle.show();
                        Intent intent = new Intent(SettingsExpandableActivity.this, PinActivity.class);
                        intent.putExtra(Constants.INTENT_IS_REGISTRATION, false);
                        intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_CHANGE_SIGNING_PIN);
                        pinActivityChangeSigningPinResultLauncher.launch(intent);
                    }
                    else if (childPosition == 1) {
                        
                        progressCircle = new ProgressCircle(SettingsExpandableActivity.this);
                        progressCircle.show();
                        Intent intent = new Intent(SettingsExpandableActivity.this, PinActivity.class);
                        intent.putExtra(Constants.INTENT_IS_REGISTRATION, false);
                        intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_CHANGE_UNLOCK_PIN);
                        pinActivityChangeUnlockPinResultLauncher.launch(intent);
                    }
                } else if(groupPosition == 1) {
                    if (childPosition == 0) {
                        // todo: bio key 존재 유무 체크
                        Toast.makeText(SettingsExpandableActivity.this, "signing fingerprint", Toast.LENGTH_SHORT).show();
                        try {
                            WalletApi walletApi = WalletApi.getInstance(SettingsExpandableActivity.this);
                            walletApi.setBioPromptListener(new BioPromptHelper.BioPromptInterface() {
                                @Override
                                public void onSuccess(String result) {
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                //walletApi.createHolderDIDDoc(hWalletToken); // todo: DID Doc bio키 갱신
//                                                ContextCompat.getMainExecutor(SettingsExpandableActivity.this).execute(() -> {
//                                                    Bundle bundle = new Bundle();
//                                                    bundle.putInt("step", Constants.STEP3);
//                                                    navController.navigate(R.id.action_stepFragment_self, bundle);
//                                                });
                                                CaLog.d("is bio key : " + walletApi.isSavedKey(Constants.KEY_ID_BIO));
                                                CaLog.d("get DID Document : " + walletApi.getDIDDocument(2).toJson());
                                            } catch (Exception e) {
                                                CaLog.e("bio key creation fail " + e.getMessage());
                                                ContextCompat.getMainExecutor(SettingsExpandableActivity.this).execute(() -> {
                                                    CaUtil.showErrorDialog(SettingsExpandableActivity.this, e.getMessage());
                                                });
                                            }
                                        }
                                    }).start();
                                }

                                @Override
                                public void onError(String result) {
                                    ContextCompat.getMainExecutor(SettingsExpandableActivity.this).execute(() -> {
                                        CaUtil.showErrorDialog(SettingsExpandableActivity.this, "[Error] Authentication failed.\nPlease try again later.");
                                    });
                                }

                                @Override
                                public void onCancel(String result) {
                                    ContextCompat.getMainExecutor(SettingsExpandableActivity.this).execute(() -> {
                                        CaUtil.showErrorDialog(SettingsExpandableActivity.this, "[Information] canceled by user");
                                    });
                                }

                                @Override
                                public void onFail(String result) {
                                    CaLog.e("RegUser registerBioKey onFail : " + result);
                                }
                            });
                            walletApi.registerBioKey(SettingsExpandableActivity.this);
                        } catch (Exception e) {
                            CaLog.e("bio key creation fail : " + e.getMessage());
                            ContextCompat.getMainExecutor(SettingsExpandableActivity.this).execute(() -> {
                                CaUtil.showErrorDialog(SettingsExpandableActivity.this, e.getMessage());
                            });
                        }
                    }
                    else if (childPosition == 1)
                        Toast.makeText(SettingsExpandableActivity.this, "unlock fingerprint", Toast.LENGTH_SHORT).show();
                }
                return false;
            }
        });

        pinActivityChangeSigningPinResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            //progressCircle.dismiss();
                            try {
                                WalletApi walletApi = WalletApi.getInstance(SettingsExpandableActivity.this);
                                String oldPin = result.getData().getStringExtra("oldPin");
                                String newPin = result.getData().getStringExtra("newPin");
                                walletApi.changePin(Constants.KEY_ID_PIN, oldPin, newPin);
                            } catch (WalletCoreException | UtilityException e) {
                                CaUtil.showErrorDialog(SettingsExpandableActivity.this, e.getMessage());
                            }
                        } else if(result.getResultCode() == Activity.RESULT_CANCELED){
                            CaUtil.showErrorDialog(SettingsExpandableActivity.this,"[Information] canceled by user");
                        }
                        progressCircle.dismiss();
                    }
                }
        );

        pinActivityChangeUnlockPinResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        WalletApi walletApi = WalletApi.getInstance(SettingsExpandableActivity.this);
                                        String oldPassCode = result.getData().getStringExtra("oldPassCode");
                                        String newPassCode = result.getData().getStringExtra("newPassCode");
                                        walletApi.changeLock(oldPassCode, newPassCode);
                                    } catch (WalletCoreException | UtilityException | WalletException e) {
                                        ContextCompat.getMainExecutor(SettingsExpandableActivity.this).execute(()  -> {
                                            CaUtil.showErrorDialog(SettingsExpandableActivity.this, e.getMessage());
                                        });
                                    }
                                }
                            }).start();

                        } else if(result.getResultCode() == Activity.RESULT_CANCELED){
                            CaUtil.showErrorDialog(SettingsExpandableActivity.this,"[Information] canceled by user");
                        }
                        progressCircle.dismiss();
                    }
                }
        );
    }
}