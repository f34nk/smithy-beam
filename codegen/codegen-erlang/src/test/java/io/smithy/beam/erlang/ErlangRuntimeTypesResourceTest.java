package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ErlangRuntimeTypesResourceTest {

  private static final String RESOURCE_PATH = "runtime/erlang/src/runtime_types.hrl";

  @Test
  void runtimeTypesResourceIsPackaged() throws IOException {
    String output = readResource(RESOURCE_PATH);
    assertThat(output)
        .contains("-ifndef(BEAM_RUNTIME_TYPES_INCLUDED).")
        .contains("-define(BEAM_RUNTIME_TYPES_INCLUDED, true).")
        .contains("-record(http_request, {")
        .contains("method = <<\"GET\">> :: binary()")
        .contains("-type http_request() :: #http_request{}.")
        .contains("-record(http_response, {")
        .contains("status = 200 :: non_neg_integer()")
        .contains("-type http_response() :: #http_response{}.")
        .contains("-endif.");
  }

  private static String readResource(String resourcePath) throws IOException {
    try (InputStream in =
        ErlangRuntimeTypesResourceTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
