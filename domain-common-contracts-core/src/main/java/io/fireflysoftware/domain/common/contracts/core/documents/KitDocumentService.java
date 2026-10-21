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

import io.fireflysoftware.commons.ecm.sdk.api.DocumentControllerApi;
import io.fireflysoftware.commons.ecm.sdk.model.DocumentDTO;
import io.fireflysoftware.documents.DocumentTypeCode;
import io.fireflysoftware.documents.IssuedDocument;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Issues a contract, letter, or welcome through the document core SDK.
 * The journey calls {@code KitDocumentsApi}. This service is the domain behind that client.
 */
@Service
public class KitDocumentService {

    private final DocumentControllerApi documents;

    public KitDocumentService(DocumentControllerApi documents) {
        this.documents = documents;
    }

    public Mono<UUID> issue(String templateCode, UUID partyId, UUID contractId) {
        if (DocumentTypeCode.fromCode(templateCode) == null) {
            return Mono.error(new IllegalArgumentException("Unknown document template: " + templateCode));
        }
        DocumentDTO body = new DocumentDTO();
        body.setName(templateCode);
        body.setDescription(describe(templateCode, partyId, contractId));
        body.setFileName(templateCode + ".html");
        body.setFileExtension("html");
        body.setMimeType("text/html");
        body.setDocumentType(documentType(templateCode));
        body.setDocumentStatus(DocumentDTO.DocumentStatusEnum.PUBLISHED);
        body.setStorageType(DocumentDTO.StorageTypeEnum.DATABASE);
        if (partyId != null) {
            body.setTenantId(partyId.toString());
        }
        return documents.createDocument(body, UUID.randomUUID().toString())
                .flatMap(saved -> {
                    if (saved == null || saved.getId() == null) {
                        return Mono.error(new IllegalStateException("Document core did not return an id"));
                    }
                    return Mono.just(saved.getId());
                });
    }

    public Mono<IssuedDocument> read(UUID documentId) {
        return documents.getDocumentById(documentId, UUID.randomUUID().toString())
                .flatMap(dto -> {
                    if (dto == null || dto.getId() == null) {
                        return Mono.error(new IllegalStateException("Document core did not return an id"));
                    }
                    String template = dto.getName() == null ? dto.getDescription() : dto.getName();
                    return Mono.just(new IssuedDocument(dto.getId(), template, dto.getMimeType(), "", dto.getName()));
                });
    }

    private static String describe(String templateCode, UUID partyId, UUID contractId) {
        return templateCode + " party=" + partyId + " contract=" + contractId;
    }

    private static DocumentDTO.DocumentTypeEnum documentType(String templateCode) {
        if (DocumentTypeCode.CONTRACT.code().equals(templateCode)) {
            return DocumentDTO.DocumentTypeEnum.CONTRACT;
        }
        return DocumentDTO.DocumentTypeEnum.DOCUMENT;
    }
}
