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

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class GovernedPeerEndpointTest {
  void rejects(String value, boolean sandbox) {
    assertThatThrownBy(() -> new GovernedPeerEndpoint(value, sandbox).required())
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> {
              assertThat(e.getStatusCode().value()).isEqualTo(503);
              assertThat(e.getReason()).isEqualTo("GOVERNED_DOCUMENT_PEER_NOT_CONFIGURED");
            });
  }

  @Test
  void httpsOriginIsExplicitAndDoesNotRequireSandbox() {
    assertThat(
            new GovernedPeerEndpoint("https://synthetic.example.invalid:9443/", false).required())
        .isEqualTo("https://synthetic.example.invalid:9443");
  }

  @Test
  void cleartextNeedsExplicitSandboxAndLiteralLoopbackOrigin() {
    for (String host : List.of("127.0.0.1", "localhost", "[::1]")) {
      String url = "http://" + host + ":12345";
      rejects(url, false);
      assertThat(new GovernedPeerEndpoint(url, true).required()).isEqualTo(url);
    }
    for (String value :
        List.of(
            "http://synthetic.example.invalid",
            "http://127.1:12345",
            "http://127.0.0.2:12345",
            "http://localhost.evil.invalid")) rejects(value, true);
  }

  @Test
  void absentCredentialsQueryFragmentPathAliasesAndInvalidPortsReject() {
    rejects(null, false);
    for (String value :
        List.of(
            "",
            " ",
            "https://user:secret@synthetic.example.invalid",
            "https://synthetic.example.invalid?",
            "https://synthetic.example.invalid#",
            "https://synthetic.example.invalid/api",
            "https://synthetic.example.invalid/../",
            "https://synthetic.example.invalid/%2e",
            "https://synthetic.example.invalid//",
            "https://synthetic.example.invalid:0",
            "https://synthetic.example.invalid:65536",
            "file:///synthetic",
            "//synthetic.example.invalid",
            "https://synthetic.example.invalid/" + "x".repeat(2100))) rejects(value, false);
  }
}
