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
import io.fireflysoftware.commons.ecm.sdk.model.SignatureSubmissionRequestDTO;
import io.fireflysoftware.domain.common.contracts.infra.ports.ESignaturePort;
import io.fireflysoftware.domain.common.contracts.infra.ports.SignatureRequestContextPort;
import io.fireflysoftware.domain.common.contracts.infra.ports.model.SignatureRequest;
import io.fireflysoftware.domain.common.contracts.infra.ports.model.SignatureRequestResult;
import io.fireflysoftware.domain.common.contracts.infra.ports.model.SignatureVerificationResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Objects;
import java.util.UUID;

/** Uses the core document authority for both synthetic and configured ECM providers. */
@Component
public class DocumentManagementESignatureAdapter implements ESignaturePort {
    private final SignatureSubmissionApi api;
    private final SignatureRequestContextPort context;
    public DocumentManagementESignatureAdapter(SignatureSubmissionApi api,SignatureRequestContextPort context) {
        this.api=api;this.context=context;
    }
    @Override public Mono<SignatureRequestResult> requestSignature(SignatureRequest request) {
        return context.current().flatMap(actor -> {
            String key="contract-signature:"+request.getContractId()+":"+request.getDocumentId()+":"+request.getSignerPartyId();
            var input=new SignatureSubmissionRequestDTO().contractId(request.getContractId()).documentId(request.getDocumentId())
                    .signerPartyId(request.getSignerPartyId()).signatureProviderId(actor.signatureProviderId()).tenantId(actor.tenantId())
                    .signatureRequestKey(key).signatureType(SignatureSubmissionRequestDTO.SignatureTypeEnum.DIGITAL)
                    .signatureFormat(SignatureSubmissionRequestDTO.SignatureFormatEnum.PADES);
            return api.submitContractSignature(input,key)
                    .switchIfEmpty(Mono.error(new IllegalStateException("Core returned no signature submission")))
                    .flatMap(result -> {
                        if ((Boolean.TRUE.equals(result.getSynthetic()) && !actor.syntheticAllowed()) || result.getSynthetic()==null)
                            return Mono.error(new IllegalStateException("Synthetic signatures are not permitted for this actor context"));
                        if(result.getId()==null || !Objects.equals(request.getContractId(),result.getContractId())
                                || !Objects.equals(request.getDocumentId(),result.getDocumentId())
                                || !Objects.equals(request.getSignerPartyId(),result.getSignerPartyId())
                                || !Objects.equals(actor.tenantId(),result.getTenantId()) || result.getProviderName()==null)
                            return Mono.error(new IllegalStateException("Core signature association does not match"));
                        return Mono.just(SignatureRequestResult.builder().signatureRequestId(result.getId().toString())
                                .provider(result.getProviderName()).status(result.getProviderStatus()).build());
                    });
        });
    }
    @Override public Mono<SignatureVerificationResult> verifySignature(String id) {
        return context.current().flatMap(actor -> api.verifyContractSignature(UUID.fromString(id),actor.tenantId(),null,null)
                .switchIfEmpty(Mono.error(new IllegalStateException("Core returned no signature verification")))
                .flatMap(result -> {
                    if ((Boolean.TRUE.equals(result.getSynthetic()) && !actor.syntheticAllowed()) || result.getSynthetic()==null)
                        return Mono.error(new IllegalStateException("Synthetic signatures are not permitted for this actor context"));
                    if(!Objects.equals(actor.tenantId(),result.getTenantId()) || !Objects.equals(UUID.fromString(id),result.getId()))
                        return Mono.error(new IllegalStateException("Core signature ownership does not match"));
                    boolean completed="COMPLETED".equals(result.getProviderStatus())
                            && result.getSignatureStatus()==DocumentSignatureDTO.SignatureStatusEnum.SIGNED && result.getSignedAt()!=null;
                    return Mono.just(SignatureVerificationResult.builder().signatureRequestId(id)
                            .contractId(result.getContractId()).documentId(result.getDocumentId()).signerPartyId(result.getSignerPartyId())
                            .tenantId(result.getTenantId()).synthetic(result.getSynthetic()).status(completed ? "VERIFIED" : result.getProviderStatus())
                            .provider(result.getProviderName()).build());
                }));
    }
}
