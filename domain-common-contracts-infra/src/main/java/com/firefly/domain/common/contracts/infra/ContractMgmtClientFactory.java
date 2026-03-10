package com.firefly.domain.common.contracts.infra;

import com.firefly.core.contract.sdk.api.*;
import com.firefly.core.contract.sdk.invoker.ApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * Client factory for the core-common-contract-mgmt SDK.
 * Creates and exposes API beans for contract management operations.
 */
@Component
public class ContractMgmtClientFactory {

    private final ApiClient apiClient;

    @Autowired
    public ContractMgmtClientFactory(ContractMgmtProperties contractMgmtProperties) {
        this.apiClient = new ApiClient();
        this.apiClient.setBasePath(contractMgmtProperties.getBasePath());
    }

    @Bean
    public ContractsApi contractsApi() {
        return new ContractsApi(apiClient);
    }

    @Bean
    public ContractPartiesApi contractPartiesApi() {
        return new ContractPartiesApi(apiClient);
    }

    @Bean
    public ContractTermsApi contractTermsApi() {
        return new ContractTermsApi(apiClient);
    }

    @Bean
    public ContractDocumentsApi contractDocumentsApi() {
        return new ContractDocumentsApi(apiClient);
    }

    @Bean
    public ContractStatusHistoryApi contractStatusHistoryApi() {
        return new ContractStatusHistoryApi(apiClient);
    }

    @Bean
    public ContractEventsApi contractEventsApi() {
        return new ContractEventsApi(apiClient);
    }

    @Bean
    public ContractRiskAssessmentsApi contractRiskAssessmentsApi() {
        return new ContractRiskAssessmentsApi(apiClient);
    }

    @Bean
    public GlobalContractPartiesApi globalContractPartiesApi() {
        return new GlobalContractPartiesApi(apiClient);
    }

    @Bean
    public ContractTermTemplatesApi contractTermTemplatesApi() {
        return new ContractTermTemplatesApi(apiClient);
    }

    @Bean
    public ContractTermValidationRulesApi contractTermValidationRulesApi() {
        return new ContractTermValidationRulesApi(apiClient);
    }

}
