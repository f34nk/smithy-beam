package io.smithy.beam.core;

import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/** Single place for Gleam artifact paths derived from {@link BeamSettings} and namespace. */
public final class BeamGleamLayout {

  private final BeamSettings settings;
  private final String namespace;
  private final String serviceName;

  public BeamGleamLayout(BeamSettings settings, String namespace) {
    this(settings, namespace, (String) null);
  }

  /**
   * @param serviceName effective service name from {@link BeamServiceNaming#effectiveServiceName},
   *     not the raw shape id name
   */
  public BeamGleamLayout(BeamSettings settings, String namespace, String serviceName) {
    this.settings = settings;
    this.namespace = namespace;
    this.serviceName = serviceName;
  }

  public BeamGleamLayout(BeamSettings settings, String namespace, ServiceShape service) {
    this(settings, namespace, BeamServiceNaming.effectiveServiceName(service));
  }

  public String typesModuleName() {
    return serviceSnakeName() + "_types";
  }

  public String typesModuleFile() {
    return typesModuleName() + ".gleam";
  }

  public String clientModuleFile() {
    return clientModuleName() + ".gleam";
  }

  public String clientModuleName() {
    return serviceSnakeName() + "_client";
  }

  public String serverModuleFile() {
    return serverModuleName() + ".gleam";
  }

  public String serverModuleName() {
    return serviceSnakeName() + "_server";
  }

  public String behaviourModuleFile() {
    return behaviourModuleName() + ".gleam";
  }

  public String behaviourModuleName() {
    return serviceSnakeName() + "_behaviour";
  }

  public String implModuleName() {
    return serviceSnakeName() + "_impl";
  }

  public String routerModuleFile() {
    return routerModuleName() + ".gleam";
  }

  public String routerModuleName() {
    return serviceSnakeName() + "_router";
  }

  public String codecModuleName(ShapeId protocolTraitId) {
    return codecModuleName(protocolTraitId, List.of());
  }

  public String codecModuleName(
      ShapeId protocolTraitId, List<? extends BeamProtocolIntegration> integrations) {
    return serviceSnakeName()
        + "_"
        + BeamProtocolModuleSuffix.codecSuffix(protocolTraitId, integrations);
  }

  public String codecModuleFile(ShapeId protocolTraitId) {
    return codecModuleName(protocolTraitId) + ".gleam";
  }

  public String codecModuleFile(
      ShapeId protocolTraitId, List<? extends BeamProtocolIntegration> integrations) {
    return codecModuleName(protocolTraitId, integrations) + ".gleam";
  }

  public String runtimeTypesModuleName() {
    return "runtime_types";
  }

  public String runtimeHttpModuleName() {
    return "runtime_http";
  }

  public String waitersModuleFile() {
    return waitersModuleName() + ".gleam";
  }

  public String waitersModuleName() {
    return serviceSnakeName() + "_waiters";
  }

  public String complianceTestsModuleFile() {
    return "test/" + complianceTestsModuleName() + ".gleam";
  }

  public String complianceTestsModuleName() {
    return serviceSnakeName() + "_compliance_test";
  }

  public String eventStreamModuleFile() {
    return eventStreamModuleName() + ".gleam";
  }

  public String eventStreamModuleName() {
    return serviceSnakeName() + "_event_stream";
  }

  public String resourceClientModuleFile(String resourceSnakeName) {
    return resourceClientModuleName(resourceSnakeName) + ".gleam";
  }

  public String resourceClientModuleName(String resourceSnakeName) {
    return resourceSnakeName + "_resource";
  }

  public String resourceServerModuleFile(String resourceSnakeName) {
    return resourceServerModuleName(resourceSnakeName) + ".gleam";
  }

  public String resourceServerModuleName(String resourceSnakeName) {
    return resourceSnakeName + "_resource";
  }

  private String serviceSnakeName() {
    return BeamServiceNaming.effectiveServiceSnakeName(settings, requireServiceName());
  }

  private String requireServiceName() {
    if (serviceName == null || serviceName.isEmpty()) {
      throw new IllegalStateException("serviceName is required for Gleam module layout");
    }
    return serviceName;
  }
}
