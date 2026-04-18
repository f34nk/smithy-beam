package io.smithy.beam.erlang.client;

import io.smithy.beam.core.ClientPluginRunner;
import io.smithy.beam.erlang.writer.ErlangWriter;
import io.smithy.beam.protocols.ProtocolRegistrations;

public final class ErlangClientPlugin extends ClientPluginRunner {
    public ErlangClientPlugin() { super("erlang-client-codegen", ErlangWriter::new, ProtocolRegistrations::init); }
}
