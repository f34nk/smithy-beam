package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.erlang.ErlFunction;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

class ErlangAwsQueryIrTest {
  private static Model model;
  private static ServiceShape service;
  private static OperationShape listUsersOp;
  private static HttpBindingIndex httpIndex;
  private static ErlangSymbolProvider provider;

  @BeforeAll
  static void setup() {
    model = loadFixtureModel();
    service =
        model.expectShape(
            ShapeId.from("smithy.beam.test.awsquery#QueryService"), ServiceShape.class);
    listUsersOp =
        model.expectShape(
            ShapeId.from("smithy.beam.test.awsquery#ListUsers"), OperationShape.class);
    httpIndex = HttpBindingIndex.of(model);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamErlangLayout layout =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service);
    provider =
        new ErlangSymbolProvider(
            settings, model, service, layout.clientModuleFile(), BeamCodegenKind.CLIENT);
  }

  @Test
  void queryHelpersAwsAsStringMatchGolden() throws IOException {
    assertGolden(
        ErlangAwsQueryIr.queryHelpers(false), "ir/aws_query_flatten_helpers_aws.expected.erl");
  }

  @Test
  void queryHelpersEc2AsStringMatchGolden() throws IOException {
    assertGolden(
        ErlangAwsQueryIr.queryHelpers(true), "ir/aws_query_flatten_helpers_ec2.expected.erl");
  }

  @Test
  void xmlHelpersAwsAsStringMatchGolden() throws IOException {
    assertGolden(ErlangAwsQueryIr.xmlHelpers(false), "ir/aws_query_xml_helpers_aws.expected.erl");
  }

  @Test
  void serverQueryDecodeHelpersAwsAsStringMatchGolden() throws IOException {
    assertGolden(
        ErlangAwsQueryIr.serverQueryDecodeHelpers(false),
        "ir/aws_query_form_decode_aws.expected.erl");
  }

  @Test
  void encodeListUsersRequestMatchesGolden() throws IOException {
    ErlFunction fn =
        ErlangAwsQueryIr.encodeRequest(model, service, listUsersOp, httpIndex, provider);
    assertStructural(fn);
    assertThat(fn.asString())
        .isEqualTo(readExpectedString("ir/aws_query_encode_list_users_request.expected.erl"));
  }

  @Test
  void decodeListUsersResponseMatchesGolden() throws IOException {
    ErlFunction fn = ErlangAwsQueryIr.decodeResponse(model, service, listUsersOp, provider, false);
    assertStructural(fn);
    assertThat(fn.asString())
        .isEqualTo(readExpectedString("ir/aws_query_decode_list_users_response.expected.erl"));
  }

  @Test
  void flattenQueryInputMatchesGolden() throws IOException {
    Model flattenModel = flattenModel();
    ServiceShape flattenService =
        flattenModel.expectShape(
            ShapeId.from("smithy.beam.test.awsqueryflatten#QueryService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamErlangLayout layout =
        new BeamErlangLayout(settings, flattenService.getId().getNamespace(), flattenService);
    ErlangSymbolProvider flattenProvider =
        new ErlangSymbolProvider(
            settings,
            flattenModel,
            flattenService,
            layout.clientModuleFile(),
            BeamCodegenKind.CLIENT);
    ErlFunction fn =
        ErlangAwsQueryIr.flattenQueryInput(
            flattenModel,
            HttpBindingIndex.of(flattenModel),
            flattenProvider,
            ErlangAwsQueryIr.inputShapes(flattenModel, flattenService),
            false);
    assertStructural(fn);
    assertThat(fn.asString())
        .isEqualTo(readExpectedString("ir/aws_query_flatten_query_input.expected.erl"));
  }

  @Test
  void serverDecodeListUsersRequestMatchesGolden() throws IOException {
    ErlFunction fn = ErlangAwsQueryIr.serverDecodeRequest(model, listUsersOp, provider, false);
    assertStructural(fn);
    assertThat(fn.asString())
        .isEqualTo(
            readExpectedString("ir/aws_query_server_decode_list_users_request.expected.erl"));
  }

  @Test
  void serverEncodeListUsersResponseMatchesGolden() throws IOException {
    ErlFunction fn =
        ErlangAwsQueryIr.serverEncodeResponse(model, service, listUsersOp, provider, false);
    assertStructural(fn);
    assertThat(fn.asString())
        .isEqualTo(
            readExpectedString("ir/aws_query_server_encode_list_users_response.expected.erl"));
  }

  @Test
  void parseListUsersInputMatchesGolden() throws IOException {
    StructureShape input = model.expectShape(listUsersOp.getInputShape(), StructureShape.class);
    ErlFunction fn = ErlangAwsQueryIr.parseInputFromForm(model, provider, input, false);
    assertStructural(fn);
    assertThat(fn.asString())
        .isEqualTo(readExpectedString("ir/aws_query_parse_list_users_input.expected.erl"));
  }

  @Test
  void queryHelpersAwsContainsMapListAndStructureClauses() {
    for (ErlFunction fn : ErlangAwsQueryIr.queryHelpers(false)) {
      assertStructural(fn);
    }
    String text = helpersAsString(ErlangAwsQueryIr.queryHelpers(false));
    assertThat(text).contains("flatten_member(Key, Value) when is_map(Value) ->");
    assertThat(text).contains("flatten_member(Key, Value) when is_list(Value) ->");
    assertThat(text).contains("flatten_member(Key, Value) when is_tuple(Value) ->");
    assertThat(text).contains("flatten_structure(_Key, _Value) ->");
  }

  @Test
  void xmlHelpersAwsContainsListDecodeHelpers() {
    for (ErlFunction fn : ErlangAwsQueryIr.xmlHelpers(false)) {
      assertStructural(fn);
    }
    String text = helpersAsString(ErlangAwsQueryIr.xmlHelpers(false));
    assertThat(text).contains("xml_child_list(Parent, ListName, ItemName) ->");
    assertThat(text).contains("xml_child_struct_list(Parent, ListName, ItemName, DecodeFun) ->");
  }

  @Test
  void serverQueryDecodeHelpersAreStructural() {
    for (ErlFunction fn : ErlangAwsQueryIr.serverQueryDecodeHelpers(false)) {
      assertStructural(fn);
    }
  }

  @Test
  void serverXmlEncodeHelpersAreStructural() {
    for (ErlFunction fn : ErlangAwsQueryIr.serverXmlEncodeHelpers(false)) {
      assertStructural(fn);
    }
    for (ErlFunction fn : ErlangAwsQueryIr.serverXmlEncodeHelpers(true)) {
      assertStructural(fn);
    }
  }

  private static Model loadFixtureModel() {
    String idl =
        """
                $version: "2"
                namespace smithy.beam.test.awsquery

                use aws.protocols#awsQuery
                use aws.api#service
                use smithy.api#http
                use smithy.api#xmlNamespace

                @awsQuery
                @xmlNamespace(uri: "https://querytest.amazonaws.com/doc/2010-05-08/")
                @service(sdkId: "QueryTest", endpointPrefix: "querytest")
                service QueryService {
                    version: "2010-05-08"
                    operations: [ListUsers]
                }

                @http(method: "POST", uri: "/")
                operation ListUsers {
                    input: ListUsersInput
                    output: ListUsersOutput
                }

                structure ListUsersInput {
                    pathPrefix: String
                }

                structure ListUsersOutput {
                    users: UserNameList
                }

                list UserNameList {
                    member: String
                }
                """;
    return Model.assembler()
        .addUnparsedModel("aws_query_fixture.smithy", idl)
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static Model flattenModel() {
    String idl =
        """
                $version: "2"
                namespace smithy.beam.test.awsqueryflatten

                use aws.protocols#awsQuery
                use aws.api#service
                use smithy.api#http
                use smithy.api#xmlNamespace

                @awsQuery
                @xmlNamespace(uri: "https://queryflattentest.amazonaws.com/doc/2010-05-08/")
                @service(sdkId: "QueryFlattenTest", endpointPrefix: "queryflattentest")
                service QueryService {
                    version: "2010-05-08"
                    operations: [ListUsers, DeleteUser]
                }

                @http(method: "POST", uri: "/")
                operation ListUsers {
                    input: ListUsersInput
                    output: ListUsersOutput
                }

                @http(method: "POST", uri: "/")
                operation DeleteUser {
                    input: DeleteUserInput
                    output: DeleteUserOutput
                }

                structure ListUsersInput {
                    pathPrefix: String
                }

                structure DeleteUserInput {
                    userName: String
                }

                structure ListUsersOutput {
                    users: UserNameList
                }

                structure DeleteUserOutput {}

                list UserNameList {
                    member: String
                }
                """;
    return Model.assembler()
        .addUnparsedModel("flatten.smithy", idl)
        .discoverModels()
        .assemble()
        .getResult()
        .orElseThrow();
  }

  private static void assertGolden(List<ErlFunction> functions, String resourcePath)
      throws IOException {
    assertThat(helpersAsString(functions)).isEqualTo(readExpectedString(resourcePath));
    for (ErlFunction fn : functions) {
      assertStructural(fn);
    }
  }

  private static String helpersAsString(List<ErlFunction> functions) {
    return functions.stream().map(ErlFunction::asString).collect(Collectors.joining("\n\n"));
  }

  private static void assertStructural(ErlFunction fn) {
    assertThat(fn.name()).isNotBlank();
    assertThat(fn.clauses()).isNotEmpty();
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ErlangAwsQueryIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
