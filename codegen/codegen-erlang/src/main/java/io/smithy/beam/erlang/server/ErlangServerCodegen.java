package io.smithy.beam.erlang.server;

import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangIntegration;
import io.smithy.beam.erlang.codegen.ErlangSettings;
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
 *
 * <p>{@code S} is fixed to {@link ErlangSettings} (the base settings type) because
 * {@link ErlangContext} is typed on {@code ErlangSettings}. Server-specific
 * settings ({@link ErlangServerSettings}) are accessed via {@code (ErlangServerSettings)
 * d.context().settings()} inside methods that need them.
 */
public final class ErlangServerCodegen
        implements DirectedCodegen<ErlangContext, ErlangSettings, ErlangIntegration> {

    @Override
    public SymbolProvider createSymbolProvider(
            CreateSymbolProviderDirective<ErlangSettings> d) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ErlangContext createContext(
            CreateContextDirective<ErlangSettings, ErlangIntegration> d) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void generateService(GenerateServiceDirective<ErlangContext, ErlangSettings> d) {
    }

    @Override
    public void generateStructure(GenerateStructureDirective<ErlangContext, ErlangSettings> d) {
    }

    @Override
    public void generateError(GenerateErrorDirective<ErlangContext, ErlangSettings> d) {
    }

    @Override
    public void generateUnion(GenerateUnionDirective<ErlangContext, ErlangSettings> d) {
    }

    @Override
    public void generateEnumShape(GenerateEnumDirective<ErlangContext, ErlangSettings> d) {
    }

    @Override
    public void generateIntEnumShape(
            GenerateIntEnumDirective<ErlangContext, ErlangSettings> d) {
    }
}
