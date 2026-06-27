package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamContextParamsIndex;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import java.util.Map;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Emits {@code <service>_endpoints.ex} when the model defines {@code @endpointRuleSet}. */
public final class ElixirEndpointRulesEmitter {

  private ElixirEndpointRulesEmitter() {}

  public static void emit(ElixirContext ctx, ServiceShape service) {
    if (!BeamEndpointRuleSetEmitter.hasRuleSet(ctx.model(), service)) {
      return;
    }

    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    String endpointsModule = ElixirSymbolProvider.toModuleName(layout.endpointsModuleName());
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    Map<String, String> clientContextKeys = BeamContextParamsIndex.clientContextConfigKeys(service);

    ctx.writerDelegator()
        .useFileWriter(
            layout.endpointsModuleFile(),
            writer -> {
              writer.write("defmodule $L do", endpointsModule);
              writer.indent();
              writer.write(
                  "@moduledoc \"Generated endpoint rule resolver for Smithy service clients.\"");
              writer.write("");
              writer.write("alias $L, as: RuntimeTypes", runtimeMod);
              writer.write("");
              ElixirFormat.writeSpec(
                  writer,
                  "@spec",
                  "resolve",
                  "map(), map()",
                  "{:ok, %{url: String.t()}} | {:error, term()}");
              writer.write("def resolve(config, params) do");
              writer.indent();
              writer.write(
                  "AwsEndpointRules.evaluate(RuntimeTypes.endpoint_rule_set(), merge_params(config, params))");
              writer.dedent();
              writer.write("end");
              writer.write("");
              writeMergeParams(writer, clientContextKeys);
              writer.dedent();
              writer.write("end");
            });
  }

  private static void writeMergeParams(ElixirWriter writer, Map<String, String> clientContextKeys) {
    writer.write("defp merge_params(config, params) do");
    writer.indent();
    writer.write("config_params = config_to_rule_params(config)");
    writer.write("client_params = client_context_params(config)");
    writer.write("Map.merge(Map.merge(config_params, client_params), params)");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp config_to_rule_params(config) do");
    writer.indent();
    writer.write("case Map.get(config, :region) do");
    writer.indent();
    writer.write("nil -> %{}");
    writer.write("");
    writer.write("value -> %{\"Region\" => value}");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp client_context_params(config) do");
    writer.indent();
    if (clientContextKeys.isEmpty()) {
      writer.write("%{}");
    } else {
      ElixirFormat.beginPipelineBinding(writer, "params");
      writer.write("[");
      boolean first = true;
      for (Map.Entry<String, String> entry : clientContextKeys.entrySet()) {
        if (!first) {
          writer.write(",");
        }
        first = false;
        writer.write("optional_param(config, :$L, \"$L\")", entry.getValue(), entry.getKey());
      }
      writer.write("]");
      ElixirFormat.writePipelineStep(
          writer, "Enum.reduce(%{}, fn map, acc -> Map.merge(acc, map) end)");
      ElixirFormat.endPipelineBinding(writer);
    }
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp optional_param(config, key, rule_key) do");
    writer.indent();
    writer.write("case Map.get(config, key) do");
    writer.indent();
    writer.write("nil -> %{}");
    writer.write("");
    writer.write("value -> %{rule_key => value}");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
  }
}
