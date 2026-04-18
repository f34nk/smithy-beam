package io.smithy.beam.erlang.server;

import io.smithy.beam.core.ServerPluginRunner;
import io.smithy.beam.erlang.writer.ErlangWriter;
import io.smithy.beam.protocols.ProtocolRegistrations;

public final class ErlangServerPlugin extends ServerPluginRunner {
    public ErlangServerPlugin() { super("erlang-server-codegen", ErlangWriter::new, ProtocolRegistrations::init); }
}
