package io.smithy.beam.erlang;

import io.beam.dsl.erlang.AtomExpr;
import io.beam.dsl.erlang.Function;
import io.beam.dsl.erlang.FunctionClause;
import io.beam.dsl.erlang.RecordPattern;
import io.beam.dsl.erlang.Spec;
import io.beam.dsl.erlang.TuplePattern;
import io.beam.dsl.erlang.VariablePattern;
import io.beam.dsl.erlang.WildcardPattern;
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
