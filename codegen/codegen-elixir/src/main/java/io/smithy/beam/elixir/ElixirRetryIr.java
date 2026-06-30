package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamRetryIndex;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExDoc;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExInteger;
import io.smithy.beam.ir.elixir.ExIntegerPattern;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExStructPattern;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;

final class ElixirRetryIr {
  private ElixirRetryIr() {}

  static ExModule retryModule(
      ElixirContext ctx, ServiceShape service, Model model, SymbolProvider sp) {
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    String moduleName = ElixirSymbolProvider.toModuleName(layout.retryModuleName());
    List<StructureShape> retryableErrors = retryableErrors(model, service);
    List<ExFunction> functions = new ArrayList<>();
    functions.addAll(withRetryFunctions());
    functions.add(shouldRetry(retryableErrors, sp, layout));
    return ExModule.module(
        moduleName,
        List.of(
            ExModuledoc.moduledoc(
                "Generated retry helpers for " + service.getId() + " (generated).")),
        List.of(),
        functions);
  }

  static List<ExFunction> withRetryFunctions() {
    return List.of(withRetryOuter(), withRetryInner());
  }

  private static ExFunction withRetryOuter() {
    return ExFunction.functionWithDocAndSpec(
        "def",
        "with_retry",
        ExDoc.doc(
            "Invokes fun with exponential backoff when a modeled retryable error is returned."),
        ExSpec.functionSpec("with_retry", "(-> term()), keyword()", "term()"),
        List.of(
            ExClause.blockClauseSingleLineHead(
                List.of(ExVarPattern.var("fun"), ExVarPattern.var("opts")),
                ExMatch.match(
                    ExVarPattern.var("max_attempts"),
                    ExCall.call(
                        "Keyword",
                        "get",
                        ExVar.var("opts"),
                        ExAtom.atom("max_attempts"),
                        ExInteger.integer(3))),
                ExMatch.match(
                    ExVarPattern.var("base_delay_ms"),
                    ExCall.call(
                        "Keyword",
                        "get",
                        ExVar.var("opts"),
                        ExAtom.atom("base_delay_ms"),
                        ExInteger.integer(100))),
                ExCallLocal.callLocal(
                    "with_retry",
                    ExVar.var("fun"),
                    ExVar.var("max_attempts"),
                    ExVar.var("base_delay_ms"),
                    ExInteger.integer(1)))));
  }

  private static ExFunction withRetryInner() {
    return ExFunction.defpFunction(
        "with_retry",
        List.of(
            ExClause.inlineClause(
                List.of(
                    ExVarPattern.var("fun"),
                    ExIntegerPattern.integer(0),
                    ExVarPattern.var("_base"),
                    ExVarPattern.var("_n")),
                ExCapturedBlock.capturedBlock("fun.()")),
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("fun"),
                    ExVarPattern.var("attempts"),
                    ExVarPattern.var("base"),
                    ExVarPattern.var("n")),
                ExCapturedBlock.capturedBlock(
                    """
                    case fun.() do
                      {:ok, _} = ok -> ok
                      {:error, _} = err ->
                        if should_retry?(err) and attempts > 1 do
                          Process.sleep(trunc(base * :math.pow(2, n - 1)))
                          with_retry(fun, attempts - 1, base, n + 1)
                        else
                          err
                        end
                    end"""))));
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
        ExClause.inlineClause(List.of(ExVarPattern.var("_")), ExCapturedBlock.capturedBlock("false")));
    return ExFunction.defFunction("should_retry?", clauses);
  }

  private static List<StructureShape> retryableErrors(Model model, ServiceShape service) {
    List<StructureShape> errors = new ArrayList<>();
    for (Shape shape : new Walker(model).walkShapes(service)) {
      if (shape instanceof StructureShape structure) {
        BeamRetryIndex.forError(structure)
            .filter(BeamRetryIndex.RetryInfo::retryable)
            .ifPresent(info -> errors.add(structure));
      }
    }
    errors.sort(Comparator.comparing(s -> s.getId().toString()));
    return errors;
  }
}
