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

package org.omnione.did.ca.ui.user;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
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

import org.omnione.did.ca.ui.PinActivity;
import org.omnione.did.ca.R;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.config.Preference;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.protocol.user.RegUser;
import org.omnione.did.ca.ui.common.CustomDialog;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.datamodel.protocol.P210ResponseVo;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.wallet.WalletApi;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;


public class StepFragment extends Fragment {
    NavController navController;
    Activity activity;
    int step = Constants.STEP1;
    ActivityResultLauncher<Intent> pinActivityResultLauncher;
    String txId;
    TextView stepTitle1, stepTitle2, stepTitle3;
    TextView stepBody1, stepBody2, stepBody3;
    TextView progressText1, progressText2, progressText3;
    ImageView progressImage1, progressImage2, progressImage3;
    ImageView progressLine1, progressLine2;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState){
        return inflater.inflate(R.layout.fragment_step, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);
        stepTitle1 = view.findViewById(R.id.stepTitle1);
        stepTitle2 = view.findViewById(R.id.stepTitle2);
        stepTitle3 = view.findViewById(R.id.stepTitle3);
        stepBody1 = view.findViewById(R.id.stepBody1);
        stepBody2 = view.findViewById(R.id.stepBody2);
        stepBody3 = view.findViewById(R.id.stepBody3);
        progressImage1 = view.findViewById(R.id.progressImage1);
        progressImage2 = view.findViewById(R.id.progressImage2);
        progressImage3 = view.findViewById(R.id.progressImage3);
        progressText1 = view.findViewById(R.id.progressText1);
        progressText2 = view.findViewById(R.id.progressText2);
        progressText3 = view.findViewById(R.id.progressText3);
        progressLine1 = view.findViewById(R.id.progressLine1);
        progressLine2 = view.findViewById(R.id.progressLine2);
        if(Preference.getInit(activity)) {
            return;
        }
        if(getArguments() != null){
            step = requireArguments().getInt("step");
            initStep(step);
        } else {
            if (Preference.getUsernameForDemo(activity).length() != 0) {
                try {
                    WalletApi walletApi = WalletApi.getInstance(activity);
                    if (walletApi.isSavedKey(Constants.KEY_ID_PIN)) {
                        //step3
                        step = Constants.STEP3;
                        initStep(Constants.STEP3);
                        step3();
                    } else {
                        //step2
                        step = Constants.STEP2;
                        initStep(Constants.STEP2);
                    }
                } catch (WalletCoreException | UtilityException | WalletException e) {
                    CaLog.e("step error : " + e.getMessage());
                    CaUtil.showErrorDialog(activity, e.getMessage());
                }

            }
        }

        Button button1 = view.findViewById(R.id.button);
        button1.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(step == Constants.STEP1) {
                    Bundle bundle = new Bundle();
                    bundle.putInt("type", Constants.WEBVIEW_USER_INFO);
                    navController.navigate(R.id.action_stepFragment_to_webviewFragment, bundle);
                } else if(step == Constants.STEP2){
                    // step2 : pin authentication
                    regUserPreProcess();
                } else if(step == Constants.STEP3){
                    // step3 : pin key signing
                    Intent intent = new Intent(getContext(), PinActivity.class);
                    intent.putExtra(Constants.INTENT_IS_REGISTRATION, false);
                    intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_USE_KEY);
                    pinActivityResultLauncher.launch(intent);
                }
            }
        });

        pinActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            try {
                                String pin = result.getData().getStringExtra("pin");
                                RegUser regUser = RegUser.getInstance(activity);
                                if(result.getData().getIntExtra("reg", 0) == Constants.PIN_TYPE_REG_KEY) {
                                    CaLog.d( "Register sign pin : " + pin);
                                    regUser.regUser(Constants.PIN_TYPE_REG_KEY, pin, StepFragment.this, navController);
                                    showDialog();
                                } else if(result.getData().getIntExtra("reg", 0) == Constants.PIN_TYPE_USE_KEY) {
                                    CaLog.d( "Authenticate sign Pin : " + pin);
                                    regUser.regUser(Constants.PIN_TYPE_USE_KEY, pin, StepFragment.this, navController);

                                }
                            } catch (WalletException | WalletCoreException | UtilityException e){
                                CaLog.e("generateKeyPair error : " + e.getMessage());
                                CaUtil.showErrorDialog(activity, e.getMessage());
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
        requireActivity().getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
    }

    @Override
    public void onResume() {
        super.onResume();
        if(getArguments() != null) {
            if (requireArguments().getInt("step") == Constants.STEP3) {
                step = Constants.STEP3;
                initStep(step);
            }
        }
    }

    private final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            showExitDialog();
        }
    };

    public void step3() {
        RegUser regUser = RegUser.getInstance(activity);
        try {
            txId = MessageUtil.deserialize(regUser.regUserPreProcess().get(), P210ResponseVo.class).getTxId();
        } catch (ExecutionException | InterruptedException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CompletionException && cause.getCause() instanceof CommunicationException) {
                ContextCompat.getMainExecutor(activity).execute(() -> {
                    CaUtil.showErrorDialog(activity, cause.getCause().getMessage());
                });
            }
        }
    }
    public void regUserPreProcess(){
        RegUser regUser = RegUser.getInstance(activity);
        try {
            txId = MessageUtil.deserialize(regUser.regUserPreProcess().get(), P210ResponseVo.class).getTxId();
        } catch (ExecutionException | InterruptedException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CompletionException && cause.getCause() instanceof CommunicationException) {
                ContextCompat.getMainExecutor(activity).execute(() -> {
                    CaUtil.showErrorDialog(activity, cause.getCause().getMessage());
                });
            }
        }
        Intent intent = new Intent(getContext(), PinActivity.class);
        intent.putExtra(Constants.INTENT_IS_REGISTRATION, true);
        intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_REG_KEY);
        pinActivityResultLauncher.launch(intent);
    }

    private void showDialog() {
        CustomDialog customDialog = new CustomDialog(activity, Constants.DIALOG_CONFIRM_TYPE);
        customDialog.setMessage("Would you like to register\n additional fingerprints?");
        customDialog.setCancelable(false);
        customDialog.setDialogListener(new CustomDialog.CustomDialogInterface() {
            @Override
            public void yesBtnClicked(String btnName) {
                RegUser regUser = RegUser.getInstance(activity);
                regUser.createHolderDocByBio(navController);
            }
            @Override
            public void noBtnClicked(String btnName) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        RegUser regUser = RegUser.getInstance(activity);
                        regUser.createHolderDocByPin(navController);
                    }
                }).start();
            }
        });
        customDialog.show();
    }

    private void initStep(int step){
        if(step == Constants.STEP2) {
            stepTitle1.setTextColor(Color.parseColor("#ed8202"));
            stepTitle2.setTextColor(Color.parseColor("#ed8202"));
            progressImage1.setImageResource(R.drawable.step_after);
            progressImage2.setImageResource(R.drawable.step_active);
            progressLine1.setImageResource(R.drawable.line_blue);
            progressText1.setTextColor(Color.parseColor("#ed8202"));
            progressText2.setTextColor(Color.parseColor("#ffffff"));
        } else if(step == Constants.STEP3){
            stepTitle1.setTextColor(Color.parseColor("#ed8202"));
            stepTitle2.setTextColor(Color.parseColor("#ed8202"));
            stepTitle3.setTextColor(Color.parseColor("#ed8202"));
            progressImage1.setImageResource(R.drawable.step_after);
            progressImage2.setImageResource(R.drawable.step_after);
            progressImage3.setImageResource(R.drawable.step_active);
            progressLine1.setImageResource(R.drawable.line_blue);
            progressLine2.setImageResource(R.drawable.line_blue);
            progressText1.setTextColor(Color.parseColor("#ed8202"));
            progressText2.setTextColor(Color.parseColor("#ed8202"));
            progressText3.setTextColor(Color.parseColor("#ffffff"));
        }
    }

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
