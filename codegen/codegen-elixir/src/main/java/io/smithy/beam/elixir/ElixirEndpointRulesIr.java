package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamContextParamsIndex;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExAliasAttr;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExNilPattern;
import io.smithy.beam.ir.elixir.ExPipeline;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirEndpointRulesIr {
  private ElixirEndpointRulesIr() {}

  static ExModule endpointRulesModule(ElixirContext ctx, ServiceShape service) {
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    String endpointsModule = ElixirSymbolProvider.toModuleName(layout.endpointsModuleName());
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    Map<String, String> clientContextKeys = BeamContextParamsIndex.clientContextConfigKeys(service);

    List<ExFunction> functions = new ArrayList<>();
    functions.add(resolve());
    functions.addAll(mergeParamsFunctions(clientContextKeys));

    return ExModule.module(
        endpointsModule,
        List.of(
            ExModuledoc.moduledoc(
                "Generated endpoint rule resolver for Smithy service clients.")),
        List.of(ExAliasAttr.alias(runtimeMod, "RuntimeTypes")),
        functions);
  }

  static ExFunction resolve() {
    return ExFunction.functionWithSpec(
        "def",
        "resolve",
        ExSpec.functionSpec("resolve", "map(), map()", "{:ok, %{url: String.t()}} | {:error, term()}"),
        List.of(
            ExClause.blockClauseSingleLineHead(
                List.of(ExVarPattern.var("config"), ExVarPattern.var("params")),
                ExCall.call(
                    "AwsEndpointRules",
                    "evaluate",
                    ExCall.call("RuntimeTypes", "endpoint_rule_set"),
                    ExCallLocal.callLocal(
                        "merge_params", ExVar.var("config"), ExVar.var("params"))))));
  }

  static List<ExFunction> mergeParamsFunctions(Map<String, String> clientContextKeys) {
    return List.of(
        mergeParams(),
        configToRuleParams(),
        clientContextParams(clientContextKeys),
        optionalParam());
  }

  private static ExFunction mergeParams() {
    return ExFunction.defpFunction(
        "merge_params",
        List.of(
            ExClause.blockClauseSingleLineHead(
                List.of(ExVarPattern.var("config"), ExVarPattern.var("params")),
                ExCapturedBlock.capturedBlock(
                    """
                    config_params = config_to_rule_params(config)
                    client_params = client_context_params(config)
                    Map.merge(Map.merge(config_params, client_params), params)"""))));
  }

  private static ExFunction configToRuleParams() {
    return ExFunction.defpFunction(
        "config_to_rule_params",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("config")),
                ExCase.caseExpr(
                    ExCall.call(
                        "Map", "get", ExVar.var("config"), ExAtom.atom("region")),
                    List.of(
                        ExCaseBranch.branch(ExNilPattern.nil(), ExMap.map()),
                        ExCaseBranch.branch(
                            ExVarPattern.var("value"),
                            ExMap.map(
                                ExMapEntry.entry(ExString.string("Region"), ExVar.var("value"))))),
                    true))));
  }

  private static ExFunction clientContextParams(Map<String, String> clientContextKeys) {
    if (clientContextKeys.isEmpty()) {
      return ExFunction.defpFunction(
          "client_context_params",
          List.of(ExClause.blockClause(List.of(ExVarPattern.var("_config")), ExMap.map())));
    }
    List<io.smithy.beam.ir.elixir.ExExpr> optionalCalls = new ArrayList<>();
    for (Map.Entry<String, String> entry : clientContextKeys.entrySet()) {
      optionalCalls.add(
          ExCallLocal.callLocal(
              "optional_param",
              ExVar.var("config"),
              ExAtom.atom(entry.getValue()),
              ExString.string(entry.getKey())));
    }
    return ExFunction.defpFunction(
        "client_context_params",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("config")),
                ExPipeline.pipeChain(
                    ExList.list(optionalCalls.toArray(io.smithy.beam.ir.elixir.ExExpr[]::new)),
                    ExCapturedBlock.capturedBlock(
                        "Enum.reduce(%{}, fn map, acc -> Map.merge(acc, map) end)")))));
  }

  private static ExFunction optionalParam() {
    return ExFunction.defpFunction(
        "optional_param",
        List.of(
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("config"),
                    ExVarPattern.var("key"),
                    ExVarPattern.var("rule_key")),
                ExCase.caseExpr(
                    ExCall.call("Map", "get", ExVar.var("config"), ExVar.var("key")),
                    List.of(
                        ExCaseBranch.branch(ExNilPattern.nil(), ExMap.map()),
                        ExCaseBranch.branch(
                            ExVarPattern.var("value"),
                            ExMap.map(
                                ExMapEntry.entry(ExVar.var("rule_key"), ExVar.var("value"))))),
                    true))));
  }
}
