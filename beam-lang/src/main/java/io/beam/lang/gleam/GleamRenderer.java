package io.beam.lang.gleam;

public final class GleamRenderer {

  private static final Renderer DEFAULT = new DefaultGleamRenderer();

  private GleamRenderer() {}

  public static Renderer create() {
    return new DefaultGleamRenderer();
  }

  public static String render(Module module) {
    return DEFAULT.render(module);
  }

  public static String render(TypeAlias typeAlias) {
    return DEFAULT.render(typeAlias);
  }

  public static String render(CustomType customType) {
    return DEFAULT.render(customType);
  }
}
