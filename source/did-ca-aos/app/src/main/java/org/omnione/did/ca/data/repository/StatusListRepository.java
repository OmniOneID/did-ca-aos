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

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.config.AppConfig;
import org.omnione.did.ca.data.statuslist.StatusListDecoder;
import org.omnione.did.ca.data.statuslist.StatusListFetcher;
import org.omnione.did.ca.data.statuslist.StatusListReference;
import org.omnione.did.ca.data.statuslist.StatusListResult;
import org.omnione.did.ca.data.statuslist.StatusListToken;
import org.omnione.did.ca.util.AppLog;
import org.omnione.did.sdk.core.oid4vc.verify.IssuerSignatureVerifier;
import org.omnione.did.sdk.datamodel.vc.issue.VcStatus;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class StatusListRepository {

    private static final String TAG = "CaStatusList";
    private static final long IAT_SKEW_SEC = 60L;

    private final AppConfig config;
    private final Executor executor = Executors.newCachedThreadPool();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private final Map<String, CompletableFuture<Cached>> inFlight = new ConcurrentHashMap<>();

    @Inject
    public StatusListRepository(AppConfig config) {
        this.config = config;
    }

    @NonNull
    public CompletableFuture<StatusListResult> check(@Nullable StatusListReference ref,
                                                     @Nullable Long expEpochSec) {
        return CompletableFuture.supplyAsync(() -> checkBlocking(ref, expEpochSec), executor);
    }

    public void invalidate() {
        cache.clear();
    }

    public void invalidate(@Nullable String uri) {
        if (uri == null) {
            cache.clear();
        } else {
            cache.remove(uri);
        }
    }

    private StatusListResult checkBlocking(@Nullable StatusListReference ref,
                                           @Nullable Long expEpochSec) {
        long nowSec = System.currentTimeMillis() / 1000L;
        if (expEpochSec != null && expEpochSec <= nowSec) {

            return new StatusListResult(VcStatus.REVOKED, false, null);
        }
        if (ref == null) {
            return new StatusListResult(VcStatus.ACTIVE, false, null);
        }
        try {
            int statusInt = resolveStatusInt(ref, nowSec);
            VcStatus mapped;
            switch (statusInt) {
                case 0:  mapped = VcStatus.ACTIVE;   break;
                case 1:  mapped = VcStatus.REVOKED;  break;
                case 2:  mapped = VcStatus.INACTIVE; break;
                default: mapped = VcStatus.INACTIVE; break;
            }
            return new StatusListResult(mapped, false, null);
        } catch (IOException e) {
            AppLog.e(TAG, "status list fetch failed for " + ref.uri, e);
            return new StatusListResult(fallback(expEpochSec, nowSec), true,
                    R.string.status_check_fetch_failed);
        } catch (Exception e) {
            AppLog.e(TAG, "status list verify/decode failed for " + ref.uri, e);
            return new StatusListResult(fallback(expEpochSec, nowSec), true,
                    R.string.status_check_verify_failed);
        }
    }

    private int resolveStatusInt(@NonNull StatusListReference ref, long nowSec) throws Exception {
        Cached cached = loadOrGet(ref.uri, nowSec);
        return StatusListDecoder.statusAt(cached.decodedList, ref.idx, cached.bits);
    }

    @NonNull
    private Cached loadOrGet(@NonNull String uri, long nowSec) throws Exception {
        Cached cached = cache.get(uri);
        if (cached != null) {
            return cached;
        }
        CompletableFuture<Cached> mine = new CompletableFuture<>();
        CompletableFuture<Cached> running = inFlight.putIfAbsent(uri, mine);
        if (running != null) {
            return await(running);
        }
        try {
            Cached again = cache.get(uri);
            if (again != null) {
                mine.complete(again);
                return again;
            }
            Cached fresh = doLoad(uri, nowSec);
            cache.put(uri, fresh);
            mine.complete(fresh);
            return fresh;
        } catch (Exception e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(uri, mine);
        }
    }

    @NonNull
    private static Cached await(@NonNull CompletableFuture<Cached> running) throws Exception {
        try {
            return running.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            if (cause instanceof Exception) throw (Exception) cause;
            throw e;
        }
    }

    @NonNull
    private Cached doLoad(@NonNull String uri, long nowSec) throws Exception {
        String compact = StatusListFetcher.fetch(uri);
        StatusListToken token = StatusListToken.parse(compact);
        IssuerSignatureVerifier.verify(compact, config.apiGwUrl()).get();
        if (!token.sub.equals(uri)) {
            throw new IllegalStateException("status list sub mismatch");
        }
        if (token.exp != 0 && token.exp <= nowSec) {
            throw new IllegalStateException("status list token expired");
        }
        if (token.iat != 0 && token.iat > nowSec + IAT_SKEW_SEC) {
            throw new IllegalStateException("status list token not yet valid");
        }
        byte[] decoded = StatusListDecoder.decodeList(token.lst);
        return new Cached(decoded, token.bits);
    }

    private static VcStatus fallback(@Nullable Long expEpochSec, long nowSec) {
        if (expEpochSec != null && expEpochSec <= nowSec) return VcStatus.REVOKED;
        return VcStatus.ACTIVE;
    }

    private static final class Cached {
        final byte[] decodedList;
        final int bits;

        Cached(byte[] decodedList, int bits) {
            this.decodedList = decodedList;
            this.bits = bits;
        }
    }
}
