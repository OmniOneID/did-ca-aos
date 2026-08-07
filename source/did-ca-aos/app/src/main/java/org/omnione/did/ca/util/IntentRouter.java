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
import android.content.Intent;

import org.omnione.did.ca.ui.biometric.AuthMethodActivity;
import org.omnione.did.ca.ui.biometric.BiometricSetupActivity;
import org.omnione.did.ca.ui.docs.DocsActivity;
import org.omnione.did.ca.ui.issue.IssueByQrActivity;
import org.omnione.did.ca.ui.issue.IssueProtocol;
import org.omnione.did.ca.ui.issue.IssueSuccessActivity;
import org.omnione.did.ca.ui.issue.IssuerListActivity;
import org.omnione.did.ca.ui.issue.ProtocolSelectActivity;
import org.omnione.did.ca.ui.issuebyoid4vci.IssueByOid4vciActivity;
import org.omnione.did.ca.ui.onboarding.OnboardingActivity;
import org.omnione.did.ca.ui.pin.PinActivity;
import org.omnione.did.ca.ui.pin.PinMode;
import org.omnione.did.ca.ui.proximity.ProximityActivity;
import org.omnione.did.ca.ui.qr.QrScanActivity;
import org.omnione.did.ca.ui.qr.QrScanMode;
import org.omnione.did.ca.ui.referent.ReferentListActivity;
import org.omnione.did.ca.ui.settings.SettingsActivity;
import org.omnione.did.ca.ui.splash.SplashActivity;
import org.omnione.did.ca.ui.txcode.TxCodeActivity;
import org.omnione.did.ca.ui.vc.VcDetailActivity;
import org.omnione.did.ca.ui.vp.VpRequestActivity;
import org.omnione.did.ca.ui.web.ClaimWebActivity;
import org.omnione.did.ca.ui.web.Oid4vciUserInitWebActivity;

public final class IntentRouter {

    public static final String EXTRA_VC_ID = "extra_vc_id";

    public static final String EXTRA_DISPLAY_NAME = "extra_display_name";

    public static final String EXTRA_DISPLAY_ONLY = "extra_display_only";

    private IntentRouter() {}

    public static void startOnboarding(Context context, int step) {
        startOnboarding(context, step, false);
    }

