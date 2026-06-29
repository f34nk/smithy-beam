package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamResourceIndex;
import io.smithy.beam.core.BeamResourceInputBuilder;
import io.smithy.beam.core.BeamResourceInputBuilder.IdentifierArg;
import io.smithy.beam.core.BeamResourceInputBuilder.InputPlan;
import io.smithy.beam.core.BeamResourceLifecycle;
import io.smithy.beam.ir.elixir.ExAliasAttr;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExBlankLine;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExDoc;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExMapUpdate;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExModuleEntry;
import io.smithy.beam.ir.elixir.ExPattern;
import io.smithy.beam.ir.elixir.ExPreambleEntry;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExStruct;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
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

final class ElixirResourceIr {
  private ElixirResourceIr() {}

  private record HelperBinding(String helperName, ShapeId operationId) {}

  static ExModule clientModule(
      ElixirContext ctx,
      ResourceShape resource,
      BeamResourceIndex index,
      BeamElixirLayout layout,
      String delegateMod,
      String typesMod) {
    return lifecycleModule(ctx, resource, index, layout, delegateMod, typesMod, false);
  }

  static ExModule serverModule(
      ElixirContext ctx,
      ResourceShape resource,
      BeamResourceIndex index,
      BeamElixirLayout layout,
      String delegateMod,
      String typesMod) {
    return lifecycleModule(ctx, resource, index, layout, delegateMod, typesMod, true);
  }

  private static ExModule lifecycleModule(
      ElixirContext ctx,
      ResourceShape resource,
      BeamResourceIndex index,
      BeamElixirLayout layout,
      String delegateMod,
      String typesMod,
      boolean server) {
    SymbolProvider sp = ctx.symbolProvider();
    String resourceSnake = sp.toSymbol(resource).getName();
    String mod =
        ElixirSymbolProvider.toModuleName(
            server
                ? layout.resourceServerModuleName(resourceSnake)
                : layout.resourceClientModuleName(resourceSnake));
    List<HelperBinding> bindings = collectBindings(index, sp, resource);
    List<ExFunction> functions = new ArrayList<>();
    for (HelperBinding binding : bindings) {
      functions.add(
          helperFunction(index, sp, resource, binding, delegateMod, typesMod, server));
    }
    List<ExPreambleEntry> preamble = new ArrayList<>();
    BeamDocumentation.forShape(resource)
        .ifPresent(doc -> preamble.add(ExModuledoc.moduledoc(doc)));
    if (preamble.isEmpty()) {
      preamble.add(ExModuledoc.moduledoc("Lifecycle helpers for " + resource.getId() + "."));
    }
    List<ExModuleEntry> entries = new ArrayList<>();
    if (!server) {
      entries.add(ElixirClientIr.clientConfigTypeDef());
      entries.add(new ExBlankLine());
    }
    entries.addAll(functions);
    return ExModule.module(
        mod,
        preamble,
        List.of(
            ExAliasAttr.alias(delegateMod, server ? "Server" : "Client"),
            ExAliasAttr.alias(typesMod, "Types")),
        List.of(),
        List.of(),
        entries);
  }

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

  private static ExFunction helperFunction(
      BeamResourceIndex index,
      SymbolProvider sp,
      ResourceShape resource,
      HelperBinding binding,
      String delegateMod,
      String typesMod,
      boolean server) {
    OperationShape op = index.expectOperation(binding.operationId());
    InputPlan plan = BeamResourceInputBuilder.plan(index, sp, resource, op);
    Symbol opSym = sp.toSymbol(op);
    StructureShape output = index.model().expectShape(op.getOutputShape(), StructureShape.class);
    String helper = server ? "handle_" + binding.helperName() : binding.helperName();
    String opHandler = server ? "handle_" + opSym.getName() : opSym.getName();
    String outType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(output));

    List<ExPattern> patterns = new ArrayList<>();
    if (server) {
      patterns.add(ExVarPattern.var("ctx"));
    } else {
      patterns.add(ExVarPattern.var("config"));
    }
    for (IdentifierArg arg : plan.identifierArgs()) {
      patterns.add(ExVarPattern.var(arg.paramName()));
    }
    if (plan.acceptsFullInput()) {
      patterns.add(ExVarPattern.var("input"));
    }
    if (server) {
      patterns.add(ExVarPattern.var("meta"));
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
      specParams.add(ElixirTopDown.structureSpecType(typesMod, plan.inputSymbol()));
    }
    if (server) {
      specParams.add("term()");
    }

    ExDoc doc = BeamDocumentation.forShape(op).map(ExDoc::doc).orElse(null);

    List<ExExpr> callArgs = new ArrayList<>();
    if (server) {
      callArgs.add(ExVar.var("ctx"));
    } else {
      callArgs.add(ExVar.var("config"));
    }
    callArgs.add(inputExpression(plan, typesMod));
    if (server) {
      callArgs.add(ExVar.var("meta"));
    }

    String delegateAlias = server ? "Server" : "Client";
    return ExFunction.functionWithDocAndSpec(
        "def",
        helper,
        doc,
        ExSpec.functionSpec(
            helper,
            String.join(", ", specParams),
            "{:ok, " + outType + "} | {:error, term()}"),
        List.of(
            ExClause.inlineClause(
                patterns,
                ExCall.call(
                    delegateAlias,
                    opHandler,
                    callArgs.toArray(ExExpr[]::new)))));
  }

  private static String identifierType(
      SymbolProvider sp, BeamResourceIndex index, IdentifierArg arg) {
    Shape shape = index.model().expectShape(arg.shapeId(), Shape.class);
    return sp.toSymbol(shape).getName();
  }

  private static ExExpr inputExpression(InputPlan plan, String typesMod) {
    if (plan.acceptsFullInput()) {
      if (plan.identifierArgs().isEmpty()) {
        return ExVar.var("input");
      }
      ExMapEntry[] updates =
          plan.identifierArgs().stream()
              .map(
                  arg ->
                      ExMapEntry.entry(
                          ExAtom.atom(arg.fieldName()), ExVar.var(arg.paramName())))
              .toArray(ExMapEntry[]::new);
      return ExMapUpdate.mapUpdate(ExVar.var("input"), updates);
    }
    ExMapEntry[] fields =
        plan.identifierArgs().stream()
            .map(
                arg ->
                    ExMapEntry.entry(ExAtom.atom(arg.fieldName()), ExVar.var(arg.paramName())))
            .toArray(ExMapEntry[]::new);
    return ExStruct.struct(typesMod + "." + plan.inputSymbol().getName(), fields);
  }
}
