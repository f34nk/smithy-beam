package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.ir.erlang.ErlAttribute;
import io.smithy.beam.ir.erlang.ErlCallbackSpec;
import io.smithy.beam.ir.erlang.ErlComment;
import io.smithy.beam.ir.erlang.ErlFunctionDoc;
import io.smithy.beam.ir.erlang.ErlModule;
import io.smithy.beam.ir.erlang.ErlModuleAttribute;
import io.smithy.beam.ir.erlang.ErlPreambleEntry;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.ArrayList;
import java.util.List;

final class ErlangBehaviourIr {
    private ErlangBehaviourIr() {}

    static ErlModule behaviourModule(
            BeamErlangLayout layout, ServiceShape service, List<ErlCallbackSpec> callbacks) {
        List<ErlPreambleEntry> preamble = List.of(
                ErlComment.comment("Generated Erlang server behaviour for " + service.getId() + "."));
        List<ErlModuleAttribute> attributes = new ArrayList<>();
        attributes.add(new ErlAttribute("include", "\"" + layout.typesHeaderFile() + "\""));
        attributes.addAll(callbacks);
        return new ErlModule(layout.behaviourModuleName(), preamble, attributes, List.of());
    }

    static ErlCallbackSpec operationCallback(
            ErlangContext ctx, OperationShape op, SymbolProvider sp) {
        Symbol opSym = sp.toSymbol(op);
        StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
        Symbol inSym = sp.toSymbol(input);
        Symbol outSym = sp.toSymbol(output);
        String name = "handle_" + opSym.getName();
        String inputTypes = "Ctx :: term(), Input :: " + inSym.getName() + ", Meta :: term()";
        String outputTypes = "{ok, " + outSym.getName() + "} | {error, term()}";
        ErlFunctionDoc doc = BeamDocumentation.forShape(op)
                .map(ErlFunctionDoc::functionDoc)
                .orElse(null);
        return ErlCallbackSpec.callbackSpec(name, inputTypes, outputTypes, doc);
    }
}
