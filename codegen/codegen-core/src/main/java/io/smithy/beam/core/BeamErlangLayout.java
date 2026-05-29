package io.smithy.beam.core;

/**
 * Single place for Erlang output filenames derived from {@link BeamSettings} and namespace.
 * Call from every {@code createSymbolProvider} and from writers that open the same paths.
 */
public final class BeamErlangLayout {

    private final BeamSettings settings;
    private final String namespace;
    private final String serviceName;

    public BeamErlangLayout(BeamSettings settings, String namespace) {
        this(settings, namespace, null);
    }

    public BeamErlangLayout(BeamSettings settings, String namespace, String serviceName) {
        this.settings = settings;
        this.namespace = namespace;
        this.serviceName = serviceName;
    }

    public String modulePrefix() {
        return settings.resolveModule(namespace);
    }

    public String typesHeaderFile() {
        return modulePrefix() + "_types.hrl";
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
        return serviceSnakeName() + "_rest_json_1";
    }

    public String serverCodecModuleFile() {
        return serverCodecModuleName() + ".erl";
    }

    public String serverCodecModuleName() {
        return serviceSnakeName() + "_rest_json_1";
    }

    public String runtimeTypesHeaderFile() {
        return "runtime_types.hrl";
    }

    public String runtimeHelpersModuleFile() {
        return "runtime_helpers.erl";
    }

    public String runtimeHelpersModuleName() {
        return "runtime_helpers";
    }

    public String runtimeHttpModuleFile() {
        return runtimeHttpModuleName() + ".erl";
    }

    public String runtimeHttpModuleName() {
        return "runtime_http";
    }

    public String paginatorsModuleFile() {
        return paginatorsModuleName() + ".erl";
    }

    public String paginatorsModuleName() {
        return serviceSnakeName() + "_paginators";
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
        return BeamNameUtils.toSnakeCase(requireServiceName());
    }

    private String requireServiceName() {
        if (serviceName == null || serviceName.isEmpty()) {
            throw new IllegalStateException("serviceName is required for server module layout");
        }
        return serviceName;
    }
}
