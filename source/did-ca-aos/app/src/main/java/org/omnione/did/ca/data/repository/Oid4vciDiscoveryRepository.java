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

package org.omnione.did.ca.data.repository;

import androidx.annotation.NonNull;

import org.omnione.did.ca.data.config.AppConfig;
import org.omnione.did.ca.data.model.Oid4vciCredentialChoice;
import org.omnione.did.ca.data.model.Oid4vciIssuerSection;
import org.omnione.did.ca.data.network.NetworkManager;
import org.omnione.did.ca.data.network.protocol.GetOid4vciIssuerListOp;
import org.omnione.did.ca.data.sdk.WalletGateway;
import org.omnione.did.ca.protocol.OID4VCIProtocol;
import org.omnione.did.ca.util.AppLog;
import org.omnione.did.sdk.core.oid4vc.model.CredentialConfigDescriptor;
import org.omnione.did.sdk.core.oid4vc.model.IssuerMetadataResponse;
import org.omnione.did.sdk.datamodel.oid4vc.OID4VCIIssuerItem;
import org.omnione.did.sdk.datamodel.oid4vc.OID4VCIIssuerList;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.wallet.walletservice.config.Constants;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class Oid4vciDiscoveryRepository {

    private final NetworkManager net;
    private final AppConfig config;
    private final OID4VCIProtocol protocol;
    private final UserIdentityRepository identity;
    private final WalletGateway walletGateway;
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    @Inject
    public Oid4vciDiscoveryRepository(NetworkManager net,
                                      AppConfig config,
                                      OID4VCIProtocol protocol,
                                      UserIdentityRepository identity,
                                      WalletGateway walletGateway) {
        this.net = net;
        this.config = config;
        this.protocol = protocol;
        this.identity = identity;
        this.walletGateway = walletGateway;
    }

    public CompletableFuture<List<Oid4vciIssuerSection>> loadCatalog() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = GetOid4vciIssuerListOp.call(net, config.tasUrl());
                OID4VCIIssuerList issuerList = MessageUtil.deserialize(body, OID4VCIIssuerList.class);
                List<Oid4vciIssuerSection> sections = new ArrayList<>();
                for (OID4VCIIssuerItem item : issuerList.getItems()) {
                    try {

                        String metadataBase = issuerBaseFromMetadataUri(item.getCredentialIssuerMetadataUri());
                        IssuerMetadataResponse metadata = protocol.getMetadata(metadataBase).get();
                        List<Oid4vciCredentialChoice> choices = toChoices(item, metadata);
                        if (!choices.isEmpty()) {

                            sections.add(new Oid4vciIssuerSection(item.getCredentialIssuer(), choices));
                        }
                    } catch (Exception metaErr) {

                        AppLog.w("Oid4vciDiscovery",
                                "metadata failed, skipping issuer=" + item.getCredentialIssuer()
                                        + " (" + metaErr.getMessage() + ")");
                    }
                }
                return sections;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, backgroundExecutor);
    }

    public CompletableFuture<String> buildUserInitiationUrl(@NonNull String userInitiationUri,
                                                            @NonNull String configurationId) {
        return CompletableFuture.supplyAsync(() -> {
            String userName = identity.getUserId();
            if (userName == null) userName = "";
            String holderDid;
            try {
                holderDid = walletGateway.getDIDDocument(Constants.DID_DOC_TYPE_HOLDER).get().getId();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
            return userInitiationUri
                    + "?did=" + enc(holderDid)
                    + "&userName=" + enc(userName)
                    + "&credential_configuration_id=" + enc(configurationId);
        }, backgroundExecutor);
    }

    private static List<Oid4vciCredentialChoice> toChoices(OID4VCIIssuerItem item,
                                                           IssuerMetadataResponse metadata) {
        List<Oid4vciCredentialChoice> choices = new ArrayList<>();
        Map<String, CredentialConfigDescriptor> cfgs = metadata.getConfigurationsSupported();
        if (cfgs == null) return choices;

        String expectedIssuer = (metadata.getCredentialIssuer() != null
                && !metadata.getCredentialIssuer().isEmpty())
                ? metadata.getCredentialIssuer()
                : item.getCredentialIssuer();
        for (Map.Entry<String, CredentialConfigDescriptor> e : cfgs.entrySet()) {
            CredentialConfigDescriptor d = e.getValue();
            String cfgId = (d != null && d.getConfigurationId() != null && !d.getConfigurationId().isEmpty())
                    ? d.getConfigurationId()
                    : e.getKey();
            if (cfgId == null || cfgId.isEmpty()) continue;
            choices.add(new Oid4vciCredentialChoice(
                    expectedIssuer,
                    item.getUserInitiationUri(),
                    cfgId,
                    d != null ? d.getFormat() : null,
                    d != null ? d.getVct() : null,
                    d != null ? d.getDoctype() : null,
                    d != null && d.isSdJwtVc(),
                    d != null && d.isMdoc()));
        }
        return choices;
    }

    private static String issuerBaseFromMetadataUri(String metadataUri) {
        String s = metadataUri.trim();
        int idx = s.indexOf("/.well-known/");
        if (idx > 0) return s.substring(0, idx);
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
