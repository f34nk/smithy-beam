package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExModule;
import java.util.List;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Emits {@code runtime_helpers.ex} with HTTP path label parsing and AWS endpoint helpers. Emitted
 * when any operation binds {@code @httpLabel} members or the service has aws.api#service.
 */
public final class ElixirRuntimeHelpersEmitter {

  private ElixirRuntimeHelpersEmitter() {}

  public static void emitIfNeeded(ElixirContext ctx, ServiceShape service) {
    boolean awsMetadata = BeamAwsServiceMetadata.from(service).isPresent();
    boolean labelBindings = serviceHasLabelBindings(ctx.model(), service);
    if (!awsMetadata && !labelBindings) {
      return;
    }
    BeamElixirLayout layout = new BeamElixirLayout(ctx.settings(), service.getId().getNamespace());
    ExModule module = ElixirRuntimeHelpersIr.runtimeHelpersModule(ctx, service);

    ctx.writerDelegator()
        .useFileWriter(
            layout.runtimeHelpersModuleFile(),
            writer -> writer.write("$L", module.asString()));
  }

  static boolean serviceHasLabelBindings(Model model, ServiceShape service) {
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    for (OperationShape op : operations) {
      if (!httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL).isEmpty()) {
        return true;
      }
    }
    return false;
  }
}
