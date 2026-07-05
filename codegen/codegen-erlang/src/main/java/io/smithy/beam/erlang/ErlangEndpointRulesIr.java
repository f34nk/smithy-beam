package io.smithy.beam.erlang;

import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.BinaryExpr;
import io.beam.ir.erlang.BlockExpr;
import io.beam.ir.erlang.CaseExpr;
import io.beam.ir.erlang.Clause;
import io.beam.ir.erlang.Expression;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.LocalCallExpr;
import io.beam.ir.erlang.MacroExpr;
import io.beam.ir.erlang.MapEntry;
import io.beam.ir.erlang.MapExpr;
import io.beam.ir.erlang.MatchExpr;
import io.beam.ir.erlang.Module;
import io.beam.ir.erlang.RemoteCallExpr;
import io.beam.ir.erlang.Spec;
import io.beam.ir.erlang.TypeAlias;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ErlangEndpointRulesIr {
  private ErlangEndpointRulesIr() {}

  static Module endpointRulesModule(
      String endpointsModule,
      String runtimeTypesHeaderFile,
      ServiceShape service,
      Map<String, String> clientContextKeys) {
    List<Function> functions = new ArrayList<>();
    functions.add(resolve());
    functions.addAll(mergeParamsFunctions(clientContextKeys));
    return Module.of(
        endpointsModule,
        functions,
        List.of("Generated endpoint rule resolver for " + service.getId() + "."),
        null,
        List.of(runtimeTypesHeaderFile),
        List.of(
            TypeAlias.of("client_config", "#{binary() => term()}"),
            TypeAlias.of("endpoint_params", "#{binary() => term()}")),
        List.of("resolve/2"));
  }

  static Function resolve() {
    return Function.of(
        "resolve",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config"), VariablePattern.of("Params")),
                RemoteCallExpr.of(
                    "aws_endpoint_rules",
                    "evaluate",
                    List.of(
                        MacroExpr.of("ENDPOINT_RULE_SET"),
                        LocalCallExpr.of(
                            "merge_params",
                            List.of(Variable.of("Config"), Variable.of("Params"))))))),
        Spec.of(
            "resolve(client_config(), endpoint_params()) -> {ok, #{url := binary()}} | {error, term()}"),
        null,
        null);
  }

  static List<Function> mergeParamsFunctions(Map<String, String> clientContextKeys) {
    return List.of(
        mergeParams(), configToRuleParams(), clientContextParams(clientContextKeys), optionalParam());
  }

  private static Function mergeParams() {
    return Function.of(
        "merge_params",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config"), VariablePattern.of("Params")),
                BlockExpr.newlineSeparated(
                    List.of(
                        MatchExpr.bindValue(
                            "ConfigParams",
                            LocalCallExpr.of("config_to_rule_params", List.of(Variable.of("Config")))),
                        MatchExpr.bindValue(
                            "ClientParams",
                            LocalCallExpr.of("client_context_params", List.of(Variable.of("Config")))),
                        RemoteCallExpr.of(
                            "maps",
                            "merge",
                            List.of(
                                RemoteCallExpr.of(
                                    "maps",
                                    "merge",
                                    List.of(Variable.of("ConfigParams"), Variable.of("ClientParams"))),
                                Variable.of("Params"))))))),
        null,
        null,
        null);
  }

  private static Function configToRuleParams() {
    return Function.of(
        "config_to_rule_params",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "maps",
                        "get",
                        List.of(
                            AtomExpr.of("region"),
                            Variable.of("Config"),
                            AtomExpr.of("undefined"))),
                    List.of(
                        Clause.of(AtomPattern.of("undefined"), MapExpr.of(List.of())),
                        Clause.of(
                            VariablePattern.of("Value"),
                            MapExpr.of(
                                List.of(
                                    MapEntry.of(BinaryExpr.of("Region"), Variable.of("Value"))))))))),
        null,
        null,
        null);
  }

  private static Function clientContextParams(Map<String, String> clientContextKeys) {
    Expression body;
    if (clientContextKeys.isEmpty()) {
      body = MapExpr.of(List.of());
    } else {
      List<Expression> mergeArgs = new ArrayList<>();
      for (Map.Entry<String, String> entry : clientContextKeys.entrySet()) {
        mergeArgs.add(
            LocalCallExpr.of(
                "optional_param",
                List.of(
                    Variable.of("Config"),
                    AtomExpr.of(entry.getValue()),
                    BinaryExpr.of(entry.getKey()))));
      }
      body = RemoteCallExpr.of("maps", "merge", mergeArgs);
    }
    return Function.of(
        "client_context_params",
        List.of(FunctionClause.of(List.of(VariablePattern.of("Config")), body)),
        null,
        null,
        null);
  }

  private static Function optionalParam() {
    return Function.of(
        "optional_param",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Config"),
                    VariablePattern.of("Key"),
                    VariablePattern.of("RuleKey")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "maps",
                        "get",
                        List.of(
                            Variable.of("Key"),
                            Variable.of("Config"),
                            AtomExpr.of("undefined"))),
                    List.of(
                        Clause.of(AtomPattern.of("undefined"), MapExpr.of(List.of())),
                        Clause.of(
                            VariablePattern.of("Value"),
                            MapExpr.of(
                                List.of(
                                    MapEntry.of(Variable.of("RuleKey"), Variable.of("Value"))))))))),
        null,
        null,
        null);
  }
}
