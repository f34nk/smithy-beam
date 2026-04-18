package io.smithy.beam.erlang.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.Symbol;

class ErlangImportContainerTest {

    @Test
    void importSymbol_hrlDefinitionFile_addsIncludeLib() {
        ErlangImportContainer container = new ErlangImportContainer();
        Symbol symbol = Symbol.builder()
                .name("my_record")
                .definitionFile("src/generated/my_module_types.hrl")
                .build();

        container.importSymbol(symbol, "");

        assertThat(container.getIncludeLibs())
                .contains("src/generated/my_module_types.hrl");
    }

    @Test
    void importSymbol_nonHrlDefinitionFile_doesNotAddIncludeLib() {
        ErlangImportContainer container = new ErlangImportContainer();
        Symbol symbol = Symbol.builder()
                .name("my_module")
                .definitionFile("src/generated/my_module_client.erl")
                .build();

        container.importSymbol(symbol, "");

        assertThat(container.getIncludeLibs()).isEmpty();
    }

    @Test
    void importSymbol_withNamespace_addsQualifiedModule() {
        ErlangImportContainer container = new ErlangImportContainer();
        Symbol symbol = Symbol.builder()
                .name("encode")
                .namespace("smithy_json", ":")
                .build();

        container.importSymbol(symbol, "");

        assertThat(container.getQualifiedModules()).contains("smithy_json");
    }

    @Test
    void importSymbol_withoutNamespace_doesNotAddQualifiedModule() {
        ErlangImportContainer container = new ErlangImportContainer();
        Symbol symbol = Symbol.builder()
                .name("binary()")
                .build();

        container.importSymbol(symbol, "");

        assertThat(container.getQualifiedModules()).isEmpty();
    }

    @Test
    void importSymbol_multipleHrlFiles_accumulates() {
        ErlangImportContainer container = new ErlangImportContainer();

        container.importSymbol(Symbol.builder().name("a").definitionFile("a_types.hrl").build(), "");
        container.importSymbol(Symbol.builder().name("b").definitionFile("b_types.hrl").build(), "");

        assertThat(container.getIncludeLibs()).hasSize(2)
                .contains("a_types.hrl", "b_types.hrl");
    }

    @Test
    void importSymbol_duplicateHrlFile_deduplicates() {
        ErlangImportContainer container = new ErlangImportContainer();
        Symbol symbol = Symbol.builder()
                .name("rec")
                .definitionFile("types.hrl")
                .build();

        container.importSymbol(symbol, "");
        container.importSymbol(symbol, "");

        assertThat(container.getIncludeLibs()).hasSize(1);
    }

    @Test
    void toString_withIncludeLibs_emitsIncludeLibLines() {
        ErlangImportContainer container = new ErlangImportContainer();
        container.importSymbol(
                Symbol.builder().name("r").definitionFile("types.hrl").build(), "");

        assertThat(container.toString()).contains("-include_lib(\"types.hrl\").");
    }

    @Test
    void toString_withNoImports_returnsEmpty() {
        ErlangImportContainer container = new ErlangImportContainer();
        assertThat(container.toString()).isEmpty();
    }

    @Test
    void isQualifiedModule_returnsTrueAfterImport() {
        ErlangImportContainer container = new ErlangImportContainer();
        container.importSymbol(
                Symbol.builder().name("encode").namespace("smithy_xml", ":").build(), "");

        assertThat(container.isQualifiedModule("smithy_xml")).isTrue();
        assertThat(container.isQualifiedModule("smithy_json")).isFalse();
    }
}
