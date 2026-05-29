package io.smithy.beam.core;

/**
 * Single place for Elixir artifact paths derived from {@link BeamSettings} and namespace.
 */
public final class BeamElixirLayout {

    private final BeamSettings settings;
    private final String namespace;
    private final String serviceName;

    public BeamElixirLayout(BeamSettings settings, String namespace) {
        this(settings, namespace, null);
    }

    public BeamElixirLayout(BeamSettings settings, String namespace, String serviceName) {
        this.settings = settings;
        this.namespace = namespace;
        this.serviceName = serviceName;
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
        return serviceSnakeName() + "_rest_json_1";
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
        return serviceSnakeName() + "_rest_json_1";
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
