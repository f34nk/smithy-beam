package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamEdition;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.ir.erlang.ErlModule;
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

        ErlModule module = ErlangEventStreamIr.eventStreamModule(
                moduleName,
                layout.typesHeaderFile(),
                service,
                unions,
                model,
                sp,
                exports);
        ctx.writerDelegator().useFileWriter(layout.eventStreamModuleFile(), writer -> {
            writer.write("$L", module.asString());
        });
    }

    static String helperName(SymbolProvider sp, UnionShape union) {
        return ErlangEventStreamIr.helperName(sp, union);
    }
}
