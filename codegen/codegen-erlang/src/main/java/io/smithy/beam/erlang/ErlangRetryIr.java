package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamRetryIndex;
import io.smithy.beam.ir.erlang.ErlAttribute;
import io.smithy.beam.ir.erlang.ErlCapturedBlock;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlComment;
import io.smithy.beam.ir.erlang.ErlExportAttribute;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlFunctionDoc;
import io.smithy.beam.ir.erlang.ErlModule;
import io.smithy.beam.ir.erlang.ErlRecordPattern;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlVarPattern;
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

  static ErlModule retryModule(
      String retryMod,
      String typesHeaderFile,
      ServiceShape service,
      Model model,
      SymbolProvider sp) {
    List<StructureShape> retryableErrors = retryableErrors(model, service);
    List<StructureShape> modeledErrors = modeledErrors(model, service);
    List<ErlFunction> functions = new ArrayList<>();
    functions.addAll(withRetryFunctions());
    functions.add(retryable(modeledErrors, sp));
    functions.add(throttling(modeledErrors, sp));
    functions.add(shouldRetry(retryableErrors, sp));
    return new ErlModule(
        retryMod,
        List.of(ErlComment.comment("Generated retry helpers for " + service.getId() + ".")),
        List.of(
            new ErlAttribute("include", "\"" + typesHeaderFile + "\""),
            ErlExportAttribute.export(
                List.of("with_retry/2", "retryable/1", "throttling/1", "should_retry/1"))),
        functions);
  }

  static List<ErlFunction> withRetryFunctions() {
    return List.of(withRetryOuter(), withRetryInner());
  }

  private static ErlFunction withRetryOuter() {
    return new ErlFunction(
        "with_retry",
        2,
        ErlFunctionDoc.functionDoc(
            "Invokes {@code Fun} with exponential backoff when a modeled retryable error is returned."),
        io.smithy.beam.ir.erlang.ErlFunctionSpec.functionSpec(
            "with_retry", "fun(() -> term()), map()", "term()"),
        List.of(
            ErlClause.blockClause(
                List.of(ErlVarPattern.varPattern("Fun"), ErlVarPattern.varPattern("Opts")),
                ErlCapturedBlock.capturedBlock(
                    """
                                Max = maps:get(max_attempts, Opts, 3),
                                Base = maps:get(base_delay_ms, Opts, 100),
                                with_retry(Fun, Max, Base, 1)"""))));
  }

  private static ErlFunction withRetryInner() {
    return ErlFunction.function(
        "with_retry",
        4,
        List.of(
            ErlClause.clause(
                List.of(
                    ErlVarPattern.varPattern("Fun"),
                    ErlVarPattern.varPattern("0"),
                    ErlVarPattern.varPattern("_"),
                    ErlVarPattern.varPattern("_")),
                ErlCapturedBlock.capturedBlock("Fun()")),
            ErlClause.blockClause(
                List.of(
                    ErlVarPattern.varPattern("Fun"),
                    ErlVarPattern.varPattern("Attempts"),
                    ErlVarPattern.varPattern("Base"),
                    ErlVarPattern.varPattern("N")),
                ErlCapturedBlock.capturedBlock(
                    """
                                        case Fun() of
                                            {ok, _} = Ok ->
                                                Ok;
                                            {error, _} = Err ->
                                                case should_retry(Err) of
                                                    true when Attempts > 1 ->
                                                        timer:sleep(trunc(Base * math:pow(2, N - 1))),
                                                        with_retry(Fun, Attempts - 1, Base, N + 1);
                                                    _ ->
                                                        Err
                                                end
                                        end"""))));
  }

  static ErlFunction retryable(List<StructureShape> modeledErrors, SymbolProvider sp) {
    List<ErlClause> clauses = new ArrayList<>();
    for (StructureShape error : modeledErrors) {
      Optional<BeamRetryIndex.RetryInfo> info = BeamRetryIndex.forError(error);
      if (info.isPresent() && info.get().retryable()) {
        String recordName = recordName(sp.toSymbol(error));
        clauses.add(
            ErlClause.clause(
                List.of(ErlRecordPattern.recordPattern(recordName)),
                ErlCapturedBlock.capturedBlock("true")));
      }
    }
    clauses.add(
        ErlClause.clause(
            List.of(ErlVarPattern.varPattern("_")), ErlCapturedBlock.capturedBlock("false")));
    return ErlFunction.functionWithSpec("retryable", 1, "term()", "boolean()", clauses);
  }

  static ErlFunction throttling(List<StructureShape> modeledErrors, SymbolProvider sp) {
    List<ErlClause> clauses = new ArrayList<>();
    for (StructureShape error : modeledErrors) {
      Optional<BeamRetryIndex.RetryInfo> info = BeamRetryIndex.forError(error);
      if (info.isPresent() && info.get().throttling()) {
        String recordName = recordName(sp.toSymbol(error));
        clauses.add(
            ErlClause.clause(
                List.of(ErlRecordPattern.recordPattern(recordName)),
                ErlCapturedBlock.capturedBlock("true")));
      }
    }
    clauses.add(
        ErlClause.clause(
            List.of(ErlVarPattern.varPattern("_")), ErlCapturedBlock.capturedBlock("false")));
    return ErlFunction.functionWithSpec("throttling", 1, "term()", "boolean()", clauses);
  }

  static ErlFunction shouldRetry(List<StructureShape> retryableErrors, SymbolProvider sp) {
    List<ErlClause> clauses = new ArrayList<>();
    for (StructureShape error : retryableErrors) {
      String recordName = recordName(sp.toSymbol(error));
      clauses.add(
          ErlClause.clause(
              List.of(
                  ErlTuplePattern.tuplePattern(
                      ErlVarPattern.varPattern("error"),
                      ErlRecordPattern.recordPattern(recordName))),
              ErlCapturedBlock.capturedBlock("true")));
    }
    clauses.add(
        ErlClause.clause(
            List.of(ErlVarPattern.varPattern("_")), ErlCapturedBlock.capturedBlock("false")));
    return ErlFunction.functionWithSpec("should_retry", 1, "term()", "boolean()", clauses);
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
