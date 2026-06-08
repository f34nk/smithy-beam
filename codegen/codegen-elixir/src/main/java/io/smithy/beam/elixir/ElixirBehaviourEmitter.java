package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamElixirLayout;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.List;

/**
 * Generates {@code {service}_behaviour.ex} with one {@code @callback} per operation
 * and {@code callbacks/0} listing expected handler callbacks.
 */
final class ElixirBehaviourEmitter {

    private ElixirBehaviourEmitter() {}

    static void beginService(
            ElixirContext ctx, ServiceShape service, List<OperationShape> operations) {
        String ns = service.getId().getNamespace();
        BeamElixirLayout layout = new BeamElixirLayout(ctx.settings(), ns, service);
        String behaviourMod = ElixirSymbolProvider.toModuleName(layout.behaviourModuleName());
        String typesModuleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());

        ctx.writerDelegator().useFileWriter(layout.behaviourModuleFile(), writer -> {
            writer.pushModuleHeaderSection();
            writer.write("defmodule $L do", behaviourMod);
            writer.popState();

            writer.indent();

            writer.pushGeneratedDocumentationSection();
            writer.openBlock("@moduledoc \"\"\"");
            writer.write("Generated Elixir server behaviour for $L.", service.getId());
            writer.closeBlock("\"\"\"");
            writer.popState();

            writer.pushDependenciesSection();
            writer.write("alias $L", typesModuleName);
            writer.popState();
        });
    }

    static void emitOperationCallback(
            ElixirContext ctx, OperationShape op, SymbolProvider sp) {
        Symbol opSym = sp.toSymbol(op);
        StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
        Symbol inSym = sp.toSymbol(input);
        Symbol outSym = sp.toSymbol(output);
        String handler = "handle_" + opSym.getName();

        String ns = ctx.service().getId().getNamespace();
        BeamElixirLayout layout = new BeamElixirLayout(ctx.settings(), ns, ctx.service());
        String typesModuleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
        String inType = ElixirTopDown.structureSpecType(typesModuleName, inSym);
        String outType = ElixirTopDown.structureSpecType(typesModuleName, outSym);

        ctx.writerDelegator().useFileWriter(layout.behaviourModuleFile(), writer -> {
            writer.pushOperationBodySection();
            BeamDocumentation.forShape(op).ifPresent(doc -> BeamDocumentation.writeElixirDoc(writer, doc));
            ElixirFormat.writeSpec(
                    writer,
                    "@callback",
                    handler,
                    "term(), " + inType + ", term()",
                    "{:ok, " + outType + "} | {:error, term()}");
            writer.write("");
            writer.popState();
        });
    }

    static void finishService(
            ElixirContext ctx,
            List<OperationShape> operations,
            SymbolProvider sp) {
        String ns = ctx.service().getId().getNamespace();
        BeamElixirLayout layout = new BeamElixirLayout(ctx.settings(), ns, ctx.service());

        ctx.writerDelegator().useFileWriter(layout.behaviourModuleFile(), writer -> {
            writer.pushOperationBodySection();
            ElixirFormat.writeSpec(writer, "@spec", "callbacks() :: [{atom(), non_neg_integer()}]");
            writer.write("def callbacks do");
            writer.indent();
            writer.write("[");
            writer.indent();
            for (OperationShape op : operations) {
                Symbol opSym = sp.toSymbol(op);
                writer.write("{:handle_$L, 3},", opSym.getName());
            }
            writer.dedent();
            writer.write("]");
            writer.dedent();
            writer.write("end");
            writer.write("");
            writer.popState();

            writer.dedent();

            writer.pushModuleHeaderSection();
            writer.write("end");
            writer.popState();
        });
    }
}
