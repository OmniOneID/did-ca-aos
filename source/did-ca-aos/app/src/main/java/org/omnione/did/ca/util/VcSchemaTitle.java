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

package org.omnione.did.ca.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.omnione.did.sdk.communication.urlconnection.HttpUrlConnectionTask;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.datamodel.vcschema.VCSchema;

public final class VcSchemaTitle {

    private static final String FALLBACK = "Requested credentials";

    private VcSchemaTitle() {
    }

    @NonNull
    public static String resolve(@Nullable String schemaId) {
        String title = fetch(schemaId);
        if (title.isEmpty()) {
            title = lastSegment(schemaId);
        }
        if (title.isEmpty()) {
            title = FALLBACK;
        }
        return title;
    }

    @NonNull
    private static String fetch(@Nullable String schemaUrl) {
        if (schemaUrl == null || schemaUrl.isEmpty()) {
            return "";
        }
        try {
            String body = new HttpUrlConnectionTask().makeHttpRequest(schemaUrl, "GET", null, null);
            VCSchema schema = MessageUtil.deserialize(body, VCSchema.class);
            if (schema != null && schema.getTitle() != null) {
                return schema.getTitle();
            }
        } catch (Exception ignored) {

        }
        return "";
    }

    @NonNull
    private static String lastSegment(@Nullable String s) {
        if (s == null || s.isEmpty()) return "";
        int slash = s.lastIndexOf('/');
        int colon = s.lastIndexOf(':');
        int idx = Math.max(slash, colon);
        return idx >= 0 && idx < s.length() - 1 ? s.substring(idx + 1) : s;
    }
}
