package io.beam.lang.gleam;

import java.util.List;

/**
 * A Gleam import. When {@code typeNames} is empty, renders {@code import module/path}. Otherwise
 * renders {@code import module/path.{type A, type B}}.
 */
public record Import(String modulePath, List<String> typeNames) {

  public static Import of(String modulePath) {
    return new Import(modulePath, List.of());
  }

  public static Import of(String modulePath, List<String> typeNames) {
    return new Import(modulePath, List.copyOf(typeNames));
  }

  public static Import type(String modulePath, String typeName) {
    return new Import(modulePath, List.of(typeName));
  }
}
