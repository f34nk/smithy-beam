package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExCallTest {
  @Test
  void remoteCallLines() {
    ExCall call =
        ExCall.call("Map", "get", ExString.string("name"), ExVar.var("map"), ExAtom.atom("nil"));
    assertThat(call.lines()).containsExactly("Map.get(\"name\", map, :nil)");
  }

  @Test
  void remoteCallAsString() {
    ExCall call =
        ExCall.call("Map", "get", ExString.string("name"), ExVar.var("map"), ExAtom.atom("nil"));
    assertThat(call.asString()).isEqualTo("Map.get(\"name\", map, :nil)");
  }

  @Test
  void remoteCallBreaksMultilineArgs() {
    ExCall call =
        ExCall.call(
            "RetryMod",
            "with_retry",
            ExAnonymousFn.fn(
                ExClause.blockClause(
                    List.of(),
                    ExExprBlock.block(
                        ExMatch.match(
                            ExVarPattern.var("req"),
                            ExCallLocal.callLocal(
                                "encode_get_name_request", ExVar.var("input")))))),
            ExVar.var("retry_opts"));
    assertThat(call.lines())
        .containsExactly(
            "RetryMod.with_retry(",
            "  fn ->",
            "    req = encode_get_name_request(input)",
            "  end,",
            "  retry_opts",
            ")");
  }
}
