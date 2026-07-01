package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExTypeDefTest {
  @Test
  void aliasAsString() {
    assertThat(ExTypeDef.alias("basic_string", "String.t()").asString())
        .isEqualTo("@type basic_string :: String.t()");
  }

  @Test
  void unionTypeMultilineAsString() {
    String out =
        ExTypeDef.unionType(
                "basic_union",
                java.util.List.of(
                    "{:text, basic_string()}",
                    "{:number, basic_integer()}",
                    "{:flag, basic_boolean()}",
                    "{:unknown, binary()}"))
            .asString();

    assertThat(out).contains("@type basic_union ::");
    assertThat(out).contains("    {:text, basic_string()}");
    assertThat(out).contains("    | {:unknown, binary()}");
  }

  @Test
  void structureTypeAsString() {
    String out =
        ExTypeDef.structureType(
                "t",
                java.util.List.of(
                    "name: BasicServiceTypes.basic_string()",
                    "count: BasicServiceTypes.basic_integer() | nil"))
            .asString();
    assertThat(out).contains("@type t :: %__MODULE__{");
    assertThat(out).contains("name: BasicServiceTypes.basic_string(),");
    assertThat(out).contains("count: BasicServiceTypes.basic_integer() | nil");
    assertThat(out).contains("}");
  }

  @Test
  void mapTypeAsString() {
    String out =
        ExTypeDef.mapType(
                "aws_credentials",
                java.util.List.of(
                    "required(:access_key_id) => String.t()",
                    "required(:secret_access_key) => String.t()",
                    "optional(:session_token) => String.t() | nil"))
            .asString();

    assertThat(out).contains("@type aws_credentials :: %{");
    assertThat(out).doesNotContain("%__MODULE__");
    assertThat(out).contains("required(:access_key_id) => String.t(),");
    assertThat(out).contains("optional(:session_token) => String.t() | nil");
    assertThat(out).contains("}");
  }

  @Test
  void isModuleStructType_true_forStructureType() {
    ExTypeDef typeDef = ExTypeDef.structureType("t", List.of("name: String.t()"));
    assertThat(typeDef.isModuleStructType()).isTrue();
    assertThat(typeDef.structureFieldLines()).containsExactly("name: String.t()");
  }

  @Test
  void isModuleStructType_false_forAlias() {
    ExTypeDef typeDef = ExTypeDef.alias("foo", "String.t()");
    assertThat(typeDef.isModuleStructType()).isFalse();
    assertThat(typeDef.structureFieldLines()).isEmpty();
  }
}
