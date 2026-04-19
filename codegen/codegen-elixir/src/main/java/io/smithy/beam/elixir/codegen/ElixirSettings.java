package io.smithy.beam.elixir.codegen;

import io.smithy.beam.core.BeamCodegenException;
import io.smithy.beam.core.BeamSettings;

/**
 * Elixir-specific codegen settings.
 *
 * <p>Extends {@link BeamSettings} with the {@code namespace} field, which becomes the
 * top-level Elixir module name (e.g. {@code "WeatherService"}) shared by the generated
 * client and server modules.
 *
 * <p>{@link #mode()} is abstract here — overridden by {@code ElixirClientSettings}
 * (returns {@code Mode.CLIENT}) and {@code ElixirServerSettings} (returns
 * {@code Mode.SERVER}).
 */
public abstract class ElixirSettings extends BeamSettings {

    private static final String NAMESPACE_PATTERN =
            "^[A-Z][A-Za-z0-9]*(\\.[A-Z][A-Za-z0-9]*)*$";

    /** Top-level Elixir module name, e.g. {@code "WeatherService"}. */
    private String namespace;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Validates all settings, including the Elixir-specific {@code namespace} field.
     *
     * @throws BeamCodegenException if {@code namespace} is missing or does not match
     *                              the required pattern
     */
    @Override
    public void validate() {
        super.validate();
        if (namespace == null) {
            throw new BeamCodegenException(
                    "'namespace' is required in Elixir codegen settings");
        }
        if (!namespace.matches(NAMESPACE_PATTERN)) {
            throw new BeamCodegenException(
                    "'namespace' must be a valid Elixir module name matching "
                    + NAMESPACE_PATTERN + ", got: \"" + namespace + "\"");
        }
    }
}
