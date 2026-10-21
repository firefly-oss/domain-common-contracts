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

package io.fireflysoftware.domain.common.contracts.sdk.api;

import io.fireflysoftware.domain.common.contracts.sdk.invoker.ApiClient;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Domain client for a contract, letter, or welcome document.
 * A journey uses this class. It does not call the document core.
 */
public class KitDocumentsApi {

    private final WebClient webClient;
    private final String basePath;

    public KitDocumentsApi(ApiClient apiClient) {
        this.webClient = apiClient.getWebClient();
        this.basePath = apiClient.getBasePath();
    }

    public Mono<UUID> issue(String templateCode, UUID partyId, UUID contractId, String key) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("templateCode", templateCode);
        body.put("partyId", partyId);
        body.put("contractId", contractId);
        return webClient.post()
                .uri(basePath + "/api/v1/kit-documents")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Idempotency-Key", key)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(RecordedId.class)
                .map(RecordedId::id);
    }

    public record RecordedId(UUID id) {
    }
}
