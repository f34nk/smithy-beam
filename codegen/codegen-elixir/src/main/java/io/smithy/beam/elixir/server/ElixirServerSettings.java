package io.smithy.beam.elixir.server;

import io.smithy.beam.core.Mode;
import io.smithy.beam.elixir.codegen.ElixirSettings;

/**
 * Settings for the Elixir server codegen plugin.
 *
 * <p>Extends {@link ElixirSettings} and locks {@link #mode()} to {@link Mode#SERVER}.
 */
public class ElixirServerSettings extends ElixirSettings {

    /** Suffix appended to the base module name to form the behaviour module name. */
    private String behaviourSuffix = "Behaviour";

    @Override
    public Mode mode() {
        return Mode.SERVER;
    }

    public String getBehaviourSuffix() {
        return behaviourSuffix;
    }

    public void setBehaviourSuffix(String behaviourSuffix) {
        this.behaviourSuffix = behaviourSuffix;
    }
}
