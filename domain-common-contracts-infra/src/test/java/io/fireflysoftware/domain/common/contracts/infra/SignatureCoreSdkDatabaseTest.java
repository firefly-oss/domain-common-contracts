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

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import io.fireflysoftware.commons.ecm.sdk.api.SignatureSubmissionApi;
import io.fireflysoftware.commons.ecm.sdk.api.SandboxSignatureOutcomeApi;
import io.fireflysoftware.commons.ecm.sdk.invoker.ApiClient;
import io.fireflysoftware.domain.common.contracts.infra.ports.SignatureRequestContextPort;
import io.fireflysoftware.domain.common.contracts.infra.ports.model.SignatureRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.core.publisher.Mono;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@EnabledIfSystemProperty(named="signature.core.executable",matches=".+")
class SignatureCoreSdkDatabaseTest {
    @Container static final PostgreSQLContainer DATABASE=new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("signature_sdk_qualification").withUsername("signature_sdk_test")
            .withPassword("synthetic-disposable-test-password")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1",0),ExposedPort.tcp(5432))));
    static final UUID DOCUMENT=UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID CONTRACT=UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID SIGNER=UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID PROVIDER=UUID.fromString("44444444-4444-4444-4444-444444444444");
    static final Duration TIMEOUT=Duration.ofSeconds(20);

    @Test void actualGeneratedSdkAndDomainAdapterRetainPendingAndCompletionAcrossCoreProcessRestart() throws Exception {
        UUID id;
        try(CoreProcess core=startCore("PENDING")) {
            seed();
            var adapter=adapter(core.baseUrl());
            var input=SignatureRequest.builder().contractId(CONTRACT).documentId(DOCUMENT).signerPartyId(SIGNER).build();
            var submission=adapter.requestSignature(input).block(TIMEOUT);
            id=UUID.fromString(submission.getSignatureRequestId());
            assertThat(submission.getStatus()).isEqualTo("PENDING");
            assertThat(adapter.verifySignature(id.toString()).block(TIMEOUT).getStatus()).isEqualTo("PENDING");
            assertThat(adapter.requestSignature(input).block(TIMEOUT).getSignatureRequestId()).isEqualTo(id.toString());
        }
        try(CoreProcess pendingRestart=startCore("PENDING")) {
            assertThat(adapter(pendingRestart.baseUrl()).verifySignature(id.toString()).block(TIMEOUT).getStatus()).isEqualTo("PENDING");
            var sdk=new ApiClient();sdk.setBasePath(pendingRestart.baseUrl());
            var outcomes=new SandboxSignatureOutcomeApi(sdk);
            var first=outcomes.setSandboxSignatureOutcome(id,"synthetic-tenant",CONTRACT,"COMPLETED","complete:"+id).block(TIMEOUT);
            var duplicate=outcomes.setSandboxSignatureOutcome(id,"synthetic-tenant",CONTRACT,"COMPLETED","duplicate:"+id).block(TIMEOUT);
            assertThat(duplicate.getSignedAt()).isEqualTo(first.getSignedAt());
            assertThatThrownBy(()->outcomes.setSandboxSignatureOutcome(id,"synthetic-tenant",CONTRACT,"DECLINED","conflict:"+id).block(TIMEOUT))
                    .isInstanceOf(org.springframework.web.reactive.function.client.WebClientResponseException.class);
        }
        try(CoreProcess restarted=startCore("PENDING")) {
            var verified=adapter(restarted.baseUrl()).verifySignature(id.toString()).block(TIMEOUT);
            assertThat(verified.getStatus()).isEqualTo("VERIFIED");assertThat(verified.getContractId()).isEqualTo(CONTRACT);
            assertThat(verified.getDocumentId()).isEqualTo(DOCUMENT);assertThat(verified.getSignerPartyId()).isEqualTo(SIGNER);
            assertThat(verified.getSynthetic()).isTrue();
            var sdk=new ApiClient();sdk.setBasePath(restarted.baseUrl());
            assertThatThrownBy(()->new SignatureSubmissionApi(sdk).verifyContractSignature(id,"other-tenant",CONTRACT,null).block(TIMEOUT))
                    .isInstanceOf(org.springframework.web.reactive.function.client.WebClientResponseException.class);
            try(var connection=DriverManager.getConnection(DATABASE.getJdbcUrl(),DATABASE.getUsername(),DATABASE.getPassword());
                var statement=connection.createStatement();var result=statement.executeQuery("SELECT count(*) FROM document_signatures")) {
                assertThat(result.next()).isTrue();assertThat(result.getLong(1)).isEqualTo(1L);
            }
        }
    }
    private DocumentManagementESignatureAdapter adapter(String baseUrl) {
        var sdk=new ApiClient();sdk.setBasePath(baseUrl);
        return new DocumentManagementESignatureAdapter(new SignatureSubmissionApi(sdk),
                ()->Mono.just(new SignatureRequestContextPort.Context("synthetic-tenant",PROVIDER,true)));
    }
    private void seed() throws Exception {
        try(var connection=DriverManager.getConnection(DATABASE.getJdbcUrl(),DATABASE.getUsername(),DATABASE.getPassword());var statement=connection.createStatement()) {
            statement.execute("INSERT INTO documents(id,name,tenant_id) VALUES ('"+DOCUMENT+"','Synthetic SDK contract','synthetic-tenant')");
            statement.execute("INSERT INTO signature_providers(id,name,provider_code,tenant_id) VALUES ('"+PROVIDER+"','Synthetic SDK provider','SANDBOX','synthetic-tenant')");
        }
    }
    private CoreProcess startCore(String scenario) throws Exception {
        Path executable=Path.of(System.getProperty("signature.core.executable")).toAbsolutePath();
        assertThat(executable).isRegularFile();
        int port;try(var socket=new ServerSocket(0,0,java.net.InetAddress.getLoopbackAddress())) { port=socket.getLocalPort(); }
        Path log=Files.createTempFile(Path.of("target"),"signature-core-",".log");
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-jar",executable.toString(),
                "--spring.profiles.active=sandbox","--server.address=127.0.0.1","--server.port="+port,"--firefly.eda.enabled=false",
                "--spring.r2dbc.url=r2dbc:postgresql://"+DATABASE.getHost()+":"+DATABASE.getMappedPort(5432)+"/"+DATABASE.getDatabaseName(),
                "--spring.r2dbc.username="+DATABASE.getUsername(),"--spring.r2dbc.password="+DATABASE.getPassword(),
                "--spring.flyway.url="+DATABASE.getJdbcUrl(),"--spring.flyway.user="+DATABASE.getUsername(),"--spring.flyway.password="+DATABASE.getPassword(),
                "--integration.signature.sandbox.scenario="+scenario).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        var core=new CoreProcess(process,"http://127.0.0.1:"+port);
        var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        long deadline=System.nanoTime()+Duration.ofSeconds(45).toNanos();
        while(System.nanoTime()<deadline && process.isAlive()) {
            try {
                var response=http.send(HttpRequest.newBuilder(URI.create(core.baseUrl()+"/v3/api-docs")).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.discarding());
                if(response.statusCode()==200) return core;
            } catch(java.io.IOException ignored) { }
            Thread.sleep(150);
        }
        core.close();throw new IllegalStateException("Core did not start; inspect "+log.toAbsolutePath());
    }
    record CoreProcess(Process process,String baseUrl) implements AutoCloseable {
        @Override public void close() throws Exception {
            process.destroy();if(!process.waitFor(10,TimeUnit.SECONDS)) { process.destroyForcibly();process.waitFor(10,TimeUnit.SECONDS); }
        }
    }
}
