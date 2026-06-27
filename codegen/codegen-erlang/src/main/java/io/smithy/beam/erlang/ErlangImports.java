package io.smithy.beam.erlang;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.Symbol;

/**
 * Tracks Erlang header fragments for generated modules. Types-only .hrl output keeps this empty.
 * Client and server modules write -include lines in the Dependencies writer section after -module.
 */
final class ErlangImports implements ImportContainer {

  private final Set<String> includeLines = new LinkedHashSet<>();

  static String relativeIncludeLine(String relativePath) {
    return "-include(\"" + relativePath + "\").";
  }

  static String includeLibLine(String application, String includePath) {
    return "-include_lib(\"" + application + "\", \"" + includePath + "\").";
  }

  void addIncludeRelative(String relativePath) {
    includeLines.add(relativeIncludeLine(relativePath));
  }

  void addIncludeLib(String application, String includePath) {
    includeLines.add(includeLibLine(application, includePath));
  }

  @Override
  public void importSymbol(Symbol symbol, String alias) {
    // Reserved: map Symbol metadata to include paths when protocol deps arrive.
  }

  @Override
  public String toString() {
    if (includeLines.isEmpty()) {
      return "";
    }
    return includeLines.stream().collect(Collectors.joining("\n", "", "\n"));
  }
}
