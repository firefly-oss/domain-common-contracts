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

import static io.fireflysoftware.qualification.contracts.ContractRuntimeSupport.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpServer;
import io.fireflysoftware.common.config.sdk.api.DocumentTemplateBindingsApi;
import io.fireflysoftware.common.config.sdk.model.DocumentTemplateBindingDTO;
import io.fireflysoftware.common.reference.master.data.sdk.api.*;
import io.fireflysoftware.common.reference.master.data.sdk.model.DocumentTemplateCatalogDTO;
import io.fireflysoftware.common.reference.master.data.sdk.model.DocumentTemplateLocalizationDTO;
import io.fireflysoftware.common.reference.master.data.sdk.model.DocumentTemplatePublicationRequest;
import io.fireflysoftware.common.reference.master.data.sdk.model.DocumentTemplateRevisionDTO;
import io.fireflysoftware.commons.ecm.sdk.api.*;
import io.fireflysoftware.core.contract.sdk.api.*;
import io.fireflysoftware.core.contract.sdk.model.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.core.publisher.Flux;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GovernedContractDocumentRuntimeIT {
  @Container
  static PostgreSQLContainer contractDb = postgres("synthetic_governed_contracts"),
      documentDb = postgres("synthetic_contract_documents"),
      configDb = postgres("synthetic_contract_configuration"),
      mastersDb = postgres("synthetic_contract_masters"),
      customerDb = postgres("synthetic_domain_customers"),
      productDb = postgres("synthetic_domain_products");
  static final Duration WAIT = Duration.ofSeconds(40);
  static final UUID TENANT = UUID.randomUUID(),
      CHANNEL = UUID.randomUUID(),
      BRAND = UUID.randomUUID();
  static final String SECRET = UUID.randomUUID().toString();
  static final JsonMapper JSON = JsonMapper.builder().build();
  static final Set<String> CONTRACT_SCOPES =
      Set.of(
          "contracts.read",
          "contracts.write",
          "contracts.prepare",
          "contracts.seal",
          "contracts.facts.read",
          "contracts.record-issuance",
          "contracts.attach",
          "contracts.ownership.bind",
          "contracts.admin");
  static final Set<String> CONTRACT_ROLES =
      Set.of("contracts.document.fact-producer", "contracts.document.ownership-authority");
  static final AtomicInteger exchanges = new AtomicInteger();
  static final Map<String, Path> peers = new LinkedHashMap<>();
  static HttpServer oauth;
  static Process contracts, documents, configuration, masters, customers, products, domain;
  static PrivateIssuer issuer;
  static OwnedPeerProxy contractProxy, documentProxy;
  static int domainPort, customerPort, productPort;
  static Map<String, String> domainProperties;
  static int contractPort, documentPort, configPort, mastersPort;
  static Map<String, String> contractProperties;
  static Fixture completed;

  record Fixture(
      UUID contract,
      UUID party,
      UUID role,
      UUID product,
      UUID version,
      UUID documentType,
      ContractDocumentPrepareRequest request) {}

  static String hash(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  static void pinPeers() throws Exception {
    String path = System.getProperty("contract.domain.qualifier.peer-manifest");
    assertThat(path).as("Explicit copied peer manifest required").isNotBlank();
    var root = JSON.readTree(Files.readString(Path.of(path)));
    for (var fields = root.fields(); fields.hasNext(); ) {
      var entry = fields.next();
      Path copy = Path.of(entry.getValue().path("copy").asText());
      assertThat(hash(Files.readAllBytes(copy)))
          .as(entry.getKey())
          .isEqualTo(entry.getValue().path("sha256").asText());
      peers.put(entry.getKey(), copy);
    }
    for (var entry :
        Map.of(
                ContractDocumentOperationsApi.class,
                "contract-sdk.jar",
                GovernedDocumentIssuanceApi.class,
                "document-sdk.jar",
                DocumentTemplateBindingsApi.class,
                "configuration-sdk.jar",
                DocumentTemplateRevisionsApi.class,
                "masters-sdk.jar")
            .entrySet()) {
      Path loaded =
          Path.of(entry.getKey().getProtectionDomain().getCodeSource().getLocation().toURI());
      assertThat(hash(Files.readAllBytes(loaded)))
          .as("Actual loaded SDK " + entry.getKey())
          .isEqualTo(hash(Files.readAllBytes(peers.get(entry.getValue()))));
    }
    try (var boot = new ZipFile(CONTRACT_JAR.toFile())) {
      var entry = boot.getEntry("BOOT-INF/lib/core-common-document-mgmt-sdk-26.10.6.jar");
      assertThat(entry).isNotNull();
      try (var input = boot.getInputStream(entry)) {
        assertThat(hash(input.readAllBytes()))
            .isEqualTo(hash(Files.readAllBytes(peers.get("document-sdk.jar"))));
      }
    }
  }

  @BeforeAll
  static void start() throws Exception {
    pinPeers();
    migrate(peers.get("contract-boot.jar"), "core-common-contract-mgmt-models-", contractDb);
    migrate(peers.get("customer-executable.jar"), "core-common-customer-mgmt-models-", customerDb);
    migrate(peers.get("product-executable.jar"), "core-common-product-mgmt-models-", productDb);
    migrate(peers.get("document-boot.jar"), "core-common-document-mgmt-models-", documentDb);
    migrate(peers.get("configuration-boot.jar"), "core-common-config-mgmt-models-", configDb);
    migrate(peers.get("masters-boot.jar"), "core-common-reference-master-data-models-", mastersDb);
    sql(
        configDb,
        "INSERT INTO tenants(id,code,name,legal_entity_name,tenant_status_id) SELECT ?,?,'Synthetic"
            + " Harbor Finance','Synthetic Harbor Finance LLC',id FROM tenant_statuses WHERE"
            + " code='ACTIVE'",
        TENANT,
        TENANT.toString());
    sql(
        configDb,
        "INSERT INTO channel_configs(id,tenant_id,channel_code,channel_name)"
            + " VALUES(?,?,'WEB','Synthetic contract web')",
        CHANNEL,
        TENANT);
    sql(
        configDb,
        "INSERT INTO"
            + " tenant_brandings(id,tenant_id,primary_color,secondary_color,accent_color,background_color,text_color,font_family)"
            + " VALUES(?,?,'#143D59','#52697A','#D89C36','#FFFFFF','#172A38','Georgia')",
        BRAND,
        TENANT);
    mastersPort = port();
    masters =
        boot(peers.get("masters-boot.jar"), mastersDb, mastersPort, "masters", true, Map.of());
    oauth = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    oauth.createContext(
        "/token",
        exchange -> {
          String auth = exchange.getRequestHeaders().getFirst("Authorization"),
              form =
                  URLDecoder.decode(
                      new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                      StandardCharsets.UTF_8),
              client = null;
          for (String candidate :
              List.of("config-master", "document-master", "document-config", "contract-document"))
            if (("Basic "
                    + Base64.getEncoder()
                        .encodeToString(
                            (candidate + ":" + SECRET).getBytes(StandardCharsets.UTF_8)))
                .equals(auth)) client = candidate;
          boolean doc = "contract-document".equals(client), cfg = "document-config".equals(client);
          String scopes =
              doc
                  ? "documents.read documents.content.read"
                  : cfg ? "bindings.resolve" : "templates.read templates.resolve";
          boolean valid =
              client != null
                  && form.contains("grant_type=client_credentials")
                  && form.contains("scope=" + scopes);
          String result =
              valid
                  ? "{\"access_token\":\""
                      + token(
                          doc ? "document" : cfg ? "config" : "masters",
                          null,
                          doc
                              ? Set.of("documents.read", "documents.content.read")
                              : cfg
                                  ? Set.of("bindings.resolve")
                                  : Set.of("templates.read", "templates.resolve"),
                          doc
                              ? Set.of("ecm.document.delegate")
                              : cfg ? Set.of("config.document-template.delegate") : Set.of(),
                          doc || cfg ? List.of(TENANT.toString()) : List.of(),
                          Map.of())
                      + "\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                  : "{}";
          if (valid) exchanges.incrementAndGet();
          byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(valid ? 200 : 401, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    oauth.start();
    configPort = port();
    configuration =
        boot(
            peers.get("configuration-boot.jar"),
            configDb,
            configPort,
            "config",
            true,
            endpoint(
                "firefly.document-bindings.masters",
                mastersPort,
                "config-master",
                "templates.read templates.resolve"));
    documentPort = port();
    var documentProperties =
        endpoint(
            "firefly.documents.governed.masters",
            mastersPort,
            "document-master",
            "templates.read templates.resolve");
    documentProperties.putAll(
        endpoint(
            "firefly.documents.governed.configuration",
            configPort,
            "document-config",
            "bindings.resolve"));
    documents =
        boot(
            peers.get("document-boot.jar"),
            documentDb,
            documentPort,
            "document",
            true,
            documentProperties);
    contractProperties =
        endpoint(
            "firefly.contract-documents.documents",
            documentPort,
            "contract-document",
            "documents.read documents.content.read");
    contractProperties.put(
        "firefly.contracts.documents.ownership.issuer", "synthetic-document-authority");
    contractProperties.put("firefly.contracts.documents.ownership.audience", "contracts");
    startContracts(true);
    customerPort = port();
    customers =
        boot(
            peers.get("customer-executable.jar"),
            customerDb,
            customerPort,
            "customers",
            true,
            Map.of());
    productPort = port();
    products =
        boot(
            peers.get("product-executable.jar"),
            productDb,
            productPort,
            "products",
            true,
            Map.of());
    sql(contractDb, "CREATE TABLE synthetic_attachment_fault(contract_id UUID PRIMARY KEY)");
    sql(
        contractDb,
        "CREATE FUNCTION synthetic_attachment_failure() RETURNS trigger LANGUAGE plpgsql AS $$"
            + " BEGIN IF EXISTS(SELECT 1 FROM synthetic_attachment_fault f WHERE"
            + " f.contract_id=NEW.contract_id) THEN RAISE EXCEPTION 'Synthetic attachment rollback'"
            + " USING ERRCODE='23514'; END IF; RETURN NEW; END $$");
    sql(
        contractDb,
        "CREATE TRIGGER synthetic_attachment_failure BEFORE INSERT ON contract_document FOR EACH"
            + " ROW EXECUTE FUNCTION synthetic_attachment_failure()");
    issuer = new PrivateIssuer();
    contractProxy = new OwnedPeerProxy(contractPort);
    documentProxy = new OwnedPeerProxy(documentPort);
    domainProperties = new LinkedHashMap<>();
    domainProperties.put("firefly.security.resource-server.strict-policy", "true");
    domainProperties.put("firefly.contracts.documents.security.issuer", PrivateIssuer.ISSUER);
    domainProperties.put("firefly.contracts.documents.security.audience", "domain-contracts");
    domainProperties.put("firefly.contracts.documents.security.jwk-set-uri", issuer.jwks());
    Map.of(
            "contracts",
            contractProxy.port(),
            "documents",
            documentProxy.port(),
            "customers",
            customerPort,
            "products",
            productPort,
            "masters",
            mastersPort)
        .forEach(
            (name, value) -> {
              domainProperties.put(
                  "firefly.contracts.documents.peers." + name + ".base-url",
                  "http://127.0.0.1:" + value);
              domainProperties.put(
                  "firefly.contracts.documents.peers." + name + ".allow-loopback-http", "true");
            });
    startDomain();
  }

  static Map<String, String> endpoint(String prefix, int port, String client, String scopes) {
    var p = new LinkedHashMap<String, String>();
    p.put(prefix + ".base-url", "http://127.0.0.1:" + port);
    p.put(prefix + ".token-uri", "http://127.0.0.1:" + oauth.getAddress().getPort() + "/token");
    p.put(prefix + ".client-id", client);
    p.put(prefix + ".client-secret", SECRET);
    p.put(prefix + ".scopes", scopes);
    p.put(prefix + ".allow-loopback-http", "true");
    return p;
  }

  static void startContracts(boolean trust) throws Exception {
    contractPort = port();
    contracts =
        boot(
            peers.get("contract-boot.jar"),
            contractDb,
            contractPort,
            "contracts",
            trust,
            contractProperties);
  }

  @AfterAll
  static void finish() throws Exception {
    for (Process p :
        new Process[] {domain, contracts, documents, configuration, masters, customers, products})
      stop(p);
    if (oauth != null) oauth.stop(0);
    if (issuer != null) issuer.close();
    if (contractProxy != null) contractProxy.close();
    if (documentProxy != null) documentProxy.close();
    if (!peers.isEmpty()) pinPeers();
  }

  static io.fireflysoftware.core.contract.sdk.invoker.ApiClient contractClient(String bearer) {
    var client =
        new io.fireflysoftware.core.contract.sdk.invoker.ApiClient(
                io.fireflysoftware.core.contract.sdk.invoker.ApiClient.buildWebClientBuilder()
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(2097152))
                    .build())
            .setBasePath("http://127.0.0.1:" + contractPort);
    if (bearer != null) client.addDefaultHeader("Authorization", "Bearer " + bearer);
    return client;
  }

  static String operator() {
    return token("contracts", TENANT, CONTRACT_SCOPES, CONTRACT_ROLES, List.of(), Map.of());
  }

  static ContractDocumentOperationsApi api() {
    return new ContractDocumentOperationsApi(contractClient(operator()));
  }

  static WebClient raw(String bearer) {
    var b = WebClient.builder().baseUrl("http://127.0.0.1:" + contractPort);
    if (bearer != null) b.defaultHeader("Authorization", "Bearer " + bearer);
    return b.build();
  }

  static io.fireflysoftware.common.reference.master.data.sdk.invoker.ApiClient masterClient() {
    var c =
        new io.fireflysoftware.common.reference.master.data.sdk.invoker.ApiClient()
            .setBasePath("http://127.0.0.1:" + mastersPort);
    c.addDefaultHeader(
        "Authorization",
        "Bearer "
            + token(
                "masters",
                null,
                Set.of("templates.admin", "templates.read", "templates.resolve"),
                Set.of(),
                List.of(),
                Map.of()));
    return c;
  }

  static io.fireflysoftware.common.config.sdk.invoker.ApiClient configClient() {
    var c =
        new io.fireflysoftware.common.config.sdk.invoker.ApiClient()
            .setBasePath("http://127.0.0.1:" + configPort);
    c.addDefaultHeader(
        "Authorization",
        "Bearer "
            + token(
                "config",
                TENANT,
                Set.of("bindings.admin", "bindings.read", "bindings.resolve"),
                Set.of(),
                List.of(),
                Map.of()));
    return c;
  }

  static io.fireflysoftware.commons.ecm.sdk.invoker.ApiClient documentClient() {
    var c =
        new io.fireflysoftware.commons.ecm.sdk.invoker.ApiClient(
                io.fireflysoftware.commons.ecm.sdk.invoker.ApiClient.buildWebClientBuilder()
                    .codecs(v -> v.defaultCodecs().maxInMemorySize(2097152))
                    .build())
            .setBasePath("http://127.0.0.1:" + documentPort);
    c.addDefaultHeader(
        "Authorization",
        "Bearer "
            + token(
                "document",
                TENANT,
                Set.of(
                    "documents.issue",
                    "documents.read",
                    "documents.content.read",
                    "documents.retention"),
                Set.of("ecm.document.fact-producer"),
                List.of(),
                Map.of()));
    return c;
  }

  static void status(int expected, Runnable work) {
    assertThatThrownBy(work::run)
        .isInstanceOfSatisfying(
            WebClientResponseException.class,
            e -> {
              assertThat(e.getStatusCode().value()).isEqualTo(expected);
              assertThat(e.getResponseBodyAsString())
                  .doesNotContain(
                      "sealedFactsJson", "coreSnapshotCanonicalJson", "synthetic-sensitive");
            });
  }

  static final String SCHEMA =
      """
      {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","properties":{"contract":{"type":"object","required":["contractId","contractNumber","sourceRevision"]},"parties":{"type":"array","minItems":1},"products":{"type":"array","minItems":1},"lines":{"type":"array","minItems":1},"terms":{"type":"array","minItems":1},"businessAtEpochMillis":{"type":"integer"},"sourceSnapshotDigest":{"type":"string"},"documentType":{"type":"object"}},"required":["contract","parties","products","lines","terms","businessAtEpochMillis","sourceSnapshotDigest","documentType"],"additionalProperties":false}
      """;
  static final String TEMPLATE =
      """
      <h1>${issuance.displayName}</h1><p>Synthetic contract evidence; no financial calculation or execution.</p><h2>${facts.contract.contractNumber}</h2><p>Currency: ${facts.contract.currencyCode}</p><#list facts.parties as party><p>${party.roleName}: ${party.displayName}</p></#list><#list facts.products as product><p>Product: ${product.productName}, version ${product.versionNumber}</p></#list><#list facts.terms as term><h3>${term.name}</h3><p>${term.jsonValueJson}</p></#list><p>Source revision ${facts.contract.sourceRevision}</p>
      """;

  static DocumentTemplateRevisionDTO publish() throws Exception {
    UUID locale = UUID.randomUUID();
    String code = "en-" + locale.toString().substring(0, 7);
    sql(
        mastersDb,
        "INSERT INTO language_locale(locale_id,language_code,locale_code,language_name,status)"
            + " VALUES(?,'en',?,'Synthetic contract English','ACTIVE')",
        locale,
        code);
    UUID type;
    try (var c =
            java.sql.DriverManager.getConnection(
                mastersDb.getJdbcUrl(), mastersDb.getUsername(), mastersDb.getPassword());
        var q = c.createStatement();
        var result =
            q.executeQuery(
                "SELECT type_id FROM document_template_type_catalog WHERE type_code='HTML'")) {
      assertThat(result.next()).isTrue();
      type = result.getObject(1, UUID.class);
    }
    var draft =
        new DocumentTemplatesApi(masterClient())
            .createDocumentTemplate(
                new DocumentTemplateCatalogDTO()
                    .templateCode("contract-" + UUID.randomUUID())
                    .typeId(type)
                    .category("SYNTHETIC")
                    .templateName("Synthetic governed contract")
                    .templateContent(TEMPLATE)
                    .mediaType("text/html")
                    .styleContent("")
                    .version("1")
                    .status(DocumentTemplateCatalogDTO.StatusEnum.ACTIVE)
                    .inputSchema(null)
                    .inputSchemaJson(SCHEMA),
                null)
            .block(WAIT);
    new DocumentTemplateLocalizationsApi(masterClient())
        .createDocumentTemplateLocalization(
            new DocumentTemplateLocalizationDTO()
                .templateId(draft.getTemplateId())
                .localeId(locale)
                .templateName("Synthetic contract evidence")
                .templateContent(TEMPLATE)
                .status(DocumentTemplateLocalizationDTO.StatusEnum.ACTIVE),
            null)
        .block(WAIT);
    return new DocumentTemplateRevisionsApi(masterClient())
        .publishDocumentTemplateRevision(
            draft.getTemplateId(),
            new DocumentTemplatePublicationRequest().version("1").localeCode(code),
            null)
        .block(WAIT);
  }

  static DocumentTemplateBindingDTO activate(DocumentTemplateRevisionDTO revision) {
    var ref =
        new io.fireflysoftware.common.config.sdk.model.DocumentTemplateMasterReference()
            .revisionId(revision.getRevisionId().toString())
            .templateId(revision.getTemplateId().toString())
            .templateCode(revision.getTemplateCode())
            .templateVersion(revision.getVersion())
            .localeCode(revision.getLocaleCode())
            .mediaType(revision.getMediaType())
            .canonicalizationVersion(revision.getCanonicalizationVersion())
            .sourceDigest(revision.getSourceDigest())
            .contentDigest(revision.getContentDigest())
            .schemaDigest(revision.getSchemaDigest())
            .styleDigest(revision.getStyleDigest());
    var definition =
        new io.fireflysoftware.common.config.sdk.model.DocumentTemplateBindingDefinition()
            .documentPurpose("CONTRACT_DOCUMENT")
            .productScope(
                io.fireflysoftware.common.config.sdk.model.DocumentTemplateBindingDefinition
                    .ProductScopeEnum.NONE)
            .channelConfigId(CHANNEL.toString())
            .channelCode("WEB")
            .jurisdiction("US")
            .localeCode(revision.getLocaleCode())
            .effectiveFromEpochMillis(0L)
            .masterReference(ref)
            .branding(
                new io.fireflysoftware.common.config.sdk.model.DocumentBindingBrandReference()
                    .scope(
                        io.fireflysoftware.common.config.sdk.model.DocumentBindingBrandReference
                            .ScopeEnum.EXACT)
                    .tenantBrandingId(BRAND.toString())
                    .version(0L));
    var a = new DocumentTemplateBindingsApi(configClient());
    var binding =
        a.createDocumentTemplateBinding(
                TENANT.toString(),
                new io.fireflysoftware.common.config.sdk.model
                        .DocumentTemplateBindingCreateRequest()
                    .actionId(UUID.randomUUID().toString())
                    .definition(definition),
                null)
            .block(WAIT);
    return a.activateDocumentTemplateBinding(
            TENANT.toString(),
            binding.getBindingId().toString(),
            new io.fireflysoftware.common.config.sdk.model.DocumentTemplateBindingActionRequest()
                .actionId(UUID.randomUUID().toString()),
            null)
        .block(WAIT);
  }

  static ContractDocumentSourceObservation observed(String system, String type, UUID id) {
    return new ContractDocumentSourceObservation()
        .system(system)
        .resourceType(type)
        .resourceId(id.toString())
        .sourceVersion("synthetic-authoritative-1")
        .observedAtEpochMillis(System.currentTimeMillis());
  }

  static Fixture fixture() throws Exception {
    return fixture(false);
  }

  static Fixture fixture(boolean unsupported) throws Exception {
    var revision = publish();
    var binding = activate(revision);
    UUID contract = UUID.randomUUID(),
        party = UUID.randomUUID(),
        role = UUID.randomUUID(),
        product = UUID.randomUUID(),
        version = UUID.randomUUID(),
        type = UUID.randomUUID(),
        term = UUID.randomUUID();
    sql(
        mastersDb,
        "INSERT INTO contract_role(role_id,role_code,name) VALUES(?,'BORROWER','Borrower')",
        role);
    sql(
        mastersDb,
        "INSERT INTO contract_document_type(document_type_id,document_code,name)"
            + " VALUES(?,?,'Synthetic contract record')",
        type,
        "SYNTHETIC_" + type.toString().substring(0, 8));
    var actualRole = new ContractRoleApi(masterClient()).getContractRole(role, null).block(WAIT);
    var actualType =
        new ContractDocumentTypeApi(masterClient()).getContractDocumentType(type, null).block(WAIT);
    assertThat(actualRole.getRoleId()).isEqualTo(role);
    assertThat(actualType.getDocumentTypeId()).isEqualTo(type);
    sql(
        contractDb,
        "INSERT INTO contract(contract_id,contract_number,contract_status,product_id,currency_code)"
            + " VALUES(?,?,'ACTIVE',?,'USD')",
        contract,
        "SYNTHETIC-" + contract,
        product);
    sql(
        contractDb,
        "INSERT INTO contract_party(contract_id,party_id,role_in_contract_id) VALUES(?,?,?)",
        contract,
        party,
        role);
    sql(
        contractDb,
        "INSERT INTO contract_line(contract_id,product_id,quantity,is_primary) VALUES(?,?,1,true)",
        contract,
        product);
    sql(
        contractDb,
        "INSERT INTO"
            + " contract_term_template(term_template_id,code,name,term_category,data_type,is_required)"
            + " VALUES(?,?,'Exact synthetic JSON terms','FINANCIAL','JSON',true)",
        term,
        "EXACT_" + term.toString().replace("-", ""));
    sql(
        contractDb,
        "INSERT INTO"
            + " contract_term_dynamic(contract_id,term_template_id,term_value_json,effective_date)"
            + " VALUES(?,?,CAST(? AS jsonb),'2020-01-01T00:00:00')",
        contract,
        term,
        "{\"amount\":0.123456789012345678,\"large\":9007199254740993}");
    if (unsupported)
      sql(
          contractDb,
          "INSERT INTO"
              + " contract_term_validation_rule(term_template_id,validation_type,validation_value)"
              + " VALUES(?,'CUSTOM_FUNCTION',CAST(? AS jsonb))",
          term,
          "{\"function\":\"synthetic-unsupported\"}");
    long source =
        count(
            contractDb,
            "SELECT document_source_revision FROM contract WHERE contract_id=?",
            contract);
    UUID action = UUID.randomUUID();
    var ownership =
        Map.<String, Object>of(
            "contractId",
            contract.toString(),
            "tenantId",
            TENANT.toString(),
            "expectedContractRevision",
            source,
            "actionId",
            action.toString(),
            "attestationId",
            "synthetic-attestation-" + action,
            "expiresAtEpochMillis",
            System.currentTimeMillis() + 300000,
            "timestampInterpretation",
            "UTC");
    String exact =
        token(
            "contracts",
            TENANT,
            CONTRACT_SCOPES,
            CONTRACT_ROLES,
            List.of(),
            Map.of("contractDocumentOwnership", ownership));
    var owner =
        new ContractDocumentOperationsApi(contractClient(exact))
            .bindContractDocumentOwner(
                TENANT,
                contract,
                new ContractDocumentOwnerBindingRequest()
                    .actionId(action.toString())
                    .expectedContractRevision(source)
                    .legacyTimestampInterpretation("UTC"))
            .block(WAIT);
    assertThat(owner.getTenantId()).isEqualTo(TENANT.toString());
    var request =
        new ContractDocumentPrepareRequest()
            .actionId(UUID.randomUUID().toString())
            .expectedContractRevision(source)
            .selection(
                new ContractDocumentSelection()
                    .documentPurpose("CONTRACT_DOCUMENT")
                    .productScope(ContractDocumentSelection.ProductScopeEnum.NONE)
                    .channelCode("WEB")
                    .jurisdiction("US")
                    .localeCode(revision.getLocaleCode())
                    .businessAtEpochMillis(System.currentTimeMillis() - 1000))
            .expected(
                new ContractDocumentPins()
                    .bindingId(binding.getBindingId().toString())
                    .bindingStateVersion(binding.getStateVersion())
                    .revisionId(revision.getRevisionId().toString())
                    .sourceDigest(revision.getSourceDigest()))
            .contractDocumentTypeId(type.toString())
            .productVersionReferences(
                List.of(
                    new ContractProductVersionReference()
                        .productId(product.toString())
                        .productVersionId(version.toString())))
            .retention(
                new ContractDocumentRetention()
                    .policyCode("CONTRACT_EVIDENCE")
                    .policyVersion("1")
                    .retainUntilEpochMillis(System.currentTimeMillis() + 86400000))
            .displayName("Synthetic governed contract evidence");
    sql(
        customerDb,
        "INSERT INTO party(party_id,tenant_id,party_kind) VALUES(?,?, 'INDIVIDUAL')",
        party,
        TENANT);
    sql(
        customerDb,
        "INSERT INTO natural_person(party_id,given_name,family_name,tax_id_number)"
            + " VALUES(?,?,?,'synthetic-sensitive-tax')",
        party,
        "Morgan é漢😀",
        "Example <script>private</script>");
    UUID category = UUID.randomUUID();
    sql(
        productDb,
        "INSERT INTO product_category(product_category_id,category_name) VALUES(?,'Synthetic"
            + " category')",
        category);
    sql(
        productDb,
        "INSERT INTO"
            + " product(product_id,tenant_id,product_category_id,product_type,product_name,product_code,product_status)"
            + " VALUES(?,?,?,'FINANCIAL','Synthetic lending product','SYNTHETIC_PRODUCT','ACTIVE')",
        product,
        TENANT,
        category);
    sql(
        productDb,
        "INSERT INTO product_version(product_version_id,product_id,version_number,effective_date)"
            + " VALUES(?,?,1,'2020-01-01T00:00:00')",
        version,
        product);
    return new Fixture(contract, party, role, product, version, type, request);
  }

  static void startDomain() throws Exception {
    domainPort = port();
    domain = boot(CONTRACT_JAR, contractDb, domainPort, "domain", true, domainProperties);
  }

  static io.fireflysoftware.domain.common.contracts.sdk.api.GovernedContractDocumentsApi domainApi(
      UUID contract) {
    return domainApi(
        contract,
        Set.of(
            "contracts.document.issue",
            "contracts.document.resume",
            "contracts.document.read",
            "contracts.document.collect-facts",
            "contracts.document.content.read",
            "fixture.admin"));
  }

  static io.fireflysoftware.domain.common.contracts.sdk.api.GovernedContractDocumentsApi domainApi(
      UUID contract, Set<String> scopes) {
    var c =
        new io.fireflysoftware.domain.common.contracts.sdk.invoker.ApiClient(
                io.fireflysoftware.domain.common.contracts.sdk.invoker.ApiClient
                    .buildWebClientBuilder()
                    .codecs(codec -> codec.defaultCodecs().maxInMemorySize(2097152))
                    .build())
            .setBasePath("http://127.0.0.1:" + domainPort);
    c.addDefaultHeader(
        "Authorization", "Bearer " + issuer.token(TENANT, List.of(contract), scopes));
    return new io.fireflysoftware.domain.common.contracts.sdk.api.GovernedContractDocumentsApi(c);
  }

  static io.fireflysoftware.domain.common.contracts.sdk.model.GovernedContractDocumentIntent intent(
      Fixture f) throws Exception {
    return JSON.readValue(
        JSON.writeValueAsBytes(f.request()),
        io.fireflysoftware.domain.common.contracts.sdk.model.GovernedContractDocumentIntent.class);
  }

  @Test
  @Order(1)
  void genuineDomainSdkUsesSevenBootOwnersAndRetainsExactImmutableEvidence() throws Exception {
    var f = fixture();
    completed = f;
    UUID action = UUID.fromString(f.request().getActionId());
    var a = domainApi(f.contract());
    var attached = a.issueGovernedContractDocument(TENANT, f.contract(), intent(f)).block(WAIT);
    assertThat(attached.getPhase().toString()).isEqualTo("ATTACHED");
    assertThat(attached.getAttachmentId()).isNotBlank();
    assertThat(attached.getIssued().getContentDigest()).matches("[0-9a-f]{64}");
    assertThat(a.issueGovernedContractDocument(TENANT, f.contract(), intent(f)).block(WAIT))
        .isEqualTo(attached);
    assertThat(a.getGovernedContractDocument(TENANT, f.contract(), action).block(WAIT))
        .isEqualTo(attached);
    byte[] bytes =
        a.getGovernedContractDocumentContentWithResponseSpec(TENANT, f.contract(), action)
            .bodyToMono(byte[].class)
            .block(WAIT);
    assertThat(hash(bytes)).isEqualTo(attached.getIssued().getContentDigest());
    assertThat(new String(bytes, StandardCharsets.UTF_8))
        .contains(
            "0.123456789012345678",
            "9007199254740993",
            "Morgan é漢😀",
            "&lt;script&gt;private&lt;/script&gt;",
            "Synthetic lending product")
        .doesNotContain("synthetic-sensitive-tax", "<script>");
    assertThat(
            count(
                contractDb,
                "SELECT count(*) FROM contract_document_operations WHERE contract_id=?",
                f.contract()))
        .isEqualTo(1);
    assertThat(
            count(
                contractDb,
                "SELECT count(*) FROM contract_document WHERE contract_id=? AND"
                    + " managed_operation_id IS NOT NULL",
                f.contract()))
        .isEqualTo(1);
    assertThat(count(documentDb, "SELECT count(*) FROM document_issuances")).isEqualTo(1);
    assertThat(
            count(
                documentDb,
                "SELECT count(*) FROM document_issuances WHERE issuance_id=? AND"
                    + " retention_policy_code='CONTRACT_EVIDENCE' AND retention_policy_version='1'"
                    + " AND retain_until_epoch_millis=? AND"
                    + " issued_by='synthetic-governed-contract-producer'",
                UUID.fromString(attached.getIssued().getIssuanceId()),
                f.request().getRetention().getRetainUntilEpochMillis()))
        .isEqualTo(1);
    assertThat(attached.getCreatedBy()).isEqualTo("synthetic-governed-contract-producer");
    Path result = Path.of("target/governed-domain-actual-content.html");
    Files.write(result, bytes);
    Files.writeString(
        Path.of("target/governed-domain-actual-operation.json"), JSON.writeValueAsString(attached));
  }

  static Map<String, Integer> policy(UUID contract, boolean deny) {
    return WebClient.builder()
        .baseUrl("http://127.0.0.1:" + domainPort)
        .defaultHeader(
            "Authorization",
            "Bearer " + issuer.token(TENANT, List.of(contract), Set.of("fixture.admin")))
        .build()
        .post()
        .uri(
            "/api/v1/tenants/"
                + TENANT
                + "/contracts/"
                + contract
                + "/governed-documents/synthetic-policy?deny="
                + deny)
        .retrieve()
        .bodyToMono(
            new org.springframework.core.ParameterizedTypeReference<Map<String, Integer>>() {})
        .block(WAIT);
  }

  @Test
  @Order(2)
  void currentAuthorityWithdrawalCannotReplayReadResumeOrDownloadWarmEvidence() throws Exception {
    var f = completed == null ? fixture() : completed;
    UUID action = UUID.fromString(f.request().getActionId());
    var a = domainApi(f.contract());
    Map<String, Integer> before = policy(f.contract(), true);
    try {
      status(
          403,
          () ->
              a.issueGovernedContractDocument(TENANT, f.contract(), uncheckedIntent(f))
                  .block(WAIT));
      status(403, () -> a.resumeGovernedContractDocument(TENANT, f.contract(), action).block(WAIT));
      status(403, () -> a.getGovernedContractDocument(TENANT, f.contract(), action).block(WAIT));
      status(
          403,
          () ->
              a.getGovernedContractDocumentContentWithResponseSpec(TENANT, f.contract(), action)
                  .bodyToMono(byte[].class)
                  .block(WAIT));
      assertThat(policy(f.contract(), true)).isEqualTo(before);
    } finally {
      policy(f.contract(), false);
    }
    status(
        403,
        () ->
            domainApi(f.contract(), Set.of())
                .getGovernedContractDocument(TENANT, f.contract(), action)
                .block(WAIT));
    status(
        403,
        () -> a.getGovernedContractDocument(UUID.randomUUID(), f.contract(), action).block(WAIT));
    var changed = intent(f).displayName("A different retained intent");
    status(409, () -> a.issueGovernedContractDocument(TENANT, f.contract(), changed).block(WAIT));
    assertThat(
            count(
                contractDb,
                "SELECT count(*) FROM contract_document_operations WHERE contract_id=?",
                f.contract()))
        .isEqualTo(1);
  }

  static io.fireflysoftware.domain.common.contracts.sdk.model.GovernedContractDocumentIntent
      uncheckedIntent(Fixture f) {
    try {
      return intent(f);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  @Order(3)
  void unsupportedCapturedRulesRetainOnlyReservedAndNeverReadForeignSourcesOrIssue()
      throws Exception {
    var f = fixture(true);
    var a = domainApi(f.contract());
    UUID action = UUID.fromString(f.request().getActionId());
    Map<String, Integer> before = policy(f.contract(), false);
    long documentsBefore = count(documentDb, "SELECT count(*) FROM document_issuances");
    status(
        422,
        () ->
            a.issueGovernedContractDocument(TENANT, f.contract(), uncheckedIntent(f)).block(WAIT));
    assertThat(
            api()
                .getContractDocumentOperation(TENANT, f.contract(), action, null)
                .block(WAIT)
                .getPhase())
        .isEqualTo(ContractDocumentOperation.PhaseEnum.RESERVED);
    var after = policy(f.contract(), false);
    for (String peer : List.of("CUSTOMERS", "PRODUCTS", "MASTERS", "DOCUMENTS"))
      assertThat(after.getOrDefault(peer, 0)).isEqualTo(before.getOrDefault(peer, 0));
    status(422, () -> a.resumeGovernedContractDocument(TENANT, f.contract(), action).block(WAIT));
    assertThat(
            count(
                contractDb,
                "SELECT count(*) FROM contract_document_operations WHERE contract_id=? AND"
                    + " phase='RESERVED' AND sealed_request_json IS NULL AND issuance_id IS NULL"
                    + " AND attachment_id IS NULL",
                f.contract()))
        .isEqualTo(1);
    assertThat(count(documentDb, "SELECT count(*) FROM document_issuances"))
        .isEqualTo(documentsBefore);
    policy(f.contract(), true);
    try {
      status(403, () -> a.resumeGovernedContractDocument(TENANT, f.contract(), action).block(WAIT));
    } finally {
      policy(f.contract(), false);
    }
  }

  @Test
  @Order(4)
  void
      lostSuccessfulIssueRecordAndAttachRepliesRecoverAfterDomainRestartWithoutNewSourcesOrDeletes()
          throws Exception {
    for (String phase : List.of("issue", "record", "attach")) {
      var f = fixture();
      UUID action = UUID.fromString(f.request().getActionId());
      var a = domainApi(f.contract());
      var proxy = phase.equals("issue") ? documentProxy : contractProxy;
      proxy.loseSuccessfulSuffix.set(
          phase.equals("issue") ? "" : phase.equals("record") ? "/issuance" : "/attachment");
      int oldLost = proxy.lost.get();
      long oldDocuments = count(documentDb, "SELECT count(*) FROM document_issuances");
      status(
          503,
          () ->
              a.issueGovernedContractDocument(TENANT, f.contract(), uncheckedIntent(f))
                  .block(WAIT));
      assertThat(proxy.lost).hasValue(oldLost + 1);
      var retained =
          api().getContractDocumentOperation(TENANT, f.contract(), action, null).block(WAIT);
      assertThat(retained.getPhase().toString())
          .isEqualTo(
              phase.equals("issue")
                  ? "READY"
                  : phase.equals("record") ? "ISSUED_PENDING_ATTACHMENT" : "ATTACHED");
      assertThat(count(documentDb, "SELECT count(*) FROM document_issuances"))
          .isEqualTo(oldDocuments + 1);
      stop(domain);
      startDomain();
      var before = policy(f.contract(), false);
      var attached =
          domainApi(f.contract())
              .resumeGovernedContractDocument(TENANT, f.contract(), action)
              .block(WAIT);
      assertThat(attached.getPhase().toString()).isEqualTo("ATTACHED");
      var after = policy(f.contract(), false);
      for (String peer : List.of("CUSTOMERS", "PRODUCTS", "MASTERS"))
        assertThat(after.getOrDefault(peer, 0)).isEqualTo(before.getOrDefault(peer, 0));
      assertThat(count(documentDb, "SELECT count(*) FROM document_issuances"))
          .isEqualTo(oldDocuments + 1);
      assertThat(
              count(
                  contractDb,
                  "SELECT count(*) FROM contract_document WHERE contract_id=? AND"
                      + " managed_operation_id IS NOT NULL",
                  f.contract()))
          .isEqualTo(1);
      assertThat(contractProxy.deletes).hasValue(0);
      assertThat(documentProxy.deletes).hasValue(0);
    }
  }

  @Test
  @Order(5)
  void authoritativeWrongTenantCustomerAndProductSourcesCannotSealOrIssue() throws Exception {
    for (String family : List.of("CUSTOMERS", "PRODUCTS")) {
      var f = fixture();
      UUID action = UUID.fromString(f.request().getActionId());
      long before = count(documentDb, "SELECT count(*) FROM document_issuances");
      if (family.equals("CUSTOMERS"))
        sql(
            customerDb,
            "UPDATE party SET tenant_id=? WHERE party_id=?",
            UUID.randomUUID(),
            f.party());
      else
        sql(
            productDb,
            "UPDATE product SET tenant_id=? WHERE product_id=?",
            UUID.randomUUID(),
            f.product());
      status(
          502,
          () ->
              domainApi(f.contract())
                  .issueGovernedContractDocument(TENANT, f.contract(), uncheckedIntent(f))
                  .block(WAIT));
      assertThat(
              api()
                  .getContractDocumentOperation(TENANT, f.contract(), action, null)
                  .block(WAIT)
                  .getPhase())
          .isEqualTo(ContractDocumentOperation.PhaseEnum.RESERVED);
      assertThat(
              count(
                  contractDb,
                  "SELECT count(*) FROM contract_document WHERE contract_id=?",
                  f.contract()))
          .isZero();
      assertThat(count(documentDb, "SELECT count(*) FROM document_issuances")).isEqualTo(before);
    }
  }

  @Test
  @Order(6)
  void nativeAttachmentRollbackRetainsIssuedEvidenceAndResumesWithoutCompensation()
      throws Exception {
    var f = fixture();
    UUID action = UUID.fromString(f.request().getActionId());
    long before = count(documentDb, "SELECT count(*) FROM document_issuances");
    sql(contractDb, "INSERT INTO synthetic_attachment_fault(contract_id) VALUES(?)", f.contract());
    status(
        409,
        () ->
            domainApi(f.contract())
                .issueGovernedContractDocument(TENANT, f.contract(), uncheckedIntent(f))
                .block(WAIT));
    var pending =
        api().getContractDocumentOperation(TENANT, f.contract(), action, null).block(WAIT);
    assertThat(pending.getPhase())
        .isEqualTo(ContractDocumentOperation.PhaseEnum.ISSUED_PENDING_ATTACHMENT);
    assertThat(count(documentDb, "SELECT count(*) FROM document_issuances")).isEqualTo(before + 1);
    assertThat(
            count(
                contractDb,
                "SELECT count(*) FROM contract_document WHERE contract_id=?",
                f.contract()))
        .isZero();
    sql(contractDb, "DELETE FROM synthetic_attachment_fault WHERE contract_id=?", f.contract());
    var attached =
        domainApi(f.contract())
            .resumeGovernedContractDocument(TENANT, f.contract(), action)
            .block(WAIT);
    assertThat(attached.getPhase().toString()).isEqualTo("ATTACHED");
    assertThat(attached.getIssued().getIssuanceId()).isEqualTo(pending.getIssued().getIssuanceId());
    assertThat(count(documentDb, "SELECT count(*) FROM document_issuances")).isEqualTo(before + 1);
    assertThat(contractProxy.deletes).hasValue(0);
    assertThat(documentProxy.deletes).hasValue(0);
  }

  @Test
  @Order(7)
  void concurrentSameIntentKeepsOneNativeJournalIssuanceAndAttachment() throws Exception {
    var f = fixture();
    UUID action = UUID.fromString(f.request().getActionId());
    long before = count(documentDb, "SELECT count(*) FROM document_issuances");
    var a = domainApi(f.contract());
    var statuses =
        Flux.range(0, 2)
            .flatMap(
                i ->
                    a.issueGovernedContractDocument(TENANT, f.contract(), uncheckedIntent(f))
                        .map(value -> 200)
                        .onErrorResume(
                            WebClientResponseException.class,
                            error ->
                                reactor.core.publisher.Mono.just(error.getStatusCode().value())),
                2)
            .collectList()
            .block(WAIT);
    assertThat(statuses).contains(200).allMatch(code -> code == 200 || code == 409);
    assertThat(
            a.resumeGovernedContractDocument(TENANT, f.contract(), action)
                .block(WAIT)
                .getPhase()
                .toString())
        .isEqualTo("ATTACHED");
    assertThat(
            count(
                contractDb,
                "SELECT count(*) FROM contract_document_operations WHERE contract_id=?",
                f.contract()))
        .isEqualTo(1);
    assertThat(
            count(
                contractDb,
                "SELECT count(*) FROM contract_document WHERE contract_id=? AND"
                    + " managed_operation_id IS NOT NULL",
                f.contract()))
        .isEqualTo(1);
    assertThat(count(documentDb, "SELECT count(*) FROM document_issuances")).isEqualTo(before + 1);
  }

  @Test
  @Order(8)
  void ordinaryActualBootWithoutProjectPolicyDeniesBeforeCredentialOrJournalRead()
      throws Exception {
    stop(domain);
    domainPort = port();
    domain = boot(CONTRACT_JAR, contractDb, domainPort, "domain", false, domainProperties);
    try {
      var f = completed == null ? fixture() : completed;
      UUID action = UUID.fromString(f.request().getActionId());
      status(
          403,
          () ->
              domainApi(f.contract())
                  .getGovernedContractDocument(TENANT, f.contract(), action)
                  .block(WAIT));
    } finally {
      stop(domain);
      startDomain();
    }
  }

  @Test
  @Order(9)
  void ordinaryActualBootWithoutTrustRejectsSignedTokenAndDeniesUnmatchedLegacyAndDiscovery()
      throws Exception {
    stop(domain);
    var missingTrust = new LinkedHashMap<>(domainProperties);
    for (String key : List.of("issuer", "audience", "jwk-set-uri"))
      missingTrust.put("firefly.contracts.documents.security." + key, "");
    domainPort = port();
    domain = boot(CONTRACT_JAR, contractDb, domainPort, "domain", false, missingTrust);
    try {
      var f = completed == null ? fixture() : completed;
      UUID action = UUID.fromString(f.request().getActionId());
      status(
          401,
          () ->
              domainApi(f.contract())
                  .getGovernedContractDocument(TENANT, f.contract(), action)
                  .block(WAIT));
      for (String route :
          List.of("/v3/api-docs", "/api/v1/contracts/" + f.contract() + "/documents")) {
        var response =
            java.net.http.HttpClient.newHttpClient()
                .send(
                    java.net.http.HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + domainPort + route))
                        .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).doesNotContain("sealedFactsJson", "synthetic-sensitive");
      }
    } finally {
      stop(domain);
      startDomain();
    }
  }

  @Test
  @Order(10)
  void actualRuntimeOpenApiKeepsFourGovernedRoutesAndExplicitParameters() throws Exception {
    var f = completed == null ? fixture() : completed;
    var response =
        java.net.http.HttpClient.newHttpClient()
            .send(
                java.net.http.HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + domainPort + "/v3/api-docs"))
                    .header(
                        "Authorization",
                        "Bearer "
                            + issuer.token(
                                TENANT, List.of(f.contract()), Set.of("fixture.openapi.read")))
                    .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    var spec = JSON.readTree(response.body());
    int governed = 0;
    for (var entries = spec.path("paths").fields(); entries.hasNext(); ) {
      var path = entries.next();
      if (!path.getKey().contains("governed-documents")) continue;
      governed++;
      path.getValue()
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
    assertThat(governed).isEqualTo(4);
    assertThat(spec.path("paths").size()).isEqualTo(20);
    assertThat(
            spec.path("paths")
                .path("/api/v1/contracts/{contractId}/documents/generate")
                .path("post")
                .path("responses")
                .path("200")
                .path("content")
                .path("*/*")
                .path("schema")
                .path("type")
                .asText())
        .as("Historical legacy200 schema remains object in actual runtime discovery")
        .isEqualTo("object");
    assertThat(
            spec.path("components")
                .path("schemas")
                .path("GovernedContractDocumentIntent")
                .path("properties")
                .has("factsJson"))
        .isFalse();
    Files.writeString(Path.of("target/governed-domain-runtime-openapi.json"), response.body());
  }

  @Test
  @Order(11)
  void policyWithdrawalAfterActualSnapshotReadStopsBeforeSourceCollectionAndSeal()
      throws Exception {
    var f = fixture();
    UUID action = UUID.fromString(f.request().getActionId());
    var before = policy(f.contract(), false);
    long documentsBefore = count(documentDb, "SELECT count(*) FROM document_issuances");
    var pause = new OwnedPeerProxy.Pause("/facts");
    contractProxy.pauseSuccessfulReply.set(pause);
    var result =
        domainApi(f.contract())
            .issueGovernedContractDocument(TENANT, f.contract(), intent(f))
            .map(value -> 200)
            .onErrorResume(
                WebClientResponseException.class,
                error -> reactor.core.publisher.Mono.just(error.getStatusCode().value()))
            .toFuture();
    try {
      assertThat(pause.observed.await(3, java.util.concurrent.TimeUnit.SECONDS))
          .as("Actual peer facts response reached private barrier")
          .isTrue();
      policy(f.contract(), true);
      pause.release.countDown();
      assertThat(result.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(403);
      var after = policy(f.contract(), true);
      for (String peer : List.of("CUSTOMERS", "PRODUCTS", "MASTERS", "DOCUMENTS"))
        assertThat(after.getOrDefault(peer, 0)).isEqualTo(before.getOrDefault(peer, 0));
      assertThat(
              api()
                  .getContractDocumentOperation(TENANT, f.contract(), action, null)
                  .block(WAIT)
                  .getPhase())
          .isEqualTo(ContractDocumentOperation.PhaseEnum.RESERVED);
      assertThat(count(documentDb, "SELECT count(*) FROM document_issuances"))
          .isEqualTo(documentsBefore);
    } finally {
      pause.release.countDown();
      policy(f.contract(), false);
    }
    assertThat(
            domainApi(f.contract())
                .resumeGovernedContractDocument(TENANT, f.contract(), action)
                .block(WAIT)
                .getPhase()
                .toString())
        .isEqualTo("ATTACHED");
  }
}
