package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExModule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import io.smithy.beam.core.BeamRetryIndex;

/**
 * Generates a {@code <Service>Retry} helper that optionally retries operation calls when the
 * returned error matches a modeled {@code @retryable} exception.
 */
public final class ElixirRetryEmitter {

  private ElixirRetryEmitter() {}

  public static void emit(ElixirContext ctx, ServiceShape service) {
    List<StructureShape> retryableErrors = retryableErrors(ctx.model(), service);
    if (retryableErrors.isEmpty()) {
      return;
    }

    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    ExModule module = ElixirRetryIr.retryModule(ctx, service, ctx.model(), ctx.symbolProvider());
    ctx.writerDelegator()
        .useFileWriter(
            layout.retryModuleFile(), writer -> writer.write("$L", module.asString()));
  }

  private static List<StructureShape> retryableErrors(Model model, ServiceShape service) {
    List<StructureShape> errors = new ArrayList<>();
    for (Shape shape : new Walker(model).walkShapes(service)) {
      if (shape instanceof StructureShape structure) {
        BeamRetryIndex.forError(structure)
            .filter(BeamRetryIndex.RetryInfo::retryable)
            .ifPresent(info -> errors.add(structure));
      }
    }
    errors.sort(Comparator.comparing(s -> s.getId().toString()));
    return errors;
  }
}
