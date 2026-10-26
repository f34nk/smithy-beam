package io.smithy.beam.gleam;

import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.utils.CodeSection;

/**
 * Code writer for Gleam generated sources.
 *
 * <p>Use {@link #factory()} when constructing a {@link
 * software.amazon.smithy.codegen.core.WriterDelegator}.
 */
public final class GleamWriter extends SymbolWriter<GleamWriter, GleamImports> {

  public GleamWriter() {
    super(new GleamImports());
    setIndentText("  ");
    trimBlankLines();
    trimTrailingSpaces();
  }

  public static Factory<GleamWriter> factory() {
    return (filename, namespace) -> new GleamWriter();
  }

  public void pushGeneratedDocumentationSection() {
    pushState((CodeSection) new GleamWriterSections.GeneratedDocumentation());
  }

  public void pushOperationBodySection() {
    pushState((CodeSection) new GleamWriterSections.OperationBody());
  }
}
