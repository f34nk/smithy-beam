package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.erlang.ErlangRenderer;
import io.beam.dsl.erlang.Function;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSettings;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

class ErlangAwsQueryIrTest {
  static Model model;
  static ServiceShape service;
  static OperationShape listUsersOp;
  static HttpBindingIndex httpIndex;
  static ErlangSymbolProvider provider;

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
        ErlangAwsQueryDsl.queryHelpers(false), "dsl/aws_query_flatten_helpers_aws.expected.erl");
  }

  @Test
  void queryHelpersEc2AsStringMatchGolden() throws IOException {
    assertGolden(
        ErlangAwsQueryDsl.queryHelpers(true), "dsl/aws_query_flatten_helpers_ec2.expected.erl");
  }

  @Test
  void xmlHelpersAwsAsStringMatchGolden() throws IOException {
    assertGolden(ErlangAwsQueryDsl.xmlHelpers(false), "dsl/aws_query_xml_helpers_aws.expected.erl");
  }

  @Test
  void serverQueryDecodeHelpersAwsAsStringMatchGolden() throws IOException {
    assertGolden(
        ErlangAwsQueryDsl.serverQueryDecodeHelpers(false),
        "dsl/aws_query_form_decode_aws.expected.erl");
  }

  @Test
  void encodeListUsersRequestMatchesGolden() throws IOException {
    Function fn = ErlangAwsQueryDsl.encodeRequest(model, service, listUsersOp, httpIndex, provider);
    assertStructural(fn);
    DslGoldenAssertions.assertGolden(fn, "dsl/aws_query_encode_list_users_request.expected.erl");
  }

  @Test
  void decodeListUsersResponseMatchesGolden() throws IOException {
    Function fn = ErlangAwsQueryDsl.decodeResponse(model, service, listUsersOp, provider, false);
    assertStructural(fn);
    DslGoldenAssertions.assertGolden(fn, "dsl/aws_query_decode_list_users_response.expected.erl");
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
    Function fn =
        ErlangAwsQueryDsl.flattenQueryInput(
            flattenModel,
            HttpBindingIndex.of(flattenModel),
            flattenProvider,
            ErlangAwsQueryDsl.inputShapes(flattenModel, flattenService),
            false);
    assertStructural(fn);
    DslGoldenAssertions.assertGolden(fn, "dsl/aws_query_flatten_query_input.expected.erl");
  }

  @Test
  void serverDecodeListUsersRequestMatchesGolden() throws IOException {
    Function fn = ErlangAwsQueryDsl.serverDecodeRequest(model, listUsersOp, provider, false);
    assertStructural(fn);
    DslGoldenAssertions.assertGolden(
        fn, "dsl/aws_query_server_decode_list_users_request.expected.erl");
  }

  @Test
  void serverEncodeListUsersResponseMatchesGolden() throws IOException {
    Function fn =
        ErlangAwsQueryDsl.serverEncodeResponse(model, service, listUsersOp, provider, false);
    assertStructural(fn);
    DslGoldenAssertions.assertGolden(
        fn, "dsl/aws_query_server_encode_list_users_response.expected.erl");
  }

  @Test
  void parseListUsersInputMatchesGolden() throws IOException {
    StructureShape input = model.expectShape(listUsersOp.getInputShape(), StructureShape.class);
    Function fn = ErlangAwsQueryDsl.parseInputFromForm(model, provider, input, false);
    assertStructural(fn);
    DslGoldenAssertions.assertGolden(fn, "dsl/aws_query_parse_list_users_input.expected.erl");
  }

  @Test
  void queryHelpersAwsContainsMapListAndStructureClauses() {
    for (Function fn : ErlangAwsQueryDsl.queryHelpers(false)) {
      assertStructural(fn);
    }
    String text = DslGoldenAssertions.renderFunctions(ErlangAwsQueryDsl.queryHelpers(false));
    assertThat(text).contains("flatten_member(Key, Value) when is_map(Value) ->");
    assertThat(text).contains("flatten_member(Key, Value) when is_list(Value) ->");
    assertThat(text).contains("flatten_member(Key, Value) when is_tuple(Value) ->");
    assertThat(text).contains("flatten_structure(Key, Value)");
  }

  @Test
  void xmlHelpersAwsContainsListDecodeHelpers() {
    for (Function fn : ErlangAwsQueryDsl.xmlHelpers(false)) {
      assertStructural(fn);
    }
    String text = DslGoldenAssertions.renderFunctions(ErlangAwsQueryDsl.xmlHelpers(false));
    assertThat(text).contains("xml_child_list(Parent, ListName, ItemName) ->");
    assertThat(text).contains("xml_child_struct_list(Parent, ListName, ItemName, DecodeFun) ->");
  }

  @Test
  void serverQueryDecodeHelpersAreStructural() {
    for (Function fn : ErlangAwsQueryDsl.serverQueryDecodeHelpers(false)) {
      assertStructural(fn);
    }
  }

  @Test
  void serverXmlEncodeHelpersAreStructural() {
    for (Function fn : ErlangAwsQueryDsl.serverXmlEncodeHelpers(false)) {
      assertStructural(fn);
    }
    for (Function fn : ErlangAwsQueryDsl.serverXmlEncodeHelpers(true)) {
      assertStructural(fn);
    }
  }

  @Test
  void flattenStructureUsesWirePrefixForStructuresWithKeyField() {
    Model model = tagFlattenModel();
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.test.tagflatten#QueryService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamErlangLayout layout =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service);
    ErlangSymbolProvider provider =
        new ErlangSymbolProvider(
            settings, model, service, layout.clientModuleFile(), BeamCodegenKind.CLIENT);
    StructureShape tag =
        model.expectShape(ShapeId.from("smithy.beam.test.tagflatten#Tag"), StructureShape.class);

    Function fn = ErlangAwsQueryOperationDsl.buildFlattenStructure(provider, Set.of(tag), true);
    assertStructural(fn);

    String text = ErlangRenderer.renderFunction(fn);
    assertThat(text).contains("flatten_structure(WirePrefix, #tag{key = Key, value = Value})");
    assertThat(text).contains("<<WirePrefix/binary, \".Key\">>, Key");
    assertThat(text).doesNotContain("flatten_structure(Key, #tag{key = Key");
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

  static Model flattenModel() {
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

  private static void assertGolden(List<Function> functions, String resourcePath)
      throws IOException {
    DslGoldenAssertions.assertGoldenFunctions(functions, resourcePath);
    for (Function fn : functions) {
      assertStructural(fn);
    }
  }

  private static void assertStructural(Function fn) {
    assertThat(fn.name()).isNotBlank();
    assertThat(fn.clauses()).isNotEmpty();
  }
}
