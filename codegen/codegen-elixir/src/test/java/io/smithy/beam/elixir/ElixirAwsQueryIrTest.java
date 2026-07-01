package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExFunction;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

class ElixirAwsQueryIrTest {
  @Test
  void queryHelpersAwsAsStringMatchGolden() throws IOException {
    assertGolden(
        ElixirAwsQueryHelperIr.queryHelperFunctions(false),
        "ir/aws_query_flatten_member.expected.ex");
  }

  @Test
  void serverQueryDecodeHelpersAwsAsStringMatchGolden() throws IOException {
    assertGolden(
        ElixirAwsQueryHelperIr.serverDecodeHelpers(false),
        "ir/aws_query_form_decode_aws.expected.ex");
  }

  @Test
  void queryHelpersAwsAreStructural() {
    for (ExFunction fn : ElixirAwsQueryHelperIr.queryHelperFunctions(false)) {
      ElixirIrTestSupport.assertStructural(fn);
    }
    String text = helpersAsString(ElixirAwsQueryHelperIr.queryHelperFunctions(false));
    assertThat(text).contains("when is_list(value) do");
    assertThat(text).contains("when is_struct(value) do");
    assertThat(text).contains("when is_map(value) do");
  }

  @Test
  void serverQueryDecodeHelpersAreStructural() {
    for (ExFunction fn : ElixirAwsQueryHelperIr.serverDecodeHelpers(false)) {
      ElixirIrTestSupport.assertStructural(fn);
    }
  }

  @Test
  void xmlHelperFunctionsIncludeCollectText() {
    String text =
        ElixirAwsQueryHelperIr.xmlHelperFunctions(false).stream()
            .map(ExFunction::asString)
            .collect(Collectors.joining("\n\n"));
    assertThat(text).contains("defp collect_text(");
    assertThat(text).contains("defp element_text(");
  }

  @Test
  void flattenStructureUsesWirePrefixForStructuresWithKeyField() {
    Model model = tagFlattenModel();
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.test.tagflatten#QueryService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    ElixirSymbolProvider provider =
        new ElixirSymbolProvider(
            settings,
            model,
            service,
            layout.typesModuleFile(),
            ElixirSymbolProvider.toModuleName(layout.typesModuleName()),
            BeamCodegenKind.CLIENT);
    StructureShape tag =
        model.expectShape(ShapeId.from("smithy.beam.test.tagflatten#Tag"), StructureShape.class);

    ExFunction fn = ElixirAwsQueryOperationIr.buildFlattenStructure(provider, Set.of(tag), true);
    ElixirIrTestSupport.assertStructural(fn);

    String text = fn.asString();
    assertThat(text)
        .contains("defp flatten_structure(wire_prefix, %Types.Tag{key: key, value: value})");
    assertThat(text).contains("flatten_member(wire_prefix <> \".\" <> \"Key\", key)");
    assertThat(text).doesNotContain("defp flatten_structure(key, %Types.Tag{key: key");
  }

  private static Model tagFlattenModel() {
    String idl =
        """
                $version: "2"
                namespace smithy.beam.test.tagflatten

                use aws.protocols#ec2Query
                use aws.protocols#ec2QueryName
                use aws.api#service
                use smithy.api#http
                use smithy.api#xmlNamespace

                @ec2Query
                @xmlNamespace(uri: "https://tagflattentest.amazonaws.com/doc/2020-01-01/")
                @service(sdkId: "TagFlattenTest", endpointPrefix: "tagflattentest")
                service QueryService {
                    version: "2020-01-01"
                    operations: [RunInstances]
                }

                @http(method: "POST", uri: "/")
                operation RunInstances {
                    input: RunInstancesInput
                }

                structure RunInstancesInput {
                    @ec2QueryName("TagSpecification")
                    tagSpecifications: TagSpecificationList
                }

                list TagSpecificationList {
                    member: TagSpecification
                }

                structure TagSpecification {
                    @ec2QueryName("ResourceType")
                    resourceType: String
                    @ec2QueryName("Tag")
                    tags: TagList
                }

                list TagList {
                    member: Tag
                }

                structure Tag {
                    key: String
                    value: String
                }
                """;
    return Model.assembler()
        .addUnparsedModel("tag_flatten_fixture.smithy", idl)
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static void assertGolden(List<ExFunction> functions, String resourcePath)
      throws IOException {
    assertThat(helpersAsString(functions)).isEqualTo(readExpectedString(resourcePath));
    for (ExFunction fn : functions) {
      ElixirIrTestSupport.assertStructural(fn);
    }
  }

  private static String helpersAsString(List<ExFunction> functions) {
    return functions.stream().map(ExFunction::asString).collect(Collectors.joining("\n\n"));
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirAwsQueryIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
