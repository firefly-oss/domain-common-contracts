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

import io.fireflysoftware.domain.common.contracts.infra.ports.SignatureRequestContextPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Configuration
public class SignatureRequestContextConfiguration {
    @Bean
    @ConditionalOnMissingBean(SignatureRequestContextPort.class)
    SignatureRequestContextPort signatureRequestContextPort(Environment environment) {
        return () -> Mono.defer(() -> {
            if (!environment.acceptsProfiles(Profiles.of("sandbox & !prod & !production"))) {
                return Mono.error(new IllegalStateException("A validated signature actor context adapter is required"));
            }
            String tenant=environment.getProperty("integration.esignature.sandbox.tenant-id");
            String provider=environment.getProperty("integration.esignature.sandbox.signature-provider-id");
            if (tenant==null || tenant.isBlank() || provider==null || provider.isBlank())
                return Mono.error(new IllegalStateException("Explicit synthetic tenant and signature provider are required"));
            return Mono.just(new SignatureRequestContextPort.Context(tenant,UUID.fromString(provider),true));
        });
    }
}
