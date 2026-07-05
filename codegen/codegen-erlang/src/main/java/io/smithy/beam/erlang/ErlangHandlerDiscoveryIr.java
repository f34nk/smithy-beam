package io.smithy.beam.erlang;

import io.beam.ir.erlang.ApplyExpr;
import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.CaseExpr;
import io.beam.ir.erlang.Clause;
import io.beam.ir.erlang.Expression;
import io.beam.ir.erlang.ExpressionGuard;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.Fun;
import io.beam.ir.erlang.FunClause;
import io.beam.ir.erlang.IntegerExpr;
import io.beam.ir.erlang.IntegerPattern;
import io.beam.ir.erlang.ListComprehensionExpr;
import io.beam.ir.erlang.ListComprehensionFilter;
import io.beam.ir.erlang.ListComprehensionGenerator;
import io.beam.ir.erlang.LocalCallExpr;
import io.beam.ir.erlang.MacroExpr;
import io.beam.ir.erlang.MapExpr;
import io.beam.ir.erlang.MatchExpr;
import io.beam.ir.erlang.RemoteCallExpr;
import io.beam.ir.erlang.Spec;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.TuplePattern;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import io.beam.ir.erlang.WildcardPattern;
import java.util.List;

final class ErlangHandlerDiscoveryIr {
  private ErlangHandlerDiscoveryIr() {}

  static Function resolveImpl(String behaviourMod) {
    Expression handlersComprehension =
        ListComprehensionExpr.of(
            TupleExpr.of(
                List.of(
                    Variable.of("Fun"),
                    LocalCallExpr.of(
                        "make_handler", List.of(Variable.of("Impl"), Variable.of("Fun"))))),
            List.of(
                ListComprehensionGenerator.of(
                    TuplePattern.of(List.of(VariablePattern.of("Fun"), IntegerPattern.of(3))),
                    Variable.of("Callbacks")),
                ListComprehensionFilter.of(
                    RemoteCallExpr.of(
                        "erlang",
                        "function_exported",
                        List.of(Variable.of("Impl"), Variable.of("Fun"), IntegerExpr.of(3))))));
    Expression successBody =
        MatchExpr.bind(
            "Callbacks",
            RemoteCallExpr.of(behaviourMod, "behaviour_info", List.of(AtomExpr.of("callbacks"))),
            MatchExpr.bind(
                "Handlers",
                RemoteCallExpr.of("maps", "from_list", List.of(handlersComprehension)),
                TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("Handlers")))));
    return Function.of(
        "resolve_impl",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Impl")),
                CaseExpr.of(
                    RemoteCallExpr.of("code", "ensure_loaded", List.of(Variable.of("Impl"))),
                    List.of(
                        Clause.of(
                            TuplePattern.of(
                                List.of(AtomPattern.of("module"), VariablePattern.of("Impl"))),
                            successBody),
                        Clause.of(
                            TuplePattern.of(List.of(AtomPattern.of("error"), WildcardPattern.of())),
                            TupleExpr.of(
                                List.of(
                                    AtomExpr.of("error"),
                                    TupleExpr.of(
                                        List.of(
                                            AtomExpr.of("impl_not_loaded"),
                                            Variable.of("Impl")))))))))));
  }

  static Function makeHandler() {
    return Function.of(
        "make_handler",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Impl"), VariablePattern.of("Fun")),
                Fun.of(
                    List.of(
                        FunClause.of(
                            List.of(
                                VariablePattern.of("Ctx"),
                                VariablePattern.of("Input"),
                                VariablePattern.of("Meta")),
                            RemoteCallExpr.of(
                                Variable.of("Impl"),
                                Variable.of("Fun"),
                                List.of(
                                    Variable.of("Ctx"),
                                    Variable.of("Input"),
                                    Variable.of("Meta")))))))));
  }

  static Function initHandlers() {
    return Function.of(
        "init_handlers",
        List.of(
            FunctionClause.of(
                List.of(),
                CaseExpr.of(
                    LocalCallExpr.of("resolve_impl", List.of(MacroExpr.of("DEFAULT_IMPL"))),
                    List.of(
                        Clause.of(
                            TuplePattern.of(
                                List.of(AtomPattern.of("ok"), VariablePattern.of("Handlers"))),
                            MatchExpr.bind(
                                "_",
                                RemoteCallExpr.of(
                                    "persistent_term",
                                    "put",
                                    List.of(MacroExpr.of("HANDLERS_KEY"), Variable.of("Handlers"))),
                                AtomExpr.of("ok"))),
                        Clause.of(
                            TuplePattern.of(
                                List.of(AtomPattern.of("error"), VariablePattern.of("Reason"))),
                            MatchExpr.bind(
                                "_",
                                RemoteCallExpr.of(
                                    "persistent_term",
                                    "put",
                                    List.of(MacroExpr.of("HANDLERS_KEY"), MapExpr.of(List.of()))),
                                TupleExpr.of(
                                    List.of(AtomExpr.of("error"), Variable.of("Reason"))))))))),
        Spec.of("init_handlers() -> ok | {error, term()}"),
        null,
        null);
  }

  static Function dispatchHandler() {
    return Function.of(
        "dispatch_handler",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Fun"),
                    VariablePattern.of("Ctx"),
                    VariablePattern.of("Input"),
                    VariablePattern.of("Meta")),
                MatchExpr.bind(
                    "Handlers",
                    RemoteCallExpr.of(
                        "persistent_term",
                        "get",
                        List.of(MacroExpr.of("HANDLERS_KEY"), MapExpr.of(List.of()))),
                    CaseExpr.of(
                        RemoteCallExpr.of(
                            "maps",
                            "get",
                            List.of(
                                Variable.of("Fun"),
                                Variable.of("Handlers"),
                                AtomExpr.of("undefined"))),
                        List.of(
                            Clause.of(
                                VariablePattern.of("Handler"),
                                ExpressionGuard.of(
                                    LocalCallExpr.of(
                                        "is_function",
                                        List.of(Variable.of("Handler"), IntegerExpr.of(3)))),
                                ApplyExpr.of(
                                    Variable.of("Handler"),
                                    List.of(
                                        Variable.of("Ctx"),
                                        Variable.of("Input"),
                                        Variable.of("Meta")))),
                            Clause.of(
                                WildcardPattern.of(),
                                TupleExpr.of(
                                    List.of(
                                        AtomExpr.of("error"),
                                        AtomExpr.of("not_implemented"))))))))));
  }

  static Function operationDispatch(String handler) {
    return Function.of(
        handler,
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Ctx"),
                    VariablePattern.of("Input"),
                    VariablePattern.of("Meta")),
                LocalCallExpr.of(
                    "dispatch_handler",
                    List.of(
                        AtomExpr.of(handler),
                        Variable.of("Ctx"),
                        Variable.of("Input"),
                        Variable.of("Meta"))))));
  }

  static List<Function> discoveryFunctions(String behaviourMod) {
    return List.of(resolveImpl(behaviourMod), makeHandler(), initHandlers(), dispatchHandler());
  }
}
