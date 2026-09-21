package io.beam.lang.erlang;

public sealed interface FunctionDoc permits Doc, Edoc {
  String text();
}
