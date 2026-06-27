package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlRemoteCallTest {
  @Test
  void remoteCallLines() {
    ErlRemoteCall call =
        ErlRemoteCall.call(
            ErlVar.var("HttpClient"),
            "request",
            ErlAtom.atom("get"),
            ErlVar.var("Req"),
            ErlList.list(),
            ErlList.list(ErlTuple.tuple(ErlAtom.atom("body_format"), ErlAtom.atom("binary"))));
    assertThat(call.lines())
        .containsExactly("HttpClient:request(get, Req, [], [{body_format, binary}])");
  }

  @Test
  void remoteCallAsString() {
    ErlRemoteCall call =
        ErlRemoteCall.call(
            ErlVar.var("HttpClient"),
            "request",
            ErlAtom.atom("get"),
            ErlVar.var("Req"),
            ErlList.list(),
            ErlList.list(ErlTuple.tuple(ErlAtom.atom("body_format"), ErlAtom.atom("binary"))));
    assertThat(call.asString())
        .isEqualTo("HttpClient:request(get, Req, [], [{body_format, binary}])");
  }

  @Test
  void remoteCallVariableFunction() {
    assertThat(
            ErlRemoteCall.call(ErlVar.var("Impl"), ErlVar.var("Fun"), ErlVar.var("Ctx")).asString())
        .isEqualTo("Impl:Fun(Ctx)");
  }
}
