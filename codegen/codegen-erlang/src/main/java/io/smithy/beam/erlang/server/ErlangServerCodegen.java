package io.smithy.beam.erlang.server;

import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangIntegration;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.CreateContextDirective;
import software.amazon.smithy.codegen.core.directed.CreateSymbolProviderDirective;
import software.amazon.smithy.codegen.core.directed.DirectedCodegen;
import software.amazon.smithy.codegen.core.directed.GenerateEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateErrorDirective;
import software.amazon.smithy.codegen.core.directed.GenerateIntEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.codegen.core.directed.GenerateStructureDirective;
import software.amazon.smithy.codegen.core.directed.GenerateUnionDirective;

/**
 * {@link DirectedCodegen} implementation for the Erlang server plugin.
 */
public final class ErlangServerCodegen
        implements DirectedCodegen<ErlangContext, ErlangServerSettings, ErlangIntegration> {

    @Override
    public SymbolProvider createSymbolProvider(
            CreateSymbolProviderDirective<ErlangServerSettings> d) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ErlangContext createContext(
            CreateContextDirective<ErlangServerSettings, ErlangIntegration> d) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void generateService(GenerateServiceDirective<ErlangContext, ErlangServerSettings> d) {
    }

    @Override
    public void generateStructure(GenerateStructureDirective<ErlangContext, ErlangServerSettings> d) {
    }

    @Override
    public void generateError(GenerateErrorDirective<ErlangContext, ErlangServerSettings> d) {
    }

    @Override
    public void generateUnion(GenerateUnionDirective<ErlangContext, ErlangServerSettings> d) {
    }

    @Override
    public void generateEnumShape(GenerateEnumDirective<ErlangContext, ErlangServerSettings> d) {
    }

    @Override
    public void generateIntEnumShape(
            GenerateIntEnumDirective<ErlangContext, ErlangServerSettings> d) {
    }
}
