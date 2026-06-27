package io.smithy.beam.test.support;

import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.erlang.ErlangContext;
import io.smithy.beam.erlang.ErlangIntegration;
import io.smithy.beam.erlang.ErlangWriter;
import io.smithy.beam.erlang.ErlangWriterSections;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.utils.CodeInterceptor;

public final class RecordingErlangIntegration implements ErlangIntegration {

  @Override
  public String name() {
    return "recording-erlang-integration";
  }

  @Override
  public SymbolProvider decorateSymbolProvider(
      Model model, BeamSettings settings, SymbolProvider delegate) {
    return delegate;
  }

  @Override
  public List<
          ? extends
              CodeInterceptor<? extends software.amazon.smithy.utils.CodeSection, ErlangWriter>>
      interceptors(ErlangContext codegenContext) {
    return List.of(
        new CodeInterceptor<ErlangWriterSections.GeneratedDocumentation, ErlangWriter>() {
          @Override
          public Class<ErlangWriterSections.GeneratedDocumentation> sectionType() {
            return ErlangWriterSections.GeneratedDocumentation.class;
          }

          @Override
          public void write(
              ErlangWriter writer,
              String previousText,
              ErlangWriterSections.GeneratedDocumentation section) {
            writer.writeWithNoFormatting(previousText);
            writer.write("%% recording-erlang-integration was here\n");
          }
        });
  }

  @Override
  public void configure(BeamSettings settings, ObjectNode integrationSettings) {
    // Example opt-in: read integrationSettings.getBooleanMemberOrDefault("enabled", false)
  }

  @Override
  public void customize(ErlangContext codegenContext) {
    // Example: write auxiliary files through codegenContext.fileManifest()
  }
}
