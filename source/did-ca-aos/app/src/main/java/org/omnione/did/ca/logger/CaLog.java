/*
 * Copyright 2024-2025 OmniOne.
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

package org.omnione.did.ca.logger;

import android.util.Log;

public class CaLog {
    private static String lineOut() {
        int level = 4;
        StackTraceElement[] traces;
        traces = Thread.currentThread().getStackTrace();
        return ("at " + traces[level] + " ");
    }

    private static String buildLogMsg(String message) {
        StackTraceElement ste = Thread.currentThread().getStackTrace()[4];
        StringBuilder sb = new StringBuilder();
        if(ste.getFileName() != null) {
            sb.append(" [ ");
            sb.append(ste.getFileName().replace(".java", ""));
            sb.append(" :: ");
            sb.append(ste.getMethodName());
            sb.append(" ] ");
            sb.append(message);
        }
        return sb.toString();
    }

    public static void d(String logMsg) {
        Log.d("CA_LOG", buildLogMsg(logMsg + " :: " + lineOut()));
    }
    public static void e(String logMsg) {
        Log.e("CA_LOG", buildLogMsg(logMsg + " :: " + lineOut()));
    }
    public static void v(String logMsg) {
        Log.v("CA_LOG", buildLogMsg(logMsg + " :: " + lineOut()));
    }
    public static void i(String logMsg) {
        Log.i("CA_LOG", buildLogMsg(logMsg + " :: " + lineOut()));
    }
}
