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

import android.util.Log;

import org.omnione.did.ca.BuildConfig;

public class AppLog {
    private static boolean sEnabled = BuildConfig.DEBUG;
    private static final String sTag = "[DID_CA]";
    private static final int PRINT_LOG_MAX_LENGTH = 1024;

    private static String addPrefix(String tag, String msg) {
        return ("[" + tag + "] " + msg);
    }

    private static String addStackTraceInfo(String msg) {
        StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[4];
        return "[" +
                stackTraceElement.getFileName() +
                " -> " +
                stackTraceElement.getMethodName() +
                " -> #" +
                stackTraceElement.getLineNumber() +
                "] " +
                msg;
    }

    public static void w(String msg) {
        if (sEnabled) {
            String dbgMsg = addStackTraceInfo(msg);
            Log.w(sTag, dbgMsg);
        }
    }

    public static void w(String tag, String msg) {
        if (sEnabled) {
            String dbgMsg = addStackTraceInfo(addPrefix(tag, msg));
            Log.w(sTag, dbgMsg);
        }
    }

    public static void e(String msg) {
        if (sEnabled) {
            String dbgMsg = addStackTraceInfo(msg);
            Log.e(sTag, dbgMsg);
        }
    }

    public static void e(String tag, String msg) {
        if (sEnabled) {
            String dbgMsg = addStackTraceInfo(addPrefix(tag, msg));
            Log.e(sTag, dbgMsg);
        }
    }

    public static void e(String tag, String msg, Throwable tr) {
        if (sEnabled) {
            String dbgMsg = addStackTraceInfo(addPrefix(tag, msg));
            Log.e(sTag, dbgMsg, tr);
        }
    }

    public static void d(String msg) {
        if (sEnabled && BuildConfig.DEBUG) {
            String dbgMsg = addStackTraceInfo(msg);

            int index = 0;
            int logLength = dbgMsg.length();
            do {
                if ((logLength - index) > PRINT_LOG_MAX_LENGTH) {
                    int endIndex = index + PRINT_LOG_MAX_LENGTH;
                    Log.d(sTag, dbgMsg.substring(index, endIndex));
                    index += PRINT_LOG_MAX_LENGTH;
                } else {
                    Log.d(sTag, dbgMsg.substring(index, logLength));
                    index += (logLength - index);
                }
            } while (index < logLength);
        }
    }

    public static void d(String tag, String msg) {
        if (sEnabled && BuildConfig.DEBUG) {
            d(addPrefix(tag, msg));
        }
    }

    public static void i(String msg) {
        if (sEnabled) {
            String dbgMsg = addStackTraceInfo(msg);
            Log.i(sTag, dbgMsg);
        }
    }

    public static void i(String tag, String msg) {
        if (sEnabled) {
            String dbgMsg = addStackTraceInfo(addPrefix(tag, msg));
            Log.i(sTag, dbgMsg);
        }
    }

    public static void v(String msg) {
        if (sEnabled) {
            String dbgMsg = addStackTraceInfo(msg);
            Log.v(sTag, dbgMsg);
        }
    }

    public static void v(String tag, String msg) {
        if (sEnabled) {
            String dbgMsg = addStackTraceInfo(addPrefix(tag, msg));
            Log.v(sTag, dbgMsg);
        }
    }
}
