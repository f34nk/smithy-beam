package io.beam.lang.gleam;

/** Optional labelled argument {@code label: value} for constructors and calls. */
public record LabelledArg(String labelOrNull, Expression value) {

  public static LabelledArg positional(Expression value) {
    return new LabelledArg(null, value);
  }

  public static LabelledArg labelled(String label, Expression value) {
    return new LabelledArg(label, value);
  }
}
