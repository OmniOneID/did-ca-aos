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

package org.omnione.did.ca.ui.qr;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import org.omnione.did.ca.R;
import org.omnione.did.ca.databinding.ActivityQrScanBinding;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.ui.common.NoticeDialogFragment;
import org.omnione.did.ca.util.IntentRouter;

import java.util.Collections;
import java.util.List;

public class QrScanActivity extends BaseActivity {

    public static final String EXTRA_MODE = "mode";

    public static final String EXTRA_SCAN_PAYLOAD = "payload";

    public static final String EXTRA_ERROR_MESSAGE = "errorMessage";

    public static final int RESULT_ERROR = Activity.RESULT_FIRST_USER;

    private ActivityQrScanBinding binding;
    private ActivityResultLauncher<String> permissionLauncher;

    private boolean cameraStarted = false;

    private boolean processed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQrScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applySystemBarInsets();

        binding.closeButton.setOnClickListener(v -> finish());

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startScanner();
                    } else {
                        showPermissionDeniedDialog();
                    }
                });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startScanner();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startScanner() {
        binding.cameraView.setDecoderFactory(
                new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE)));
        binding.cameraView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (processed) return;
                processed = true;
                binding.cameraView.pause();
                onScanned(result.getText());
            }

            @Override
            public void possibleResultPoints(List<ResultPoint> resultPoints) {

            }
        });
        binding.cameraView.resume();
        cameraStarted = true;
    }

    private void onScanned(String raw) {
        QrScanMode mode = QrScanMode.from(getIntent().getStringExtra(EXTRA_MODE));
        switch (mode) {
            case ISSUE:
                Intent data = new Intent();
                data.putExtra(EXTRA_SCAN_PAYLOAD, raw);
                setResult(RESULT_OK, data);
                finish();
                break;
            case PRESENT:
            default:

                IntentRouter.startVpRequest(this, raw);
                finish();
                break;
        }
    }

    private void showPermissionDeniedDialog() {
        NoticeDialogFragment.newInstance(
                        this,
                        R.string.common_error,
                        R.string.qr_permission_dialog_message,
                        R.string.common_ok)
                .setOnConfirmed(this::openAppSettings)
                .show(getSupportFragmentManager());
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraStarted && !processed) {
            binding.cameraView.resume();
        }
    }

    @Override
    protected void onPause() {
        if (cameraStarted) {
            binding.cameraView.pause();
        }
        super.onPause();
    }

    private void applySystemBarInsets() {
        applySystemBarInsets(binding.getRoot(), bars ->
                applyTopInsetPadding(binding.topContainer, bars.top));
    }
}
