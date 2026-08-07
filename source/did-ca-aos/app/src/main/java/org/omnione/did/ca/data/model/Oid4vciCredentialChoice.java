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

public final class Oid4vciCredentialChoice {

    @NonNull public final String expectedIssuer;
    @NonNull public final String userInitiationUri;
    @NonNull public final String configurationId;
    @Nullable public final String format;
    @Nullable public final String vct;
    @Nullable public final String doctype;
    public final boolean sdJwtVc;
    public final boolean mdoc;

    public Oid4vciCredentialChoice(@NonNull String expectedIssuer,
                                   @NonNull String userInitiationUri,
                                   @NonNull String configurationId,
                                   @Nullable String format,
                                   @Nullable String vct,
                                   @Nullable String doctype,
                                   boolean sdJwtVc,
                                   boolean mdoc) {
        this.expectedIssuer = expectedIssuer;
        this.userInitiationUri = userInitiationUri;
        this.configurationId = configurationId;
        this.format = format;
        this.vct = vct;
        this.doctype = doctype;
        this.sdJwtVc = sdJwtVc;
        this.mdoc = mdoc;
    }

}
