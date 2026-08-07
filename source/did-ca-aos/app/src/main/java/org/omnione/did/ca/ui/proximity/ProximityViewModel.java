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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.UUID;

public class ProximityViewModel extends ViewModel {

    private static final int QR_SIZE_PX = 720;

    private static final String PAYLOAD_PREFIX = "omn://present/proximity/";

    private final MutableLiveData<ProximityUiState> state = new MutableLiveData<>();

    public ProximityViewModel() {
        Bitmap bitmap = generateQr(PAYLOAD_PREFIX + UUID.randomUUID());
        if (bitmap != null) {
            state.setValue(new ProximityUiState(bitmap));
        }
    }

    public LiveData<ProximityUiState> getState() {
        return state;
    }

    private static Bitmap generateQr(@NonNull String payload) {
        try {
            return new BarcodeEncoder().encodeBitmap(
                    payload, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX);
        } catch (WriterException e) {
            return null;
        }
    }

    public static final class Factory implements ViewModelProvider.Factory {

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(ProximityViewModel.class)) {
                return (T) new ProximityViewModel();
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass);
        }
    }
}
