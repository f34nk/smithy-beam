package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.MemberShape;

public final class BeamMemberNames {
  private BeamMemberNames() {}

  public static String fieldName(SymbolProvider sp, MemberShape member) {
    return sp.toSymbol(member)
        .getProperty("fieldName", String.class)
        .orElseThrow(
            () ->
                new CodegenException(
                    "Missing fieldName property on member symbol: " + member.getId()));
  }
}
