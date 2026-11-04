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

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import org.omnione.did.ca.R;
import org.omnione.did.ca.databinding.ActivityProximityBinding;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.ui.common.NoticeDialogFragment;
import org.omnione.did.ca.util.AppLog;
import org.omnione.did.ca.util.IntentRouter;
import org.omnione.did.ca.util.WifiAwareReadiness;
import org.omnione.did.proximity.sdk.core.engagement.BleMode;
import org.omnione.did.proximity.sdk.holder.HolderSession;
import org.omnione.did.proximity.sdk.holder.nfc.NfcEngagementPreference;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ProximityActivity extends BaseActivity {

    public static final String EXTRA_MODE = "proximity_mode";

    private static final String TAG = "ProximityActivity";

    private static final int FOOTER_BASE_PADDING_BOTTOM_DP = 20;

    private static final String[] BLE_PERMISSIONS_S_PLUS = {
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
    };

    private static final String[] BLE_PERMISSIONS_LEGACY = {
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private ActivityProximityBinding binding;
    private ProximityViewModel viewModel;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<Intent> vpRequestLauncher;
    private boolean engagementStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProximityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applySystemBarInsets();

        NoticeDialogFragment.dismissRestored(getSupportFragmentManager());
        viewModel = new ViewModelProvider(this).get(ProximityViewModel.class);

        binding.backButton.setOnClickListener(v -> {
            viewModel.cancel();
            finish();
        });

        vpRequestLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> finish());

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    if (!hasBlePermissions()) {
                        showPermissionDeniedDialog();
                        return;
                    }
                    if (!hasOptionalWifiAwarePermissions()) {
                        AppLog.d(TAG, "Wi-Fi Aware permission denied, presenting over BLE only");
                    }
                    startEngagementOnce();
                });

        viewModel.getState().observe(this, state -> {
            if (state == null) return;
            if (state.qrBitmap != null) {
                binding.qrImage.setImageBitmap(state.qrBitmap);
            } else {
                binding.qrImage.setImageDrawable(null);
            }
        });

        viewModel.getEvents().observe(this, evt -> {
            ProximityEvent value = evt.getIfNotHandled();
            if (value == null) return;
            if (value instanceof ProximityEvent.LaunchConsent) {
                vpRequestLauncher.launch(IntentRouter.vpRequestProximityIntent(this));
            } else if (value instanceof ProximityEvent.ShowError) {
                showError(((ProximityEvent.ShowError) value).messageRes);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (engagementStarted) return;
        if (viewModel.reshowError()) {
            engagementStarted = true;
            return;
        }
        if (bluetoothAdapter() == null
                || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            engagementStarted = true;
            viewModel.failBeforeStart(R.string.notice_proximity_ble_unavailable_message);
            return;
        }
        if (hasBlePermissions() && hasOptionalWifiAwarePermissions()) {
            startEngagementOnce();
        } else {
            permissionLauncher.launch(requiredPermissions());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        NfcEngagementPreference.prefer(this);
    }

    @Override
    protected void onPause() {
        NfcEngagementPreference.release(this);
        super.onPause();
    }

    private void startEngagementOnce() {
        if (engagementStarted) return;
        engagementStarted = true;
        viewModel.startEngagement(modeOverride(), lacksPeripheralTransport());
    }

    @Nullable
    private BluetoothAdapter bluetoothAdapter() {
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        return manager != null ? manager.getAdapter() : null;
    }

    private boolean lacksPeripheralTransport() {
        BluetoothAdapter adapter = bluetoothAdapter();
        return adapter != null && adapter.isEnabled()
                && !adapter.isMultipleAdvertisementSupported()
                && !WifiAwareReadiness.isReady(this)
                && !HolderSession.isNfcEngagementAvailable(this);
    }

    @Nullable
    private BleMode modeOverride() {
        String raw = getIntent().getStringExtra(EXTRA_MODE);
        if (raw == null) {
            return null;
        }
        try {
            return BleMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String[] blePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return BLE_PERMISSIONS_S_PLUS;
        }
        return BLE_PERMISSIONS_LEGACY;
    }

    private String[] requiredPermissions() {
        if (!WifiAwareReadiness.isSupported(this)) {
            return blePermissions();
        }
        LinkedHashSet<String> all = new LinkedHashSet<>(Arrays.asList(blePermissions()));
        all.addAll(Arrays.asList(WifiAwareReadiness.permissions()));
        return all.toArray(new String[0]);
    }

    private boolean hasBlePermissions() {
        for (String permission : blePermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private boolean hasOptionalWifiAwarePermissions() {
        return !WifiAwareReadiness.isSupported(this) || WifiAwareReadiness.hasPermissions(this);
    }

    private void showPermissionDeniedDialog() {
        NoticeDialogFragment.newInstance(
                        this,
                        R.string.common_error,
                        R.string.proximity_permission_dialog_message,
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

    private void showError(@StringRes int messageRes) {
        showErrorNotice(R.string.notice_vp_failed_title, messageRes, null, this::finish, false);
    }

    private void applySystemBarInsets() {
        applySystemBarInsets(binding.getRoot(), bars -> {
            applyTopInsetPadding(binding.appBar, bars.top);
            applyBottomInsetPadding(binding.footer,
                    dp(FOOTER_BASE_PADDING_BOTTOM_DP), bars.bottom);
        });
    }
}
