package io.smithy.beam.elixir;

import io.beam.dsl.elixir.AtomExpr;
import io.beam.dsl.elixir.AtomPattern;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.FunctionHead;
import io.beam.dsl.elixir.Pattern;
import io.beam.dsl.elixir.StructPattern;
import io.beam.dsl.elixir.TuplePattern;
import io.beam.dsl.elixir.WildcardPattern;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamRetryIndex;
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

  static List<Function> clientPredicateFunctions(
      Model model, ServiceShape service, SymbolProvider sp, BeamElixirLayout layout) {
    List<StructureShape> retryableErrors = retryableErrors(model, service);
    if (retryableErrors.isEmpty()) {
      return List.of();
    }
    List<StructureShape> modeledErrors = modeledErrors(model, service);
    List<Function> functions = new ArrayList<>();
    functions.addAll(shouldRetry(retryableErrors, sp, layout));
    functions.addAll(retryable(modeledErrors, sp, layout));
    functions.addAll(throttling(modeledErrors, sp, layout));
    return functions;
  }

  static List<Function> shouldRetry(
      List<StructureShape> retryableErrors, SymbolProvider sp, BeamElixirLayout layout) {
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    List<Function> functions = new ArrayList<>();
    for (StructureShape error : retryableErrors) {
      String exceptionMod = sp.toSymbol(error).getName();
      functions.add(
          predicateClause(
              "should_retry?",
              false,
              TuplePattern.of(
                  List.of(
                      AtomPattern.of("error"),
                      StructPattern.of(typesMod + "." + exceptionMod, List.of()))),
              AtomExpr.of("true")));
    }
    functions.add(
        predicateClause("should_retry?", false, WildcardPattern.of(), AtomExpr.of("false")));
    return functions;
  }

  static List<Function> retryable(
      List<StructureShape> modeledErrors, SymbolProvider sp, BeamElixirLayout layout) {
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    List<Function> functions = new ArrayList<>();
    for (StructureShape error : modeledErrors) {
      Optional<BeamRetryIndex.RetryInfo> info = BeamRetryIndex.forError(error);
      if (info.isPresent() && info.get().retryable()) {
        String exceptionMod = sp.toSymbol(error).getName();
        functions.add(
            predicateClause(
                "retryable?",
                true,
                StructPattern.of(typesMod + "." + exceptionMod, List.of()),
                AtomExpr.of("true")));
      }
    }
    functions.add(predicateClause("retryable?", true, WildcardPattern.of(), AtomExpr.of("false")));
    return functions;
  }

  static List<Function> throttling(
      List<StructureShape> modeledErrors, SymbolProvider sp, BeamElixirLayout layout) {
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    List<Function> functions = new ArrayList<>();
    for (StructureShape error : modeledErrors) {
      Optional<BeamRetryIndex.RetryInfo> info = BeamRetryIndex.forError(error);
      if (info.isPresent() && info.get().throttling()) {
        String exceptionMod = sp.toSymbol(error).getName();
        functions.add(
            predicateClause(
                "throttling?",
                true,
                StructPattern.of(typesMod + "." + exceptionMod, List.of()),
                AtomExpr.of("true")));
      }
    }
    functions.add(predicateClause("throttling?", true, WildcardPattern.of(), AtomExpr.of("false")));
    return functions;
  }

  private static Function predicateClause(
      String name, boolean defp, Pattern pattern, Expression body) {
    return Function.of(
        name, defp, List.of(FunctionHead.of(List.of(pattern))), body, null, null, true);
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
