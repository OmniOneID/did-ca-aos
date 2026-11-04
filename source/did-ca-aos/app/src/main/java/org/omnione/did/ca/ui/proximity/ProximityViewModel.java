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

package org.omnione.did.ca.ui.proximity;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.repository.BleTransferModeRepository;
import org.omnione.did.ca.data.repository.ProximityPresentationRepository;
import org.omnione.did.ca.data.repository.ProximitySessionEndedException;
import org.omnione.did.ca.ui.Event;
import org.omnione.did.ca.util.ProximityErrors;
import org.omnione.did.proximity.sdk.core.ProximityException;
import org.omnione.did.proximity.sdk.core.engagement.BleMode;
import org.omnione.did.proximity.sdk.core.engagement.BleTransferMode;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ProximityViewModel extends ViewModel {

    private static final int QR_SIZE_PX = 720;

    private final ProximityPresentationRepository repo;
    private final BleTransferModeRepository bleTransferModeRepository;
    private final MutableLiveData<ProximityUiState> state = new MutableLiveData<>();
    private final MutableLiveData<Event<ProximityEvent>> events = new MutableLiveData<>();

    private final AtomicBoolean errorEmitted = new AtomicBoolean(false);
    private final AtomicBoolean requestReceived = new AtomicBoolean(false);
    @StringRes private volatile int errorRes = R.string.notice_proximity_failed_message;

    @Inject
    public ProximityViewModel(@NonNull ProximityPresentationRepository repo,
                              @NonNull BleTransferModeRepository bleTransferModeRepository) {
        this.repo = repo;
        this.bleTransferModeRepository = bleTransferModeRepository;
        state.setValue(new ProximityUiState(ProximityUiState.Phase.PERMISSION, null, null));
    }

    @NonNull
    private BleTransferMode resolveTransferMode() {
        org.omnione.did.ca.data.model.BleTransferMode selected = bleTransferModeRepository.get();
        switch (selected) {
            case CENTRAL:
                return BleTransferMode.CENTRAL_CLIENT;
            case PERIPHERAL:
                return BleTransferMode.PERIPHERAL_SERVER;
            case BOTH:
            default:
                return BleTransferMode.BOTH;
        }
    }

    public LiveData<ProximityUiState> getState() {
        return state;
    }

    public LiveData<Event<ProximityEvent>> getEvents() {
        return events;
    }

    public void startEngagement(@Nullable BleMode override, boolean lacksPeripheralTransport) {
        ProximityUiState current = state.getValue();
        if (current != null && (isSessionInFlight(current.phase)
                || current.phase == ProximityUiState.Phase.ERROR)) {
            return;
        }

        BleTransferMode mode = override != null ? BleTransferMode.of(override) : resolveTransferMode();
        if (lacksPeripheralTransport && mode == BleTransferMode.PERIPHERAL_SERVER) {
            failBeforeStart(R.string.notice_proximity_no_transport_message);
            return;
        }

        errorEmitted.set(false);
        requestReceived.set(false);
        state.setValue(new ProximityUiState(ProximityUiState.Phase.PERMISSION, null, null));

        repo.setStateListener(new ProximityPresentationRepository.StateListener() {
            @Override
            public void onReaderConnected() {
                state.postValue(new ProximityUiState(
                        ProximityUiState.Phase.CONNECTED, currentQr(), null));
            }

            @Override
            public void onTerminated() {
                if (requestReceived.get()) {
                    return;
                }
                emitError(new ProximitySessionEndedException("the verifier ended the session"));
            }

            @Override
            public void onError(Throwable t) {
                emitError(t);
            }
        });

        repo.startEngagement(mode)
                .whenComplete((qr, error) -> {
            if (error != null) {
                emitError(error);
                return;
            }
            Bitmap bitmap = generateQr(qr);
            if (bitmap == null) {
                emitError(new IllegalStateException("failed to render device engagement QR code"));
                return;
            }
            state.postValue(new ProximityUiState(
                    ProximityUiState.Phase.ADVERTISING, bitmap, null));
        });

        repo.awaitRequest().whenComplete((request, error) -> {
            if (error != null) {
                if (error instanceof ProximityException) {
                    emitError(error);
                }
                return;
            }
            requestReceived.set(true);
            state.postValue(new ProximityUiState(
                    ProximityUiState.Phase.REQUEST_RECEIVED, currentQr(), null));
            events.postValue(new Event<>(new ProximityEvent.LaunchConsent()));
        });
    }

    public boolean reshowError() {
        ProximityUiState current = state.getValue();
        if (current == null || current.phase != ProximityUiState.Phase.ERROR) {
            return false;
        }
        events.setValue(new Event<>(new ProximityEvent.ShowError(errorRes)));
        return true;
    }

    public void failBeforeStart(@StringRes int messageRes) {
        errorRes = messageRes;
        state.setValue(new ProximityUiState(ProximityUiState.Phase.ERROR, null, null));
        events.setValue(new Event<>(new ProximityEvent.ShowError(messageRes)));
    }

    public void cancel() {
        repo.setStateListener(null);
        repo.cancel();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repo.setStateListener(null);
        repo.cancel();
    }

    private void emitError(@NonNull Throwable t) {
        if (!errorEmitted.compareAndSet(false, true)) {
            return;
        }
        int messageRes = ProximityErrors.messageFor(t);
        if (messageRes == ProximityErrors.NOT_A_SESSION_ERROR) {
            messageRes = R.string.notice_proximity_failed_message;
        }
        errorRes = messageRes;
        String detail = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
        state.postValue(new ProximityUiState(ProximityUiState.Phase.ERROR, null, detail));
        events.postValue(new Event<>(new ProximityEvent.ShowError(messageRes)));
    }

    private static boolean isSessionInFlight(@NonNull ProximityUiState.Phase phase) {
        return phase == ProximityUiState.Phase.ADVERTISING
                || phase == ProximityUiState.Phase.CONNECTED
                || phase == ProximityUiState.Phase.REQUEST_RECEIVED;
    }

    @Nullable
    private Bitmap currentQr() {
        ProximityUiState s = state.getValue();
        return s != null ? s.qrBitmap : null;
    }

    private static Bitmap generateQr(@NonNull String payload) {
        try {
            return new BarcodeEncoder().encodeBitmap(
                    payload, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX);
        } catch (WriterException e) {
            return null;
        }
    }
}
