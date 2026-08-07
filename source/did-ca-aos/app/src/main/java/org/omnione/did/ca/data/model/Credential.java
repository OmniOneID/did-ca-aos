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

import org.omnione.did.sdk.datamodel.vc.issue.VcStatus;

import java.util.Objects;

public final class Credential {

    @NonNull
    public final String id;
    @NonNull
    public final String name;
    @NonNull
    public final CredentialBadge badge;
    @NonNull
    public final String issuer;
    @Nullable
    public final String issued;
    @Nullable
    public final String valid;
    @NonNull
    public final VcStatus status;
    public final boolean zkp;

    public Credential(@NonNull String id,
                      @NonNull String name,
                      @NonNull CredentialBadge badge,
                      @NonNull String issuer,
                      @Nullable String issued,
                      @Nullable String valid,
                      @NonNull VcStatus status,
                      boolean zkp) {
        this.id = id;
        this.name = name;
        this.badge = badge;
        this.issuer = issuer;
        this.issued = issued;
        this.valid = valid;
        this.status = status;
        this.zkp = zkp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Credential)) return false;
        Credential other = (Credential) o;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @NonNull
    public Credential withStatus(@NonNull VcStatus newStatus) {
        return new Credential(id, name, badge, issuer, issued, valid, newStatus, zkp);
    }
}
