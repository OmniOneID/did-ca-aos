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
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import org.omnione.did.ca.R;

import java.util.ArrayList;

public class AttrRefAdapter extends BaseAdapter {

    private ArrayList<ReferentItem> referentItems = new ArrayList<ReferentItem>() ;
    // ListViewAdapter의 생성자
    public AttrRefAdapter() {

    }

    // Adapter에 사용되는 데이터의 개수를 리턴. : 필수 구현
    @Override
    public int getCount() {
        return referentItems.size() ;
    }

    // position에 위치한 데이터를 화면에 출력하는데 사용될 View를 리턴. : 필수 구현
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final int pos = position;
        final Context context = parent.getContext();

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.item_attr_ref, parent, false);
        }

        // 화면에 표시될 View(Layout이 inflate된)으로부터 위젯에 대한 참조 획득
        TextView textViewTitle = (TextView) convertView.findViewById(R.id.textView_attr_ref_item_title) ;

        TextView textViewSubTitle = (TextView) convertView.findViewById(R.id.textView_attr_ref_item_subtitle) ;
        CheckBox checkBox = (CheckBox) convertView.findViewById(R.id.checkBox_attr_ref);

        // Data Set(listViewItemList)에서 position에 위치한 데이터 참조 획득
        ReferentItem listViewItem = referentItems.get(position);

        // 아이템 내 각 위젯에 데이터 반영
        textViewTitle.setText(listViewItem.getText());

        if (listViewItem.getTextValue() == null) {
            textViewSubTitle.setText("* Tap to select");
            textViewSubTitle.setTextColor(Color.parseColor("#FF0000"));
        } else {
            textViewSubTitle.setText(listViewItem.getTextValue());
        }

        if(listViewItem.getCheckBoxAvailabe() == false){
            checkBox.setEnabled(false);
        }


        return convertView;
    }

    // 지정한 위치(position)에 있는 데이터와 관계된 아이템(row)의 ID를 리턴. : 필수 구현
    @Override
    public long getItemId(int position) {
        return position ;
    }

    // 지정한 위치(position)에 있는 데이터 리턴 : 필수 구현
    @Override
    public Object getItem(int position) {
        return referentItems.get(position) ;
    }

    // 아이템 데이터 추가를 위한 함수. 개발자가 원하는대로 작성 가능.
    public void addItem(String data, boolean isCheckAvailable) {
        ReferentItem item = new ReferentItem();
        item.setText(data);
        item.setCheckBoxAvailable(isCheckAvailable);
        referentItems.add(item);
    }


}
