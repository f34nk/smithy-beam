package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErlCallTest {
  @Test
  void remoteCallLines() {
    ErlCall call =
        new ErlCall(
            new ErlAtom("maps"),
            "get",
            List.of(new ErlBinary("name"), new ErlVar("Map"), new ErlAtom("undefined")));
    assertThat(call.lines()).containsExactly("maps:get(<<\"name\">>, Map, undefined)");
  }

  @Test
  void remoteCallAsString() {
    ErlCall call =
        new ErlCall(
            new ErlAtom("maps"),
            "get",
            List.of(new ErlBinary("name"), new ErlVar("Map"), new ErlAtom("undefined")));
    assertThat(call.asString()).isEqualTo("maps:get(<<\"name\">>, Map, undefined)");
  }

  @Test
  void localCallLines() {
    ErlCallLocal call = new ErlCallLocal("is_map", List.of(new ErlVar("Map")));
    assertThat(call.lines()).containsExactly("is_map(Map)");
  }

  @Test
  void localCallAsString() {
    ErlCallLocal call = new ErlCallLocal("is_map", List.of(new ErlVar("Map")));
    assertThat(call.asString()).isEqualTo("is_map(Map)");
  }

  @Test
  void filtermapAsStringMatchesGolden() throws IOException {
    ErlCall call = buildVerboseFiltermap();
    assertThat(call.asString())
        .isEqualTo(ErlFunctionTest.readExpectedString("ir/filtermap_verbose.expected.erl"));
  }

  private static ErlCall buildVerboseFiltermap() {
    return ErlCall.filtermap(
        ErlFun.fun(
            ErlClause.clause(
                List.of(ErlVarPattern.varPattern("V")),
                List.of(
                    ErlGuard.exprGuard(
                        ErlOp.op("=/=", ErlVar.var("V"), ErlAtom.atom("undefined")))),
                ErlTuple.tuple(
                    ErlAtom.atom("true"),
                    ErlTuple.tuple(
                        ErlBinary.binary("verbose"),
                        ErlCallLocal.callLocal("encode_query_value", ErlVar.var("V"))))),
            ErlClause.clause(List.of(ErlVarPattern.varPattern("_")), ErlAtom.atom("false"))),
        ErlList.list(ErlVar.var("Verbose")));
  }
}
