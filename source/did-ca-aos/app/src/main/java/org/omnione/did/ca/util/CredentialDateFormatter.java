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

import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class CredentialDateFormatter {

    private CredentialDateFormatter() { }

    private static final String DISPLAY_PATTERN = "dd MMM yyyy";

    private static final String[] INPUT_PATTERNS = {
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd",
            "yyyyMMdd",
    };

    @Nullable
    public static String format(@Nullable String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;

        Date parsed = parse(trimmed);
        if (parsed == null) return trimmed;

        SimpleDateFormat out = new SimpleDateFormat(DISPLAY_PATTERN, Locale.US);
        out.setTimeZone(TimeZone.getTimeZone("UTC"));
        return out.format(parsed);
    }

    @Nullable
    public static Long toEpochSeconds(@Nullable String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;

        Date parsed = parse(trimmed);
        return parsed == null ? null : parsed.getTime() / 1000L;
    }

    @Nullable
    private static Date parse(@Nullable String trimmed) {
        for (String pattern : INPUT_PATTERNS) {
            SimpleDateFormat in = new SimpleDateFormat(pattern, Locale.US);
            in.setLenient(false);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            try {
                Date d = in.parse(trimmed);
                if (d != null) return d;
            } catch (ParseException ignored) {

            }
        }
        return null;
    }
}
