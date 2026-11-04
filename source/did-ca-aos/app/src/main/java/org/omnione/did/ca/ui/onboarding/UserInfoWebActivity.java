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

package org.omnione.did.ca.ui.onboarding;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.core.view.WindowCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.omnione.did.ca.R;
import org.omnione.did.ca.databinding.ActivityUserInfoWebBinding;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.ui.common.NoticeDialogFragment;
import org.omnione.did.ca.util.IntentRouter;

public final class UserInfoWebActivity extends BaseActivity {

    public static final String EXTRA_URL = "extra_user_info_web_url";
    public static final String EXTRA_USER_NAME = "extra_user_name";
    public static final String EXTRA_USER_ID = "extra_user_id";

    private ActivityUserInfoWebBinding binding;
    private boolean loadFailed;
    private boolean pageLoaded;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserInfoWebBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applySystemBarInsets(binding.getRoot(), bars -> {
            applyTopInsetPadding(binding.appBar, bars.top);
            applyBottomInsetPadding(binding.body, 0, bars.bottom);
        });

        NoticeDialogFragment.dismissRestored(getSupportFragmentManager());

        binding.backButton.setOnClickListener(v -> finish());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finish(); }
        });

        WebView webView = binding.webView;
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String loadedUrl) {
                super.onPageFinished(view, loadedUrl);
                pageLoaded = true;
                binding.spinner.setVisibility(View.GONE);
                if (!loadFailed) webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame() && !loadFailed) {
                    loadFailed = true;
                    showLoadFailed();
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            WebResourceResponse response) {
                super.onReceivedHttpError(view, request, response);
                if (!pageLoaded && request != null && request.isForMainFrame()
                        && response != null && response.getStatusCode() >= 400 && !loadFailed) {
                    loadFailed = true;
                    showLoadFailed();
                }
            }
        });
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new JsBridge(), "android");
        webView.loadUrl(getIntent().getStringExtra(EXTRA_URL));
    }

    private void showLoadFailed() {
        if (getSupportFragmentManager().isStateSaved()) {
            finish();
            return;
        }
        binding.spinner.setVisibility(View.GONE);
        binding.webView.setVisibility(View.INVISIBLE);
        binding.body.setBackgroundColor(Color.WHITE);
        NoticeDialogFragment.newInstance(this,
                        R.string.common_error,
                        R.string.splash_init_failed_message,
                        R.string.common_ok)
                .setOnConfirmed(this::finish)
                .setOnCancelled(this::finish)
                .setLink(R.string.server_settings, () -> {
                    startActivity(IntentRouter.serverUrlIntent(this, false));
                    finish();
                })
                .show(getSupportFragmentManager());
    }

    private void showRegistrationFailed() {
        if (getSupportFragmentManager().isStateSaved()) return;
        NoticeDialogFragment.newInstance(this,
                        R.string.user_info_web_failed_title,
                        R.string.user_info_web_failed_message,
                        R.string.common_ok)
                .show(getSupportFragmentManager());
    }

    private void complete(String userName, String userId) {
        Intent data = new Intent()
                .putExtra(EXTRA_USER_NAME, userName)
                .putExtra(EXTRA_USER_ID, userId);
        setResult(RESULT_OK, data);
        finish();
    }

    private final class JsBridge {

        @JavascriptInterface
        public void onCompletedUserInfoUpload(String json) {
            JSONObject obj;
            try {
                obj = new JSONObject(json);
            } catch (JSONException | RuntimeException e) {
                obj = new JSONObject();
            }
            String userName = obj.isNull("username") ? "" : obj.optString("username");
            String userId = obj.isNull("userId") ? "" : obj.optString("userId");
            runOnUiThread(userId.isEmpty()
                    ? UserInfoWebActivity.this::showRegistrationFailed
                    : () -> complete(userName, userId));
        }

        @JavascriptInterface
        public void onFailedUserInfoUpload() {
            runOnUiThread(UserInfoWebActivity.this::showRegistrationFailed);
        }
    }
}
