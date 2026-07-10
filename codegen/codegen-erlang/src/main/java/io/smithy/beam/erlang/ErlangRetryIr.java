package io.smithy.beam.erlang;

import io.beam.ir.erlang.ApplyExpr;
import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.BlockExpr;
import io.beam.ir.erlang.CaseExpr;
import io.beam.ir.erlang.Clause;
import io.beam.ir.erlang.Edoc;
import io.beam.ir.erlang.Expression;
import io.beam.ir.erlang.ExpressionGuard;
import io.beam.ir.erlang.Fun;
import io.beam.ir.erlang.FunClause;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.InfixExpr;
import io.beam.ir.erlang.IntegerExpr;
import io.beam.ir.erlang.IntegerPattern;
import io.beam.ir.erlang.LocalCallExpr;
import io.beam.ir.erlang.MatchExpr;
import io.beam.ir.erlang.MatchPattern;
import io.beam.ir.erlang.RecordPattern;
import io.beam.ir.erlang.RemoteCallExpr;
import io.beam.ir.erlang.Spec;
import io.beam.ir.erlang.TuplePattern;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import io.beam.ir.erlang.WildcardPattern;
import io.smithy.beam.core.BeamRetryIndex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;

final class ErlangRetryIr {
  private ErlangRetryIr() {}

  static boolean serviceHasRetryableErrors(Model model, ServiceShape service) {
    return !retryableErrors(model, service).isEmpty();
  }

  static List<Function> clientPredicateFunctions(
      Model model, ServiceShape service, SymbolProvider sp) {
    List<StructureShape> retryableErrors = retryableErrors(model, service);
    if (retryableErrors.isEmpty()) {
      return List.of();
    }
    List<StructureShape> modeledErrors = modeledErrors(model, service);
    return List.of(
        shouldRetry(retryableErrors, sp),
        retryable(modeledErrors, sp),
        throttling(modeledErrors, sp));
  }

  static List<Function> withRetryFunctions() {
    return List.of(withRetryOuter(), withRetryInner());
  }

  private static Expression defaultShouldRetryFun() {
    return Fun.of(List.of(FunClause.of(List.of(WildcardPattern.of()), AtomExpr.of("false"))));
  }

