/*
 * Copyright 2025 OmniOne.
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

package org.omnione.did.ca.zkp.referent;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.omnione.did.ca.R;

import java.util.ArrayList;

public class CredentialIdAdapter extends BaseAdapter {

    private ArrayList<ReferentItem> referentItems = new ArrayList<ReferentItem>() ;

    private int selectedPosition = -1;
    public CredentialIdAdapter() {

    }

    @Override
    public int getCount() {
        return referentItems.size() ;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final int pos = position;
        final Context context = parent.getContext();

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.item_credential_id, parent, false);
        }

        TextView textTextView = (TextView) convertView.findViewById(R.id.textView_value) ;

        ReferentItem listViewItem = referentItems.get(position);

        textTextView.setText(listViewItem.getText());

        if (position == selectedPosition) {
            convertView.setBackgroundResource(R.drawable.item_selected);
        } else {
            convertView.setBackgroundResource(R.drawable.item_default);
        }


        return convertView;
    }

    @Override
    public long getItemId(int position) {
        return position ;
    }

    @Override
    public Object getItem(int position) {
        return referentItems.get(position) ;
    }

    public void addItem(String data) {
        ReferentItem item = new ReferentItem();
        item.setText(data);
        referentItems.add(item);
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged(); // UI 갱신
    }
}
