package io.smithy.beam.erlang.client;

import io.smithy.beam.core.Mode;
import io.smithy.beam.erlang.codegen.ErlangSettings;

/**
 * Settings for the Erlang client codegen plugin.
 *
 * <p>Extends {@link ErlangSettings} and locks {@link #mode()} to {@link Mode#CLIENT}.
 */
public class ErlangClientSettings extends ErlangSettings {

    /** Optional name of a custom endpoint-resolver Erlang module. */
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
