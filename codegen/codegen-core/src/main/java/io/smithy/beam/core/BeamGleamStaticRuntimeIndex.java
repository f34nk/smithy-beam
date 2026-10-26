package io.smithy.beam.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Selects Gleam runtime modules required for a generated service.
 *
 * <p>Only modules referenced by generated code (directly or transitively) are returned. The plugin
 * JAR contains the full runtime tree; this index controls what is written to the output manifest.
 */
public final class BeamGleamStaticRuntimeIndex {

  public record Requirements(List<BeamGleamStaticRuntimeModule> modules) {}

  private BeamGleamStaticRuntimeIndex() {}

  public static Requirements forClient(Model model, ServiceShape service, BeamSettings settings) {
    return requirements(true);
  }

  public static Requirements forServer(Model model, ServiceShape service, BeamSettings settings) {
    return requirements(false);
  }

  private static Requirements requirements(boolean client) {
    Set<BeamGleamStaticRuntimeModule> selected = new LinkedHashSet<>();

    if (client) {
      addWithDependencies(selected, BeamGleamStaticRuntimeModule.HTTP);
      addWithDependencies(selected, BeamGleamStaticRuntimeModule.HTTPC);
    } else {
      addWithDependencies(selected, BeamGleamStaticRuntimeModule.TYPES);
      addWithDependencies(selected, BeamGleamStaticRuntimeModule.UTILS);
    }

    return new Requirements(List.copyOf(selected));
  }

  /** Adds a module and any runtime modules it depends on at compile time. */
  private static void addWithDependencies(
      Set<BeamGleamStaticRuntimeModule> selected, BeamGleamStaticRuntimeModule module) {
    if (!selected.add(module)) {
      return;
    }
    if (module == BeamGleamStaticRuntimeModule.HTTP) {
      addWithDependencies(selected, BeamGleamStaticRuntimeModule.TYPES);
      addWithDependencies(selected, BeamGleamStaticRuntimeModule.UTILS);
    }
  }
}
