package io.smithy.beam.elixir.client;

import io.smithy.beam.core.ClientPluginRunner;
import io.smithy.beam.elixir.writer.ElixirWriter;
import io.smithy.beam.protocols.ProtocolRegistrations;

public final class ElixirClientPlugin extends ClientPluginRunner {
    public ElixirClientPlugin() { super("elixir-client-codegen", ElixirWriter::new, ProtocolRegistrations::init); }
}
