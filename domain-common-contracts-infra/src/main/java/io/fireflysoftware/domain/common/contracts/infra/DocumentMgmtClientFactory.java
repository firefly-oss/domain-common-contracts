/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.domain.common.contracts.infra;

import io.fireflysoftware.commons.ecm.sdk.api.*;
import io.fireflysoftware.commons.ecm.sdk.invoker.ApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * Client factory for the core-common-document-mgmt SDK.
 * Creates and exposes API beans for document management operations.
 */
@Component
public class DocumentMgmtClientFactory {

    private final ApiClient apiClient;

    @Autowired
    public DocumentMgmtClientFactory(DocumentMgmtProperties documentMgmtProperties) {
        this.apiClient = new ApiClient();
        this.apiClient.setBasePath(documentMgmtProperties.getBasePath());
    }

    @Bean
    public DocumentControllerApi documentControllerApi() {
        return new DocumentControllerApi(apiClient);
    }

    @Bean
    public DocumentSignatureControllerApi documentSignatureControllerApi() {
        return new DocumentSignatureControllerApi(apiClient);
    }

    @Bean
    public SignatureRequestControllerApi signatureRequestControllerApi() {
        return new SignatureRequestControllerApi(apiClient);
    }

    @Bean
    public SignatureVerificationControllerApi signatureVerificationControllerApi() {
        return new SignatureVerificationControllerApi(apiClient);
    }

    @Bean
    public SignatureProviderControllerApi signatureProviderControllerApi() {
        return new SignatureProviderControllerApi(apiClient);
    }

    @Bean
    public DocumentVersionControllerApi documentVersionControllerApi() {
        return new DocumentVersionControllerApi(apiClient);
    }

    @Bean
    public DocumentMetadataControllerApi documentMetadataControllerApi() {
        return new DocumentMetadataControllerApi(apiClient);
    }

    @Bean
    public DocumentTagControllerApi documentTagControllerApi() {
        return new DocumentTagControllerApi(apiClient);
    }

    @Bean
    public DocumentPermissionControllerApi documentPermissionControllerApi() {
        return new DocumentPermissionControllerApi(apiClient);
    }

    @Bean
    public DocumentSearchControllerApi documentSearchControllerApi() {
        return new DocumentSearchControllerApi(apiClient);
    }

    @Bean
    public FolderControllerApi folderControllerApi() {
        return new FolderControllerApi(apiClient);
    }

    @Bean
    public TagControllerApi tagControllerApi() {
        return new TagControllerApi(apiClient);
    }

}
