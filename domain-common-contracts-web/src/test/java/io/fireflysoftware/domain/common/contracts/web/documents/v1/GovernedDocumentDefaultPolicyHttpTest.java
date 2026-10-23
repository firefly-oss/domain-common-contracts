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
package io.fireflysoftware.domain.common.contracts.web.documents.v1;

import static org.assertj.core.api.Assertions.*;

import io.fireflysoftware.domain.common.contracts.core.documents.v1.GovernedDocumentAccessGuard;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;

@SpringBootTest(
    classes = GovernedDocumentDefaultPolicyHttpTest.Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "server.address=127.0.0.1",
      "firefly.eda.enabled=false",
      "firefly.stepevents.enabled=false",
      "firefly.security.resource-server.strict-policy=true"
    })
class GovernedDocumentDefaultPolicyHttpTest {
  static final GovernedDocumentHttpTestSupport issuer = new GovernedDocumentHttpTestSupport();

  @DynamicPropertySource
  static void trust(DynamicPropertyRegistry p) {
    p.add(
        "firefly.contracts.documents.security.issuer",
        () -> GovernedDocumentHttpTestSupport.ISSUER);
    p.add("firefly.contracts.documents.security.audience", () -> "domain-contracts");
    p.add("firefly.contracts.documents.security.jwk-set-uri", issuer::jwks);
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({
    GovernedDocumentSecurityConfiguration.class,
    GovernedDocumentAccessGuard.class,
    GovernedDocumentHttpTestSupport.GuardProbe.class
  })
  static class Application {}

  @LocalServerPort int port;

  @AfterAll
  static void stop() {
    issuer.close();
  }

  @Test
  void signedTokenWithoutProjectPolicyRulesCannotReadTheResource() throws Exception {
    GovernedDocumentHttpTestSupport.RESOURCE_READS.set(0);
    var response = issuer.get(port);
    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).doesNotContain("synthetic-private-document");
    assertThat(GovernedDocumentHttpTestSupport.RESOURCE_READS).hasValue(0);
  }
}
