package io.beam.dsl.elixir;

import java.util.List;

public record Module(
    String name,
    Moduledoc moduledocOrNull,
    List<UseDirective> uses,
    List<Alias> aliases,
    List<String> moduleAttributes,
    List<TypesModule> nestedTypesModules,
    List<Callback> callbacks,
    List<String> trailingModuleAttributes,
    List<Function> functions) {

  public static Module of(String name, List<Function> functions) {
    return new Module(
        name, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), functions);
  }

  public static Module of(
      String name,
      Moduledoc moduledocOrNull,
      List<UseDirective> uses,
      List<Alias> aliases,
      List<String> moduleAttributes,
      List<TypesModule> nestedTypesModules,
      List<Callback> callbacks,
      List<String> trailingModuleAttributes,
      List<Function> functions) {
    return new Module(
        name,
        moduledocOrNull,
        uses,
        aliases,
        moduleAttributes,
        nestedTypesModules,
        callbacks,
        trailingModuleAttributes,
        functions);
  }
}
