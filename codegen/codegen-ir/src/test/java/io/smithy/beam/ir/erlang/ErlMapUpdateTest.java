package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlMapUpdateTest {
  @Test
  void mapUpdateLines() {
    ErlMapUpdate update =
        ErlMapUpdate.mapUpdate(
            ErlVar.var("Config"),
            ErlMapEntry.entry(ErlAtom.atom("credentials"), ErlVar.var("Creds")));
    assertThat(update.lines()).containsExactly("Config#{credentials => Creds}");
  }

  @Test
  void mapUpdateAsString() {
    ErlMapUpdate update =
        ErlMapUpdate.mapUpdate(
            ErlVar.var("Config"),
            ErlMapEntry.entry(ErlAtom.atom("credentials"), ErlVar.var("Creds")));
    assertThat(update.asString()).isEqualTo("Config#{credentials => Creds}");
  }
}
