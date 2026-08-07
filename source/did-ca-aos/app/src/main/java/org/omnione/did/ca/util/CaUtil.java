/*
 * Copyright 2024-2026 OmniOne.
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

import android.content.Context;

import org.omnione.did.sdk.utility.CryptoUtils;
import org.omnione.did.sdk.utility.Encodings.Base16;
import org.omnione.did.sdk.utility.Errors.UtilityException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CaUtil {

    private CaUtil() {}

    public static String createMessageId(Context ctx) {
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSSSSS", Locale.US);
        try {
            return df.format(new Date()) + Base16.toHex(CryptoUtils.generateNonce(4));
        } catch (UtilityException e) {
            throw new RuntimeException(e);
        }
    }

    public static String createCaAppId() {
        SimpleDateFormat df = new SimpleDateFormat("yyyyMM", Locale.US);
        try {
            return "AID" + df.format(new Date()) + "a" + Base16.toHex(CryptoUtils.generateNonce(5));
        } catch (UtilityException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getPackageName(Context ctx) {
        return ctx.getPackageName();
    }

    public static boolean isAuthError(Throwable err) {
        Throwable cause = err;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && (msg.toLowerCase().contains("auth")
                    || msg.toLowerCase().contains("pin"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
