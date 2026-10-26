package io.beam.lang.gleam;

import java.util.List;

/**
 * A Gleam source module. The module name matches the file stem; Gleam does not emit an in-file
 * module declaration.
 */
public record Module(
    String name,
    DocComment moduleDocOrNull,
    List<Import> imports,
    List<TypeAlias> typeAliases,
    List<CustomType> customTypes,
    List<Function> functions) {

  public static Module of(
      String name,
      DocComment moduleDocOrNull,
      List<Import> imports,
      List<TypeAlias> typeAliases,
      List<CustomType> customTypes,
      List<Function> functions) {
    return new Module(
        name,
        moduleDocOrNull,
        List.copyOf(imports),
        List.copyOf(typeAliases),
        List.copyOf(customTypes),
        List.copyOf(functions));
  }

  public static Module of(
      String name,
      DocComment moduleDocOrNull,
      List<Import> imports,
      List<TypeAlias> typeAliases,
      List<CustomType> customTypes) {
    return of(name, moduleDocOrNull, imports, typeAliases, customTypes, List.of());
  }

  public static Module of(String name, List<TypeAlias> typeAliases, List<CustomType> customTypes) {
    return of(name, null, List.of(), typeAliases, customTypes, List.of());
  }
}
