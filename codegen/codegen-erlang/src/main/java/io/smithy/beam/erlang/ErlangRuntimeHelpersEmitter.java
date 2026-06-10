package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamErlangLayout;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Emits {@code runtime_helpers.erl} with HTTP path label parsing and AWS endpoint helpers.
 * Emitted when any operation binds {@code @httpLabel} members or the service has aws.api#service.
 */
public final class ErlangRuntimeHelpersEmitter {

    private ErlangRuntimeHelpersEmitter() {}

    public static void emitIfNeeded(ErlangContext ctx, ServiceShape service) {
        boolean awsMetadata = BeamAwsServiceMetadata.from(service).isPresent();
        boolean labelBindings = serviceHasLabelBindings(ctx.model(), service);
        boolean checksumBindings = ErlangHttpChecksumEmitter.serviceHasChecksumOperations(ctx.model(), service);
        if (!awsMetadata && !labelBindings && !checksumBindings) {
            return;
        }
        BeamErlangLayout layout = new BeamErlangLayout(ctx.settings(), service.getId().getNamespace());
        String helpersMod = layout.runtimeHelpersModuleName();

        List<String> exports = new ArrayList<>();
        if (labelBindings) {
            exports.add("parse_labels/2");
        }
        if (awsMetadata) {
            exports.add("resolve_base_url/1");
        }
        if (checksumBindings) {
            exports.add("headers_set/3");
            exports.add("base16_encode/1");
            exports.add("sha256_hash/1");
            exports.add("crc32_hash/1");
        }

        ctx.writerDelegator().useFileWriter(layout.runtimeHelpersModuleFile(), writer -> {
            writer.write("%% Generated runtime helpers for $L.", service.getId());
            writer.write("%% Do not edit.");
            writer.write("-module($L).", helpersMod);
            writer.write("");
            ErlangFormat.writeExport(writer, exports);
            writer.write("");

            if (awsMetadata) {
                ErlangFormat.writeSpec(writer, "resolve_base_url(map()) -> binary()");
                writer.write("resolve_base_url(Config) ->");
                writer.indent();
                writer.write("Prefix = maps:get(endpoint_prefix, Config),");
                writer.write("Region = maps:get(region, Config, <<\"us-east-1\">>),");
                writer.write("<<\"https://\", Prefix/binary, \".\", Region/binary, \".amazonaws.com\">>.");
                writer.dedent();
                writer.write("");
            }

            if (labelBindings) {
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
            }

            if (checksumBindings) {
                writer.write("");
                ErlangHttpChecksumEmitter.emitChecksumHelpers(writer);
            }
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
