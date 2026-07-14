package io.smithy.beam.elixir;

import io.smithy.beam.ir.elixir.ExDefstruct;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExModuleEntry;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExNestedModule;
import io.smithy.beam.ir.elixir.ExPreambleEntry;
import io.smithy.beam.ir.elixir.ExTypeDef;
import java.util.ArrayList;
import java.util.List;

final class ElixirRuntimeTypesIr {

  private ElixirRuntimeTypesIr() {}

  static ExModule runtimeTypesModule(String moduleName) {
    List<ExPreambleEntry> preamble =
        List.of(
            ExModuledoc.moduledoc(
                "Generated HTTP and client runtime types for Smithy service clients."));

    List<ExModuleEntry> entries = new ArrayList<>();
    entries.add(ExTypeDef.alias("http_request", "%__MODULE__.HttpRequest{}"));
    entries.add(httpRequestModule());
    entries.add(httpResponseModule());

    return ExModule.module(moduleName, preamble, List.of(), List.of(), List.of(), entries);
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
