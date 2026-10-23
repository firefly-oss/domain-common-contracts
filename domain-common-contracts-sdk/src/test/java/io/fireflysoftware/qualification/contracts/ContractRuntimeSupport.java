/*
 * Copyright 2025 Firefly Software Foundation
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

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipFile;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.flywaydb.core.Flyway;
import org.testcontainers.postgresql.PostgreSQLContainer;

final class ContractRuntimeSupport {
  static final String KEY =
      System.getenv("CONTRACT_FIXTURE_SIGNING_KEY") == null
          ? randomKey()
          : System.getenv("CONTRACT_FIXTURE_SIGNING_KEY");
  static final Path CONTRACT_JAR =
      Path.of(
              System.getProperty(
                  "contract.domain.qualifier.domain-jar",
                  "../domain-common-contracts-web/target/domain-common-contracts.jar"))
          .toAbsolutePath()
          .normalize();

  static PostgreSQLContainer postgres(String name) {
    return new PostgreSQLContainer("postgres:17-alpine")
        .withDatabaseName(name)
        .withUsername("synthetic_contract")
        .withPassword("synthetic-only")
        .withCreateContainerCmdModifier(
            cmd ->
                cmd.getHostConfig()
                    .withPortBindings(
                        new com.github.dockerjava.api.model.PortBinding(
                            com.github.dockerjava.api.model.Ports.Binding.bindIp("127.0.0.1"),
                            new com.github.dockerjava.api.model.ExposedPort(5432))));
  }

  static String randomKey() {
    byte[] bytes = new byte[32];
    new java.security.SecureRandom().nextBytes(bytes);
    return Base64.getEncoder().encodeToString(bytes);
  }

  static int port() throws Exception {
    try (var socket = new java.net.ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      return socket.getLocalPort();
    }
  }

  static void sql(PostgreSQLContainer pg, String sql, Object... values) throws Exception {
    try (var c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        var q = c.prepareStatement(sql)) {
      for (int i = 0; i < values.length; i++) q.setObject(i + 1, values[i]);
      q.executeUpdate();
    }
  }

  static long count(PostgreSQLContainer pg, String sql, Object... values) throws Exception {
    try (var c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        var q = c.prepareStatement(sql)) {
      for (int i = 0; i < values.length; i++) q.setObject(i + 1, values[i]);
      try (var r = q.executeQuery()) {
        assertThat(r.next()).isTrue();
        return r.getLong(1);
      }
    }
  }

  static void migrate(Path bootJar, String prefix, PostgreSQLContainer pg) throws Exception {
    assertThat(bootJar).isRegularFile();
    Path directory = Files.createTempDirectory("synthetic-contract-migrations-");
    try (var boot = new ZipFile(bootJar.toFile())) {
      var nested =
          boot.stream()
              .filter(
                  entry ->
                      entry.getName().startsWith("BOOT-INF/lib/" + prefix)
                          && entry.getName().endsWith(".jar"))
              .findFirst()
              .orElseThrow();
      Path model = Files.createTempFile("synthetic-contract-models-", ".jar");
      try (var input = boot.getInputStream(nested)) {
        Files.copy(input, model, StandardCopyOption.REPLACE_EXISTING);
      }
      try (var jar = new ZipFile(model.toFile())) {
        for (var entry :
            jar.stream()
                .filter(
                    value ->
                        value.getName().startsWith("db/migration/")
                            && value.getName().endsWith(".sql"))
                .toList())
          try (var input = jar.getInputStream(entry)) {
            Files.copy(input, directory.resolve(Path.of(entry.getName()).getFileName()));
          }
      }
    }
    Flyway.configure()
        .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
        .locations("filesystem:" + directory)
        .load()
        .migrate();
  }

  static Process boot(
      Path jar,
      PostgreSQLContainer pg,
      int port,
      String audience,
      boolean trust,
      Map<String, String> properties)
      throws Exception {
    var args =
        new ArrayList<String>(
            List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx320m"));
    if (trust)
      args.addAll(
          List.of(
              "-Dloader.path=" + Path.of("target/test-classes").toAbsolutePath(),
              "-Dloader.main="
                  + (audience.equals("domain")
                      ? "io.fireflysoftware.qualification.contracts.GovernedDocumentRuntimeBootstrap"
                      : "io.fireflysoftware.qualification.contracts.ContractDocumentRuntimeBootstrap"),
              "-Dcontract.fixture.audience=" + audience,
              "-Dcontract.fixture.application="
                  + switch (audience) {
                    case "masters" -> "io.fireflysoftware.masters.web.MasterDataApplication";
                    case "config" ->
                        "io.fireflysoftware.common.config.web.CommonPlatformConfigMgmtApplication";
                    case "customers" -> "io.fireflysoftware.core.customer.web.CustomerApplication";
                    case "products" ->
                        "io.fireflysoftware.core.product.web.ProductManagementApplication";
                    case "document" ->
                        "io.fireflysoftware.commons.ecm.web.EnterpriseContentManagementApplication";
                    case "domain" ->
                        "io.fireflysoftware.domain.common.contracts.web.DomainCommonContractsApplication";
                    default -> "io.fireflysoftware.core.contracts.web.ContractApplication";
                  },
              "-cp",
              jar.toString(),
              "org.springframework.boot.loader.launch.PropertiesLauncher"));
    else args.addAll(List.of("-jar", jar.toString()));
    args.addAll(
        List.of(
            "--server.address=127.0.0.1",
            "--server.port=" + port,
            "--DB_HOST=" + pg.getHost(),
            "--DB_PORT=" + pg.getMappedPort(5432),
            "--DB_NAME=" + pg.getDatabaseName(),
            "--DB_USERNAME=" + pg.getUsername(),
            "--DB_PASSWORD=" + pg.getPassword(),
            "--DB_SSL_MODE=disable",
            "--spring.flyway.enabled=false",
            "--firefly.eda.enabled=false",
            "--firefly.stepevents.enabled=false"));
    properties.forEach((key, value) -> args.add("--" + key + "=" + value));
    var builder =
        new ProcessBuilder(args)
            .redirectErrorStream(true)
            .redirectOutput(
                Path.of("target/governed-domain-runtime-" + audience + "-" + port + ".log")
                    .toFile());
    builder.environment().put("CONTRACT_FIXTURE_SIGNING_KEY", KEY);
    var process = builder.start();
    var http = java.net.http.HttpClient.newHttpClient();
    for (int i = 0; i < 160; i++) {
      if (!process.isAlive())
        throw new IllegalStateException(
            "Actual " + audience + " Boot exited; inspect private runtime log");
      try {
        int status =
            http.send(
                    java.net.http.HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + port + "/v3/api-docs"))
                        .timeout(Duration.ofSeconds(1))
                        .build(),
                    java.net.http.HttpResponse.BodyHandlers.discarding())
                .statusCode();
        if (status == 200 || audience.equals("domain") && (status == 403 || status == 401))
          return process;
      } catch (java.io.IOException ignored) {
      }
      Thread.sleep(250);
    }
    stop(process);
    throw new IllegalStateException("Private Boot startup timed out");
  }

  static void stop(Process process) throws Exception {
    if (process == null) return;
    process.destroy();
    if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
      process.destroyForcibly();
      if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
        throw new IllegalStateException("Private Boot would not exit");
    }
  }

  static String token(
      String audience,
      UUID tenant,
      Set<String> scopes,
      Set<String> authorities,
      List<String> delegated,
      Map<String, Object> extra) {
    try {
      var claims = new LinkedHashMap<String, Object>();
      claims.put("iss", "synthetic-document-authority");
      claims.put("aud", audience);
      claims.put("sub", "synthetic-governed-contract-producer");
      claims.put("exp", java.time.Instant.now().getEpochSecond() + 1800);
      claims.put("nbf", java.time.Instant.now().getEpochSecond() - 1);
      if (tenant != null) claims.put("tenant", tenant.toString());
      claims.put("scopes", scopes);
      claims.put("authorities", authorities);
      claims.put("delegatedTenants", delegated);
      claims.putAll(extra);
      var json = JsonMapper.builder().build();
      var encoder = Base64.getUrlEncoder().withoutPadding();
      String encoded =
          encoder.encodeToString(json.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")))
              + "."
              + encoder.encodeToString(json.writeValueAsBytes(claims));
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(Base64.getDecoder().decode(KEY), "HmacSHA256"));
      return encoded
          + "."
          + encoder.encodeToString(mac.doFinal(encoded.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }
}
