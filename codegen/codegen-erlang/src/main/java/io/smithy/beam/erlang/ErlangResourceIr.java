package io.smithy.beam.erlang;

import io.beam.ir.erlang.Edoc;
import io.beam.ir.erlang.Expression;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.Module;
import io.beam.ir.erlang.Pattern;
import io.beam.ir.erlang.RecordExpr;
import io.beam.ir.erlang.RecordField;
import io.beam.ir.erlang.RemoteCallExpr;
import io.beam.ir.erlang.Spec;
import io.beam.ir.erlang.TypeAlias;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamErlangLayout;
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

final class ErlangResourceIr {
  private ErlangResourceIr() {}

  static Module clientModule(
      ErlangContext ctx,
      ResourceShape resource,
      BeamResourceIndex index,
      BeamErlangLayout layout,
      String delegateMod) {
    return lifecycleModule(ctx, resource, index, layout, delegateMod, false);
  }

  static Module serverModule(
      ErlangContext ctx,
      ResourceShape resource,
      BeamResourceIndex index,
      BeamErlangLayout layout,
      String delegateMod) {
    return lifecycleModule(ctx, resource, index, layout, delegateMod, true);
  }

  private static Module lifecycleModule(
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

    List<String> preambleComments = new ArrayList<>();
    preambleComments.add("Generated resource helpers for " + resource.getId() + ".");
    BeamDocumentation.forShape(resource).ifPresent(preambleComments::add);

    List<Function> functions = new ArrayList<>();
    for (HelperBinding binding : bindings) {
      functions.add(helperFunction(index, sp, resource, binding, delegateMod, server));
    }

    List<TypeAlias> typeAliases = new ArrayList<>();
    if (!server) {
      typeAliases.add(ErlangClientIr.clientConfigTypeDef());
    }

    return Module.of(
        mod,
        functions,
        preambleComments,
        null,
        List.of(layout.typesHeaderFile()),
        typeAliases.isEmpty() ? null : typeAliases,
        exports);
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

  private static Function helperFunction(
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

    List<Pattern> patterns = new ArrayList<>();
    if (server) {
      patterns.add(VariablePattern.of("Ctx"));
    } else {
      patterns.add(VariablePattern.of("Config"));
    }
    for (IdentifierArg arg : plan.identifierArgs()) {
      patterns.add(VariablePattern.of(arg.paramName()));
    }
    if (plan.acceptsFullInput()) {
      patterns.add(VariablePattern.of("Input"));
    }
    if (server) {
      patterns.add(VariablePattern.of("Meta"));
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

    List<Expression> callArgs = new ArrayList<>();
    if (server) {
      callArgs.add(Variable.of("Ctx"));
    } else {
      callArgs.add(Variable.of("Config"));
    }
    callArgs.add(inputExpression(plan));
    if (server) {
      callArgs.add(Variable.of("Meta"));
    }

    return Function.of(
        helper,
        List.of(
            FunctionClause.of(patterns, RemoteCallExpr.of(delegateMod, opHandler, callArgs))),
        Spec.of(
            helper
                + "("
                + String.join(", ", specParams)
                + ") -> {'ok', "
                + outSym.getName()
                + "} | {'error', term()}"),
        BeamDocumentation.forShape(op).map(Edoc::of).orElse(null),
        null);
  }

  private static String identifierType(
      SymbolProvider sp, BeamResourceIndex index, IdentifierArg arg) {
    Shape shape = index.model().expectShape(arg.shapeId(), Shape.class);
    return sp.toSymbol(shape).getName();
  }

  private static Expression inputExpression(InputPlan plan) {
    String recordName = recordName(plan.inputSymbol());
    if (plan.acceptsFullInput()) {
      if (plan.identifierArgs().isEmpty()) {
        return Variable.of("Input");
      }
      List<RecordField> updates =
          plan.identifierArgs().stream()
              .map(arg -> RecordField.of(arg.fieldName(), Variable.of(arg.paramName())))
              .toList();
      return RecordExpr.update(Variable.of("Input"), recordName, updates);
    }
    List<RecordField> fields =
        plan.identifierArgs().stream()
            .map(arg -> RecordField.of(arg.fieldName(), Variable.of(arg.paramName())))
            .toList();
    return RecordExpr.of(recordName, fields);
  }

  private static String recordName(Symbol symbol) {
    return symbol.getName().replace("()", "");
  }
}
