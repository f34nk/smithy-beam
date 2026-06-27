package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlBinPattern implements ErlPattern {
  private final String syntax;

  public ErlBinPattern(String syntax) {
    this.syntax = syntax;
  }

  public static ErlBinPattern binPattern(String syntax) {
    return new ErlBinPattern(syntax);
  }

  public String syntax() {
    return syntax;
  }

  @Override
  public List<String> lines() {
    return List.of("<<" + syntax + ">>");
  }
}
