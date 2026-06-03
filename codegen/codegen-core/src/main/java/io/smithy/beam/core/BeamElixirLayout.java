package io.smithy.beam.core;

import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Single place for Elixir artifact paths derived from {@link BeamSettings} and namespace.
 */
public final class BeamElixirLayout {

    private final BeamSettings settings;
    private final String namespace;
    private final String serviceName;

    public BeamElixirLayout(BeamSettings settings, String namespace) {
        this(settings, namespace, (String) null);
    }

    /**
     * @param serviceName effective service name from {@link BeamServiceNaming#effectiveServiceName},
     *                    not the raw shape id name
     */
    public BeamElixirLayout(BeamSettings settings, String namespace, String serviceName) {
        this.settings = settings;
        this.namespace = namespace;
        this.serviceName = serviceName;
    }

    public BeamElixirLayout(BeamSettings settings, String namespace, ServiceShape service) {
        this(settings, namespace, BeamServiceNaming.effectiveServiceName(service));
    }

    public String modulePrefix() {
        return settings.resolveModule(namespace);
    }

    public String typesModuleFile() {
        return typesModuleName() + ".ex";
    }

    public String typesModuleName() {
        return modulePrefix() + "_types";
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

    public String routerModuleFile() {
        return routerModuleName() + ".ex";
    }

    public String routerModuleName() {
        return serviceSnakeName() + "_router";
    }

    public String restJson1ModuleFile() {
        return codecModuleFile();
    }

    public String codecModuleFile() {
        return clientCodecModuleName() + ".ex";
    }

    public String clientCodecModuleName() {
        return clientCodecModuleName(BeamRestJson1ProtocolCodegen.REST_JSON_1);
    }

    public String clientCodecModuleName(ShapeId protocolTraitId) {
        return serviceSnakeName() + "_" + BeamProtocolModuleSuffix.codecSuffix(protocolTraitId);
    }

    public String runtimeTypesModuleFile() {
        return runtimeTypesModuleName() + ".ex";
    }

    public String runtimeTypesModuleName() {
        return "runtime_types";
    }

    public String runtimeHelpersModuleFile() {
        return runtimeHelpersModuleName() + ".ex";
    }

    public String runtimeHelpersModuleName() {
        return "runtime_helpers";
    }

    public String runtimeHttpModuleFile() {
        return runtimeHttpModuleName() + ".ex";
    }

    public String runtimeHttpModuleName() {
        return "runtime_http";
    }

    public String paginatorsModuleFile() {
        return paginatorsModuleName() + ".ex";
    }

    public String paginatorsModuleName() {
        return serviceSnakeName() + "_paginators";
    }

    public String sigv4ModuleFile() {
        return sigv4ModuleName() + ".ex";
    }

    public String sigv4ModuleName() {
        return serviceSnakeName() + "_sigv4";
    }

    public String credentialsModuleFile() {
        return credentialsModuleName() + ".ex";
    }

    public String credentialsModuleName() {
        return serviceSnakeName() + "_credentials";
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

    public String serverCodecModuleFile() {
        return serverCodecModuleName() + ".ex";
    }

    public String serverCodecModuleName() {
        return serverCodecModuleName(BeamRestJson1ProtocolCodegen.REST_JSON_1);
    }

    public String serverCodecModuleName(ShapeId protocolTraitId) {
        return serviceSnakeName() + "_" + BeamProtocolModuleSuffix.codecSuffix(protocolTraitId);
    }

    private String serviceSnakeName() {
        return BeamNameUtils.toSnakeCase(requireServiceName());
    }

    private String requireServiceName() {
        if (serviceName == null || serviceName.isEmpty()) {
            throw new IllegalStateException("serviceName is required for server module layout");
        }
        return serviceName;
    }
}
