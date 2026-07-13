package io.smithy.beam.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.traits.EndpointTrait;

/**
 * Selects Elixir runtime modules required for a generated service.
 *
 * <p>Only modules referenced by generated code (directly or transitively) are returned. The plugin
 * JAR contains the full runtime tree; this index controls what is written to the output manifest.
 */
public final class BeamElixirStaticRuntimeIndex {

  public record Requirements(List<BeamElixirStaticRuntimeModule> modules) {}

  private BeamElixirStaticRuntimeIndex() {}

  public static Requirements forClient(Model model, ServiceShape service, BeamSettings settings) {
    return requirements(model, service, settings, true);
  }

  public static Requirements forServer(Model model, ServiceShape service, BeamSettings settings) {
    return requirements(model, service, settings, false);
  }

  private static Requirements requirements(
      Model model, ServiceShape service, BeamSettings settings, boolean client) {
    Set<BeamElixirStaticRuntimeModule> selected = new LinkedHashSet<>();

    if (client) {
      addWithDependencies(selected, BeamElixirStaticRuntimeModule.HTTP_RUNTIME);
    } else {
      addWithDependencies(selected, BeamElixirStaticRuntimeModule.HTTP_TYPES);
    }

    if (serviceUsesHostLabels(model, service) || BeamS3CustomizationIndex.isS3Service(service)) {
      addWithDependencies(selected, BeamElixirStaticRuntimeModule.UTILS);
    }

    if (client && BeamSigV4Metadata.from(service).isPresent()) {
      addWithDependencies(selected, BeamElixirStaticRuntimeModule.AWS_SIGV4);
    }

    if (serviceHasHttpChecksum(model, service)) {
      addWithDependencies(selected, BeamElixirStaticRuntimeModule.HTTP_CHECKSUM);
    }

    if (BeamEventStreamIndex.of(model).serviceHasEventStreams(service)) {
      addWithDependencies(selected, BeamElixirStaticRuntimeModule.AWS_EVENT_STREAM);
    }

    return new Requirements(List.copyOf(selected));
  }

  /** Adds a module and any runtime modules it depends on at compile time. */
  private static void addWithDependencies(
      Set<BeamElixirStaticRuntimeModule> selected, BeamElixirStaticRuntimeModule module) {
    if (!selected.add(module)) {
      return;
    }
    if (module == BeamElixirStaticRuntimeModule.HTTP_RUNTIME) {
      addWithDependencies(selected, BeamElixirStaticRuntimeModule.HTTP_TYPES);
      addWithDependencies(selected, BeamElixirStaticRuntimeModule.UTILS);
    } else if (module == BeamElixirStaticRuntimeModule.AWS_SIGV4) {
      addWithDependencies(selected, BeamElixirStaticRuntimeModule.HTTP_TYPES);
      addWithDependencies(selected, BeamElixirStaticRuntimeModule.UTILS);
    }
  }

  private static boolean serviceUsesHostLabels(Model model, ServiceShape service) {
    TopDownIndex topDown = TopDownIndex.of(model);
    BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
    for (OperationShape operation : topDown.getContainedOperations(service)) {
      if (!hostLabelIndex.hostLabelMembers(operation).isEmpty()
          && operation.hasTrait(EndpointTrait.class)) {
        return true;
      }
    }
    return false;
  }

  private static boolean serviceHasHttpChecksum(Model model, ServiceShape service) {
    TopDownIndex topDown = TopDownIndex.of(model);
    BeamHttpChecksumIndex index = BeamHttpChecksumIndex.of(model);
    for (OperationShape operation : topDown.getContainedOperations(service)) {
      if (index.hasChecksumBehavior(operation)) {
        return true;
      }
    }
    return false;
  }
}
