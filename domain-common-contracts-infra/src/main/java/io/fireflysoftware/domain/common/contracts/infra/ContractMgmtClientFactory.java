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

import io.fireflysoftware.core.contract.sdk.api.*;
import io.fireflysoftware.core.contract.sdk.invoker.ApiClient;
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

    @Bean
    public ContractLinesApi contractLinesApi() {
        return new ContractLinesApi(apiClient);
    }

}
