package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.erlang.AtomExpr;
import io.beam.dsl.erlang.BlockExpr;
import io.beam.dsl.erlang.Function;
import io.beam.dsl.erlang.FunctionClause;
import io.beam.dsl.erlang.LocalCallExpr;
import io.beam.dsl.erlang.RecordPattern;
import io.beam.dsl.erlang.RecordPatternField;
import io.beam.dsl.erlang.Variable;
import io.beam.dsl.erlang.VariablePattern;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ErlangUnusedBindingsTest {

  @Test
  void prefixesUnusedHeadBindings() {
    Function function =
        ErlangUnusedBindings.prefix(
            Function.of(
                "decode_request",
                List.of(
                    FunctionClause.of(
                        List.of(
                            RecordPattern.of(
                                "http_request",
                                List.of(
                                    RecordPatternField.of("query", VariablePattern.of("Query")),
                                    RecordPatternField.of(
                                        "headers", VariablePattern.of("Headers")),
                                    RecordPatternField.of("body", VariablePattern.of("Body"))))),
                        BlockExpr.commaSeparated(
                            List.of(
                                LocalCallExpr.of(
                                    "decode_json_body", List.of(Variable.of("Body")))),
                            false)))));

    RecordPattern pattern = (RecordPattern) function.clauses().get(0).patterns().get(0);
    assertThat(((VariablePattern) pattern.fields().get(0).pattern()).name()).isEqualTo("_Query");
    assertThat(((VariablePattern) pattern.fields().get(1).pattern()).name()).isEqualTo("_Headers");
    assertThat(((VariablePattern) pattern.fields().get(2).pattern()).name()).isEqualTo("Body");
  }

  @Test
  void prefixesUnusedRecordAlias() {
    Function function =
        ErlangUnusedBindings.prefix(
            Function.of(
                "encode_request",
                List.of(
                    FunctionClause.of(
                        List.of(
                            RecordPattern.bind(
                                "Input",
                                "get_user_input",
                                List.of(
                                    RecordPatternField.of(
                                        "user_name", VariablePattern.of("UserName"))))),
                        LocalCallExpr.of("to_binary", List.of(Variable.of("UserName")))))));

    RecordPattern pattern = (RecordPattern) function.clauses().get(0).patterns().get(0);
    assertThat(pattern.alias()).isEqualTo("_Input");
    assertThat(((VariablePattern) pattern.fields().get(0).pattern()).name()).isEqualTo("UserName");
  }

  @Test
  void referencedNamesCollectsVariablesOnly() {
    Set<String> names =
        ErlangUnusedBindings.referencedNames(
            LocalCallExpr.of(
                "f", List.of(Variable.of("Body"), AtomExpr.of("ok"), Variable.of("Headers"))));
    assertThat(names).containsExactlyInAnyOrder("Body", "Headers");
  }
}
