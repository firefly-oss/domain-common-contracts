/*
 * Copyright 2026 Firefly Software Foundation
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

import io.fireflysoftware.core.product.sdk.api.ProductApi;
import io.fireflysoftware.core.product.sdk.invoker.ApiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * Product core client. The contracts domain uses it to name a holding.
 * An experience journey does not call this client.
 */
@Component
public class ProductMgmtClientFactory {

    private final ApiClient apiClient;

    public ProductMgmtClientFactory(ProductMgmtProperties properties) {
        this.apiClient = new ApiClient();
        this.apiClient.setBasePath(properties.getBasePath());
    }

    @Bean
    public ProductApi productApi() {
        return new ProductApi(apiClient);
    }
}
