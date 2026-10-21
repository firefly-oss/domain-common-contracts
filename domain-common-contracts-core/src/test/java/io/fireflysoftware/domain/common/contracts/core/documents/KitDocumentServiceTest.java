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

import io.fireflysoftware.documents.DocumentPort;
import io.fireflysoftware.documents.DocumentTypeCode;
import io.fireflysoftware.documents.IssuedDocument;
import io.fireflysoftware.documents.SandboxEngagement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KitDocumentServiceTest {

    @Mock
    private DocumentPort documents;

    @Test
    void issuesContractLetterAndWelcomeThroughDocumentPort() {
        KitDocumentService service = new KitDocumentService(documents);
        UUID partyId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        when(documents.issue(any())).thenAnswer(invocation -> {
            var command = invocation.getArgument(0, io.fireflysoftware.documents.IssueDocument.class);
            return new IssuedDocument(UUID.randomUUID(), command.templateCode(), "text/html", "<p></p>", command.templateCode());
        });

        for (String code : new String[]{DocumentTypeCode.CONTRACT.code(), DocumentTypeCode.LETTER.code(), DocumentTypeCode.WELCOME.code()}) {
            UUID id = service.issue(code, partyId, contractId).block();
            assertThat(id).isNotNull();
        }

        ArgumentCaptor<io.fireflysoftware.documents.IssueDocument> captor =
                ArgumentCaptor.forClass(io.fireflysoftware.documents.IssueDocument.class);
        verify(documents, org.mockito.Mockito.times(3)).issue(captor.capture());
        assertThat(captor.getAllValues()).extracting(io.fireflysoftware.documents.IssueDocument::templateCode)
                .containsExactly("contract", "letter", "welcome");
        assertThat(captor.getValue().party().id()).isEqualTo(partyId);
        assertThat(captor.getValue().contract().id()).isEqualTo(contractId);
        assertThat(SandboxEngagement.issue("welcome").templateCode()).isEqualTo("welcome");
    }
}
