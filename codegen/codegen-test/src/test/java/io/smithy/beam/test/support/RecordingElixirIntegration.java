package io.smithy.beam.test.support;

import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.elixir.ElixirContext;
import io.smithy.beam.elixir.ElixirIntegration;
import io.smithy.beam.elixir.ElixirWriter;
import io.smithy.beam.elixir.ElixirWriterSections;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.utils.CodeInterceptor;

import java.util.List;

public final class RecordingElixirIntegration implements ElixirIntegration {

    @Override
    public String name() {
        return "recording-elixir-integration";
    }

    @Override
    public SymbolProvider decorateSymbolProvider(
            Model model, BeamSettings settings, SymbolProvider delegate) {
        return delegate;
    }

    @Override
    public List<? extends CodeInterceptor<? extends software.amazon.smithy.utils.CodeSection, ElixirWriter>>
            interceptors(ElixirContext codegenContext) {
        return List.of(
                new CodeInterceptor<ElixirWriterSections.GeneratedDocumentation, ElixirWriter>() {
                    @Override
                    public Class<ElixirWriterSections.GeneratedDocumentation> sectionType() {
                        return ElixirWriterSections.GeneratedDocumentation.class;
                    }

                    @Override
                    public void write(
                            ElixirWriter writer,
                            String previousText,
                            ElixirWriterSections.GeneratedDocumentation section) {
                        writer.writeWithNoFormatting(previousText);
                        writer.write("# recording-elixir-integration was here\n");
                    }
                });
    }

    @Override
    public void configure(BeamSettings settings, ObjectNode integrationSettings) {
        // Example opt-in: read integrationSettings.getBooleanMemberOrDefault("enabled", false)
    }

    @Override
    public void customize(ElixirContext codegenContext) {
        // Example: write auxiliary files through codegenContext.fileManifest()
    }
}
