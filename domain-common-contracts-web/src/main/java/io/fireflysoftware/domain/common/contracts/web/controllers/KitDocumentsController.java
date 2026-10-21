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

package io.fireflysoftware.domain.common.contracts.web.controllers;

import io.fireflysoftware.domain.common.contracts.core.documents.KitDocumentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kit-documents")
public class KitDocumentsController {

    private final KitDocumentService documents;

    public KitDocumentsController(KitDocumentService documents) {
        this.documents = documents;
    }

    @PostMapping
    public Mono<RecordedId> issue(@RequestBody IssueBody body) {
        return documents.issue(body.templateCode(), body.partyId(), body.contractId()).map(RecordedId::new);
    }

    @GetMapping("/{id}")
    public Mono<IssuedView> read(@PathVariable UUID id) {
        return documents.read(id).map(document -> new IssuedView(
                document.id(), document.templateCode(), document.name()));
    }

    public record IssueBody(String templateCode, UUID partyId, UUID contractId) {
    }

    public record RecordedId(UUID id) {
    }

    public record IssuedView(UUID id, String templateCode, String name) {
    }
}
