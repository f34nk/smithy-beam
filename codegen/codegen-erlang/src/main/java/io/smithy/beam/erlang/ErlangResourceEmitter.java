package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamErlangLayout;
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
public final class ErlangResourceEmitter {

    private ErlangResourceEmitter() {}

    public static void emitClient(ErlangContext ctx, ResourceShape resource) {
        if (!BeamResourceLifecycle.hasEmittableBindings(resource)) {
            return;
        }
        emit(ctx, resource, false);
    }

    public static void emitServer(ErlangContext ctx, ResourceShape resource) {
        if (!BeamResourceLifecycle.hasEmittableBindings(resource)) {
            return;
        }
        emit(ctx, resource, true);
    }

    private static void emit(ErlangContext ctx, ResourceShape resource, boolean server) {
        BeamResourceIndex index = BeamResourceIndex.of(ctx.model());
        BeamErlangLayout layout = new BeamErlangLayout(
                ctx.settings(), ctx.service().getId().getNamespace(), ctx.service());
        SymbolProvider sp = ctx.symbolProvider();
        String resourceSnake = sp.toSymbol(resource).getName();
        String mod = server
                ? layout.resourceServerModuleName(resourceSnake)
                : layout.resourceClientModuleName(resourceSnake);
        String delegateMod = server ? layout.serverModuleName() : layout.clientModuleName();
        String file = server
                ? layout.resourceServerModuleFile(resourceSnake)
                : layout.resourceClientModuleFile(resourceSnake);

        List<HelperBinding> bindings = collectBindings(index, sp, resource);
        List<String> exports = new ArrayList<>();
        for (HelperBinding binding : bindings) {
            InputPlan plan = BeamResourceInputBuilder.plan(
                    index, sp, resource, index.expectOperation(binding.operationId()));
            exports.add(exportName(binding, server) + "/" + helperArity(plan, server));
        }

        ctx.writerDelegator().useFileWriter(file, writer -> {
            writer.pushGeneratedDocumentationSection();
            writer.write("%% Generated resource helpers for $L.", resource.getId());
            BeamDocumentation.forShape(resource).ifPresent(
                    doc -> BeamDocumentation.writeErlangDoc(writer, doc));
            writer.popState();

            writer.write("-module($L).", mod);
            writer.write("-include(\"$L\").", layout.typesHeaderFile());
            if (!server) {
                writer.write("-type client_config() :: #{binary() => term()}.");
            }
            ErlangFormat.writeExport(writer, exports);
            writer.write("");

            for (HelperBinding binding : bindings) {
                emitHelper(writer, index, sp, resource, binding, delegateMod, server);
            }
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

    private static String exportName(HelperBinding binding, boolean server) {
        return server ? "handle_" + binding.helperName() : binding.helperName();
    }

    private static int helperArity(InputPlan plan, boolean server) {
        int arity = 1 + plan.identifierArgs().size() + (plan.acceptsFullInput() ? 1 : 0);
        if (server) {
            arity += 1;
        }
        return arity;
    }

    private static void emitHelper(
            ErlangWriter writer,
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
        String helper = exportName(binding, server);
        String opHandler = server ? "handle_" + opSym.getName() : opSym.getName();

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

        BeamDocumentation.forShape(op).ifPresent(
                doc -> BeamDocumentation.writeErlangDoc(writer, doc));

        ErlangFormat.writeSpec(
                writer,
                helper
                        + "("
                        + String.join(", ", specParams)
                        + ") -> {'ok', "
                        + outSym.getName()
                        + "} | {'error', term()}");
        writer.write("$L($L) ->", helper, paramList(plan, server));
        writer.indent();
        writer.write("$L:$L($L).",
                delegateMod,
                opHandler,
                callArgs(plan, server));
        writer.dedent();
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
            params.add("Ctx");
        } else {
            params.add("Config");
        }
        for (IdentifierArg arg : plan.identifierArgs()) {
            params.add(arg.paramName());
        }
        if (plan.acceptsFullInput()) {
            params.add("Input");
        }
        if (server) {
            params.add("Meta");
        }
        return String.join(", ", params);
    }

    private static String callArgs(InputPlan plan, boolean server) {
        List<String> args = new ArrayList<>();
        if (server) {
            args.add("Ctx");
        } else {
            args.add("Config");
        }
        args.add(inputExpression(plan));
        if (server) {
            args.add("Meta");
        }
        return String.join(", ", args);
    }

    private static String inputExpression(InputPlan plan) {
        String recordName = recordName(plan.inputSymbol());
        if (plan.acceptsFullInput()) {
            if (plan.identifierArgs().isEmpty()) {
                return "Input";
            }
            String updates = plan.identifierArgs().stream()
                    .map(arg -> arg.fieldName() + " = " + arg.paramName())
                    .collect(Collectors.joining(", "));
            return "Input#" + recordName + "{" + updates + "}";
        }
        String fields = plan.identifierArgs().stream()
                .map(arg -> arg.fieldName() + " = " + arg.paramName())
                .collect(Collectors.joining(", "));
        return "#" + recordName + "{" + fields + "}";
    }

    private static String recordName(Symbol symbol) {
        return symbol.getName().replace("()", "");
    }
}
