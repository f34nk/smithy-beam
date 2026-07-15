package io.smithy.beam.elixir;

import io.beam.dsl.elixir.Alias;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.Module;
import io.beam.dsl.elixir.Moduledoc;
import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamElixirLayout;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirClientDsl {
  private ElixirClientDsl() {}

  static List<String> clientConfigTrailingAttributes() {
    return List.of(
        "# Client configuration is intentionally opaque at this layer; "
            + "endpoint, transport, and protocol live in future runtime modules.",
        "@type client_config :: map()");
  }

  static Module clientModule(
      BeamElixirLayout layout,
      ServiceShape service,
      String typesModuleName,
      List<Function> operationFunctions) {
    List<String> moduleAttributes = new ArrayList<>();
    BeamAwsServiceMetadata.from(service)
        .ifPresent(
            meta -> {
              moduleAttributes.add("# AWS service metadata from model:");
              moduleAttributes.add("#   sdkId: " + meta.sdkId());
              moduleAttributes.add("#   endpointPrefix: " + meta.endpointPrefix());
            });
    return Module.of(
        ElixirSymbolProvider.toModuleName(layout.clientModuleName()),
        Moduledoc.of(
            "Generated Elixir client for "
                + service.getId()
                + ".\n\nOperation stubs accept config and input. "
                + "Transport and protocol are not generated here."),
        List.of(),
        List.of(Alias.of(typesModuleName, "Types")),
        moduleAttributes,
        List.of(),
        List.of(),
        clientConfigTrailingAttributes(),
        operationFunctions);
  }
}
