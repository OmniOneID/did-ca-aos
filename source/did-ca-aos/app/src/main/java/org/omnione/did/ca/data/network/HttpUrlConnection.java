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

package org.omnione.did.ca.data.network;

import com.google.gson.Gson;

import org.omnione.did.ca.util.AppLog;
import org.omnione.did.sdk.communication.exception.CommunicationErrorCode;
import org.omnione.did.sdk.communication.exception.CommunicationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class HttpUrlConnection {

    private static final String TAG = "CaNet";

    public static String send(String urlString, String method, String body)
            throws CommunicationException {
        HttpURLConnection conn = null;
        AppLog.d(TAG, "→ " + method + " " + urlString + (body != null && !body.isEmpty() ? " body=" + body : ""));
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);

            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                conn.setDoOutput(true);
                if (body != null && !body.isEmpty()) {
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }

            int status = conn.getResponseCode();
            InputStream stream = (status >= 200 && status < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            StringBuilder sb = new StringBuilder();
            if (stream != null) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
            }

            String responseBody = sb.toString();
            AppLog.d(TAG, "← " + status + " " + urlString + " body=" + responseBody);

            if (status >= 200 && status < 300) {
                return responseBody;
            }
            throw newCommunicationException(status, responseBody);
        } catch (IOException e) {
            String msg = e.getMessage() == null ? "io error" : e.getMessage();
            AppLog.e(TAG, "✗ IOException " + urlString + " : " + msg);
            throw newCommunicationException(-1, msg);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static CommunicationException newCommunicationException(int code, String message) {
        CommunicationErrorCode errorCode = (code == -1)
                ? CommunicationErrorCode.ERR_CODE_COMMUNICATION_INCORRECT_URL_CONNECTION
                : (code == 401
                        ? CommunicationErrorCode.ERR_CODE_COMMUNICATION_UNAUTHORIZED
                        : (code >= 500
                                ? CommunicationErrorCode.ERR_CODE_COMMUNICATION_SERVER_FAIL
                                : CommunicationErrorCode.ERR_CODE_COMMUNICATION_UNKNOWN));
        return new CommunicationException(errorCode, formatDetail(code, message));
    }

    private static String formatDetail(int code, String message) {
        ServerErrorWire err = tryParseServerError(message);
        if (err != null && err.code != null && err.description != null) {
            return "[" + err.code + "] " + err.description;
        }
        return "[" + code + "] " + message;
    }

    private static ServerErrorWire tryParseServerError(String body) {
        if (body == null || body.isEmpty()) return null;
        try {
            return new Gson().fromJson(body, ServerErrorWire.class);
        } catch (Exception e) {
            return null;
        }
    }

    private HttpUrlConnection() {}
}
