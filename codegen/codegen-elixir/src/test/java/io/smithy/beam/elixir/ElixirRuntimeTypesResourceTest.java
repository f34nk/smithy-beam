package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ElixirRuntimeTypesResourceTest {

  private static final String RESOURCE_PATH = "runtime/elixir/lib/runtime_types.ex";

  @Test
  void runtimeTypesResourceIsPackaged() throws IOException {
    String output = readResource(RESOURCE_PATH);
    assertThat(output)
        .contains("defmodule RuntimeTypes do")
        .contains(
            "@moduledoc \"Generated HTTP and client runtime types for Smithy service clients.\"")
        .contains("@type http_request :: %__MODULE__.HttpRequest{}")
        .contains("defmodule HttpRequest do")
        .contains("method: \"GET\"")
        .contains("defmodule HttpResponse do")
        .contains("status: 200");
    assertThat(output).doesNotContain("endpoint_rule_set");
  }

  private static String readResource(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirRuntimeTypesResourceTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
