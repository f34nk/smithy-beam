package io.smithy.beam.core;

import java.util.List;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Settings deserialized from the smithy-build.json plugin configuration block by
 * CodegenDirector#settings.
 *
 * <p>Supported properties: "service" -- shape ID of the service to generate (required unless model
 * has exactly one service). "edition" -- required explicit opt-in to generator behavior, e.g.
 * "2026". "relativeDate" -- optional YYYY-MM-DD value forwarded to {@link
 * software.amazon.smithy.codegen.core.directed.CodegenDirector#removeShapesDeprecatedBeforeDate}.
 * "relativeVersion" -- optional SemVer value forwarded to {@link
 * software.amazon.smithy.codegen.core.directed.CodegenDirector#removeShapesDeprecatedBeforeVersion}.
 * "packageVersion" -- optional SemVer for the generated package, recorded in dependency metadata.
 * "protocol" -- optional protocol trait shape id. When set, client and server plugins use this id
 * for wire emission instead of deriving it from the service. When unset, plugins derive the
 * protocol from the sole @protocolDefinition trait on the service, or emit stub-only output when
 * none is present. "name" -- optional snake_case stem for service-scoped module and file names.
 * When unset, derived from the service shape id (honoring rename maps). Must not include role
 * suffixes such as "_client" or "_types".
 * "typesDefstructSplitThreshold" -- optional positive integer. When a nested
 * structure module defstruct literal estimate exceeds this value, Elixir types
 * emission writes that module to a separate file under the default "types/"
 * directory. Defaults to no splitting.
 */
public final class BeamSettings {

  private ShapeId service;
  private String edition;
  private String relativeDate;
  private String relativeVersion;
  private String packageVersion;
  private ShapeId protocol;
  private String name;
  private Integer typesDefstructSplitThreshold;

  public BeamSettings() {}

  public void service(ShapeId service) {
    this.service = service;
  }

  public ShapeId service() {
    return service;
  }

  public void edition(String edition) {
    this.edition = edition;
  }

  public String edition() {
    return edition;
  }

  public void relativeDate(String relativeDate) {
    this.relativeDate = relativeDate;
  }

  public String relativeDate() {
    return relativeDate;
  }

  public void relativeVersion(String relativeVersion) {
    this.relativeVersion = relativeVersion;
  }

  public String relativeVersion() {
    return relativeVersion;
  }

  public void packageVersion(String packageVersion) {
    this.packageVersion = packageVersion;
  }

  public String packageVersion() {
    return packageVersion;
  }

  public void protocol(ShapeId protocol) {
    this.protocol = protocol;
  }

  public ShapeId protocol() {
    return protocol;
  }

  public void name(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  public void typesDefstructSplitThreshold(Integer typesDefstructSplitThreshold) {
    this.typesDefstructSplitThreshold = typesDefstructSplitThreshold;
  }

  public int typesDefstructSplitThreshold() {
    return typesDefstructSplitThreshold != null
        ? typesDefstructSplitThreshold
        : Integer.MAX_VALUE;
  }

  public ShapeId resolveService(Model model) {
    requireEdition();
    if (service() != null) {
      return service();
    }

    List<ServiceShape> services =
        model.getServiceShapes().stream()
            .sorted((a, b) -> a.getId().toString().compareTo(b.getId().toString()))
            .toList();
    if (services.size() == 1) {
      return services.get(0).getId();
    }
    if (services.isEmpty()) {
      throw new CodegenException("No service shape found. Configure the 'service' setting.");
    }
    throw new CodegenException(
        "Multiple service shapes found. Configure the 'service' setting explicitly.");
  }

  private void requireEdition() {
    if (edition() == null || edition().isBlank()) {
      throw new CodegenException(
          "Missing required 'edition' setting. Set edition to opt in to generator behavior.");
    }
  }
}
