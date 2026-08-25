package io.beam.dsl.erlang;

import java.util.List;

public record Module(
    String name,
    List<Function> functions,
    List<String> headerComments,
    Moduledoc moduledoc,
    List<String> includeHeaders,
    List<ModuleAttribute> moduleAttributes,
    List<Callback> callbacks,
    List<ModuleAttribute> trailingAttributes,
    List<TypeAlias> typeAliases,
    List<String> exports,
    boolean suppressExport,
    List<String> epilogueComments) {

  public Module(String name, List<Function> functions) {
    this(name, functions, null, null, null, null, null, null, null, null, false, null);
  }

  public static Module of(String name, List<Function> functions) {
    return new Module(name, functions);
  }

  public static Module of(
      String name,
      List<Function> functions,
      List<String> headerComments,
      Moduledoc moduledoc,
      String includeHeader) {
    return new Module(
        name,
        functions,
        headerComments,
        moduledoc,
        includeHeader == null ? null : List.of(includeHeader),
        null,
        null,
        null,
        null,
        null,
        false,
        null);
  }

  public static Module of(
      String name,
      List<Function> functions,
      List<String> headerComments,
      Moduledoc moduledoc,
      String includeHeader,
      List<TypeAlias> typeAliases) {
    return new Module(
        name,
        functions,
        headerComments,
        moduledoc,
        includeHeader == null ? null : List.of(includeHeader),
        null,
        null,
        null,
        typeAliases,
        null,
        false,
        null);
  }

  public static Module of(
      String name,
      List<Function> functions,
      List<String> headerComments,
      Moduledoc moduledoc,
      List<String> includeHeaders,
      List<TypeAlias> typeAliases,
      List<String> exports) {
    return new Module(
        name,
        functions,
        headerComments,
        moduledoc,
        includeHeaders,
        null,
        null,
        null,
        typeAliases,
        exports,
        false,
        null);
  }

  public static Module behaviour(
      String name, List<String> headerComments, String includeHeader, List<Callback> callbacks) {
    return new Module(
        name,
        List.of(),
        headerComments,
        null,
        List.of(includeHeader),
        null,
        callbacks,
        null,
        null,
        null,
        true,
        null);
  }

  public static Module server(
      String name,
      List<String> headerComments,
      String behaviourModule,
      List<String> exports,
      String includeHeader,
      String defaultImpl,
      String handlersKeyTuple,
      List<Function> functions,
      List<String> epilogueComments) {
    return new Module(
        name,
        functions,
        headerComments,
        null,
        List.of(includeHeader),
        List.of(ModuleAttribute.behaviour(behaviourModule)),
        null,
        List.of(
            ModuleAttribute.define("DEFAULT_IMPL", defaultImpl),
            ModuleAttribute.define("HANDLERS_KEY", handlersKeyTuple)),
        null,
        exports,
        false,
        epilogueComments);
  }
}
