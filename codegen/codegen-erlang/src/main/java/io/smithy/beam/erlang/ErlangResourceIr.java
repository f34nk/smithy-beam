package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamResourceIndex;
import io.smithy.beam.core.BeamResourceInputBuilder;
import io.smithy.beam.core.BeamResourceInputBuilder.IdentifierArg;
import io.smithy.beam.core.BeamResourceInputBuilder.InputPlan;
import io.smithy.beam.core.BeamResourceLifecycle;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAttribute;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlComment;
import io.smithy.beam.ir.erlang.ErlExportAttribute;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlFunctionDoc;
import io.smithy.beam.ir.erlang.ErlFunctionSpec;
import io.smithy.beam.ir.erlang.ErlModule;
import io.smithy.beam.ir.erlang.ErlPattern;
import io.smithy.beam.ir.erlang.ErlPreambleEntry;
import io.smithy.beam.ir.erlang.ErlRecord;
import io.smithy.beam.ir.erlang.ErlRecordField;
import io.smithy.beam.ir.erlang.ErlRecordUpdate;
import io.smithy.beam.ir.erlang.ErlRemoteCall;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

final class ErlangResourceIr {
  private ErlangResourceIr() {}

  static ErlModule clientModule(
      ErlangContext ctx,
      ResourceShape resource,
      BeamResourceIndex index,
      BeamErlangLayout layout,
      String delegateMod) {
    return lifecycleModule(ctx, resource, index, layout, delegateMod, false);
  }

  static ErlModule serverModule(
      ErlangContext ctx,
      ResourceShape resource,
      BeamResourceIndex index,
      BeamErlangLayout layout,
      String delegateMod) {
    return lifecycleModule(ctx, resource, index, layout, delegateMod, true);
  }

  private static ErlModule lifecycleModule(
      ErlangContext ctx,
      ResourceShape resource,
      BeamResourceIndex index,
      BeamErlangLayout layout,
      String delegateMod,
      boolean server) {
    SymbolProvider sp = ctx.symbolProvider();
    String resourceSnake = sp.toSymbol(resource).getName();
    String mod =
        server
            ? layout.resourceServerModuleName(resourceSnake)
            : layout.resourceClientModuleName(resourceSnake);
    List<HelperBinding> bindings = collectBindings(index, sp, resource);
    List<String> exports = new ArrayList<>();
    for (HelperBinding binding : bindings) {
      InputPlan plan =
          BeamResourceInputBuilder.plan(
              index, sp, resource, index.expectOperation(binding.operationId()));
      exports.add(
          (server ? "handle_" : "") + binding.helperName() + "/" + helperArity(plan, server));
    }

    List<ErlPreambleEntry> preamble = new ArrayList<>();
    preamble.add(ErlComment.comment("Generated resource helpers for " + resource.getId() + "."));
    BeamDocumentation.forShape(resource).ifPresent(doc -> preamble.add(ErlComment.comment(doc)));

    List<ErlFunction> functions = new ArrayList<>();
    for (HelperBinding binding : bindings) {
      functions.add(helperFunction(index, sp, resource, binding, delegateMod, server));
    }

    List<io.smithy.beam.ir.erlang.ErlModuleAttribute> attributes = new ArrayList<>();
    attributes.add(new ErlAttribute("include", "\"" + layout.typesHeaderFile() + "\""));
    if (!server) {
      attributes.add(ErlangClientIr.clientConfigTypeDef());
    }
    attributes.add(ErlExportAttribute.export(exports));

    return new ErlModule(mod, preamble, attributes, functions);
  }

  private record HelperBinding(String helperName, ShapeId operationId) {}

  private static List<HelperBinding> collectBindings(
      BeamResourceIndex index, SymbolProvider sp, ResourceShape resource) {
    List<HelperBinding> bindings = new ArrayList<>();
    for (Map.Entry<BeamResourceLifecycle, ShapeId> entry :
        BeamResourceLifecycle.bindings(resource).entrySet()) {
      bindings.add(new HelperBinding(entry.getKey().helperName(), entry.getValue()));
    }
    for (OperationShape op : index.collectionOperationsSorted(resource)) {
      bindings.add(new HelperBinding(sp.toSymbol(op).getName(), op.toShapeId()));
    }
    return bindings;
  }

