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

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.*;

/** Forwards real peer effects, then deliberately loses one successful reply. */
final class OwnedPeerProxy implements AutoCloseable {
  private final HttpServer server;
  private final int target;
  final AtomicReference<String> loseSuccessfulSuffix = new AtomicReference<>();
  final AtomicReference<Pause> pauseSuccessfulReply = new AtomicReference<>();

  static final class Pause {
    final String suffix;
    final java.util.concurrent.CountDownLatch observed = new java.util.concurrent.CountDownLatch(1),
        release = new java.util.concurrent.CountDownLatch(1);

    Pause(String suffix) {
      this.suffix = suffix;
    }
  }

  final AtomicInteger lost = new AtomicInteger(),
      requests = new AtomicInteger(),
      deletes = new AtomicInteger();
  private final java.util.concurrent.ExecutorService executor =
      Executors.newVirtualThreadPerTaskExecutor();

  OwnedPeerProxy(int target) throws Exception {
    this.target = target;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(executor);
    server.createContext(
        "/",
        exchange -> {
          try {
            requests.incrementAndGet();
            if (exchange.getRequestMethod().equals("DELETE")) deletes.incrementAndGet();
            String path = exchange.getRequestURI().toString();
            byte[] body = exchange.getRequestBody().readAllBytes();
            var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.target + path))
                    .timeout(Duration.ofSeconds(30));
            for (String name : new String[] {"Authorization", "Content-Type", "Accept"}) {
              String value = exchange.getRequestHeaders().getFirst(name);
              if (value != null) request.header(name, value);
            }
            var response =
                HttpClient.newHttpClient()
                    .send(
                        request
                            .method(
                                exchange.getRequestMethod(),
                                HttpRequest.BodyPublishers.ofByteArray(body))
                            .build(),
                        HttpResponse.BodyHandlers.ofByteArray());
            var pause = pauseSuccessfulReply.get();
            if (response.statusCode() == 200
                && pause != null
                && path.endsWith(pause.suffix)
                && pauseSuccessfulReply.compareAndSet(pause, null)) {
              pause.observed.countDown();
              if (!pause.release.await(3, java.util.concurrent.TimeUnit.SECONDS))
                throw new IllegalStateException("Private qualification barrier timed out");
            }
            String suffix = loseSuccessfulSuffix.get();
            boolean lose =
                response.statusCode() == 200
                    && exchange.getRequestMethod().equals("POST")
                    && suffix != null
                    && path.endsWith(suffix)
                    && loseSuccessfulSuffix.compareAndSet(suffix, null);
            byte[] bytes =
                lose ? "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8) : response.body();
            if (lose) lost.incrementAndGet();
            exchange
                .getResponseHeaders()
                .set(
                    "Content-Type",
                    response.headers().firstValue("Content-Type").orElse("application/json"));
            exchange.sendResponseHeaders(lose ? 503 : response.statusCode(), bytes.length);
            try (var output = exchange.getResponseBody()) {
              output.write(bytes);
            }
          } catch (Exception error) {
            try {
              exchange.sendResponseHeaders(502, -1);
            } catch (Exception ignored) {
            }
          } finally {
            exchange.close();
          }
        });
    server.start();
  }

  int port() {
    return server.getAddress().getPort();
  }

  @Override
  public void close() {
    server.stop(0);
    executor.close();
  }
}
