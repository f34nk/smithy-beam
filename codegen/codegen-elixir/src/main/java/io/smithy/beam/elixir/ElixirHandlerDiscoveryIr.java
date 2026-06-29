package io.smithy.beam.elixir;

import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExDotCall;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExExprBlock;
import io.smithy.beam.ir.elixir.ExFor;
import io.smithy.beam.ir.elixir.ExForFilter;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExGuard;
import io.smithy.beam.ir.elixir.ExInteger;
import io.smithy.beam.ir.elixir.ExIntegerPattern;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.List;

final class ElixirHandlerDiscoveryIr {
  private ElixirHandlerDiscoveryIr() {}

  static ExFunction resolveImpl(String behaviourMod) {
    ExExpr handlers =
        ExFor.forIntoExpr(
            ExTuple.tuple(
                ExVar.var("fun"),
                ExCall.call(
                    "Function",
                    "capture",
                    ExVar.var("impl"),
                    ExVar.var("fun"),
                    ExInteger.integer(3))),
            ExTuplePattern.tuple(
                ExVarPattern.var("fun"), ExIntegerPattern.integer(3)),
            ExCall.call(behaviourMod, "callbacks"),
            ExMap.map(),
            ExForFilter.filter(
                ExCallLocal.callLocal(
                    "function_exported?",
                    ExVar.var("impl"),
                    ExVar.var("fun"),
                    ExInteger.integer(3))));
    ExCase ensureLoaded =
        ExCase.caseExpr(
            ExCall.call("Code", "ensure_loaded", ExVar.var("impl")),
            ExCaseBranch.branch(
                ExTuplePattern.tuple(
                    ExAtomPattern.atom("module"), ExVarPattern.var("_")),
                ExExprBlock.block(
                    ExMatch.match(ExVarPattern.var("handlers"), handlers),
                    ExTuple.tuple(ExAtom.atom("ok"), ExVar.var("handlers")))),
            ExCaseBranch.branch(
                ExTuplePattern.tuple(
                    ExAtomPattern.atom("error"), ExVarPattern.var("_")),
                ExTuple.tuple(
                    ExAtom.atom("error"),
                    ExTuple.tuple(ExAtom.atom("impl_not_loaded"), ExVar.var("impl")))));
    return ExFunction.defpFunction(
        "resolve_impl",
        List.of(ExClause.blockClause(List.of(ExVarPattern.var("impl")), ensureLoaded)));
  }

  static ExFunction initHandlers() {
    ExCase resolveCase =
        ExCase.caseExpr(
            ExCallLocal.callLocal("resolve_impl", ExVar.var("@default_impl")),
            ExCaseBranch.branch(
                ExTuplePattern.tuple(
                    ExAtomPattern.atom("ok"), ExVarPattern.var("handlers")),
                ExExprBlock.block(
                    ExCall.call(
                        ":persistent_term",
                        "put",
                        ExVar.var("@handlers_key"),
                        ExVar.var("handlers")),
                    ExAtom.atom("ok"))),
            ExCaseBranch.branch(
                ExTuplePattern.tuple(
                    ExAtomPattern.atom("error"), ExVarPattern.var("reason")),
                ExExprBlock.block(
                    ExCall.call(
                        ":persistent_term", "put", ExVar.var("@handlers_key"), ExMap.map()),
                    ExTuple.tuple(ExAtom.atom("error"), ExVar.var("reason")))));
    return ExFunction.functionWithSpec(
        "def",
        "init_handlers",
        ExSpec.functionSpec("init_handlers", "", ":ok | {:error, term()}"),
        List.of(ExClause.blockClause(List.of(), resolveCase)));
  }

  static ExFunction dispatchHandler() {
    ExCase lookupCase =
        ExCase.caseExpr(
            ExCall.call("Map", "get", ExVar.var("handlers"), ExVar.var("fun")),
            ExCaseBranch.branch(
                ExVarPattern.var("handler"),
                List.of(
                    ExGuard.guard("is_function", ExVar.var("handler"), ExInteger.integer(3))),
                ExDotCall.dotCall(
                    ExVar.var("handler"),
                    ExVar.var("ctx"),
                    ExVar.var("input"),
                    ExVar.var("meta"))),
            ExCaseBranch.branch(
                ExVarPattern.var("_"),
                ExTuple.tuple(ExAtom.atom("error"), ExAtom.atom("not_implemented"))));
    return ExFunction.defpFunction(
        "dispatch_handler",
        List.of(
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("fun"),
                    ExVarPattern.var("ctx"),
                    ExVarPattern.var("input"),
                    ExVarPattern.var("meta")),
                ExExprBlock.block(
                    ExMatch.match(
                        ExVarPattern.var("handlers"),
                        ExCall.call(
                            ":persistent_term",
                            "get",
                            ExVar.var("@handlers_key"),
                            ExMap.map())),
                    lookupCase))));
  }

  static ExFunction operationDispatch(String handler) {
    return ExFunction.defFunction(
        handler,
        List.of(
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("ctx"),
                    ExVarPattern.var("input"),
                    ExVarPattern.var("meta")),
                ExCallLocal.callLocal(
                    "dispatch_handler",
                    ExAtom.atom(handler),
                    ExVar.var("ctx"),
                    ExVar.var("input"),
                    ExVar.var("meta")))));
  }

  static List<ExFunction> discoveryFunctions(String behaviourMod) {
    return List.of(resolveImpl(behaviourMod), initHandlers(), dispatchHandler());
  }
}
