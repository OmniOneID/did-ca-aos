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
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.protocol.token.GetWalletToken;
import org.omnione.did.ca.ui.common.CustomDialog;
import org.omnione.did.ca.ui.common.ProgressCircle;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.datamodel.common.enums.WalletTokenPurpose;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class SetLockFragment extends Fragment {
    NavController navController;
    Activity activity;
    ActivityResultLauncher<Intent> pinActivityResultLauncher;
    String hWalletToken = "";
    GetWalletToken getWalletToken;
    ProgressCircle progressCircle;
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState){
        return inflater.inflate(R.layout.fragment_set_lock, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);
        getWalletToken = GetWalletToken.getInstance(activity);
        TextView title = (TextView) view.findViewById(R.id.title);
        title.setText(Constants.TITLE);

        // activity callback
        pinActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            progressCircle.dismiss();
                            Bundle bundle = new Bundle();
                            bundle.putInt("step", 2);
                            navController.navigate(R.id.action_setLockFragment_to_stepFragment, bundle);
                        } else if(result.getResultCode() == Activity.RESULT_CANCELED){
                            CaUtil.showErrorDialog(activity,"[Information] canceled by user");
                        }
                    }
                }
        );
        showDialog();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (Activity) context;
    }

    private void showDialog() {
        CustomDialog customDialog = new CustomDialog(activity, Constants.DIALOG_CONFIRM_TYPE);
        customDialog.setMessage(Constants.DIALOG_MESSAGE_SET_LOCK);
        customDialog.setCancelable(false);
        customDialog.setDialogListener(new CustomDialog.CustomDialogInterface() {
            @Override
            public void yesBtnClicked(String btnName) {
                progressCircle = new ProgressCircle(activity);
                progressCircle.show();
                try {
                    hWalletToken = getWalletToken.getWalletTokenDataAPI(WalletTokenPurpose.WALLET_TOKEN_PURPOSE.PERSONALIZE_AND_CONFIGLOCK).get();
                    if (CaUtil.personalize(hWalletToken, activity, WalletTokenPurpose.WALLET_TOKEN_PURPOSE.PERSONALIZE_AND_CONFIGLOCK)) {
                        Intent intent = new Intent(getContext(), PinActivity.class);
                        intent.putExtra(Constants.INTENT_IS_REGISTRATION, true);
                        intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_SET_LOCK);
                        intent.putExtra(Constants.INTENT_WALLET_TOKEN, hWalletToken);
                        pinActivityResultLauncher.launch(intent);
                    }
                } catch (WalletCoreException e) {
                    CaLog.e("setLock fail : " + e.getMessage());
                    ContextCompat.getMainExecutor(activity).execute(()  -> {
                        CaUtil.showErrorDialog(activity, e.getMessage(), activity);
                    });
                    progressCircle.dismiss();
                } catch (ExecutionException | InterruptedException e) {
                    CaLog.e("setLock fail : " + e.getMessage());
                    Throwable cause = e.getCause();
                    if (cause instanceof CompletionException && cause.getCause() instanceof CommunicationException) {
                        ContextCompat.getMainExecutor(activity).execute(()  -> {
                            CaUtil.showErrorDialog(activity, cause.getCause().getMessage(), activity);
                        });
                    } else {
                        CaUtil.showErrorDialog(activity, cause.getCause().getMessage(), activity);
                    }
                    progressCircle.dismiss();
                }
                progressCircle.dismiss();
            }

            @Override
            public void noBtnClicked(String btnName) {
                progressCircle = new ProgressCircle(activity);
                progressCircle.show();
                try {
                    if (CaUtil.isLock(activity)) {
                        Intent intent = new Intent(getContext(), PinActivity.class);
                        intent.putExtra(Constants.INTENT_IS_REGISTRATION, false);
                        intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_SET_UNLOCK);
                        pinActivityResultLauncher.launch(intent);
                    } else {
                        //if(true) //최초 실행 일때
                        hWalletToken = getWalletToken.getWalletTokenDataAPI(WalletTokenPurpose.WALLET_TOKEN_PURPOSE.PERSONALIZE).get();
                        CaUtil.personalize(hWalletToken, activity, WalletTokenPurpose.WALLET_TOKEN_PURPOSE.PERSONALIZE);
                        progressCircle.dismiss();
                        Bundle bundle = new Bundle();
                        bundle.putInt("step", 2);
                        navController.navigate(R.id.action_setLockFragment_to_stepFragment, bundle);

                    }
                } catch(WalletCoreException e){
                    CaLog.e("setUnlock fail : " + e.getMessage());
                    CaUtil.showErrorDialog(activity, e.getMessage());
                    progressCircle.dismiss();
                } catch (ExecutionException | InterruptedException e) {
                    CaLog.e("setUnlock fail : " + e.getMessage());
                    Throwable cause = e.getCause();
                    if (cause instanceof CompletionException && cause.getCause() instanceof CommunicationException) {
                        ContextCompat.getMainExecutor(activity).execute(()  -> {
                            CaUtil.showErrorDialog(activity, cause.getCause().getMessage(), activity);
                        });
                    } else {
                        CaUtil.showErrorDialog(activity, cause.getCause().getMessage(), activity);
                    }
                    progressCircle.dismiss();
                }

            }
        });
        customDialog.show();
    }

}
