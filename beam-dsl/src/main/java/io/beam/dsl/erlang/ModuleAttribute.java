package io.beam.dsl.erlang;

public record ModuleAttribute(String name, String value) {

  public static ModuleAttribute of(String name, String value) {
    return new ModuleAttribute(name, value);
  }

  public static ModuleAttribute behaviour(String module) {
    return new ModuleAttribute("behaviour", module);
  }

  public static ModuleAttribute define(String name, String value) {
    return new ModuleAttribute("define", name + ", " + value);
  }

  public static ModuleAttribute include(String headerFile) {
    return new ModuleAttribute("include", "\"" + headerFile + "\"");
  }
}
