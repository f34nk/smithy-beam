package io.smithy.beam.core;

/**
 * Single place for Elixir artifact paths derived from {@link BeamSettings} and namespace.
 */
public final class BeamElixirLayout {

    private final BeamSettings settings;
    private final String namespace;

    public BeamElixirLayout(BeamSettings settings, String namespace) {
        this.settings = settings;
        this.namespace = namespace;
    }

    public String modulePrefix() {
        return settings.resolveModule(namespace);
    }

    public String typesModuleFile() {
        return modulePrefix() + "_types.ex";
    }

    public String clientModuleFile() {
        return modulePrefix() + "_client.ex";
    }

    public String serverModuleFile() {
        return modulePrefix() + "_server.ex";
    }

    public String restJson1ModuleFile() {
        return codecModuleFile();
    }

    public String codecModuleFile() {
        return modulePrefix() + "_rest_json_1.ex";
    }

    public String runtimeTypesModuleFile() {
        return modulePrefix() + "_runtime_types.ex";
    }

    public String runtimeHelpersModuleFile() {
        return modulePrefix() + "_runtime_helpers.ex";
    }

    public String paginatorsModuleFile() {
        return modulePrefix() + "_paginators.ex";
    }

    public String resourceClientModuleFile(String resourceSnakeName) {
        return modulePrefix() + "_" + resourceSnakeName + ".ex";
    }

    public String resourceServerModuleFile(String resourceSnakeName) {
        return modulePrefix() + "_" + resourceSnakeName + "_server.ex";
    }

    public String serverCodecModuleFile() {
        return modulePrefix() + "_server_rest_json_1.ex";
    }

    public String serverCodecModuleName() {
        return modulePrefix() + "_server_rest_json_1";
    }
}
