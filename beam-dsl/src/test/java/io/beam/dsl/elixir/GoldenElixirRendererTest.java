package io.beam.dsl.elixir;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoldenElixirRendererTest {

  private final Renderer renderer = ElixirRenderer.create();

  @Test
  void rendersSyntaxModuleCodec() throws IOException {
    assertGolden("syntax_module_codec.expected.ex", renderer.render(syntaxModuleCodec()));
  }

  @Test
  void rendersSyntaxModuleEnumUnion() throws IOException {
    assertGolden("syntax_module_enum_union.expected.ex", renderer.render(syntaxModuleEnumUnion()));
  }

  @Test
  void rendersSyntaxModuleDispatch() throws IOException {
    assertGolden("syntax_module_dispatch.expected.ex", renderer.render(syntaxModuleDispatch()));
  }

  @Test
  void rendersSyntaxModulePatterns() throws IOException {
    assertGolden("syntax_module_patterns.expected.ex", renderer.render(syntaxModulePatterns()));
  }

  @Test
  void rendersSyntaxModuleTransform() throws IOException {
    assertGolden("syntax_module_transform.expected.ex", renderer.render(syntaxModuleTransform()));
  }

  @Test
  void rendersSyntaxModuleRetry() throws IOException {
    assertGolden("syntax_module_retry.expected.ex", renderer.render(syntaxModuleRetry()));
  }

  @Test
  void rendersSyntaxModulePrivate() throws IOException {
    assertGolden("syntax_module_private.expected.ex", renderer.render(syntaxModulePrivate()));
  }

  @Test
  void rendersSyntaxTypesModule() throws IOException {
    assertGolden("syntax_types.expected.ex", renderer.render(syntaxTypesModule()));
  }

  @Test
  void rendersSyntaxExpression() throws IOException {
    assertGolden("syntax_expression.expected.ex", renderer.renderExpression(syntaxExpression()));
  }

  @Test
  void rendersSyntaxMapEntries() throws IOException {
    assertGolden("syntax_map_entries.expected.ex", renderer.renderExpression(syntaxMapEntries()));
  }

  @Test
  void rendersSyntaxCaseExpression() throws IOException {
    assertGolden(
        "syntax_case_expression.expected.ex", renderer.renderExpression(syntaxCaseExpression()));
  }

  @Test
  void rendersSyntaxStatement() throws IOException {
    assertGolden("syntax_statement.expected.ex", renderer.renderStatement(syntaxStatement()));
  }

  @Test
  void rendersSyntaxPrelude() throws IOException {
    assertGolden("syntax_prelude.expected.ex", renderer.renderExpression(syntaxPrelude()));
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
    String path = "/elixir/" + resourceName;
    try (InputStream in = GoldenElixirRendererTest.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IOException("Missing golden resource: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static Module syntaxTypesModule() {
    return new Module(
        "SyntaxTypes",
        Moduledoc.falseLiteral(),
        List.of(),
        List.of(),
        List.of("@default_handler :default", "@handlers_key :handlers"),
        List.of(
            syntaxTypesItem(),
            syntaxTypesRequest(),
            syntaxTypesResponse(),
            syntaxTypesTaggedError()),
        List.of(),
        List.of(
            "@type client_config :: %{binary() => term()}",
            "@type credentials :: %{\n"
                + "          required(:access_key) => binary(),\n"
                + "          required(:secret_key) => binary(),\n"
                + "          optional(:token) => binary() | nil\n"
                + "        }"),
        List.of());
  }

  private static TypesModule syntaxTypesItem() {
    return new TypesModule(
        "Item",
        null,
        new TypeDef(
            "t",
            "%__MODULE__{\n"
                + "            name: binary() | nil,\n"
                + "            count: integer() | nil\n"
                + "          }"),
        List.of(DefstructField.field("name"), DefstructField.field("count")));
  }

  private static TypesModule syntaxTypesRequest() {
    return new TypesModule(
        "Request",
        null,
        new TypeDef(
            "t",
            "%__MODULE__{\n"
                + "            method: binary(),\n"
                + "            path: binary(),\n"
                + "            query: %{binary() => binary()},\n"
                + "            headers: [{binary(), binary()}],\n"
                + "            body: iodata(),\n"
                + "            host: binary() | nil\n"
                + "          }"),
        List.of(
            DefstructField.field("method", StringExpr.of("GET")),
            DefstructField.field("path", StringExpr.of("/")),
            DefstructField.field("query", MapExpr.of(List.of())),
            DefstructField.field("headers", ListExpr.of(List.of())),
            DefstructField.field("body", StringExpr.of("")),
            DefstructField.field("host", NilExpr.of())));
  }

  private static TypesModule syntaxTypesResponse() {
    return new TypesModule(
        "Response",
        null,
        new TypeDef(
            "t",
            "%__MODULE__{\n"
                + "            status: non_neg_integer(),\n"
                + "            headers: [{binary(), binary()}],\n"
                + "            body: iodata()\n"
                + "          }"),
        List.of(
            DefstructField.field("status", IntegerExpr.of(200)),
            DefstructField.field("headers", ListExpr.of(List.of())),
            DefstructField.field("body", StringExpr.of(""))));
  }

  private static TypesModule syntaxTypesTaggedError() {
    return new TypesModule(
        "TaggedError",
        null,
        new TypeDef(
            "t",
            "%__MODULE__{\n"
                + "            code: atom(),\n"
                + "            message: binary() | nil\n"
                + "          }"),
        List.of(DefstructField.field("code"), DefstructField.field("message")));
  }

  private static Module syntaxModuleCodec() {
    return syntaxModule(
        "SyntaxFixtureCodec",
        "Codec and spec/doc coverage for Elixir IR rendering.",
        List.of(Alias.of("SyntaxTypes.Item", "Item")),
        List.of(),
        List.of(),
        List.of(
            syntaxModuleFn0(),
            syntaxModuleFn1(),
            syntaxModuleFn2(),
            syntaxModuleFn3(),
            syntaxModuleFn4(),
            syntaxModuleFn5(),
            syntaxModuleFn6(),
            syntaxModuleFn7(),
            syntaxModuleFn8(),
            syntaxModuleFn9()));
  }

  private static Module syntaxModuleEnumUnion() {
    return syntaxModule(
        "SyntaxFixtureEnumUnion",
        "Enum and union coverage for Elixir IR rendering.",
        List.of(),
        List.of(),
        List.of(),
        List.of(
            syntaxModuleFn10(),
            syntaxModuleFn11(),
            syntaxModuleFn12(),
            syntaxModuleFn13(),
            syntaxModuleFn14(),
            syntaxModuleFn15(),
            syntaxModuleFn16(),
            syntaxModuleFn17(),
            syntaxModuleFn18(),
            syntaxModuleFn19(),
            syntaxModuleFn20(),
            syntaxModuleFn21(),
            syntaxModuleFn22()));
  }

  private static Module syntaxModuleDispatch() {
    return syntaxModule(
        "SyntaxFixtureDispatch",
        "Dispatch coverage for Elixir IR rendering.",
        List.of(Alias.of("SyntaxTypes.Request", "Request")),
        List.of("@default_handler :default"),
        List.of(),
        List.of(syntaxModuleFn23(), syntaxModuleFn24()));
  }

  private static Module syntaxModulePatterns() {
    return syntaxModule(
        "SyntaxFixturePatterns",
        "Pattern matching coverage for Elixir IR rendering.",
        List.of(
            Alias.of("SyntaxTypes.Item", "Item"),
            Alias.of("SyntaxTypes.TaggedError", "TaggedError")),
        List.of(),
        List.of(),
        List.of(
            syntaxModuleFn25(),
            syntaxModuleFn26(),
            syntaxModuleFn27(),
            syntaxModuleFn28(),
            syntaxModuleFn29(),
            syntaxModuleFn66(),
            syntaxModuleFn30(),
            syntaxModuleFn67(),
            syntaxModuleFn31(),
            syntaxModuleFn32()));
  }

  private static Module syntaxModuleTransform() {
    return syntaxModule(
        "SyntaxFixtureTransform",
        "Transform coverage for Elixir IR rendering.",
        List.of(
            Alias.of("SyntaxTypes.Request", "Request"),
            Alias.of("SyntaxTypes.Response", "Response")),
        List.of(),
        List.of("@type config :: %{binary() => term()}"),
        List.of(
            syntaxModuleFn33(),
            syntaxModuleFn41(),
            syntaxModuleFn42(),
            syntaxModuleFn68(),
            syntaxModuleFn43(),
            syntaxModuleFn44()));
  }

  private static Module syntaxModuleRetry() {
    return syntaxModule(
        "SyntaxFixtureRetry",
        "Retry, try/catch, and helper coverage for Elixir IR rendering.",
        List.of(Alias.of("SyntaxTypes.TaggedError", "TaggedError")),
        List.of(),
        List.of(),
        List.of(
            syntaxModuleFn34(),
            syntaxModuleFn69(),
            syntaxModuleFn35(),
            syntaxModuleFn36(),
            syntaxModuleFn37(),
            syntaxModuleFn38(),
            syntaxModuleFn70(),
            syntaxModuleFn39(),
            syntaxModuleFn71(),
            syntaxModuleFn72(),
            syntaxModuleFn73(),
            syntaxModuleFn40(),
            syntaxModuleFn74()));
  }

  private static Module syntaxModulePrivate() {
    return syntaxModule(
        "SyntaxFixturePrivate",
        "Private helper coverage for Elixir IR rendering.",
        List.of(Alias.of("SyntaxTypes.Request", "Request")),
        List.of("@handlers_key :handlers"),
        List.of(),
        List.of(
            syntaxModuleFn75(),
            syntaxModuleFn76(),
            syntaxModuleFn45(),
            syntaxModuleFn46(),
            syntaxModuleFn47(),
            syntaxModuleFn48(),
            syntaxModuleFn49(),
            syntaxModuleFn50(),
            syntaxModuleFn51(),
            syntaxModuleFn52(),
            syntaxModuleFn53(),
            syntaxModuleFn54(),
            syntaxModuleFn55(),
            syntaxModuleFn77(),
            syntaxModuleFn56(),
            syntaxModuleFn57(),
            syntaxModuleFn58(),
            syntaxModuleFn59(),
            syntaxModuleFn60(),
            syntaxModuleFn61(),
            syntaxModuleFn62(),
            syntaxModuleFn63(),
            syntaxModuleFn64(),
            syntaxModuleFn65(),
            syntaxModuleFn78(),
            syntaxModuleFn79()));
  }

  private static Function syntaxModuleFn0() {
    return new Function(
        "decode",
        false,
        List.of(FunctionHead.of(List.of(NilPattern.of()))),
        NilExpr.of(),
        Spec.of("decode(nil | map()) :: Item.t() | nil"),
        FunctionDoc.of("Decode input with spec and documentation."),
        true);
  }

  private static Function syntaxModuleFn1() {
    return new Function(
        "decode",
        false,
        List.of(
            FunctionHead.of(List.of(VariablePattern.of("map")), IsTypeGuard.of("is_map", "map"))),
        StructExpr.of(
            "Item",
            List.of(
                StructField.of(
                    "name",
                    RemoteCallExpr.of(
                        "Map", "get", List.of(Variable.of("map"), StringExpr.of("name")))),
                StructField.of(
                    "count",
                    RemoteCallExpr.of(
                        "Map", "get", List.of(Variable.of("map"), StringExpr.of("count")))))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn2() {
    return new Function(
        "encode",
        false,
        List.of(FunctionHead.of(List.of(NilPattern.of()))),
        NilExpr.of(),
        Spec.of("encode(Item.t() | nil) :: map() | nil"),
        null,
        true);
  }

  private static Function syntaxModuleFn3() {
    return new Function(
        "encode",
        false,
        List.of(
            FunctionHead.of(
                List.of(AssignPattern.of("record", StructPattern.of("Item", List.of()))))),
        new PipeExpr(
            MapExpr.of(
                List.of(
                    MapEntry.stringKey("name", dot(Variable.of("record"), "name")),
                    MapEntry.stringKey("count", dot(Variable.of("record"), "count")))),
            List.of(
                new PipeStep(
                    RemoteCallExpr.of(
                        "Enum",
                        "reject",
                        List.of(
                            new AnonFun(
                                List.of(
                                    AnonFunClause.of(
                                        List.of(
                                            TuplePattern.of(
                                                List.of(
                                                    VariablePattern.of("_k"),
                                                    VariablePattern.of("v")))),
                                        LocalCallExpr.of("is_nil", List.of(Variable.of("v")))))))),
                    List.of()),
                new PipeStep(RemoteCallExpr.of("Map", "new", List.of()), List.of()))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn4() {
    return new Function(
        "no_spec_no_doc",
        false,
        List.of(FunctionHead.of(List.of(NilPattern.of()))),
        NilExpr.of(),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn5() {
    return new Function(
        "no_spec_no_doc",
        false,
        List.of(FunctionHead.of(List.of(VariablePattern.of("value")))),
        Variable.of("value"),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn6() {
    return new Function(
        "with_spec_and_doc",
        false,
        List.of(
            FunctionHead.of(
                List.of(AssignPattern.of("record", StructPattern.of("Item", List.of()))))),
        MapExpr.of(
            List.of(
                MapEntry.atomKey("name", dot(Variable.of("record"), "name")),
                MapEntry.atomKey("count", dot(Variable.of("record"), "count")))),
        Spec.of("with_spec_and_doc(Item.t()) :: map()"),
        FunctionDoc.of("Return a plain map from a struct."),
        false);
  }

  private static Function syntaxModuleFn7() {
    return new Function(
        "with_spec_no_doc",
        false,
        List.of(FunctionHead.of(List.of(StringPattern.of("a")))),
        AtomExpr.of("a"),
        Spec.of("with_spec_no_doc(binary()) :: atom() | {:unknown, binary()}"),
        null,
        true);
  }

  private static Function syntaxModuleFn8() {
    return new Function(
        "with_spec_no_doc",
        false,
        List.of(FunctionHead.of(List.of(StringPattern.of("b")))),
        AtomExpr.of("b"),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn9() {
    return new Function(
        "with_spec_no_doc",
        false,
        List.of(
            FunctionHead.of(List.of(VariablePattern.of("v")), IsTypeGuard.of("is_binary", "v"))),
        TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("v"))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn10() {
    return new Function(
        "decode_enum",
        false,
        List.of(FunctionHead.of(List.of(StringPattern.of("a")))),
        AtomExpr.of("a"),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn11() {
    return new Function(
        "decode_enum",
        false,
        List.of(FunctionHead.of(List.of(StringPattern.of("b")))),
        AtomExpr.of("b"),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn12() {
    return new Function(
        "decode_enum",
        false,
        List.of(
            FunctionHead.of(List.of(VariablePattern.of("v")), IsTypeGuard.of("is_binary", "v"))),
        TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("v"))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn13() {
    return new Function(
        "decode_enum",
        false,
        List.of(FunctionHead.of(List.of(NilPattern.of()))),
        NilExpr.of(),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn14() {
    return new Function(
        "encode_enum",
        false,
        List.of(FunctionHead.of(List.of(AtomPattern.of("a")))),
        StringExpr.of("a"),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn15() {
    return new Function(
        "encode_enum",
        false,
        List.of(FunctionHead.of(List.of(AtomPattern.of("b")))),
        StringExpr.of("b"),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn16() {
    return new Function(
        "encode_enum",
        false,
        List.of(FunctionHead.of(List.of(NilPattern.of()))),
        NilExpr.of(),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn17() {
    return new Function(
        "decode_union",
        false,
        List.of(
            FunctionHead.of(List.of(VariablePattern.of("map")), IsTypeGuard.of("is_map", "map"))),
        new CaseExpr(
            RemoteCallExpr.of("Map", "to_list", List.of(Variable.of("map"))),
            List.of(
                Clause.of(
                    ListPattern.of(
                        List.of(
                            TuplePattern.of(
                                List.of(StringPattern.of("left"), VariablePattern.of("v"))))),
                    TupleExpr.of(List.of(AtomExpr.of("left"), Variable.of("v")))),
                Clause.of(
                    ListPattern.of(
                        List.of(
                            TuplePattern.of(
                                List.of(StringPattern.of("right"), VariablePattern.of("v"))))),
                    TupleExpr.of(List.of(AtomExpr.of("right"), Variable.of("v")))),
                Clause.of(
                    ListPattern.of(
                        List.of(
                            TuplePattern.of(
                                List.of(VariablePattern.of("k"), VariablePattern.of("_v"))))),
                    TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("k")))),
                Clause.of(WildcardPattern.of(), NilExpr.of()))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn18() {
    return new Function(
        "decode_union",
        false,
        List.of(FunctionHead.of(List.of(NilPattern.of()))),
        NilExpr.of(),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn19() {
    return new Function(
        "encode_union",
        false,
        List.of(
            FunctionHead.of(
                List.of(
                    TuplePattern.of(List.of(AtomPattern.of("left"), VariablePattern.of("v")))))),
        MapExpr.of(List.of(MapEntry.stringKey("left", Variable.of("v")))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn20() {
    return new Function(
        "encode_union",
        false,
        List.of(
            FunctionHead.of(
                List.of(
                    TuplePattern.of(List.of(AtomPattern.of("right"), VariablePattern.of("v")))))),
        MapExpr.of(List.of(MapEntry.stringKey("right", Variable.of("v")))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn21() {
    return new Function(
        "encode_union",
        false,
        List.of(
            FunctionHead.of(
                List.of(
                    TuplePattern.of(List.of(AtomPattern.of("unknown"), VariablePattern.of("k")))),
                IsTypeGuard.of("is_binary", "k"))),
        MapExpr.of(List.of(MapEntry.pair(Variable.of("k"), NilExpr.of()))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn22() {
    return new Function(
        "encode_union",
        false,
        List.of(FunctionHead.of(List.of(NilPattern.of()))),
        NilExpr.of(),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn23() {
    return new Function(
        "dispatch",
        false,
        List.of(
            FunctionHead.of(List.of(VariablePattern.of("config"), VariablePattern.of("request")))),
        new BlockExpr(
            List.of(
                MatchExpr.bind(
                    "handler",
                    RemoteCallExpr.of(
                        "Map",
                        "get",
                        List.of(
                            Variable.of("config"),
                            AtomExpr.of("handler"),
                            Variable.of("@default_handler")))),
                LocalCallExpr.of(
                    "dispatch",
                    List.of(
                        Variable.of("handler"), Variable.of("config"), Variable.of("request"))))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn24() {
    return new Function(
        "dispatch",
        false,
        List.of(
            FunctionHead.of(
                List.of(
                    VariablePattern.of("handler"),
                    VariablePattern.of("_config"),
                    StructPattern.of(
                        "Request",
                        List.of(
                            StructPatternField.of("method", VariablePattern.of("method")),
                            StructPatternField.of("path", VariablePattern.of("path"))))),
                IsTypeGuard.of("is_atom", "handler"))),
        TupleExpr.of(List.of(Variable.of("handler"), Variable.of("method"), Variable.of("path"))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn25() {
    return new Function(
        "match_patterns",
        false,
        List.of(FunctionHead.of(List.of(NilPattern.of()))),
        NilExpr.of(),
        Spec.of("match_patterns(term()) :: term()"),
        null,
        true);
  }

  private static Function syntaxModuleFn26() {
    return new Function(
        "match_patterns",
        false,
        List.of(
            FunctionHead.of(
                List.of(
                    MapPattern.of(
                        List.of(
                            MapPatternEntry.of(
                                StringExpr.of("key"), VariablePattern.of("value"))))))),
        TupleExpr.of(List.of(AtomExpr.of("map"), Variable.of("value"))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn27() {
    return new Function(
        "match_patterns",
        false,
        List.of(
            FunctionHead.of(
                List.of(
                    StructPattern.of(
                        "Item",
                        List.of(StructPatternField.of("name", VariablePattern.of("name"))))))),
        TupleExpr.of(List.of(AtomExpr.of("record"), Variable.of("name"))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn28() {
    return new Function(
        "match_patterns",
        false,
        List.of(FunctionHead.of(List.of(StructPattern.of("TaggedError", List.of())))),
        TupleExpr.of(List.of(AtomExpr.of("error_record"), BooleanExpr.of(true))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn29() {
    return new Function(
        "match_patterns",
        false,
        List.of(
            FunctionHead.of(
                List.of(
                    TuplePattern.of(List.of(AtomPattern.of("tag"), VariablePattern.of("value")))))),
        TupleExpr.of(List.of(AtomExpr.of("tuple"), Variable.of("value"))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn30() {
    return new Function(
        "match_patterns",
        false,
        List.of(
            FunctionHead.of(
                List.of(
                    AssignPattern.of(
                        BinaryPattern.of(
                            List.of(BinarySegmentPattern.of(WildcardPattern.of(), "binary"))),
                        VariablePattern.of("bin"))))),
        TupleExpr.of(List.of(AtomExpr.of("binary"), Variable.of("bin"))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn31() {
    return new Function(
        "match_patterns",
        false,
        List.of(
            FunctionHead.of(List.of(VariablePattern.of("v")), IsTypeGuard.of("is_integer", "v"))),
        TupleExpr.of(List.of(AtomExpr.of("int"), Variable.of("v"))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn32() {
    return new Function(
        "match_patterns",
        false,
        List.of(FunctionHead.of(List.of(VariablePattern.of("v")), IsTypeGuard.of("is_atom", "v"))),
        TupleExpr.of(List.of(AtomExpr.of("atom"), Variable.of("v"))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn33() {
    return new Function(
        "transform",
        false,
        List.of(
            FunctionHead.of(
                List.of(
                    VariablePattern.of("config"),
                    StructPattern.of(
                        "Request",
                        List.of(
                            StructPatternField.of("path", VariablePattern.of("path")),
                            StructPatternField.of("query", VariablePattern.of("query")),
                            StructPatternField.of("headers", VariablePattern.of("headers"))))))),
        elixirTransformBody(),
        Spec.of("transform(config(), Request.t()) :: Response.t()"),
        null,
        false);
  }

  private static Function syntaxModuleFn34() {
    return new Function(
        "with_retry",
        false,
        List.of(FunctionHead.of(List.of(VariablePattern.of("fun"), VariablePattern.of("opts")))),
        new BlockExpr(
            List.of(
                MatchExpr.bind(
                    "max",
                    RemoteCallExpr.of(
                        "Map",
                        "get",
                        List.of(
                            Variable.of("opts"), AtomExpr.of("max_attempts"), IntegerExpr.of(3)))),
                MatchExpr.bind(
                    "base",
                    RemoteCallExpr.of(
                        "Map",
                        "get",
                        List.of(
                            Variable.of("opts"),
                            AtomExpr.of("base_delay_ms"),
                            IntegerExpr.of(100)))),
                LocalCallExpr.of(
                    "with_retry",
                    List.of(
                        Variable.of("fun"),
                        Variable.of("max"),
                        Variable.of("base"),
                        IntegerExpr.of(1))))),
        Spec.of("with_retry((-> term()), map()) :: term()"),
        FunctionDoc.of("Invoke fun with exponential backoff on retryable errors."),
        false);
  }

  private static Function syntaxModuleFn35() {
    return new Function(
        "with_retry",
        true,
        List.of(
            FunctionHead.of(
                List.of(
                    VariablePattern.of("fun"),
                    VariablePattern.of("attempts"),
                    VariablePattern.of("base"),
                    VariablePattern.of("n")))),
        withRetryCaseBody(),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn36() {
    return new Function(
        "retryable?",
        true,
        List.of(
            FunctionHead.of(
                List.of(
                    TuplePattern.of(
                        List.of(
                            AtomPattern.of("error"),
                            StructPattern.of("TaggedError", List.of())))))),
        Variable.of("true"),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn37() {
    return new Function(
        "retryable?",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("_")))),
        Variable.of("false"),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn38() {
    return new Function(
        "risky_call",
        false,
        List.of(FunctionHead.of(List.of(VariablePattern.of("fun")))),
        new TryExpr(
            new DotCallExpr(Variable.of("fun"), "()", List.of()),
            List.of(
                new CatchClause(
                    WildcardPattern.of(),
                    VariablePattern.of("reason"),
                    TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("reason")))))),
        Spec.of("risky_call((-> term())) :: term() | {:error, term()}"),
        null,
        false);
  }

  private static Function syntaxModuleFn39() {
    return new Function(
        "fetch",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("key")))),
        new CaseExpr(
            RemoteCallExpr.of("Map", "get", List.of(MapExpr.of(List.of()), Variable.of("key"))),
            List.of(
                Clause.of(NilPattern.of(), AtomExpr.of("error")),
                Clause.of(
                    VariablePattern.of("v"),
                    TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("v")))))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn40() {
    return new Function(
        "encode_value",
        true,
        List.of(
            FunctionHead.of(List.of(VariablePattern.of("v")), IsTypeGuard.of("is_binary", "v"))),
        Variable.of("v"),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn41() {
    return new Function(
        "coalesce",
        true,
        List.of(
            FunctionHead.of(
                List.of(ConsListPattern.of(VariablePattern.of("h"), VariablePattern.of("rest"))))),
        new CaseExpr(
            Variable.of("h"),
            List.of(
                Clause.of(
                    NilPattern.of(), LocalCallExpr.of("coalesce", List.of(Variable.of("rest")))),
                Clause.of(
                    StringPattern.of(""),
                    LocalCallExpr.of("coalesce", List.of(Variable.of("rest")))),
                Clause.of(VariablePattern.of("value"), Variable.of("value")))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn42() {
    return new Function(
        "coalesce",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("[]")))),
        NilExpr.of(),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn43() {
    return new Function(
        "split_base_url",
        true,
        List.of(FunctionHead.of(List.of(StringPattern.of("")))),
        TupleExpr.of(List.of(StringExpr.of(""), StringExpr.of(""))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn44() {
    return new Function(
        "split_base_url",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("base_url")))),
        splitBaseUrlBody(),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn45() {
    return new Function(
        "flatten_pairs",
        true,
        List.of(
            FunctionHead.of(List.of(VariablePattern.of("map")), IsTypeGuard.of("is_map", "map"))),
        flattenPairsBody(),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn46() {
    return new Function(
        "flatten_entry",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("_key"), NilPattern.of()))),
        Variable.of("[]"),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn47() {
    return new Function(
        "flatten_entry",
        true,
        List.of(
            FunctionHead.of(
                List.of(VariablePattern.of("key"), VariablePattern.of("value")),
                IsTypeGuard.of("is_map", "value"))),
        ListExpr.of(List.of(TupleExpr.of(List.of(Variable.of("key"), Variable.of("value"))))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn48() {
    return new Function(
        "flatten_entry",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("key"), VariablePattern.of("value")))),
        Variable.of("[{key, value}]"),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn49() {
    return new Function(
        "content_type_matches",
        true,
        List.of(
            FunctionHead.of(
                List.of(VariablePattern.of("headers"), VariablePattern.of("expected")))),
        new CaseExpr(
            RemoteCallExpr.of(
                "List",
                "keyfind",
                List.of(Variable.of("headers"), StringExpr.of("content-type"), IntegerExpr.of(0))),
            List.of(
                Clause.of(
                    TuplePattern.of(List.of(WildcardPattern.of(), PinPattern.of("expected"))),
                    Variable.of("true")),
                Clause.of(
                    TuplePattern.of(List.of(WildcardPattern.of(), VariablePattern.of("ct"))),
                    IsTypeGuard.of("is_binary", "ct"),
                    new InfixExpr(
                        LocalCallExpr.of("ct_base", List.of(Variable.of("ct"))),
                        "==",
                        LocalCallExpr.of("ct_base", List.of(Variable.of("expected"))))),
                Clause.of(WildcardPattern.of(), Variable.of("false")))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn50() {
    return new Function(
        "ct_base",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("ct")))),
        new CaseExpr(
            RemoteCallExpr.of("String", "split", List.of(Variable.of("ct"), StringExpr.of(";"))),
            List.of(
                Clause.of(
                    ConsListPattern.of(VariablePattern.of("base"), WildcardPattern.of()),
                    Variable.of("base")),
                Clause.of(WildcardPattern.of(), Variable.of("ct")))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn51() {
    return new Function(
        "update_request",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("req"), VariablePattern.of("host")))),
        StructExpr.update(
            Variable.of("req"), "Request", List.of(StructField.of("host", Variable.of("host")))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn52() {
    return new Function(
        "dispatch_handler",
        true,
        List.of(
            FunctionHead.of(
                List.of(
                    VariablePattern.of("fun"),
                    VariablePattern.of("ctx"),
                    VariablePattern.of("input"),
                    VariablePattern.of("meta")))),
        new BlockExpr(
            List.of(
                MatchExpr.bind(
                    "handlers",
                    RemoteCallExpr.of(
                        "Map",
                        "get",
                        List.of(
                            MapExpr.of(List.of()),
                            Variable.of("@handlers_key"),
                            MapExpr.of(List.of())))),
                new CaseExpr(
                    RemoteCallExpr.of(
                        "Map", "get", List.of(Variable.of("handlers"), Variable.of("fun"))),
                    List.of(
                        Clause.of(
                            VariablePattern.of("handler"),
                            FunctionArityGuard.of("handler", 3),
                            dotCall(
                                Variable.of("handler"),
                                List.of(
                                    Variable.of("ctx"),
                                    Variable.of("input"),
                                    Variable.of("meta")))),
                        Clause.of(
                            WildcardPattern.of(),
                            TupleExpr.of(
                                List.of(AtomExpr.of("error"), AtomExpr.of("not_implemented")))))))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn53() {
    return new Function(
        "decode_sparse_map",
        true,
        List.of(FunctionHead.of(List.of(NilPattern.of()))),
        NilExpr.of(),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn54() {
    return new Function(
        "decode_sparse_map",
        true,
        List.of(
            FunctionHead.of(List.of(VariablePattern.of("map")), IsTypeGuard.of("is_map", "map"))),
        RemoteCallExpr.of(
            "Map",
            "new",
            List.of(
                Variable.of("map"),
                new AnonFun(
                    List.of(
                        AnonFunClause.of(
                            List.of(
                                TuplePattern.of(List.of(VariablePattern.of("k"), NilPattern.of()))),
                            TupleExpr.of(List.of(Variable.of("k"), NilExpr.of()))),
                        AnonFunClause.of(
                            List.of(
                                TuplePattern.of(
                                    List.of(VariablePattern.of("k"), VariablePattern.of("v")))),
                            TupleExpr.of(List.of(Variable.of("k"), Variable.of("v")))))))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn55() {
    return new Function(
        "decode_list",
        true,
        List.of(FunctionHead.of(List.of(NilPattern.of()))),
        NilExpr.of(),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn56() {
    return new Function(
        "decode_json_body",
        true,
        List.of(FunctionHead.of(List.of(StringPattern.of("")))),
        MapExpr.of(List.of()),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn57() {
    return new Function(
        "decode_json_body",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("body")))),
        new CaseExpr(
            RemoteCallExpr.of("Jason", "decode", List.of(Variable.of("body"))),
            List.of(
                Clause.of(
                    TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("map"))),
                    IsTypeGuard.of("is_map", "map"),
                    Variable.of("map")),
                Clause.of(WildcardPattern.of(), MapExpr.of(List.of())))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn58() {
    return new Function(
        "merge_params",
        true,
        List.of(
            FunctionHead.of(List.of(VariablePattern.of("config"), VariablePattern.of("params")))),
        new BlockExpr(
            List.of(
                MatchExpr.bind(
                    "config_params",
                    LocalCallExpr.of("config_to_params", List.of(Variable.of("config")))),
                MatchExpr.bind(
                    "client_params",
                    LocalCallExpr.of("client_params", List.of(Variable.of("config")))),
                RemoteCallExpr.of(
                    "Map",
                    "merge",
                    List.of(
                        RemoteCallExpr.of(
                            "Map",
                            "merge",
                            List.of(Variable.of("config_params"), Variable.of("client_params"))),
                        Variable.of("params"))))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn59() {
    return new Function(
        "config_to_params",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("config")))),
        new CaseExpr(
            RemoteCallExpr.of("Map", "get", List.of(Variable.of("config"), AtomExpr.of("region"))),
            List.of(
                Clause.of(NilPattern.of(), MapExpr.of(List.of())),
                Clause.of(
                    VariablePattern.of("value"),
                    MapExpr.of(List.of(MapEntry.stringKey("Region", Variable.of("value"))))))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn60() {
    return new Function(
        "client_params",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("config")))),
        RemoteCallExpr.of(
            "Map",
            "merge",
            List.of(
                LocalCallExpr.of(
                    "optional_param",
                    List.of(Variable.of("config"), AtomExpr.of("region"), StringExpr.of("Region"))),
                LocalCallExpr.of(
                    "optional_param",
                    List.of(
                        Variable.of("config"), AtomExpr.of("bucket"), StringExpr.of("Bucket"))))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn61() {
    return new Function(
        "optional_param",
        true,
        List.of(
            FunctionHead.of(
                List.of(
                    VariablePattern.of("config"),
                    VariablePattern.of("key"),
                    VariablePattern.of("param_key")))),
        new CaseExpr(
            RemoteCallExpr.of("Map", "get", List.of(Variable.of("config"), Variable.of("key"))),
            List.of(
                Clause.of(NilPattern.of(), MapExpr.of(List.of())),
                Clause.of(
                    VariablePattern.of("value"),
                    MapExpr.of(
                        List.of(MapEntry.pair(Variable.of("param_key"), Variable.of("value"))))))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn62() {
    return new Function(
        "prefix_headers_to_list",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("_prefix"), NilPattern.of()))),
        Variable.of("[]"),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn63() {
    return new Function(
        "prefix_headers_to_list",
        true,
        List.of(
            FunctionHead.of(
                List.of(VariablePattern.of("prefix"), VariablePattern.of("map")),
                IsTypeGuard.of("is_map", "map"))),
        RemoteCallExpr.of(
            "Enum",
            "map",
            List.of(
                Variable.of("map"),
                new AnonFun(
                    List.of(
                        AnonFunClause.of(
                            List.of(
                                TuplePattern.of(
                                    List.of(VariablePattern.of("h"), VariablePattern.of("v")))),
                            TupleExpr.of(
                                List.of(
                                    concat(Variable.of("prefix"), Variable.of("h")),
                                    LocalCallExpr.of("to_binary", List.of(Variable.of("v")))))))))),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn64() {
    return new Function(
        "prefix_headers_from_list",
        true,
        List.of(
            FunctionHead.of(List.of(VariablePattern.of("headers"), VariablePattern.of("prefix")))),
        prefixHeadersFromListBody(),
        null,
        null,
        false);
  }

  private static Function syntaxModuleFn65() {
    return new Function(
        "to_binary",
        true,
        List.of(
            FunctionHead.of(List.of(VariablePattern.of("v")), IsTypeGuard.of("is_binary", "v"))),
        Variable.of("v"),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn66() {
    return new Function(
        "match_patterns",
        false,
        List.of(
            FunctionHead.of(
                List.of(ConsListPattern.of(VariablePattern.of("h"), VariablePattern.of("t"))),
                IsTypeGuard.of("is_list", "t"))),
        TupleExpr.of(
            List.of(
                AtomExpr.of("list"),
                Variable.of("h"),
                LocalCallExpr.of("length", List.of(Variable.of("t"))))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn67() {
    return new Function(
        "match_patterns",
        false,
        List.of(FunctionHead.of(List.of(VariablePattern.of("v")), FunctionArityGuard.of("v", 1))),
        dotCall(Variable.of("v"), List.of(AtomExpr.of("syntax"))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn68() {
    return new Function(
        "resolve_host",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("config")))),
        RemoteCallExpr.of(
            "Map",
            "get",
            List.of(Variable.of("config"), AtomExpr.of("host"), StringExpr.of("localhost"))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn69() {
    return new Function(
        "with_retry",
        true,
        List.of(
            FunctionHead.of(
                List.of(
                    VariablePattern.of("fun"),
                    IntegerPattern.of(0),
                    VariablePattern.of("_base"),
                    VariablePattern.of("_n")))),
        new DotCallExpr(Variable.of("fun"), "()", List.of()),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn70() {
    return new Function(
        "helpers",
        false,
        List.of(FunctionHead.of(List.of())),
        new BlockExpr(
            List.of(
                MatchExpr.bind(
                    "result",
                    new CaseExpr(
                        LocalCallExpr.of("fetch", List.of(StringExpr.of("x"))),
                        List.of(
                            Clause.of(
                                TuplePattern.of(
                                    List.of(AtomPattern.of("ok"), VariablePattern.of("value"))),
                                Variable.of("value")),
                            Clause.of(AtomPattern.of("error"), AtomExpr.of("default"))))),
                MatchExpr.bind(
                    "filtered",
                    RemoteCallExpr.of(
                        "Enum",
                        "filter_map",
                        List.of(
                            ListExpr.of(List.of(Variable.of("result"))),
                            new AnonFun(
                                List.of(
                                    AnonFunClause.of(
                                        List.of(VariablePattern.of("v")),
                                        new ComparisonGuard(Variable.of("v"), "!=", NilExpr.of()),
                                        LocalCallExpr.of(
                                            "encode_value", List.of(Variable.of("v")))),
                                    AnonFunClause.of(
                                        List.of(WildcardPattern.of()), AtomExpr.of("pop"))))))),
                new IfExpr(
                    new InfixExpr(
                        LocalCallExpr.of("length", List.of(Variable.of("filtered"))),
                        ">",
                        IntegerExpr.of(0)),
                    LocalCallExpr.of("hd", List.of(Variable.of("filtered"))),
                    NilExpr.of(),
                    false))),
        Spec.of("helpers() :: term()"),
        null,
        false);
  }

  private static Function syntaxModuleFn71() {
    return new Function(
        "encode_value",
        true,
        List.of(
            FunctionHead.of(List.of(VariablePattern.of("v")), IsTypeGuard.of("is_boolean", "v"))),
        RemoteCallExpr.of("Atom", "to_string", List.of(Variable.of("v"))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn72() {
    return new Function(
        "encode_value",
        true,
        List.of(
            FunctionHead.of(List.of(VariablePattern.of("v")), IsTypeGuard.of("is_integer", "v"))),
        RemoteCallExpr.of("Integer", "to_string", List.of(Variable.of("v"))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn73() {
    return new Function(
        "encode_value",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("v")), IsTypeGuard.of("is_float", "v"))),
        RemoteCallExpr.of("Float", "to_string", List.of(Variable.of("v"))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn74() {
    return new Function(
        "encode_value",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("v")), IsTypeGuard.of("is_atom", "v"))),
        RemoteCallExpr.of("Atom", "to_string", List.of(Variable.of("v"))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn75() {
    return new Function(
        "parse_header",
        true,
        List.of(
            FunctionHead.of(
                List.of(
                    AssignPattern.of(
                        ConcatPattern.of(StringPattern.of("["), WildcardPattern.of()),
                        VariablePattern.of("line"))))),
        RemoteCallExpr.of("String", "trim", List.of(Variable.of("line"))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn76() {
    return new Function(
        "parse_header",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("line")))),
        RemoteCallExpr.of("String", "trim", List.of(Variable.of("line"))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn77() {
    return new Function(
        "decode_list",
        true,
        List.of(
            FunctionHead.of(
                List.of(VariablePattern.of("list")), IsTypeGuard.of("is_list", "list"))),
        RemoteCallExpr.of(
            "Enum", "reject", List.of(Variable.of("list"), CaptureExpr.of("is_nil", 1))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn78() {
    return new Function(
        "to_binary",
        true,
        List.of(FunctionHead.of(List.of(VariablePattern.of("v")), IsTypeGuard.of("is_atom", "v"))),
        RemoteCallExpr.of("Atom", "to_string", List.of(Variable.of("v"))),
        null,
        null,
        true);
  }

  private static Function syntaxModuleFn79() {
    return new Function(
        "to_binary",
        true,
        List.of(
            FunctionHead.of(List.of(VariablePattern.of("v")), IsTypeGuard.of("is_integer", "v"))),
        RemoteCallExpr.of("Integer", "to_string", List.of(Variable.of("v"))),
        null,
        null,
        true);
  }

  private static Expression withRetryCaseBody() {
    Expression backoff =
        new InfixExpr(
            Variable.of("base"),
            "*",
            RemoteCallExpr.of(
                ":math",
                "pow",
                List.of(
                    IntegerExpr.of(2), new InfixExpr(Variable.of("n"), "-", IntegerExpr.of(1)))));
    return new CaseExpr(
        new DotCallExpr(Variable.of("fun"), "()", List.of()),
        List.of(
            Clause.of(
                AssignPattern.of(
                    TuplePattern.of(List.of(AtomPattern.of("ok"), WildcardPattern.of())),
                    VariablePattern.of("ok")),
                Variable.of("ok")),
            Clause.of(
                AssignPattern.of(
                    TuplePattern.of(List.of(AtomPattern.of("error"), WildcardPattern.of())),
                    VariablePattern.of("err")),
                new IfExpr(
                    new InfixExpr(
                        LocalCallExpr.of("retryable?", List.of(Variable.of("err"))),
                        "and",
                        new InfixExpr(Variable.of("attempts"), ">", IntegerExpr.of(1))),
                    new BlockExpr(
                        List.of(
                            RemoteCallExpr.of(
                                "Process",
                                "sleep",
                                List.of(LocalCallExpr.of("trunc", List.of(backoff)))),
                            LocalCallExpr.of(
                                "with_retry",
                                List.of(
                                    Variable.of("fun"),
                                    new InfixExpr(Variable.of("attempts"), "-", IntegerExpr.of(1)),
                                    Variable.of("base"),
                                    new InfixExpr(Variable.of("n"), "+", IntegerExpr.of(1)))))),
                    Variable.of("err"),
                    false))));
  }

  private static Expression splitBaseUrlBody() {
    Expression portSuffixCase =
        new CaseExpr(
            dot(Variable.of("uri"), "port"),
            List.of(
                Clause.of(NilPattern.of(), StringExpr.of("")),
                Clause.of(
                    VariablePattern.of("port"),
                    new InterpolatedStringExpr(
                        List.of(
                            new InterpolatedLiteral(":"),
                            new InterpolatedExpr(Variable.of("port")))))));
    return new CaseExpr(
        RemoteCallExpr.of("URI", "parse", List.of(Variable.of("base_url"))),
        List.of(
            Clause.of(
                AssignPattern.of(
                    StructPattern.of(
                        "URI",
                        List.of(
                            StructPatternField.of("scheme", VariablePattern.of("scheme")),
                            StructPatternField.of("host", VariablePattern.of("host")))),
                    VariablePattern.of("uri")),
                IsTypeGuard.of("is_binary", "host"),
                new BlockExpr(
                    List.of(
                        MatchExpr.bind("port_suffix", portSuffixCase),
                        TupleExpr.of(
                            List.of(
                                concat(Variable.of("scheme"), StringExpr.of("://")),
                                concat(Variable.of("host"), Variable.of("port_suffix"))))))),
            Clause.of(
                WildcardPattern.of(),
                TupleExpr.of(List.of(StringExpr.of(""), Variable.of("base_url"))))));
  }

  private static Expression flattenPairsBody() {
    InterpolatedStringExpr keyWithIndex =
        new InterpolatedStringExpr(
            List.of(
                new InterpolatedExpr(Variable.of("key")),
                new InterpolatedLiteral("."),
                new InterpolatedExpr(Variable.of("i"))));
    return new PipeExpr(
        Variable.of("map"),
        List.of(
            new PipeStep(RemoteCallExpr.of("Map", "to_list", List.of()), List.of()),
            new PipeStep(
                RemoteCallExpr.of("Enum", "with_index", List.of(IntegerExpr.of(1))), List.of()),
            new PipeStep(
                RemoteCallExpr.of(
                    "Enum",
                    "flat_map",
                    List.of(
                        new AnonFun(
                            List.of(
                                AnonFunClause.of(
                                    List.of(
                                        TuplePattern.of(
                                            List.of(
                                                VariablePattern.of("i"),
                                                TuplePattern.of(
                                                    List.of(
                                                        VariablePattern.of("key"),
                                                        VariablePattern.of("v")))))),
                                    new IfExpr(
                                        LocalCallExpr.of("is_nil", List.of(Variable.of("v"))),
                                        ListExpr.of(List.of()),
                                        LocalCallExpr.of(
                                            "flatten_entry",
                                            List.of(keyWithIndex, Variable.of("v"))),
                                        false)))))),
                List.of())));
  }

  private static Expression prefixHeadersFromListBody() {
    return new PipeExpr(
        Variable.of("headers"),
        List.of(
            new PipeStep(
                RemoteCallExpr.of(
                    "Enum",
                    "filter",
                    List.of(
                        new AnonFun(
                            List.of(
                                AnonFunClause.of(
                                    List.of(
                                        TuplePattern.of(
                                            List.of(
                                                VariablePattern.of("name"), WildcardPattern.of()))),
                                    new InfixExpr(
                                        LocalCallExpr.of("byte_size", List.of(Variable.of("name"))),
                                        ">",
                                        LocalCallExpr.of(
                                            "byte_size", List.of(Variable.of("prefix"))))))))),
                List.of()),
            new PipeStep(
                RemoteCallExpr.of(
                    "Enum",
                    "filter",
                    List.of(
                        new AnonFun(
                            List.of(
                                AnonFunClause.of(
                                    List.of(
                                        TuplePattern.of(
                                            List.of(
                                                VariablePattern.of("name"), WildcardPattern.of()))),
                                    new InfixExpr(
                                        LocalCallExpr.of(
                                            "binary_part",
                                            List.of(
                                                Variable.of("name"),
                                                IntegerExpr.of(0),
                                                LocalCallExpr.of(
                                                    "byte_size", List.of(Variable.of("prefix"))))),
                                        "==",
                                        Variable.of("prefix"))))))),
                List.of()),
            new PipeStep(
                RemoteCallExpr.of(
                    "Map",
                    "new",
                    List.of(
                        new AnonFun(
                            List.of(
                                AnonFunClause.of(
                                    List.of(
                                        TuplePattern.of(
                                            List.of(
                                                VariablePattern.of("name"),
                                                VariablePattern.of("val")))),
                                    TupleExpr.of(
                                        List.of(
                                            LocalCallExpr.of(
                                                "binary_part",
                                                List.of(
                                                    Variable.of("name"),
                                                    LocalCallExpr.of(
                                                        "byte_size",
                                                        List.of(Variable.of("prefix"))),
                                                    new InfixExpr(
                                                        LocalCallExpr.of(
                                                            "byte_size",
                                                            List.of(Variable.of("name"))),
                                                        "-",
                                                        LocalCallExpr.of(
                                                            "byte_size",
                                                            List.of(Variable.of("prefix")))))),
                                            Variable.of("val")))))))),
                List.of()),
            new PipeStep(
                CaseExpr.piped(
                    List.of(
                        Clause.of(
                            VariablePattern.of("map"),
                            new ComparisonGuard(Variable.of("map"), "==", MapExpr.of(List.of())),
                            NilExpr.of()),
                        Clause.of(VariablePattern.of("map"), Variable.of("map")))),
                List.of())));
  }

  private static Expression elixirTransformBody() {
    Expression baseUrlCase =
        new CaseExpr(
            RemoteCallExpr.of(
                "Map", "get", List.of(Variable.of("config"), AtomExpr.of("base_url"))),
            List.of(
                Clause.of(
                    NilPattern.of(),
                    LocalCallExpr.of(
                        "coalesce",
                        List.of(
                            ListExpr.of(
                                List.of(
                                    RemoteCallExpr.of(
                                        "Map",
                                        "get",
                                        List.of(Variable.of("config"), AtomExpr.of("endpoint"))),
                                    LocalCallExpr.of(
                                        "resolve_host", List.of(Variable.of("config")))))))),
                Clause.of(VariablePattern.of("given_url"), Variable.of("given_url"))));
    Expression queryStrCase =
        new CaseExpr(
            RemoteCallExpr.of("Map", "to_list", List.of(Variable.of("query"))),
            List.of(
                Clause.of(ListPattern.of(List.of()), StringExpr.of("")),
                Clause.of(
                    VariablePattern.of("pairs"),
                    MatchExpr.bind(
                        "encoded",
                        RemoteCallExpr.of("URI", "encode_query", List.of(Variable.of("pairs"))),
                        concat(StringExpr.of("?"), Variable.of("encoded"))))));
    return new BlockExpr(
        List.of(
            MatchExpr.bind("base_url", baseUrlCase),
            MatchExpr.bind("query_str", queryStrCase),
            MatchExpr.bind(
                TuplePattern.of(
                    List.of(VariablePattern.of("scheme"), VariablePattern.of("authority"))),
                LocalCallExpr.of("split_base_url", List.of(Variable.of("base_url")))),
            MatchExpr.bind(
                "req_url",
                concat(
                    concat(
                        concat(Variable.of("scheme"), Variable.of("authority")),
                        Variable.of("path")),
                    Variable.of("query_str"))),
            StructExpr.of(
                "Response",
                List.of(
                    StructField.of("status", IntegerExpr.of(200)),
                    StructField.of("headers", Variable.of("headers")),
                    StructField.of("body", Variable.of("req_url"))))));
  }

  private static Expression syntaxExpression() {
    AnonFun filterMapFun =
        new AnonFun(
            List.of(
                AnonFunClause.of(
                    List.of(VariablePattern.of("v")),
                    new ComparisonGuard(Variable.of("v"), "!=", NilExpr.of()),
                    TupleExpr.of(
                        List.of(
                            StringExpr.of("key"),
                            RemoteCallExpr.of(
                                "Codec", "encode_value", List.of(Variable.of("v")))))),
                AnonFunClause.of(List.of(WildcardPattern.of()), AtomExpr.of("pop"))));
    return new PipeExpr(
        ListExpr.of(List.of(Variable.of("value"))),
        List.of(
            new PipeStep(
                RemoteCallExpr.of("Enum", "filter_map", List.of(filterMapFun)), List.of())));
  }

  private static Expression syntaxMapEntries() {
    return MapExpr.of(
        List.of(
            MapEntry.stringKey("field_a", Variable.of("field_a")),
            MapEntry.stringKey("field_b", Variable.of("field_b"))));
  }

  private static Expression syntaxCaseExpression() {
    return new CaseExpr(
        LocalCallExpr.of(
            "validate_checksum",
            List.of(
                Variable.of("body"),
                Variable.of("headers"),
                ListExpr.of(
                    List.of(StringExpr.of("x-checksum-a"), StringExpr.of("x-checksum-b"))))),
        List.of(
            Clause.of(
                AtomPattern.of("ok"),
                TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("output")))),
            Clause.of(
                TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("reason"))),
                TupleExpr.of(
                    List.of(
                        AtomExpr.of("error"),
                        TupleExpr.of(
                            List.of(AtomExpr.of("checksum_failed"), Variable.of("reason"))))))));
  }

  private static Expression syntaxStatement() {
    return MatchExpr.bind(
        "req",
        RemoteCallExpr.of("Codec", "encode_request", List.of(Variable.of("input"))),
        new CaseExpr(
            RemoteCallExpr.of(
                "Transport", "dispatch", List.of(Variable.of("config"), Variable.of("req"))),
            List.of(
                Clause.of(
                    TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("resp"))),
                    RemoteCallExpr.of("Codec", "decode_response", List.of(Variable.of("resp")))),
                Clause.of(
                    TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("reason"))),
                    TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("reason")))))));
  }

  private static Expression syntaxPrelude() {
    return MatchExpr.bind(
        "decoded",
        new CaseExpr(
            Variable.of("body"),
            List.of(
                Clause.of(StringPattern.of(""), MapExpr.of(List.of())),
                Clause.of(
                    WildcardPattern.of(),
                    new CaseExpr(
                        RemoteCallExpr.of("Jason", "decode", List.of(Variable.of("body"))),
                        List.of(
                            Clause.of(
                                TuplePattern.of(
                                    List.of(AtomPattern.of("ok"), VariablePattern.of("val"))),
                                Variable.of("val")),
                            Clause.of(
                                TuplePattern.of(
                                    List.of(AtomPattern.of("error"), WildcardPattern.of())),
                                MapExpr.of(List.of()))))))));
  }

  private static Module syntaxModule(
      String name,
      String moduledoc,
      List<Alias> aliases,
      List<String> moduleAttributes,
      List<String> trailingModuleAttributes,
      List<Function> functions) {
    return new Module(
        name,
        Moduledoc.of(moduledoc),
        List.of(),
        aliases,
        moduleAttributes,
        List.of(),
        List.of(),
        trailingModuleAttributes,
        functions);
  }

  private static DotCallExpr dot(Expression receiver, String field) {
    return new DotCallExpr(receiver, field, List.of());
  }

  private static DotCallExpr dotCall(Expression receiver, List<Expression> args) {
    return new DotCallExpr(receiver, "()", args);
  }

  private static InfixExpr concat(Expression left, Expression right) {
    return new InfixExpr(left, "<>", right);
  }
}
