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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class ClaimImages {

    private static final Set<String> IMAGE_ELEMENTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("portrait", "picture", "signature_usual_mark")));

    private static final String DATA_URI_PREFIX = "data:";
    private static final String BASE64_MARKER = ";base64,";
    private static final int DECODE_GUARD_PX = 2048;

    private ClaimImages() {}

    public static boolean isImageElement(@Nullable String label) {
        if (label == null || label.isEmpty()) {
            return false;
        }
        int dot = label.lastIndexOf('.');
        String element = dot >= 0 && dot < label.length() - 1 ? label.substring(dot + 1) : label;
        return IMAGE_ELEMENTS.contains(element.toLowerCase(Locale.ROOT));
    }

    @Nullable
    public static byte[] toImageBytes(@Nullable Object value) {
        if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            return bytes.length == 0 ? null : bytes;
        }
        if (value instanceof String) {
            return decodeBase64((String) value);
        }
        return null;
    }

    @Nullable
    private static byte[] decodeBase64(@NonNull String value) {
        String payload = value.trim();
        if (payload.isEmpty()) {
            return null;
        }
        if (payload.startsWith(DATA_URI_PREFIX)) {
            int marker = payload.indexOf(BASE64_MARKER);
            if (marker < 0) {
                return null;
            }
            payload = payload.substring(marker + BASE64_MARKER.length());
        }
        byte[] decoded = tryDecode(payload, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        if (decoded == null) {
            decoded = tryDecode(payload, Base64.DEFAULT);
        }
        return decoded == null || decoded.length == 0 ? null : decoded;
    }

    @Nullable
    private static byte[] tryDecode(@NonNull String payload, int flags) {
        try {
            return Base64.decode(payload, flags);
        } catch (IllegalArgumentException notBase64) {
            return null;
        }
    }

    @Nullable
    public static Bitmap decode(@NonNull Context context, @Nullable byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (OutOfMemoryError tooLarge) {
            return null;
        }
        if (bitmap == null) {
            return null;
        }
        bitmap.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        return bitmap;
    }

    private static int sampleSize(int width, int height) {
        int sample = 1;
        while (width / sample > DECODE_GUARD_PX || height / sample > DECODE_GUARD_PX) {
            sample *= 2;
        }
        return sample;
    }

    public static int maxWidthPx(@NonNull Context context, int insetDp) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int inset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, insetDp, metrics);
        return Math.max(1, metrics.widthPixels - inset);
    }
}
