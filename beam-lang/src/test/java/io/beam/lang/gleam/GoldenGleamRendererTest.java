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
