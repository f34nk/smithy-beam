package io.smithy.beam.elixir;

import io.beam.dsl.elixir.Alias;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.FunctionDoc;
import io.beam.dsl.elixir.FunctionHead;
import io.beam.dsl.elixir.MapEntry;
import io.beam.dsl.elixir.MapExpr;
import io.beam.dsl.elixir.Module;
import io.beam.dsl.elixir.Moduledoc;
import io.beam.dsl.elixir.Pattern;
import io.beam.dsl.elixir.RemoteCallExpr;
import io.beam.dsl.elixir.Spec;
import io.beam.dsl.elixir.StructExpr;
import io.beam.dsl.elixir.StructField;
import io.beam.dsl.elixir.Variable;
import io.beam.dsl.elixir.VariablePattern;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamResourceIndex;
import io.smithy.beam.core.BeamResourceInputBuilder;
import io.smithy.beam.core.BeamResourceInputBuilder.IdentifierArg;
import io.smithy.beam.core.BeamResourceInputBuilder.InputPlan;
import io.smithy.beam.core.BeamResourceLifecycle;
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

  static Module clientModule(
      ElixirContext ctx,
      ResourceShape resource,
      BeamResourceIndex index,
      BeamElixirLayout layout,
      String delegateMod,
      String typesMod) {
    return lifecycleModule(ctx, resource, index, layout, delegateMod, typesMod, false);
  }

  static Module serverModule(
      ElixirContext ctx,
      ResourceShape resource,
      BeamResourceIndex index,
      BeamElixirLayout layout,
      String delegateMod,
      String typesMod) {
    return lifecycleModule(ctx, resource, index, layout, delegateMod, typesMod, true);
  }

  private static Module lifecycleModule(
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
    List<Function> functions = new ArrayList<>();
    for (HelperBinding binding : bindings) {
      functions.add(helperFunction(index, sp, resource, binding, delegateMod, typesMod, server));
    }
    Moduledoc moduledoc =
        BeamDocumentation.forShape(resource)
            .map(Moduledoc::of)
            .orElse(Moduledoc.of("Lifecycle helpers for " + resource.getId() + "."));
    return Module.of(
        mod,
        moduledoc,
        List.of(),
        List.of(Alias.of(delegateMod, server ? "Server" : "Client"), Alias.of(typesMod, "Types")),
        List.of(),
        List.of(),
        List.of(),
        server ? List.of() : ElixirClientIr.clientConfigTrailingAttributes(),
        functions);
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

  private static Function helperFunction(
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

    List<Pattern> patterns = new ArrayList<>();
    if (server) {
      patterns.add(VariablePattern.of("ctx"));
    } else {
      patterns.add(VariablePattern.of("config"));
    }
    for (IdentifierArg arg : plan.identifierArgs()) {
      patterns.add(VariablePattern.of(arg.paramName()));
    }
    if (plan.acceptsFullInput()) {
      patterns.add(VariablePattern.of("input"));
    }
    if (server) {
      patterns.add(VariablePattern.of("meta"));
    }

    List<String> specParams = new ArrayList<>();
    if (server) {
      specParams.add("term()");
    } else {
      specParams.add("client_config()");
    }
    for (IdentifierArg arg : plan.identifierArgs()) {
      specParams.add(identifierType(sp, index, arg, typesMod));
    }
    if (plan.acceptsFullInput()) {
      specParams.add(ElixirTopDown.structureSpecType(typesMod, plan.inputSymbol()));
    }
    if (server) {
      specParams.add("term()");
    }

    FunctionDoc doc = BeamDocumentation.forShape(op).map(FunctionDoc::of).orElse(null);

    List<Expression> callArgs = new ArrayList<>();
    if (server) {
      callArgs.add(Variable.of("ctx"));
    } else {
      callArgs.add(Variable.of("config"));
    }
    callArgs.add(inputExpression(plan, typesMod));
    if (server) {
      callArgs.add(Variable.of("meta"));
    }

    String delegateAlias = server ? "Server" : "Client";
    return Function.of(
        helper,
        false,
        List.of(FunctionHead.of(patterns)),
        RemoteCallExpr.of(delegateAlias, opHandler, callArgs),
        Spec.of(
            helper
                + "("
                + String.join(", ", specParams)
                + ") :: {:ok, "
                + outType
                + "} | {:error, term()}"),
        doc,
        true);
  }

  private static String identifierType(
      SymbolProvider sp, BeamResourceIndex index, IdentifierArg arg, String typesMod) {
    Shape shape = index.model().expectShape(arg.shapeId(), Shape.class);
    return ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(shape));
  }

  private static Expression inputExpression(InputPlan plan, String typesMod) {
    if (plan.acceptsFullInput()) {
      if (plan.identifierArgs().isEmpty()) {
        return Variable.of("input");
      }
      List<MapEntry> updates =
          plan.identifierArgs().stream()
              .map(arg -> MapEntry.atomKey(arg.fieldName(), Variable.of(arg.paramName())))
              .toList();
      return MapExpr.of(Variable.of("input"), updates);
    }
    List<StructField> fields =
        plan.identifierArgs().stream()
            .map(arg -> StructField.of(arg.fieldName(), Variable.of(arg.paramName())))
            .toList();
    return StructExpr.of(typesMod + "." + plan.inputSymbol().getName(), fields);
  }
}
