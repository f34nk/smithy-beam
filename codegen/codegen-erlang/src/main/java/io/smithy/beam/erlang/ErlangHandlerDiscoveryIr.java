package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.*;
import java.util.List;

final class ErlangHandlerDiscoveryIr {
  private ErlangHandlerDiscoveryIr() {}

  static ErlFunction resolveImpl(String behaviourMod) {
    ErlCase ensureLoadedCase =
        ErlCase.caseExpr(
            ErlCall.call("code", "ensure_loaded", ErlVar.var("Impl")),
            ErlClause.clause(
                List.of(
                    ErlTuplePattern.tuplePattern(
                        ErlAtomPattern.atomPattern("module"), ErlVarPattern.varPattern("Impl"))),
                ErlExprBlock.block(
                    ErlMatch.match(
                        ErlVarPattern.varPattern("Callbacks"),
                        ErlCall.call(behaviourMod, "behaviour_info", ErlAtom.atom("callbacks"))),
                    ErlMatch.match(
                        ErlVarPattern.varPattern("Handlers"),
                        ErlCall.call(
                            "maps",
                            "from_list",
                            ErlListComprehension.comprehensionQualifiers(
                                ErlTuple.tuple(
                                    ErlVar.var("Fun"),
                                    ErlCallLocal.callLocal(
                                        "make_handler", ErlVar.var("Impl"), ErlVar.var("Fun"))),
                                List.of(
                                    new ErlComprehensionGenerator(
                                        ErlTuplePattern.tuplePattern(
                                            ErlVarPattern.varPattern("Fun"),
                                            ErlIntegerPattern.integerPattern(3)),
                                        ErlVar.var("Callbacks")),
                                    new ErlComprehensionFilter(
                                        ErlCall.call(
                                            "erlang",
                                            "function_exported",
                                            ErlVar.var("Impl"),
                                            ErlVar.var("Fun"),
                                            ErlInteger.integer(3))))))),
                    ErlTuple.tuple(ErlAtom.atom("ok"), ErlVar.var("Handlers")))),
            ErlClause.blockClause(
                List.of(
                    ErlTuplePattern.tuplePattern(
                        ErlAtomPattern.atomPattern("error"), ErlVarPattern.varPattern("_"))),
                ErlTuple.tuple(
                    ErlAtom.atom("error"),
                    ErlTuple.tuple(ErlAtom.atom("impl_not_loaded"), ErlVar.var("Impl")))));

    return ErlFunction.function(
        "resolve_impl",
        1,
        List.of(ErlClause.clause(List.of(ErlVarPattern.varPattern("Impl")), ensureLoadedCase)));
  }

  static ErlFunction makeHandler() {
    return ErlFunction.function(
        "make_handler",
        2,
        List.of(
            ErlClause.blockClause(
                List.of(ErlVarPattern.varPattern("Impl"), ErlVarPattern.varPattern("Fun")),
                ErlFun.compactFun(
                    ErlClause.clause(
                        List.of(
                            ErlVarPattern.varPattern("Ctx"),
                            ErlVarPattern.varPattern("Input"),
                            ErlVarPattern.varPattern("Meta")),
                        ErlRemoteCall.call(
                            ErlVar.var("Impl"),
                            ErlVar.var("Fun"),
                            ErlVar.var("Ctx"),
                            ErlVar.var("Input"),
                            ErlVar.var("Meta")))))));
  }

  static ErlFunction initHandlers() {
    ErlCase resolveCase =
        ErlCase.caseExpr(
            ErlCallLocal.callLocal("resolve_impl", ErlMacro.macro("DEFAULT_IMPL")),
            ErlClause.blockClause(
                List.of(
                    ErlTuplePattern.tuplePattern(
                        ErlAtomPattern.atomPattern("ok"), ErlVarPattern.varPattern("Handlers"))),
                ErlExprBlock.block(
                    ErlCall.call(
                        "persistent_term",
                        "put",
                        ErlMacro.macro("HANDLERS_KEY"),
                        ErlVar.var("Handlers")),
                    ErlAtom.atom("ok"))),
            ErlClause.blockClause(
                List.of(
                    ErlTuplePattern.tuplePattern(
                        ErlAtomPattern.atomPattern("error"), ErlVarPattern.varPattern("Reason"))),
                ErlExprBlock.block(
                    ErlCall.call(
                        "persistent_term", "put", ErlMacro.macro("HANDLERS_KEY"), ErlMap.map()),
                    ErlTuple.tuple(ErlAtom.atom("error"), ErlVar.var("Reason")))));

    return ErlFunction.functionWithSpec(
        "init_handlers",
        0,
        ErlFunctionSpec.functionSpec("init_handlers", "", "ok | {error, term()}"),
        List.of(ErlClause.clause(List.of(), resolveCase)));
  }

  static ErlFunction dispatchHandler() {
    ErlCase lookupCase =
        ErlCase.caseExpr(
            ErlCall.call(
                "maps",
                "get",
                ErlVar.var("Fun"),
                ErlVar.var("Handlers"),
                ErlAtom.atom("undefined")),
            ErlClause.blockClause(
                List.of(ErlVarPattern.varPattern("Handler")),
                List.of(
                    ErlGuard.guard("is_function", ErlVar.var("Handler"), ErlInteger.integer(3))),
                ErlApply.apply(
                    ErlVar.var("Handler"),
                    ErlVar.var("Ctx"),
                    ErlVar.var("Input"),
                    ErlVar.var("Meta"))),
            ErlClause.blockClause(
                List.of(ErlVarPattern.varPattern("_")),
                ErlTuple.tuple(ErlAtom.atom("error"), ErlAtom.atom("not_implemented"))));

    return ErlFunction.function(
        "dispatch_handler",
        4,
        List.of(
            ErlClause.clause(
                List.of(
                    ErlVarPattern.varPattern("Fun"),
                    ErlVarPattern.varPattern("Ctx"),
                    ErlVarPattern.varPattern("Input"),
                    ErlVarPattern.varPattern("Meta")),
                ErlExprBlock.block(
                    ErlMatch.match(
                        ErlVarPattern.varPattern("Handlers"),
                        ErlCall.call(
                            "persistent_term",
                            "get",
                            ErlMacro.macro("HANDLERS_KEY"),
                            ErlMap.map())),
                    lookupCase))));
  }

  static ErlFunction operationDispatch(String handler) {
    return ErlFunction.function(
        handler,
        3,
        List.of(
            ErlClause.blockClause(
                List.of(
                    ErlVarPattern.varPattern("Ctx"),
                    ErlVarPattern.varPattern("Input"),
                    ErlVarPattern.varPattern("Meta")),
                ErlCallLocal.callLocal(
                    "dispatch_handler",
                    ErlAtom.atom(handler),
                    ErlVar.var("Ctx"),
                    ErlVar.var("Input"),
                    ErlVar.var("Meta")))));
  }

  static List<ErlFunction> discoveryFunctions(String behaviourMod) {
    return List.of(resolveImpl(behaviourMod), makeHandler(), initHandlers(), dispatchHandler());
  }
}
