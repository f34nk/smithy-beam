package io.smithy.beam.elixir.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.Symbol;

class ElixirImportContainerTest {

    // -------------------------------------------------------------------------
    // .ex symbols → aliases
    // -------------------------------------------------------------------------

    @Test
    void exSymbolAddsToAliases() {
        ElixirImportContainer container = new ElixirImportContainer();
        Symbol symbol = Symbol.builder()
                .name("Weather.Types.GetForecastInput")
                .definitionFile("src/generated/weather_types.ex")
                .build();

        container.importSymbol(symbol, "GetForecastInput");

        assertThat(container.getAliases()).contains("Weather.Types.GetForecastInput");
    }

    @Test
    void exSymbolDoesNotAddToUses() {
        ElixirImportContainer container = new ElixirImportContainer();
        Symbol symbol = Symbol.builder()
                .name("Weather.Types.GetForecastInput")
                .definitionFile("src/generated/weather_types.ex")
                .build();

        container.importSymbol(symbol, "GetForecastInput");

        assertThat(container.getUses()).isEmpty();
    }

    @Test
    void exSymbolDoesNotAddToImports() {
        ElixirImportContainer container = new ElixirImportContainer();
        Symbol symbol = Symbol.builder()
                .name("Weather.Types.GetForecastInput")
                .definitionFile("src/generated/weather_types.ex")
                .build();

        container.importSymbol(symbol, "GetForecastInput");

        assertThat(container.getImports()).isEmpty();
    }

    @Test
    void symbolWithNoDefinitionFileAddsNothing() {
        ElixirImportContainer container = new ElixirImportContainer();
        Symbol symbol = Symbol.builder().name("String.t()").build();

        container.importSymbol(symbol, "String.t()");

        assertThat(container.getAliases()).isEmpty();
        assertThat(container.getUses()).isEmpty();
        assertThat(container.getImports()).isEmpty();
    }

    @Test
    void nonExDefinitionFileAddsNothing() {
        ElixirImportContainer container = new ElixirImportContainer();
        Symbol symbol = Symbol.builder()
                .name("some_module")
                .definitionFile("src/generated/some_module.erl")
                .build();

        container.importSymbol(symbol, "some_module");

        assertThat(container.getAliases()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Manual addAlias / addUse / addImport
    // -------------------------------------------------------------------------

    @Test
    void addAliasAppearsInAliases() {
        ElixirImportContainer container = new ElixirImportContainer();
        container.addAlias("Foo.Bar");
        assertThat(container.getAliases()).contains("Foo.Bar");
    }

    @Test
    void addUseAppearsInUses() {
        ElixirImportContainer container = new ElixirImportContainer();
        container.addUse("Foo.Behaviour");
        assertThat(container.getUses()).contains("Foo.Behaviour");
    }

    @Test
    void addImportAppearsInImports() {
        ElixirImportContainer container = new ElixirImportContainer();
        container.addImport("Foo.Helpers");
        assertThat(container.getImports()).contains("Foo.Helpers");
    }

    @Test
    void aliasesPreserveInsertionOrder() {
        ElixirImportContainer container = new ElixirImportContainer();
        container.addAlias("Alpha");
        container.addAlias("Beta");
        container.addAlias("Gamma");

        assertThat(container.getAliases()).containsExactly("Alpha", "Beta", "Gamma");
    }

    @Test
    void duplicateAliasIsDeduped() {
        ElixirImportContainer container = new ElixirImportContainer();
        container.addAlias("Foo.Bar");
        container.addAlias("Foo.Bar");

        assertThat(container.getAliases()).hasSize(1);
    }

    @Test
    void duplicateUseIsDeduped() {
        ElixirImportContainer container = new ElixirImportContainer();
        container.addUse("Foo.Behaviour");
        container.addUse("Foo.Behaviour");

        assertThat(container.getUses()).hasSize(1);
    }

    // -------------------------------------------------------------------------
    // writeImports
    // -------------------------------------------------------------------------

    @Test
    void writeImportsEmitsAliasLines() {
        ElixirImportContainer container = new ElixirImportContainer();
        container.addAlias("Foo.Bar");
        container.addAlias("Foo.Baz");

        ElixirWriter writer = new ElixirWriter("test.ex");
        container.writeImports(writer);

        String output = writer.toString();
        assertThat(output).contains("alias Foo.Bar");
        assertThat(output).contains("alias Foo.Baz");
    }

    @Test
    void writeImportsEmitsUseLines() {
        ElixirImportContainer container = new ElixirImportContainer();
        container.addUse("GenServer");

        ElixirWriter writer = new ElixirWriter("test.ex");
        container.writeImports(writer);

        assertThat(writer.toString()).contains("use GenServer");
    }

    @Test
    void writeImportsEmitsImportLines() {
        ElixirImportContainer container = new ElixirImportContainer();
        container.addImport("Foo.Helpers");

        ElixirWriter writer = new ElixirWriter("test.ex");
        container.writeImports(writer);

        assertThat(writer.toString()).contains("import Foo.Helpers");
    }

    @Test
    void writeImportsIsNoOpWhenEmpty() {
        ElixirImportContainer container = new ElixirImportContainer();
        ElixirWriter writer = new ElixirWriter("test.ex");

        container.writeImports(writer);

        assertThat(writer.toString()).doesNotContain("alias");
        assertThat(writer.toString()).doesNotContain("use");
        assertThat(writer.toString()).doesNotContain("import");
    }
}
