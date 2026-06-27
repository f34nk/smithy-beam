package io.smithy.beam.elixir;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.Symbol;

/**
 * Accumulates {@code import}, {@code alias}, and {@code require} text for symbol-driven deps.
 * Default {@code _types.ex} output keeps this empty. Emit lines inside {@code
 * pushDependenciesSection} with {@code writer.write} for {@code defmodule}-first files; do not
 * prepend the container in {@link ElixirWriter#toString()}.
 */
final class ElixirImports implements ImportContainer {

  private final Set<String> preambleLines = new LinkedHashSet<>();

  void addAlias(String modulePath) {
    preambleLines.add("alias " + modulePath);
  }

  void addImport(String modulePath) {
    preambleLines.add("import " + modulePath);
  }

  void addRequire(String modulePath) {
    preambleLines.add("require " + modulePath);
  }

  @Override
  public void importSymbol(Symbol symbol, String alias) {
    // Reserved: map Symbol metadata to import or alias lines when protocol deps arrive.
  }

  @Override
  public String toString() {
    if (preambleLines.isEmpty()) {
      return "";
    }
    return preambleLines.stream().collect(Collectors.joining("\n", "", "\n"));
  }
}
