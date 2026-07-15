package io.smithy.beam.core;

import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/** Single place for Elixir artifact paths derived from {@link BeamSettings} and namespace. */
public final class BeamElixirLayout {

  private final BeamSettings settings;
  private final String namespace;
  private final String serviceName;

  public BeamElixirLayout(BeamSettings settings, String namespace) {
    this(settings, namespace, (String) null);
  }

  /**
   * @param serviceName effective service name from {@link BeamServiceNaming#effectiveServiceName},
   *     not the raw shape id name
   */
  public BeamElixirLayout(BeamSettings settings, String namespace, String serviceName) {
    this.settings = settings;
    this.namespace = namespace;
    this.serviceName = serviceName;
  }

  public BeamElixirLayout(BeamSettings settings, String namespace, ServiceShape service) {
    this(settings, namespace, BeamServiceNaming.effectiveServiceName(service));
  }

  public String typesModuleName() {
    return serviceSnakeName() + "_types";
  }

  public String typesModuleFile() {
    return typesModuleName() + ".ex";
  }

  public String nestedTypesDirectory() {
    return "types";
  }

  public String nestedTypeModuleFile(String nestedShapeName) {
    return nestedTypesDirectory() + "/" + BeamNameUtils.toSnakeCase(nestedShapeName) + ".ex";
  }

  public String clientModuleFile() {
    return clientModuleName() + ".ex";
  }

  public String clientModuleName() {
    return serviceSnakeName() + "_client";
  }

  public String serverModuleFile() {
    return serverModuleName() + ".ex";
  }

  public String serverModuleName() {
    return serviceSnakeName() + "_server";
  }

  public String behaviourModuleFile() {
    return behaviourModuleName() + ".ex";
  }

  public String behaviourModuleName() {
    return serviceSnakeName() + "_behaviour";
  }

  public String implModuleName() {
    return serviceSnakeName() + "_impl";
  }

  public String routerModuleFile() {
    return routerModuleName() + ".ex";
  }

  public String routerModuleName() {
    return serviceSnakeName() + "_router";
  }

  public String clientCodecModuleName() {
    return clientCodecModuleName(BeamProtocolIds.REST_JSON_1);
  }

  public String clientCodecModuleName(ShapeId protocolTraitId) {
    return clientCodecModuleName(protocolTraitId, List.of());
  }

  public String clientCodecModuleName(
      ShapeId protocolTraitId, List<? extends BeamProtocolIntegration> integrations) {
    return serviceSnakeName()
        + "_"
        + BeamProtocolModuleSuffix.codecSuffix(protocolTraitId, integrations);
  }

  public String runtimeTypesModuleName() {
    return "runtime_types";
  }

  public String runtimeHttpModuleName() {
    return "runtime_http";
  }

  public String waitersModuleFile() {
    return waitersModuleName() + ".ex";
  }

  public String waitersModuleName() {
    return serviceSnakeName() + "_waiters";
  }

  public String complianceTestsModuleFile() {
    return "test/" + complianceTestsModuleName() + ".ex";
  }

  public String complianceTestsModuleName() {
    return serviceSnakeName() + "_compliance_test";
  }

  public String eventStreamModuleFile() {
    return eventStreamModuleName() + ".ex";
  }

  public String eventStreamModuleName() {
    return serviceSnakeName() + "_event_stream";
  }

  public String resourceClientModuleFile(String resourceSnakeName) {
    return resourceClientModuleName(resourceSnakeName) + ".ex";
  }

  public String resourceClientModuleName(String resourceSnakeName) {
    return resourceSnakeName + "_resource";
  }

  public String resourceServerModuleFile(String resourceSnakeName) {
    return resourceServerModuleName(resourceSnakeName) + ".ex";
  }

  public String resourceServerModuleName(String resourceSnakeName) {
    return resourceSnakeName + "_resource";
  }

  public String serverCodecModuleName() {
    return serverCodecModuleName(BeamProtocolIds.REST_JSON_1);
  }

  public String serverCodecModuleName(ShapeId protocolTraitId) {
    return serverCodecModuleName(protocolTraitId, List.of());
  }

  public String serverCodecModuleName(
      ShapeId protocolTraitId, List<? extends BeamProtocolIntegration> integrations) {
    return serviceSnakeName()
        + "_"
        + BeamProtocolModuleSuffix.codecSuffix(protocolTraitId, integrations);
  }

  private String serviceSnakeName() {
    return BeamServiceNaming.effectiveServiceSnakeName(settings, requireServiceName());
  }

  private String requireServiceName() {
    if (serviceName == null || serviceName.isEmpty()) {
      throw new IllegalStateException("serviceName is required for server module layout");
    }
    return serviceName;
  }
}
