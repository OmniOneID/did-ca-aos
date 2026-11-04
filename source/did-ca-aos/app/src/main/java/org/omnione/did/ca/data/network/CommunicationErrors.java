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

package org.omnione.did.ca.data.network;

import androidx.annotation.Nullable;

import org.omnione.did.sdk.communication.exception.CommunicationErrorCode;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

public final class CommunicationErrors {

    @Nullable
    public static String findDetailMessage(@Nullable Throwable err) {
        Throwable cause = err;
        while (cause != null) {
            if (cause instanceof CommunicationException) {
                return cause.getMessage();
            }
            cause = cause.getCause();
        }
        return null;
    }

    @Nullable
    public static String findSdkMessage(@Nullable Throwable err) {
        String deepest = null;
        Throwable cause = err;
        while (cause != null) {
            if (isSdkException(cause)) {
                String message = cause.getMessage();
                if (message != null && !message.isEmpty()) deepest = message;
            }
            cause = cause.getCause();
        }
        return deepest;
    }

    private static boolean isSdkException(Throwable t) {
        return t instanceof CommunicationException
                || t instanceof WalletException
                || t instanceof WalletCoreException
                || t instanceof UtilityException;
    }

    public static boolean isUnreachable(@Nullable Throwable err) {
        Throwable cause = err;
        while (cause != null) {
            if (cause instanceof CommunicationException) {
                return ((CommunicationException) cause).getErrorCode()
                        == CommunicationErrorCode
                                .ERR_CODE_COMMUNICATION_INCORRECT_URL_CONNECTION.getCode();
            }
            cause = cause.getCause();
        }
        return false;
    }

    private CommunicationErrors() {}
}
