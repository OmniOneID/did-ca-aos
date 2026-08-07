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

package org.omnione.did.ca.ui.issue;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.omnione.did.ca.data.model.IssuableCredential;
import org.omnione.did.ca.databinding.ItemIssuerBinding;
import org.omnione.did.ca.ui.common.CredentialBadgeBinder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class IssuerListAdapter extends RecyclerView.Adapter<IssuerListAdapter.VH> {

    private final List<IssuableCredential> items = new ArrayList<>();
    private final Consumer<IssuableCredential> onClick;

    public IssuerListAdapter(@NonNull Consumer<IssuableCredential> onClick) {
        this.onClick = onClick;
    }

    public void submit(@NonNull List<IssuableCredential> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemIssuerBinding binding = ItemIssuerBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        IssuableCredential item = items.get(position);

        holder.binding.issuerName.setText(item.displayName);
        CredentialBadgeBinder.bind(holder.binding.formatBadge, item.badge);

        ViewGroup.MarginLayoutParams lp =
                (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
        int gapPx = (int) (10 * holder.itemView.getResources().getDisplayMetrics().density);
        lp.bottomMargin = (position == items.size() - 1) ? 0 : gapPx;
        holder.itemView.setLayoutParams(lp);

        holder.itemView.setOnClickListener(v -> onClick.accept(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final ItemIssuerBinding binding;

        VH(@NonNull ItemIssuerBinding b) {
            super(b.getRoot());
            this.binding = b;
        }
    }
}
