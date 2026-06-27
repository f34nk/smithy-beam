package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSettings;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;

class ElixirDirectedCodegenTest {

  private static final String SERVICE_ID = "com.variantorder#VariantOrderService";

  private static Model model;
  private static String typesFile;

  @BeforeAll
  static void setup() {
    String idl =
        """
                $version: "2"
                namespace com.variantorder

                service VariantOrderService {
                    operations: [GetVariants]
                }

                @readonly
                operation GetVariants {
                    output: VariantBundle
                }

                structure VariantBundle {
                    status: OrderStatus
                    priority: OrderPriority
                    narrow: NarrowUnion
                    wide: WideUnion
                }

                enum OrderStatus {
                    ALPHA
                    BETA
                }

                intEnum OrderPriority {
                    LOW = 1
                    HIGH = 2
                }

                union NarrowUnion {
                    only: OrderString
                }

                union WideUnion {
                    a: OrderString
                    b: OrderInteger
                    c: OrderBoolean
                }

                string OrderString
                integer OrderInteger
                boolean OrderBoolean
                """;

    model = Model.assembler().addUnparsedModel("variant_order.smithy", idl).assemble().unwrap();

    ServiceShape service = model.expectShape(ShapeId.from(SERVICE_ID), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    typesFile = layout.typesModuleFile();
  }

  private static String generateTypes() {
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", SERVICE_ID)
            .withMember("edition", "2026")
            .build();
    PluginContext context =
        PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build();
    new ElixirTypeGeneration().generate(context);
    return manifest.expectFileString(typesFile);
  }

  private static String enumTypeLine(String content, String nestedModule) {
    String marker = "defmodule " + nestedModule + " do";
    int start = content.indexOf(marker);
    assertThat(start).as("module %s", nestedModule).isGreaterThan(-1);
    int typeStart = content.indexOf("@type t ::", start);
    assertThat(typeStart).as("@type t for %s", nestedModule).isGreaterThan(-1);
    int lineEnd = content.indexOf('\n', typeStart);
    return content.substring(typeStart, lineEnd == -1 ? content.length() : lineEnd);
  }

  private static String unknownUnionVariantLine(String content, String typeName) {
    int start = content.indexOf("@type " + typeName + " ::");
    assertThat(start).as("union @type for %s", typeName).isGreaterThan(-1);
    int unknownPos = content.indexOf("{:unknown, String.t()}", start);
    assertThat(unknownPos).as("unknown variant for %s", typeName).isGreaterThan(-1);
    int lineStart = content.lastIndexOf('\n', unknownPos) + 1;
    int lineEnd = content.indexOf('\n', unknownPos);
    return content.substring(lineStart, lineEnd == -1 ? content.length() : lineEnd);
  }

  @Test
  void enumUnknownVariantIsLastOnTypeLine() {
    String line = enumTypeLine(generateTypes(), "OrderStatus");
    assertThat(line.strip()).endsWith("{:unknown, String.t()}");
    assertThat(line).contains(":alpha").contains(":beta");
    assertThat(line.indexOf(":beta")).isLessThan(line.indexOf("{:unknown"));
  }

  @Test
  void intEnumUnknownVariantIsLastOnTypeLine() {
    String line = enumTypeLine(generateTypes(), "OrderPriority");
    assertThat(line.strip()).endsWith("{:unknown, integer()}");
    assertThat(line).contains(":low").contains(":high");
    assertThat(line.indexOf(":high")).isLessThan(line.indexOf("{:unknown"));
  }

  @Test
  void narrowUnionUnknownVariantIsLast() {
    String line = unknownUnionVariantLine(generateTypes(), "narrow_union");
    assertThat(line.strip()).endsWith("{:unknown, String.t()}");
  }

  @Test
  void wideUnionUnknownVariantIsLastVariantBlock() {
    String content = generateTypes();
    String line = unknownUnionVariantLine(content, "wide_union");
    assertThat(line.strip()).endsWith("{:unknown, String.t()}");
    int wideStart = content.indexOf("@type wide_union ::");
    int unknownPos = content.indexOf("{:unknown, String.t()}", wideStart);
    int aPos = content.indexOf("{:a,", wideStart);
    int bPos = content.indexOf("{:b,", wideStart);
    int cPos = content.indexOf("{:c,", wideStart);
    assertThat(aPos).isGreaterThan(-1);
    assertThat(bPos).isGreaterThan(aPos);
    assertThat(cPos).isGreaterThan(bPos);
    assertThat(unknownPos).isGreaterThan(cPos);
  }

  @Test
  void preambleAliasExpectationsMatchWalkerClosureScalarsAndAggregates() {
    Model preambleModel =
        Model.assembler()
            .addUnparsedModel(
                "preamble_audit.smithy",
                """
                        $version: "2"
                        namespace com.preambleaudit

                        use smithy.api#default
                        use smithy.api#streaming

                        service PreambleAuditService {
                            operations: [GetPreambleBundle]
                        }

                        @readonly
                        operation GetPreambleBundle {
                            output: PreambleBundle
                        }

                        structure PreambleBundle {
                            @default("")
                            payload: PaStreamingBlob
                            body: PaBlob
                            at: PaTimestamp
                            doc: PaDocument
                            tags: PaStringList
                            attrs: PaStringMap
                            status: PaStatus
                        }

                        @streaming
                        blob PaStreamingBlob
                        blob PaBlob
                        timestamp PaTimestamp
                        document PaDocument
                        string PaString

                        list PaStringList {
                            member: PaString
                        }

                        map PaStringMap {
                            key: PaString
                            value: PaString
                        }

                        enum PaStatus {
                            ON
                            OFF
                        }
                        """)
            .assemble()
            .unwrap();

    ServiceShape service =
        preambleModel.expectShape(
            ShapeId.from("com.preambleaudit#PreambleAuditService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    String typesModule = layout.typesModuleFile();
    String typesModuleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    SymbolProvider symbolProvider =
        new ElixirSymbolProvider(
            settings, preambleModel, service, typesModule, typesModuleName, BeamCodegenKind.TYPES);
    Set<Shape> closure = new Walker(preambleModel).walkShapes(service);
    Set<ShapeId> expected =
        ElixirDirectedCodegen.expectedPreambleAliasShapeIds(closure, symbolProvider);

    assertThat(expected)
        .contains(
            ShapeId.from("com.preambleaudit#PaStreamingBlob"),
            ShapeId.from("com.preambleaudit#PaBlob"),
            ShapeId.from("com.preambleaudit#PaTimestamp"),
            ShapeId.from("com.preambleaudit#PaDocument"),
            ShapeId.from("com.preambleaudit#PaString"),
            ShapeId.from("com.preambleaudit#PaStringList"),
            ShapeId.from("com.preambleaudit#PaStringMap"))
        .doesNotContain(
            ShapeId.from("com.preambleaudit#PaStatus"),
            ShapeId.from("com.preambleaudit#PreambleBundle"),
            ShapeId.from("com.preambleaudit#GetPreambleBundle"),
            ShapeId.from("com.preambleaudit#PreambleAuditService"));
  }

  @Test
  void eachClosureScalarAndAggregateGetsExactlyOnePreambleAliasLine() {
    Model preambleModel =
        Model.assembler()
            .addUnparsedModel(
                "preamble_audit_emit.smithy",
                """
                        $version: "2"
                        namespace com.preambleemit

                        use smithy.api#default
                        use smithy.api#streaming

                        service PreambleEmitService {
                            operations: [GetEmitBundle]
                        }

                        @readonly
                        operation GetEmitBundle {
                            output: EmitBundle
                        }

                        structure EmitBundle {
                            @default("")
                            payload: PeStreamingBlob
                            body: PeBlob
                            at: PeTimestamp
                            doc: PeDocument
                            tags: PeStringList
                            attrs: PeStringMap
                        }

                        @streaming
                        blob PeStreamingBlob
                        blob PeBlob
                        timestamp PeTimestamp
                        document PeDocument
                        string PeString

                        list PeStringList {
                            member: PeString
                        }

                        map PeStringMap {
                            key: PeString
                            value: PeString
                        }
                        """)
            .assemble()
            .unwrap();

    ServiceShape service =
        preambleModel.expectShape(
            ShapeId.from("com.preambleemit#PreambleEmitService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    String typesModule = layout.typesModuleFile();
    String typesModuleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    SymbolProvider symbolProvider =
        new ElixirSymbolProvider(
            settings, preambleModel, service, typesModule, typesModuleName, BeamCodegenKind.TYPES);

    MockManifest manifest = new MockManifest();
    ObjectNode pluginSettings =
        ObjectNode.builder()
            .withMember("service", "com.preambleemit#PreambleEmitService")
            .withMember("edition", "2026")
            .build();
    new ElixirTypeGeneration()
        .generate(
            PluginContext.builder()
                .model(preambleModel)
                .fileManifest(manifest)
                .settings(pluginSettings)
                .build());

    String content = manifest.expectFileString(typesModule);
    Set<Shape> closure = new Walker(preambleModel).walkShapes(service);
    Set<ShapeId> expected =
        ElixirDirectedCodegen.expectedPreambleAliasShapeIds(closure, symbolProvider);

    for (ShapeId shapeId : expected) {
      Shape shape = preambleModel.expectShape(shapeId, Shape.class);
      Symbol symbol = symbolProvider.toSymbol(shape);
      String marker = "@type " + symbol.getName() + " ::";
      assertThat(countOccurrences(content, marker))
          .as("preamble alias for %s", shapeId)
          .isEqualTo(1);
    }

    assertThat(content)
        .contains("# Streaming payload; framing deferred to protocol layer.")
        .contains("@type pe_streaming_blob :: binary()")
        .contains("@type pe_timestamp :: DateTime.t()")
        .contains("@type pe_document :: any()")
        .contains("@type pe_string_list :: [")
        .contains(".pe_string()]")
        .contains("@type pe_string_map :: %{")
        .contains(".pe_string() => ")
        .contains(".pe_string()}");
  }

  @Test
  void omitsRedundantPrimitiveNamedScalarAliases() {
    Model primitiveModel =
        Model.assembler()
            .addUnparsedModel(
                "primitive_aliases.smithy",
                """
                        $version: "2"
                        namespace com.primitivealiases

                        service PrimitiveAliasService {
                            operations: [GetPrimitiveBundle]
                        }

                        @readonly
                        operation GetPrimitiveBundle {
                            output: PrimitiveBundle
                        }

                        structure PrimitiveBundle {
                            f: Float
                            i: Integer
                            b: Boolean
                            d: Double
                            s: String
                        }

                        float Float
                        integer Integer
                        boolean Boolean
                        double Double
                        string String
                        """)
            .assemble()
            .unwrap();

    ServiceShape service =
        primitiveModel.expectShape(
            ShapeId.from("com.primitivealiases#PrimitiveAliasService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    String typesModule = layout.typesModuleFile();
    String typesModuleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    SymbolProvider symbolProvider =
        new ElixirSymbolProvider(
            settings, primitiveModel, service, typesModule, typesModuleName, BeamCodegenKind.TYPES);

    assertThat(
            symbolProvider.toSymbol(
                primitiveModel.expectShape(ShapeId.from("com.primitivealiases#Float"))))
        .satisfies(
            sym -> {
              assertThat(sym.getName()).isEqualTo("float()");
              assertThat(sym.getProperty("builtIn", Boolean.class)).contains(true);
            });

    MockManifest manifest = new MockManifest();
    ObjectNode pluginSettings =
        ObjectNode.builder()
            .withMember("service", "com.primitivealiases#PrimitiveAliasService")
            .withMember("edition", "2026")
            .build();
    new ElixirTypeGeneration()
        .generate(
            PluginContext.builder()
                .model(primitiveModel)
                .fileManifest(manifest)
                .settings(pluginSettings)
                .build());

    String content = manifest.expectFileString(typesModule);
    assertThat(content).doesNotContain("@type float ::");
    assertThat(content).doesNotContain("@type integer ::");
    assertThat(content).doesNotContain("@type boolean ::");
    assertThat(content).contains("@type double :: float()");
    assertThat(content).contains("@type string :: String.t()");
    assertThat(content).contains("f: float()");
    assertThat(content).contains("i: integer()");
    assertThat(content).contains("b: boolean()");
  }

  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    int index = 0;
    while ((index = haystack.indexOf(needle, index)) != -1) {
      count++;
      index += needle.length();
    }
    return count;
  }
}
