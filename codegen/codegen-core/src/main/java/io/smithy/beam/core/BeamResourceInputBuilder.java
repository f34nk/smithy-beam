package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Describes how to construct an operation input from resource identifier arguments.
 */
public final class BeamResourceInputBuilder {

    private BeamResourceInputBuilder() {}

    public record IdentifierArg(String paramName, String fieldName, ShapeId shapeId) {}

    public record InputPlan(
            StructureShape inputShape,
            Symbol inputSymbol,
            List<IdentifierArg> identifierArgs,
            boolean acceptsFullInput) {}

    public static InputPlan plan(
            BeamResourceIndex index,
            SymbolProvider sp,
            ResourceShape resource,
            OperationShape operation) {
        StructureShape input = index.model().expectShape(
                operation.getInputShape(), StructureShape.class);
        Symbol inputSymbol = sp.toSymbol(input);
        LinkedHashMap<String, ShapeId> mergedIds = new LinkedHashMap<>();
        for (ShapeId resourceId : index.identifierChain(resource)) {
            ResourceShape r = index.model().expectShape(resourceId, ResourceShape.class);
            mergedIds.putAll(index.identifiers(r));
        }
        List<IdentifierArg> args = new ArrayList<>();
        for (Map.Entry<String, ShapeId> entry : mergedIds.entrySet()) {
            input.getMember(entry.getKey()).ifPresent(member -> {
                String field = sp.toSymbol(member)
                        .getProperty("fieldName", String.class)
                        .orElse(BeamNameUtils.toSnakeCase(entry.getKey()));
                args.add(new IdentifierArg(
                        BeamNameUtils.toSnakeCase(entry.getKey()),
                        field,
                        entry.getValue()));
            });
        }
        boolean acceptsFullInput = !input.members().isEmpty()
                && input.members().size() > args.size();
        return new InputPlan(input, inputSymbol, args, acceptsFullInput);
    }
}
