package io.beam.dsl.erlang;

public sealed interface FunctionDoc permits Doc, Edoc {
  String text();
}