  private static Function withRetryOuter() {
    return Function.of(
        "with_retry",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Fun"), VariablePattern.of("Opts")),
                MatchExpr.bind(
                    "Max",
                    RemoteCallExpr.of(
                        "maps",
                        "get",
                        List.of(
                            AtomExpr.of("max_attempts"), Variable.of("Opts"), IntegerExpr.of(3))),
                    MatchExpr.bind(
                        "Base",
                        RemoteCallExpr.of(
                            "maps",
                            "get",
                            List.of(
                                AtomExpr.of("base_delay_ms"),
                                Variable.of("Opts"),
                                IntegerExpr.of(100))),
                        MatchExpr.bind(
                            "ShouldRetry",
                            RemoteCallExpr.of(
                                "maps",
                                "get",
                                List.of(
                                    AtomExpr.of("should_retry"),
                                    Variable.of("Opts"),
                                    defaultShouldRetryFun())),
                            LocalCallExpr.of(
                                "with_retry",
                                List.of(
                                    Variable.of("Fun"),
                                    Variable.of("Max"),
                                    Variable.of("Base"),
                                    IntegerExpr.of(1),
                                    Variable.of("ShouldRetry")))))))),
        Spec.of("with_retry(fun(() -> term()), map()) -> term()"),
        Edoc.of(
            "Invokes {@code Fun} with exponential backoff when a retryable error is returned."));
  }

  private static Function withRetryInner() {
    Expression sleepCall =
        RemoteCallExpr.of(
            "timer",
            "sleep",
            List.of(
                LocalCallExpr.of(
                    "trunc",
                    List.of(
                        InfixExpr.of(
                            Variable.of("Base"),
                            "*",
                            RemoteCallExpr.of(
                                "math",
                                "pow",
                                List.of(
                                    IntegerExpr.of(2),
                                    InfixExpr.of(Variable.of("N"), "-", IntegerExpr.of(1)))))))));
    Expression retryCall =
        LocalCallExpr.of(
            "with_retry",
            List.of(
                Variable.of("Fun"),
                InfixExpr.of(Variable.of("Attempts"), "-", IntegerExpr.of(1)),
                Variable.of("Base"),
                InfixExpr.of(Variable.of("N"), "+", IntegerExpr.of(1)),
                Variable.of("ShouldRetry")));
    Expression backoffBody = BlockExpr.commaSeparated(List.of(sleepCall, retryCall), false);
    return Function.of(
        "with_retry",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Fun"),
                    IntegerPattern.of(0),
                    WildcardPattern.of(),
                    WildcardPattern.of(),
                    WildcardPattern.of()),
                ApplyExpr.of(Variable.of("Fun"), List.of())),
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Fun"),
                    VariablePattern.of("Attempts"),
                    VariablePattern.of("Base"),
                    VariablePattern.of("N"),
                    VariablePattern.of("ShouldRetry")),
                CaseExpr.of(
                    ApplyExpr.of(Variable.of("Fun"), List.of()),
                    List.of(
                        Clause.of(
                            MatchPattern.of(
                                TuplePattern.of(
                                    List.of(AtomPattern.of("ok"), WildcardPattern.of())),
                                VariablePattern.of("Ok")),
                            Variable.of("Ok")),
                        Clause.of(
                            MatchPattern.of(
                                TuplePattern.of(
                                    List.of(AtomPattern.of("error"), WildcardPattern.of())),
                                VariablePattern.of("Err")),
                            CaseExpr.of(
                                ApplyExpr.of(
                                    Variable.of("ShouldRetry"), List.of(Variable.of("Err"))),
                                List.of(
                                    Clause.of(
                                        AtomPattern.of("true"),
                                        ExpressionGuard.of(
                                            InfixExpr.of(
                                                Variable.of("Attempts"), ">", IntegerExpr.of(1))),
                                        backoffBody),
                                    Clause.of(WildcardPattern.of(), Variable.of("Err"))))))))));
  }

  static Function retryable(List<StructureShape> modeledErrors, SymbolProvider sp) {
    List<FunctionClause> clauses = new ArrayList<>();
    for (StructureShape error : modeledErrors) {
      Optional<BeamRetryIndex.RetryInfo> info = BeamRetryIndex.forError(error);
      if (info.isPresent() && info.get().retryable()) {
        String recordName = recordName(sp.toSymbol(error));
        clauses.add(
            FunctionClause.of(
                List.of(RecordPattern.of(recordName, List.of())), AtomExpr.of("true")));
      }
    }
    clauses.add(FunctionClause.of(List.of(WildcardPattern.of()), AtomExpr.of("false")));
    return Function.of("retryable", clauses, Spec.of("retryable(term()) -> boolean()"));
  }

  static Function throttling(List<StructureShape> modeledErrors, SymbolProvider sp) {
    List<FunctionClause> clauses = new ArrayList<>();
    for (StructureShape error : modeledErrors) {
      Optional<BeamRetryIndex.RetryInfo> info = BeamRetryIndex.forError(error);
      if (info.isPresent() && info.get().throttling()) {
        String recordName = recordName(sp.toSymbol(error));
        clauses.add(
            FunctionClause.of(
                List.of(RecordPattern.of(recordName, List.of())), AtomExpr.of("true")));
      }
    }
    clauses.add(FunctionClause.of(List.of(WildcardPattern.of()), AtomExpr.of("false")));
    return Function.of("throttling", clauses, Spec.of("throttling(term()) -> boolean()"));
  }

  static Function shouldRetry(List<StructureShape> retryableErrors, SymbolProvider sp) {
    List<FunctionClause> clauses = new ArrayList<>();
    for (StructureShape error : retryableErrors) {
      String recordName = recordName(sp.toSymbol(error));
      clauses.add(
          FunctionClause.of(
              List.of(
                  TuplePattern.of(
                      List.of(
                          VariablePattern.of("error"), RecordPattern.of(recordName, List.of())))),
              AtomExpr.of("true")));
    }
    clauses.add(FunctionClause.of(List.of(WildcardPattern.of()), AtomExpr.of("false")));
    return Function.of("should_retry", clauses, Spec.of("should_retry(term()) -> boolean()"));
  }

  private static List<StructureShape> modeledErrors(Model model, ServiceShape service) {
    List<StructureShape> errors = new ArrayList<>();
    for (Shape shape : new Walker(model).walkShapes(service)) {
      if (shape instanceof StructureShape structure
          && BeamRetryIndex.forError(structure).isPresent()) {
        errors.add(structure);
      }
    }
    errors.sort(Comparator.comparing(s -> s.getId().toString()));
    return errors;
  }

  private static List<StructureShape> retryableErrors(Model model, ServiceShape service) {
    List<StructureShape> errors = new ArrayList<>();
    for (StructureShape error : modeledErrors(model, service)) {
      if (BeamRetryIndex.forError(error).orElseThrow().retryable()) {
        errors.add(error);
      }
    }
    return errors;
  }

  private static String recordName(Symbol symbol) {
    return symbol.getName().replace("()", "");
  }
}
