package io.smithy.beam.gleam;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class GleamTypesAliasEmissionTest {

  @Test
  void emitsScalarListAndMapAliases() {
    Model model =
        Model.assembler()
            .addUnparsedModel(
                "test.smithy",
                """
                $version: "2"
                namespace com.example

                service AliasService {
                    operations: [GetAliases]
                }

                operation GetAliases {
                    input: GetAliasesInput
                    output: GetAliasesOutput
                }

                structure GetAliasesInput {}

                structure GetAliasesOutput {
                    name: NamedString
                    count: NamedInt
                    flag: NamedBool
                    payload: NamedBlob
                    when: NamedTimestamp
                    amount: NamedBigDecimal
                    items: NamedList
                    labels: NamedMap
                    sparseItems: SparseList
                    sparseLabels: SparseMap
                }

                string NamedString
                integer NamedInt
                boolean NamedBool
                blob NamedBlob
                timestamp NamedTimestamp
                bigDecimal NamedBigDecimal

                list NamedList {
                    member: NamedString
                }

                map NamedMap {
                    key: NamedString
                    value: NamedString
                }

                @sparse
                list SparseList {
                    member: NamedString
                }

                @sparse
                map SparseMap {
                    key: NamedString
                    value: NamedString
                }
                """)
            .assemble()
            .unwrap();

    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "com.example#AliasService")
            .withMember("edition", "2026")
            .build();
    PluginContext context =
        PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build();

    new GleamTypesPlugin().execute(context);

    String content = manifest.expectFileString("alias_service_types.gleam");
    assertThat(content)
        .contains("//// Type definitions for the alias_service_types model.")
        .contains("import gleam/time/timestamp")
        .contains("import bigdecimal")
        .contains("import gleam/dict.{type Dict}")
        .contains("import gleam/option.{type Option}")
        .contains("pub type NamedString = String")
        .contains("pub type NamedInt = Int")
        .contains("pub type NamedBool = Bool")
        .contains("pub type NamedBlob = BitArray")
        .contains("pub type NamedTimestamp = timestamp.Timestamp")
        .contains("pub type NamedBigDecimal = bigdecimal.BigDecimal")
        .contains("pub type NamedList = List(NamedString)")
        .contains("pub type NamedMap = Dict(NamedString, NamedString)")
        .contains("pub type SparseList = List(Option(NamedString))")
        .contains("pub type SparseMap = Dict(NamedString, Option(NamedString))");
  }
}
