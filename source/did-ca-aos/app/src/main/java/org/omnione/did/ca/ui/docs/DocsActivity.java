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

package org.omnione.did.ca.ui.docs;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.Credential;
import org.omnione.did.ca.databinding.ActivityDocsBinding;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.ui.common.NoticeDialogFragment;
import org.omnione.did.ca.ui.dialog.AddBottomSheetDialogFragment;
import org.omnione.did.ca.ui.dialog.PresentBottomSheetDialogFragment;
import org.omnione.did.ca.ui.qr.QrScanActivity;
import org.omnione.did.ca.util.IntentRouter;

@dagger.hilt.android.AndroidEntryPoint
public class DocsActivity extends BaseActivity {

    private ActivityDocsBinding binding;
    private DocsViewModel viewModel;
    private CredentialAdapter adapter;

    private ActivityResultLauncher<Intent> qrIssueLauncher;

    private ActivityResultLauncher<Intent> vcDetailLauncher;

    private int statusBarInsetPx = 0;
    private int navigationBarInsetPx = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDocsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        qrIssueLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onQrIssueResult);

        vcDetailLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {

                        viewModel.reloadAfterChange();
                    }
                });

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applySystemBarInsets();

        viewModel = new ViewModelProvider(this).get(DocsViewModel.class);

        binding.emptyHint.setText(buildEmptyHint());

        adapter = new CredentialAdapter(this::openCredential);
        binding.credentialRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.credentialRecycler.setAdapter(adapter);

        binding.menuButton.setOnClickListener(v -> IntentRouter.startSettings(this));

        binding.addButton.setOnClickListener(v -> showAddSheet());

        binding.presentButton.setOnClickListener(v -> showPresentSheet());

        viewModel.getState().observe(this, this::render);

        if (savedInstanceState == null) {
            viewModel.loadWallet();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.applyPendingStatusUpdates();
    }

    private void applySystemBarInsets() {
        applySystemBarInsets(binding.getRoot(), bars -> {
            statusBarInsetPx = bars.top;
            navigationBarInsetPx = bars.bottom;
            applyTopInsetPadding(binding.appBar, statusBarInsetPx);
            applyBottomInset();
        });
    }

    private void applyBottomInset() {
        boolean walletVisible = binding.bottomBar.getVisibility() == View.VISIBLE;
        int basePaddingBottomPx = dp(16);

        binding.bottomBar.setPadding(
                binding.bottomBar.getPaddingLeft(),
                binding.bottomBar.getPaddingTop(),
                binding.bottomBar.getPaddingRight(),
                basePaddingBottomPx + (walletVisible ? navigationBarInsetPx : 0));

        binding.contentContainer.setPadding(
                0, 0, 0, walletVisible ? 0 : navigationBarInsetPx);
    }

    private void render(DocsUiState state) {
        if (state == null) return;
        if (state.loading) {
            showProgress();
        } else {
            dismissProgress();
        }
        boolean empty = state.isEmpty();
        binding.emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.credentialRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.bottomBar.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (!empty) {
            adapter.submit(state.credentials);
        }
        applyBottomInset();

        if (state.errorMessageRes != null) {
            NoticeDialogFragment.newInstance(
                            getText(R.string.notice_vc_load_failed_title),
                            getText(state.errorMessageRes),
                            getText(R.string.common_ok))
                    .show(getSupportFragmentManager());
        }
    }

    private void showAddSheet() {
        new AddBottomSheetDialogFragment().show(
                getSupportFragmentManager(),
                AddBottomSheetDialogFragment.TAG);
    }

    private void showPresentSheet() {
        new PresentBottomSheetDialogFragment().show(
                getSupportFragmentManager(),
                PresentBottomSheetDialogFragment.TAG);
    }

    private void openCredential(Credential c) {
        vcDetailLauncher.launch(IntentRouter.vcDetailIntent(this, c.id));
    }

    public void onAddFromList() {
        startActivity(IntentRouter.protocolSelectIntent(this));
    }

    public void onAddScanQr() {
        qrIssueLauncher.launch(IntentRouter.qrScanIssueIntent(this));
    }

    private void onQrIssueResult(androidx.activity.result.ActivityResult result) {
        int code = result.getResultCode();
        if (code == Activity.RESULT_OK) {
            Intent data = result.getData();
            String payload = data == null
                    ? null
                    : data.getStringExtra(QrScanActivity.EXTRA_SCAN_PAYLOAD);
            if (payload != null) {
                IntentRouter.startIssueFromQrPayload(this, payload);
            }
        } else if (code == QrScanActivity.RESULT_ERROR) {
            Intent data = result.getData();
            CharSequence message = data == null
                    ? null
                    : data.getCharSequenceExtra(QrScanActivity.EXTRA_ERROR_MESSAGE);
            NoticeDialogFragment.newInstance(
                            getText(R.string.common_error),
                            message != null ? message : "",
                            getText(R.string.common_ok))
                    .show(getSupportFragmentManager());
        }
    }

    public void onPresentProximity() {
        Toast.makeText(this, R.string.present_proximity_unavailable, Toast.LENGTH_SHORT).show();
    }

    public void onPresentScanQr() {
        IntentRouter.startQrScan(this);
    }

    private CharSequence buildEmptyHint() {

        String prefix = getString(R.string.docs_empty_hint_prefix);
        String plus = getString(R.string.docs_empty_hint_plus);
        String suffix = getString(R.string.docs_empty_hint_suffix);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        sb.append(prefix);
        int start = sb.length();
        sb.append(plus);
        int end = sb.length();
        sb.setSpan(new android.text.style.ForegroundColorSpan(
                        getResources().getColor(R.color.brand_primary, null)),
                start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        Typeface bold = ResourcesCompat.getFont(this, R.font.pretendard_bold);
        if (bold != null) {
            sb.setSpan(new PretendardTypefaceSpan(bold),
                    start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        sb.append(suffix);
        return sb;
    }

    private static final class PretendardTypefaceSpan extends MetricAffectingSpan {
        private final Typeface typeface;

        PretendardTypefaceSpan(Typeface typeface) {
            this.typeface = typeface;
        }

        @Override
        public void updateDrawState(@NonNull TextPaint paint) {
            paint.setTypeface(typeface);
        }

        @Override
        public void updateMeasureState(@NonNull TextPaint paint) {
            paint.setTypeface(typeface);
        }
    }
}
