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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.Credential;
import org.omnione.did.ca.data.model.CredentialBadge;
import org.omnione.did.ca.ui.common.CredentialBadgeBinder;
import org.omnione.did.ca.ui.common.CredentialStatusBinder;

import java.util.ArrayList;
import java.util.List;

public class CredentialAdapter extends RecyclerView.Adapter<CredentialAdapter.VH> {

    public interface OnCardClick {
        void onClick(@NonNull Credential credential);
    }

    private final List<Credential> items = new ArrayList<>();
    private final OnCardClick onClick;

    public CredentialAdapter(@NonNull OnCardClick onClick) {
        this.onClick = onClick;
    }

    public void submit(@NonNull List<Credential> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_credential, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position), onClick);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final View card;
        final TextView issuer;
        final TextView name;
        final LinearLayout badgeRow;
        final LinearLayout statusBadge;
        final View statusDot;
        final TextView statusText;
        final TextView formatBadge;
        final TextView zkpBadge;
        final TextView issued;
        final TextView valid;

        VH(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.credentialCard);
            issuer = itemView.findViewById(R.id.credentialIssuer);
            name = itemView.findViewById(R.id.credentialName);
            badgeRow = itemView.findViewById(R.id.badgeRow);
            statusBadge = itemView.findViewById(R.id.statusBadge);
            statusDot = itemView.findViewById(R.id.statusDot);
            statusText = itemView.findViewById(R.id.statusText);
            formatBadge = itemView.findViewById(R.id.formatBadge);
            zkpBadge = itemView.findViewById(R.id.zkpBadge);
            issued = itemView.findViewById(R.id.credentialIssued);
            valid = itemView.findViewById(R.id.credentialValid);
        }

        void bind(@NonNull Credential c, @NonNull OnCardClick onClick) {
            issuer.setText(c.issuer);
            name.setText(c.name);

            CredentialStatusBinder.bind(statusBadge, statusDot, statusText, c.status);
            bindFormat(c.badge);
            zkpBadge.setVisibility(c.zkp ? View.VISIBLE : View.GONE);

            String placeholder = card.getResources().getString(R.string.docs_placeholder);
            issued.setText(c.issued != null ? c.issued : placeholder);
            valid.setText(c.valid != null ? c.valid : placeholder);

            card.setOnClickListener(v -> onClick.onClick(c));

            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            int gapPx = (int) (12 * itemView.getResources().getDisplayMetrics().density);
            int last = getBindingAdapter() == null ? 0 : getBindingAdapter().getItemCount() - 1;
            lp.bottomMargin = (getBindingAdapterPosition() == last) ? 0 : gapPx;
            itemView.setLayoutParams(lp);
        }

        private void bindFormat(@NonNull CredentialBadge badge) {
            CredentialBadgeBinder.bind(formatBadge, badge);
        }
    }
}
