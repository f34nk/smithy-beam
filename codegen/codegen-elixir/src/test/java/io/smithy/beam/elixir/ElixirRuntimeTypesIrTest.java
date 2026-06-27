package io.smithy.beam.elixir;

import io.smithy.beam.ir.elixir.ExModule;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ElixirRuntimeTypesIrTest {

  @Test
  void runtimeTypesModuleMatchesResource() {
    String output =
        ElixirRuntimeTypesIr.runtimeTypesModule("RuntimeTypes", Optional.empty()).asString();
    assertThat(output)
        .contains("defmodule RuntimeTypes do")
        .contains(
            "@moduledoc \"Generated HTTP and client runtime types for Smithy service clients.\"")
        .contains("@type http_request :: %__MODULE__.HttpRequest{}")
        .contains("defmodule HttpRequest do")
        .contains("method: \"GET\"")
        .contains("defmodule HttpResponse do")
        .contains("status: 200");
  }

  @Test
  void runtimeTypesModuleAppendsEndpointRuleSet() {
    ExModule module =
        ElixirRuntimeTypesIr.runtimeTypesModule("RuntimeTypes", Optional.of("{\"region\":\"us-east-1\"}"));
    String output = module.asString();
    assertThat(output)
        .contains("@type endpoint_rule_set :: map()")
        .contains("@endpoint_rule_set_json ~S\"\"\"")
        .contains("{\"region\":\"us-east-1\"}")
        .contains("@endpoint_rule_set Jason.decode!(@endpoint_rule_set_json)")
        .contains("@spec endpoint_rule_set() :: endpoint_rule_set()")
        .contains("def endpoint_rule_set, do: @endpoint_rule_set");
  }
}
