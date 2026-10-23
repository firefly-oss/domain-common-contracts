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

import java.net.URI;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A configured origin, with cleartext restricted to an explicit private-loopback profile. */
record GovernedPeerEndpoint(String baseUrl, boolean allowLoopbackHttp) {
  String required() {
    try {
      if (baseUrl == null || baseUrl.isBlank() || baseUrl.length() > 2048) throw invalid();
      var uri = URI.create(baseUrl);
      String path = uri.getRawPath();
      int port = uri.getPort();
      if (uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || path != null && !path.isEmpty() && !"/".equals(path)
          || port == 0
          || port > 65535) throw invalid();
      if (!"https".equals(uri.getScheme())
          && !(allowLoopbackHttp
              && "http".equals(uri.getScheme())
              && Set.of("127.0.0.1", "localhost", "::1", "[::1]").contains(uri.getHost())))
        throw invalid();
      return "/".equals(path) ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    } catch (Exception error) {
      throw invalid();
    }
  }

  private static ResponseStatusException invalid() {
    return new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE, "GOVERNED_DOCUMENT_PEER_NOT_CONFIGURED");
  }
}
