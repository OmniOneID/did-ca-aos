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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import org.omnione.did.ca.R;
import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.config.Preference;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.ui.common.ProgressCircle;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.communication.logger.CommunicationLogger;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.wallet.WalletApi;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;
import org.omnione.did.sdk.wallet.walletservice.logger.WalletLogger;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;


public class SplashActivity extends AppCompatActivity {
    ActivityResultLauncher<Intent> pinActivityResultLauncher;
    ActivityResultLauncher<Intent> pinActivityPushResultLauncher;
    WalletApi walletApi;
    ProgressCircle progressCircle;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);


        //log enable
        WalletLogger walletLogger = WalletLogger.getInstance();
        walletLogger.enable();
        CommunicationLogger communicationLogger = CommunicationLogger.getInstance();
        communicationLogger.enable();

        progressCircle = new ProgressCircle(this);
        progressCircle.show();
        Handler handler = new Handler();

        handler.postDelayed(() -> {
            new Thread(() -> {
                Intent intent = getIntent();
                if (intent != null && intent.hasExtra("data")) {
                    pushInit(intent.getStringExtra("data"));
                } else {
                    init();
                }

            }).start();
        }, Config.SPLASH_DELAY);

        pinActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            startActivity(new Intent(SplashActivity.this, MainActivity.class));
                            finish();
                        } else if(result.getResultCode() == Activity.RESULT_CANCELED){
                            CaLog.e("pin authentication fail");
                            CaUtil.showErrorDialog(SplashActivity.this,"[Authenticate failed] PIN Authenticate Failed",SplashActivity.this);
                        }
                    }
                }
        );
        pinActivityPushResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                            intent.putExtra("pushdata", result.getData().getStringExtra("data"));
                            startActivity(intent);
                            finish();
                        } else if(result.getResultCode() == Activity.RESULT_CANCELED){
                            CaLog.e("pin authentication fail");
                            CaUtil.showErrorDialog(SplashActivity.this,"[Authenticate failed] PIN Authenticate Failed", SplashActivity.this);
                        }
                    }
                }
        );
    }

    private void init(){
        try {
            walletApi = WalletApi.getInstance(this);
            if(!walletApi.isExistWallet()) {
               walletApi.createWallet(Config.WALLET_URL, Config.TAS_URL);
            }
            if(Preference.getCaAppId(this).length() == 0)
                Preference.saveCaAppId(this, CaUtil.createCaAppId());
            getFcmToken();
            if (CaUtil.isLock(this)) {
                Intent intent = new Intent(SplashActivity.this, PinActivity.class);
                intent.putExtra(Constants.INTENT_IS_REGISTRATION, false);
                intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_STATUS_UNLOCK);
                pinActivityResultLauncher.launch(intent);
            } else {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
            }
        } catch (WalletException | UtilityException | WalletCoreException e) {
            CaLog.e("Error creating wallet : " + e.getMessage());
            ContextCompat.getMainExecutor(SplashActivity.this).execute(()  -> {
                CaUtil.showErrorDialog(SplashActivity.this, e.getMessage(),SplashActivity.this);
            });

        } catch (ExecutionException | InterruptedException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CompletionException && cause.getCause() instanceof CommunicationException) {
                CaLog.e("Error creating wallet : " + e.getMessage());
                ContextCompat.getMainExecutor(SplashActivity.this).execute(()  -> {
                    CaUtil.showErrorDialog(SplashActivity.this, cause.getCause().getMessage(), SplashActivity.this);
                });
            }
        }
        progressCircle.dismiss();

    }
    private void pushInit(String pushData){
        CaLog.d("pushData : " + pushData);
        try {
            walletApi = WalletApi.getInstance(this);
        } catch (WalletCoreException e) {
            CaLog.e("Error PushMessage process : " + e.getMessage());
            ContextCompat.getMainExecutor(SplashActivity.this).execute(()  -> {
                CaUtil.showErrorDialog(SplashActivity.this, e.getMessage(),SplashActivity.this);
            });
        }
        if (CaUtil.isLock(this)) {
            Intent intent = new Intent(SplashActivity.this, PinActivity.class);
            intent.putExtra(Constants.INTENT_IS_REGISTRATION, false);
            intent.putExtra(Constants.INTENT_TYPE_AUTHENTICATION, Constants.PIN_TYPE_STATUS_UNLOCK);
            intent.putExtra("pushdata", pushData);
            pinActivityPushResultLauncher.launch(intent);
        } else {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            intent.putExtra("pushdata", pushData);
            startActivity(intent);
            finish();
        }

    }

    void getFcmToken(){
        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                    @Override
                    public void onComplete(@NonNull Task<InstanceIdResult> task) {
                        if (!task.isSuccessful()) {
                            CaLog.e("Fetching FCM registration token failed : " + task.getException());
                            ContextCompat.getMainExecutor(SplashActivity.this).execute(()  -> {
                                CaUtil.showErrorDialog(SplashActivity.this, "Fetching FCM registration token failed",SplashActivity.this);
                            });
                            return;
                        }
                        String token = task.getResult().getToken();
                        Preference.setPushToken(SplashActivity.this, token);
                        String msg = getString(R.string.msg_token_fmt, token);
                        CaLog.d("FCM Token : " + msg);
                    }
                });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
//        init();
//        receivePush(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        progressCircle.dismiss();
    }

}