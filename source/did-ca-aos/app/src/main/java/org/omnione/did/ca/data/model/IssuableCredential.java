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

package org.omnione.did.ca.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class IssuableCredential {

    public static final String MODE_DIRECT = "DIRECT";
    public static final String MODE_PROXY  = "PROXY";

    @NonNull public final String vcPlanId;
    @NonNull public final String allowedIssuerDid;
    @NonNull public final String displayName;
    @NonNull public final String description;
    @NonNull public final String issuerName;
    @NonNull public final String issuanceMode;
    @Nullable public final String proxyEndpoint;

    @NonNull public final CredentialBadge badge;

    @Nullable public final String userInitiationUri;

    @Nullable public final String oid4vciConfigurationId;

    @Nullable public final String oid4vciExpectedIssuer;

    public IssuableCredential(@NonNull String vcPlanId,
                              @NonNull String allowedIssuerDid,
                              @NonNull String displayName,
                              @NonNull String description,
                              @NonNull String issuerName,
                              @NonNull String issuanceMode,
                              @Nullable String proxyEndpoint,
                              @NonNull CredentialBadge badge) {
        this(vcPlanId, allowedIssuerDid, displayName, description, issuerName, issuanceMode,
                proxyEndpoint, badge, null, null, null);
    }

    private IssuableCredential(@NonNull String vcPlanId,
                               @NonNull String allowedIssuerDid,
                               @NonNull String displayName,
                               @NonNull String description,
                               @NonNull String issuerName,
                               @NonNull String issuanceMode,
                               @Nullable String proxyEndpoint,
                               @NonNull CredentialBadge badge,
                               @Nullable String userInitiationUri,
                               @Nullable String oid4vciConfigurationId,
                               @Nullable String oid4vciExpectedIssuer) {
        this.vcPlanId = vcPlanId;
        this.allowedIssuerDid = allowedIssuerDid;
        this.displayName = displayName;
        this.description = description;
        this.issuerName = issuerName;
        this.issuanceMode = issuanceMode;
        this.proxyEndpoint = proxyEndpoint;
        this.badge = badge;
        this.userInitiationUri = userInitiationUri;
        this.oid4vciConfigurationId = oid4vciConfigurationId;
        this.oid4vciExpectedIssuer = oid4vciExpectedIssuer;
    }

    @NonNull
    public static IssuableCredential oid4vci(@NonNull String issuerName,
                                             @NonNull String displayName,
                                             @NonNull CredentialBadge badge,
                                             @NonNull String userInitiationUri,
                                             @NonNull String configurationId,
                                             @NonNull String expectedIssuer) {
        return new IssuableCredential(configurationId, expectedIssuer, displayName, "",
                issuerName, MODE_DIRECT, null, badge,
                userInitiationUri, configurationId, expectedIssuer);
    }

    public boolean isProxy() {
        return MODE_PROXY.equalsIgnoreCase(issuanceMode);
    }

    public boolean isOid4vci() {
        return userInitiationUri != null;
    }
}
