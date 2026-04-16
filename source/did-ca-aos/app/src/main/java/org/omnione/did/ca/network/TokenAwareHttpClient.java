/*
 * Copyright 2025 OmniOne.
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

package org.omnione.did.ca.network;

import android.content.Context;

import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.communication.urlconnection.HttpUrlConnectionTask;

/**
 * Simple HTTP client wrapper.
 */
public class TokenAwareHttpClient {

    /**
     * Sends an HTTP request without any token handling.
     */
    public static String send(Context context, String url, String method, String request)
            throws CommunicationException {

        CaLog.d(url + " :: connect");
        return new HttpUrlConnectionTask().makeHttpRequest(url, method, request, null);
    }
}
