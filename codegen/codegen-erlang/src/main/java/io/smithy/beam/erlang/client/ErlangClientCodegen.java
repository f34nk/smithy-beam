package io.smithy.beam.erlang.client;

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
 * {@link DirectedCodegen} implementation for the Erlang client plugin.
 */
public final class ErlangClientCodegen
        implements DirectedCodegen<ErlangContext, ErlangClientSettings, ErlangIntegration> {

    @Override
    public SymbolProvider createSymbolProvider(
            CreateSymbolProviderDirective<ErlangClientSettings> d) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ErlangContext createContext(
            CreateContextDirective<ErlangClientSettings, ErlangIntegration> d) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void generateService(GenerateServiceDirective<ErlangContext, ErlangClientSettings> d) {
    }

    @Override
    public void generateStructure(GenerateStructureDirective<ErlangContext, ErlangClientSettings> d) {
    }

    @Override
    public void generateError(GenerateErrorDirective<ErlangContext, ErlangClientSettings> d) {
    }

    @Override
    public void generateUnion(GenerateUnionDirective<ErlangContext, ErlangClientSettings> d) {
    }

    @Override
    public void generateEnumShape(GenerateEnumDirective<ErlangContext, ErlangClientSettings> d) {
    }

    @Override
    public void generateIntEnumShape(
            GenerateIntEnumDirective<ErlangContext, ErlangClientSettings> d) {
    }
}
