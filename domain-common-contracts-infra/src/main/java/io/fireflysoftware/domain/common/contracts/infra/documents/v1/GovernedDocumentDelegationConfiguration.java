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
package io.fireflysoftware.domain.common.contracts.infra.documents.v1;

import java.time.Clock;
import java.util.EnumMap;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
public class GovernedDocumentDelegationConfiguration {
  @Bean
  @ConditionalOnMissingBean(GovernedDocumentCredentialPort.class)
  GovernedDocumentCredentialPort governedDocumentCredentials(
      Environment environment, ObjectProvider<SecurityContextPort> contexts) {
    var audiences =
        new EnumMap<GovernedDocumentCredentialPort.Peer, String>(
            GovernedDocumentCredentialPort.Peer.class);
    for (var peer : GovernedDocumentCredentialPort.Peer.values()) {
      String audience =
          environment.getProperty(
              "firefly.contracts.documents.peers."
                  + peer.name().toLowerCase(java.util.Locale.ROOT)
                  + ".audience",
              "");
      if (!audience.isBlank()) audiences.put(peer, audience);
    }
    return new GovernedDocumentRelay(
        environment.getProperty("firefly.contracts.documents.delegation.mode", ""),
        audiences,
        contexts,
        Clock.systemUTC());
  }
}
