package io.smithy.beam.erlang;

import io.beam.dsl.erlang.AndGuard;
import io.beam.dsl.erlang.AtomExpr;
import io.beam.dsl.erlang.AtomPattern;
import io.beam.dsl.erlang.BinaryExpr;
import io.beam.dsl.erlang.BlockExpr;
import io.beam.dsl.erlang.CaseExpr;
import io.beam.dsl.erlang.Clause;
import io.beam.dsl.erlang.Edoc;
import io.beam.dsl.erlang.Expression;
import io.beam.dsl.erlang.ExpressionGuard;
import io.beam.dsl.erlang.Fun;
import io.beam.dsl.erlang.FunClause;
import io.beam.dsl.erlang.Function;
import io.beam.dsl.erlang.FunctionClause;
import io.beam.dsl.erlang.InfixExpr;
import io.beam.dsl.erlang.IntegerExpr;
import io.beam.dsl.erlang.IsTypeGuard;
import io.beam.dsl.erlang.ListExpr;
import io.beam.dsl.erlang.ListPattern;
import io.beam.dsl.erlang.LocalCallExpr;
import io.beam.dsl.erlang.MapEntry;
import io.beam.dsl.erlang.MapExpr;
import io.beam.dsl.erlang.MapPattern;
import io.beam.dsl.erlang.MapPatternEntry;
import io.beam.dsl.erlang.MatchExpr;
import io.beam.dsl.erlang.Module;
import io.beam.dsl.erlang.RecordExpr;
import io.beam.dsl.erlang.RemoteCallExpr;
import io.beam.dsl.erlang.TupleExpr;
import io.beam.dsl.erlang.TuplePattern;
import io.beam.dsl.erlang.Variable;
import io.beam.dsl.erlang.VariablePattern;
import io.beam.dsl.erlang.WildcardPattern;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamWaiterIndex;
import io.smithy.beam.core.BeamWaiterPaths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;

final class ErlangWaiterIr {
  private ErlangWaiterIr() {}

  static Module waitersModule(
      String waitersMod,
      String typesHeaderFile,
      String clientMod,
      BeamWaiterIndex index,
      SymbolProvider sp,
      Model model,
      ServiceShape service) {
    List<String> exports = new ArrayList<>();
    for (BeamWaiterIndex.WaiterBinding binding : index.bindings()) {
      exports.add(waitFunctionName(binding.name()) + "/3");
    }

    List<Function> functions = new ArrayList<>();
    for (BeamWaiterIndex.WaiterBinding binding : index.bindings()) {
      functions.add(waiterFunction(index, binding, clientMod, sp));
    }
    functions.addAll(waitUntilHelperFunctions(model, service, sp));

    return Module.of(
        waitersMod,
        functions,
        List.of("Generated waiters for " + service.getId() + "."),
        null,
        List.of(typesHeaderFile),
        null,
        exports);
  }

