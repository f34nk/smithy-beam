package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.ir.erlang.ErlModule;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

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
        boolean checksumBindings = ErlangHttpChecksumIr.serviceHasChecksumOperations(ctx.model(), service);
        if (!awsMetadata && !labelBindings && !checksumBindings) {
            return;
        }
        BeamErlangLayout layout = new BeamErlangLayout(ctx.settings(), service.getId().getNamespace());
        String helpersMod = layout.runtimeHelpersModuleName();

        ErlModule module = ErlangRuntimeHelpersIr.runtimeHelpersModule(
                helpersMod, service, ctx.model(), awsMetadata, labelBindings, checksumBindings);
        ctx.writerDelegator().useFileWriter(layout.runtimeHelpersModuleFile(), writer -> {
            writer.write("$L", module.asString());
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
