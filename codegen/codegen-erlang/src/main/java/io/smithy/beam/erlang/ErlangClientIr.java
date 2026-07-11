package io.smithy.beam.erlang;

import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.Module;
import io.beam.ir.erlang.TypeAlias;
import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamErlangLayout;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ErlangClientIr {
  private ErlangClientIr() {}

  static Module clientModule(
      BeamErlangLayout layout,
      ServiceShape service,
      List<String> exports,
      List<Function> operationFunctions) {
    List<String> headerComments = new ArrayList<>();
    headerComments.add("Generated Erlang client for " + service.getId() + ".");
    headerComments.add("Operation stubs use arity 2: (Config, Input).");
    headerComments.add("Service closure: " + service.getId());
    BeamAwsServiceMetadata.from(service)
        .ifPresent(
            meta -> {
              headerComments.add("AWS service metadata from model:");
              headerComments.add("  sdkId: " + meta.sdkId());
              headerComments.add("  endpointPrefix: " + meta.endpointPrefix());
            });

    List<Function> functions = new ArrayList<>(operationFunctions);

    return Module.of(
        layout.clientModuleName(),
        functions,
        headerComments,
        null,
        List.of(layout.typesHeaderFile()),
        List.of(clientConfigTypeDef()),
        exports);
  }

  static TypeAlias clientConfigTypeDef() {
    return TypeAlias.of(
        "client_config",
        "#{binary() => term()}",
        List.of(
            "Client configuration is intentionally opaque at this layer; "
                + "endpoint, transport, and protocol live in future runtime modules."));
  }
}
