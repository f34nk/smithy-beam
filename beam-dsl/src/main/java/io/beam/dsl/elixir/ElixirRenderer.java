package io.beam.dsl.elixir;

public final class ElixirRenderer {

  private static final Renderer DEFAULT = new DefaultElixirRenderer();

  private ElixirRenderer() {}

  public static Renderer create() {
    return new DefaultElixirRenderer();
  }

  public static String render(Module module) {
    return DEFAULT.render(module);
  }

  public static String render(TypesModule typesModule) {
    return DEFAULT.render(typesModule);
  }

  public static String renderFunction(Function function) {
    return DEFAULT.renderFunction(function);
  }

  public static String renderCallback(Callback callback) {
    return DEFAULT.renderCallback(callback, "  ");
  }

  public static String renderCallback(Callback callback, String indent) {
    return DEFAULT.renderCallback(callback, indent);
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
