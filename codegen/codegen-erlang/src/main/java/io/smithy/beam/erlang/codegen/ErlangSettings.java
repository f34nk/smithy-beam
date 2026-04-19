package io.smithy.beam.erlang.codegen;

import io.smithy.beam.core.BeamCodegenException;
import io.smithy.beam.core.BeamSettings;

/**
 * Erlang-specific codegen settings.
 *
 * <p>Extends {@link BeamSettings} with the {@code module} field, which becomes the
 * base Erlang module atom (e.g. {@code "weather_service"}) shared by the generated
 * client and server modules.
 *
 * <p>{@link #mode()} is abstract here — overridden by {@code ErlangClientSettings}
 * (returns {@code Mode.CLIENT}) and {@code ErlangServerSettings} (returns
 * {@code Mode.SERVER}).
 */
public abstract class ErlangSettings extends BeamSettings {

    private static final String MODULE_PATTERN = "^[a-z][a-z0-9_]*$";

    /** Base Erlang module atom, e.g. {@code "weather_service"}. */
    private String module;

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    /**
     * Validates all settings, including the Erlang-specific {@code module} field.
     *
     * @throws BeamCodegenException if {@code module} is missing or does not match
     *                              {@code ^[a-z][a-z0-9_]*$}
     */
    @Override
    public void validate() {
        super.validate();
        if (module == null) {
            throw new BeamCodegenException(
                    "'module' is required in Erlang codegen settings");
        }
        if (!module.matches(MODULE_PATTERN)) {
            throw new BeamCodegenException(
                    "'module' must be a valid Erlang atom matching "
                    + MODULE_PATTERN + ", got: \"" + module + "\"");
        }
    }
}
