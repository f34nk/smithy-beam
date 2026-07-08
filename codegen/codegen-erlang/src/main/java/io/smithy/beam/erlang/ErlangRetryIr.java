package io.smithy.beam.erlang;

import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.Edoc;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.OpaqueExpr;
import io.beam.ir.erlang.RecordPattern;
import io.beam.ir.erlang.Spec;
import io.beam.ir.erlang.TuplePattern;
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

  private static Function withRetryOuter() {
    return Function.of(
        "with_retry",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Fun"), VariablePattern.of("Opts")),
                OpaqueExpr.of(
                    """
                    Max = maps:get(max_attempts, Opts, 3),
                    Base = maps:get(base_delay_ms, Opts, 100),
                    ShouldRetry = maps:get(should_retry, Opts, fun(_) -> false end),
                    with_retry(Fun, Max, Base, 1, ShouldRetry)"""
                        .strip()))),
        Spec.of("with_retry(fun(() -> term()), map()) -> term()"),
        Edoc.of(
            "Invokes {@code Fun} with exponential backoff when a retryable error is returned."),
        null);
  }

  private static Function withRetryInner() {
    return Function.of(
        "with_retry",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Fun"),
                    VariablePattern.of("0"),
                    VariablePattern.of("_"),
                    VariablePattern.of("_"),
                    VariablePattern.of("_")),
                OpaqueExpr.of("Fun()")),
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Fun"),
                    VariablePattern.of("Attempts"),
                    VariablePattern.of("Base"),
                    VariablePattern.of("N"),
                    VariablePattern.of("ShouldRetry")),
                OpaqueExpr.of(
                    """
                    case Fun() of
                        {ok, _} = Ok ->
                            Ok;
                        {error, _} = Err ->
                            case ShouldRetry(Err) of
                                true when Attempts > 1 ->
                                    timer:sleep(trunc(Base * math:pow(2, N - 1))),
                                    with_retry(Fun, Attempts - 1, Base, N + 1, ShouldRetry);
                                _ ->
                                    Err
                            end
                    end"""
                        .strip()))),
        null,
        null,
        null);
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
    clauses.add(
        FunctionClause.of(List.of(WildcardPattern.of()), AtomExpr.of("false")));
    return Function.of(
        "retryable",
        clauses,
        Spec.of("retryable(term()) -> boolean()"),
        null,
        null);
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
    clauses.add(
        FunctionClause.of(List.of(WildcardPattern.of()), AtomExpr.of("false")));
    return Function.of(
        "throttling",
        clauses,
        Spec.of("throttling(term()) -> boolean()"),
        null,
        null);
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
                          VariablePattern.of("error"),
                          RecordPattern.of(recordName, List.of())))),
              AtomExpr.of("true")));
    }
    clauses.add(
        FunctionClause.of(List.of(WildcardPattern.of()), AtomExpr.of("false")));
    return Function.of(
        "should_retry",
        clauses,
        Spec.of("should_retry(term()) -> boolean()"),
        null,
        null);
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
