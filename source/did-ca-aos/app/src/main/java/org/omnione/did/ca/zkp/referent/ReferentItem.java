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

class ReferentItem {
    private String text ;
    private String textValue ;
    private boolean isCheckBoxAvailable;

    public void setText(String text) {
        this.text = text ;
    }

    public String getText() {
        return this.text ;
    }
    public String getTextValue() {
        return textValue;
    }

    public void setTextValue(String textValue) {
        this.textValue = textValue;
    }
    public void setCheckBoxAvailable(boolean isCheckBoxAvailable) {
        this.isCheckBoxAvailable = isCheckBoxAvailable ;
    }

    public boolean getCheckBoxAvailabe() {
        return this.isCheckBoxAvailable ;
    }
}
