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

package org.omnione.did.ca.zkp;

import android.content.Context;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.omnione.did.ca.config.Config;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.ca.network.HttpUrlConnection;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.sdk.communication.exception.CommunicationException;
import org.omnione.did.sdk.datamodel.protocol.P310ZkpRequestVo;
import org.omnione.did.sdk.datamodel.util.MessageUtil;
import org.omnione.did.sdk.datamodel.zkp.CredentialDefinition;
import org.omnione.did.sdk.datamodel.zkp.CredentialDefinitionVo;
import org.omnione.did.sdk.datamodel.zkp.CredentialSchema;
import org.omnione.did.sdk.datamodel.zkp.CredentialSchemaVo;
import org.omnione.did.sdk.datamodel.zkp.ProofRequest;
import org.omnione.did.sdk.datamodel.util.GsonWrapper;
import org.omnione.did.sdk.utility.Errors.UtilityException;
import org.omnione.did.sdk.utility.MultibaseUtils;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class VerifyModel implements VerifyConstants.Model {

    private static final String ZKP_TAG = VerifyFragment.class.getName();

    private Context context;
    private VerifyConstants.Presenter presenter;
    private HttpUrlConnection httpClient;

    public VerifyModel(Context context, VerifyPresenter issuePresenter) {

        this.presenter = issuePresenter;
        this.context = context;
        this.httpClient = new HttpUrlConnection();
    }

    @Override
    public boolean verifyProof(final P310ZkpRequestVo proof, final ProofRequest proofRequest) {

        String api = "/verifier/api/v1/request-verify-proof";
        CaLog.d("proof: " + GsonWrapper.getGson().toJson(proof));

        String result = httpClient.send(context, Config.VERIFIER_URL + api, "POST", proof.toJson());
        return result != null;
    }

    @Override
    public CredentialDefinition getCredentialDefinition(final String credDefId) {
        ExecutorService es = Executors.newCachedThreadPool();
        Future<CredentialDefinition> future = es.submit(new Callable<CredentialDefinition>() {
            @Override
            public CredentialDefinition call() {
                try {
                    String credDef = httpClient.send(context, Config.API_GATEWAY_URL + "/api-gateway/api/v1/zkp-cred-def?id="+credDefId, "GET","");
                    CaLog.d("getCredDef >>>>>>>>>> " + credDef);
                    CredentialDefinitionVo credentialDefinitionVo = MessageUtil.deserialize(credDef, CredentialDefinitionVo.class);
                    CredentialDefinition credentialDefinition = MessageUtil.deserialize(new String(MultibaseUtils.decode(credentialDefinitionVo.getCredDef())), CredentialDefinition.class);
                    CaLog.d("credentialDefinition: "+GsonWrapper.getGson().toJson(credentialDefinition));
                    return credentialDefinition;
                } catch (UtilityException e) {
                    ContextCompat.getMainExecutor(context).execute(()  -> {
                        CaUtil.showErrorDialog(context, e.getMessage());
                    });
                }
                return null;
            }
        });

        try {
            return future.get();

        } catch (ExecutionException | InterruptedException e) {
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        return null;
    }


    @Override
    public CredentialSchema getCredentialSchema(final String schemaId) {
        ExecutorService es = Executors.newCachedThreadPool();
        Future<CredentialSchema> future = es.submit(new Callable<CredentialSchema>() {
            @Override
            public CredentialSchema call() {
                try {
                    String schema = httpClient.send(context, Config.API_GATEWAY_URL + "/api-gateway/api/v1/zkp-cred-schema?id="+schemaId, "GET","");
                    CredentialSchemaVo credentialSchemaVo = MessageUtil.deserialize(schema, CredentialSchemaVo.class);
                    CredentialSchema credentialSchema = MessageUtil.deserialize(new String(MultibaseUtils.decode(credentialSchemaVo.getCredSchema())), CredentialSchema.class);
                    CaLog.d("credentialSchema: "+GsonWrapper.getGson().toJson(credentialSchema));
                    return credentialSchema;
                } catch (UtilityException e) {
                    ContextCompat.getMainExecutor(context).execute(()  -> {
                        CaUtil.showErrorDialog(context, e.getMessage());
                    });
                }
                return null;
            }
        });

        try {
            return future.get();

        } catch (ExecutionException | InterruptedException e) {
            ContextCompat.getMainExecutor(context).execute(()  -> {
                CaUtil.showErrorDialog(context, e.getMessage());
            });
        }
        return null;
    }
}
