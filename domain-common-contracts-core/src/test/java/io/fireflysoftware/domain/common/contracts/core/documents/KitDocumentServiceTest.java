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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KitDocumentServiceTest {

    @Mock
    private DocumentControllerApi documents;

    @Test
    void issuesContractLetterAndWelcomeThroughDocumentCore() {
        KitDocumentService service = new KitDocumentService(documents);
        UUID partyId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        when(documents.createDocument(any(), any())).thenAnswer(invocation -> {
            DocumentDTO command = invocation.getArgument(0);
            DocumentDTO saved = new DocumentDTO(UUID.randomUUID());
            saved.setName(command.getName());
            return Mono.just(saved);
        });

        for (String code : new String[]{DocumentTypeCode.CONTRACT.code(), DocumentTypeCode.LETTER.code(), DocumentTypeCode.WELCOME.code()}) {
            UUID id = service.issue(code, partyId, contractId).block();
            assertThat(id).isNotNull();
        }

        ArgumentCaptor<DocumentDTO> captor = ArgumentCaptor.forClass(DocumentDTO.class);
        verify(documents, org.mockito.Mockito.times(3)).createDocument(captor.capture(), any());
        assertThat(captor.getAllValues()).extracting(DocumentDTO::getName)
                .containsExactly("contract", "letter", "welcome");
        assertThat(captor.getAllValues().get(0).getDocumentType()).isEqualTo(DocumentDTO.DocumentTypeEnum.CONTRACT);
        assertThat(captor.getValue().getDescription()).contains(partyId.toString()).contains(contractId.toString());
        assertThat(captor.getAllValues()).allSatisfy(body -> assertThat(body.getId()).isNull());
    }
}
