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
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;

import org.json.JSONObject;
import org.omnione.did.ca.BuildConfig;
import org.omnione.did.ca.R;
import org.omnione.did.ca.databinding.ActivityClaimWebBinding;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.ui.common.NoticeDialogFragment;
import org.omnione.did.ca.util.AppLog;

import java.util.concurrent.atomic.AtomicBoolean;

public final class Oid4vciUserInitWebActivity extends BaseActivity {

    public static final String EXTRA_START_URL = "extra_oid4vci_web_start_url";
    public static final String EXTRA_EXPECTED_ISSUER = "extra_oid4vci_web_expected_issuer";

    public static final String RESULT_OFFER_URI = "result_oid4vci_offer_uri";

    private static final String OFFER_SCHEME = "openid-credential-offer";

    private final AtomicBoolean offerHandled = new AtomicBoolean(false);

    private ActivityClaimWebBinding binding;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityClaimWebBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applySystemBarInsets();

        binding.backButton.setOnClickListener(v -> finishCancelled());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finishCancelled(); }
        });

        String startUrl = getIntent().getStringExtra(EXTRA_START_URL);
        if (startUrl == null || startUrl.isEmpty()) {
            showLoadError();
            return;
        }

        final String allowedHost = Uri.parse(startUrl).getHost();

        WebView webView = binding.webView;
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if (OFFER_SCHEME.equals(scheme)) {
                    handleCredentialOfferRedirect(uri);
                    return true;
                }
                if (!"http".equals(scheme) && !"https".equals(scheme)) {

                    AppLog.w("Oid4vciUserInitWeb", "blocked scheme=" + scheme);
                    return true;
                }
                if (allowedHost != null && !allowedHost.isEmpty()
                        && uri.getHost() != null && !allowedHost.equals(uri.getHost())) {

                    AppLog.d("Oid4vciUserInitWeb", "navigating to other host=" + uri.getHost());
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String loadedUrl) {
                super.onPageFinished(view, loadedUrl);
                dismissProgress();
                if (BuildConfig.DEBUG) prefillMockClaims(view);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    dismissProgress();
                    showLoadError();
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {

                handler.cancel();
                dismissProgress();
                showLoadError();
            }
        });

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(BuildConfig.DEBUG);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setDomStorageEnabled(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

        showProgress();
        webView.loadUrl(startUrl);
    }

    private void applySystemBarInsets() {
        applySystemBarInsets(binding.getRoot(), bars -> {
            applyTopInsetPadding(binding.appBar, bars.top);
            applyBottomInsetPadding(binding.webView, 0, bars.bottom);
        });
    }

    private void handleCredentialOfferRedirect(Uri uri) {
        if (!offerHandled.compareAndSet(false, true)) return;

        String reference = uri.getQueryParameter("credential_offer_uri");
        String inlineValue = uri.getQueryParameter("credential_offer");

        if (reference == null || reference.isEmpty() || inlineValue != null) {
            offerHandled.set(false);
            showInvalidCompletion();
            return;
        }
        Uri offerUri = Uri.parse(reference);
        String offerScheme = offerUri.getScheme();
        if ((!"http".equals(offerScheme) && !"https".equals(offerScheme))
                || offerUri.getHost() == null || offerUri.getHost().isEmpty()) {
            offerHandled.set(false);
            showInvalidCompletion();
            return;
        }

        Intent data = new Intent();

        data.putExtra(RESULT_OFFER_URI, uri.toString());
        String expectedIssuer = getIntent().getStringExtra(EXTRA_EXPECTED_ISSUER);
        if (expectedIssuer != null) data.putExtra(EXTRA_EXPECTED_ISSUER, expectedIssuer);
        dismissProgress();
        setResult(RESULT_OK, data);
        finish();
    }

    private void finishCancelled() {
        dismissProgress();
        setResult(RESULT_CANCELED);
        finish();
    }

    private void showLoadError() {
        NoticeDialogFragment.newInstance(this,
                        R.string.oid4vci_failed,
                        R.string.oid4vci_web_load_failed,
                        R.string.common_ok)
                .setOnConfirmed(this::finishCancelled)
                .show(getSupportFragmentManager());
    }

    private void showInvalidCompletion() {
        NoticeDialogFragment.newInstance(this,
                        R.string.oid4vci_failed,
                        R.string.oid4vci_web_invalid_completion,
                        R.string.common_ok)
                .show(getSupportFragmentManager());
    }

    private void prefillMockClaims(WebView view) {
        JSONObject values = new JSONObject();
        try {

            values.put("claim-family_name", "Hong");
            values.put("claim-given_name", "Gildong");
            values.put("claim-birthdate", "1990-01-01");
            values.put("claim-place_of_birth", "[\"Seoul\", \"Seoul\", \"Seoul\"]");
            values.put("claim-nationalities", "[\"KR\"]");
            values.put("claim-date_of_issuance", "2024-01-01");
            values.put("claim-date_of_expiry", "2034-01-01");
            values.put("claim-issuing_authority", "KR Government");
            values.put("claim-issuing_country", "KR");
        } catch (Exception e) {
            AppLog.w("Oid4vciUserInitWeb", "prefill build failed: " + e.getMessage());
            return;
        }

        String js = "(function(){var m=" + values + ";"
                + "for(var k in m){var e=document.getElementById(k);"
                + "if(e){e.value=m[k];"
                + "e.dispatchEvent(new Event('input',{bubbles:true}));"
                + "e.dispatchEvent(new Event('change',{bubbles:true}));}}})();";
        view.evaluateJavascript(js, null);
        AppLog.d("Oid4vciUserInitWeb", "prefilled mock claims (debug)");
    }
}
