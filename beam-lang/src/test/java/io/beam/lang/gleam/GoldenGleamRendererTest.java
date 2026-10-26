package io.beam.lang.gleam;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoldenGleamRendererTest {

  private final Renderer renderer = GleamRenderer.create();

  @Test
  void rendersSyntaxTypesModule() throws IOException {
    assertGolden("syntax_types.expected.gleam", renderer.render(syntaxTypesModule()));
  }

  @Test
  void rendersSyntaxTypeAlias() throws IOException {
    assertGolden("syntax_type_alias.expected.gleam", renderer.render(syntaxTypeAlias()));
  }

  @Test
  void rendersSyntaxCustomType() throws IOException {
    assertGolden("syntax_custom_type.expected.gleam", renderer.render(syntaxCustomType()));
  }

  @Test
  void rendersSyntaxFunction() throws IOException {
    assertGolden("syntax_function.expected.gleam", renderer.render(syntaxFunction()));
  }

  @Test
  void rendersSyntaxCaseLetPipe() throws IOException {
    assertGolden("syntax_case_let_pipe.expected.gleam", renderer.render(syntaxCaseLetPipe()));
  }

  @Test
  void rendersSyntaxUseRemoteCall() throws IOException {
    assertGolden("syntax_use_remote_call.expected.gleam", renderer.render(syntaxUseRemoteCall()));
  }

  @Test
  void rendersSyntaxPipeFragment() throws IOException {
    assertGolden("syntax_pipe_fragment.expected.gleam", renderer.render(syntaxPipeFragment()));
  }

  private static Module syntaxTypesModule() {
    return Module.of(
        "basic_service_types",
        DocComment.of("Type definitions for the basic model."),
        List.of(
            Import.type("gleam/dict", "Dict"),
            Import.type("gleam/option", "Option")),
        List.of(
            TypeAlias.pub(
                "BasicString", "String", DocComment.of("A named string shape."))),
        List.of(
            CustomType.pub(
                "Status",
                List.of(
                    Variant.of("Active"),
                    Variant.of("Inactive"),
                    Variant.of("Unknown", List.of(Field.positional("String"))))),
            CustomType.pub(
                "Person",
                List.of(
                    Variant.of(
                        "Person",
                        List.of(
                            Field.labelled("name", "String"),
                            Field.labelled("age", "Option(Int)")))))));
  }

  private static TypeAlias syntaxTypeAlias() {
    return TypeAlias.pub("ItemList", "List(Item)");
  }

  private static CustomType syntaxCustomType() {
    return CustomType.pub(
        "Preference",
        List.of(
            Variant.of("High"),
            Variant.of("Low"),
            Variant.of("Unknown", List.of(Field.positional("Int")))),
        DocComment.of("Preference enum with unknown wire value."));
  }

  private static Function syntaxFunction() {
    return Function.pub(
        "get_type_closure",
        List.of(
            Param.of("_config", "ClientConfig"),
            Param.of("_input", "basic_service_types.GetTypeClosureInput")),
        "Result(basic_service_types.GetTypeClosureOutput, ClientError)",
        "Error(NotImplemented)",
        DocComment.of("Fetch a resource by name."));
  }

  private static Function syntaxCaseLetPipe() {
    Expression ageJson =
        CaseExpr.of(
            Variable.of("age"),
            List.of(
                CaseClause.of(
                    ConstructorPattern.of(
                        "Some",
                        List.of(PatternField.positional(VariablePattern.of("value")))),
                    RemoteCallExpr.of("json", "int", List.of(Variable.of("value")))),
                CaseClause.of(
                    ConstructorPattern.of("None"),
                    RemoteCallExpr.of("json", "null", List.of()))));
    Expression object =
        RemoteCallExpr.of(
            "json",
            "object",
            List.of(
                ListExpr.of(
                    List.of(
                        TupleExpr.of(
                            List.of(
                                StringExpr.of("name"),
                                RemoteCallExpr.of(
                                    "json", "string", List.of(Variable.of("name"))))),
                        TupleExpr.of(List.of(StringExpr.of("age"), ageJson))))));
    return Function.pub(
        "person_to_json",
        List.of(Param.of("person", "Person")),
        "json.Json",
        BlockExpr.of(
            List.of(
                LetExpr.of(
                    ConstructorPattern.of(
                        "Person",
                        List.of(PatternField.shorthand("name"), PatternField.shorthand("age"))),
                    Variable.of("person")),
                object)));
  }

  private static Function syntaxUseRemoteCall() {
    return Function.pub(
        "json_to_person",
        List.of(Param.of("data", "dynamic.Dynamic")),
        "Result(Person, List(decode.DecodeError))",
        BlockExpr.of(
            List.of(
                UseExpr.of(
                    "name",
                    RemoteCallExpr.of(
                        "result",
                        "try",
                        List.of(
                            RemoteCallExpr.of(
                                "decode",
                                "field",
                                List.of(
                                    Variable.of("data"),
                                    StringExpr.of("name"),
                                    Variable.of("decode.string")))))),
                UseExpr.of(
                    "age",
                    RemoteCallExpr.of(
                        "result",
                        "try",
                        List.of(
                            RemoteCallExpr.of(
                                "decode",
                                "optional_field",
                                List.of(
                                    Variable.of("data"),
                                    StringExpr.of("age"),
                                    Variable.of("decode.int")))))),
                ConstructorExpr.of(
                    "Ok",
                    List.of(
                        LabelledArg.positional(
                            ConstructorExpr.of(
                                "Person",
                                List.of(
                                    LabelledArg.labelled("name", Variable.of("name")),
                                    LabelledArg.labelled("age", Variable.of("age"))))))))));
  }

  private static Expression syntaxPipeFragment() {
    return PipeExpr.of(
        RemoteCallExpr.of("request", "new", List.of()),
        List.of(
            PipeStep.of(
                RemoteCallExpr.of(
                    "request", "set_method", List.of(Variable.of("http.Post")))),
            PipeStep.of(
                RemoteCallExpr.of("request", "set_body", List.of(Variable.of("body"))))));
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
    String path = "/gleam/" + resourceName;
    try (InputStream in = GoldenGleamRendererTest.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IOException("Missing golden resource: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
