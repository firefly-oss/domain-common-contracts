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

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.assertThat;

class SignatureRequestContextConfigurationTest {
    @Test void productionNeverUsesConfiguredSyntheticTenantAsAuthority() {
        var environment=new MockEnvironment().withProperty("integration.esignature.sandbox.tenant-id","synthetic-tenant")
                .withProperty("integration.esignature.sandbox.signature-provider-id","44444444-4444-4444-4444-444444444444");
        environment.setActiveProfiles("prod");
        StepVerifier.create(new SignatureRequestContextConfiguration().signatureRequestContextPort(environment).current())
                .expectError(IllegalStateException.class).verify();
    }
    @Test void sandboxRequiresExplicitTenantAndProviderConfiguration() {
        var environment=new MockEnvironment();environment.setActiveProfiles("sandbox");
        StepVerifier.create(new SignatureRequestContextConfiguration().signatureRequestContextPort(environment).current())
                .expectError(IllegalStateException.class).verify();
    }
    @Test void sandboxResolvesOnlyExplicitSyntheticOwnership() {
        var environment=new MockEnvironment().withProperty("integration.esignature.sandbox.tenant-id","synthetic-tenant")
                .withProperty("integration.esignature.sandbox.signature-provider-id","44444444-4444-4444-4444-444444444444");
        environment.setActiveProfiles("sandbox");
        StepVerifier.create(new SignatureRequestContextConfiguration().signatureRequestContextPort(environment).current())
                .assertNext(context -> assertThat(context.tenantId()).isEqualTo("synthetic-tenant")).verifyComplete();
    }
}
