package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ErlClauseTest {
  private static ErlCall mapsGet(String key) {
    return new ErlCall(
        new ErlAtom("maps"),
        "get",
        List.of(new ErlBinary(key), new ErlVar("Map"), new ErlAtom("undefined")));
  }

  private static ErlRecord basicItemRecord() {
    return new ErlRecord(
        "basic_item",
        null,
        List.of(
            new ErlRecordField("name", mapsGet("name")),
            new ErlRecordField("count", mapsGet("count"))));
  }

  private static ErlClause mapClause() {
    return new ErlClause(
        List.of(new ErlVarPattern("Map")),
        List.of(new ErlGuard("is_map", List.of(new ErlVar("Map")))),
        List.of(basicItemRecord()));
  }

  @Test
  void inlineAtomClauseLines() {
    ErlClause clause =
        new ErlClause(
            List.of(new ErlAtomPattern("undefined")), List.of(), List.of(new ErlAtom("undefined")));
    assertThat(clause.lines(0, "decode_basic_item", true))
        .containsExactly("decode_basic_item(undefined) -> undefined;");
  }

  @Test
  void inlineAtomClauseAsStringIsNotSupported() {
    assertThat(new ErlAtom("undefined").asString()).isEqualTo("undefined");
  }

  @Test
  void multilineRecordClauseLines() {
    ErlClause clause = mapClause();
    assertThat(clause.lines(0, "decode_basic_item", false))
        .containsExactly(
            "decode_basic_item(Map) when is_map(Map) ->",
            "    #basic_item{",
            "        name = maps:get(<<\"name\">>, Map, undefined),",
            "        count = maps:get(<<\"count\">>, Map, undefined)",
            "    }.");
  }

  @Test
  void recordPatternClauseStaysSingleLine() {
    ErlClause clause =
        new ErlClause(
            List.of(
                ErlRecordPattern.recordPattern(
                    "http_response",
                    ErlRecordFieldPattern.fieldPattern(
                        "status", ErlIntegerPattern.integerPattern(200)),
                    ErlRecordFieldPattern.fieldPattern(
                        "headers", ErlVarPattern.varPattern("Headers")),
                    ErlRecordFieldPattern.fieldPattern("body", ErlVarPattern.varPattern("Body")))),
            List.of(),
            List.of(new ErlAtom("ok")));
    assertThat(clause.lines(0, "decode_get_name_response", false))
        .containsExactly(
            "decode_get_name_response(#http_response{status = 200, headers = Headers, body = Body}) -> ok.");
  }

  @Test
  void aliasedEmptyMapClauseStaysSingleLine() {
    ErlClause clause =
        new ErlClause(
            List.of(new ErlRecordPattern("", List.of(), "Map")),
            List.of(),
            List.of(new ErlAtom("undefined")));
    assertThat(clause.lines(0, "decode_event", false))
        .containsExactly("decode_event(Map = #{}) -> undefined.");
  }

  @Test
  void aliasedRecordClauseStaysSingleLine() {
    ErlClause clause =
        new ErlClause(
            List.of(
                ErlRecordPattern.recordFunctionHead(
                    "Input", "input_record", List.of("field_a", "field_b"))),
            List.of(),
            List.of(new ErlAtom("ok")));
    assertThat(clause.lines(0, "handle", false))
        .containsExactly("handle(Input = #input_record{field_a, field_b}) -> ok.");
  }

  @Test
  void multilineAliasedRecordPatternInFunctionHead() {
    ErlClause clause =
        new ErlClause(
            List.of(
                new ErlRecordPattern(
                    "create_resource_input",
                    List.of(
                        ErlRecordFieldPattern.fieldPattern(
                            "name", ErlVarPattern.varPattern("Name")),
                        ErlRecordFieldPattern.fieldPattern(
                            "client_token", ErlVarPattern.varPattern("ClientToken"))),
                    "Input")),
            List.of(),
            List.of(new ErlAtom("ok")));
    assertThat(clause.lines(0, "encode_create_resource_request", false))
        .containsExactly(
            "encode_create_resource_request(Input = #create_resource_input{",
            "    name = Name, client_token = ClientToken",
            "}) -> ok.");
  }

  @Test
  void multilineRecordPatternInFunctionHead() {
    ErlClause clause =
        new ErlClause(
            List.of(
                ErlVarPattern.varPattern("HttpClient"),
                ErlVarPattern.varPattern("Config"),
                ErlRecordPattern.recordPattern(
                    "http_request",
                    ErlRecordFieldPattern.fieldPattern(
                        "method", ErlVarPattern.varPattern("Method")),
                    ErlRecordFieldPattern.fieldPattern("path", ErlVarPattern.varPattern("Path")),
                    ErlRecordFieldPattern.fieldPattern("query", ErlVarPattern.varPattern("Query")),
                    ErlRecordFieldPattern.fieldPattern(
                        "headers", ErlVarPattern.varPattern("Headers")),
                    ErlRecordFieldPattern.fieldPattern("body", ErlVarPattern.varPattern("Body")),
                    ErlRecordFieldPattern.fieldPattern("host", ErlVarPattern.varPattern("Host")))),
            List.of(),
            List.of(
                ErlExprBlock.block(
                    ErlMatch.match(
                        ErlVarPattern.varPattern("BaseUrl"),
                        ErlCase.caseExpr(
                            ErlCall.call(
                                "maps",
                                "get",
                                ErlAtom.atom("base_url"),
                                ErlVar.var("Config"),
                                ErlAtom.atom("undefined")),
                            ErlClause.clause(
                                List.of(ErlAtomPattern.atomPattern("undefined")),
                                ErlAtom.atom("x")))))));
    assertThat(clause.lines(0, "dispatch_signed", false))
        .containsExactly(
            "dispatch_signed(HttpClient, Config, #http_request{",
            "    method = Method, path = Path, query = Query, headers = Headers, body = Body, host = Host",
            "}) ->",
            "    BaseUrl =",
            "        case maps:get(base_url, Config, undefined) of",
            "            undefined -> x",
            "        end.");
    ErlFunction fn =
        ErlFunction.function(
            "dispatch_signed",
            3,
            List.of(
                new ErlClause(
                    List.of(
                        ErlVarPattern.varPattern("HttpClient"),
                        ErlVarPattern.varPattern("Config"),
                        ErlRecordPattern.recordPattern(
                            "http_request",
                            ErlRecordFieldPattern.fieldPattern(
                                "method", ErlVarPattern.varPattern("Method")),
                            ErlRecordFieldPattern.fieldPattern(
                                "path", ErlVarPattern.varPattern("Path")),
                            ErlRecordFieldPattern.fieldPattern(
                                "query", ErlVarPattern.varPattern("Query")),
                            ErlRecordFieldPattern.fieldPattern(
                                "headers", ErlVarPattern.varPattern("Headers")),
                            ErlRecordFieldPattern.fieldPattern(
                                "body", ErlVarPattern.varPattern("Body")),
                            ErlRecordFieldPattern.fieldPattern(
                                "host", ErlVarPattern.varPattern("Host")))),
                    List.of(),
                    List.of(
                        ErlExprBlock.block(
                            ErlMatch.match(
                                ErlVarPattern.varPattern("BaseUrl"),
                                ErlCase.caseExpr(
                                    ErlCall.call(
                                        "maps",
                                        "get",
                                        ErlAtom.atom("base_url"),
                                        ErlVar.var("Config"),
                                        ErlAtom.atom("undefined")),
                                    ErlClause.clause(
                                        List.of(ErlAtomPattern.atomPattern("undefined")),
                                        ErlAtom.atom("x")))))))));
    assertThat(fn.lines(0).get(1))
        .isEqualTo(
            "    method = Method, path = Path, query = Query, headers = Headers, body = Body, host = Host");
    assertThat(clause.lines(0, "dispatch_signed", false, true).get(1))
        .isEqualTo(
            "    method = Method, path = Path, query = Query, headers = Headers, body = Body, host = Host");
  }

  @Test
  void multilineRecordBodyAsString() {
    ErlRecord record = basicItemRecord();
    assertThat(record.asString(1))
        .isEqualTo(
            "    #basic_item{\n"
                + "        name = maps:get(<<\"name\">>, Map, undefined),\n"
                + "        count = maps:get(<<\"count\">>, Map, undefined)\n"
                + "    }");
  }
}
