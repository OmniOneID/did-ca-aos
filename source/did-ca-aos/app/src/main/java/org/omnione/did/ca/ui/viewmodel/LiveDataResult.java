package org.omnione.did.ca.ui.viewmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LiveDataResult {
    @NonNull
    public final Status status;

    @Nullable
    public final BaseModel data;

    @Nullable
    public final Throwable error;

    @Nullable
    public final String errorMessage;

    private LiveDataResult(@NonNull Status status, @Nullable BaseModel data, @Nullable Throwable error, @Nullable String errorMessage) {
        this.status = status;
        this.data = data;
        this.error = error;
        this.errorMessage = errorMessage;
    }

    public static  LiveDataResult success(@NonNull BaseModel data) {
        return new LiveDataResult(Status.SUCCESS, data, null, null);
    }
    public static  LiveDataResult success() {
        return new LiveDataResult(Status.SUCCESS, null, null, null);
    }
    public static  LiveDataResult error(@NonNull Throwable error, @Nullable BaseModel data, @Nullable String userFriendlyMessage) {
        return new LiveDataResult(Status.ERROR, data, error, userFriendlyMessage);
    }
    public static  LiveDataResult error(@NonNull String userFriendlyMessage, @Nullable BaseModel data) {
        return new LiveDataResult(Status.ERROR, data, null, userFriendlyMessage);
    }
    public static  LiveDataResult error(@NonNull String userFriendlyMessage) {
        return new LiveDataResult(Status.ERROR, null, null, userFriendlyMessage);
    }
    public static  LiveDataResult error(@NonNull Throwable error, @Nullable BaseModel data) {
        return new LiveDataResult(Status.ERROR, data, error, error.getLocalizedMessage());
    }
    public static  LiveDataResult loading(@Nullable BaseModel data) {
        return new LiveDataResult(Status.LOADING, data, null, null);
    }
    public static  LiveDataResult loading() {
        return new LiveDataResult(Status.LOADING, null, null, null);
    }

    @Override
    public String toString() {
        return "Result{" +
                "status=" + status +
                ", data=" + data +
                ", error=" + (error != null ? error.getMessage() : "null") +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }

    public enum Status {
        SUCCESS,
        ERROR,
        LOADING
    }
}
