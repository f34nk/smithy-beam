package io.smithy.beam.elixir;

import io.beam.dsl.elixir.AndGuard;
import io.beam.dsl.elixir.AnonFun;
import io.beam.dsl.elixir.AnonFunClause;
import io.beam.dsl.elixir.AtomExpr;
import io.beam.dsl.elixir.AtomPattern;
import io.beam.dsl.elixir.BlockExpr;
import io.beam.dsl.elixir.BooleanExpr;
import io.beam.dsl.elixir.CaseExpr;
import io.beam.dsl.elixir.Clause;
import io.beam.dsl.elixir.ComparisonGuard;
import io.beam.dsl.elixir.ConsListPattern;
import io.beam.dsl.elixir.DotCallExpr;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.FunctionDoc;
import io.beam.dsl.elixir.FunctionHead;
import io.beam.dsl.elixir.Guard;
import io.beam.dsl.elixir.IfExpr;
import io.beam.dsl.elixir.InfixExpr;
import io.beam.dsl.elixir.IntegerExpr;
import io.beam.dsl.elixir.IntegerPattern;
import io.beam.dsl.elixir.IsTypeGuard;
import io.beam.dsl.elixir.ListExpr;
import io.beam.dsl.elixir.ListPattern;
import io.beam.dsl.elixir.LocalCallExpr;
import io.beam.dsl.elixir.MapEntry;
import io.beam.dsl.elixir.MapExpr;
import io.beam.dsl.elixir.MapPattern;
import io.beam.dsl.elixir.MapPatternEntry;
import io.beam.dsl.elixir.MatchExpr;
import io.beam.dsl.elixir.Module;
import io.beam.dsl.elixir.Moduledoc;
import io.beam.dsl.elixir.NilExpr;
import io.beam.dsl.elixir.NilPattern;
import io.beam.dsl.elixir.Pattern;
import io.beam.dsl.elixir.RemoteCallExpr;
import io.beam.dsl.elixir.Spec;
import io.beam.dsl.elixir.StringExpr;
import io.beam.dsl.elixir.StructExpr;
import io.beam.dsl.elixir.TupleExpr;
import io.beam.dsl.elixir.TuplePattern;
import io.beam.dsl.elixir.Variable;
import io.beam.dsl.elixir.VariablePattern;
import io.beam.dsl.elixir.WildcardPattern;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamWaiterIndex;
import io.smithy.beam.core.BeamWaiterPaths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirWaiterDsl {
  private ElixirWaiterDsl() {}

  static Module waitersModule(
      ElixirContext ctx,
      ServiceShape service,
      BeamWaiterIndex index,
      SymbolProvider sp,
      Model model) {
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    String moduleName = ElixirSymbolProvider.toModuleName(layout.waitersModuleName());
    String clientMod = ElixirSymbolProvider.toModuleName(layout.clientModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    List<Function> functions = new ArrayList<>();
    for (BeamWaiterIndex.WaiterBinding binding : index.bindings()) {
      functions.add(waiterFunction(index, binding, clientMod, typesMod, sp));
    }
    functions.addAll(waitUntilHelperFunctions());
    return Module.of(
        moduleName,
        Moduledoc.of("Generated waiters for " + service.getId() + " (generated)."),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        functions);
  }

  static Function waiterFunction(
      BeamWaiterIndex index,
      BeamWaiterIndex.WaiterBinding binding,
      String clientMod,
      String typesMod,
      SymbolProvider sp) {
    OperationShape operation = binding.operation();
    Symbol opSym = sp.toSymbol(operation);
    String fn = waitFunctionName(binding.name());
    List<Expression> acceptorMaps =
        index.acceptors(binding).stream().map(a -> acceptorMap(a, typesMod, sp)).toList();

    return Function.of(
        fn,
        false,
        List.of(
            FunctionHead.of(
                List.of(
                    VariablePattern.of("client"),
                    VariablePattern.of("input"),
                    VariablePattern.of("opts")))),
        BlockExpr.of(
            List.of(
                MatchExpr.bind("acceptors", ListExpr.of(acceptorMaps)),
                MatchExpr.bind(
                    "wait_opts",
                    RemoteCallExpr.of(
                        "Keyword",
                        "merge",
                        List.of(
                            ListExpr.of(
                                List.of(
                                    TupleExpr.of(
                                        List.of(
                                            AtomExpr.of("min_delay_ms"),
                                            IntegerExpr.of(binding.minDelaySeconds() * 1000L))),
                                    TupleExpr.of(
                                        List.of(
                                            AtomExpr.of("max_delay_ms"),
                                            IntegerExpr.of(binding.maxDelaySeconds() * 1000L))))),
                            Variable.of("opts")))),
                LocalCallExpr.of(
                    "wait_until",
                    List.of(
                        AnonFun.of(
                            List.of(
                                AnonFunClause.of(
                                    List.of(),
                                    RemoteCallExpr.of(
                                        clientMod,
                                        opSym.getName(),
                                        List.of(Variable.of("client"), Variable.of("input")))))),
                        Variable.of("acceptors"),
                        Variable.of("wait_opts"))))),
        Spec.of(fn + "(term(), map(), keyword()) :: {:ok, term()} | {:error, term()}"),
        FunctionDoc.of(
            "Waits using the " + binding.name() + " waiter on " + operation.getId() + "."),
        false);
  }

  static Expression acceptorMap(
      BeamWaiterIndex.AcceptorInfo acceptor, String typesMod, SymbolProvider sp) {
    List<MapEntry> entries = new ArrayList<>();
    entries.add(MapEntry.atomKey("state", AtomExpr.of(acceptor.state())));
    if (acceptor.successExpected().isPresent()) {
      boolean expected = acceptor.successExpected().get();
      entries.add(MapEntry.atomKey("matcher", AtomExpr.of("success")));
      entries.add(MapEntry.atomKey("expected", BooleanExpr.of(expected)));
    } else if (acceptor.errorTypeName().isPresent()) {
      String errorType = acceptor.errorTypeName().get();
      entries.add(MapEntry.atomKey("matcher", AtomExpr.of("errorType")));
      if (acceptor.resolvedError().isPresent()) {
        String exception = sp.toSymbol(acceptor.resolvedError().get()).getName();
        entries.add(
            MapEntry.atomKey("expected", StructExpr.of(typesMod + "." + exception, List.of())));
      } else {
        entries.add(MapEntry.atomKey("expected", StringExpr.of(errorType)));
      }
    } else if (acceptor.pathMatcher().isPresent()) {
      BeamWaiterIndex.PathMatcherInfo pathMatcher = acceptor.pathMatcher().get();
      entries.add(MapEntry.atomKey("matcher", AtomExpr.of(acceptor.matcherKind())));
      entries.add(MapEntry.atomKey("path", pathExpr(pathMatcher.path())));
      entries.add(MapEntry.atomKey("comparator", AtomExpr.of(pathMatcher.comparator())));
      entries.add(MapEntry.atomKey("expected", StringExpr.of(pathMatcher.expected())));
    } else {
      entries.add(MapEntry.atomKey("matcher", AtomExpr.of(acceptor.matcherKind())));
    }
    return MapExpr.of(entries);
  }

  static List<Function> waitUntilHelperFunctions() {
    List<Function> functions = new ArrayList<>();
    functions.add(waitUntilArity3());
    functions.addAll(waitUntilArity5());
    functions.addAll(classify());
    functions.addAll(matchesAcceptor());
    functions.addAll(errorTypesMatch());
    functions.add(pathStringEquals());
    functions.addAll(pathValue());
    functions.addAll(stringEquals());
    return functions;
  }

  private static Function waitUntilArity3() {
    return defp(
        "wait_until",
        List.of(
            VariablePattern.of("step"),
            VariablePattern.of("acceptors"),
            VariablePattern.of("opts")),
        BlockExpr.of(
            List.of(
                MatchExpr.bind(
                    "max_attempts",
                    RemoteCallExpr.of(
                        "Keyword",
                        "get",
                        List.of(
                            Variable.of("opts"), AtomExpr.of("max_attempts"), IntegerExpr.of(25)))),
                MatchExpr.bind(
                    "min_delay",
                    RemoteCallExpr.of(
                        "Keyword",
                        "get",
                        List.of(
                            Variable.of("opts"),
                            AtomExpr.of("min_delay_ms"),
                            IntegerExpr.of(2000)))),
                MatchExpr.bind(
                    "max_delay",
                    RemoteCallExpr.of(
                        "Keyword",
                        "get",
                        List.of(
                            Variable.of("opts"),
                            AtomExpr.of("max_delay_ms"),
                            IntegerExpr.of(120_000)))),
                LocalCallExpr.of(
                    "wait_until",
                    List.of(
                        Variable.of("step"),
                        Variable.of("acceptors"),
                        Variable.of("max_attempts"),
                        Variable.of("min_delay"),
                        Variable.of("max_delay"))))),
        false);
  }

  private static List<Function> waitUntilArity5() {
    Expression pollCase =
        CaseExpr.of(
            LocalCallExpr.of("classify", List.of(Variable.of("acceptors"), Variable.of("result"))),
            List.of(
                Clause.of(
                    AtomPattern.of("success"),
                    TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("result")))),
                Clause.of(
                    AtomPattern.of("failure"),
                    TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("result")))),
                Clause.of(
                    AtomPattern.of("retry"),
                    ComparisonGuard.of(Variable.of("attempts"), "<=", IntegerExpr.of(1)),
                    TupleExpr.of(
                        List.of(AtomExpr.of("error"), AtomExpr.of("max_attempts_exceeded")))),
                Clause.of(
                    AtomPattern.of("retry"),
                    BlockExpr.of(
                        List.of(
                            RemoteCallExpr.of("Process", "sleep", List.of(Variable.of("delay"))),
                            MatchExpr.bind(
                                "next_delay",
                                RemoteCallExpr.of(
                                    "Kernel",
                                    "min",
                                    List.of(
                                        InfixExpr.of(Variable.of("delay"), "*", IntegerExpr.of(2)),
                                        Variable.of("max_delay"))),
                                LocalCallExpr.of(
                                    "wait_until",
                                    List.of(
                                        Variable.of("step"),
                                        Variable.of("acceptors"),
                                        InfixExpr.of(
                                            Variable.of("attempts"), "-", IntegerExpr.of(1)),
                                        Variable.of("next_delay"),
                                        Variable.of("max_delay")))))))));
    return List.of(
        defp(
            "wait_until",
            List.of(
                VariablePattern.of("_step"),
                VariablePattern.of("_acceptors"),
                IntegerPattern.of(0),
                VariablePattern.of("_delay"),
                VariablePattern.of("_max_delay")),
            TupleExpr.of(List.of(AtomExpr.of("error"), AtomExpr.of("max_attempts_exceeded"))),
            true),
        defp(
            "wait_until",
            List.of(
                VariablePattern.of("step"),
                VariablePattern.of("acceptors"),
                VariablePattern.of("attempts"),
                VariablePattern.of("delay"),
                VariablePattern.of("max_delay")),
            BlockExpr.of(
                List.of(
                    MatchExpr.bind(
                        "result", DotCallExpr.of(Variable.of("step"), "()", List.of()), pollCase))),
            false));
  }

  private static List<Function> classify() {
    return List.of(
        defp(
            "classify",
            List.of(ListPattern.of(List.of()), VariablePattern.of("_result")),
            AtomExpr.of("retry"),
            true),
        defp(
            "classify",
            List.of(
                ConsListPattern.of(VariablePattern.of("acceptor"), VariablePattern.of("rest")),
                VariablePattern.of("result")),
            IfExpr.of(
                LocalCallExpr.of(
                    "matches_acceptor?", List.of(Variable.of("acceptor"), Variable.of("result"))),
                RemoteCallExpr.of(
                    "Map", "get", List.of(Variable.of("acceptor"), AtomExpr.of("state"))),
                LocalCallExpr.of("classify", List.of(Variable.of("rest"), Variable.of("result"))),
                false),
            false));
  }

  private static List<Function> matchesAcceptor() {
    List<Function> functions = new ArrayList<>();
    functions.add(
        defp(
            "matches_acceptor?",
            List.of(
                MapPattern.of(
                    List.of(
                        MapPatternEntry.of(AtomExpr.of("matcher"), AtomPattern.of("success")),
                        MapPatternEntry.of(
                            AtomExpr.of("expected"), VariablePattern.of("expected")))),
                TuplePattern.of(List.of(AtomPattern.of("ok"), WildcardPattern.of()))),
            ComparisonGuard.of(Variable.of("expected"), "==", BooleanExpr.of(true)),
            BooleanExpr.of(true),
            true));
    functions.add(
        defp(
            "matches_acceptor?",
            List.of(
                MapPattern.of(
                    List.of(
                        MapPatternEntry.of(AtomExpr.of("matcher"), AtomPattern.of("success")),
                        MapPatternEntry.of(
                            AtomExpr.of("expected"), VariablePattern.of("expected")))),
                TuplePattern.of(List.of(AtomPattern.of("error"), WildcardPattern.of()))),
            ComparisonGuard.of(Variable.of("expected"), "==", BooleanExpr.of(false)),
            BooleanExpr.of(true),
            true));
    functions.add(
        defp(
            "matches_acceptor?",
            List.of(
                MapPattern.of(
                    List.of(
                        MapPatternEntry.of(AtomExpr.of("matcher"), AtomPattern.of("errorType")),
                        MapPatternEntry.of(
                            AtomExpr.of("expected"), VariablePattern.of("expected")))),
                TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("got")))),
            LocalCallExpr.of(
                "error_types_match?", List.of(Variable.of("expected"), Variable.of("got"))),
            true));
    functions.add(
        defp(
            "matches_acceptor?",
            List.of(
                MapPattern.of(
                    List.of(
                        MapPatternEntry.of(AtomExpr.of("matcher"), AtomPattern.of("output")),
                        MapPatternEntry.of(AtomExpr.of("path"), VariablePattern.of("path")),
                        MapPatternEntry.of(
                            AtomExpr.of("comparator"), AtomPattern.of("stringEquals")),
                        MapPatternEntry.of(
                            AtomExpr.of("expected"), VariablePattern.of("expected")))),
                TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("output")))),
            LocalCallExpr.of(
                "path_string_equals?",
                List.of(Variable.of("path"), Variable.of("expected"), Variable.of("output"))),
            true));
    functions.add(
        defp(
            "matches_acceptor?",
            List.of(
                MapPattern.of(
                    List.of(
                        MapPatternEntry.of(AtomExpr.of("matcher"), AtomPattern.of("inputOutput")),
                        MapPatternEntry.of(AtomExpr.of("path"), VariablePattern.of("path")),
                        MapPatternEntry.of(
                            AtomExpr.of("comparator"), AtomPattern.of("stringEquals")),
                        MapPatternEntry.of(
                            AtomExpr.of("expected"), VariablePattern.of("expected")))),
                TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("output")))),
            LocalCallExpr.of(
                "path_string_equals?",
                List.of(Variable.of("path"), Variable.of("expected"), Variable.of("output"))),
            true));
    functions.add(
        defp(
            "matches_acceptor?",
            List.of(WildcardPattern.of(), WildcardPattern.of()),
            BooleanExpr.of(false),
            true));
    return functions;
  }

  private static List<Function> errorTypesMatch() {
    return List.of(
        defp(
            "error_types_match?",
            List.of(VariablePattern.of("expected"), VariablePattern.of("_got")),
            IsTypeGuard.of("is_binary", "expected"),
            BooleanExpr.of(true),
            true),
        defp(
            "error_types_match?",
            List.of(
                MapPattern.of(
                    List.of(
                        MapPatternEntry.of(
                            AtomExpr.of("__struct__"), VariablePattern.of("struct")))),
                MapPattern.of(
                    List.of(
                        MapPatternEntry.of(
                            AtomExpr.of("__struct__"), VariablePattern.of("struct"))))),
            BooleanExpr.of(true),
            true),
        defp(
            "error_types_match?",
            List.of(VariablePattern.of("expected"), VariablePattern.of("got")),
            InfixExpr.of(Variable.of("expected"), "==", Variable.of("got")),
            true));
  }

  private static Function pathStringEquals() {
    return defp(
        "path_string_equals?",
        List.of(
            VariablePattern.of("path"),
            VariablePattern.of("expected"),
            VariablePattern.of("output")),
        CaseExpr.of(
            LocalCallExpr.of("path_value", List.of(Variable.of("path"), Variable.of("output"))),
            List.of(
                Clause.of(NilPattern.of(), BooleanExpr.of(false)),
                Clause.of(
                    VariablePattern.of("value"),
                    LocalCallExpr.of(
                        "string_equals?",
                        List.of(Variable.of("value"), Variable.of("expected")))))),
        false);
  }

  private static List<Function> pathValue() {
    return List.of(
        defp(
            "path_value",
            List.of(VariablePattern.of("path"), VariablePattern.of("_value")),
            IsTypeGuard.of("is_binary", "path"),
            NilExpr.of(),
            true),
        defp(
            "path_value",
            List.of(ListPattern.of(List.of()), VariablePattern.of("value")),
            Variable.of("value"),
            true),
        defp(
            "path_value",
            List.of(
                ConsListPattern.of(VariablePattern.of("key"), VariablePattern.of("rest")),
                VariablePattern.of("value")),
            IsTypeGuard.of("is_map", "value"),
            CaseExpr.of(
                RemoteCallExpr.of("Map", "get", List.of(Variable.of("value"), Variable.of("key"))),
                List.of(
                    Clause.of(NilPattern.of(), NilExpr.of()),
                    Clause.of(
                        VariablePattern.of("next"),
                        LocalCallExpr.of(
                            "path_value", List.of(Variable.of("rest"), Variable.of("next")))))),
            false),
        defp(
            "path_value",
            List.of(
                ConsListPattern.of(VariablePattern.of("key"), VariablePattern.of("rest")),
                VariablePattern.of("value")),
            IsTypeGuard.of("is_struct", "value"),
            CaseExpr.of(
                RemoteCallExpr.of(
                    "Map",
                    "get",
                    List.of(
                        RemoteCallExpr.of("Map", "from_struct", List.of(Variable.of("value"))),
                        Variable.of("key"))),
                List.of(
                    Clause.of(NilPattern.of(), NilExpr.of()),
                    Clause.of(
                        VariablePattern.of("next"),
                        LocalCallExpr.of(
                            "path_value", List.of(Variable.of("rest"), Variable.of("next")))))),
            false),
        defp(
            "path_value", List.of(WildcardPattern.of(), WildcardPattern.of()), NilExpr.of(), true));
  }

  private static List<Function> stringEquals() {
    return List.of(
        defp(
            "string_equals?",
            List.of(VariablePattern.of("left"), VariablePattern.of("right")),
            IsTypeGuard.of("is_atom", "left"),
            IsTypeGuard.of("is_binary", "right"),
            InfixExpr.of(
                RemoteCallExpr.of(
                    "String",
                    "upcase",
                    List.of(RemoteCallExpr.of("Atom", "to_string", List.of(Variable.of("left"))))),
                "==",
                RemoteCallExpr.of("String", "upcase", List.of(Variable.of("right")))),
            true),
        defp(
            "string_equals?",
            List.of(VariablePattern.of("left"), VariablePattern.of("right")),
            IsTypeGuard.of("is_binary", "left"),
            IsTypeGuard.of("is_binary", "right"),
            InfixExpr.of(
                RemoteCallExpr.of("String", "upcase", List.of(Variable.of("left"))),
                "==",
                RemoteCallExpr.of("String", "upcase", List.of(Variable.of("right")))),
            true),
        defp(
            "string_equals?",
            List.of(VariablePattern.of("left"), VariablePattern.of("right")),
            InfixExpr.of(Variable.of("left"), "==", Variable.of("right")),
            true));
  }

  static Expression pathExpr(String path) {
    if (BeamWaiterPaths.isSimpleDottedPath(path)) {
      List<Expression> segments =
          Arrays.stream(path.split("\\."))
              .map(segment -> AtomExpr.of(BeamNameUtils.toSnakeCase(segment)))
              .collect(Collectors.toList());
      return ListExpr.of(segments);
    }
    return StringExpr.of(path);
  }

  private static String waitFunctionName(String waiterName) {
    return "wait_" + BeamNameUtils.toSnakeCase(waiterName);
  }

  private static Function defp(
      String name, List<Pattern> params, Expression body, boolean oneLiner) {
    return Function.of(name, true, List.of(FunctionHead.of(params)), body, null, null, oneLiner);
  }

  private static Function defp(
      String name, List<Pattern> params, Guard guard, Expression body, boolean oneLiner) {
    return Function.of(
        name, true, List.of(FunctionHead.of(params, guard)), body, null, null, oneLiner);
  }

  private static Function defp(
      String name,
      List<Pattern> params,
      Guard guard1,
      Guard guard2,
      Expression body,
      boolean oneLiner) {
    return Function.of(
        name,
        true,
        List.of(FunctionHead.of(params, AndGuard.of(List.of(guard1, guard2)))),
        body,
        null,
        null,
        oneLiner);
  }
}
