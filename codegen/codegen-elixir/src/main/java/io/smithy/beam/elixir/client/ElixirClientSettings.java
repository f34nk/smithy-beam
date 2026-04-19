package io.smithy.beam.elixir.client;

import io.smithy.beam.core.Mode;
import io.smithy.beam.elixir.codegen.ElixirSettings;

/**
 * Settings for the Elixir client codegen plugin.
 *
 * <p>Extends {@link ElixirSettings} and locks {@link #mode()} to {@link Mode#CLIENT}.
 */
public class ElixirClientSettings extends ElixirSettings {

    /** Optional name of a custom endpoint-resolver Elixir module. */
    private String endpointResolver;

    @Override
    public Mode mode() {
        return Mode.CLIENT;
    }

    public String getEndpointResolver() {
        return endpointResolver;
    }

    public void setEndpointResolver(String endpointResolver) {
        this.endpointResolver = endpointResolver;
    }
}
