package com.firefly.domain.common.contracts.infra;

import com.firefly.commons.ecm.sdk.api.*;
import com.firefly.commons.ecm.sdk.invoker.ApiClient;
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
