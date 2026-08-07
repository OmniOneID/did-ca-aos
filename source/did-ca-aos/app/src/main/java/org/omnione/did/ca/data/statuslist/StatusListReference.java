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
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

public final class StatusListReference {

    @NonNull public final String uri;
    public final long idx;

    public StatusListReference(@NonNull String uri, long idx) {
        this.uri = uri;
        this.idx = idx;
    }

    @Nullable
    public static StatusListReference parse(@Nullable JsonObject jwtPayload) {
        if (jwtPayload == null || !jwtPayload.has("status")) return null;
        try {
            JsonObject status = jwtPayload.getAsJsonObject("status");
            if (status == null || !status.has("status_list")) return null;
            JsonObject sl = status.getAsJsonObject("status_list");
            if (sl == null || !sl.has("uri") || !sl.has("idx")) return null;
            String uri = sl.get("uri").getAsString();
            long idx = sl.get("idx").getAsLong();
            if (uri == null || uri.isEmpty() || idx < 0) return null;
            return new StatusListReference(uri, idx);
        } catch (Exception e) {
            return null;
        }
    }
}
