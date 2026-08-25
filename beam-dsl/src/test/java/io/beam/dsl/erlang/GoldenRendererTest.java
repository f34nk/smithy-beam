package io.beam.dsl.erlang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoldenRendererTest {

  private final Renderer renderer = ErlangRenderer.create();

  @Test
  void rendersSyntaxModule() throws IOException {
    assertGolden("syntax_module.expected.erl", renderer.render(syntaxModule()));
  }

  @Test
  void rendersSyntaxTypesHeader() throws IOException {
    assertGolden("syntax_types.expected.hrl", renderer.render(syntaxTypesHeader()));
  }

  @Test
  void rendersSyntaxExpression() throws IOException {
    assertGolden("syntax_expression.expected.erl", renderer.renderExpression(syntaxExpression()));
  }

  @Test
  void rendersSyntaxMapEntries() throws IOException {
    assertGolden("syntax_map_entries.expected.erl", renderer.renderExpression(syntaxMapEntries()));
  }

  @Test
  void rendersSyntaxCaseExpression() throws IOException {
    assertGolden(
        "syntax_case_expression.expected.erl", renderer.renderExpression(syntaxCaseExpression()));
  }

  @Test
  void rendersSyntaxStatement() throws IOException {
    assertGolden("syntax_statement.expected.erl", renderer.renderStatement(syntaxStatement()));
  }

  @Test
  void rendersSyntaxPrelude() throws IOException {
    assertGolden("syntax_prelude.expected.erl", renderer.renderExpression(syntaxPrelude()));
  }

  private static void assertGolden(String resourceName, String actual) throws IOException {
    assertEquals(
        readGolden(resourceName),
        ensureTrailingNewline(actual),
        () -> "Golden mismatch for " + resourceName);
  }

  private static String ensureTrailingNewline(String value) {
    return value.endsWith("\n") ? value : value + "\n";
  }

  private static String readGolden(String resourceName) throws IOException {
    String path = "/erlang/" + resourceName;
    try (InputStream in = GoldenRendererTest.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IOException("Missing golden resource: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static Function decodeBasicItemFunction(FunctionDoc doc) {
    return Function.of(
        "decode_basic_item",
        decodeBasicItemClauses(),
        Spec.of("decode_basic_item(undefined | null | map()) -> undefined | #basic_item{}"),
        doc);
  }

  private static Function decodeBasicItemWithoutSpecFunction() {
    return Function.of("decode_basic_item", decodeBasicItemClauses());
  }

  private static List<FunctionClause> decodeBasicItemClauses() {
    return List.of(
        FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
        FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")),
        FunctionClause.of(
            List.of(VariablePattern.of("Map")),
            IsTypeGuard.of("map", Variable.of("Map")),
            decodeBasicItemMapBody()));
  }

  private static Module basicServiceRestJson1Module() {
    return Module.of(
        "basic_service_rest_json_1",
        List.of(decodeBasicItemFunction(null)),
        List.of("Generated REST JSON codec."),
        Moduledoc.of("REST JSON 1 codecs for basic_service (generated)."),
        "basic_types.hrl");
  }

  private static Header basicTypesHeader() {
    return Header.of(
        List.of("Record and type definitions for the basic_service_types model.", ""),
        List.of(
            RecordDef.of(
                "basic_item",
                List.of(
                    TypedField.of("name", "basic_string()"),
                    TypedField.of("count", "basic_integer() | undefined")))),
        List.of(TypeAlias.of("basic_item()", "#basic_item{}")));
  }

  private static Header runtimeTypesHeader() {
    return Header.ofEntries(
        List.of(
            new HeaderIfndef("BEAM_RUNTIME_TYPES_INCLUDED"),
            new HeaderDefine("BEAM_RUNTIME_TYPES_INCLUDED", "true"),
            new HeaderBlankLine(),
            new HeaderComment("HTTP carrier types for generated clients. Adjust only via codegen."),
            new HeaderRecordEntry(runtimeHttpRequestRecord()),
            new HeaderTypeAliasEntry(TypeAlias.of("http_request", "#http_request{}")),
            new HeaderBlankLine(),
            new HeaderRecordEntry(runtimeHttpResponseRecord()),
            new HeaderTypeAliasEntry(TypeAlias.of("http_response", "#http_response{}")),
            new HeaderBlankLine(),
            new HeaderEndif()),
        false);
  }

  private static RecordDef runtimeHttpRequestRecord() {
    return RecordDef.of(
        "http_request",
        List.of(
            TypedField.of("method", "binary()", "<<\"GET\">>"),
            TypedField.of("path", "binary()", "<<\"/\">>"),
            TypedField.of("query", "#{binary() => binary()}", "#{}"),
            TypedField.of("headers", "[{binary(), binary()}]", "[]"),
            TypedField.of("body", "iodata()", "<<>>"),
            TypedField.of("host", "binary() | undefined", "undefined"),
            TypedField.of("stream", "term() | undefined", "undefined")));
  }

  private static RecordDef runtimeHttpResponseRecord() {
    return RecordDef.of(
        "http_response",
        List.of(
            TypedField.of("status", "non_neg_integer()", "200"),
            TypedField.of("headers", "[{binary(), binary()}]", "[]"),
            TypedField.of("body", "iodata()", "<<>>"),
            TypedField.of("stream", "term() | undefined", "undefined")));
  }

  private static Expression filtermapVerboseExpression() {
    return RemoteCallExpr.of(
        "lists",
        "filtermap",
        List.of(
            Fun.of(
                List.of(
                    FunClause.of(
                        VariablePattern.of("V"),
                        NotEqualGuard.of(Variable.of("V"), AtomExpr.of("undefined")),
                        TupleExpr.of(
                            List.of(
                                AtomExpr.of("true"),
                                TupleExpr.of(
                                    List.of(
                                        BinaryExpr.of("verbose"),
                                        LocalCallExpr.of(
                                            "encode_query_value", List.of(Variable.of("V")))))))),
                    FunClause.of(WildcardPattern.of(), AtomExpr.of("false")))),
            ListExpr.of(List.of(Variable.of("Verbose")))));
  }

  private static Header getNameOutputStructureHeader() {
    return Header.of(
        List.of(),
        List.of(
            RecordDef.of("get_name_output", List.of(TypedField.of("name", "name() | undefined")))),
        List.of(TypeAlias.of("get_name_output()", "#get_name_output{}")));
  }

  private static Function awsJsonErrorDispatchGetUserFunction() {
    return Function.of(
        "decode_get_user_response_error",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Status"),
                    WildcardPattern.of("Hdrs"),
                    VariablePattern.of("Body")),
                TupleExpr.of(
                    List.of(
                        AtomExpr.of("error"),
                        TupleExpr.of(
                            List.of(
                                AtomExpr.of("unknown_error"),
                                Variable.of("Status"),
                                Variable.of("Body"))))))),
        null,
        Edoc.of("Error dispatch for smithy.beam.test.awsjson11#GetUser."));
  }

  private static Function uriEncodeFunction() {
    return Function.of(
        "uri_encode",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Value")),
                RemoteCallExpr.of("uri_string", "quote", List.of(Variable.of("Value"))))));
  }

  private static Function uriDecodeFunction() {
    return Function.of(
        "uri_decode",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Value")),
                IsTypeGuard.of("binary", Variable.of("Value")),
                RemoteCallExpr.of("uri_string", "unquote", List.of(Variable.of("Value")))),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined"))));
  }

  private static Function generateUuidFunction() {
    return Function.of(
        "generate_uuid",
        List.of(
            FunctionClause.of(
                List.of(),
                LocalCallExpr.of(
                    "list_to_binary",
                    List.of(
                        RemoteCallExpr.of(
                            "uuid",
                            "to_string",
                            List.of(RemoteCallExpr.of("uuid", "v4", List.of()))))))));
  }

  private static Function headersSetFunction() {
    return Function.of(
        "headers_set",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Name"),
                    VariablePattern.of("Value"),
                    VariablePattern.of("Headers")),
                RemoteCallExpr.of(
                    "lists",
                    "keystore",
                    List.of(
                        Variable.of("Name"),
                        IntegerExpr.of(1),
                        Variable.of("Headers"),
                        TupleExpr.of(List.of(Variable.of("Name"), Variable.of("Value"))))))));
  }

  private static Function toBinaryRestJsonFunction() {
    return Function.of(
        "to_binary",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("binary", Variable.of("V")),
                Variable.of("V")),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("list", Variable.of("V")),
                LocalCallExpr.of("list_to_binary", List.of(Variable.of("V")))),
            FunctionClause.of(List.of(AtomPattern.of("true")), BinaryExpr.of("true")),
            FunctionClause.of(List.of(AtomPattern.of("false")), BinaryExpr.of("false")),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("atom", Variable.of("V")),
                LocalCallExpr.of("atom_to_binary", List.of(Variable.of("V"), AtomExpr.of("utf8")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("integer", Variable.of("V")),
                LocalCallExpr.of("integer_to_binary", List.of(Variable.of("V")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("float", Variable.of("V")),
                LocalCallExpr.of("float_to_binary", List.of(Variable.of("V"))))));
  }

  private static Function encodeQueryValueRestJsonFunction() {
    return Function.of(
        "encode_query_value",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("boolean", Variable.of("V")),
                LocalCallExpr.of("atom_to_binary", List.of(Variable.of("V"), AtomExpr.of("utf8")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("integer", Variable.of("V")),
                LocalCallExpr.of("integer_to_binary", List.of(Variable.of("V")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("float", Variable.of("V")),
                LocalCallExpr.of("float_to_binary", List.of(Variable.of("V")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("binary", Variable.of("V")),
                Variable.of("V")),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("atom", Variable.of("V")),
                LocalCallExpr.of(
                    "atom_to_binary", List.of(Variable.of("V"), AtomExpr.of("utf8"))))));
  }

  private static Function flattenQueryInputFunction() {
    return Function.of(
        "flatten_query_input",
        List.of(
            FunctionClause.of(
                List.of(
                    RecordPattern.of(
                        "delete_user_input",
                        List.of(
                            RecordPatternField.of("user_name", VariablePattern.of("UserName"))))),
                RemoteCallExpr.of(
                    "lists",
                    "append",
                    List.of(
                        ListExpr.of(
                            List.of(
                                LocalCallExpr.of(
                                    "flatten_member",
                                    List.of(
                                        BinaryExpr.of("UserName"), Variable.of("UserName")))))))),
            FunctionClause.of(
                List.of(
                    RecordPattern.of(
                        "list_users_input",
                        List.of(
                            RecordPatternField.of(
                                "path_prefix", VariablePattern.of("PathPrefix"))))),
                RemoteCallExpr.of(
                    "lists",
                    "append",
                    List.of(
                        ListExpr.of(
                            List.of(
                                LocalCallExpr.of(
                                    "flatten_member",
                                    List.of(
                                        BinaryExpr.of("PathPrefix"),
                                        Variable.of("PathPrefix"))))))))));
  }

  private static Function parseListUsersInputInputFunction() {
    return Function.of(
        "parse_list_users_input_input",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Params")),
                RecordExpr.of(
                    "list_users_input",
                    List.of(
                        RecordField.of(
                            "path_prefix",
                            LocalCallExpr.of(
                                "form_value",
                                List.of(Variable.of("Params"), BinaryExpr.of("PathPrefix")))))))));
  }

  private static Function credentialProviderResolveFunction() {
    return Function.of(
        "resolve",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "maps",
                        "get",
                        List.of(
                            AtomExpr.of("credentials"),
                            Variable.of("Config"),
                            AtomExpr.of("undefined"))),
                    List.of(
                        Clause.of(
                            AtomPattern.of("undefined"),
                            LocalCallExpr.of("resolve_chain", List.of(Variable.of("Config")))),
                        Clause.of(
                            VariablePattern.of("Creds"),
                            TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("Creds")))))))),
        Spec.of("resolve(client_config()) -> {ok, aws_credentials()} | {error, term()}"));
  }

  private static Function encodeEventHeadersFunction() {
    return Function.of(
        "encode_event_headers",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("EventType")),
                ListExpr.of(
                    List.of(
                        TupleExpr.of(
                            List.of(BinaryExpr.of(":event-type"), Variable.of("EventType"))),
                        TupleExpr.of(
                            List.of(BinaryExpr.of(":message-type"), BinaryExpr.of("event"))),
                        TupleExpr.of(
                            List.of(
                                BinaryExpr.of(":content-type"),
                                BinaryExpr.of("application/json"))))))));
  }

  private static Function headerValueFunction() {
    return Function.of(
        "header_value",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Headers"), VariablePattern.of("Name")),
                RemoteCallExpr.of(
                    "proplists",
                    "get_value",
                    List.of(
                        Variable.of("Name"), Variable.of("Headers"), AtomExpr.of("undefined"))))));
  }

  private static Function handlerDiscoveryHandleGetNameFunction() {
    return Function.of(
        "handle_get_name",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Ctx"),
                    VariablePattern.of("Input"),
                    VariablePattern.of("Meta")),
                LocalCallExpr.of(
                    "dispatch_handler",
                    List.of(
                        AtomExpr.of("handle_get_name"),
                        Variable.of("Ctx"),
                        Variable.of("Input"),
                        Variable.of("Meta"))))));
  }

  private static Function httpDispatchDispatchArity3Function() {
    return Function.of(
        "dispatch",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("HttpClient"),
                    VariablePattern.of("Config"),
                    VariablePattern.of("Request")),
                LocalCallExpr.of(
                    "dispatch_signed",
                    List.of(
                        Variable.of("HttpClient"),
                        Variable.of("Config"),
                        Variable.of("Request"))))));
  }

  private static Function httpDispatchMimeFunction() {
    return Function.of(
        "mime",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Headers")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "proplists",
                        "get_value",
                        List.of(BinaryExpr.of("Content-Type"), Variable.of("Headers"))),
                    List.of(
                        Clause.of(
                            AtomPattern.of("undefined"), StringExpr.of("application/octet-stream")),
                        Clause.of(
                            VariablePattern.of("CT"),
                            LocalCallExpr.of("binary_to_list", List.of(Variable.of("CT")))))))));
  }

  private static Function decodeQueryParamFunction() {
    return Function.of(
        "decode_query_param",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(BinaryPattern.of("true")), AtomExpr.of("true")),
            FunctionClause.of(List.of(BinaryPattern.of("false")), AtomExpr.of("false")),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("binary", Variable.of("V")),
                Variable.of("V"))));
  }

  private static Function enumDecodeColorFunction() {
    return Function.of(
        "decode_color",
        List.of(
            FunctionClause.of(List.of(BinaryPattern.of("RED")), AtomExpr.of("red")),
            FunctionClause.of(List.of(BinaryPattern.of("BLUE")), AtomExpr.of("blue")),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("binary", Variable.of("V")),
                TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("V")))),
            FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined"))));
  }

  private static Function encodeTimestampEpochSecondsFunction() {
    return Function.of(
        "encode_timestamp_epoch_seconds",
        List.of(
            FunctionClause.of(
                List.of(
                    TuplePattern.of(
                        List.of(
                            VariablePattern.of("Mega"),
                            VariablePattern.of("Secs"),
                            WildcardPattern.of("Micro")))),
                InfixExpr.of(
                    InfixExpr.of(Variable.of("Mega"), "*", IntegerExpr.of(1000000)),
                    "+",
                    Variable.of("Secs"))),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined"))));
  }

  private static Function awsJsonDecodeGetUserRequestFunction() {
    return Function.of(
        "decode_get_user_request",
        List.of(
            FunctionClause.of(
                List.of(
                    RecordPattern.of(
                        "http_request",
                        List.of(RecordPatternField.of("body", VariablePattern.of("Body"))))),
                MatchExpr.bind(
                    "Decoded",
                    decodedBodyCase(),
                    RecordExpr.of(
                        "get_user_input",
                        List.of(RecordField.of("user_name", mapsGet("userName", "Decoded"))))))),
        Spec.of("decode_get_user_request(#http_request{}) -> get_user_input()"),
        Edoc.of("Decode AWS JSON request for smithy.beam.test.awsjson11#GetUser."));
  }

  private static Function encodeTimestampDateTimeFunction() {
    return Function.of(
        "encode_timestamp_date_time",
        List.of(
            FunctionClause.of(
                List.of(
                    TuplePattern.of(
                        List.of(
                            VariablePattern.of("Mega"),
                            VariablePattern.of("Secs"),
                            WildcardPattern.of("Micro")))),
                encodeTimestampDateTimeBody()),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined"))));
  }

  private static MatchExpr encodeTimestampDateTimeBody() {
    Expression epochSecs =
        InfixExpr.of(
            InfixExpr.of(Variable.of("Mega"), "*", IntegerExpr.of(1000000)),
            "+",
            Variable.of("Secs"));
    Expression gregorian =
        RemoteCallExpr.of(
            "calendar",
            "gregorian_seconds_to_datetime",
            List.of(InfixExpr.of(Variable.of("EpochSecs"), "+", IntegerExpr.of(62167219200L))));
    Expression formatted =
        LocalCallExpr.of(
            "iolist_to_binary",
            List.of(
                RemoteCallExpr.of(
                    "io_lib",
                    "format",
                    List.of(
                        StringExpr.of("~4..0B-~2..0B-~2..0BT~2..0B:~2..0B:~2..0BZ"),
                        ListExpr.of(
                            List.of(
                                Variable.of("Y"),
                                Variable.of("Mo"),
                                Variable.of("D"),
                                Variable.of("H"),
                                Variable.of("Mi"),
                                Variable.of("S")))))));
    Pattern dateTimeTuple =
        TuplePattern.of(
            List.of(
                TuplePattern.of(
                    List.of(
                        VariablePattern.of("Y"),
                        VariablePattern.of("Mo"),
                        VariablePattern.of("D"))),
                TuplePattern.of(
                    List.of(
                        VariablePattern.of("H"),
                        VariablePattern.of("Mi"),
                        VariablePattern.of("S")))));
    return MatchExpr.of(
        VariablePattern.of("EpochSecs"),
        epochSecs,
        MatchExpr.of(dateTimeTuple, gregorian, formatted));
  }

  private static CaseExpr decodedBodyCase() {
    return CaseExpr.of(
        Variable.of("Body"),
        List.of(
            Clause.of(BinaryPattern.of(""), MapExpr.of(List.of())),
            Clause.of(
                WildcardPattern.of(),
                CaseExpr.of(
                    RemoteCallExpr.of("jsone", "try_decode", List.of(Variable.of("Body"))),
                    List.of(
                        Clause.of(
                            TuplePattern.of(
                                List.of(
                                    AtomPattern.of("ok"),
                                    VariablePattern.of("Val"),
                                    WildcardPattern.of())),
                            Variable.of("Val")),
                        Clause.of(
                            TuplePattern.of(List.of(AtomPattern.of("error"), WildcardPattern.of())),
                            MapExpr.of(List.of())))))));
  }

  private static RecordExpr decodeBasicItemMapBody() {
    return RecordExpr.of(
        "basic_item",
        List.of(
            RecordField.of("name", mapsGet("name", "Map")),
            RecordField.of("count", mapsGet("count", "Map"))));
  }

  private static RemoteCallExpr mapsGet(String key, String mapVariable) {
    return RemoteCallExpr.of(
        "maps",
        "get",
        List.of(BinaryExpr.of(key), Variable.of(mapVariable), AtomExpr.of("undefined")));
  }

  private static Function decodeJsonBodyFunction() {
    return Function.of(
        "decode_json_body",
        List.of(
            FunctionClause.of(List.of(BinaryPattern.of("")), MapExpr.of(List.of())),
            FunctionClause.of(
                List.of(VariablePattern.of("Body")),
                CaseExpr.of(
                    RemoteCallExpr.of("jsone", "try_decode", List.of(Variable.of("Body"))),
                    List.of(
                        Clause.of(
                            TuplePattern.of(
                                List.of(
                                    AtomPattern.of("ok"),
                                    VariablePattern.of("V"),
                                    WildcardPattern.of())),
                            IsTypeGuard.of("map", Variable.of("V")),
                            Variable.of("V")),
                        Clause.of(WildcardPattern.of(), MapExpr.of(List.of())))))));
  }

  private static Function decodeEventFunction() {
    Pattern messageEntry =
        ListPattern.of(
            List.of(
                TuplePattern.of(List.of(BinaryPattern.of("message"), VariablePattern.of("V")))));
    Pattern codeEntry =
        ListPattern.of(
            List.of(TuplePattern.of(List.of(BinaryPattern.of("code"), VariablePattern.of("V")))));
    Pattern unknownEntry =
        ListPattern.of(
            List.of(TuplePattern.of(List.of(VariablePattern.of("K"), WildcardPattern.of("V")))));

    return Function.of(
        "decode_event",
        List.of(
            FunctionClause.of(
                List.of(MapPattern.bind("Map")),
                CaseExpr.of(
                    RemoteCallExpr.of("maps", "to_list", List.of(Variable.of("Map"))),
                    List.of(
                        Clause.of(
                            messageEntry,
                            TupleExpr.of(List.of(AtomExpr.of("message"), Variable.of("V")))),
                        Clause.of(
                            codeEntry,
                            TupleExpr.of(List.of(AtomExpr.of("code"), Variable.of("V")))),
                        Clause.of(
                            unknownEntry,
                            TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("K")))),
                        Clause.of(WildcardPattern.of(), AtomExpr.of("undefined"))))),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined"))));
  }

  private static Function encodeEventFunction() {
    return Function.of(
        "encode_event",
        List.of(
            FunctionClause.of(
                List.of(
                    TuplePattern.of(List.of(AtomPattern.of("message"), VariablePattern.of("V")))),
                MapExpr.of(List.of(MapEntry.of(BinaryExpr.of("message"), Variable.of("V"))))),
            FunctionClause.of(
                List.of(TuplePattern.of(List.of(AtomPattern.of("code"), VariablePattern.of("V")))),
                MapExpr.of(List.of(MapEntry.of(BinaryExpr.of("code"), Variable.of("V"))))),
            FunctionClause.of(
                List.of(
                    TuplePattern.of(List.of(AtomPattern.of("unknown"), VariablePattern.of("K")))),
                IsTypeGuard.of("binary", Variable.of("K")),
                MapExpr.of(List.of(MapEntry.of(Variable.of("K"), AtomExpr.of("null"))))),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined"))));
  }

  private static Function enumEncodeColorFunction() {
    return Function.of(
        "encode_color",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("red")), BinaryExpr.of("RED")),
            FunctionClause.of(List.of(AtomPattern.of("blue")), BinaryExpr.of("BLUE")),
            FunctionClause.of(
                List.of(
                    TuplePattern.of(List.of(AtomPattern.of("unknown"), VariablePattern.of("V")))),
                IsTypeGuard.of("binary", Variable.of("V")),
                Variable.of("V")),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined"))));
  }

  private static Function decodeTimestampEpochSecondsFunction() {
    Expression epochTuple =
        MatchExpr.bind(
            "Mega",
            InfixExpr.of(Variable.of("V"), "div", IntegerExpr.of(1000000)),
            MatchExpr.bind(
                "Secs",
                InfixExpr.of(Variable.of("V"), "rem", IntegerExpr.of(1000000)),
                TupleExpr.of(
                    List.of(Variable.of("Mega"), Variable.of("Secs"), IntegerExpr.of(0)))));
    return Function.of(
        "decode_timestamp_epoch_seconds",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("number", Variable.of("V")),
                epochTuple)));
  }

  private static Function decodeSparseMapFunction() {
    return Function.of(
        "decode_sparse_map",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("Map")),
                IsTypeGuard.of("map", Variable.of("Map")),
                sparseMapTransform(AtomPattern.of("null"), AtomExpr.of("undefined")))));
  }

  private static Function encodeSparseMapFunction() {
    return Function.of(
        "encode_sparse_map",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("null")),
            FunctionClause.of(
                List.of(VariablePattern.of("Map")),
                IsTypeGuard.of("map", Variable.of("Map")),
                sparseMapTransform(AtomPattern.of("undefined"), AtomExpr.of("null")))));
  }

  private static RemoteCallExpr sparseMapTransform(Pattern nullPattern, Expression replacement) {
    return RemoteCallExpr.of(
        "maps",
        "map",
        List.of(
            Fun.of(
                List.of(
                    FunClause.of(List.of(WildcardPattern.of("K"), nullPattern), replacement),
                    FunClause.of(
                        List.of(WildcardPattern.of("K"), VariablePattern.of("V")),
                        Variable.of("V")))),
            Variable.of("Map")));
  }

  private static Function httpDispatchDispatchArity2Function() {
    return Function.of(
        "dispatch",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config"), VariablePattern.of("Request")),
                MatchExpr.bind(
                    "HttpClient",
                    RemoteCallExpr.of(
                        "maps",
                        "get",
                        List.of(
                            AtomExpr.of("http_client"),
                            Variable.of("Config"),
                            AtomExpr.of("httpc"))),
                    LocalCallExpr.of(
                        "dispatch",
                        List.of(
                            Variable.of("HttpClient"),
                            Variable.of("Config"),
                            Variable.of("Request")))))));
  }

  private static Function sigv4SignFunction() {
    Expression body =
        MatchExpr.bind(
            "Credentials",
            RemoteCallExpr.of(
                "maps", "get", List.of(AtomExpr.of("credentials"), Variable.of("Config"))),
            MatchExpr.bind(
                "Region",
                RemoteCallExpr.of(
                    "maps",
                    "get",
                    List.of(
                        AtomExpr.of("region"), Variable.of("Config"), BinaryExpr.of("us-east-1"))),
                MatchExpr.bind(
                    "Service",
                    RemoteCallExpr.of(
                        "maps", "get", List.of(AtomExpr.of("signing_name"), Variable.of("Config"))),
                    MatchExpr.bind(
                        "Unsigned",
                        RemoteCallExpr.of(
                            "maps",
                            "get",
                            List.of(
                                TupleExpr.of(
                                    List.of(
                                        AtomExpr.of("unsigned_payload"), Variable.of("Operation"))),
                                Variable.of("Config"),
                                AtomExpr.of("false"))),
                        MatchExpr.bind(
                            "Opts",
                            MapExpr.of(
                                List.of(
                                    MapEntry.of(
                                        AtomExpr.of("unsigned_payload"), Variable.of("Unsigned")),
                                    MapEntry.of(
                                        AtomExpr.of("endpoint_host"),
                                        LocalCallExpr.of(
                                            "endpoint_host_from_config",
                                            List.of(Variable.of("Config")))))),
                            LocalCallExpr.of(
                                "sign_request",
                                List.of(
                                    Variable.of("Request"),
                                    Variable.of("Credentials"),
                                    Variable.of("Region"),
                                    Variable.of("Service"),
                                    Variable.of("Opts"))))))));
    return Function.of(
        "sign",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Config"),
                    VariablePattern.of("Operation"),
                    VariablePattern.of("Request")),
                body)),
        Spec.of("sign(client_config(), Operation :: atom(), http_request()) -> http_request()"));
  }

  private static Function endpointRulesMergeParamsFunction() {
    return Function.of(
        "merge_params",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config"), VariablePattern.of("Params")),
                MatchExpr.bind(
                    "ConfigParams",
                    LocalCallExpr.of("config_to_rule_params", List.of(Variable.of("Config"))),
                    MatchExpr.bind(
                        "ClientParams",
                        LocalCallExpr.of("client_context_params", List.of(Variable.of("Config"))),
                        RemoteCallExpr.of(
                            "maps",
                            "merge",
                            List.of(
                                RemoteCallExpr.of(
                                    "maps",
                                    "merge",
                                    List.of(
                                        Variable.of("ConfigParams"), Variable.of("ClientParams"))),
                                Variable.of("Params"))))))));
  }

  private static Function endpointRulesConfigToRuleParamsFunction() {
    return Function.of(
        "config_to_rule_params",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "maps",
                        "get",
                        List.of(
                            AtomExpr.of("region"),
                            Variable.of("Config"),
                            AtomExpr.of("undefined"))),
                    List.of(
                        Clause.of(AtomPattern.of("undefined"), MapExpr.of(List.of())),
                        Clause.of(
                            VariablePattern.of("Value"),
                            MapExpr.of(
                                List.of(
                                    MapEntry.of(
                                        BinaryExpr.of("Region"), Variable.of("Value"))))))))));
  }

  private static Function endpointRulesClientContextParamsFunction() {
    return Function.of(
        "client_context_params",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config")),
                RemoteCallExpr.of(
                    "maps",
                    "merge",
                    List.of(
                        LocalCallExpr.of(
                            "optional_param",
                            List.of(
                                Variable.of("Config"),
                                AtomExpr.of("region"),
                                BinaryExpr.of("Region"))),
                        LocalCallExpr.of(
                            "optional_param",
                            List.of(
                                Variable.of("Config"),
                                AtomExpr.of("bucket"),
                                BinaryExpr.of("Bucket"))))))));
  }

  private static Function endpointRulesOptionalParamFunction() {
    return Function.of(
        "optional_param",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Config"),
                    VariablePattern.of("Key"),
                    VariablePattern.of("RuleKey")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "maps",
                        "get",
                        List.of(
                            Variable.of("Key"), Variable.of("Config"), AtomExpr.of("undefined"))),
                    List.of(
                        Clause.of(AtomPattern.of("undefined"), MapExpr.of(List.of())),
                        Clause.of(
                            VariablePattern.of("Value"),
                            MapExpr.of(
                                List.of(
                                    MapEntry.of(
                                        Variable.of("RuleKey"), Variable.of("Value"))))))))));
  }

  private static Function decodeListFunction() {
    ListComprehensionExpr comprehension =
        ListComprehensionExpr.of(
            Variable.of("V"),
            VariablePattern.of("V"),
            Variable.of("List"),
            InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("null")));
    return Function.of(
        "decode_list",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("List")),
                IsTypeGuard.of("list", Variable.of("List")),
                comprehension)));
  }

  private static Function decodeBasicItemListFunction() {
    return Function.of(
        "decode_basic_item_list",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("List")),
                IsTypeGuard.of("list", Variable.of("List")),
                ListComprehensionExpr.of(
                    LocalCallExpr.of("decode_basic_item", List.of(Variable.of("V"))),
                    VariablePattern.of("V"),
                    Variable.of("List"),
                    InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("null"))))));
  }

  private static Function encodeBasicItemListFunction() {
    return Function.of(
        "encode_basic_item_list",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("List")),
                IsTypeGuard.of("list", Variable.of("List")),
                ListComprehensionExpr.of(
                    LocalCallExpr.of("encode_basic_item", List.of(Variable.of("V"))),
                    VariablePattern.of("V"),
                    Variable.of("List"),
                    InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("undefined"))))));
  }

  private static Function ctBaseFunction() {
    return Function.of(
        "ct_base",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("CT")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "binary", "split", List.of(Variable.of("CT"), BinaryExpr.of(";"))),
                    List.of(
                        Clause.of(
                            ListPattern.cons(VariablePattern.of("Base"), WildcardPattern.of()),
                            Variable.of("Base")),
                        Clause.of(WildcardPattern.of(), Variable.of("CT")))))));
  }

  private static Function decodeSparseListFunction() {
    CaseExpr nullToUndefined =
        CaseExpr.of(
            Variable.of("V"),
            List.of(
                Clause.of(AtomPattern.of("null"), AtomExpr.of("undefined")),
                Clause.of(WildcardPattern.of(), Variable.of("V"))));
    return Function.of(
        "decode_sparse_list",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("List")),
                IsTypeGuard.of("list", Variable.of("List")),
                ListComprehensionExpr.of(
                    nullToUndefined, VariablePattern.of("V"), Variable.of("List")))));
  }

  private static Function encodeSparseListFunction() {
    CaseExpr undefinedToNull =
        CaseExpr.of(
            Variable.of("V"),
            List.of(
                Clause.of(AtomPattern.of("undefined"), AtomExpr.of("null")),
                Clause.of(WildcardPattern.of(), Variable.of("V"))));
    return Function.of(
        "encode_sparse_list",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("null")),
            FunctionClause.of(
                List.of(VariablePattern.of("List")),
                IsTypeGuard.of("list", Variable.of("List")),
                ListComprehensionExpr.of(
                    undefinedToNull, VariablePattern.of("V"), Variable.of("List")))));
  }

  private static Function contentTypeMatchesFunction() {
    Pattern binaryMatchGuard =
        MatchPattern.of(
            BinaryPattern.of(List.of(BinarySegmentPattern.of(WildcardPattern.of(), "binary"))),
            VariablePattern.of("CT"));
    return Function.of(
        "content_type_matches",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Headers"), VariablePattern.of("Expected")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "proplists",
                        "get_value",
                        List.of(
                            BinaryExpr.of("Content-Type"),
                            Variable.of("Headers"),
                            AtomExpr.of("undefined"))),
                    List.of(
                        Clause.of(VariablePattern.of("Expected"), AtomExpr.of("true")),
                        Clause.of(
                            binaryMatchGuard,
                            InfixExpr.of(
                                LocalCallExpr.of("ct_base", List.of(Variable.of("CT"))),
                                "=:=",
                                LocalCallExpr.of("ct_base", List.of(Variable.of("Expected"))))),
                        Clause.of(WildcardPattern.of(), AtomExpr.of("false")))))));
  }

  private static Function prefixHeadersToListFunction() {
    Expression headerName =
        BinaryExpr.of(
            List.of(
                BinarySegmentExpr.of(Variable.of("Prefix"), "binary"),
                BinarySegmentExpr.of(Variable.of("H"), "binary")));
    return Function.of(
        "prefix_headers_to_list",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("_Prefix"), AtomPattern.of("undefined")),
                ListExpr.of(List.of())),
            FunctionClause.of(
                List.of(VariablePattern.of("Prefix"), VariablePattern.of("Map")),
                IsTypeGuard.of("map", Variable.of("Map")),
                ListComprehensionExpr.of(
                    TupleExpr.of(
                        List.of(
                            headerName, LocalCallExpr.of("to_binary", List.of(Variable.of("V"))))),
                    TuplePattern.of(List.of(VariablePattern.of("H"), VariablePattern.of("V"))),
                    RemoteCallExpr.of("maps", "to_list", List.of(Variable.of("Map")))))));
  }

  private static Function prefixHeadersFromListFunction() {
    ListComprehensionExpr headerEntries =
        ListComprehensionExpr.of(
            TupleExpr.of(
                List.of(
                    RemoteCallExpr.of(
                        "binary",
                        "part",
                        List.of(
                            Variable.of("Name"),
                            LocalCallExpr.of("byte_size", List.of(Variable.of("Prefix"))))),
                    Variable.of("Val"))),
            TuplePattern.of(List.of(VariablePattern.of("Name"), VariablePattern.of("Val"))),
            Variable.of("Headers"),
            List.of(
                InfixExpr.of(
                    LocalCallExpr.of("byte_size", List.of(Variable.of("Name"))),
                    ">",
                    LocalCallExpr.of("byte_size", List.of(Variable.of("Prefix")))),
                InfixExpr.of(
                    RemoteCallExpr.of(
                        "binary",
                        "part",
                        List.of(
                            Variable.of("Name"),
                            IntegerExpr.of(0),
                            LocalCallExpr.of("byte_size", List.of(Variable.of("Prefix"))))),
                    "=:=",
                    Variable.of("Prefix"))));
    return Function.of(
        "prefix_headers_from_list",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Headers"), VariablePattern.of("Prefix")),
                MatchExpr.bind(
                    "Map",
                    RemoteCallExpr.of("maps", "from_list", List.of(headerEntries)),
                    CaseExpr.of(
                        RemoteCallExpr.of("maps", "size", List.of(Variable.of("Map"))),
                        List.of(
                            Clause.of(IntegerPattern.of(0), AtomExpr.of("undefined")),
                            Clause.of(WildcardPattern.of(), Variable.of("Map"))))))));
  }

  private static Function decodeTimestampDateTimeFunction() {
    BinaryPattern isoTimestampPattern =
        BinaryPattern.of(
            List.of(
                BinarySegmentPattern.of(VariablePattern.of("Y"), 4, "binary"),
                BinarySegmentPattern.literal("-"),
                BinarySegmentPattern.of(VariablePattern.of("Mo"), 2, "binary"),
                BinarySegmentPattern.literal("-"),
                BinarySegmentPattern.of(VariablePattern.of("D"), 2, "binary"),
                BinarySegmentPattern.literal("T"),
                BinarySegmentPattern.of(VariablePattern.of("H"), 2, "binary"),
                BinarySegmentPattern.literal(":"),
                BinarySegmentPattern.of(VariablePattern.of("Mi"), 2, "binary"),
                BinarySegmentPattern.literal(":"),
                BinarySegmentPattern.of(VariablePattern.of("S"), 2, "binary"),
                BinarySegmentPattern.of(WildcardPattern.of(), "binary")));
    Expression gregorianSecs =
        RemoteCallExpr.of("calendar", "datetime_to_gregorian_seconds", List.of(Variable.of("Dt")));
    Expression epochSecs =
        InfixExpr.of(Variable.of("GregorianSecs"), "-", IntegerExpr.of(62167219200L));
    Expression mega = InfixExpr.of(Variable.of("EpochSecs"), "div", IntegerExpr.of(1000000));
    Expression binaryResult =
        TupleExpr.of(
            List.of(
                Variable.of("Mega"),
                InfixExpr.of(Variable.of("EpochSecs"), "rem", IntegerExpr.of(1000000)),
                IntegerExpr.of(0)));
    TryExpr iso8601Parse =
        TryExpr.of(
            MatchExpr.of(
                isoTimestampPattern,
                Variable.of("V"),
                MatchExpr.bind(
                    "Dt",
                    TupleExpr.of(
                        List.of(
                            TupleExpr.of(
                                List.of(
                                    LocalCallExpr.of(
                                        "binary_to_integer", List.of(Variable.of("Y"))),
                                    LocalCallExpr.of(
                                        "binary_to_integer", List.of(Variable.of("Mo"))),
                                    LocalCallExpr.of(
                                        "binary_to_integer", List.of(Variable.of("D"))))),
                            TupleExpr.of(
                                List.of(
                                    LocalCallExpr.of(
                                        "binary_to_integer", List.of(Variable.of("H"))),
                                    LocalCallExpr.of(
                                        "binary_to_integer", List.of(Variable.of("Mi"))),
                                    LocalCallExpr.of(
                                        "binary_to_integer", List.of(Variable.of("S"))))))),
                    MatchExpr.bind(
                        "GregorianSecs",
                        gregorianSecs,
                        MatchExpr.bind(
                            "EpochSecs", epochSecs, MatchExpr.bind("Mega", mega, binaryResult))))),
            List.of(Clause.of(CatchPattern.anyAny(), AtomExpr.of("undefined"))));
    Expression numberResult =
        MatchExpr.bind(
            "EpochSecs",
            LocalCallExpr.of("trunc", List.of(Variable.of("V"))),
            MatchExpr.bind(
                "Mega",
                InfixExpr.of(Variable.of("EpochSecs"), "div", IntegerExpr.of(1000000)),
                TupleExpr.of(
                    List.of(
                        Variable.of("Mega"),
                        InfixExpr.of(Variable.of("EpochSecs"), "rem", IntegerExpr.of(1000000)),
                        IntegerExpr.of(0)))));
    return Function.of(
        "decode_timestamp_date_time",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("number", Variable.of("V")),
                numberResult),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("binary", Variable.of("V")),
                iso8601Parse)));
  }

  private static Function enumDecodeColorListFunction() {
    return Function.of(
        "decode_color_list",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("List")),
                IsTypeGuard.of("list", Variable.of("List")),
                ListComprehensionExpr.of(
                    LocalCallExpr.of("decode_color", List.of(Variable.of("V"))),
                    VariablePattern.of("V"),
                    Variable.of("List"),
                    InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("null"))))));
  }

  private static Function enumEncodeColorListFunction() {
    return Function.of(
        "encode_color_list",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("List")),
                IsTypeGuard.of("list", Variable.of("List")),
                ListComprehensionExpr.of(
                    LocalCallExpr.of("encode_color", List.of(Variable.of("V"))),
                    VariablePattern.of("V"),
                    Variable.of("List"),
                    InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("undefined"))))));
  }

  private static Function mapDecodeColorLabelsFunction() {
    return Function.of(
        "decode_color_labels",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("Map")),
                IsTypeGuard.of("map", Variable.of("Map")),
                mapKeyValueComprehension("decode_color"))));
  }

  private static Function mapEncodeColorLabelsFunction() {
    return Function.of(
        "encode_color_labels",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("Map")),
                IsTypeGuard.of("map", Variable.of("Map")),
                mapKeyValueComprehension("encode_color"))));
  }

  private static RemoteCallExpr mapKeyValueComprehension(String keyHelper) {
    return RemoteCallExpr.of(
        "maps",
        "from_list",
        List.of(
            ListComprehensionExpr.of(
                TupleExpr.of(
                    List.of(
                        LocalCallExpr.of(keyHelper, List.of(Variable.of("K"))), Variable.of("V"))),
                TuplePattern.of(List.of(VariablePattern.of("K"), VariablePattern.of("V"))),
                RemoteCallExpr.of("maps", "to_list", List.of(Variable.of("Map"))))));
  }

  private static Function encodeEventStreamFunction() {
    return Function.of(
        "encode_event_stream",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Events")),
                IsTypeGuard.of("list", Variable.of("Events")),
                ListComprehensionExpr.of(
                    LocalCallExpr.of("encode_event_stream_event", List.of(Variable.of("E"))),
                    VariablePattern.of("E"),
                    Variable.of("Events")))));
  }

  private static Function decodeEventStreamFunction() {
    return Function.of(
        "decode_event_stream",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Body")),
                IsTypeGuard.of("binary", Variable.of("Body")),
                ListComprehensionExpr.of(
                    LocalCallExpr.of("decode_event_stream_event", List.of(Variable.of("F"))),
                    VariablePattern.of("F"),
                    RemoteCallExpr.of(
                        "aws_event_stream", "decode_frames", List.of(Variable.of("Body")))))));
  }

  private static Function encodeEventStreamEventFunction() {
    Expression payload =
        RemoteCallExpr.of(
            "jsone",
            "encode",
            List.of(
                RemoteCallExpr.of(
                    "maps",
                    "filter",
                    List.of(
                        Fun.of(
                            List.of(
                                FunClause.of(
                                    List.of(WildcardPattern.of(), VariablePattern.of("V")),
                                    InfixExpr.of(
                                        Variable.of("V"), "=/=", AtomExpr.of("undefined"))))),
                        MapExpr.of(
                            List.of(
                                MapEntry.of(
                                    BinaryExpr.of("value"),
                                    RecordFieldAccessExpr.of(
                                        Variable.of("Value"), "member_event", "value"))))))));
    Expression frame =
        RemoteCallExpr.of(
            "aws_event_stream", "frame", List.of(Variable.of("Headers"), Variable.of("Payload")));
    return Function.of(
        "encode_event_stream_event",
        List.of(
            FunctionClause.of(
                List.of(
                    TuplePattern.of(
                        List.of(AtomPattern.of("member"), VariablePattern.of("Value")))),
                MatchExpr.bind(
                    "Payload",
                    payload,
                    MatchExpr.bind(
                        "Headers",
                        LocalCallExpr.of("encode_event_headers", List.of(BinaryExpr.of("member"))),
                        frame))),
            FunctionClause.of(
                List.of(TuplePattern.of(List.of(AtomPattern.of("unknown"), WildcardPattern.of()))),
                LocalCallExpr.of(
                    "error",
                    List.of(
                        TupleExpr.of(
                            List.of(AtomExpr.of("bad_event"), AtomExpr.of("unknown"))))))));
  }

  private static Function decodeEventStreamEventFunction() {
    return Function.of(
        "decode_event_stream_event",
        List.of(
            FunctionClause.of(
                List.of(
                    MapPattern.of(
                        List.of(
                            MapPatternEntry.of(
                                AtomExpr.of("headers"), VariablePattern.of("Headers"), true),
                            MapPatternEntry.of(
                                AtomExpr.of("payload"), VariablePattern.of("Payload"), true)))),
                MatchExpr.bind(
                    "EventType",
                    LocalCallExpr.of(
                        "header_value",
                        List.of(Variable.of("Headers"), BinaryExpr.of(":event-type"))),
                    LocalCallExpr.of(
                        "decode_event_stream_event_type",
                        List.of(Variable.of("EventType"), Variable.of("Payload")))))));
  }

  private static Function decodeEventStreamEventTypeFunction() {
    return Function.of(
        "decode_event_stream_event_type",
        List.of(
            FunctionClause.of(
                List.of(BinaryPattern.of("member"), VariablePattern.of("Payload")),
                TupleExpr.of(
                    List.of(
                        AtomExpr.of("member"),
                        RecordExpr.of(
                            "member_event",
                            List.of(
                                RecordField.of(
                                    "value",
                                    RemoteCallExpr.of(
                                        "maps",
                                        "get",
                                        List.of(
                                            BinaryExpr.of("value"),
                                            RemoteCallExpr.of(
                                                "jsone", "decode", List.of(Variable.of("Payload"))),
                                            AtomExpr.of("undefined"))))))))),
            FunctionClause.of(
                List.of(VariablePattern.of("EventType"), VariablePattern.of("_Payload")),
                LocalCallExpr.of(
                    "error",
                    List.of(
                        TupleExpr.of(
                            List.of(AtomExpr.of("bad_event"), Variable.of("EventType"))))))));
  }

  private static Function encodeBasicItemFunction() {
    return Function.of(
        "encode_basic_item",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("Record")),
                RemoteCallExpr.of(
                    "maps",
                    "filter",
                    List.of(
                        Fun.of(
                            List.of(
                                FunClause.of(
                                    List.of(WildcardPattern.of(), VariablePattern.of("V")),
                                    InfixExpr.of(
                                        Variable.of("V"), "=/=", AtomExpr.of("undefined"))))),
                        MapExpr.of(
                            List.of(
                                MapEntry.of(
                                    BinaryExpr.of("name"),
                                    RecordFieldAccessExpr.of(
                                        Variable.of("Record"), "basic_item", "name")),
                                MapEntry.of(
                                    BinaryExpr.of("count"),
                                    RecordFieldAccessExpr.of(
                                        Variable.of("Record"), "basic_item", "count")))))))));
  }

  private static Function handlerDiscoveryResolveImplFunction() {
    Expression handlersComprehension =
        ListComprehensionExpr.of(
            TupleExpr.of(
                List.of(
                    Variable.of("Fun"),
                    LocalCallExpr.of(
                        "make_handler", List.of(Variable.of("Impl"), Variable.of("Fun"))))),
            List.of(
                ListComprehensionGenerator.of(
                    TuplePattern.of(List.of(VariablePattern.of("Fun"), IntegerPattern.of(3))),
                    Variable.of("Callbacks")),
                ListComprehensionFilter.of(
                    RemoteCallExpr.of(
                        "erlang",
                        "function_exported",
                        List.of(Variable.of("Impl"), Variable.of("Fun"), IntegerExpr.of(3))))));
    Expression successBody =
        MatchExpr.bind(
            "Callbacks",
            RemoteCallExpr.of(
                "basic_service_behaviour", "behaviour_info", List.of(AtomExpr.of("callbacks"))),
            MatchExpr.bind(
                "Handlers",
                RemoteCallExpr.of("maps", "from_list", List.of(handlersComprehension)),
                TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("Handlers")))));
    return Function.of(
        "resolve_impl",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Impl")),
                CaseExpr.of(
                    RemoteCallExpr.of("code", "ensure_loaded", List.of(Variable.of("Impl"))),
                    List.of(
                        Clause.of(
                            TuplePattern.of(
                                List.of(AtomPattern.of("module"), VariablePattern.of("Impl"))),
                            successBody),
                        Clause.of(
                            TuplePattern.of(List.of(AtomPattern.of("error"), WildcardPattern.of())),
                            TupleExpr.of(
                                List.of(
                                    AtomExpr.of("error"),
                                    TupleExpr.of(
                                        List.of(
                                            AtomExpr.of("impl_not_loaded"),
                                            Variable.of("Impl")))))))))));
  }

  private static Function handlerDiscoveryMakeHandlerFunction() {
    return Function.of(
        "make_handler",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Impl"), VariablePattern.of("Fun")),
                Fun.of(
                    List.of(
                        FunClause.of(
                            List.of(
                                VariablePattern.of("Ctx"),
                                VariablePattern.of("Input"),
                                VariablePattern.of("Meta")),
                            RemoteCallExpr.of(
                                Variable.of("Impl"),
                                Variable.of("Fun"),
                                List.of(
                                    Variable.of("Ctx"),
                                    Variable.of("Input"),
                                    Variable.of("Meta")))))))));
  }

  private static Function handlerDiscoveryInitHandlersFunction() {
    return Function.of(
        "init_handlers",
        List.of(
            FunctionClause.of(
                List.of(),
                CaseExpr.of(
                    LocalCallExpr.of("resolve_impl", List.of(MacroExpr.of("DEFAULT_IMPL"))),
                    List.of(
                        Clause.of(
                            TuplePattern.of(
                                List.of(AtomPattern.of("ok"), VariablePattern.of("Handlers"))),
                            MatchExpr.bind(
                                "_",
                                RemoteCallExpr.of(
                                    "persistent_term",
                                    "put",
                                    List.of(MacroExpr.of("HANDLERS_KEY"), Variable.of("Handlers"))),
                                AtomExpr.of("ok"))),
                        Clause.of(
                            TuplePattern.of(
                                List.of(AtomPattern.of("error"), VariablePattern.of("Reason"))),
                            MatchExpr.bind(
                                "_",
                                RemoteCallExpr.of(
                                    "persistent_term",
                                    "put",
                                    List.of(MacroExpr.of("HANDLERS_KEY"), MapExpr.of(List.of()))),
                                TupleExpr.of(
                                    List.of(AtomExpr.of("error"), Variable.of("Reason"))))))))),
        Spec.of("init_handlers() -> ok | {error, term()}"));
  }

  private static Function handlerDiscoveryDispatchHandlerFunction() {
    return Function.of(
        "dispatch_handler",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Fun"),
                    VariablePattern.of("Ctx"),
                    VariablePattern.of("Input"),
                    VariablePattern.of("Meta")),
                MatchExpr.bind(
                    "Handlers",
                    RemoteCallExpr.of(
                        "persistent_term",
                        "get",
                        List.of(MacroExpr.of("HANDLERS_KEY"), MapExpr.of(List.of()))),
                    CaseExpr.of(
                        RemoteCallExpr.of(
                            "maps",
                            "get",
                            List.of(
                                Variable.of("Fun"),
                                Variable.of("Handlers"),
                                AtomExpr.of("undefined"))),
                        List.of(
                            Clause.of(
                                VariablePattern.of("Handler"),
                                ExpressionGuard.of(
                                    LocalCallExpr.of(
                                        "is_function",
                                        List.of(Variable.of("Handler"), IntegerExpr.of(3)))),
                                ApplyExpr.of(
                                    Variable.of("Handler"),
                                    List.of(
                                        Variable.of("Ctx"),
                                        Variable.of("Input"),
                                        Variable.of("Meta")))),
                            Clause.of(
                                WildcardPattern.of(),
                                TupleExpr.of(
                                    List.of(
                                        AtomExpr.of("error"),
                                        AtomExpr.of("not_implemented"))))))))));
  }

  private static Header syntaxTypesHeader() {
    return Header.ofEntries(
        List.of(
            new HeaderIfndef("SYNTAX_TYPES_INCLUDED"),
            new HeaderDefine("SYNTAX_TYPES_INCLUDED", "true"),
            new HeaderBlankLine(),
            new HeaderDefine("DEFAULT_HANDLER", "default"),
            new HeaderDefine("HANDLERS_KEY", "handlers"),
            new HeaderBlankLine(),
            new HeaderRecordEntry(
                RecordDef.of(
                    "item",
                    List.of(
                        TypedField.of("name", "binary()"),
                        TypedField.of("count", "integer() | undefined")))),
            new HeaderTypeAliasEntry(TypeAlias.of("item()", "#item{}")),
            new HeaderBlankLine(),
            new HeaderRecordEntry(
                RecordDef.of(
                    "request",
                    List.of(
                        TypedField.of("method", "binary()", "<<\"GET\">>"),
                        TypedField.of("path", "binary()", "<<\"/\">>"),
                        TypedField.of("query", "#{binary() => binary()}", "#{}"),
                        TypedField.of("headers", "[{binary(), binary()}]", "[]"),
                        TypedField.of("body", "iodata()", "<<>>"),
                        TypedField.of("host", "binary() | undefined", "undefined")))),
            new HeaderTypeAliasEntry(TypeAlias.of("request()", "#request{}")),
            new HeaderBlankLine(),
            new HeaderRecordEntry(
                RecordDef.of(
                    "response",
                    List.of(
                        TypedField.of("status", "non_neg_integer()", "200"),
                        TypedField.of("headers", "[{binary(), binary()}]", "[]"),
                        TypedField.of("body", "iodata()", "<<>>")))),
            new HeaderTypeAliasEntry(TypeAlias.of("response()", "#response{}")),
            new HeaderBlankLine(),
            new HeaderRecordEntry(
                RecordDef.of(
                    "tagged_error",
                    List.of(
                        TypedField.of("code", "atom()"),
                        TypedField.of("message", "binary() | undefined")))),
            new HeaderTypeAliasEntry(TypeAlias.of("tagged_error()", "#tagged_error{}")),
            new HeaderBlankLine(),
            new HeaderTypeAliasEntry(TypeAlias.of("client_config()", "#{binary() => term()}")),
            new HeaderTypeAliasEntry(
                TypeAlias.of(
                    "credentials()",
                    "#{access_key := binary(), secret_key := binary(), token => binary() | undefined}")),
            new HeaderBlankLine(),
            new HeaderEndif()),
        false);
  }

  private static Expression syntaxExpression() {
    return RemoteCallExpr.of(
        "lists",
        "filtermap",
        List.of(
            Fun.of(
                List.of(
                    FunClause.of(
                        VariablePattern.of("V"),
                        NotEqualGuard.of(Variable.of("V"), AtomExpr.of("undefined")),
                        TupleExpr.of(
                            List.of(
                                AtomExpr.of("true"),
                                TupleExpr.of(
                                    List.of(
                                        BinaryExpr.of("key"),
                                        RemoteCallExpr.of(
                                            "codec",
                                            "encode_value",
                                            List.of(Variable.of("V")))))))),
                    FunClause.of(WildcardPattern.of(), AtomExpr.of("false")))),
            ListExpr.of(List.of(Variable.of("Value")))));
  }

  private static Expression syntaxMapEntries() {
    return MapExpr.of(
        List.of(
            MapEntry.of(BinaryExpr.of("field_a"), Variable.of("FieldA")),
            MapEntry.of(BinaryExpr.of("field_b"), Variable.of("FieldB"))));
  }

  static Expression syntaxCaseExpression() {
    return CaseExpr.of(
        LocalCallExpr.of(
            "validate_checksum",
            List.of(
                Variable.of("Body"),
                Variable.of("Headers"),
                ListExpr.of(
                    List.of(BinaryExpr.of("x-checksum-a"), BinaryExpr.of("x-checksum-b"))))),
        List.of(
            Clause.of(
                AtomPattern.of("ok"),
                TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("Output")))),
            Clause.of(
                TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("Reason"))),
                TupleExpr.of(
                    List.of(
                        AtomExpr.of("error"),
                        TupleExpr.of(
                            List.of(AtomExpr.of("checksum_failed"), Variable.of("Reason"))))))));
  }

  private static Expression syntaxStatement() {
    return MatchExpr.bind(
        "Req",
        RemoteCallExpr.of("codec", "encode_request", List.of(Variable.of("Input"))),
        CaseExpr.of(
            RemoteCallExpr.of(
                "transport", "dispatch", List.of(Variable.of("Config"), Variable.of("Req"))),
            List.of(
                Clause.of(
                    TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("Resp"))),
                    RemoteCallExpr.of("codec", "decode_response", List.of(Variable.of("Resp")))),
                Clause.of(
                    TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("Reason"))),
                    TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("Reason")))))));
  }

  private static Expression syntaxPrelude() {
    return MatchExpr.bind(
        "Decoded",
        CaseExpr.of(
            Variable.of("Body"),
            List.of(
                Clause.of(BinaryPattern.of(""), MapExpr.of(List.of())),
                Clause.of(
                    WildcardPattern.of(),
                    CaseExpr.of(
                        RemoteCallExpr.of("jsone", "try_decode", List.of(Variable.of("Body"))),
                        List.of(
                            Clause.of(
                                TuplePattern.of(
                                    List.of(
                                        AtomPattern.of("ok"),
                                        VariablePattern.of("Val"),
                                        WildcardPattern.of())),
                                Variable.of("Val")),
                            Clause.of(
                                TuplePattern.of(
                                    List.of(AtomPattern.of("error"), WildcardPattern.of())),
                                MapExpr.of(List.of()))))))));
  }

  private static Module syntaxModule() {
    return Module.of(
        "syntax_fixture",
        List.of(
            syntaxDecodeFunction(),
            syntaxEncodeFunction(),
            syntaxNoSpecNoDocFunction(),
            syntaxWithSpecAndDocFunction(),
            syntaxWithSpecNoDocFunction(),
            syntaxDecodeEnumFunction(),
            syntaxEncodeEnumFunction(),
            syntaxDecodeUnionFunction(),
            syntaxEncodeUnionFunction(),
            syntaxDispatch2Function(),
            syntaxDispatch3Function(),
            syntaxMatchPatternsFunction(),
            syntaxTransformFunction(),
            syntaxWithRetry2Function(),
            syntaxWithRetry4Function(),
            syntaxRetryableFunction(),
            syntaxRiskyCallFunction(),
            syntaxHelpersFunction(),
            syntaxFetchFunction(),
            syntaxEncodeValueFunction(),
            syntaxCoalesceFunction(),
            syntaxResolveHostFunction(),
            syntaxSplitBaseUrlFunction(),
            syntaxParseHeaderFunction(),
            syntaxFlattenPairsFunction(),
            syntaxFlattenEntryFunction(),
            syntaxContentTypeMatchesFunction(),
            ctBaseFunction(),
            syntaxUpdateRequestFunction(),
            syntaxDispatchHandlerFunction(),
            decodeSparseMapFunction(),
            decodeListFunction(),
            decodeJsonBodyFunction(),
            syntaxMergeParamsFunction(),
            syntaxConfigToParamsFunction(),
            syntaxClientParamsFunction(),
            syntaxOptionalParamFunction(),
            prefixHeadersToListFunction(),
            prefixHeadersFromListFunction(),
            syntaxToBinaryFunction()),
        null,
        Moduledoc.of("Syntax coverage fixture for Erlang IR rendering."),
        List.of("syntax_types.hrl"),
        List.of(TypeAlias.of("config()", "#{binary() => term()}")),
        List.of(
            "with_spec_and_doc/1",
            "with_spec_no_doc/1",
            "no_spec_no_doc/1",
            "decode/1",
            "encode/1",
            "decode_enum/1",
            "encode_enum/1",
            "decode_union/1",
            "encode_union/1",
            "dispatch/2",
            "dispatch/3",
            "match_patterns/1",
            "transform/2",
            "with_retry/2",
            "risky_call/1",
            "helpers/0"));
  }

  private static Function syntaxDecodeFunction() {
    return Function.of(
        "decode",
        syntaxDecodeClauses(),
        Spec.of("decode(undefined | null | map()) -> undefined | #item{}"),
        Edoc.of("Decode input with spec and documentation."));
  }

  private static List<FunctionClause> syntaxDecodeClauses() {
    return List.of(
        FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
        FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")),
        FunctionClause.of(
            List.of(MapPattern.bind("Map")),
            IsTypeGuard.of("map", Variable.of("Map")),
            RecordExpr.of(
                "item",
                List.of(
                    RecordField.of("name", mapsGet("name", "Map")),
                    RecordField.of("count", mapsGet("count", "Map"))))));
  }

  private static Function syntaxEncodeFunction() {
    return Function.of(
        "encode",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("Record")),
                RemoteCallExpr.of(
                    "maps",
                    "filter",
                    List.of(
                        Fun.of(
                            List.of(
                                FunClause.of(
                                    List.of(WildcardPattern.of(), VariablePattern.of("V")),
                                    InfixExpr.of(
                                        Variable.of("V"), "=/=", AtomExpr.of("undefined"))))),
                        MapExpr.of(
                            List.of(
                                MapEntry.of(
                                    BinaryExpr.of("name"),
                                    RecordFieldAccessExpr.of(
                                        Variable.of("Record"), "item", "name")),
                                MapEntry.of(
                                    BinaryExpr.of("count"),
                                    RecordFieldAccessExpr.of(
                                        Variable.of("Record"), "item", "count")))))))),
        Spec.of("encode(item() | undefined) -> map() | undefined"));
  }

  private static Function syntaxNoSpecNoDocFunction() {
    return Function.of(
        "no_spec_no_doc",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(VariablePattern.of("Value")), Variable.of("Value"))));
  }

  private static Function syntaxWithSpecAndDocFunction() {
    return Function.of(
        "with_spec_and_doc",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Record")),
                MapExpr.of(
                    List.of(
                        MapEntry.of(
                            AtomExpr.of("name"),
                            RecordFieldAccessExpr.of(Variable.of("Record"), "item", "name")),
                        MapEntry.of(
                            AtomExpr.of("count"),
                            RecordFieldAccessExpr.of(Variable.of("Record"), "item", "count")))))),
        Spec.of("with_spec_and_doc(item()) -> map()"),
        Edoc.of("Return a plain map from a record."));
  }

  private static Function syntaxWithSpecNoDocFunction() {
    return Function.of(
        "with_spec_no_doc",
        List.of(
            FunctionClause.of(List.of(BinaryPattern.of("a")), AtomExpr.of("a")),
            FunctionClause.of(List.of(BinaryPattern.of("b")), AtomExpr.of("b")),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("binary", Variable.of("V")),
                TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("V"))))),
        Spec.of("with_spec_no_doc(binary()) -> atom() | {unknown, binary()}"));
  }

  private static Function syntaxDecodeEnumFunction() {
    return Function.of(
        "decode_enum",
        List.of(
            FunctionClause.of(List.of(BinaryPattern.of("a")), AtomExpr.of("a")),
            FunctionClause.of(List.of(BinaryPattern.of("b")), AtomExpr.of("b")),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("binary", Variable.of("V")),
                TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("V")))),
            FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined"))));
  }

  private static Function syntaxEncodeEnumFunction() {
    return Function.of(
        "encode_enum",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("a")), BinaryExpr.of("a")),
            FunctionClause.of(List.of(AtomPattern.of("b")), BinaryExpr.of("b")),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined"))));
  }

  private static Function syntaxDecodeUnionFunction() {
    Pattern leftEntry =
        ListPattern.of(
            List.of(TuplePattern.of(List.of(BinaryPattern.of("left"), VariablePattern.of("V")))));
    Pattern rightEntry =
        ListPattern.of(
            List.of(TuplePattern.of(List.of(BinaryPattern.of("right"), VariablePattern.of("V")))));
    Pattern unknownEntry =
        ListPattern.of(
            List.of(TuplePattern.of(List.of(VariablePattern.of("K"), WildcardPattern.of("V")))));
    return Function.of(
        "decode_union",
        List.of(
            FunctionClause.of(
                List.of(MapPattern.bind("Map")),
                CaseExpr.of(
                    RemoteCallExpr.of("maps", "to_list", List.of(Variable.of("Map"))),
                    List.of(
                        Clause.of(
                            leftEntry,
                            TupleExpr.of(List.of(AtomExpr.of("left"), Variable.of("V")))),
                        Clause.of(
                            rightEntry,
                            TupleExpr.of(List.of(AtomExpr.of("right"), Variable.of("V")))),
                        Clause.of(
                            unknownEntry,
                            TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("K")))),
                        Clause.of(WildcardPattern.of(), AtomExpr.of("undefined"))))),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined"))));
  }

  private static Function syntaxEncodeUnionFunction() {
    return Function.of(
        "encode_union",
        List.of(
            FunctionClause.of(
                List.of(TuplePattern.of(List.of(AtomPattern.of("left"), VariablePattern.of("V")))),
                MapExpr.of(List.of(MapEntry.of(BinaryExpr.of("left"), Variable.of("V"))))),
            FunctionClause.of(
                List.of(TuplePattern.of(List.of(AtomPattern.of("right"), VariablePattern.of("V")))),
                MapExpr.of(List.of(MapEntry.of(BinaryExpr.of("right"), Variable.of("V"))))),
            FunctionClause.of(
                List.of(
                    TuplePattern.of(List.of(AtomPattern.of("unknown"), VariablePattern.of("K")))),
                IsTypeGuard.of("binary", Variable.of("K")),
                MapExpr.of(List.of(MapEntry.of(Variable.of("K"), AtomExpr.of("null"))))),
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined"))));
  }

  private static Function syntaxDispatch2Function() {
    return Function.of(
        "dispatch",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config"), VariablePattern.of("Request")),
                MatchExpr.bind(
                    "Handler",
                    RemoteCallExpr.of(
                        "maps",
                        "get",
                        List.of(
                            AtomExpr.of("handler"),
                            Variable.of("Config"),
                            MacroExpr.of("DEFAULT_HANDLER"))),
                    LocalCallExpr.of(
                        "dispatch",
                        List.of(
                            Variable.of("Handler"),
                            Variable.of("Config"),
                            Variable.of("Request")))))));
  }

  private static Function syntaxDispatch3Function() {
    return Function.of(
        "dispatch",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Handler"),
                    VariablePattern.of("_Config"),
                    RecordPattern.of(
                        "request",
                        List.of(
                            RecordPatternField.of("method", VariablePattern.of("Method")),
                            RecordPatternField.of("path", VariablePattern.of("Path"))))),
                IsTypeGuard.of("atom", Variable.of("Handler")),
                TupleExpr.of(
                    List.of(Variable.of("Handler"), Variable.of("Method"), Variable.of("Path"))))));
  }

  private static Function syntaxMatchPatternsFunction() {
    return Function.of(
        "match_patterns",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(
                    MapPattern.of(
                        List.of(
                            MapPatternEntry.of(
                                BinaryExpr.of("key"), VariablePattern.of("Value"), true)))),
                TupleExpr.of(List.of(AtomExpr.of("map"), Variable.of("Value")))),
            FunctionClause.of(
                List.of(
                    RecordPattern.of(
                        "item",
                        List.of(RecordPatternField.of("name", VariablePattern.of("Name"))))),
                TupleExpr.of(List.of(AtomExpr.of("record"), Variable.of("Name")))),
            FunctionClause.of(
                List.of(RecordPattern.of("tagged_error", List.of())),
                TupleExpr.of(List.of(AtomExpr.of("error_record"), AtomExpr.of("true")))),
            FunctionClause.of(
                List.of(
                    TuplePattern.of(List.of(AtomPattern.of("tag"), VariablePattern.of("Value")))),
                TupleExpr.of(List.of(AtomExpr.of("tuple"), Variable.of("Value")))),
            FunctionClause.of(
                List.of(ListPattern.cons(VariablePattern.of("H"), VariablePattern.of("T"))),
                IsTypeGuard.of("list", Variable.of("T")),
                TupleExpr.of(
                    List.of(
                        AtomExpr.of("list"),
                        Variable.of("H"),
                        LocalCallExpr.of("length", List.of(Variable.of("T")))))),
            FunctionClause.of(
                List.of(
                    MatchPattern.of(
                        BinaryPattern.of(
                            List.of(BinarySegmentPattern.of(WildcardPattern.of(), "binary"))),
                        VariablePattern.of("Bin"))),
                TupleExpr.of(List.of(AtomExpr.of("binary"), Variable.of("Bin")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                ExpressionGuard.of(
                    LocalCallExpr.of("is_function", List.of(Variable.of("V"), IntegerExpr.of(1)))),
                ApplyExpr.of(Variable.of("V"), List.of(AtomExpr.of("syntax")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("integer", Variable.of("V")),
                TupleExpr.of(List.of(AtomExpr.of("int"), Variable.of("V")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("atom", Variable.of("V")),
                TupleExpr.of(List.of(AtomExpr.of("atom"), Variable.of("V"))))),
        Spec.of("match_patterns(term()) -> term()"));
  }

  private static Function syntaxTransformFunction() {
    return Function.of(
        "transform",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Config"),
                    RecordPattern.of(
                        "request",
                        List.of(
                            RecordPatternField.of("path", VariablePattern.of("Path")),
                            RecordPatternField.of("query", VariablePattern.of("Query")),
                            RecordPatternField.of("headers", VariablePattern.of("Headers"))))),
                syntaxTransformBody())),
        Spec.of("transform(config(), request()) -> response()"));
  }

  private static Expression syntaxTransformBody() {
    Expression baseUrlCase =
        CaseExpr.of(
            RemoteCallExpr.of(
                "maps",
                "get",
                List.of(AtomExpr.of("base_url"), Variable.of("Config"), AtomExpr.of("undefined"))),
            List.of(
                Clause.of(
                    AtomPattern.of("undefined"),
                    LocalCallExpr.of(
                        "coalesce",
                        List.of(
                            ListExpr.of(
                                List.of(
                                    RemoteCallExpr.of(
                                        "maps",
                                        "get",
                                        List.of(
                                            AtomExpr.of("endpoint"),
                                            Variable.of("Config"),
                                            AtomExpr.of("undefined"))),
                                    LocalCallExpr.of(
                                        "resolve_host", List.of(Variable.of("Config")))))))),
                Clause.of(VariablePattern.of("GivenUrl"), Variable.of("GivenUrl"))));
    Expression queryStrCase =
        CaseExpr.of(
            RemoteCallExpr.of("maps", "to_list", List.of(Variable.of("Query"))),
            List.of(
                Clause.of(ListPattern.of(List.of()), BinaryExpr.of("")),
                Clause.of(
                    VariablePattern.of("Pairs"),
                    MatchExpr.bind(
                        "Encoded",
                        RemoteCallExpr.of(
                            "uri_string",
                            "compose_query",
                            List.of(
                                ListComprehensionExpr.of(
                                    TupleExpr.of(List.of(Variable.of("K"), Variable.of("V"))),
                                    TuplePattern.of(
                                        List.of(VariablePattern.of("K"), VariablePattern.of("V"))),
                                    Variable.of("Pairs")))),
                        BinaryExpr.of(
                            List.of(
                                BinarySegmentExpr.literal("?"),
                                BinarySegmentExpr.of(Variable.of("Encoded"), "binary")))))));
    Expression reqUrlBinary =
        BinaryExpr.of(
            List.of(
                BinarySegmentExpr.of(Variable.of("Scheme"), "binary"),
                BinarySegmentExpr.of(Variable.of("Authority"), "binary"),
                BinarySegmentExpr.of(Variable.of("Path"), "binary"),
                BinarySegmentExpr.of(Variable.of("QueryStr"), "binary")));
    Expression responseRecord =
        RecordExpr.of(
            "response",
            List.of(
                RecordField.of("status", IntegerExpr.of(200)),
                RecordField.of("headers", Variable.of("Headers")),
                RecordField.of("body", Variable.of("ReqUrl"))));
    return MatchExpr.bind(
        "BaseUrl",
        baseUrlCase,
        MatchExpr.bind(
            "QueryStr",
            queryStrCase,
            MatchExpr.of(
                TuplePattern.of(
                    List.of(VariablePattern.of("Scheme"), VariablePattern.of("Authority"))),
                LocalCallExpr.of("split_base_url", List.of(Variable.of("BaseUrl"))),
                MatchExpr.bind("ReqUrl", reqUrlBinary, responseRecord))));
  }

  private static Function syntaxWithRetry2Function() {
    return Function.of(
        "with_retry",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Fun"), VariablePattern.of("Opts")),
                MatchExpr.bind(
                    "Max",
                    RemoteCallExpr.of(
                        "maps",
                        "get",
                        List.of(
                            AtomExpr.of("max_attempts"), Variable.of("Opts"), IntegerExpr.of(3))),
                    MatchExpr.bind(
                        "Base",
                        RemoteCallExpr.of(
                            "maps",
                            "get",
                            List.of(
                                AtomExpr.of("base_delay_ms"),
                                Variable.of("Opts"),
                                IntegerExpr.of(100))),
                        LocalCallExpr.of(
                            "with_retry",
                            List.of(
                                Variable.of("Fun"),
                                Variable.of("Max"),
                                Variable.of("Base"),
                                IntegerExpr.of(1))))))),
        Spec.of("with_retry(fun(() -> term()), map()) -> term()"),
        Edoc.of("Invoke {@code Fun} with exponential backoff on retryable errors."));
  }

  private static Function syntaxWithRetry4Function() {
    Expression sleepCall =
        RemoteCallExpr.of(
            "timer",
            "sleep",
            List.of(
                LocalCallExpr.of(
                    "trunc",
                    List.of(
                        InfixExpr.of(
                            Variable.of("Base"),
                            "*",
                            RemoteCallExpr.of(
                                "math",
                                "pow",
                                List.of(
                                    IntegerExpr.of(2),
                                    InfixExpr.of(Variable.of("N"), "-", IntegerExpr.of(1)))))))));
    Expression retryCall =
        LocalCallExpr.of(
            "with_retry",
            List.of(
                Variable.of("Fun"),
                InfixExpr.of(Variable.of("Attempts"), "-", IntegerExpr.of(1)),
                Variable.of("Base"),
                InfixExpr.of(Variable.of("N"), "+", IntegerExpr.of(1))));
    Expression backoffBody = BlockExpr.commaSeparated(List.of(sleepCall, retryCall), false);
    return Function.of(
        "with_retry",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Fun"),
                    IntegerPattern.of(0),
                    WildcardPattern.of(),
                    WildcardPattern.of()),
                ApplyExpr.of(Variable.of("Fun"), List.of())),
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Fun"),
                    VariablePattern.of("Attempts"),
                    VariablePattern.of("Base"),
                    VariablePattern.of("N")),
                CaseExpr.of(
                    ApplyExpr.of(Variable.of("Fun"), List.of()),
                    List.of(
                        Clause.of(
                            MatchPattern.of(
                                TuplePattern.of(
                                    List.of(AtomPattern.of("ok"), WildcardPattern.of())),
                                VariablePattern.of("Ok")),
                            Variable.of("Ok")),
                        Clause.of(
                            MatchPattern.of(
                                TuplePattern.of(
                                    List.of(AtomPattern.of("error"), WildcardPattern.of())),
                                VariablePattern.of("Err")),
                            CaseExpr.of(
                                LocalCallExpr.of("retryable", List.of(Variable.of("Err"))),
                                List.of(
                                    Clause.of(
                                        AtomPattern.of("true"),
                                        ExpressionGuard.of(
                                            InfixExpr.of(
                                                Variable.of("Attempts"), ">", IntegerExpr.of(1))),
                                        backoffBody),
                                    Clause.of(WildcardPattern.of(), Variable.of("Err"))))))))));
  }

  private static Function syntaxRetryableFunction() {
    return Function.of(
        "retryable",
        List.of(
            FunctionClause.of(
                List.of(
                    TuplePattern.of(
                        List.of(
                            AtomPattern.of("error"), RecordPattern.of("tagged_error", List.of())))),
                AtomExpr.of("true")),
            FunctionClause.of(List.of(WildcardPattern.of()), AtomExpr.of("false"))));
  }

  private static Function syntaxRiskyCallFunction() {
    return Function.of(
        "risky_call",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Fun")),
                TryExpr.of(
                    ApplyExpr.of(Variable.of("Fun"), List.of()),
                    List.of(
                        Clause.of(
                            CatchPattern.anyReason("Reason"),
                            TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("Reason")))))))),
        Spec.of("risky_call(fun(() -> term())) -> term() | {error, term()}"));
  }

  private static Function syntaxHelpersFunction() {
    Expression filtermap =
        RemoteCallExpr.of(
            "lists",
            "filtermap",
            List.of(
                Fun.of(
                    List.of(
                        FunClause.of(
                            VariablePattern.of("V"),
                            NotEqualGuard.of(Variable.of("V"), AtomExpr.of("undefined")),
                            TupleExpr.of(
                                List.of(
                                    AtomExpr.of("true"),
                                    LocalCallExpr.of("encode_value", List.of(Variable.of("V")))))),
                        FunClause.of(WildcardPattern.of(), AtomExpr.of("false")))),
                ListExpr.of(List.of(Variable.of("Result")))));
    Expression ifExpr =
        IfExpr.of(
            List.of(
                IfClause.of(
                    ExpressionGuard.of(
                        InfixExpr.of(
                            LocalCallExpr.of("length", List.of(Variable.of("Filtered"))),
                            ">",
                            IntegerExpr.of(0))),
                    LocalCallExpr.of("hd", List.of(Variable.of("Filtered")))),
                IfClause.of(ExpressionGuard.of(AtomExpr.of("true")), AtomExpr.of("undefined"))));
    return Function.of(
        "helpers",
        List.of(
            FunctionClause.of(
                List.of(),
                MatchExpr.bind(
                    "Result",
                    CaseExpr.of(
                        LocalCallExpr.of("fetch", List.of(BinaryExpr.of("x"))),
                        List.of(
                            Clause.of(
                                TuplePattern.of(
                                    List.of(AtomPattern.of("ok"), VariablePattern.of("Value"))),
                                Variable.of("Value")),
                            Clause.of(AtomPattern.of("error"), AtomExpr.of("default")))),
                    MatchExpr.bind("Filtered", filtermap, ifExpr)))),
        Spec.of("helpers() -> term()"));
  }

  private static Function syntaxFetchFunction() {
    return Function.of(
        "fetch",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Key")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "maps",
                        "get",
                        List.of(
                            Variable.of("Key"), MapExpr.of(List.of()), AtomExpr.of("undefined"))),
                    List.of(
                        Clause.of(AtomPattern.of("undefined"), AtomExpr.of("error")),
                        Clause.of(
                            VariablePattern.of("V"),
                            TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("V")))))))));
  }

  private static Function syntaxEncodeValueFunction() {
    return Function.of(
        "encode_value",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("boolean", Variable.of("V")),
                LocalCallExpr.of("atom_to_binary", List.of(Variable.of("V"), AtomExpr.of("utf8")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("integer", Variable.of("V")),
                LocalCallExpr.of("integer_to_binary", List.of(Variable.of("V")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("float", Variable.of("V")),
                LocalCallExpr.of("float_to_binary", List.of(Variable.of("V")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("binary", Variable.of("V")),
                Variable.of("V")),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("atom", Variable.of("V")),
                LocalCallExpr.of(
                    "atom_to_binary", List.of(Variable.of("V"), AtomExpr.of("utf8"))))));
  }

  private static Function syntaxCoalesceFunction() {
    return Function.of(
        "coalesce",
        List.of(
            FunctionClause.of(
                List.of(ListPattern.cons(VariablePattern.of("H"), VariablePattern.of("Rest"))),
                CaseExpr.of(
                    Variable.of("H"),
                    List.of(
                        Clause.of(
                            AtomPattern.of("undefined"),
                            LocalCallExpr.of("coalesce", List.of(Variable.of("Rest")))),
                        Clause.of(
                            BinaryPattern.of(""),
                            LocalCallExpr.of("coalesce", List.of(Variable.of("Rest")))),
                        Clause.of(VariablePattern.of("Value"), Variable.of("Value"))))),
            FunctionClause.of(List.of(ListPattern.of(List.of())), AtomExpr.of("undefined"))));
  }

  private static Function syntaxResolveHostFunction() {
    return Function.of(
        "resolve_host",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config")),
                RemoteCallExpr.of(
                    "maps",
                    "get",
                    List.of(
                        AtomExpr.of("host"), Variable.of("Config"), BinaryExpr.of("localhost"))))));
  }

  private static Function syntaxSplitBaseUrlFunction() {
    Expression portSuffixCase =
        CaseExpr.of(
            RemoteCallExpr.of(
                "maps",
                "get",
                List.of(AtomExpr.of("port"), Variable.of("Parts"), AtomExpr.of("undefined"))),
            List.of(
                Clause.of(AtomPattern.of("undefined"), BinaryExpr.of("")),
                Clause.of(
                    VariablePattern.of("Port"),
                    BinaryExpr.of(
                        List.of(
                            BinarySegmentExpr.literal(":"),
                            BinarySegmentExpr.of(
                                LocalCallExpr.of("integer_to_binary", List.of(Variable.of("Port"))),
                                "binary"))))));
    Expression successTuple =
        TupleExpr.of(
            List.of(
                BinaryExpr.of(
                    List.of(
                        BinarySegmentExpr.of(
                            LocalCallExpr.of("list_to_binary", List.of(Variable.of("Scheme"))),
                            "binary"),
                        BinarySegmentExpr.literal("://"))),
                BinaryExpr.of(
                    List.of(
                        BinarySegmentExpr.of(
                            LocalCallExpr.of("list_to_binary", List.of(Variable.of("Host"))),
                            "binary"),
                        BinarySegmentExpr.of(Variable.of("PortSuffix"), "binary")))));
    return Function.of(
        "split_base_url",
        List.of(
            FunctionClause.of(
                List.of(BinaryPattern.of("")),
                TupleExpr.of(List.of(BinaryExpr.of(""), BinaryExpr.of("")))),
            FunctionClause.of(
                List.of(VariablePattern.of("BaseUrl")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "uri_string",
                        "parse",
                        List.of(
                            LocalCallExpr.of("binary_to_list", List.of(Variable.of("BaseUrl"))))),
                    List.of(
                        Clause.of(
                            MapPattern.bind(
                                "Parts",
                                List.of(
                                    MapPatternEntry.of(
                                        AtomExpr.of("scheme"), VariablePattern.of("Scheme"), true),
                                    MapPatternEntry.of(
                                        AtomExpr.of("host"), VariablePattern.of("Host"), true))),
                            MatchExpr.bind("PortSuffix", portSuffixCase, successTuple)),
                        Clause.of(
                            WildcardPattern.of(),
                            TupleExpr.of(List.of(BinaryExpr.of(""), Variable.of("BaseUrl")))))))));
  }

  private static Function syntaxParseHeaderFunction() {
    return Function.of(
        "parse_header",
        List.of(
            FunctionClause.of(
                List.of(
                    MatchPattern.of(
                        ListPattern.cons(CharPattern.of('['), WildcardPattern.of()),
                        VariablePattern.of("Line"))),
                RemoteCallExpr.of("string", "trim", List.of(Variable.of("Line")))),
            FunctionClause.of(
                List.of(VariablePattern.of("Line")),
                RemoteCallExpr.of("string", "trim", List.of(Variable.of("Line"))))));
  }

  private static Function syntaxFlattenPairsFunction() {
    return Function.of(
        "flatten_pairs",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Map")),
                IsTypeGuard.of("map", Variable.of("Map")),
                RemoteCallExpr.of(
                    "lists",
                    "append",
                    List.of(
                        ListComprehensionExpr.of(
                            LocalCallExpr.of(
                                "flatten_entry",
                                List.of(
                                    BinaryExpr.of(
                                        List.of(
                                            BinarySegmentExpr.of(Variable.of("Key"), "binary"),
                                            BinarySegmentExpr.literal("."),
                                            BinarySegmentExpr.of(
                                                LocalCallExpr.of(
                                                    "integer_to_binary", List.of(Variable.of("I"))),
                                                "binary"))),
                                    Variable.of("V"))),
                            TuplePattern.of(
                                List.of(
                                    VariablePattern.of("I"),
                                    TuplePattern.of(
                                        List.of(
                                            VariablePattern.of("Key"), VariablePattern.of("V"))))),
                            RemoteCallExpr.of(
                                "lists",
                                "enumerate",
                                List.of(
                                    RemoteCallExpr.of(
                                        "maps", "to_list", List.of(Variable.of("Map"))))),
                            InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("undefined"))))))));
  }

  private static Function syntaxFlattenEntryFunction() {
    return Function.of(
        "flatten_entry",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("_Key"), AtomPattern.of("undefined")),
                ListExpr.of(List.of())),
            FunctionClause.of(
                List.of(VariablePattern.of("Key"), VariablePattern.of("Value")),
                IsTypeGuard.of("map", Variable.of("Value")),
                ListExpr.of(
                    List.of(TupleExpr.of(List.of(Variable.of("Key"), Variable.of("Value")))))),
            FunctionClause.of(
                List.of(VariablePattern.of("Key"), VariablePattern.of("Value")),
                ListExpr.of(
                    List.of(TupleExpr.of(List.of(Variable.of("Key"), Variable.of("Value"))))))));
  }

  private static Function syntaxContentTypeMatchesFunction() {
    Pattern binaryMatchGuard =
        MatchPattern.of(
            BinaryPattern.of(List.of(BinarySegmentPattern.of(WildcardPattern.of(), "binary"))),
            VariablePattern.of("CT"));
    return Function.of(
        "content_type_matches",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Headers"), VariablePattern.of("Expected")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "proplists",
                        "get_value",
                        List.of(
                            BinaryExpr.of("content-type"),
                            Variable.of("Headers"),
                            AtomExpr.of("undefined"))),
                    List.of(
                        Clause.of(VariablePattern.of("Expected"), AtomExpr.of("true")),
                        Clause.of(
                            binaryMatchGuard,
                            InfixExpr.of(
                                LocalCallExpr.of("ct_base", List.of(Variable.of("CT"))),
                                "=:=",
                                LocalCallExpr.of("ct_base", List.of(Variable.of("Expected"))))),
                        Clause.of(WildcardPattern.of(), AtomExpr.of("false")))))));
  }

  private static Function syntaxUpdateRequestFunction() {
    return Function.of(
        "update_request",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Req"), VariablePattern.of("Host")),
                RecordExpr.update(
                    Variable.of("Req"),
                    "request",
                    List.of(RecordField.of("host", Variable.of("Host")))))));
  }

  private static Function syntaxDispatchHandlerFunction() {
    return Function.of(
        "dispatch_handler",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Fun"),
                    VariablePattern.of("Ctx"),
                    VariablePattern.of("Input"),
                    VariablePattern.of("Meta")),
                MatchExpr.bind(
                    "Handlers",
                    RemoteCallExpr.of(
                        "maps",
                        "get",
                        List.of(
                            MacroExpr.of("HANDLERS_KEY"),
                            MapExpr.of(List.of()),
                            MapExpr.of(List.of()))),
                    CaseExpr.of(
                        RemoteCallExpr.of(
                            "maps",
                            "get",
                            List.of(
                                Variable.of("Fun"),
                                Variable.of("Handlers"),
                                AtomExpr.of("undefined"))),
                        List.of(
                            Clause.of(
                                VariablePattern.of("Handler"),
                                ExpressionGuard.of(
                                    LocalCallExpr.of(
                                        "is_function",
                                        List.of(Variable.of("Handler"), IntegerExpr.of(3)))),
                                ApplyExpr.of(
                                    Variable.of("Handler"),
                                    List.of(
                                        Variable.of("Ctx"),
                                        Variable.of("Input"),
                                        Variable.of("Meta")))),
                            Clause.of(
                                WildcardPattern.of(),
                                TupleExpr.of(
                                    List.of(
                                        AtomExpr.of("error"),
                                        AtomExpr.of("not_implemented"))))))))));
  }

  private static Function syntaxMergeParamsFunction() {
    return Function.of(
        "merge_params",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config"), VariablePattern.of("Params")),
                MatchExpr.bind(
                    "ConfigParams",
                    LocalCallExpr.of("config_to_params", List.of(Variable.of("Config"))),
                    MatchExpr.bind(
                        "ClientParams",
                        LocalCallExpr.of("client_params", List.of(Variable.of("Config"))),
                        RemoteCallExpr.of(
                            "maps",
                            "merge",
                            List.of(
                                RemoteCallExpr.of(
                                    "maps",
                                    "merge",
                                    List.of(
                                        Variable.of("ConfigParams"), Variable.of("ClientParams"))),
                                Variable.of("Params"))))))));
  }

  private static Function syntaxConfigToParamsFunction() {
    return Function.of(
        "config_to_params",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "maps",
                        "get",
                        List.of(
                            AtomExpr.of("region"),
                            Variable.of("Config"),
                            AtomExpr.of("undefined"))),
                    List.of(
                        Clause.of(AtomPattern.of("undefined"), MapExpr.of(List.of())),
                        Clause.of(
                            VariablePattern.of("Value"),
                            MapExpr.of(
                                List.of(
                                    MapEntry.of(
                                        BinaryExpr.of("Region"), Variable.of("Value"))))))))));
  }

  private static Function syntaxClientParamsFunction() {
    return Function.of(
        "client_params",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config")),
                RemoteCallExpr.of(
                    "maps",
                    "merge",
                    List.of(
                        LocalCallExpr.of(
                            "optional_param",
                            List.of(
                                Variable.of("Config"),
                                AtomExpr.of("region"),
                                BinaryExpr.of("Region"))),
                        LocalCallExpr.of(
                            "optional_param",
                            List.of(
                                Variable.of("Config"),
                                AtomExpr.of("bucket"),
                                BinaryExpr.of("Bucket"))))))));
  }

  private static Function syntaxOptionalParamFunction() {
    return Function.of(
        "optional_param",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Config"),
                    VariablePattern.of("Key"),
                    VariablePattern.of("ParamKey")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "maps",
                        "get",
                        List.of(
                            Variable.of("Key"), Variable.of("Config"), AtomExpr.of("undefined"))),
                    List.of(
                        Clause.of(AtomPattern.of("undefined"), MapExpr.of(List.of())),
                        Clause.of(
                            VariablePattern.of("Value"),
                            MapExpr.of(
                                List.of(
                                    MapEntry.of(
                                        Variable.of("ParamKey"), Variable.of("Value"))))))))));
  }

  private static Function syntaxToBinaryFunction() {
    return Function.of(
        "to_binary",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("binary", Variable.of("V")),
                Variable.of("V")),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("atom", Variable.of("V")),
                LocalCallExpr.of("atom_to_binary", List.of(Variable.of("V"), AtomExpr.of("utf8")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("integer", Variable.of("V")),
                LocalCallExpr.of("integer_to_binary", List.of(Variable.of("V"))))));
  }
}