  static Function waiterFunction(
      BeamWaiterIndex index,
      BeamWaiterIndex.WaiterBinding binding,
      String clientMod,
      SymbolProvider sp) {
    OperationShape operation = binding.operation();
    Symbol opSym = sp.toSymbol(operation);
    String fn = waitFunctionName(binding.name());
    List<BeamWaiterIndex.AcceptorInfo> acceptors = index.acceptors(binding);

    List<Expression> acceptorMaps = new ArrayList<>();
    for (BeamWaiterIndex.AcceptorInfo acceptor : acceptors) {
      acceptorMaps.add(acceptorMap(acceptor, sp));
    }

    return Function.of(
        fn,
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Client"),
                    VariablePattern.of("Input"),
                    VariablePattern.of("Opts")),
                BlockExpr.commaSeparated(
                    List.of(
                        MatchExpr.bindValue("Acceptors", ListExpr.of(acceptorMaps)),
                        MatchExpr.bindValue(
                            "WaitOpts",
                            RemoteCallExpr.of(
                                "maps",
                                "merge",
                                List.of(
                                    MapExpr.of(
                                        List.of(
                                            MapEntry.of(
                                                AtomExpr.of("min_delay_ms"),
                                                IntegerExpr.of(binding.minDelaySeconds() * 1000L)),
                                            MapEntry.of(
                                                AtomExpr.of("max_delay_ms"),
                                                IntegerExpr.of(
                                                    binding.maxDelaySeconds() * 1000L)))),
                                    Variable.of("Opts")))),
                        LocalCallExpr.of(
                            "wait_until",
                            List.of(
                                Fun.of(
                                    List.of(
                                        FunClause.of(
                                            List.of(),
                                            RemoteCallExpr.of(
                                                clientMod,
                                                opSym.getName(),
                                                List.of(
                                                    Variable.of("Client"),
                                                    Variable.of("Input")))))),
                                Variable.of("Acceptors"),
                                Variable.of("WaitOpts")))),
                    false))),
        null,
        Edoc.of("Waits using the " + binding.name() + " waiter on " + operation.getId() + "."));
  }

  static Expression acceptorMap(BeamWaiterIndex.AcceptorInfo acceptor, SymbolProvider sp) {
    List<MapEntry> entries = new ArrayList<>();
    entries.add(MapEntry.of(AtomExpr.of("state"), AtomExpr.of(acceptor.state())));
    if (acceptor.successExpected().isPresent()) {
      boolean expected = acceptor.successExpected().get();
      entries.add(MapEntry.of(AtomExpr.of("matcher"), AtomExpr.of("success")));
      entries.add(
          MapEntry.of(
              AtomExpr.of("expected"), expected ? AtomExpr.of("true") : AtomExpr.of("false")));
    } else if (acceptor.errorTypeName().isPresent()) {
      String errorType = acceptor.errorTypeName().get();
      entries.add(MapEntry.of(AtomExpr.of("matcher"), AtomExpr.of("errorType")));
      if (acceptor.resolvedError().isPresent()) {
        String record = recordName(sp.toSymbol(acceptor.resolvedError().get()));
        entries.add(MapEntry.of(AtomExpr.of("expected"), RecordExpr.of(record, List.of())));
      } else {
        entries.add(MapEntry.of(AtomExpr.of("expected"), BinaryExpr.of(errorType)));
      }
    } else if (acceptor.pathMatcher().isPresent()) {
      BeamWaiterIndex.PathMatcherInfo pathMatcher = acceptor.pathMatcher().get();
      entries.add(MapEntry.of(AtomExpr.of("matcher"), AtomExpr.of(acceptor.matcherKind())));
      entries.add(MapEntry.of(AtomExpr.of("path"), pathExpr(pathMatcher.path())));
      entries.add(MapEntry.of(AtomExpr.of("comparator"), AtomExpr.of(pathMatcher.comparator())));
      entries.add(MapEntry.of(AtomExpr.of("expected"), BinaryExpr.of(pathMatcher.expected())));
    } else {
      entries.add(MapEntry.of(AtomExpr.of("matcher"), AtomExpr.of(acceptor.matcherKind())));
    }
    return MapExpr.of(entries);
  }

  static List<Function> waitUntilHelperFunctions(
      Model model, ServiceShape service, SymbolProvider sp) {
    List<Function> functions = new ArrayList<>();
    functions.add(waitUntilArity3());
    functions.add(waitUntilArity5());
    functions.add(classify());
    functions.add(matchesAcceptor());
    functions.add(errorTypesMatch());
    functions.add(pathStringEquals());
    functions.add(pathValue());
    functions.addAll(recordFieldsFunctions(model, service, sp));
    functions.add(recordField());
    functions.add(stringEquals());
    return functions;
  }

  private static Function waitUntilArity3() {
    return Function.of(
        "wait_until",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Fun"),
                    VariablePattern.of("Acceptors"),
                    VariablePattern.of("Opts")),
                BlockExpr.commaSeparated(
                    List.of(
                        MatchExpr.bindValue(
                            "MaxAttempts",
                            RemoteCallExpr.of(
                                "maps",
                                "get",
                                List.of(
                                    AtomExpr.of("max_attempts"),
                                    Variable.of("Opts"),
                                    IntegerExpr.of(25)))),
                        MatchExpr.bindValue(
                            "MinDelay",
                            RemoteCallExpr.of(
                                "maps",
                                "get",
                                List.of(
                                    AtomExpr.of("min_delay_ms"),
                                    Variable.of("Opts"),
                                    IntegerExpr.of(2000)))),
                        MatchExpr.bindValue(
                            "MaxDelay",
                            RemoteCallExpr.of(
                                "maps",
                                "get",
                                List.of(
                                    AtomExpr.of("max_delay_ms"),
                                    Variable.of("Opts"),
                                    IntegerExpr.of(120000)))),
                        LocalCallExpr.of(
                            "wait_until",
                            List.of(
                                Variable.of("Fun"),
                                Variable.of("Acceptors"),
                                Variable.of("MaxAttempts"),
                                Variable.of("MinDelay"),
                                Variable.of("MaxDelay")))),
                    false))));
  }

  private static Function waitUntilArity5() {
    Expression pollCase =
        CaseExpr.of(
            LocalCallExpr.of("classify", List.of(Variable.of("Acceptors"), Variable.of("Result"))),
            List.of(
                Clause.of(
                    AtomPattern.of("success"),
                    TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("Result")))),
                Clause.of(
                    AtomPattern.of("failure"),
                    TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("Result")))),
                Clause.of(
                    AtomPattern.of("retry"),
                    ExpressionGuard.of(
                        InfixExpr.of(Variable.of("Attempts"), "=<", IntegerExpr.of(1))),
                    TupleExpr.of(
                        List.of(AtomExpr.of("error"), AtomExpr.of("max_attempts_exceeded")))),
                Clause.of(
                    AtomPattern.of("retry"),
                    BlockExpr.commaSeparated(
                        List.of(
                            RemoteCallExpr.of("timer", "sleep", List.of(Variable.of("Delay"))),
                            MatchExpr.bindValue(
                                "NextDelay",
                                RemoteCallExpr.of(
                                    "erlang",
                                    "min",
                                    List.of(
                                        InfixExpr.of(Variable.of("Delay"), "*", IntegerExpr.of(2)),
                                        Variable.of("MaxDelay")))),
                            LocalCallExpr.of(
                                "wait_until",
                                List.of(
                                    Variable.of("Fun"),
                                    Variable.of("Acceptors"),
                                    InfixExpr.of(Variable.of("Attempts"), "-", IntegerExpr.of(1)),
                                    Variable.of("NextDelay"),
                                    Variable.of("MaxDelay")))),
                        false))));
    return Function.of(
        "wait_until",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("_Fun"),
                    VariablePattern.of("_Acceptors"),
                    VariablePattern.of("0"),
                    VariablePattern.of("_Delay"),
                    VariablePattern.of("_MaxDelay")),
                TupleExpr.of(List.of(AtomExpr.of("error"), AtomExpr.of("max_attempts_exceeded")))),
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Fun"),
                    VariablePattern.of("Acceptors"),
                    VariablePattern.of("Attempts"),
                    VariablePattern.of("Delay"),
                    VariablePattern.of("MaxDelay")),
                BlockExpr.commaSeparated(
                    List.of(
                        MatchExpr.bindValue("Result", LocalCallExpr.of("Fun", List.of())),
                        pollCase),
                    false))));
  }

  private static Function classify() {
    return Function.of(
        "classify",
        List.of(
            FunctionClause.of(
                List.of(ListPattern.of(List.of()), VariablePattern.of("_Result")),
                AtomExpr.of("retry")),
            FunctionClause.of(
                List.of(
                    ListPattern.cons(VariablePattern.of("Acceptor"), VariablePattern.of("Rest")),
                    VariablePattern.of("Result")),
                CaseExpr.of(
                    LocalCallExpr.of(
                        "matches_acceptor",
                        List.of(Variable.of("Acceptor"), Variable.of("Result"))),
                    List.of(
                        Clause.of(
                            AtomPattern.of("true"),
                            RemoteCallExpr.of(
                                "maps",
                                "get",
                                List.of(AtomExpr.of("state"), Variable.of("Acceptor")))),
                        Clause.of(
                            AtomPattern.of("false"),
                            LocalCallExpr.of(
                                "classify",
                                List.of(Variable.of("Rest"), Variable.of("Result")))))))));
  }

  private static Function matchesAcceptor() {
    return Function.of(
        "matches_acceptor",
        List.of(
            FunctionClause.of(
                List.of(
                    MapPattern.of(
                        List.of(
                            MapPatternEntry.of(
                                AtomExpr.of("matcher"), AtomPattern.of("success"), true),
                            MapPatternEntry.of(
                                AtomExpr.of("expected"), AtomPattern.of("true"), true))),
                    TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("_")))),
                AtomExpr.of("true")),
            FunctionClause.of(
                List.of(
                    MapPattern.of(
                        List.of(
                            MapPatternEntry.of(
                                AtomExpr.of("matcher"), AtomPattern.of("success"), true),
                            MapPatternEntry.of(
                                AtomExpr.of("expected"), AtomPattern.of("false"), true))),
                    TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("_")))),
                AtomExpr.of("true")),
            FunctionClause.of(
                List.of(
                    MapPattern.of(
                        List.of(
                            MapPatternEntry.of(
                                AtomExpr.of("matcher"), AtomPattern.of("errorType"), true),
                            MapPatternEntry.of(
                                AtomExpr.of("expected"), VariablePattern.of("Expected"), true))),
                    TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("Got")))),
                LocalCallExpr.of(
                    "error_types_match", List.of(Variable.of("Expected"), Variable.of("Got")))),
            FunctionClause.of(
                List.of(
                    MapPattern.of(
                        List.of(
                            MapPatternEntry.of(
                                AtomExpr.of("matcher"), AtomPattern.of("output"), true),
                            MapPatternEntry.of(
                                AtomExpr.of("path"), VariablePattern.of("Path"), true),
                            MapPatternEntry.of(
                                AtomExpr.of("comparator"), AtomPattern.of("stringEquals"), true),
                            MapPatternEntry.of(
                                AtomExpr.of("expected"), VariablePattern.of("Expected"), true))),
                    TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("Output")))),
                LocalCallExpr.of(
                    "path_string_equals",
                    List.of(Variable.of("Path"), Variable.of("Expected"), Variable.of("Output")))),
            FunctionClause.of(
                List.of(
                    MapPattern.of(
                        List.of(
                            MapPatternEntry.of(
                                AtomExpr.of("matcher"), AtomPattern.of("inputOutput"), true),
                            MapPatternEntry.of(
                                AtomExpr.of("path"), VariablePattern.of("Path"), true),
                            MapPatternEntry.of(
                                AtomExpr.of("comparator"), AtomPattern.of("stringEquals"), true),
                            MapPatternEntry.of(
                                AtomExpr.of("expected"), VariablePattern.of("Expected"), true))),
                    TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("Output")))),
                LocalCallExpr.of(
                    "path_string_equals",
                    List.of(Variable.of("Path"), Variable.of("Expected"), Variable.of("Output")))),
            FunctionClause.of(
                List.of(WildcardPattern.of(), WildcardPattern.of()), AtomExpr.of("false"))));
  }

  private static Function errorTypesMatch() {
    return Function.of(
        "error_types_match",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Expected"), VariablePattern.of("_Got")),
                IsTypeGuard.of("binary", Variable.of("Expected")),
                AtomExpr.of("true")),
            FunctionClause.of(
                List.of(VariablePattern.of("Expected"), VariablePattern.of("Got")),
                AndGuard.of(
                    List.of(
                        IsTypeGuard.of("tuple", Variable.of("Expected")),
                        IsTypeGuard.of("tuple", Variable.of("Got")))),
                InfixExpr.of(
                    LocalCallExpr.of(
                        "element", List.of(IntegerExpr.of(1), Variable.of("Expected"))),
                    "=:=",
                    LocalCallExpr.of("element", List.of(IntegerExpr.of(1), Variable.of("Got"))))),
            FunctionClause.of(
                List.of(VariablePattern.of("Expected"), VariablePattern.of("Got")),
                InfixExpr.of(Variable.of("Expected"), "=:=", Variable.of("Got")))));
  }

  private static Function pathStringEquals() {
    return Function.of(
        "path_string_equals",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Path"),
                    VariablePattern.of("Expected"),
                    VariablePattern.of("Output")),
                CaseExpr.of(
                    LocalCallExpr.of(
                        "path_value", List.of(Variable.of("Path"), Variable.of("Output"))),
                    List.of(
                        Clause.of(AtomPattern.of("undefined"), AtomExpr.of("false")),
                        Clause.of(
                            VariablePattern.of("Value"),
                            LocalCallExpr.of(
                                "string_equals",
                                List.of(Variable.of("Value"), Variable.of("Expected")))))))));
  }

  private static Function pathValue() {
    return Function.of(
        "path_value",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Path"), VariablePattern.of("_Value")),
                IsTypeGuard.of("binary", Variable.of("Path")),
                AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(ListPattern.of(List.of()), VariablePattern.of("Value")),
                Variable.of("Value")),
            FunctionClause.of(
                List.of(
                    ListPattern.cons(VariablePattern.of("Key"), VariablePattern.of("Rest")),
                    VariablePattern.of("Value")),
                IsTypeGuard.of("map", Variable.of("Value")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "maps",
                        "get",
                        List.of(
                            Variable.of("Key"), Variable.of("Value"), AtomExpr.of("undefined"))),
                    List.of(
                        Clause.of(AtomPattern.of("undefined"), AtomExpr.of("undefined")),
                        Clause.of(
                            VariablePattern.of("Next"),
                            LocalCallExpr.of(
                                "path_value",
                                List.of(Variable.of("Rest"), Variable.of("Next"))))))),
            FunctionClause.of(
                List.of(
                    ListPattern.cons(VariablePattern.of("Key"), VariablePattern.of("Rest")),
                    VariablePattern.of("Value")),
                AndGuard.of(
                    List.of(
                        IsTypeGuard.of("tuple", Variable.of("Value")),
                        ExpressionGuard.of(
                            InfixExpr.of(
                                LocalCallExpr.of("tuple_size", List.of(Variable.of("Value"))),
                                ">=",
                                IntegerExpr.of(1))))),
                CaseExpr.of(
                    LocalCallExpr.of(
                        "record_field", List.of(Variable.of("Value"), Variable.of("Key"))),
                    List.of(
                        Clause.of(AtomPattern.of("undefined"), AtomExpr.of("undefined")),
                        Clause.of(
                            VariablePattern.of("Next"),
                            LocalCallExpr.of(
                                "path_value",
                                List.of(Variable.of("Rest"), Variable.of("Next"))))))),
            FunctionClause.of(
                List.of(WildcardPattern.of(), WildcardPattern.of()), AtomExpr.of("undefined"))));
  }

  private static List<Function> recordFieldsFunctions(
      Model model, ServiceShape service, SymbolProvider sp) {
    Set<Shape> closure = new Walker(model).walkShapes(service);
    List<StructureShape> structures =
        closure.stream()
            .filter(shape -> shape instanceof StructureShape)
            .map(shape -> (StructureShape) shape)
            .filter(shape -> !shape.getId().getNamespace().equals("smithy.api"))
            .sorted(Comparator.comparing(s -> s.getId().toString()))
            .collect(Collectors.toList());

    List<FunctionClause> clauses = new ArrayList<>();
    for (StructureShape structure : structures) {
      String record = recordName(sp.toSymbol(structure));
      clauses.add(
          FunctionClause.of(
              List.of(AtomPattern.of(record)),
              LocalCallExpr.of(
                  "record_info", List.of(AtomExpr.of("fields"), AtomExpr.of(record)))));
    }
    clauses.add(FunctionClause.of(List.of(WildcardPattern.of()), AtomExpr.of("undefined")));
    return List.of(Function.of("record_fields", clauses));
  }

  private static Function recordField() {
    Expression keyfindCase =
        CaseExpr.of(
            LocalCallExpr.of(
                "lists:keyfind",
                List.of(
                    Variable.of("Field"),
                    IntegerExpr.of(1),
                    RemoteCallExpr.of(
                        "lists", "zip", List.of(Variable.of("Fields"), Variable.of("Values"))))),
            List.of(
                Clause.of(
                    TuplePattern.of(List.of(VariablePattern.of("Field"), VariablePattern.of("V"))),
                    Variable.of("V")),
                Clause.of(AtomPattern.of("false"), AtomExpr.of("undefined"))));

    Expression valuesBind =
        MatchExpr.bind(
            "Values",
            LocalCallExpr.of(
                "tl", List.of(LocalCallExpr.of("tuple_to_list", List.of(Variable.of("Record"))))),
            keyfindCase);

    Expression recordFieldsCase =
        CaseExpr.of(
            LocalCallExpr.of("record_fields", List.of(Variable.of("Tag"))),
            List.of(
                Clause.of(
                    VariablePattern.of("Fields"),
                    IsTypeGuard.of("list", Variable.of("Fields")),
                    valuesBind),
                Clause.of(AtomPattern.of("undefined"), AtomExpr.of("undefined"))));

    Expression tagBind =
        MatchExpr.bind(
            "Tag",
            LocalCallExpr.of("element", List.of(IntegerExpr.of(1), Variable.of("Record"))),
            recordFieldsCase);

    return Function.of(
        "record_field",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Record"), VariablePattern.of("Field")),
                AndGuard.of(
                    List.of(
                        IsTypeGuard.of("tuple", Variable.of("Record")),
                        ExpressionGuard.of(
                            InfixExpr.of(
                                LocalCallExpr.of("tuple_size", List.of(Variable.of("Record"))),
                                ">=",
                                IntegerExpr.of(1))))),
                tagBind)));
  }

  private static Function stringEquals() {
    return Function.of(
        "string_equals",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("V"), VariablePattern.of("Expected")),
                AndGuard.of(
                    List.of(
                        IsTypeGuard.of("atom", Variable.of("V")),
                        IsTypeGuard.of("binary", Variable.of("Expected")))),
                InfixExpr.of(
                    RemoteCallExpr.of(
                        "string",
                        "uppercase",
                        List.of(
                            LocalCallExpr.of(
                                "atom_to_binary", List.of(Variable.of("V"), AtomExpr.of("utf8"))))),
                    "=:=",
                    RemoteCallExpr.of("string", "uppercase", List.of(Variable.of("Expected"))))),
            FunctionClause.of(
                List.of(VariablePattern.of("V"), VariablePattern.of("Expected")),
                AndGuard.of(
                    List.of(
                        IsTypeGuard.of("binary", Variable.of("V")),
                        IsTypeGuard.of("binary", Variable.of("Expected")))),
                InfixExpr.of(
                    RemoteCallExpr.of("string", "uppercase", List.of(Variable.of("V"))),
                    "=:=",
                    RemoteCallExpr.of("string", "uppercase", List.of(Variable.of("Expected"))))),
            FunctionClause.of(
                List.of(VariablePattern.of("V"), VariablePattern.of("Expected")),
                InfixExpr.of(Variable.of("V"), "=:=", Variable.of("Expected")))));
  }

  static Expression pathExpr(String path) {
    if (BeamWaiterPaths.isSimpleDottedPath(path)) {
      List<Expression> segments =
          Arrays.stream(path.split("\\."))
              .map(segment -> AtomExpr.of(BeamNameUtils.toSnakeCase(segment)))
              .collect(Collectors.toList());
      return ListExpr.of(segments);
    }
    return BinaryExpr.of(path);
  }

  private static String waitFunctionName(String waiterName) {
    return "wait_" + BeamNameUtils.toSnakeCase(waiterName);
  }

  private static String recordName(Symbol symbol) {
    return symbol.getName().replace("()", "");
  }
}
