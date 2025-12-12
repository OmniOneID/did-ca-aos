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

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.ui.common.ProgressDialog;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.exception.WalletCoreException;

public class BaseFragment extends Fragment {

    protected Context context;

    protected WalletApi walletApi;
    private ProgressDialog progressDialog;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            if (context != null) {
                walletApi = WalletApi.getInstance(context);
            } else {
                CaLog.d("BaseFragment: Context is null, WalletApi cannot be initialized in onCreate.");
            }
        } catch (WalletCoreException e) {
            CaLog.d("BaseFragment: walletApi getInstance ex: " + e.getMessage());
        }
    }

    public void showProgress() {
        CaLog.d("showProgress !!!! ");
        if (!isAdded() || isRemoving()) {
            CaLog.d("BaseFragment: showProgress returning early due to fragment state.");
            return;
        }

        FragmentManager fm = getChildFragmentManager();

        if (fm.findFragmentByTag("ProgressDialog") == null) {
            progressDialog = ProgressDialog.getInstance();
            try {
                progressDialog.show(fm, "ProgressDialog");
            } catch (IllegalStateException e) {
                CaLog.d("BaseFragment: Error showing ProgressDialog: " + e.getMessage());
            }
        } else {
            CaLog.d("BaseFragment: ProgressDialog is already showing.");
        }
    }

    public void dismissProgress() {

        CaLog.d("dismissProgress !!!! ");

        Fragment existingDialog = getChildFragmentManager().findFragmentByTag("ProgressDialog");
        if (existingDialog instanceof ProgressDialog) {
            try {
                ((ProgressDialog) existingDialog).dismissAllowingStateLoss();
            } catch (Exception e) {
                CaLog.d("Error dismissing ProgressDialog: " + e.getMessage());
            }
        }

        if (progressDialog != null) {
            progressDialog = null;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        context = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}
