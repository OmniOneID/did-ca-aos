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
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.omnione.did.ca.R;
import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.ui.common.ProgressCircle;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.wallet.WalletApi;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

public class PinActivity extends AppCompatActivity {
    Button[] button = new Button[10];
    ImageView[] pw = new ImageView[6];
    String password = "";
    String tempPassword = "";
    Button delBtn;
    Button cancelBtn;
    Integer[] Rid_button = {
            R.id.button0, R.id.button1, R.id.button2, R.id.button3, R.id.button4,
            R.id.button5, R.id.button6, R.id.button7, R.id.button8, R.id.button9,
    };
    Integer[] Rid_pw = {
            R.id.img_pw1, R.id.img_pw2, R.id.img_pw3, R.id.img_pw4, R.id.img_pw5, R.id.img_pw6,
    };
    boolean isRegistration = false;
    int authenticationType = 0;
    boolean isRegister = false;
    boolean isChange = false;
    String oldPin = "";
    WalletApi walletApi;
    ProgressCircle progressCircle;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        initPin();

        for(int i = 0; i < button.length; i++){
            final int index;
            index = i;
            button[index].setOnClickListener(new Button.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(password.length() < 6) {
                        if (password.length() == 0) {
                            password = button[index].getTag().toString();
                        } else {
                            password = password + button[index].getTag().toString();
                        }
                        pw[password.length() - 1].setImageDrawable(ContextCompat.getDrawable(PinActivity.this, R.drawable.omni_pin_num_out));
                    }
                    if (password.length() == Config.PIN_MAX_VALUE) {
                        switch(authenticationType) {
                            case Constants.PIN_TYPE_SET_LOCK:
                                setLockRegisterPin();
                                break;
                            case Constants.PIN_TYPE_SET_UNLOCK:
                                setUnlockAuthenticatePin();
                                break;
                            case Constants.PIN_TYPE_STATUS_UNLOCK:
                                authenticatePin();
                                break;
                            case Constants.PIN_TYPE_REG_KEY:
                                genKeyRegisterPin();
                                break;
                            case Constants.PIN_TYPE_USE_KEY:
                                useKeyAuthenticatePin();
                                break;
                            case Constants.PIN_TYPE_CHANGE_SIGNING_PIN:
                                changeSigningPin();
                                break;
                            case Constants.PIN_TYPE_CHANGE_UNLOCK_PIN:
                                changeUnlockPin();
                                break;
                            default:
                                break;
                        }
                    }
                }
            });

            delBtn.setOnClickListener(new Button.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(password.length() != 0) {
                        pw[password.length()-1].setImageDrawable(ContextCompat.getDrawable(PinActivity.this, R.drawable.omni_pin_num_in));
                        password = password.substring(0, password.length() - 1);
                    }
                }
            });

            cancelBtn.setOnClickListener(new Button.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cancelPin();
                }
            });
        }
    }
    private void initPin() {
        try {
            walletApi = WalletApi.getInstance(PinActivity.this);
        } catch (WalletCoreException e) {
            CaUtil.showErrorDialog(PinActivity.this, e.getMessage());
        }
        isRegistration = getIntent().getBooleanExtra(Constants.INTENT_IS_REGISTRATION, false);
        authenticationType = getIntent().getIntExtra(Constants.INTENT_TYPE_AUTHENTICATION, 0);
        if(isRegistration){
            isRegister = true;
            TextView message = findViewById(R.id.status_text);
            if(authenticationType == Constants.PIN_TYPE_SET_LOCK || 
                    authenticationType == Constants.PIN_TYPE_SET_UNLOCK ||
                    authenticationType == Constants.PIN_TYPE_STATUS_UNLOCK) {
                message.setText(Constants.PIN_REGISTER_LOCK_TEXT);
            } else if(authenticationType == Constants.PIN_TYPE_REG_KEY ||
                    authenticationType == Constants.PIN_TYPE_USE_KEY) {
                message.setText(Constants.PIN_REGISTER_TEXT);
            } else {
                message.setText(Constants.PIN_REGISTER_TEXT); //default message
            }
        }

        if(authenticationType == Constants.PIN_TYPE_CHANGE_UNLOCK_PIN ||
            authenticationType == Constants.PIN_TYPE_STATUS_UNLOCK) {
            TextView message = findViewById(R.id.status_text);
            message.setText(Constants.PIN_INPUT_LOCK_TEXT);
        }
        delBtn = findViewById(R.id.button_delete);
        cancelBtn = findViewById(R.id.button_cancel);
        for(int i=0;i<=9; i++){
            button[i] = findViewById(Rid_button[i]);
            button[i].setTag(i);
            button[i].setText(String.valueOf(i));
        }
        for(int i=0;i<=5; i++){
            pw[i] = findViewById(Rid_pw[i]);
            pw[i].setTag(i);
            pw[i].setImageDrawable(ContextCompat.getDrawable(PinActivity.this, R.drawable.omni_pin_num_in));
        }
        tempPassword = "";
        password = "";
        enableButton(true);
    }
    private void setLockRegisterPin(){
        if(isRegister) {
            TextView message = findViewById(R.id.status_text);
            message.setText(Constants.PIN_INPUT_LOCK_TEXT);
            for (int i = 0; i <= 5; i++) {
                pw[i].setImageDrawable(ContextCompat.getDrawable(PinActivity.this, R.drawable.omni_pin_num_in));
            }
            tempPassword = password;
            password = "";
            isRegister = false;
        } else {
            if (tempPassword.equals(password)) {
                registerLock(password, true);
                Intent resultIntent = new Intent();
                resultIntent.putExtra("reg", Constants.PIN_TYPE_REG_KEY);
                setResult(Activity.RESULT_OK, resultIntent);
                finish();
            } else {
                TextView message = findViewById(R.id.status_text);
                message.setText(Constants.PIN_NOT_MATCH_TEXT);
                enableButton(false);
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        initPin();
                    }
                }, Config.PIN_FAIL_DELAY);
            }
        }
    }

    private void genKeyRegisterPin(){
        if(isRegister) {
            TextView message = findViewById(R.id.status_text);
            message.setText(Constants.PIN_INPUT_TEXT);

            for (int i = 0; i <= 5; i++) {
                pw[i].setImageDrawable(ContextCompat.getDrawable(PinActivity.this, R.drawable.omni_pin_num_in));
            }
            tempPassword = password;
            password = "";
            isRegister = false;
        } else {
            if (tempPassword.equals(password)) {
                Intent resultIntent = new Intent();
                resultIntent.putExtra("reg", Constants.PIN_TYPE_REG_KEY);
                resultIntent.putExtra("pin", tempPassword);
                setResult(Activity.RESULT_OK, resultIntent);
                finish();
            } else {
                TextView message = findViewById(R.id.status_text);
                message.setText(Constants.PIN_NOT_MATCH_TEXT);
                enableButton(false);
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        initPin();
                    }
                }, Config.PIN_FAIL_DELAY);
            }
        }
    }
    private void setUnlockAuthenticatePin(){
        authenticateLock(password);
        registerLock(password, false);
        password = "";

    }

    private void authenticatePin(){
        authenticateLock(password);
        password = "";
    }
    private void useKeyAuthenticatePin(){
        progressCircle = new ProgressCircle(this);
        progressCircle.show();
        Intent resultIntent = new Intent();
        resultIntent.putExtra("reg", Constants.PIN_TYPE_USE_KEY);
        resultIntent.putExtra("pin", password);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    private void changeSigningPin() {
        if (isChange) {
            if(isRegister) {
                TextView message = findViewById(R.id.status_text);
                message.setText(Constants.PIN_RE_ENTER_TEXT);

                for (int i = 0; i <= 5; i++) {
                    pw[i].setImageDrawable(ContextCompat.getDrawable(PinActivity.this, R.drawable.omni_pin_num_in));
                }
                tempPassword = password;
                password = "";
                isRegister = false;
            } else {
                if (tempPassword.equals(password)) {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("reg", Constants.PIN_TYPE_REG_KEY);
                    resultIntent.putExtra("oldPin", oldPin);
                    resultIntent.putExtra("newPin", tempPassword);
                    setResult(Activity.RESULT_OK, resultIntent);
                    finish();
                } else {
                    TextView message = findViewById(R.id.status_text);
                    message.setText(Constants.PIN_NOT_MATCH_TEXT);
                    enableButton(false);
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            message.setText(Constants.PIN_CHANGE_TEXT);
                            isRegister = true;
                            initPin();
                        }
                    }, Config.PIN_FAIL_DELAY);
                }
            }
        } else {
            oldPin = password;
            TextView message = findViewById(R.id.status_text);
            message.setText(Constants.PIN_CHANGE_TEXT);

            for (int i = 0; i <= 5; i++) {
                pw[i].setImageDrawable(ContextCompat.getDrawable(PinActivity.this, R.drawable.omni_pin_num_in));
            }
            tempPassword = password;
            password = "";
            isChange = true;
            isRegister = true;

        }
    }

    private void changeUnlockPin() {
        if (isChange) {
            if(isRegister) {
                TextView message = findViewById(R.id.status_text);
                message.setText(Constants.PIN_RE_ENTER_TEXT);

                for (int i = 0; i <= 5; i++) {
                    pw[i].setImageDrawable(ContextCompat.getDrawable(PinActivity.this, R.drawable.omni_pin_num_in));
                }
                tempPassword = password;
                password = "";
                isRegister = false;
            } else {
                if (tempPassword.equals(password)) {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("reg", Constants.PIN_TYPE_REG_KEY);
                    resultIntent.putExtra("oldPassCode", oldPin);
                    resultIntent.putExtra("newPassCode", tempPassword);
                    setResult(Activity.RESULT_OK, resultIntent);
                    finish();
                } else {
                    TextView message = findViewById(R.id.status_text);
                    message.setText(Constants.PIN_NOT_MATCH_TEXT);
                    enableButton(false);
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            message.setText(Constants.PIN_CHANGE_TEXT);
                            isRegister = true;
                            initPin();
                        }
                    }, Config.PIN_FAIL_DELAY);
                }
            }
        } else {
            oldPin = password;
            TextView message = findViewById(R.id.status_text);
            message.setText(Constants.PIN_CHANGE_TEXT);

            for (int i = 0; i <= 5; i++) {
                pw[i].setImageDrawable(ContextCompat.getDrawable(PinActivity.this, R.drawable.omni_pin_num_in));
            }
            tempPassword = password;
            password = "";
            isChange = true;
            isRegister = true;

        }
    }
    private void registerLock(String passCode, boolean isLock) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    walletApi.registerLock(getIntent().getStringExtra(Constants.INTENT_WALLET_TOKEN), passCode, isLock);
                } catch (WalletException | WalletCoreException | UtilityException e) {
                    CaUtil.showErrorDialog(PinActivity.this, e.getMessage());
                }
            }
        }).start();
    }

    private void authenticateLock(String passCode) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    walletApi.authenticateLock(passCode);
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("reg", Constants.PIN_TYPE_USE_KEY);
                    if (getIntent() != null && getIntent().hasExtra("data")) {
                        resultIntent.putExtra("data",getIntent().getStringExtra("data"));
                    }
                    setResult(Activity.RESULT_OK, resultIntent);
                    finish();
                } catch (UtilityException | WalletCoreException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            CaUtil.showErrorDialog(PinActivity.this, e.getMessage());
                            for (int i = 0; i <= 5; i++) {
                                pw[i].setImageDrawable(ContextCompat.getDrawable(PinActivity.this, R.drawable.omni_pin_num_in));
                            }
                        }
                    });
                }
            }
        }).start();

    }

    private void cancelPin(){
        Intent resultIntent = new Intent();
        setResult(RESULT_CANCELED, resultIntent);
        finish();
    }
    private void enableButton(boolean isEnable){
        for(int i=0;i<=9; i++){
            button[i].setEnabled(isEnable);
        }
        delBtn.setEnabled(isEnable);
        cancelBtn.setEnabled(isEnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(progressCircle != null)
            progressCircle.dismiss();
    }
}
