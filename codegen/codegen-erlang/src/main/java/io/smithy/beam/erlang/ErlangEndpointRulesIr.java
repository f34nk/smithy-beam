package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlAttribute;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlComment;
import io.smithy.beam.ir.erlang.ErlExportAttribute;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlExprBlock;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlMacro;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMapEntry;
import io.smithy.beam.ir.erlang.ErlMatch;
import io.smithy.beam.ir.erlang.ErlModule;
import io.smithy.beam.ir.erlang.ErlRemoteCall;
import io.smithy.beam.ir.erlang.ErlTypeDef;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ErlangEndpointRulesIr {
  private static final String CLIENT_CONFIG = "client_config()";
  private static final String ENDPOINT_PARAMS = "endpoint_params()";

  private ErlangEndpointRulesIr() {}

  static ErlModule endpointRulesModule(
      String endpointsModule,
      String runtimeTypesHeaderFile,
      ServiceShape service,
      Map<String, String> clientContextKeys) {
    List<ErlFunction> functions = new ArrayList<>();
    functions.add(resolve());
    functions.addAll(mergeParamsFunctions(clientContextKeys));
    return new ErlModule(
        endpointsModule,
        List.of(
            ErlComment.comment("Generated endpoint rule resolver for " + service.getId() + ".")),
        List.of(
            new ErlAttribute("include", "\"" + runtimeTypesHeaderFile + "\""),
            ErlExportAttribute.export(List.of("resolve/2")),
            clientConfigType(),
            endpointParamsType()),
        functions);
  }

  static ErlFunction resolve() {
    return ErlFunction.functionWithSpec(
        "resolve",
        2,
        CLIENT_CONFIG + ", " + ENDPOINT_PARAMS,
        "{ok, #{url := binary()}} | {error, term()}",
        List.of(
            ErlClause.clause(
                List.of(ErlVarPattern.varPattern("Config"), ErlVarPattern.varPattern("Params")),
                ErlRemoteCall.call(
                    ErlAtom.atom("aws_endpoint_rules"),
                    "evaluate",
                    ErlMacro.macro("ENDPOINT_RULE_SET"),
                    ErlCallLocal.callLocal(
                        "merge_params", ErlVar.var("Config"), ErlVar.var("Params"))))));
  }

  static List<ErlFunction> mergeParamsFunctions(Map<String, String> clientContextKeys) {
    return List.of(
        mergeParams(),
        configToRuleParams(),
        clientContextParams(clientContextKeys),
        optionalParam());
  }

  private static ErlFunction mergeParams() {
    return ErlFunction.function(
        "merge_params",
        2,
        List.of(
            ErlClause.blockClause(
                List.of(ErlVarPattern.varPattern("Config"), ErlVarPattern.varPattern("Params")),
                ErlExprBlock.block(
                    ErlMatch.match(
                        ErlVarPattern.varPattern("ConfigParams"),
                        ErlCallLocal.callLocal("config_to_rule_params", ErlVar.var("Config"))),
                    ErlMatch.match(
                        ErlVarPattern.varPattern("ClientParams"),
                        ErlCallLocal.callLocal("client_context_params", ErlVar.var("Config"))),
                    ErlCall.call(
                        "maps",
                        "merge",
                        ErlCall.call(
                            "maps",
                            "merge",
                            ErlVar.var("ConfigParams"),
                            ErlVar.var("ClientParams")),
                        ErlVar.var("Params"))))));
  }

  private static ErlFunction configToRuleParams() {
    return ErlFunction.function(
        "config_to_rule_params",
        1,
        List.of(
            ErlClause.blockClause(
                List.of(ErlVarPattern.varPattern("Config")),
                ErlCase.caseExpr(
                    ErlCall.call(
                        "maps",
                        "get",
                        ErlAtom.atom("region"),
                        ErlVar.var("Config"),
                        ErlAtom.atom("undefined")),
                    ErlClause.clause(
                        List.of(ErlAtomPattern.atomPattern("undefined")), ErlMap.map()),
                    ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Value")),
                        ErlMap.map(
                            ErlMapEntry.entry(
                                ErlBinary.binary("Region"), ErlVar.var("Value"))))))));
  }

  private static ErlFunction clientContextParams(Map<String, String> clientContextKeys) {
    ErlExpr body;
    if (clientContextKeys.isEmpty()) {
      body = ErlMap.map();
    } else {
      List<ErlExpr> mergeArgs = new ArrayList<>();
      for (Map.Entry<String, String> entry : clientContextKeys.entrySet()) {
        mergeArgs.add(
            ErlCallLocal.callLocal(
                "optional_param",
                ErlVar.var("Config"),
                ErlAtom.atom(entry.getValue()),
                ErlBinary.binary(entry.getKey())));
      }
      body = ErlCall.call("maps", "merge", mergeArgs.toArray(ErlExpr[]::new));
    }
    return ErlFunction.function(
        "client_context_params",
        1,
        List.of(ErlClause.clause(List.of(ErlVarPattern.varPattern("Config")), body)));
  }

  private static ErlFunction optionalParam() {
    return ErlFunction.function(
        "optional_param",
        3,
        List.of(
            ErlClause.blockClause(
                List.of(
                    ErlVarPattern.varPattern("Config"),
                    ErlVarPattern.varPattern("Key"),
                    ErlVarPattern.varPattern("RuleKey")),
                ErlCase.caseExpr(
                    ErlCall.call(
                        "maps",
                        "get",
                        ErlVar.var("Key"),
                        ErlVar.var("Config"),
                        ErlAtom.atom("undefined")),
                    ErlClause.clause(
                        List.of(ErlAtomPattern.atomPattern("undefined")), ErlMap.map()),
                    ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Value")),
                        ErlMap.map(
                            ErlMapEntry.entry(ErlVar.var("RuleKey"), ErlVar.var("Value"))))))));
  }

  private static ErlTypeDef clientConfigType() {
    return new ErlTypeDef("client_config", "#{binary() => term()}");
  }

  private static ErlTypeDef endpointParamsType() {
    return new ErlTypeDef("endpoint_params", "#{binary() => term()}");
  }
}
