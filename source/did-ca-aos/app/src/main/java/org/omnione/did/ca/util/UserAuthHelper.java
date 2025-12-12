package org.omnione.did.ca.util;

import android.content.Context;

import org.omnione.did.ca.config.Preference;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.exception.WalletCoreException;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.wallet.walletservice.exception.WalletException;

public class UserAuthHelper {

    public enum AuthMethod {
        PIN("pin"),
        BIOMETRIC("bio");

        private String authMethod;

        AuthMethod(String authMethod) {
            this.authMethod = authMethod;
        }

        public String getAuthMethod() {
            return authMethod;
        }
    }

    public static boolean isPinAuthAvailable(Context context) {

        boolean isPinActuallyAvailable = false;
        String holderDIDDoc = "";
        try {
            WalletApi api = WalletApi.getInstance(context);
            holderDIDDoc = api.getDIDDocument(2).getId();
            isPinActuallyAvailable = api.isSavedKey("pin");
        } catch (WalletCoreException | UtilityException | WalletException e) {
            CaLog.d("isPinAuthAvailable ex: " + e.getMessage());
        }

        if (!holderDIDDoc.isEmpty() && isPinActuallyAvailable) {
            return true;
        } else {
            CaLog.d("it's not available now. returning pin.");
            return false;
        }
    }

    public static boolean isBiometricAuthAvailable(Context context, boolean checkDidDocOpt) {

        boolean isBioActuallyAvailable = false;
        String holderDIDDoc = "";
        try {
            WalletApi api = WalletApi.getInstance(context);
            if (checkDidDocOpt) {
                holderDIDDoc = api.getDIDDocument(2).getId();
            }
            isBioActuallyAvailable = api.isSavedKey("bio");
        } catch (WalletCoreException | UtilityException | WalletException e) {
            CaLog.d("isBiometricAuthAvailable ex: " + e.getMessage());
        }

        if (!checkDidDocOpt) {
            CaLog.d("isBioActuallyAvailable : " + isBioActuallyAvailable);
            return isBioActuallyAvailable;
        }

        if (!holderDIDDoc.isEmpty() && isBioActuallyAvailable) {
            return true;
        } else {
            CaLog.d("it's not available now. returning biometric.");
            return false;
        }
    }

    public static AuthMethod getDefaultAuthMethod(Context context) {

        String defaultAuthMethod = Preference.getDefaultAuthenticator(context);
        if ("bio".equals(defaultAuthMethod)) {
            boolean isBioActuallyAvailable = false;
            try {
                WalletApi api = WalletApi.getInstance(context);
                isBioActuallyAvailable = api.isSavedKey("bio");
            } catch (WalletCoreException | UtilityException | WalletException e) {
                CaLog.d("Default is bio, but error checking bio key. Falling back to PIN.");
            }

            if (isBioActuallyAvailable) {
                return AuthMethod.BIOMETRIC;
            } else {
                CaLog.d("Default was bio, but it's not available now. returning pin.");
                return AuthMethod.PIN;
            }
        } else if ("pin".equals(defaultAuthMethod)) {
            return AuthMethod.PIN;
        } else {
            CaLog.d("Unknown default authenticator value in sharedPreferences: " + defaultAuthMethod + ". defaulting to PIN.");
            return AuthMethod.PIN;
        }
    }

    public static void setDefaultAuthMethod(Context context, AuthMethod method) {

        String defaultAuthMethod = "pin";
        if (method.equals(AuthMethod.BIOMETRIC)) {
            boolean isBioKeySaved = false;
            try {
                WalletApi api = WalletApi.getInstance(context);
                isBioKeySaved = api.isSavedKey("bio");
            } catch (WalletCoreException | UtilityException | WalletException e) {
                CaLog.d("Error checking for bio key, defaulting to PIN for this attempt.");
            }

            if (isBioKeySaved) {
                defaultAuthMethod = "bio";
            }
        }
        Preference.setDefaultAuthenticator(context, defaultAuthMethod);
    }
}