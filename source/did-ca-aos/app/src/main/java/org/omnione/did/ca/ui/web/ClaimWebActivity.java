/*
 * Copyright 2026 OmniOne.
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

package org.omnione.did.ca.ui.web;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;

import org.omnione.did.ca.R;
import org.omnione.did.ca.databinding.ActivityClaimWebBinding;
import org.omnione.did.ca.ui.common.BaseActivity;

public final class ClaimWebActivity extends BaseActivity {

    public static final String EXTRA_URL = "extra_claim_web_url";
    public static final String EXTRA_AFTER_AUTH = "extra_claim_web_after_auth";

    private ActivityClaimWebBinding binding;

    @Nullable
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> filePickerLauncher;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityClaimWebBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applySystemBarInsets();

        binding.backButton.setOnClickListener(v -> finishCancelled());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finishCancelled(); }
        });

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
                });

        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null || url.isEmpty()) {
            showError();
            return;
        }

        WebView webView = binding.webView;
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String loadedUrl) {
                super.onPageFinished(view, loadedUrl);
                dismissProgress();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                super.onReceivedError(view, request, error);

                if (request != null && request.isForMainFrame()) {
                    dismissProgress();
                    showError();
                }
            }
        });
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.addJavascriptInterface(new JsBridge(), "android");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (ClaimWebActivity.this.filePathCallback != null) {
                    ClaimWebActivity.this.filePathCallback.onReceiveValue(null);
                }
                ClaimWebActivity.this.filePathCallback = filePathCallback;
                try {
                    filePickerLauncher.launch(fileChooserParams.createIntent());
                } catch (Exception e) {
                    ClaimWebActivity.this.filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        showProgress();
        webView.loadUrl(url);
    }

    private void applySystemBarInsets() {
        applySystemBarInsets(binding.getRoot(), bars -> {
            applyTopInsetPadding(binding.appBar, bars.top);
            applyBottomInsetPadding(binding.webView, 0, bars.bottom);
        });
    }

    private void finishCancelled() {
        dismissProgress();
        setResult(RESULT_CANCELED);
        finish();
    }

    private void finishCompleted() {
        dismissProgress();
        Intent data = new Intent();
        String afterAuth = getIntent().getStringExtra(EXTRA_AFTER_AUTH);
        if (afterAuth != null) data.putExtra(EXTRA_AFTER_AUTH, afterAuth);
        setResult(RESULT_OK, data);
        finish();
    }

    private void showError() {
        showErrorNotice(R.string.notice_issue_failed_title, R.string.claim_web_error_failed,
                null, this::finishCancelled);
    }

    private final class JsBridge {

        @JavascriptInterface
        public void onCompletedAddVcUpload() {
            runOnUiThread(ClaimWebActivity.this::finishCompleted);
        }

        @JavascriptInterface
        public void onFailedAddVcUpload() {
            runOnUiThread(ClaimWebActivity.this::showError);
        }
    }
}
