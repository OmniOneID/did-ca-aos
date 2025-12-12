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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

import org.omnione.did.ca.R;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.databinding.ActivitySettingsExpandableBinding;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.ui.viewmodel.AddBioViewModel;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.util.UserAuthHelper;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.bioprompthelper.BioPromptHelper;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SettingsExpandableActivity extends BaseActivity {
    ActivityResultLauncher<Intent> pinActivityResultLauncher;
    ActivityResultLauncher<Intent> pinActivityChangeSigningPinResultLauncher;
    ActivityResultLauncher<Intent> pinActivityChangeUnlockPinResultLauncher;
    private AddBioViewModel viewModel;
    private ActivitySettingsExpandableBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySettingsExpandableBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(AddBioViewModel.class);
        binding.setLifecycleOwner(this);
        binding.setViewModel(viewModel);
        binding.setHandle(this);

        initialize();

        observeViewModel();
    }

    private void observeViewModel() {

        viewModel.getPreProcessDidUpdateLiveData().observe(this, result -> {
            switch (result.status) {
                case LOADING:
                    CaLog.d("UpdateBioActivity preProcess LOADING");
                    showProgress();
                    break;
                case SUCCESS:
                    CaLog.d("UpdateBioActivity preProcess SUCCESS");

                    Intent intent = new Intent(SettingsExpandableActivity.this, PinActivity.class);
                    intent.putExtra(Constants.INTENT_IS_REGISTRATION, false);
                    intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_USE_KEY);
                    pinActivityResultLauncher.launch(intent);
                    dismissProgress();
                    break;
                case ERROR:
                    CaLog.d("UpdateBioActivity preProcess ERROR");
                    dismissProgress();
                    break;
            }
        });

        viewModel.getProcessDidUpdateLiveData().observe(this, result -> {
            switch (result.status) {
                case LOADING:
                    CaLog.d("DID UpdateBioActivity process LOADING");
                    showProgress();
                    break;
                case SUCCESS:
                    CaLog.d("DID UpdateBioActivity process SUCCESS");
                    dismissProgress();

                    try {
                        walletApi.saveDocument();
                        Toast.makeText(SettingsExpandableActivity.this, "reg fingerprint completed", Toast.LENGTH_SHORT).show();
                    } catch (WalletException | UtilityException | WalletCoreException e) {
                        CaLog.d("DID UpdateBioActivity process exception : " + e.getMessage());
                        dismissProgress();
                    }
                    break;
                case ERROR:
                    CaLog.d("DID UpdateBioActivity process ERROR : " + result.errorMessage);
                    dismissProgress();
                    break;
            }
        });
    }

    private void initialize() {
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
        childBA.put("data", "Setting up a fingerprint for Signing");
        childListB.add(childBA);

//        HashMap<String, String> childBB = new HashMap<>();
//        childBB.put("data", "Setting up a fingerprint for Unlock");
//        childListB.add(childBB);

        childData.add(childListB);

        SimpleExpandableListAdapter adapter = new SimpleExpandableListAdapter(
                this, groupData,
                android.R.layout.simple_expandable_list_item_1,
                new String[]{"group"}, new int[]{android.R.id.text1},

                childData, android.R.layout.simple_expandable_list_item_1,
                new String[]{"data"}, new int[]{android.R.id.text1});

        binding.expandableListView.setAdapter(adapter);
        binding.expandableListView.setOnChildClickListener((parent, v, groupPosition, childPosition, id) -> {
            if (groupPosition == 0) {
                if (childPosition == 0) {
                    showProgress();
                    Intent intent = new Intent(SettingsExpandableActivity.this, PinActivity.class);
                    intent.putExtra(Constants.INTENT_IS_REGISTRATION, false);
                    intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_CHANGE_SIGNING_PIN);
                    pinActivityChangeSigningPinResultLauncher.launch(intent);
                } else if (childPosition == 1) {
                    showProgress();
                    Intent intent = new Intent(SettingsExpandableActivity.this, PinActivity.class);
                    intent.putExtra(Constants.INTENT_IS_REGISTRATION, false);
                    intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_CHANGE_UNLOCK_PIN);
                    pinActivityChangeUnlockPinResultLauncher.launch(intent);
                }
                // Fingerprint Settings
            } else if (groupPosition == 1) {
                // Setting up a fingerprint for signing
                if (childPosition == 0) {
                    new Thread(() -> {
                        if (UserAuthHelper.isBiometricAuthAvailable(SettingsExpandableActivity.this, true)) {
                            ContextCompat.getMainExecutor(SettingsExpandableActivity.this).execute(() -> {
                                CaUtil.showErrorDialog(SettingsExpandableActivity.this, "already reg bio");
                            });
                        } else {
                            viewModel.preProcess(SettingsExpandableActivity.this);
                        }
                    }).start();
                }
                // Setting up a fingerprint for lock
//                else if (childPosition == 1)
//                    Toast.makeText(SettingsExpandableActivity.this, "unlock fingerprint", Toast.LENGTH_SHORT).show();
            }
            return false;
        });

        pinActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                String pin = result.getData().getStringExtra("pin");
                CaLog.d("Authenticate sign Pin : " + pin);

                try {

                    WalletApi walletApi = WalletApi.getInstance(context);
                    walletApi.setBioPromptListener(new BioPromptHelper.BioPromptInterface() {
                        @Override
                        public void onSuccess(String result) {
                            CaLog.d("[onSuccess] bio");
                            Map<String, String> requestData = new HashMap<>();
                            requestData.put("pin", pin);
                            viewModel.process(context, requestData);
                        }
                        @Override
                        public void onError(String result) {
                            CaLog.d("[onError] Authentication failed.\nPlease try again later.");
                            dismissProgress();
                        }
                        @Override
                        public void onCancel(String result) {
                            CaLog.d("[onCancel] UpdateUser registerBioKey onCancel : " + result);
                            dismissProgress();
                        }
                        @Override
                        public void onFail(String result) {
                            CaLog.d("[onFail] UpdateUser registerBioKey onFail : " + result);
                            dismissProgress();
                        }
                    });

                    try {
                        walletApi.registerBioKey(SettingsExpandableActivity.this);
                    } catch (WalletException e) {
                        CaLog.d("WalletException :: Failed to create biometric key. : " + e.getMessage());
                    }

                } catch (WalletCoreException e) {
                    CaLog.d("registerBioKey error: "+e.getMessage());
                    dismissProgress();
                }

            } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                CaLog.e("pin authentication fail");
                CaUtil.showErrorDialog(context, "[Information] canceled by user");
            }
        });

        pinActivityChangeSigningPinResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
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
                } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                    CaUtil.showErrorDialog(SettingsExpandableActivity.this, "[Information] canceled by user");
                }
                dismissProgress();
            }
        );
        pinActivityChangeUnlockPinResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                WalletApi walletApi = WalletApi.getInstance(SettingsExpandableActivity.this);
                                String oldPassCode = result.getData().getStringExtra("oldPassCode");
                                String newPassCode = result.getData().getStringExtra("newPassCode");
                                walletApi.changeLock(oldPassCode, newPassCode);
                            } catch (WalletCoreException | UtilityException |
                                     WalletException e) {
                                ContextCompat.getMainExecutor(SettingsExpandableActivity.this).execute(() -> {
                                    CaUtil.showErrorDialog(SettingsExpandableActivity.this, e.getMessage());
                                });
                            }
                        }
                    }).start();

                } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                    CaUtil.showErrorDialog(SettingsExpandableActivity.this, "[Information] canceled by user");
                }
                dismissProgress();
            }
        );
    }
}