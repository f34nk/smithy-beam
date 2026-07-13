package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ElixirRuntimeTypesIrTest {

  @Test
  void runtimeTypesModuleMatchesResource() {
    String output = ElixirRuntimeTypesIr.runtimeTypesModule("RuntimeTypes").asString();
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
}
