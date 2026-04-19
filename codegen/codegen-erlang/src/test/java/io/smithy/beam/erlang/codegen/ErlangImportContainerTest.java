package io.smithy.beam.erlang.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.Symbol;

class ErlangImportContainerTest {

    @Test
    void hrlSymbolAddsToIncludeLibs() {
        ErlangImportContainer container = new ErlangImportContainer();
        Symbol symbol = Symbol.builder()
                .name("my_record")
                .definitionFile("src/generated/my_types.hrl")
                .build();

        container.importSymbol(symbol, "my_record");

        assertThat(container.getIncludeLibs()).contains("src/generated/my_types.hrl");
    }

    @Test
    void hrlSymbolDoesNotAddToQualifiedModules() {
        ErlangImportContainer container = new ErlangImportContainer();
        Symbol symbol = Symbol.builder()
                .name("my_record")
                .definitionFile("src/generated/my_types.hrl")
                .build();

        container.importSymbol(symbol, "my_record");

        assertThat(container.getQualifiedModules()).isEmpty();
    }

    @Test
    void erlSymbolAddsToQualifiedModules() {
        ErlangImportContainer container = new ErlangImportContainer();
        Symbol symbol = Symbol.builder()
                .name("my_module")
                .definitionFile("src/generated/my_module.erl")
                .build();

        container.importSymbol(symbol, "my_module");

        assertThat(container.getQualifiedModules()).contains("src/generated/my_module.erl");
    }

    @Test
    void erlSymbolDoesNotAddToIncludeLibs() {
        ErlangImportContainer container = new ErlangImportContainer();
        Symbol symbol = Symbol.builder()
                .name("my_module")
                .definitionFile("src/generated/my_module.erl")
                .build();

        container.importSymbol(symbol, "my_module");

        assertThat(container.getIncludeLibs()).isEmpty();
    }

    @Test
    void symbolWithNoDefinitionFileAddsNothing() {
        ErlangImportContainer container = new ErlangImportContainer();
        Symbol symbol = Symbol.builder().name("binary()").build();

        container.importSymbol(symbol, "binary()");

        assertThat(container.getIncludeLibs()).isEmpty();
        assertThat(container.getQualifiedModules()).isEmpty();
    }

    @Test
    void includeLibsPreservesInsertionOrder() {
        ErlangImportContainer container = new ErlangImportContainer();
        Symbol s1 = Symbol.builder().name("a").definitionFile("alpha.hrl").build();
        Symbol s2 = Symbol.builder().name("b").definitionFile("beta.hrl").build();
        Symbol s3 = Symbol.builder().name("c").definitionFile("gamma.hrl").build();

        container.importSymbol(s1, "a");
        container.importSymbol(s2, "b");
        container.importSymbol(s3, "c");

        assertThat(container.getIncludeLibs()).containsExactly("alpha.hrl", "beta.hrl", "gamma.hrl");
    }

    @Test
    void duplicateHrlSymbolIsDeduped() {
        ErlangImportContainer container = new ErlangImportContainer();
        Symbol symbol = Symbol.builder()
                .name("my_record")
                .definitionFile("src/generated/my_types.hrl")
                .build();

        container.importSymbol(symbol, "my_record");
        container.importSymbol(symbol, "my_record");

        assertThat(container.getIncludeLibs()).hasSize(1);
    }

    @Test
    void writeImportsEmitsIncludeLibLines() {
        ErlangImportContainer container = new ErlangImportContainer();
        Symbol symbol = Symbol.builder()
                .name("my_record")
                .definitionFile("some/path/types.hrl")
                .build();
        container.importSymbol(symbol, "my_record");

        ErlangWriter writer = new ErlangWriter("test.erl");
        container.writeImports(writer);

        assertThat(writer.toString()).contains("-include_lib(\"some/path/types.hrl\").");
    }

    @Test
    void writeImportsIsNoOpWhenEmpty() {
        ErlangImportContainer container = new ErlangImportContainer();
        ErlangWriter writer = new ErlangWriter("test.erl");

        container.writeImports(writer);

        assertThat(writer.toString()).doesNotContain("-include_lib");
    }
}
