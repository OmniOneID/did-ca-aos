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

package org.omnione.did.ca.ui.referent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.ReferentOption;
import org.omnione.did.ca.databinding.ActivityReferentListBinding;
import org.omnione.did.ca.ui.common.BaseActivity;

import java.util.ArrayList;

public class ReferentListActivity extends BaseActivity {

    public static final String EXTRA_REFERENT_KEY = "extra_referent_key";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_OPTION_LABELS = "extra_option_labels";
    public static final String EXTRA_OPTION_VALUES = "extra_option_values";
    public static final String EXTRA_OPTION_CRED_IDS = "extra_option_cred_ids";
    public static final String EXTRA_INITIAL_VALUE = "extra_initial_value";
    public static final String EXTRA_SELECTED_VALUE = "extra_selected_value";
    public static final String EXTRA_SELECTED_CRED_ID = "extra_selected_cred_id";

    private static final int SCROLL_BASE_PADDING_BOTTOM_DP = 16;

    private ActivityReferentListBinding binding;
    private ReferentListViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReferentListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applySystemBarInsets();

        Intent intent = getIntent();
        String referentKey = intent.getStringExtra(EXTRA_REFERENT_KEY);
        String title = intent.getStringExtra(EXTRA_TITLE);
        ArrayList<String> labels = intent.getStringArrayListExtra(EXTRA_OPTION_LABELS);
        ArrayList<String> values = intent.getStringArrayListExtra(EXTRA_OPTION_VALUES);
        ArrayList<String> credIds = intent.getStringArrayListExtra(EXTRA_OPTION_CRED_IDS);
        String initialValue = intent.getStringExtra(EXTRA_INITIAL_VALUE);

        if (referentKey == null || title == null
                || labels == null || values == null || credIds == null
                || labels.size() != values.size()
                || labels.size() != credIds.size()) {
            finish();
            return;
        }

        ArrayList<ReferentOption> options = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            options.add(new ReferentOption(labels.get(i), values.get(i), credIds.get(i)));
        }

        viewModel = new ViewModelProvider(this,
                new ReferentListViewModel.Factory(referentKey, title, options, initialValue))
                .get(ReferentListViewModel.class);

        binding.optionsCard.setClipToOutline(true);

        binding.backButton.setOnClickListener(v -> finish());
        viewModel.getState().observe(this, this::render);
    }

    private void applySystemBarInsets() {
        applySystemBarInsets(binding.getRoot(), bars -> {
            applyTopInsetPadding(binding.appBar, bars.top);
            applyBottomInsetPadding(binding.contentScroll,
                    dp(SCROLL_BASE_PADDING_BOTTOM_DP), bars.bottom);
        });
    }

    private void render(ReferentListUiState state) {
        if (state == null) return;
        binding.titleText.setText(state.title);

        LinearLayout card = binding.optionsCard;
        card.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < state.options.size(); i++) {
            ReferentOption opt = state.options.get(i);
            boolean isLast = i == state.options.size() - 1;
            boolean selected = opt.value.equals(state.selectedValue);

            View row = buildRow(inflater, card, opt, selected);
            card.addView(row);

            if (!isLast) {
                card.addView(buildDivider(card));
            }
        }
    }

    @NonNull
    private View buildRow(@NonNull LayoutInflater inflater,
                          @NonNull LinearLayout parent,
                          @NonNull ReferentOption opt,
                          boolean selected) {
        View row = inflater.inflate(R.layout.item_referent_option, parent, false);
        ((TextView) row.findViewById(R.id.optionLabel)).setText(opt.label);
        ((TextView) row.findViewById(R.id.optionValue)).setText(opt.value);

        ImageView check = row.findViewById(R.id.checkIcon);
        check.setVisibility(selected ? View.VISIBLE : View.GONE);

        row.setBackgroundColor(selected
                ? getResources().getColor(R.color.brand_primary_pale, null)
                : getResources().getColor(R.color.white, null));

        row.setOnClickListener(v -> onOptionSelected(opt));
        return row;
    }

    @NonNull
    private View buildDivider(@NonNull LinearLayout parent) {
        View divider = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divider.setLayoutParams(lp);
        divider.setBackgroundResource(R.color.brand_divider);
        return divider;
    }

    private void onOptionSelected(@NonNull ReferentOption opt) {
        ReferentListUiState state = viewModel.getState().getValue();
        if (state == null) return;

        viewModel.select(opt.value);

        Intent result = new Intent();
        result.putExtra(EXTRA_REFERENT_KEY, state.referentKey);
        result.putExtra(EXTRA_SELECTED_VALUE, opt.value);
        result.putExtra(EXTRA_SELECTED_CRED_ID, opt.credentialId);
        setResult(Activity.RESULT_OK, result);
        finish();
    }
}
