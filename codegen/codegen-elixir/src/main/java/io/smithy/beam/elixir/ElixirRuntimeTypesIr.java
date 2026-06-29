package io.smithy.beam.elixir;

import io.smithy.beam.ir.elixir.ExBlankLine;
import io.smithy.beam.ir.elixir.ExDefstruct;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExModuleEntry;
import io.smithy.beam.ir.elixir.ExNestedModule;
import io.smithy.beam.ir.elixir.ExPreambleEntry;
import io.smithy.beam.ir.elixir.ExSourceLine;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExTypeDef;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExVar;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ElixirRuntimeTypesIr {

  private ElixirRuntimeTypesIr() {}

  static ExModule runtimeTypesModule(String moduleName, Optional<String> endpointRuleSetJson) {
    List<ExPreambleEntry> preamble =
        List.of(
            ExModuledoc.moduledoc(
                "Generated HTTP and client runtime types for Smithy service clients."));

    List<ExModuleEntry> entries = new ArrayList<>();
    entries.add(ExTypeDef.alias("http_request", "%__MODULE__.HttpRequest{}"));
    entries.add(httpRequestModule());
    entries.add(httpResponseModule());

    List<ExFunction> functions = new ArrayList<>();
    endpointRuleSetJson.ifPresent(
        json -> {
          entries.add(new ExBlankLine());
          entries.add(ExTypeDef.alias("endpoint_rule_set", "map()"));
          entries.add(ExSourceLine.line("@endpoint_rule_set_json ~S\"\"\""));
          for (String line : json.split("\n", -1)) {
            entries.add(ExSourceLine.line(line));
          }
          entries.add(ExSourceLine.line("\"\"\""));
          entries.add(
              ExSourceLine.line(
                  "Module.register_attribute(__MODULE__, :endpoint_rule_set, persist: true)"));
          entries.add(
              ExSourceLine.line("@endpoint_rule_set Jason.decode!(@endpoint_rule_set_json)"));
          functions.add(
              ExFunction.functionWithSpec(
                  "def",
                  "endpoint_rule_set",
                  ExSpec.functionSpec("endpoint_rule_set", "", "endpoint_rule_set()"),
                  List.of(
                      ExClause.inlineClause(List.of(), ExVar.var("@endpoint_rule_set")))));
        });

    return ExModule.module(moduleName, preamble, List.of(), List.of(), functions, entries);
  }

  private static ExNestedModule httpRequestModule() {
    return ExNestedModule.nestedModule(
        "HttpRequest",
        List.of(),
        List.of(
            ExDefstruct.defstructKeywords(
                List.of(
                    "method: \"GET\"",
                    "path: \"/\"",
                    "query: %{}",
                    "headers: []",
                    "body: \"\"",
                    "host: nil",
                    "stream: nil"))),
        List.of());
  }

  private static ExNestedModule httpResponseModule() {
    return ExNestedModule.nestedModule(
        "HttpResponse",
        List.of(),
        List.of(
            ExDefstruct.defstructKeywords(
                List.of("status: 200", "headers: []", "body: \"\"", "stream: nil"))),
        List.of());
  }
}
