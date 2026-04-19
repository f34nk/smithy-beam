package io.smithy.beam.erlang.server;

import io.smithy.beam.core.Mode;
import io.smithy.beam.erlang.codegen.ErlangSettings;

/**
 * Settings for the Erlang server codegen plugin.
 *
 * <p>Extends {@link ErlangSettings} and locks {@link #mode()} to {@link Mode#SERVER}.
 */
public class ErlangServerSettings extends ErlangSettings {

    /** Suffix appended to the base module name to form the behaviour module name. */
    private String behaviourSuffix = "_handler";

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
