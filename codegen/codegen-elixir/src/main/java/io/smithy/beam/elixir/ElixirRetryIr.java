package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamRetryIndex;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExStructPattern;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;

final class ElixirRetryIr {
  private ElixirRetryIr() {}

  static boolean serviceHasRetryableErrors(Model model, ServiceShape service) {
    return !retryableErrors(model, service).isEmpty();
  }

  static List<ExFunction> clientPredicateFunctions(
      Model model, ServiceShape service, SymbolProvider sp, BeamElixirLayout layout) {
    List<StructureShape> retryableErrors = retryableErrors(model, service);
    if (retryableErrors.isEmpty()) {
      return List.of();
    }
    List<StructureShape> modeledErrors = modeledErrors(model, service);
    return List.of(
        shouldRetry(retryableErrors, sp, layout),
        retryable(modeledErrors, sp, layout),
        throttling(modeledErrors, sp, layout));
  }

  static ExFunction shouldRetry(
      List<StructureShape> retryableErrors, SymbolProvider sp, BeamElixirLayout layout) {
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    List<ExClause> clauses = new ArrayList<>();
    for (StructureShape error : retryableErrors) {
      String exceptionMod = sp.toSymbol(error).getName();
      clauses.add(
          ExClause.inlineClause(
              List.of(
                  ExTuplePattern.tuple(
                      ExAtomPattern.atom("error"),
                      ExStructPattern.struct(typesMod + "." + exceptionMod, List.of()))),
              ExCapturedBlock.capturedBlock("true")));
    }
    clauses.add(
        ExClause.inlineClause(
            List.of(ExVarPattern.var("_")), ExCapturedBlock.capturedBlock("false")));
    return ExFunction.defFunction("should_retry?", clauses);
  }

  static ExFunction retryable(
      List<StructureShape> modeledErrors, SymbolProvider sp, BeamElixirLayout layout) {
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    List<ExClause> clauses = new ArrayList<>();
    for (StructureShape error : modeledErrors) {
      Optional<BeamRetryIndex.RetryInfo> info = BeamRetryIndex.forError(error);
      if (info.isPresent() && info.get().retryable()) {
        String exceptionMod = sp.toSymbol(error).getName();
        clauses.add(
            ExClause.inlineClause(
                List.of(ExStructPattern.struct(typesMod + "." + exceptionMod, List.of())),
                ExCapturedBlock.capturedBlock("true")));
      }
    }
    clauses.add(
        ExClause.inlineClause(
            List.of(ExVarPattern.var("_")), ExCapturedBlock.capturedBlock("false")));
    return ExFunction.defpFunction("retryable?", clauses);
  }

  static ExFunction throttling(
      List<StructureShape> modeledErrors, SymbolProvider sp, BeamElixirLayout layout) {
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    List<ExClause> clauses = new ArrayList<>();
    for (StructureShape error : modeledErrors) {
      Optional<BeamRetryIndex.RetryInfo> info = BeamRetryIndex.forError(error);
      if (info.isPresent() && info.get().throttling()) {
        String exceptionMod = sp.toSymbol(error).getName();
        clauses.add(
            ExClause.inlineClause(
                List.of(ExStructPattern.struct(typesMod + "." + exceptionMod, List.of())),
                ExCapturedBlock.capturedBlock("true")));
      }
    }
    clauses.add(
        ExClause.inlineClause(
            List.of(ExVarPattern.var("_")), ExCapturedBlock.capturedBlock("false")));
    return ExFunction.defpFunction("throttling?", clauses);
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
}
