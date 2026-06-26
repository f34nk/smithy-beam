package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamEdition;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamEventStreamIndex;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.UnionShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Emits Amazon Event Stream encode and decode helpers for {@code @streaming} union shapes.
 */
public final class ErlangEventStreamEmitter {

    private ErlangEventStreamEmitter() {}

    public static void emit(ErlangContext ctx, ServiceShape service) {
        if (!BeamEdition.fromSettings(ctx.settings()).supportsEventStreams()) {
            return;
        }
        BeamEventStreamIndex index = BeamEventStreamIndex.of(ctx.model());
        List<UnionShape> unions = index.eventStreamUnions(service);
        if (unions.isEmpty()) {
            return;
        }

        BeamErlangLayout layout = new BeamErlangLayout(
                ctx.settings(), service.getId().getNamespace(), service);
        String moduleName = layout.eventStreamModuleName();
        SymbolProvider sp = ctx.symbolProvider();
        Model model = ctx.model();

        List<String> exports = new ArrayList<>();
        for (UnionShape union : unions) {
            String helper = helperName(sp, union);
            exports.add("encode_" + helper + "/1");
            exports.add("decode_" + helper + "/1");
        }

        ctx.writerDelegator().useFileWriter(layout.eventStreamModuleFile(), writer -> {
            writer.write("%% Generated Amazon Event Stream helpers for $L.", service.getId());
            writer.write("-module($L).", moduleName);
            writer.write("-include(\"$L\").", layout.typesHeaderFile());
            ErlangFormat.writeExport(writer, exports);
            writer.write("");

            for (UnionShape union : unions) {
                writer.write("%% Event stream helpers for $L.", union.getId());
                ErlangEventStreamIr.writeFunctions(writer, ErlangEventStreamIr.unionHelpers(model, union, sp));
            }

            ErlangEventStreamIr.writeFunctions(writer, List.of(
                    ErlangEventStreamIr.encodeEventHeaders(),
                    ErlangEventStreamIr.headerValue()));
        });
    }

    static String helperName(SymbolProvider sp, UnionShape union) {
        return ErlangEventStreamIr.helperName(sp, union);
    }
}
