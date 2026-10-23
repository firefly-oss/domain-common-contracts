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
package io.fireflysoftware.qualification.contracts;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fireflysoftware.domain.common.contracts.sdk.api.GovernedContractDocumentsApi;
import io.fireflysoftware.domain.common.contracts.sdk.model.GovernedContractDocumentIntent;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GovernedContractDocumentGeneratedContractTest {
  @TempDir Path directory;

  @Test
  void oldTypedClientCodeStillCompilesWhileLegacyRoutesRequireMigration() throws Exception {
    Path source = directory.resolve("LegacyContractDocumentConsumer.java");
    Files.writeString(
        source,
        """
        import reactor.core.publisher.Mono;
        import java.util.UUID;
        import io.fireflysoftware.domain.common.contracts.sdk.api.*;
        import io.fireflysoftware.domain.common.contracts.sdk.model.*;
        class LegacyContractDocumentConsumer {
          Mono<RecordedId> issue(KitDocumentsControllerApi api,IssueBody body,String key) {return api.issue(body,key);}
          Mono<IssuedView> read(KitDocumentsControllerApi api,UUID id,String key) {return api.read(id,key);}
          Mono<Object> generate(ContractDocumentsApi api,UUID contract,GenerateContractDocumentCommand cmd,String key) {return api.generateDocument(contract,cmd,key);}
        }
        """);
    var diagnostics = new java.io.ByteArrayOutputStream();
    int result =
        ToolProvider.getSystemJavaCompiler()
            .run(
                null,
                diagnostics,
                diagnostics,
                "-proc:none",
                "-classpath",
                System.getProperty("java.class.path"),
                "-d",
                directory.toString(),
                source.toString());
    assertThat(result).as(diagnostics.toString(java.nio.charset.StandardCharsets.UTF_8)).isZero();
  }

  @Test
  void generatedNativeApiHasOnlyExplicitResourceAndIntentParameters() throws Exception {
    var type = GovernedContractDocumentsApi.class;
    assertThat(
            type.getMethod(
                "issueGovernedContractDocument",
                UUID.class,
                UUID.class,
                GovernedContractDocumentIntent.class))
        .isNotNull();
    for (String name :
        List.of(
            "resumeGovernedContractDocument",
            "getGovernedContractDocument",
            "getGovernedContractDocumentContentWithResponseSpec"))
      assertThat(type.getMethod(name, UUID.class, UUID.class, UUID.class)).isNotNull();
    try (var stream = getClass().getClassLoader().getResourceAsStream("openapi.yml")) {
      var spec = new ObjectMapper(new YAMLFactory()).readTree(stream);
      int routes = 0;
      var paths = spec.path("paths").fields();
      while (paths.hasNext()) {
        var route = paths.next();
        if (!route.getKey().contains("governed-documents")) continue;
        routes++;
        route
            .getValue()
            .forEach(
                operation ->
                    operation
                        .path("parameters")
                        .forEach(
                            parameter -> {
                              assertThat(parameter.path("in").asText()).isEqualTo("path");
                              assertThat(parameter.path("name").asText())
                                  .isIn("tenantId", "contractId", "actionId");
                            }));
      }
      assertThat(routes).isEqualTo(4);
    }
  }
}
