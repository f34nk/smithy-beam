package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.List;
import java.util.Optional;

public final class BeamClientPaginationSupport {

    private BeamClientPaginationSupport() {}

    public static boolean isPaginated(Model model, ServiceShape service, OperationShape operation) {
        return BeamPaginationInfo.of(model).isPaginated(service, operation);
    }

    public static PaginationInfo requirePaginationInfo(
            Model model, ServiceShape service, OperationShape operation) {
        return BeamPaginationInfo.of(model)
                .forOperation(service, operation)
                .orElseThrow();
    }

    public static boolean hasItemsMember(PaginationInfo paginationInfo) {
        return !paginationInfo.getItemsMemberPath().isEmpty();
    }

    public static Optional<Symbol> itemsElementSymbol(
            Model model, SymbolProvider symbolProvider, PaginationInfo paginationInfo) {
        List<MemberShape> path = paginationInfo.getItemsMemberPath();
        if (path.isEmpty()) {
            return Optional.empty();
        }
        MemberShape itemsMember = path.get(path.size() - 1);
        Shape target = model.expectShape(itemsMember.getTarget(), Shape.class);
        if (target instanceof ListShape list) {
            return Optional.of(symbolProvider.toSymbol(list.getMember()));
        }
        return Optional.empty();
    }
}
