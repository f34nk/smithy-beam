package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ErlMatchTest {
  @Test
  void matchLines() {
    ErlMatch match = ErlMatch.match(new ErlVarPattern("Value"), ErlCallLocal.callLocal("fetch"));
    assertThat(match.lines()).containsExactly("Value = fetch()");
  }

  @Test
  void matchAsString() {
    ErlMatch match = ErlMatch.match(new ErlVarPattern("Value"), ErlCallLocal.callLocal("fetch"));
    assertThat(match.asString()).isEqualTo("Value = fetch()");
  }

  @Test
  void matchCaseLines() {
    ErlMatch match =
        ErlMatch.match(
            new ErlVarPattern("Input1"),
            ErlCase.caseExpr(
                ErlRecordAccess.recordAccess(
                    ErlVar.var("Input"), "create_resource_input", "client_token"),
                ErlClause.clause(
                    List.of(new ErlAtomPattern("undefined")), new ErlAtom("undefined")),
                ErlClause.clause(List.of(new ErlVarPattern("_")), ErlVar.var("Input"))));
    assertThat(match.lines())
        .containsExactly(
            "Input1 = case Input#create_resource_input.client_token of",
            "    undefined -> undefined;",
            "    _ -> Input",
            "end");
  }
}
