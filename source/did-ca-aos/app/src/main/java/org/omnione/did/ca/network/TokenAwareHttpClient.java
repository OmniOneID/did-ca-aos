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

package org.omnione.did.ca.network;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.util.AuthTokenHelper;
import org.omnione.did.sdk.communication.exception.CommunicationErrorCode;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.communication.urlconnection.HttpUrlConnectionTask;

import java.util.concurrent.Semaphore;

/**
 * HTTP client that automatically attaches a CAS access token for /cas/* URLs
 * and retries once after refreshing the token on 401 Unauthorized.
 * On refresh failure, clears all tokens and restarts the application.
 */
public class TokenAwareHttpClient {

    private static final String PREFIX_CAS = "/cas";

    /**
     * Sends an HTTP request. If the URL contains "/cas", the access token is
     * attached and a single automatic refresh + retry is performed on 401.
     */
    public static String send(Context context, String url, String method, String request)
            throws CommunicationException {

        CaLog.d(url + " :: connect");

        // Attach the stored access token only if one actually exists.
        // Passing null (not empty string) skips the Authorization header entirely,
        // which is correct for public endpoints (signup, signin, check-registration-status)
        // called before a session has been established.
        String rawToken = url.toLowerCase().contains(PREFIX_CAS)
                ? AuthTokenHelper.getAccessToken(context)
                : null;
        String token = (rawToken != null && !rawToken.isEmpty()) ? rawToken : null;

        try {
            return new HttpUrlConnectionTask().makeHttpRequest(url, method, request, token);
        } catch (CommunicationException e) {
            CaLog.d(url + " :: error code=" + e.getErrorCode() + ", msg=" + e.getMessage());

            // Only attempt a token refresh if:
            // 1. The error is 401 Unauthorized
            // 2. We actually had a token (public endpoints with no token should not trigger refresh)
            // 3. The URL is a CAS endpoint
            if (CommunicationErrorCode.ERR_CODE_COMMUNICATION_UNAUTHORIZED.getCode() != e.getErrorCode()
                    || token == null
                    || !url.toLowerCase().contains(PREFIX_CAS)) {
                throw e;
            }

            // 401 for a CAS URL where we had a token — try token refresh once
            CaLog.d(url + " :: 401, attempting token refresh");
            return retryWithRefreshedToken(context, url, method, request);
        }
    }

    // ── private ───────────────────────────────────────────────────────────────

    private static String retryWithRefreshedToken(Context context, String url,
            String method, String request) throws CommunicationException {

        final Semaphore semaphore = new Semaphore(0);
        final String[] newToken = {null};
        final Exception[] refreshError = {null};

        AuthTokenHelper.requestTokenRefresh(context, new AuthTokenRefreshCallback() {
            @Override
            public void onSuccess(String token) {
                CaLog.d(url + " :: refresh success");
                newToken[0] = token;
                semaphore.release();
            }

            @Override
            public void onFailure(Exception ex) {
                CaLog.d(url + " :: refresh failed: " + ex.getMessage());
                refreshError[0] = ex;
                semaphore.release();
            }
        });

        try {
            semaphore.acquire();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for token refresh", ex);
        }

        if (refreshError[0] != null) {
            handleSessionExpired(context);
            // Return empty string after showing the dialog; caller should treat this as a no-op.
            return "";
        }

        CaLog.d(url + " :: retrying with new token");
        return new HttpUrlConnectionTask().makeHttpRequest(url, method, request, newToken[0]);
    }

    /**
     * Clears all tokens and restarts the app. Must be called on a background thread;
     * the UI dialog is posted to the main thread.
     */
    private static void handleSessionExpired(Context context) {
        CaLog.d("Session expired — clearing tokens and restarting");
        AuthTokenHelper.clearAllTokens(context);

        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            activity.runOnUiThread(() -> {
                new android.app.AlertDialog.Builder(activity)
                        .setTitle("Session Expired")
                        .setMessage("Your session has expired. Please sign in again.")
                        .setCancelable(false)
                        .setPositiveButton("OK", (d, w) -> restartApp(activity))
                        .show();
            });
        } else {
            restartApp(context);
        }
    }

    private static void restartApp(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(context.getPackageName());
        if (intent != null) {
            ComponentName cn = intent.getComponent();
            Intent main = Intent.makeRestartActivityTask(cn);
            context.startActivity(main);
            System.exit(0);
        }
    }
}
