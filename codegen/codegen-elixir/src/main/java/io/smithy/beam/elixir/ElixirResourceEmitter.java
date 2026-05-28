package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamResourceIndex;
import io.smithy.beam.core.BeamResourceInputBuilder;
import io.smithy.beam.core.BeamResourceInputBuilder.IdentifierArg;
import io.smithy.beam.core.BeamResourceInputBuilder.InputPlan;
import io.smithy.beam.core.BeamResourceLifecycle;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates per-resource lifecycle helper modules for client and server passes.
 */
public final class ElixirResourceEmitter {

    private ElixirResourceEmitter() {}

    public static void emitClient(ElixirContext ctx, ResourceShape resource) {
        if (!BeamResourceLifecycle.hasEmittableBindings(resource)) {
            return;
        }
        emit(ctx, resource, false);
    }

    public static void emitServer(ElixirContext ctx, ResourceShape resource) {
        if (!BeamResourceLifecycle.hasEmittableBindings(resource)) {
            return;
        }
        emit(ctx, resource, true);
    }

    private static void emit(ElixirContext ctx, ResourceShape resource, boolean server) {
        BeamResourceIndex index = BeamResourceIndex.of(ctx.model());
        BeamElixirLayout layout = new BeamElixirLayout(
                ctx.settings(), ctx.service().getId().getNamespace());
        SymbolProvider sp = ctx.symbolProvider();
        String resourceSnake = sp.toSymbol(resource).getName();
        String mod = ElixirSymbolProvider.toModuleName(
                layout.modulePrefix() + "_" + resourceSnake + (server ? "_server" : ""));
        String delegateMod = ElixirSymbolProvider.toModuleName(
                layout.modulePrefix() + (server ? "_server" : "_client"));
        String typesMod = ElixirSymbolProvider.toModuleName(layout.modulePrefix());
        String file = server
                ? layout.resourceServerModuleFile(resourceSnake)
                : layout.resourceClientModuleFile(resourceSnake);

        List<HelperBinding> bindings = collectBindings(index, sp, resource);

        ctx.writerDelegator().useFileWriter(file, writer -> {
            writer.write("defmodule $L do", mod);
            writer.indent();
            writer.pushGeneratedDocumentationSection();
            BeamDocumentation.forShape(resource).ifPresent(
                    doc -> BeamDocumentation.writeElixirModuledoc(writer, doc));
            if (BeamDocumentation.forShape(resource).isEmpty()) {
                writer.write("@moduledoc \"Lifecycle helpers for $L.\"", resource.getId());
            }
            writer.popState();
            writer.write("alias $L", delegateMod);
            writer.write("alias $L", typesMod);
            writer.write("");

            for (HelperBinding binding : bindings) {
                emitHelper(writer, index, sp, resource, binding, delegateMod, typesMod, server);
            }

            writer.dedent();
            writer.write("end");
        });
    }

    private static List<HelperBinding> collectBindings(
            BeamResourceIndex index,
            SymbolProvider sp,
            ResourceShape resource) {
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

    private record HelperBinding(String helperName, ShapeId operationId) {}

    private static void emitHelper(
            ElixirWriter writer,
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

        List<String> specParams = new ArrayList<>();
        if (server) {
            specParams.add("term()");
        } else {
            specParams.add("map()");
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

        BeamDocumentation.forShape(op).ifPresent(
                doc -> BeamDocumentation.writeElixirDoc(writer, doc));

        writer.write(
                "@spec $L($L) :: {:ok, $L} | {:error, term()}",
                helper,
                String.join(", ", specParams),
                outType);
        writer.write("def $L($L) do", helper, paramList(plan, server));
        writer.indent();
        writer.write("$L.$L($L)", delegateMod, opHandler, callArgs(plan, typesMod, server));
        writer.dedent();
        writer.write("end");
        writer.write("");
    }

    private static String identifierType(
            SymbolProvider sp, BeamResourceIndex index, IdentifierArg arg) {
        Shape shape = index.model().expectShape(arg.shapeId(), Shape.class);
        return sp.toSymbol(shape).getName();
    }

    private static String paramList(InputPlan plan, boolean server) {
        List<String> params = new ArrayList<>();
        if (server) {
            params.add("ctx");
        } else {
            params.add("config");
        }
        for (IdentifierArg arg : plan.identifierArgs()) {
            params.add(arg.paramName());
        }
        if (plan.acceptsFullInput()) {
            params.add("input");
        }
        if (server) {
            params.add("meta");
        }
        return String.join(", ", params);
    }

    private static String callArgs(InputPlan plan, String typesMod, boolean server) {
        List<String> args = new ArrayList<>();
        if (server) {
            args.add("ctx");
        } else {
            args.add("config");
        }
        args.add(inputExpression(plan, typesMod));
        if (server) {
            args.add("meta");
        }
        return String.join(", ", args);
    }

    private static String inputExpression(InputPlan plan, String typesMod) {
        String struct = typesMod + "." + plan.inputSymbol().getName();
        if (plan.acceptsFullInput()) {
            if (plan.identifierArgs().isEmpty()) {
                return "input";
            }
            String updates = plan.identifierArgs().stream()
                    .map(arg -> arg.fieldName() + ": " + arg.paramName())
                    .collect(Collectors.joining(", "));
            return "%{input | " + updates + "}";
        }
        String fields = plan.identifierArgs().stream()
                .map(arg -> arg.fieldName() + ": " + arg.paramName())
                .collect(Collectors.joining(", "));
        return "%" + struct + "{" + fields + "}";
    }
}
