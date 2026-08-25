package io.beam.dsl.erlang;

public final class ErlangRenderer {

  private static final Renderer DEFAULT = new DefaultErlangRenderer();

  private ErlangRenderer() {}

  public static Renderer create() {
    return new DefaultErlangRenderer();
  }

  public static String render(Module module) {
    return DEFAULT.render(module);
  }

  public static String render(Header header) {
    return DEFAULT.render(header);
  }

  public static String renderFunction(Function function) {
    return DEFAULT.renderFunction(function);
  }

  public static String renderExpression(Expression expression) {
    return DEFAULT.renderExpression(expression);
  }

  public static String renderStatement(Expression expression) {
    return DEFAULT.renderStatement(expression);
  }

  public static String renderPattern(Pattern pattern) {
    return DEFAULT.renderPattern(pattern);
  }
}
