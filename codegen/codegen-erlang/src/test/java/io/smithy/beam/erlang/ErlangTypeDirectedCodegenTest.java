package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.erlang.ErlangRenderer;
import io.beam.ir.erlang.Header;
import io.beam.ir.erlang.HeaderRecordEntry;
import io.beam.ir.erlang.HeaderTypeAliasEntry;
import io.beam.ir.erlang.RecordDef;
import io.beam.ir.erlang.TypeAlias;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamRetryIndex;
import io.smithy.beam.core.BeamSettings;
import java.net.URL;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.NullableIndex;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.ErrorTrait;

class ErlangTypeDirectedCodegenTest {

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
    typesFile =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service).typesHeaderFile();
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
    new ErlangTypeGeneration().generate(context);
    return manifest.expectFileString(typesFile);
  }

  private static String lastLineOfTypeDeclaration(String content, String typeName) {
    String marker = "-type " + typeName + "() ::";
    boolean inBlock = false;
    String last = null;
    for (String line : content.split("\n", -1)) {
      if (line.contains(marker)) {
        inBlock = true;
        last = line;
        if (line.strip().endsWith(".")) {
          return line;
        }
        continue;
      }
      if (inBlock) {
        last = line;
        if (line.strip().endsWith(".")) {
          return line;
        }
      }
    }
    assertThat(last).as("type block for %s", typeName).isNotNull();
    return last;
  }

  private static String singleLineTypeDeclaration(String content, String typeName) {
    String marker = "-type " + typeName + "() ::";
    for (String line : content.split("\n", -1)) {
      if (line.contains(marker)) {
        return line;
      }
    }
    throw new AssertionError("missing type declaration for " + typeName);
  }

  @Test
  void enumUnknownVariantIsLastOnTypeLine() {
    String line = singleLineTypeDeclaration(generateTypes(), "order_status");
    assertThat(line.strip()).endsWith("{unknown, binary()}.");
    assertThat(line).contains("alpha").contains("beta");
    assertThat(line.indexOf("beta")).isLessThan(line.indexOf("{unknown"));
  }

  @Test
  void intEnumUnknownVariantIsLastOnTypeLine() {
    String line = singleLineTypeDeclaration(generateTypes(), "order_priority");
    assertThat(line.strip()).endsWith("{unknown, integer()}.");
    assertThat(line).contains("low").contains("high");
    assertThat(line.indexOf("high")).isLessThan(line.indexOf("{unknown"));
  }

  @Test
  void narrowUnionUnknownVariantIsLast() {
    String line = lastLineOfTypeDeclaration(generateTypes(), "narrow_union");
    assertThat(line.strip()).endsWith("{unknown, binary()}.");
  }

  @Test
  void wideUnionUnknownVariantIsLastVariantBlock() {
    String line = lastLineOfTypeDeclaration(generateTypes(), "wide_union");
    assertThat(line.strip()).endsWith("{unknown, binary()}.");
    String content = generateTypes();
    assertThat(content).contains("-type wide_union() ::");
    assertThat(content).contains("    {a, order_string()}");
    assertThat(content).contains("    | {b, order_integer()}");
    assertThat(content).doesNotContain("{a, order_string()}  |");
    int wideStart = content.indexOf("-type wide_union() ::");
    int unknownPos = content.indexOf("{unknown, binary()}", wideStart);
    int aPos = content.indexOf("{a,", wideStart);
    int bPos = content.indexOf("{b,", wideStart);
    int cPos = content.indexOf("{c,", wideStart);
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
    String typesHeader =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service).typesHeaderFile();
    SymbolProvider symbolProvider =
        new ErlangSymbolProvider(
            settings, preambleModel, service, typesHeader, BeamCodegenKind.TYPES);
    Set<Shape> closure = new Walker(preambleModel).walkShapes(service);
    Set<ShapeId> expected =
        ErlangTypeDirectedCodegen.expectedPreambleAliasShapeIds(closure, symbolProvider);

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
            ShapeId.from("com.preambleaudit#PreambleAuditService"),
            ShapeId.from("smithy.api#String"),
            ShapeId.from("smithy.api#Timestamp"));
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
    String typesHeader =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service).typesHeaderFile();
    SymbolProvider symbolProvider =
        new ErlangSymbolProvider(
            settings, preambleModel, service, typesHeader, BeamCodegenKind.TYPES);

    MockManifest manifest = new MockManifest();
    ObjectNode pluginSettings =
        ObjectNode.builder()
            .withMember("service", "com.preambleemit#PreambleEmitService")
            .withMember("edition", "2026")
            .build();
    new ErlangTypeGeneration()
        .generate(
            PluginContext.builder()
                .model(preambleModel)
                .fileManifest(manifest)
                .settings(pluginSettings)
                .build());

    String content = manifest.expectFileString(typesHeader);
    Set<Shape> closure = new Walker(preambleModel).walkShapes(service);
    Set<ShapeId> expected =
        ErlangTypeDirectedCodegen.expectedPreambleAliasShapeIds(closure, symbolProvider);

    for (ShapeId shapeId : expected) {
      Shape shape = preambleModel.expectShape(shapeId, Shape.class);
      Symbol symbol = symbolProvider.toSymbol(shape);
      String marker = "-type " + symbol.getName() + " ::";
      assertThat(countOccurrences(content, marker))
          .as("preamble alias for %s", shapeId)
          .isEqualTo(1);
    }

    assertThat(content)
        .contains(
            "%% streaming payload; framing deferred to protocol layer",
            "-type pe_streaming_blob() :: binary().")
        .contains("-type pe_timestamp() :: erlang:timestamp().")
        .contains("-type pe_document() :: term().")
        .contains("-type pe_string_list() :: [pe_string()].")
        .contains("-type pe_string_map() :: #{pe_string() => pe_string()}.");
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
    String typesHeader =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service).typesHeaderFile();
    SymbolProvider symbolProvider =
        new ErlangSymbolProvider(
            settings, primitiveModel, service, typesHeader, BeamCodegenKind.TYPES);

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
    new ErlangTypeGeneration()
        .generate(
            PluginContext.builder()
                .model(primitiveModel)
                .fileManifest(manifest)
                .settings(pluginSettings)
                .build());

    String content = manifest.expectFileString(typesHeader);
    assertThat(content).doesNotContain("-type float() ::");
    assertThat(content).doesNotContain("-type integer() ::");
    assertThat(content).doesNotContain("-type boolean() ::");
    assertThat(content).contains("-type double() :: float().");
    assertThat(content).contains("-type string() :: binary().");
    assertThat(content).contains("f :: float()");
    assertThat(content).contains("i :: integer()");
    assertThat(content).contains("b :: boolean()");
  }

  @Test
  void scalarAliasGoldenIncludesBlobAndBigDecimalCommentVariants() {
    Model model =
        Model.assembler()
            .addUnparsedModel(
                "scalar_variants.smithy",
                """
                        $version: "2"
                        namespace com.scalarvariants

                        use smithy.api#default
                        use smithy.api#streaming

                        service ScalarVariantService {
                            operations: [GetScalarBundle]
                        }

                        @readonly
                        operation GetScalarBundle {
                            output: ScalarBundle
                        }

                        structure ScalarBundle {
                            @default("")
                            payload: SvStreamingBlob
                            amount: SvBigDecimal
                        }

                        @streaming
                        blob SvStreamingBlob
                        bigDecimal SvBigDecimal
                        """)
            .assemble()
            .unwrap();

    ServiceShape service =
        model.expectShape(
            ShapeId.from("com.scalarvariants#ScalarVariantService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    String typesHeader =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service).typesHeaderFile();

    MockManifest manifest = new MockManifest();
    ObjectNode pluginSettings =
        ObjectNode.builder()
            .withMember("service", "com.scalarvariants#ScalarVariantService")
            .withMember("edition", "2026")
            .build();
    new ErlangTypeGeneration()
        .generate(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(pluginSettings)
                .build());

    String content = manifest.expectFileString(typesHeader);
    assertThat(content)
        .contains(
            "%% streaming payload; framing deferred to protocol layer",
            "-type sv_streaming_blob() :: binary().")
        .contains("%% decimal:decimal()", "-type sv_big_decimal() :: term().");
  }

  @Test
  void listAndSparseMapAliasGoldenUsesErlTypeDef() {
    Model model =
        Model.assembler()
            .addUnparsedModel(
                "list_map_aliases.smithy",
                """
                        $version: "2"
                        namespace com.listmapaliases

                        use smithy.api#sparse

                        service ListMapAliasService {
                            operations: [GetListMapBundle]
                        }

                        @readonly
                        operation GetListMapBundle {
                            output: ListMapBundle
                        }

                        structure ListMapBundle {
                            tags: LmaStringList
                            attrs: LmaSparseStringMap
                        }

                        string LmaString

                        list LmaStringList {
                            member: LmaString
                        }

                        @sparse
                        map LmaSparseStringMap {
                            key: LmaString
                            value: LmaString
                        }
                        """)
            .assemble()
            .unwrap();

    ServiceShape service =
        model.expectShape(
            ShapeId.from("com.listmapaliases#ListMapAliasService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    String typesHeader =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service).typesHeaderFile();

    MockManifest manifest = new MockManifest();
    ObjectNode pluginSettings =
        ObjectNode.builder()
            .withMember("service", "com.listmapaliases#ListMapAliasService")
            .withMember("edition", "2026")
            .build();
    new ErlangTypeGeneration()
        .generate(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(pluginSettings)
                .build());

    String content = manifest.expectFileString(typesHeader);
    assertThat(content)
        .contains("-type lma_string_list() :: [lma_string()].")
        .contains("-type lma_sparse_string_map() :: #{lma_string() => lma_string() | undefined}.");
  }

  @Test
  void typesHeaderAppendsEndpointRuleSetWhenTraitPresent() {
    URL resource =
        ErlangTypeDirectedCodegenTest.class.getResource("/model/endpoint_rules_minimal.smithy");
    assertThat(resource).isNotNull();
    Model endpointModel =
        Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    ServiceShape service =
        endpointModel.expectShape(
            ShapeId.from("smithy.beam.test.endpoints#EndpointRulesService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    String typesHeader =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service).typesHeaderFile();

    MockManifest manifest = new MockManifest();
    ObjectNode pluginSettings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.test.endpoints#EndpointRulesService")
            .withMember("edition", "2026")
            .build();
    new ErlangTypeGeneration()
        .generate(
            PluginContext.builder()
                .model(endpointModel)
                .fileManifest(manifest)
                .settings(pluginSettings)
                .build());

    String content = manifest.expectFileString(typesHeader);
    assertThat(content)
        .contains("%% @endpointRuleSet embedded at codegen time.")
        .contains("-type endpoint_rule_set() :: map().")
        .contains("-define(ENDPOINT_RULE_SET,")
        .contains("s3.{Region}.amazonaws.com");
  }

  @Test
  void buildErrorRecordGoldenUsesIrNodes() {
    Model model =
        Model.assembler()
            .addUnparsedModel(
                "error_record.smithy",
                """
                        $version: "2"
                        namespace com.errorrecord

                        use smithy.api#error

                        service ErrorRecordService {
                            operations: [Fail]
                            errors: [ErUnavailable]
                        }

                        operation Fail {
                            errors: [ErUnavailable]
                        }

                        @error("server")
                        @retryable
                        structure ErUnavailable {
                            message: ErString
                        }

                        string ErString
                        """)
            .assemble()
            .unwrap();

    ServiceShape service =
        model.expectShape(ShapeId.from("com.errorrecord#ErrorRecordService"), ServiceShape.class);
    StructureShape shape =
        model.expectShape(ShapeId.from("com.errorrecord#ErUnavailable"), StructureShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    String typesHeader =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service).typesHeaderFile();
    SymbolProvider symbolProvider =
        new ErlangSymbolProvider(settings, model, service, typesHeader, BeamCodegenKind.TYPES);
    ErrorTrait errorTrait = shape.expectTrait(ErrorTrait.class);
    BeamRetryIndex.RetryInfo retryInfo = BeamRetryIndex.forError(shape).orElseThrow();

    RecordDef record =
        ErlangTypeDirectedCodegen.buildErrorRecord(
            shape,
            symbolProvider,
            NullableIndex.of(model),
            errorTrait,
            retryInfo.retryable(),
            retryInfo.throttling());
    TypeAlias type = TypeAlias.of("er_unavailable", "#er_unavailable{}");

    assertThat(
            ErlangRenderer.render(Header.ofEntries(List.of(new HeaderRecordEntry(record)), false)))
        .contains("-record(er_unavailable, {")
        .contains("message :: er_string() | undefined,")
        .contains("%% fault: server | retryable: true | throttling: false")
        .contains("'__beam_error_kind' = server :: client | server");
    assertThat(
            ErlangRenderer.render(Header.ofEntries(List.of(new HeaderTypeAliasEntry(type)), false)))
        .isEqualTo("-type er_unavailable() :: #er_unavailable{}.\n");
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
