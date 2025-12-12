/*
 * Copyright 2025 OmniOne.
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.ui.common.ProgressDialog;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.exception.WalletCoreException;

public class BaseActivity extends AppCompatActivity {

    protected Context context;
    protected WalletApi walletApi;

    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        try {
            walletApi = WalletApi.getInstance(context);
        } catch (WalletCoreException e) {
            CaLog.d("walletApi getInstance ex: "+e.getMessage());
        }
    }

    public void showProgress() {
        if (isFinishing() || isDestroyed() || (progressDialog != null && progressDialog.isVisible())) {
            return;
        }
        progressDialog = ProgressDialog.getInstance();
        progressDialog.show(getSupportFragmentManager(), "ProgressDialog");
    }

    public void dismissProgress() {
        if (isFinishing() || isDestroyed() || progressDialog == null || !progressDialog.isVisible()) {
            return;
        }
        progressDialog.dismiss();
        progressDialog = null;
    }

    public void reStart() {
        PackageManager packageManager = getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(getPackageName());
        if (intent != null) {
            ComponentName componentName = intent.getComponent();
            Intent mainIntent = Intent.makeRestartActivityTask(componentName);
            startActivity(mainIntent);
            System.exit(0);
        }
    }
}
