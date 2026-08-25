package io.beam.dsl.elixir;

public interface Renderer {

  String render(Module module);

  String render(TypesModule typesModule);

  String renderFunction(Function function);

  String renderCallback(Callback callback, String indent);

  String renderExpression(Expression expression);

  default String renderStatement(Expression expression) {
    String rendered = renderExpression(expression);
    return rendered.endsWith("\n") ? rendered.stripTrailing() : rendered;
  }

  String renderPattern(Pattern pattern);
}
