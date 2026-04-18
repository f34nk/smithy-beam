package io.smithy.beam.erlang.codegen;

import io.smithy.beam.core.BeamCodegenException;
import io.smithy.beam.core.BeamSettings;

/**
 * Settings shared by both the Erlang client and server codegen plugins.
 *
 * <p>In addition to the base {@link BeamSettings} fields, every Erlang plugin
 * requires a {@code module} value: the base Erlang module atom that is used as
 * the prefix for all generated module names (e.g. {@code "weather_service"}
 * produces {@code weather_service_client.erl}, {@code weather_service_types.hrl},
 * etc.).
 *
 * <p>{@code module} must be a valid Erlang atom that starts with a lowercase
 * letter and contains only lowercase letters, digits, and underscores.
 */
public abstract class ErlangSettings extends BeamSettings {

    private static final java.util.regex.Pattern MODULE_PATTERN =
            java.util.regex.Pattern.compile("^[a-z][a-z0-9_]*$");

    private String module;

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    @Override
    public void validate() {
        super.validate();
        if (module == null || module.isBlank()) {
            throw new BeamCodegenException("ErlangSettings requires 'module' to be set");
        }
        if (!MODULE_PATTERN.matcher(module).matches()) {
            throw new BeamCodegenException(
                    "ErlangSettings 'module' must match ^[a-z][a-z0-9_]*$ but was: " + module);
        }
    }
}
