package io.smithy.beam.erlang;

import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.utils.CodeSection;

/**
 * Code writer for Erlang generated sources.
 *
 * <p>Use ErlangWriter.factory() when constructing a WriterDelegator.
 */
public final class ErlangWriter extends SymbolWriter<ErlangWriter, ErlangImports> {

  public ErlangWriter(String filename) {
    super(new ErlangImports());
    setIndentText("    ");
    trimBlankLines();
    trimTrailingSpaces();
  }

  public static Factory<ErlangWriter> factory() {
    return (filename, namespace) -> new ErlangWriter(filename);
  }

  public void pushGeneratedDocumentationSection() {
    pushState((CodeSection) new ErlangWriterSections.GeneratedDocumentation());
  }

  public void pushOperationBodySection() {
    pushState((CodeSection) new ErlangWriterSections.OperationBody());
  }
}
