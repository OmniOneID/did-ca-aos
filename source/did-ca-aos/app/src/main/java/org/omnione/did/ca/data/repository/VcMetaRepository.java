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
import org.omnione.did.ca.data.network.NetworkManager;
import org.omnione.did.ca.data.network.protocol.RequestVcMetaOp;
import org.omnione.did.ca.data.statuslist.StatusListResult;
import org.omnione.did.ca.util.AppLog;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.datamodel.vc.issue.VcMeta;
import org.omnione.did.sdk.datamodel.vc.issue.VcStatus;
import org.omnione.did.sdk.datamodel.vc.issue.VcStatusVo;
import org.omnione.did.sdk.utility.MultibaseUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class VcMetaRepository {

    private static final String TAG = "CaNet";

    private final NetworkManager net;
    private final AppConfig config;
    private final Executor executor = Executors.newCachedThreadPool();

    @Inject
    public VcMetaRepository(NetworkManager net, AppConfig config) {
        this.net = net;
        this.config = config;
    }

    @NonNull
    public CompletableFuture<StatusListResult> check(@NonNull String vcId,
                                                     @Nullable Long expEpochSec) {
        return CompletableFuture.supplyAsync(() -> checkBlocking(vcId, expEpochSec), executor);
    }

    private StatusListResult checkBlocking(@NonNull String vcId, @Nullable Long expEpochSec) {
        long nowSec = System.currentTimeMillis() / 1000L;

        VcStatus serverStatus;
        try {
            String body = RequestVcMetaOp.call(net, config.apiGwUrl(), vcId);
            VcStatusVo vo = MessageUtil.deserialize(body, VcStatusVo.class);
            if (vo == null || vo.getVcMeta() == null) {
                throw new IllegalStateException("empty vc-meta response");
            }
            String decoded = new String(MultibaseUtils.decode(vo.getVcMeta()));
            VcMeta meta = MessageUtil.deserialize(decoded, VcMeta.class);
            VcStatus parsed = meta != null ? parseStatus(meta.getStatus()) : null;
            if (parsed == null) {
                throw new IllegalStateException(
                        "unknown vc status: " + (meta != null ? meta.getStatus() : null));
            }
            serverStatus = parsed;
        } catch (CommunicationException e) {
            AppLog.e(TAG, "vc-meta fetch failed for " + vcId, e);
            return new StatusListResult(fallback(expEpochSec, nowSec), true,
                    R.string.status_check_fetch_failed);
        } catch (Exception e) {
            AppLog.e(TAG, "vc-meta parse failed for " + vcId, e);
            return new StatusListResult(fallback(expEpochSec, nowSec), true,
                    R.string.status_check_verify_failed);
        }

        if (expEpochSec != null && expEpochSec <= nowSec) {
            return new StatusListResult(VcStatus.REVOKED, false, null);
        }
        return new StatusListResult(serverStatus, false, null);
    }

    @Nullable
    private static VcStatus parseStatus(@Nullable String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        for (VcStatus s : VcStatus.values()) {
            if (s.name().equalsIgnoreCase(t)) return s;
            String v = s.getValue();
            if (v != null && v.equalsIgnoreCase(t)) return s;
        }
        return null;
    }

    private static VcStatus fallback(@Nullable Long expEpochSec, long nowSec) {
        if (expEpochSec != null && expEpochSec <= nowSec) return VcStatus.REVOKED;
        return VcStatus.ACTIVE;
    }
}
