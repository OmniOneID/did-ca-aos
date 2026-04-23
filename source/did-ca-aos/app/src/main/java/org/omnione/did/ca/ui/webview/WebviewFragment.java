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
package org.omnione.did.ca.ui.webview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import org.omnione.did.ca.R;
import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.config.Preference;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

public class WebviewFragment extends Fragment {
    NavController navController;
    Activity activity;

    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Uri[] uris = null;
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) uris = new Uri[]{uri};
                    }
                    if (filePathCallback != null) {
                        filePathCallback.onReceiveValue(uris);
                        filePathCallback = null;
                    }
                }
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_webview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);
        showWebview(view);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (Activity) context;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void showWebview(View view) {
        WebView webView = view.findViewById(R.id.webview);
        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new WebAppInterface(activity, navController), "android");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (WebviewFragment.this.filePathCallback != null) {
                    WebviewFragment.this.filePathCallback.onReceiveValue(null);
                }
                WebviewFragment.this.filePathCallback = filePathCallback;
                try {
                    filePickerLauncher.launch(fileChooserParams.createIntent());
                } catch (Exception e) {
                    WebviewFragment.this.filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        try {
            WalletApi walletApi = WalletApi.getInstance(activity);
            String url = "";

            if (requireArguments().getInt("type") == Constants.WEBVIEW_VC_INFO) {
                CaLog.d("vcSchemaId: " + requireArguments().getString("vcSchemaId"));
                String vcSchemaId = requireArguments().getString("vcSchemaId");
                String nameValue = null;
                int idx = vcSchemaId.indexOf("name=");
                if (idx != -1) {
                    nameValue = vcSchemaId.substring(idx + "name=".length());
                }
                url = Config.Base_Demo.BASE_URL + "/addVcInfo?did="
                        + walletApi.getDIDDocument(Constants.DID_TYPE_HOLDER).getId()
                        + "&userName=" + Preference.getUsernameForDemo(activity)
                        + "&vcSchemaId=" + nameValue;
                CaLog.d("url: " + url);
            }

            webView.loadUrl(url);
        } catch (WalletCoreException | UtilityException | WalletException e) {
            CaLog.e("webview error : " + e.getMessage());
            CaUtil.showErrorDialog(activity, e.getMessage());
        }
    }

    public class WebAppInterface {
        public Activity activity;
        public NavController navController;

        public WebAppInterface(Activity activity, NavController navController) {
            this.activity = activity;
            this.navController = navController;
        }

        @JavascriptInterface
        public void onCompletedAddVcUpload() {
            CaLog.d("Webview onCompletedAddVcUpload");
            activity.runOnUiThread(() -> {
                Bundle bundle = new Bundle();
                bundle.putString("type", "webview");
                navController.navigate(R.id.action_webviewFragment_to_profileFragment, bundle);
            });
        }

        @JavascriptInterface
        public void onFailedAddVcUpload() {
            CaLog.d("Webview onFailedAddVcUpload");
            ContextCompat.getMainExecutor(activity).execute(() ->
                    CaUtil.showErrorDialog(activity, "[WebViewError] onFailedAddVcUpload"));
        }
    }
}
