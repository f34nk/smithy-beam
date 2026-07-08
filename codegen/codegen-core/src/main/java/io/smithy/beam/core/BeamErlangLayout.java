package io.smithy.beam.core;

import java.util.List;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Single place for Erlang output filenames derived from {@link BeamSettings} and namespace. Call
 * from every {@code createSymbolProvider} and from writers that open the same paths.
 */
public final class BeamErlangLayout {

  private final BeamSettings settings;
  private final String namespace;
  private final String serviceName;

  public BeamErlangLayout(BeamSettings settings, String namespace) {
    this(settings, namespace, (String) null);
  }

  /**
   * @param serviceName effective service name from {@link BeamServiceNaming#effectiveServiceName},
   *     not the raw shape id name
   */
  public BeamErlangLayout(BeamSettings settings, String namespace, String serviceName) {
    this.settings = settings;
    this.namespace = namespace;
    this.serviceName = serviceName;
  }

  public BeamErlangLayout(BeamSettings settings, String namespace, ServiceShape service) {
    this(settings, namespace, BeamServiceNaming.effectiveServiceName(service));
  }

  public String typesModuleName() {
    return serviceSnakeName() + "_types";
  }

  public String typesHeaderFile() {
    return typesModuleName() + ".hrl";
  }

  public String clientModuleFile() {
    return clientModuleName() + ".erl";
  }

  public String serverModuleFile() {
    return serverModuleName() + ".erl";
  }

  public String clientModuleName() {
    return serviceSnakeName() + "_client";
  }

  public String serverModuleName() {
    return serviceSnakeName() + "_server";
  }

  public String behaviourModuleFile() {
    return behaviourModuleName() + ".erl";
  }

  public String behaviourModuleName() {
    return serviceSnakeName() + "_behaviour";
  }

  public String implModuleName() {
    return serviceSnakeName() + "_impl";
  }

  public String routerModuleFile() {
    return routerModuleName() + ".erl";
  }

  public String routerModuleName() {
    return serviceSnakeName() + "_router";
  }

  public String codecModuleFile() {
    return clientCodecModuleName() + ".erl";
  }

  public String codecModuleName() {
    return clientCodecModuleName();
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

  public String serverCodecModuleFile() {
    return serverCodecModuleName() + ".erl";
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

  public String runtimeTypesHeaderFile() {
    return "http_types.hrl";
  }

  public String runtimeHelpersModuleFile() {
    return "utils.erl";
  }

  public String runtimeHelpersModuleName() {
    return "utils";
  }

  public String runtimeHttpModuleFile() {
    return runtimeHttpModuleName() + ".erl";
  }

  public String runtimeHttpModuleName() {
    return "reqres";
  }

  public String paginatorsModuleFile() {
    return paginatorsModuleName() + ".erl";
  }

  public String paginatorsModuleName() {
    return serviceSnakeName() + "_paginators";
  }

  public String retryModuleFile() {
    return retryModuleName() + ".erl";
  }

  public String retryModuleName() {
    return serviceSnakeName() + "_retry";
  }

  public String waitersModuleFile() {
    return waitersModuleName() + ".erl";
  }

  public String waitersModuleName() {
    return serviceSnakeName() + "_waiters";
  }

  public String complianceTestsModuleFile() {
    return "test/" + complianceTestsModuleName() + ".erl";
  }

  public String complianceTestsModuleName() {
    return serviceSnakeName() + "_compliance_tests";
  }

  public String sigv4ModuleFile() {
    return sigv4ModuleName() + ".erl";
  }

  public String sigv4ModuleName() {
    return serviceSnakeName() + "_sigv4";
  }

  public String presignerModuleFile() {
    return presignerModuleName() + ".erl";
  }

  public String presignerModuleName() {
    return serviceSnakeName() + "_presigner";
  }

  public String endpointsModuleFile() {
    return endpointsModuleName() + ".erl";
  }

  public String endpointsModuleName() {
    return serviceSnakeName() + "_endpoints";
  }

  public String eventStreamModuleFile() {
    return eventStreamModuleName() + ".erl";
  }

  public String eventStreamModuleName() {
    return serviceSnakeName() + "_event_stream";
  }

  public String resourceClientModuleFile(String resourceSnakeName) {
    return resourceClientModuleName(resourceSnakeName) + ".erl";
  }

  public String resourceClientModuleName(String resourceSnakeName) {
    return resourceSnakeName + "_resource";
  }

  public String resourceServerModuleFile(String resourceSnakeName) {
    return resourceServerModuleName(resourceSnakeName) + ".erl";
  }

  public String resourceServerModuleName(String resourceSnakeName) {
    return resourceSnakeName + "_resource";
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
