package io.smithy.beam.elixir;

import io.beam.ir.elixir.AnonFun;
import io.beam.ir.elixir.AnonFunClause;
import io.beam.ir.elixir.AtomExpr;
import io.beam.ir.elixir.AtomPattern;
import io.beam.ir.elixir.BlockExpr;
import io.beam.ir.elixir.CaseExpr;
import io.beam.ir.elixir.Clause;
import io.beam.ir.elixir.DotCallExpr;
import io.beam.ir.elixir.Expression;
import io.beam.ir.elixir.Function;
import io.beam.ir.elixir.FunctionArityGuard;
import io.beam.ir.elixir.FunctionHead;
import io.beam.ir.elixir.IfExpr;
import io.beam.ir.elixir.IntegerExpr;
import io.beam.ir.elixir.IntegerPattern;
import io.beam.ir.elixir.LocalCallExpr;
import io.beam.ir.elixir.MapExpr;
import io.beam.ir.elixir.MatchExpr;
import io.beam.ir.elixir.RemoteCallExpr;
import io.beam.ir.elixir.Spec;
import io.beam.ir.elixir.TupleExpr;
import io.beam.ir.elixir.TuplePattern;
import io.beam.ir.elixir.Variable;
import io.beam.ir.elixir.VariablePattern;
import io.beam.ir.elixir.WildcardPattern;
import java.util.List;

final class ElixirHandlerDiscoveryIr {
  private ElixirHandlerDiscoveryIr() {}

  static Function resolveImpl(String behaviourMod) {
    Expression handlers =
        RemoteCallExpr.of(
            "Enum",
            "reduce",
            List.of(
                RemoteCallExpr.of(behaviourMod, "callbacks", List.of()),
                MapExpr.of(List.of()),
                new AnonFun(
                    List.of(
                        AnonFunClause.of(
                            List.of(
                                TuplePattern.of(
                                    List.of(VariablePattern.of("fun"), IntegerPattern.of(3))),
                                VariablePattern.of("acc")),
                            new IfExpr(
                                LocalCallExpr.of(
                                    "function_exported?",
                                    List.of(
                                        Variable.of("impl"),
                                        Variable.of("fun"),
                                        IntegerExpr.of(3))),
                                RemoteCallExpr.of(
                                    "Map",
                                    "put",
                                    List.of(
                                        Variable.of("acc"),
                                        Variable.of("fun"),
                                        RemoteCallExpr.of(
                                            "Function",
                                            "capture",
                                            List.of(
                                                Variable.of("impl"),
                                                Variable.of("fun"),
                                                IntegerExpr.of(3))))),
                                Variable.of("acc"),
                                false)),
                        AnonFunClause.of(
                            List.of(VariablePattern.of("_item"), VariablePattern.of("acc")),
                            Variable.of("acc"))))));
    Expression ensureLoaded =
        new CaseExpr(
            RemoteCallExpr.of("Code", "ensure_loaded", List.of(Variable.of("impl"))),
            List.of(
                Clause.of(
                    TuplePattern.of(List.of(AtomPattern.of("module"), WildcardPattern.of())),
                    new BlockExpr(
                        List.of(
                            MatchExpr.bind("handlers", handlers),
                            TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("handlers")))))),
                Clause.of(
                    TuplePattern.of(List.of(AtomPattern.of("error"), WildcardPattern.of())),
                    TupleExpr.of(
                        List.of(
                            AtomExpr.of("error"),
                            TupleExpr.of(
                                List.of(AtomExpr.of("impl_not_loaded"), Variable.of("impl"))))))));
    return new Function(
        "resolve_impl",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("impl")))),
        ensureLoaded,
        null,
        null,
        false);
  }

  static Function initHandlers() {
    Expression resolveCase =
        new CaseExpr(
            LocalCallExpr.of("resolve_impl", List.of(Variable.of("@default_impl"))),
            List.of(
                Clause.of(
                    TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("handlers"))),
                    new BlockExpr(
                        List.of(
                            RemoteCallExpr.of(
                                ":persistent_term",
                                "put",
                                List.of(Variable.of("@handlers_key"), Variable.of("handlers"))),
                            AtomExpr.of("ok")))),
                Clause.of(
                    TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("reason"))),
                    new BlockExpr(
                        List.of(
                            RemoteCallExpr.of(
                                ":persistent_term",
                                "put",
                                List.of(Variable.of("@handlers_key"), MapExpr.of(List.of()))),
                            TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("reason"))))))));
    return new Function(
        "init_handlers",
        false,
        List.of(FunctionHead.of(List.of())),
        resolveCase,
        Spec.of("init_handlers() :: :ok | {:error, term()}"),
        null,
        false);
  }

  static Function dispatchHandler() {
    Expression lookupCase =
        new CaseExpr(
            RemoteCallExpr.of("Map", "get", List.of(Variable.of("handlers"), Variable.of("fun"))),
            List.of(
                Clause.of(
                    VariablePattern.of("handler"),
                    FunctionArityGuard.of("handler", 3),
                    new DotCallExpr(
                        Variable.of("handler"),
                        "()",
                        List.of(Variable.of("ctx"), Variable.of("input"), Variable.of("meta")))),
                Clause.of(
                    WildcardPattern.of(),
                    TupleExpr.of(List.of(AtomExpr.of("error"), AtomExpr.of("not_implemented"))))));
    return new Function(
        "dispatch_handler",
        true,
        List.of(
            FunctionHead.of(
                List.of(
                    VariablePattern.of("fun"),
                    VariablePattern.of("ctx"),
                    VariablePattern.of("input"),
                    VariablePattern.of("meta")))),
        new BlockExpr(
            List.of(
                MatchExpr.bind(
                    "handlers",
                    RemoteCallExpr.of(
                        ":persistent_term",
                        "get",
                        List.of(Variable.of("@handlers_key"), MapExpr.of(List.of())))),
                lookupCase)),
        null,
        null,
        false);
  }

  static Function operationDispatch(String handler) {
    return new Function(
        handler,
        false,
        List.of(
            FunctionHead.of(
                List.of(
                    VariablePattern.of("ctx"),
                    VariablePattern.of("input"),
                    VariablePattern.of("meta")))),
        LocalCallExpr.of(
            "dispatch_handler",
            List.of(
                AtomExpr.of(handler),
                Variable.of("ctx"),
                Variable.of("input"),
                Variable.of("meta"))),
        null,
        null,
        false);
  }

  static List<Function> discoveryFunctions(String behaviourMod) {
    return List.of(resolveImpl(behaviourMod), initHandlers(), dispatchHandler());
  }
}
