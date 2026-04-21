package com.firefly.domain.common.contracts.core.handlers;

import com.firefly.core.contract.sdk.api.GlobalContractPartiesApi;
import com.firefly.core.contract.sdk.model.ContractPartyDTO;
import com.firefly.core.contract.sdk.model.PaginationResponseContractPartyDTO;
import com.firefly.domain.common.contracts.core.queries.GetActiveContractsByPartyQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetActiveContractsByPartyHandlerTest {

    @Mock
    private GlobalContractPartiesApi globalContractPartiesApi;

    private GetActiveContractsByPartyHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetActiveContractsByPartyHandler(globalContractPartiesApi);
    }

    @Test
    void doHandle_shouldCallGetContractPartiesByPartyIdWithActiveTrue() {
        UUID partyId = UUID.randomUUID();
        GetActiveContractsByPartyQuery query = new GetActiveContractsByPartyQuery(partyId);

        ContractPartyDTO partyDto = new ContractPartyDTO(UUID.randomUUID(), null, null);
        PaginationResponseContractPartyDTO response = new PaginationResponseContractPartyDTO();
        response.setContent(List.of(partyDto));

        when(globalContractPartiesApi.getContractPartiesByPartyId(eq(partyId), eq(true), any()))
                .thenReturn(Mono.just(response));

        StepVerifier.create(handler.doHandle(query))
                .assertNext(results -> assertThat(results).hasSize(1))
                .verifyComplete();

        verify(globalContractPartiesApi).getContractPartiesByPartyId(eq(partyId), eq(true), any());
    }
}