  private static int helperArity(InputPlan plan, boolean server) {
    int arity = 1 + plan.identifierArgs().size() + (plan.acceptsFullInput() ? 1 : 0);
    if (server) {
      arity += 1;
    }
    return arity;
  }

  private static ErlFunction helperFunction(
      BeamResourceIndex index,
      SymbolProvider sp,
      ResourceShape resource,
      HelperBinding binding,
      String delegateMod,
      boolean server) {
    OperationShape op = index.expectOperation(binding.operationId());
    InputPlan plan = BeamResourceInputBuilder.plan(index, sp, resource, op);
    Symbol opSym = sp.toSymbol(op);
    StructureShape output = index.model().expectShape(op.getOutputShape(), StructureShape.class);
    Symbol outSym = sp.toSymbol(output);
    String helper = server ? "handle_" + binding.helperName() : binding.helperName();
    String opHandler = server ? "handle_" + opSym.getName() : opSym.getName();

    List<ErlPattern> patterns = new ArrayList<>();
    if (server) {
      patterns.add(ErlVarPattern.varPattern("Ctx"));
    } else {
      patterns.add(ErlVarPattern.varPattern("Config"));
    }
    for (IdentifierArg arg : plan.identifierArgs()) {
      patterns.add(ErlVarPattern.varPattern(arg.paramName()));
    }
    if (plan.acceptsFullInput()) {
      patterns.add(ErlVarPattern.varPattern("Input"));
    }
    if (server) {
      patterns.add(ErlVarPattern.varPattern("Meta"));
    }

    List<String> specParams = new ArrayList<>();
    if (server) {
      specParams.add("term()");
    } else {
      specParams.add("client_config()");
    }
    for (IdentifierArg arg : plan.identifierArgs()) {
      specParams.add(identifierType(sp, index, arg));
    }
    if (plan.acceptsFullInput()) {
      specParams.add(plan.inputSymbol().getName());
    }
    if (server) {
      specParams.add("term()");
    }

    ErlFunctionDoc doc =
        BeamDocumentation.forShape(op).map(ErlFunctionDoc::functionDoc).orElse(null);

    List<ErlExpr> callArgs = new ArrayList<>();
    if (server) {
      callArgs.add(ErlVar.var("Ctx"));
    } else {
      callArgs.add(ErlVar.var("Config"));
    }
    callArgs.add(inputExpression(plan));
    if (server) {
      callArgs.add(ErlVar.var("Meta"));
    }

    return new ErlFunction(
        helper,
        patterns.size(),
        doc,
        ErlFunctionSpec.functionSpec(
            helper,
            String.join(", ", specParams),
            "{'ok', " + outSym.getName() + "} | {'error', term()}"),
        List.of(
            ErlClause.clause(
                patterns,
                ErlRemoteCall.call(
                    ErlAtom.atom(delegateMod), opHandler, callArgs.toArray(ErlExpr[]::new)))));
  }

  private static String identifierType(
      SymbolProvider sp, BeamResourceIndex index, IdentifierArg arg) {
    Shape shape = index.model().expectShape(arg.shapeId(), Shape.class);
    return sp.toSymbol(shape).getName();
  }

  private static ErlExpr inputExpression(InputPlan plan) {
    String recordName = recordName(plan.inputSymbol());
    if (plan.acceptsFullInput()) {
      if (plan.identifierArgs().isEmpty()) {
        return ErlVar.var("Input");
      }
      ErlRecordField[] updates =
          plan.identifierArgs().stream()
              .map(arg -> ErlRecordField.field(arg.fieldName(), ErlVar.var(arg.paramName())))
              .toArray(ErlRecordField[]::new);
      return ErlRecordUpdate.recordUpdate(ErlVar.var("Input"), recordName, updates);
    }
    ErlRecordField[] fields =
        plan.identifierArgs().stream()
            .map(arg -> ErlRecordField.field(arg.fieldName(), ErlVar.var(arg.paramName())))
            .toArray(ErlRecordField[]::new);
    return ErlRecord.record(recordName, fields);
  }

  private static String recordName(Symbol symbol) {
    return symbol.getName().replace("()", "");
  }
}
