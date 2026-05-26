package io.smithy.beam.core;

/**
 * Single place for Erlang output filenames derived from {@link BeamSettings} and namespace.
 * Call from every {@code createSymbolProvider} and from writers that open the same paths.
 */
public final class BeamErlangLayout {

    private final BeamSettings settings;
    private final String namespace;

    public BeamErlangLayout(BeamSettings settings, String namespace) {
        this.settings = settings;
        this.namespace = namespace;
    }

    public String modulePrefix() {
        return settings.resolveModule(namespace);
    }

    public String typesHeaderFile() {
        return modulePrefix() + "_types.hrl";
    }

    public String clientModuleFile() {
        return modulePrefix() + "_client.erl";
    }

    public String serverModuleFile() {
        return modulePrefix() + "_server.erl";
    }

    public String clientModuleName() {
        return modulePrefix() + "_client";
    }

    public String serverModuleName() {
        return modulePrefix() + "_server";
    }

    public String codecModuleFile() {
        return modulePrefix() + "_rest_json_1.erl";
    }

    public String codecModuleName() {
        return modulePrefix() + "_rest_json_1";
    }

    public String runtimeTypesHeaderFile() {
        return modulePrefix() + "_runtime_types.hrl";
    }
}
