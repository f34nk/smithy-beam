package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ErlRecordTest {
  private static ErlCall mapsGet(String key) {
    return new ErlCall(
        new ErlAtom("maps"),
        "get",
        List.of(new ErlBinary(key), new ErlVar("Map"), new ErlAtom("undefined")));
  }

  @Test
  void emptyRecordAsString() {
    ErlRecord record = new ErlRecord("delete_user_output", null, List.of());
    assertThat(record.asString()).isEqualTo("#delete_user_output{}");
  }

  @Test
  void recordLiteralLines() {
    ErlRecord record =
        new ErlRecord(
            "basic_item",
            null,
            List.of(
                new ErlRecordField("name", mapsGet("name")),
                new ErlRecordField("count", mapsGet("count"))));
    assertThat(record.lines(1))
        .containsExactly(
            "    #basic_item{",
            "        name = maps:get(<<\"name\">>, Map, undefined),",
            "        count = maps:get(<<\"count\">>, Map, undefined)",
            "    }");
  }

  @Test
  void recordLiteralAsString() {
    ErlRecord record =
        new ErlRecord(
            "basic_item",
            null,
            List.of(
                new ErlRecordField("name", mapsGet("name")),
                new ErlRecordField("count", mapsGet("count"))));
    assertThat(record.asString(1))
        .isEqualTo(
            "    #basic_item{\n"
                + "        name = maps:get(<<\"name\">>, Map, undefined),\n"
                + "        count = maps:get(<<\"count\">>, Map, undefined)\n"
                + "    }");
  }

  @Test
  void recordUpdateLines() {
    ErlRecord update =
        new ErlRecord(
            "basic_item",
            new ErlVar("Item"),
            List.of(new ErlRecordField("name", new ErlBinary("updated"))));
    assertThat(update.lines()).containsExactly("Item#basic_item{ name = <<\"updated\">> }");
  }

  @Test
  void recordUpdateAsString() {
    ErlRecord update =
        new ErlRecord(
            "basic_item",
            new ErlVar("Item"),
            List.of(new ErlRecordField("name", new ErlBinary("updated"))));
    assertThat(update.asString()).isEqualTo("Item#basic_item{ name = <<\"updated\">> }");
  }
}
