/*
 * Copyright 2024-2025 OmniOne.
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

package org.omnione.did.ca.ui.vc;

import android.text.BoringLayout;

public class VcListItem {
    String title;
    String validUntil;
    String issuanceDate;

    boolean isZkp;
    String img;

    public VcListItem(String title, String validUntil, String issuanceDate, boolean isZkp, String img) {
        this.title = title;
        this.validUntil = validUntil;
        this.issuanceDate = issuanceDate;
        this.isZkp = isZkp;
        this.img = img;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isZkp() {
        return isZkp;
    }

    public void setIsZkp(boolean isZkp) {
        this.isZkp = isZkp;
    }

    public String getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(String validUntil) {
        this.validUntil = validUntil;
    }

    public String getImg() {
        return img;
    }

    public void setImg(String img) {
        this.img = img;
    }

    public String getIssuanceDate() {
        return issuanceDate;
    }

    public void setIssuanceDate(String issuanceDate) {
        this.issuanceDate = issuanceDate;
    }

    @Override
    public String toString() {
        return "SingerItem{" +
                "title='" + title + '\'' +
                ", validUntil='" + validUntil + '\'' +
                ", isZkp='" + isZkp + '\'' +
                '}';
    }
}