    public static void startOnboarding(Context context, int step, boolean viaBio) {
        Intent intent = new Intent(context, OnboardingActivity.class);
        intent.putExtra(OnboardingActivity.EXTRA_STEP, step);
        intent.putExtra(OnboardingActivity.EXTRA_VIA_BIO, viaBio);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static Intent pinRegisterSignIntent(Context context, boolean viaBio) {
        Intent intent = new Intent(context, PinActivity.class);
        intent.putExtra(PinActivity.EXTRA_MODE, PinMode.REGISTER_SIGN.name());
        intent.putExtra(PinActivity.EXTRA_VIA_BIOMETRIC, viaBio);
        return intent;
    }

    public static void startDocs(Context context) {
        Intent intent = new Intent(context, DocsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static Intent protocolSelectIntent(Context context) {
        return new Intent(context, ProtocolSelectActivity.class);
    }

    public static Intent issuerListIntent(Context context) {
        return issuerListIntent(context, IssueProtocol.OPEN_DID);
    }

    public static Intent issuerListIntent(Context context, IssueProtocol protocol) {
        Intent intent = new Intent(context, IssuerListActivity.class);
        intent.putExtra(IssuerListActivity.EXTRA_PROTOCOL, protocol.name());
        return intent;
    }

    public static Intent claimWebIntent(Context context, String url, String afterAuthTag) {
        Intent intent = new Intent(context, ClaimWebActivity.class);
        intent.putExtra(ClaimWebActivity.EXTRA_URL, url);
        if (afterAuthTag != null) intent.putExtra(ClaimWebActivity.EXTRA_AFTER_AUTH, afterAuthTag);
        return intent;
    }

    public static Intent issueSuccessIntent(Context context, String vcId, String displayName) {
        Intent intent = new Intent(context, IssueSuccessActivity.class);
        intent.putExtra(EXTRA_VC_ID, vcId);
        if (displayName != null) intent.putExtra(EXTRA_DISPLAY_NAME, displayName);
        return intent;
    }

    public static Intent issueSuccessDisplayOnlyIntent(Context context, String vcId, String displayName) {
        Intent intent = new Intent(context, IssueSuccessActivity.class);
        intent.putExtra(EXTRA_VC_ID, vcId);
        intent.putExtra(EXTRA_DISPLAY_ONLY, true);
        if (displayName != null) intent.putExtra(EXTRA_DISPLAY_NAME, displayName);
        return intent;
    }

    public static Intent pinRegisterIntent(Context context, int onboardingNext, boolean viaBiometric) {
        Intent intent = new Intent(context, PinActivity.class);
        intent.putExtra(PinActivity.EXTRA_MODE, PinMode.REGISTER.name());
        intent.putExtra(PinActivity.EXTRA_ONBOARDING_NEXT, onboardingNext);
        intent.putExtra(PinActivity.EXTRA_VIA_BIOMETRIC, viaBiometric);
        return intent;
    }

    public static Intent pinChangeIntent(Context context) {
        Intent intent = new Intent(context, PinActivity.class);
        intent.putExtra(PinActivity.EXTRA_MODE, PinMode.CHANGE.name());
        return intent;
    }

    public static Intent pinUnlockRegisterIntent(Context context) {
        Intent intent = new Intent(context, PinActivity.class);
        intent.putExtra(PinActivity.EXTRA_MODE, PinMode.UNLOCK_REGISTER.name());
        return intent;
    }

    public static Intent pinUnlockRegisterIntent(Context context, int onboardingNext) {
        Intent intent = new Intent(context, PinActivity.class);
        intent.putExtra(PinActivity.EXTRA_MODE, PinMode.UNLOCK_REGISTER.name());
        intent.putExtra(PinActivity.EXTRA_ONBOARDING_NEXT, onboardingNext);
        return intent;
    }

    public static Intent pinUnlockChangeIntent(Context context) {
        Intent intent = new Intent(context, PinActivity.class);
        intent.putExtra(PinActivity.EXTRA_MODE, PinMode.UNLOCK_CHANGE.name());
        return intent;
    }

    public static Intent pinAuthIntent(Context context, String afterAuth) {
        Intent intent = new Intent(context, PinActivity.class);
        intent.putExtra(PinActivity.EXTRA_MODE, PinMode.AUTH.name());
        if (afterAuth != null) intent.putExtra(PinActivity.EXTRA_AFTER_AUTH, afterAuth);
        return intent;
    }

    public static void startPinAuth(Context context) {
        Intent intent = new Intent(context, PinActivity.class);
        intent.putExtra(PinActivity.EXTRA_MODE, PinMode.UNLOCK_AUTH.name());
        intent.putExtra(PinActivity.EXTRA_ONBOARDING_DONE, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static Intent biometricSetupIntent(Context context, int onboardingNext, boolean fromSettings) {
        Intent intent = new Intent(context, BiometricSetupActivity.class);
        intent.putExtra(BiometricSetupActivity.EXTRA_ONBOARDING_NEXT, onboardingNext);
        intent.putExtra(BiometricSetupActivity.EXTRA_FROM_SETTINGS, fromSettings);
        return intent;
    }

    public static Intent authMethodChooserIntent(Context context) {
        Intent intent = new Intent(context, AuthMethodActivity.class);
        intent.putExtra(AuthMethodActivity.EXTRA_RETURN_METHOD, true);
        return intent;
    }

    public static void startProximity(Context context) {
        context.startActivity(new Intent(context, ProximityActivity.class));
    }

    public static void startQrScan(Context context) {
        Intent intent = new Intent(context, QrScanActivity.class);
        intent.putExtra(QrScanActivity.EXTRA_MODE, QrScanMode.PRESENT.name());
        context.startActivity(intent);
    }

    public static Intent qrScanIssueIntent(Context context) {
        Intent intent = new Intent(context, QrScanActivity.class);
        intent.putExtra(QrScanActivity.EXTRA_MODE, QrScanMode.ISSUE.name());
        return intent;
    }

    public static Intent issueByQrIntent(Context context, String rawPayload) {
        Intent intent = new Intent(context, IssueByQrActivity.class);
        intent.putExtra(IssueByQrActivity.EXTRA_PAYLOAD, rawPayload);
        return intent;
    }

    public static Intent issueByOid4vciIntent(Context context, String rawPayload) {
        return issueByOid4vciIntent(context, rawPayload, null);
    }

    public static Intent issueByOid4vciIntent(Context context, String rawPayload,
                                              String expectedIssuer) {
        Intent intent = new Intent(context, IssueByOid4vciActivity.class);
        intent.putExtra(IssueByOid4vciActivity.EXTRA_PAYLOAD, rawPayload);
        if (expectedIssuer != null) {
            intent.putExtra(IssueByOid4vciActivity.EXTRA_EXPECTED_ISSUER, expectedIssuer);
        }
        return intent;
    }

    public static Intent oid4vciUserInitWebIntent(Context context, String startUrl,
                                                  String expectedIssuer) {
        Intent intent = new Intent(context, Oid4vciUserInitWebActivity.class);
        intent.putExtra(Oid4vciUserInitWebActivity.EXTRA_START_URL, startUrl);
        if (expectedIssuer != null) {
            intent.putExtra(Oid4vciUserInitWebActivity.EXTRA_EXPECTED_ISSUER, expectedIssuer);
        }
        return intent;
    }

    public static void startIssueFromQrPayload(Context context, String rawPayload) {
        Intent intent = isOid4vciOffer(rawPayload)
                ? issueByOid4vciIntent(context, rawPayload)
                : issueByQrIntent(context, rawPayload);
        context.startActivity(intent);
    }

    private static boolean isOid4vciOffer(@android.annotation.SuppressLint("UnknownNullness") String rawPayload) {
        if (rawPayload == null) return false;
        String trimmed = rawPayload.trim();
        if (trimmed.startsWith("openid-credential-offer://")) return true;
        return trimmed.startsWith("{") && trimmed.contains("\"credential_issuer\"");
    }

    public static void startVpRequest(Context context, String rawPayload) {
        Intent intent = new Intent(context, VpRequestActivity.class);
        intent.putExtra(VpRequestActivity.EXTRA_PAYLOAD, rawPayload);
        context.startActivity(intent);
    }

    public static void startSettings(Context context) {
        context.startActivity(new Intent(context, SettingsActivity.class));
    }

    public static Intent referentListIntent(Context context,
                                            String referentKey,
                                            String title,
                                            java.util.ArrayList<String> optionLabels,
                                            java.util.ArrayList<String> optionValues,
                                            java.util.ArrayList<String> optionCredentialIds,
                                            String initialSelectedValue) {
        Intent intent = new Intent(context, ReferentListActivity.class);
        intent.putExtra(ReferentListActivity.EXTRA_REFERENT_KEY, referentKey);
        intent.putExtra(ReferentListActivity.EXTRA_TITLE, title);
        intent.putStringArrayListExtra(ReferentListActivity.EXTRA_OPTION_LABELS, optionLabels);
        intent.putStringArrayListExtra(ReferentListActivity.EXTRA_OPTION_VALUES, optionValues);
        intent.putStringArrayListExtra(ReferentListActivity.EXTRA_OPTION_CRED_IDS,
                optionCredentialIds);
        if (initialSelectedValue != null) {
            intent.putExtra(ReferentListActivity.EXTRA_INITIAL_VALUE, initialSelectedValue);
        }
        return intent;
    }

    public static Intent txCodeIntent(Context context,
                                      int length,
                                      String inputMode,
                                      String description) {
        Intent intent = new Intent(context, TxCodeActivity.class);
        intent.putExtra(TxCodeActivity.EXTRA_LENGTH, length);
        if (inputMode != null) intent.putExtra(TxCodeActivity.EXTRA_INPUT_MODE, inputMode);
        if (description != null) intent.putExtra(TxCodeActivity.EXTRA_DESCRIPTION, description);
        return intent;
    }

    public static Intent vcDetailIntent(Context context, String credentialId) {
        Intent intent = new Intent(context, VcDetailActivity.class);
        intent.putExtra(VcDetailActivity.EXTRA_CREDENTIAL_ID, credentialId);
        return intent;
    }

    public static Intent splashClearTaskIntent(Context context) {
        Intent intent = new Intent(context, SplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
