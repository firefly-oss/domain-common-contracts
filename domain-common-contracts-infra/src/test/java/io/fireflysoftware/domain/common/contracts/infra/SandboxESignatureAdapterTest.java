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

import io.fireflysoftware.commons.ecm.sdk.api.SignatureSubmissionApi;
import io.fireflysoftware.commons.ecm.sdk.model.DocumentSignatureDTO;
import io.fireflysoftware.domain.common.contracts.infra.ports.SignatureRequestContextPort;
import io.fireflysoftware.domain.common.contracts.infra.ports.model.SignatureRequest;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SandboxESignatureAdapterTest {
    final SignatureSubmissionApi api=mock(SignatureSubmissionApi.class);
    final UUID contract=UUID.randomUUID(),document=UUID.randomUUID(),signer=UUID.randomUUID(),provider=UUID.randomUUID(),id=UUID.randomUUID();
    final DocumentManagementESignatureAdapter adapter=new DocumentManagementESignatureAdapter(api,
            ()->Mono.just(new SignatureRequestContextPort.Context("synthetic-tenant",provider,true)));
    DocumentSignatureDTO stored(String state) {
        return new DocumentSignatureDTO(id,"sandbox",state,true,id,null,null,null,null,null,null,null).contractId(contract).documentId(document).signerPartyId(signer)
                .tenantId("synthetic-tenant");
    }
    @Test void requestUsesCoreAuthorityAndStableBusinessIdentity() {
        when(api.submitContractSignature(any(),any())).thenReturn(Mono.just(stored("PENDING")));
        var request=SignatureRequest.builder().contractId(contract).documentId(document).signerPartyId(signer).build();
        StepVerifier.create(adapter.requestSignature(request)).assertNext(result -> {
            assertThat(result.getSignatureRequestId()).isEqualTo(id.toString());assertThat(result.getStatus()).isEqualTo("PENDING");
        }).verifyComplete();
        verify(api).submitContractSignature(argThat(input -> input.getContractId().equals(contract)
                && input.getTenantId().equals("synthetic-tenant") && input.getSignatureProviderId().equals(provider)),
                eq("contract-signature:"+contract+":"+document+":"+signer));
    }
    @Test void pendingIsNotVerified() {
        when(api.verifyContractSignature(eq(id),any(),isNull(),isNull())).thenReturn(Mono.just(stored("PENDING")));
        StepVerifier.create(adapter.verifySignature(id.toString())).assertNext(result -> assertThat(result.getStatus()).isEqualTo("PENDING")).verifyComplete();
    }
    @Test void completionRetainsBindingAndSyntheticProvenance() {
        var result=stored("COMPLETED").signatureStatus(DocumentSignatureDTO.SignatureStatusEnum.SIGNED).signedAt(java.time.LocalDateTime.now());
        when(api.verifyContractSignature(eq(id),any(),isNull(),isNull())).thenReturn(Mono.just(result));
        StepVerifier.create(adapter.verifySignature(id.toString())).assertNext(value -> {
            assertThat(value.getStatus()).isEqualTo("VERIFIED");assertThat(value.getContractId()).isEqualTo(contract);
            assertThat(value.getDocumentId()).isEqualTo(document);assertThat(value.getSignerPartyId()).isEqualTo(signer);
            assertThat(value.getSynthetic()).isTrue();
        }).verifyComplete();
    }
    @Test void completionWithoutPersistedSigningTimestampIsNotVerified() {
        when(api.verifyContractSignature(eq(id),any(),isNull(),isNull())).thenReturn(Mono.just(stored("COMPLETED")));
        StepVerifier.create(adapter.verifySignature(id.toString())).assertNext(value -> assertThat(value.getStatus()).isNotEqualTo("VERIFIED")).verifyComplete();
    }
    @Test void missingCoreRequestFails() {
        when(api.verifyContractSignature(eq(id),any(),isNull(),isNull())).thenReturn(Mono.empty());
        StepVerifier.create(adapter.verifySignature(id.toString())).expectError(IllegalStateException.class).verify();
    }
    @Test void liveActorContextCannotAcceptSyntheticCompletion() {
        var live=new DocumentManagementESignatureAdapter(api,()->Mono.just(new SignatureRequestContextPort.Context("synthetic-tenant",provider)));
        when(api.verifyContractSignature(eq(id),any(),isNull(),isNull())).thenReturn(Mono.just(stored("COMPLETED")
                .signatureStatus(DocumentSignatureDTO.SignatureStatusEnum.SIGNED).signedAt(java.time.LocalDateTime.now())));
        StepVerifier.create(live.verifySignature(id.toString())).expectError(IllegalStateException.class).verify();
    }
    @Test void wrongTenantCannotBeVerified() {
        when(api.verifyContractSignature(eq(id),any(),isNull(),isNull())).thenReturn(Mono.just(stored("COMPLETED").tenantId("other-tenant")));
        StepVerifier.create(adapter.verifySignature(id.toString())).expectError(IllegalStateException.class).verify();
    }
}
