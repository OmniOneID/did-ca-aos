package org.omnione.did.ca.ui.viewmodel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.omnione.did.sdk.datamodel.util.IntEnumAdapterFactory;
import org.omnione.did.sdk.datamodel.util.JsonSortUtil;
import org.omnione.did.sdk.datamodel.util.StringEnumAdapterFactory;

abstract public class BaseModel {
    public String toJson() {
        Gson gson = (new GsonBuilder()).registerTypeAdapterFactory(new IntEnumAdapterFactory()).registerTypeAdapterFactory(new StringEnumAdapterFactory()).create();
        String json = gson.toJson(this);
        return JsonSortUtil.sortJsonString(gson, json);
    }

    public abstract void fromJson(String value);
}
