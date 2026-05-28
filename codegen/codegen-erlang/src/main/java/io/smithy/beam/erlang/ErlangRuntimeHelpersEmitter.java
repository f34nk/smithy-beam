package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.List;

/**
 * Emits {@code <app>_runtime_helpers.erl} with HTTP path label parsing helpers.
 * Emitted once per service when any operation binds {@code @httpLabel} members.
 */
public final class ErlangRuntimeHelpersEmitter {

    private ErlangRuntimeHelpersEmitter() {}

    public static void emitIfNeeded(ErlangContext ctx, ServiceShape service) {
        if (!serviceHasLabelBindings(ctx.model(), service)) {
            return;
        }
        BeamErlangLayout layout = new BeamErlangLayout(ctx.settings(), service.getId().getNamespace());
        String helpersMod = layout.runtimeHelpersModuleName();

        ctx.writerDelegator().useFileWriter(layout.runtimeHelpersModuleFile(), writer -> {
            writer.write("%% Generated runtime helpers for $L.", service.getId());
            writer.write("%% Do not edit.");
            writer.write("-module($L).", helpersMod);
            writer.write("");
            writer.write("-export([parse_labels/2]).");
            writer.write("");
            writer.write("-spec parse_labels(binary(), binary()) -> {ok, map()} | {error, path_mismatch}.");
            writer.write("parse_labels(Path, Template) ->");
            writer.indent();
            writer.write("case match_segments(segments(Path), segments(Template), #{}) of");
            writer.indent();
            writer.write("{ok, Labels} ->");
            writer.indent();
            writer.write("{ok, Labels};");
            writer.dedent();
            writer.write("error ->");
            writer.indent();
            writer.write("{error, path_mismatch}");
            writer.dedent();
            writer.dedent();
            writer.write("end.");
            writer.dedent();
            writer.write("");
            writer.write("segments(Path) ->");
            writer.indent();
            writer.write("Parts = binary:split(Path, <<\"/\">>, [global]),");
            writer.write("[S || S <- Parts, S =/= <<>>].");
            writer.dedent();
            writer.write("");
            writer.write("match_segments([], [], Acc) ->");
            writer.indent();
            writer.write("{ok, Acc};");
            writer.dedent();
            writer.write("match_segments([Seg | RestPath], [TplSeg | RestTpl], Acc) ->");
            writer.indent();
            writer.write("case label_name(TplSeg) of");
            writer.indent();
            writer.write("{ok, Key} ->");
            writer.indent();
            writer.write("Val = uri_string:unquote(Seg),");
            writer.write("match_segments(RestPath, RestTpl, Acc#{Key => Val});");
            writer.dedent();
            writer.write("error ->");
            writer.indent();
            writer.write("case Seg =:= TplSeg of");
            writer.indent();
            writer.write("true -> match_segments(RestPath, RestTpl, Acc);");
            writer.write("false -> error");
            writer.dedent();
            writer.write("end");
            writer.dedent();
            writer.write("end;");
            writer.dedent();
            writer.write("match_segments(_, _, _) ->");
            writer.indent();
            writer.write("error.");
            writer.dedent();
            writer.write("");
            writer.write("label_name(<<\"{\", Rest/binary>>) ->");
            writer.indent();
            writer.write("case binary:split(Rest, <<\"}\">>) of");
            writer.indent();
            writer.write("[Label, <<>>] -> {ok, Label};");
            writer.write("_ -> error");
            writer.dedent();
            writer.write("end;");
            writer.dedent();
            writer.write("label_name(_) ->");
            writer.indent();
            writer.write("error.");
            writer.dedent();
        });
    }

    static boolean serviceHasLabelBindings(Model model, ServiceShape service) {
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
        List<OperationShape> operations = ErlangTopDown.containedOperationsSorted(model, service);
        for (OperationShape op : operations) {
            if (!httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
