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
package org.omnione.did.ca.ui.settings;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.repository.ServerUrlRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ServerUrlViewModel extends ViewModel {

    private static final Pattern URL_PATTERN =
            Pattern.compile("^(https?)://([^\\s/:]+)(?::(\\d+))?(/\\S*)?$");

    private final ServerUrlRepository repository;

    private final Map<String, String> saved = new LinkedHashMap<>();
    private final Map<String, String> custom = new LinkedHashMap<>();
    private final Map<String, Integer> errors = new HashMap<>();

    private final MutableLiveData<List<String>> customKeys = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saveEnabled = new MutableLiveData<>(Boolean.FALSE);

    @Inject
    public ServerUrlViewModel(@NonNull ServerUrlRepository repository) {
        this.repository = repository;
    }

    public void init() {
        if (customKeys.getValue() != null) return;
        saved.putAll(repository.custom());
        custom.putAll(saved);
        publish();
    }

    @NonNull
    public LiveData<List<String>> getCustomKeys() {
        return customKeys;
    }

    @NonNull
    public LiveData<Boolean> getSaveEnabled() {
        return saveEnabled;
    }

    @NonNull
    public List<String> defaultKeys() {
        List<String> keys = new ArrayList<>();
        for (String key : ServerUrlRepository.KEYS) {
            if (!custom.containsKey(key)) keys.add(key);
        }
        return keys;
    }

    @NonNull
    public String defaultUrl(@NonNull String key) {
        return repository.defaultUrl(key);
    }

    @NonNull
    public String customUrl(@NonNull String key) {
        String value = custom.get(key);
        return value == null ? "" : value;
    }

    @StringRes
    public int errorOf(@NonNull String key) {
        Integer error = errors.get(key);
        return error == null ? 0 : error;
    }

    public void toCustom(@NonNull String key) {
        custom.put(key, repository.defaultUrl(key));
        publish();
    }

    public void toDefault(@NonNull String key) {
        custom.remove(key);
        publish();
    }

    public void allToCustom() {
        for (String key : ServerUrlRepository.KEYS) {
            if (!custom.containsKey(key)) custom.put(key, repository.defaultUrl(key));
        }
        publish();
    }

    public void allToDefault() {
        custom.clear();
        publish();
    }

    @StringRes
    public int onUrlChanged(@NonNull String key, @NonNull String value) {
        if (!custom.containsKey(key)) return 0;
        custom.put(key, value);
        int error = validate(value);
        if (error == 0) errors.remove(key);
        else errors.put(key, error);
        updateSaveEnabled();
        return error;
    }

    public boolean save() {
        Map<String, String> trimmed = new LinkedHashMap<>();
        for (String key : ServerUrlRepository.KEYS) {
            String value = custom.get(key);
            if (value != null) trimmed.put(key, normalize(value));
        }
        return repository.save(trimmed);
    }

    @NonNull
    static String normalize(@NonNull String value) {
        String text = value.trim();
        while (text.length() > 1 && text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    @StringRes
    static int validate(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return R.string.server_url_error_empty;
        Matcher matcher = URL_PATTERN.matcher(text);
        if (!matcher.matches()) return R.string.server_url_error_format;
        String port = matcher.group(3);
        if (port != null) {
            int number;
            try {
                number = Integer.parseInt(port);
            } catch (NumberFormatException e) {
                return R.string.server_url_error_port;
            }
            if (number < 1 || number > 65535) return R.string.server_url_error_port;
        }
        return 0;
    }

    private void publish() {
        errors.clear();
        for (Map.Entry<String, String> entry : custom.entrySet()) {
            int error = validate(entry.getValue());
            if (error != 0) errors.put(entry.getKey(), error);
        }
        List<String> keys = new ArrayList<>();
        for (String key : ServerUrlRepository.KEYS) {
            if (custom.containsKey(key)) keys.add(key);
        }
        customKeys.setValue(keys);
        updateSaveEnabled();
    }

    private void updateSaveEnabled() {
        saveEnabled.setValue(!custom.equals(saved) && errors.isEmpty());
    }
}
