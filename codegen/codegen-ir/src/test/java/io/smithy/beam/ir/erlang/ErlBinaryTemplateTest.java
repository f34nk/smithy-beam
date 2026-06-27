package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlBinaryTemplateTest {
  @Test
  void binaryTemplateWithBinaryTypeSpecLines() {
    ErlBinaryTemplate template =
        ErlBinaryTemplate.binaryTemplate(
            ErlBinaryExpr.expr(ErlCallLocal.callLocal("uri_encode", new ErlVar("Name")), "binary"));
    assertThat(template.lines()).containsExactly("<<(uri_encode(Name))/binary>>");
  }

  @Test
  void binaryTemplateWithBinaryTypeSpecAsString() {
    ErlBinaryTemplate template =
        ErlBinaryTemplate.binaryTemplate(
            ErlBinaryExpr.expr(ErlCallLocal.callLocal("uri_encode", new ErlVar("Name")), "binary"));
    assertThat(template.asString()).isEqualTo("<<(uri_encode(Name))/binary>>");
  }

  @Test
  void binaryTemplateWithTypeSpecLines() {
    ErlBinaryTemplate template =
        ErlBinaryTemplate.binaryTemplate(
            ErlBinaryExpr.expr(
                ErlCall.call("erlang", "crc32", new ErlVar("Body")), "32/big-unsigned-integer"));
    assertThat(template.lines())
        .containsExactly("<<(erlang:crc32(Body)):32/big-unsigned-integer>>");
  }

  @Test
  void binaryTemplateWithTypeSpecAsString() {
    ErlBinaryTemplate template =
        ErlBinaryTemplate.binaryTemplate(
            ErlBinaryExpr.expr(
                ErlCall.call("erlang", "crc32", new ErlVar("Body")), "32/big-unsigned-integer"));
    assertThat(template.asString()).isEqualTo("<<(erlang:crc32(Body)):32/big-unsigned-integer>>");
  }

  @Test
  void binaryTemplateLines() {
    ErlBinaryTemplate template =
        ErlBinaryTemplate.binaryTemplate(
            ErlBinaryText.text("/types/"),
            ErlBinaryExpr.expr(ErlCallLocal.callLocal("uri_encode", new ErlVar("Name")), true));
    assertThat(template.lines()).containsExactly("<<\"/types/\", (uri_encode(Name))/binary>>");
  }

  @Test
  void binaryTemplateAsString() {
    ErlBinaryTemplate template =
        ErlBinaryTemplate.binaryTemplate(
            ErlBinaryText.text("/types/"),
            ErlBinaryExpr.expr(ErlCallLocal.callLocal("uri_encode", new ErlVar("Name")), true));
    assertThat(template.asString()).isEqualTo("<<\"/types/\", (uri_encode(Name))/binary>>");
  }
}
