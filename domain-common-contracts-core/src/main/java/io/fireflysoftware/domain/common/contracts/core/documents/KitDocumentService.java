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

package io.fireflysoftware.domain.common.contracts.core.documents;

import io.fireflysoftware.documents.Agreement;
import io.fireflysoftware.documents.DocumentPort;
import io.fireflysoftware.documents.DocumentTypeCode;
import io.fireflysoftware.documents.IssueDocument;
import io.fireflysoftware.documents.Party;
import io.fireflysoftware.documents.SandboxEngagement;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Issues a contract, letter, or welcome through {@link DocumentPort}.
 * The journey calls {@code KitDocumentsApi}. This service is the domain behind that client.
 */
@Service
public class KitDocumentService {

    private final DocumentPort documents;

    public KitDocumentService(DocumentPort documents) {
        this.documents = documents;
    }

    public Mono<UUID> issue(String templateCode, UUID partyId, UUID contractId) {
        if (DocumentTypeCode.fromCode(templateCode) == null) {
            return Mono.error(new IllegalArgumentException("Unknown document template: " + templateCode));
        }
        return Mono.fromCallable(() -> documents.issue(command(templateCode, partyId, contractId)).id());
    }

    private static IssueDocument command(String templateCode, UUID partyId, UUID contractId) {
        IssueDocument sample = SandboxEngagement.issue(templateCode);
        UUID party = partyId == null ? sample.party().id() : partyId;
        UUID contract = contractId == null ? sample.contract().id() : contractId;
        return new IssueDocument(templateCode, sample.language(), sample.productFamily(), sample.regulated(),
                new Party(party, sample.party().givenName(), sample.party().name(), sample.party().role()),
                new Agreement(contract, sample.contract().status()),
                sample.product(), sample.currency(), sample.issuedOn(), sample.effectiveOn(), sample.endsOn(),
                sample.amount(), sample.lines(), sample.predecessorId(), sample.change());
    }
}
