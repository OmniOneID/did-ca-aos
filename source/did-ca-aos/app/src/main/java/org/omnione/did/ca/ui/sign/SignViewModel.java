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

package org.omnione.did.ca.ui.sign;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.config.Preference;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.TokenAwareHttpClient;
import org.omnione.did.ca.network.vo.CasTokenResVO;
import org.omnione.did.ca.network.vo.CheckUserRegStatusReqVO;
import org.omnione.did.ca.network.vo.CheckUserRegStatusResVO;
import org.omnione.did.ca.network.vo.SigninReqVO;
import org.omnione.did.ca.network.vo.SignupReqVO;
import org.omnione.did.ca.util.AuthTokenHelper;
import com.google.gson.Gson;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.datamodel.util.MessageUtil;

public class SignViewModel extends ViewModel {

    public enum SignStatus { LOADING, NEW, CURRENT, OTHER_DEVICE, USED_BY_OTHER, SIGNUP_SUCCESS, SIGNIN_SUCCESS, ERROR }

    public static class SignResult {
        public final SignStatus status;
        public final String errorMessage;

        private SignResult(SignStatus status, String errorMessage) {
            this.status = status;
            this.errorMessage = errorMessage;
        }

        public static SignResult of(SignStatus status)             { return new SignResult(status, null); }
        public static SignResult error(String msg)                 { return new SignResult(SignStatus.ERROR, msg); }
    }

    private final MutableLiveData<SignResult> _result = new MutableLiveData<>();
    public LiveData<SignResult> getResult() { return _result; }

    // ── public entry points ───────────────────────────────────────────────────

    /** Step 1: check registration status with the CA server. */
    public void checkRegistrationStatus(Context context, String loginId) {
        _result.postValue(SignResult.of(SignStatus.LOADING));
        new Thread(() -> {
            try {
                String walletId = Preference.getCaAppId(context);
                CheckUserRegStatusReqVO req = new CheckUserRegStatusReqVO(loginId, walletId);
                String body = new Gson().toJson(req);

                String response = TokenAwareHttpClient.send(
                        context, Config.CAS.CHECK_REGISTRATION_STATUS, "POST", body);

                CheckUserRegStatusResVO res = MessageUtil.deserialize(response, CheckUserRegStatusResVO.class);
                CaLog.d("checkRegistrationStatus: " + res.getStatus());

                switch (res.getStatus()) {
                    case "NEW"           -> _result.postValue(SignResult.of(SignStatus.NEW));
                    case "CURRENT"       -> _result.postValue(SignResult.of(SignStatus.CURRENT));
                    case "OTHER_DEVICE"  -> _result.postValue(SignResult.of(SignStatus.OTHER_DEVICE));
                    case "USED_BY_OTHER" -> _result.postValue(SignResult.of(SignStatus.USED_BY_OTHER));
                    default              -> _result.postValue(SignResult.error("Unknown status: " + res.getStatus()));
                }
            } catch (CommunicationException e) {
                CaLog.e("checkRegistrationStatus error: " + e.getMessage());
                _result.postValue(SignResult.error(e.getErrMsg()));
            } catch (Exception e) {
                CaLog.e("checkRegistrationStatus error: " + e.getMessage());
                _result.postValue(SignResult.error(e.getMessage()));
            }
        }).start();
    }

    /** Signup a NEW user. Saves tokens + loginId on success. */
    public void signup(Context context, String loginId, String password) {
        _result.postValue(SignResult.of(SignStatus.LOADING));
        new Thread(() -> {
            try {
                String walletId = Preference.getCaAppId(context);
                SignupReqVO req = new SignupReqVO(loginId, password, walletId);
                String body = new Gson().toJson(req);

                String response = TokenAwareHttpClient.send(
                        context, Config.CAS.SIGNUP, "POST", body);

                CasTokenResVO token = MessageUtil.deserialize(response, CasTokenResVO.class);
                saveTokensAndLoginId(context, loginId, token);

                CaLog.d("signup success");
                _result.postValue(SignResult.of(SignStatus.SIGNUP_SUCCESS));
            } catch (CommunicationException e) {
                CaLog.e("signup error: " + e.getMessage());
                _result.postValue(SignResult.error(e.getErrMsg()));
            } catch (Exception e) {
                CaLog.e("signup error: " + e.getMessage());
                _result.postValue(SignResult.error(e.getMessage()));
            }
        }).start();
    }

    /** Signin an existing user. Saves tokens + loginId on success. */
    public void signin(Context context, String loginId, String password) {
        _result.postValue(SignResult.of(SignStatus.LOADING));
        new Thread(() -> {
            try {
                String walletId = Preference.getCaAppId(context);
                SigninReqVO req = new SigninReqVO(loginId, password, walletId);
                String body = new Gson().toJson(req);

                String response = TokenAwareHttpClient.send(
                        context, Config.CAS.SIGNIN, "POST", body);

                CasTokenResVO token = MessageUtil.deserialize(response, CasTokenResVO.class);
                saveTokensAndLoginId(context, loginId, token);

                CaLog.d("signin success");
                _result.postValue(SignResult.of(SignStatus.SIGNIN_SUCCESS));
            } catch (CommunicationException e) {
                CaLog.e("signin error: " + e.getMessage());
                _result.postValue(SignResult.error(e.getErrMsg()));
            } catch (Exception e) {
                CaLog.e("signin error: " + e.getMessage());
                _result.postValue(SignResult.error(e.getMessage()));
            }
        }).start();
    }

    // ── private ───────────────────────────────────────────────────────────────

    private void saveTokensAndLoginId(Context context, String loginId, CasTokenResVO token) {
        Preference.setLoginId(context, loginId);
        AuthTokenHelper.saveAccessToken(context, token.getAccessToken());
        AuthTokenHelper.saveRefreshToken(context, token.getRefreshToken());
    }
}
