package org.omnione.did.ca.ui.viewmodel;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.protocol.BaseOperation;
import org.omnione.did.ca.network.protocol.ProtocolData;

import java.util.Map;

public class AddBioViewModel extends ViewModel {
    private final MutableLiveData<LiveDataResult> preProcessDidUpdateLiveData = new MutableLiveData<>();
    private final MutableLiveData<LiveDataResult> processDidUpdateLiveData = new MutableLiveData<>();

    public LiveData<LiveDataResult> getPreProcessDidUpdateLiveData() {
        return this.preProcessDidUpdateLiveData;
    }

    public LiveData<LiveDataResult> getProcessDidUpdateLiveData() {
        return this.processDidUpdateLiveData;
    }
    public void preProcess(Context context) {

        preProcessDidUpdateLiveData.postValue(LiveDataResult.loading());

        BaseOperation.getInstance(context, BaseOperation.OperationType.USER_UPDATE, null)
        .setCallback(new BaseOperation.Callback() {
            @Override
            public void onSuccess(ProtocolData resultData) {
                CaLog.d("user DID update preExecute success");
                preProcessDidUpdateLiveData.postValue(LiveDataResult.success(resultData));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                CaLog.d("user DID update preExecute failure");
                preProcessDidUpdateLiveData.postValue(LiveDataResult.error(errorMessage));
            }
        }).preExecute();
    }

    public void process(Context context, Map<String, String> requestData) {

        processDidUpdateLiveData.postValue(LiveDataResult.loading());

        BaseOperation.getInstance(context, BaseOperation.OperationType.USER_UPDATE, requestData)
        .setCallback(new BaseOperation.Callback() {
            @Override
            public void onSuccess(ProtocolData resultData) {
                CaLog.d("user DID update execute success");
                processDidUpdateLiveData.postValue(LiveDataResult.success(resultData));
            }

            @Override
            public void onFailure(int errorCode, String errorMessage) {
                CaLog.d("user DID update execute failure");
                processDidUpdateLiveData.postValue(LiveDataResult.error(errorMessage));
            }
        }).execute();
    }
}
