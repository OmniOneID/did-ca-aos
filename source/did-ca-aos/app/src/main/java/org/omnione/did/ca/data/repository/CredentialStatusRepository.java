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
import androidx.annotation.Nullable;

import org.omnione.did.ca.data.model.CredentialBadge;
import org.omnione.did.ca.data.statuslist.CredentialStatusRef;
import org.omnione.did.ca.data.statuslist.StatusListResult;
import org.omnione.did.ca.util.AppLog;
import org.omnione.did.sdk.datamodel.vc.issue.VcStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class CredentialStatusRepository {

    private static final String TAG = "CaStatusList";

    private final VcMetaRepository vcMetaRepo;
    private final StatusListRepository statusListRepo;

    private final Map<String, VcStatus> known = new ConcurrentHashMap<>();
    private final Map<String, VcStatus> pending = new ConcurrentHashMap<>();

    @Inject
    public CredentialStatusRepository(VcMetaRepository vcMetaRepo,
                                      StatusListRepository statusListRepo) {
        this.vcMetaRepo = vcMetaRepo;
        this.statusListRepo = statusListRepo;
    }

    @NonNull
    public CompletableFuture<Map<String, VcStatus>> refreshAll(@NonNull List<CredentialStatusRef> refs) {
        if (refs.isEmpty()) {
            pending.clear();
            known.clear();
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }
        statusListRepo.invalidate();

        Map<String, VcStatus> resolved = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>(refs.size());
        for (CredentialStatusRef ref : refs) {
            futures.add(check(ref).thenAccept(
                    result -> resolved.put(ref.vcId, record(ref, result, false))));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(unused -> {
                    Set<String> ids = new HashSet<>(resolved.keySet());
                    known.keySet().retainAll(ids);
                    pending.clear();
                    return new HashMap<>(resolved);
                });
    }

    @NonNull
    public CompletableFuture<StatusListResult> refreshOne(@NonNull CredentialStatusRef ref) {
        if (ref.listRef != null) {
            statusListRepo.invalidate(ref.listRef.uri);
        }
        return check(ref).thenApply(result -> {
            VcStatus resolved = record(ref, result, true);
            return resolved == result.status
                    ? result
                    : new StatusListResult(resolved, result.checkFailed, result.failMessageRes);
        });
    }

    @Nullable
    public VcStatus known(@NonNull String vcId) {
        return known.get(vcId);
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    @NonNull
    public Map<String, VcStatus> consumePending() {
        Map<String, VcStatus> snapshot = new HashMap<>(pending);
        for (Map.Entry<String, VcStatus> entry : snapshot.entrySet()) {
            pending.remove(entry.getKey(), entry.getValue());
        }
        return snapshot;
    }

    @NonNull
    private CompletableFuture<StatusListResult> check(@NonNull CredentialStatusRef ref) {
        if (ref.badge == CredentialBadge.SD_JWT) {
            return statusListRepo.check(ref.listRef, ref.expEpochSec);
        }
        return vcMetaRepo.check(ref.vcId, null);
    }

    @NonNull
    private VcStatus record(@NonNull CredentialStatusRef ref,
                            @NonNull StatusListResult result,
                            boolean trackChange) {
        VcStatus previous = known.get(ref.vcId);
        if (result.checkFailed) {
            AppLog.d(TAG, "status check failed, keeping last known for " + ref.vcId
                    + " (known=" + previous + ")");
            return previous != null ? previous : expiryFallback(ref.expEpochSec);
        }
        known.put(ref.vcId, result.status);
        if (trackChange && previous != null && previous != result.status) {
            pending.put(ref.vcId, result.status);
        }
        return result.status;
    }

    @NonNull
    private static VcStatus expiryFallback(@Nullable Long expEpochSec) {
        if (expEpochSec != null && expEpochSec <= System.currentTimeMillis() / 1000L) {
            return VcStatus.REVOKED;
        }
        return VcStatus.ACTIVE;
    }
}
