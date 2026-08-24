package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamMemberNamesTest {

  private static final MemberShape AFTER_MEMBER =
      MemberShape.builder()
          .id(ShapeId.from("demo.basic#Example$after"))
          .target(ShapeId.from("smithy.api#String"))
          .build();

  @Test
  void fieldName_returnsEscapedFieldNameProperty() {
    SymbolProvider sp =
        shape -> Symbol.builder().name("after").putProperty("fieldName", "after_").build();

    assertThat(BeamMemberNames.fieldName(sp, AFTER_MEMBER)).isEqualTo("after_");
  }

  @Test
  void fieldName_throwsWhenFieldNamePropertyAbsent() {
    SymbolProvider sp = shape -> Symbol.builder().name("after").build();

    assertThatThrownBy(() -> BeamMemberNames.fieldName(sp, AFTER_MEMBER))
        .isInstanceOf(CodegenException.class)
        .hasMessageContaining("Missing fieldName property")
        .hasMessageContaining(AFTER_MEMBER.getId().toString());
  }
}
