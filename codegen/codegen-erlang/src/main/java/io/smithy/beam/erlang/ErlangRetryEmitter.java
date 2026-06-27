package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamRetryIndex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 * Generates a {@code <service>_retry.erl} helper that optionally retries operation calls when the
 * returned error matches a modeled {@code @retryable} structure.
 */
public final class ErlangRetryEmitter {

  private ErlangRetryEmitter() {}

  public static void emit(ErlangContext ctx, ServiceShape service) {
    List<StructureShape> retryableErrors = retryableErrors(ctx.model(), service);
    if (retryableErrors.isEmpty()) {
      return;
    }

    BeamErlangLayout layout =
        new BeamErlangLayout(ctx.settings(), service.getId().getNamespace(), service);
    String retryMod = layout.retryModuleName();

    ctx.writerDelegator()
        .useFileWriter(
            layout.retryModuleFile(),
            writer -> {
              writer.write(
                  "$L",
                  ErlangRetryIr.retryModule(
                          retryMod,
                          layout.typesHeaderFile(),
                          service,
                          ctx.model(),
                          ctx.symbolProvider())
                      .asString());
            });
  }

  private static List<StructureShape> retryableErrors(
      software.amazon.smithy.model.Model model, ServiceShape service) {
    List<StructureShape> errors = new ArrayList<>();
    for (software.amazon.smithy.model.shapes.Shape shape :
        new software.amazon.smithy.model.neighbor.Walker(model).walkShapes(service)) {
      if (shape instanceof StructureShape structure
          && BeamRetryIndex.forError(structure).isPresent()
          && BeamRetryIndex.forError(structure).orElseThrow().retryable()) {
        errors.add(structure);
      }
    }
    errors.sort(Comparator.comparing(s -> s.getId().toString()));
    return errors;
  }
}
