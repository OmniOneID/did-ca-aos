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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class StatusListToken {

    @NonNull public final String sub;
    public final long iat;
    public final long exp;
    public final long ttl;
    public final int bits;
    @NonNull public final String lst;

    private StatusListToken(@NonNull String sub, long iat, long exp, long ttl,
                            int bits, @NonNull String lst) {
        this.sub = sub;
        this.iat = iat;
        this.exp = exp;
        this.ttl = ttl;
        this.bits = bits;
        this.lst = lst;
    }

    public static StatusListToken parse(@NonNull String compactJwt) {
        String[] parts = compactJwt.trim().split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("status list token is not a 3-part JWS");
        }
        JsonObject header = decodeJson(parts[0]);
        String typ = header.has("typ") ? header.get("typ").getAsString() : null;
        String alg = header.has("alg") ? header.get("alg").getAsString() : null;
        if (!"statuslist+jwt".equals(typ)) {
            throw new IllegalArgumentException("unexpected typ: " + typ);
        }
        if (!"ES256".equals(alg)) {
            throw new IllegalArgumentException("unexpected alg: " + alg);
        }
        JsonObject payload = decodeJson(parts[1]);
        if (!payload.has("sub")) throw new IllegalArgumentException("status list token has no sub");
        String sub = payload.get("sub").getAsString();
        long iat = payload.has("iat") ? payload.get("iat").getAsLong() : 0L;
        long exp = payload.has("exp") ? payload.get("exp").getAsLong() : 0L;
        long ttl = payload.has("ttl") ? payload.get("ttl").getAsLong() : 0L;
        if (!payload.has("status_list")) {
            throw new IllegalArgumentException("status list token has no status_list");
        }
        JsonObject sl = payload.getAsJsonObject("status_list");
        int bits = sl.has("bits") ? sl.get("bits").getAsInt() : 2;
        if (!sl.has("lst")) throw new IllegalArgumentException("status_list has no lst");
        String lst = sl.get("lst").getAsString();
        return new StatusListToken(sub, iat, exp, ttl, bits, lst);
    }

    private static JsonObject decodeJson(String b64u) {
        byte[] json = Base64.getUrlDecoder().decode(b64u);
        return JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
