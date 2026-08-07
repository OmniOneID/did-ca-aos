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

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class StatusListDecoder {

    private static final int MAX_DECOMPRESSED_BYTES = 1024 * 1024;

    private StatusListDecoder() {}

    public static byte[] decodeList(String lst) throws DataFormatException {
        byte[] compressed = Base64.getUrlDecoder().decode(lst);
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                out.write(buf, 0, n);
                if (out.size() > MAX_DECOMPRESSED_BYTES) {
                    throw new DataFormatException("status list too large");
                }
            }
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }

    public static int statusAt(byte[] decoded, long idx, int bits) {
        if (idx < 0) throw new IllegalArgumentException("idx must be >= 0");
        if (bits != 1 && bits != 2 && bits != 4 && bits != 8) {
            throw new IllegalArgumentException("bits must be one of 1,2,4,8");
        }
        long bitIndex = idx * (long) bits;
        long byteIndexL = bitIndex / 8;
        if (byteIndexL < 0 || byteIndexL >= decoded.length) {
            throw new IndexOutOfBoundsException("idx out of range");
        }
        int byteIndex = (int) byteIndexL;
        int shift = (int) (bitIndex % 8);
        int mask = (1 << bits) - 1;
        return ((decoded[byteIndex] & 0xFF) >> shift) & mask;
    }
}
