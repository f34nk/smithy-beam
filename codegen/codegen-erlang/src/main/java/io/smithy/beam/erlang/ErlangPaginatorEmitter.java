package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamPaginationInfo;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.List;

/**
 * Generates a {@code <app>_service_paginators.erl} helper for {@code @paginated} operations.
 * Each paginator function calls the base operation in a recursive loop
 * accumulating items until the output token is undefined.
 */
public final class ErlangPaginatorEmitter {

    private ErlangPaginatorEmitter() {}

    public static void emit(ErlangContext ctx, ServiceShape service) {
        BeamPaginationInfo paginationInfo = BeamPaginationInfo.of(ctx.model());
        List<OperationShape> operations = ErlangTopDown.containedOperationsSorted(ctx.model(), service);
        List<OperationShape> paginated = operations.stream()
                .filter(op -> paginationInfo.isPaginated(service, op))
                .toList();
        if (paginated.isEmpty()) {
            return;
        }

        BeamErlangLayout layout = new BeamErlangLayout(
                ctx.settings(), service.getId().getNamespace(), service.getId().getName());
        String paginatorMod = layout.paginatorsModuleName();
        String clientMod = layout.clientModuleName();
        SymbolProvider sp = ctx.symbolProvider();

        List<String> exports = paginated.stream()
                .flatMap(op -> {
                    String name = "paginate_" + sp.toSymbol(op).getName();
                    return java.util.stream.Stream.of(name + "/2", name + "/3");
                })
                .toList();

        ctx.writerDelegator().useFileWriter(layout.paginatorsModuleFile(), writer -> {
            writer.write("%% Generated paginators for $L.", service.getId());
            writer.write("-module($L).", paginatorMod);
            writer.write("-include(\"$L\").", layout.typesHeaderFile());
            writer.write("-export([$L]).", String.join(", ", exports));
            writer.write("");

            for (OperationShape op : paginated) {
                PaginationInfo pi = paginationInfo.forOperation(service, op).orElseThrow();
                Symbol opSym = sp.toSymbol(op);
                StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
                StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
                String inputRecord = recordName(sp.toSymbol(input));
                String outputRecord = recordName(sp.toSymbol(output));
                String inputToken = fieldName(sp, pi.getInputTokenMember());
                String outputTokenExpr = recordAccess(
                        "Output", output, pi.getOutputTokenMemberPath(), ctx.model(), sp);
                String items = pi.getItemsMember()
                        .map(member -> fieldName(sp, member))
                        .orElse(null);

                writer.write("%% @doc Paginates over all pages of $L.", op.getId());
                writer.write("%%      Returns all accumulated items or {error, Reason}.");
                writer.write("paginate_$L(Config, Input) ->", opSym.getName());
                writer.indent();
                writer.write("paginate_$L(Config, Input, []).", opSym.getName());
                writer.dedent();
                writer.write("");
                writer.write("paginate_$L(Config, Input, Acc) ->", opSym.getName());
                writer.indent();
                writer.write("case $L:$L(Config, Input) of", clientMod, opSym.getName());
                writer.indent();
                writer.write("{ok, Output} ->");
                writer.indent();
                if (items != null) {
                    writer.write("NewAcc = Acc ++ element(#$L.$L, Output),", outputRecord, items);
                } else {
                    writer.write("NewAcc = [Output | Acc],");
                }
                writer.write("case $L of", outputTokenExpr);
                writer.indent();
                if (items != null) {
                    writer.write("undefined -> {ok, NewAcc};");
                } else {
                    writer.write("undefined -> {ok, lists:reverse(NewAcc)};");
                }
                writer.write("NextToken ->");
                writer.indent();
                writer.write("NextInput = Input#$L{$L = NextToken},", inputRecord, inputToken);
                writer.write("paginate_$L(Config, NextInput, NewAcc)", opSym.getName());
                writer.dedent();
                writer.dedent();
                writer.write("end;");
                writer.dedent();
                writer.write("{error, Reason} ->");
                writer.indent();
                writer.write("{error, Reason}");
                writer.dedent();
                writer.dedent();
                writer.write("end.");
                writer.dedent();
                writer.write("");
            }
        });
    }

    private static String recordName(Symbol symbol) {
        return symbol.getName().replace("()", "");
    }

    private static String fieldName(SymbolProvider sp, MemberShape member) {
        return sp.toSymbol(member).getProperty("fieldName", String.class).orElseThrow();
    }

    private static String recordAccess(
            String rootVar,
            StructureShape rootShape,
            List<MemberShape> path,
            Model model,
            SymbolProvider sp) {
        String expr = rootVar;
        Shape container = rootShape;
        for (MemberShape member : path) {
            String record = recordName(sp.toSymbol(container));
            String field = fieldName(sp, member);
            expr = "element(#" + record + "." + field + ", " + expr + ")";
            container = model.expectShape(member.getTarget(), Shape.class);
        }
        return expr;
    }
}
