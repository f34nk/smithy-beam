package io.smithy.beam.elixir.server;

import io.smithy.beam.core.ServerPluginRunner;
import io.smithy.beam.elixir.writer.ElixirWriter;
import io.smithy.beam.protocols.ProtocolRegistrations;

public final class ElixirServerPlugin extends ServerPluginRunner {
    public ElixirServerPlugin() { super("elixir-server-codegen", ElixirWriter::new, ProtocolRegistrations::init); }
}
