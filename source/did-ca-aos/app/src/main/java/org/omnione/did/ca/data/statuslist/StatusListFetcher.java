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

package org.omnione.did.ca.data.statuslist;

import androidx.annotation.NonNull;

import org.omnione.did.ca.util.AppLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class StatusListFetcher {

    private static final String TAG = "CaStatusList";
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_BODY_BYTES = 256 * 1024;

    private StatusListFetcher() {}

    @NonNull
    public static String fetch(@NonNull String uri) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(uri).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/statuslist+jwt");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            int code = conn.getResponseCode();
            AppLog.d(TAG, "GET " + uri + " -> " + code);
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("status list fetch failed: HTTP " + code);
            }
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BODY_BYTES) {
                        throw new IOException("status list response too large");
                    }
                }
                return out.toString("UTF-8").trim();
            }
        } finally {
            conn.disconnect();
        }
    }
}
