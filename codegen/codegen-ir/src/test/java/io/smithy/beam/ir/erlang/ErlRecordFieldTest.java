package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ErlRecordFieldTest {
  @Test
  void lines() {
    ErlRecordField field = new ErlRecordField("name", new ErlBinary("updated"));
    assertThat(field.lines(0)).containsExactly("name = <<\"updated\">>");
  }

  @Test
  void asString() {
    ErlRecordField field = new ErlRecordField("name", new ErlBinary("updated"));
    assertThat(field.asString()).isEqualTo("name = <<\"updated\">>");
  }

  @Test
  void longExpressionBreaksAcrossLines() {
    ErlRecordField field =
        new ErlRecordField(
            "payload",
            ErlCallLocal.callLocal(
                "encode_very_long_helper_function_name_with_extra_length_for_line_break",
                ErlVar.var("A"),
                ErlVar.var("B"),
                ErlVar.var("C"),
                ErlVar.var("D"),
                ErlVar.var("E"),
                ErlVar.var("F"),
                ErlVar.var("G")));
    List<String> lines = field.lines(1);
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0)).endsWith("(");
    assertThat(lines.get(1)).isEqualTo("        A, B, C, D, E, F, G)");
  }
}
