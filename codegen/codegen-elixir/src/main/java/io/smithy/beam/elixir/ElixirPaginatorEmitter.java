package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamPaginationInfo;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates a {@code <App>Paginators.ex} helper for {@code @paginated} operations.
 */
public final class ElixirPaginatorEmitter {

    private ElixirPaginatorEmitter() {}

    public static void emit(ElixirContext ctx, ServiceShape service) {
        BeamPaginationInfo paginationInfo = BeamPaginationInfo.of(ctx.model());
        List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(
                ctx.model(), service);
        List<OperationShape> paginated = operations.stream()
                .filter(op -> paginationInfo.isPaginated(service, op))
                .toList();
        if (paginated.isEmpty()) {
            return;
        }

        BeamElixirLayout layout = new BeamElixirLayout(ctx.settings(),
                service.getId().getNamespace());
        String paginatorMod = ElixirSymbolProvider.toModuleName(layout.modulePrefix() + "_paginators");
        String clientMod = ElixirSymbolProvider.toModuleName(layout.modulePrefix() + "_client");
        SymbolProvider sp = ctx.symbolProvider();

        ctx.writerDelegator().useFileWriter(
                "lib/" + layout.modulePrefix() + "_paginators.ex", writer -> {
            writer.write("defmodule $L do", paginatorMod);
            writer.indent();
            writer.write("@moduledoc \"Generated paginators for $L (generated).\"", service.getId());
            writer.write("");

            for (OperationShape op : paginated) {
                PaginationInfo pi = paginationInfo.forOperation(service, op).orElseThrow();
                Symbol opSym = sp.toSymbol(op);
                String inputToken = fieldName(sp, pi.getInputTokenMember());
                String outputTokenExpr = mapAccess("output", pi.getOutputTokenMemberPath(), sp);
                String items = pi.getItemsMember()
                        .map(member -> fieldName(sp, member))
                        .orElse(null);

                writer.write("@doc \"Paginates over all pages of $L.\"", op.getId());
                writer.write("def paginate_$L(config, input, acc \\\\ []) do", opSym.getName());
                writer.indent();
                writer.write("case $L.$L(config, input) do", clientMod, opSym.getName());
                writer.indent();
                writer.write("{:ok, output} ->");
                writer.indent();
                if (items != null) {
                    writer.write("new_acc = acc ++ Map.get(output, :$L, [])", items);
                } else {
                    writer.write("new_acc = [output | acc]");
                }
                writer.write("case $L do", outputTokenExpr);
                writer.indent();
                writer.write("nil -> {:ok, Enum.reverse(new_acc)}");
                writer.write("next_token ->");
                writer.indent();
                writer.write("paginate_$L(config, Map.put(input, :$L, next_token), new_acc)",
                        opSym.getName(), inputToken);
                writer.dedent();
                writer.dedent();
                writer.write("end");
                writer.dedent();
                writer.write("{:error, reason} -> {:error, reason}");
                writer.dedent();
                writer.write("end");
                writer.dedent();
                writer.write("end");
                writer.write("");
            }

            writer.dedent();
            writer.write("end");
        });
    }

    private static String fieldName(SymbolProvider sp, MemberShape member) {
        return sp.toSymbol(member).getProperty("fieldName", String.class).orElseThrow();
    }

    private static String mapAccess(String rootVar, List<MemberShape> path, SymbolProvider sp) {
        if (path.size() == 1) {
            return "Map.get(" + rootVar + ", :" + fieldName(sp, path.get(0)) + ")";
        }
        String keys = path.stream()
                .map(member -> ":" + fieldName(sp, member))
                .collect(Collectors.joining(", "));
        return "get_in(" + rootVar + ", [" + keys + "])";
    }
}
