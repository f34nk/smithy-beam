package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.elixir.ElixirTypeGeneration;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ElixirTypesDefstructSplitEmissionTest {

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
    settings.name("variant_order");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    typesFile = layout.typesModuleFile();
  }

  private static void generateWithThreshold(MockManifest manifest, int threshold) {
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", SERVICE_ID)
            .withMember("edition", "2026")
            .withMember("name", "variant_order")
            .withMember("typesDefstructSplitThreshold", threshold)
            .build();
    PluginContext context =
        PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build();
    new ElixirTypeGeneration().generate(context);
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

  private static List<String> getFilePaths(MockManifest manifest) {
    return manifest.getFiles().stream()
        .map(Path::toString)
        .map(path -> path.startsWith("/") ? path.substring(1) : path)
        .sorted()
        .toList();
  }

  @Test
  void monolithicWhenThresholdUnset() {
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", SERVICE_ID)
            .withMember("edition", "2026")
            .withMember("name", "variant_order")
            .build();
    PluginContext context =
        PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build();
    new ElixirTypeGeneration().generate(context);

    assertThat(getFilePaths(manifest)).containsExactly(typesFile);
    String root = manifest.expectFileString(typesFile);
    assertThat(root).contains("defmodule GetVariantsOutput do");
  }

  @Test
  void keepsEnumsAndUnionsInRootFile_whenStructureSplits() {
    MockManifest manifest = new MockManifest();
    generateWithThreshold(manifest, 50);

    String root = manifest.expectFileString(typesFile);
    assertThat(root).contains("defmodule VariantOrderTypes do");
    assertThat(root).contains("@type narrow_union ::");
    assertThat(root).contains("@type wide_union ::");
    assertThat(root).contains("defmodule OrderStatus do");
    assertThat(root).contains("defmodule OrderPriority do");
    assertThat(root).doesNotContain("defmodule GetVariantsOutput do");
  }

  @Test
  void splitsOnlyOversizedStructureModules() {
    MockManifest manifest = new MockManifest();
    generateWithThreshold(manifest, 50);

    assertThat(manifest.getFileString("types/get_variants_output.ex")).isPresent();

    String bundle = manifest.expectFileString("types/get_variants_output.ex");
    assertThat(bundle).contains("defmodule VariantOrderTypes.GetVariantsOutput do");
    assertThat(bundle).contains("@type t :: %__MODULE__{");
    assertThat(bundle).doesNotContain("defmodule GetVariantsOutput do");
  }

  @Test
  void enumUnknownVariantOrderingUnchangedInRootFile() {
    MockManifest manifest = new MockManifest();
    generateWithThreshold(manifest, 50);

    String root = manifest.expectFileString(typesFile);
    String line = enumTypeLine(root, "OrderStatus");
    assertThat(line.strip()).endsWith("{:unknown, String.t()}");
    assertThat(line).contains(":alpha").contains(":beta");
    assertThat(line.indexOf(":beta")).isLessThan(line.indexOf("{:unknown"));
  }
}
