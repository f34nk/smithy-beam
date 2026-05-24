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
        return modulePrefix() + "_rest_json_1.ex";
    }
}
