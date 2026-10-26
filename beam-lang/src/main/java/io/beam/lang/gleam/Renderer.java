package io.beam.lang.gleam;

/** Renders Gleam IR nodes to source text. */
public interface Renderer {

  String render(Module module);

  String render(TypeAlias typeAlias);

  String render(CustomType customType);

  String render(Function function);

  String render(Expression expression);
}
