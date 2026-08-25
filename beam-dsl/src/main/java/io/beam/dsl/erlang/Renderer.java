package io.beam.dsl.erlang;

public interface Renderer {

  String render(Module module);

  String render(Header header);

  String renderFunction(Function function);

  String renderExpression(Expression expression);

  default String renderStatement(Expression expression) {
    String rendered = renderExpression(expression);
    return rendered.endsWith(".") ? rendered : rendered + ".";
  }

  String renderPattern(Pattern pattern);
}
