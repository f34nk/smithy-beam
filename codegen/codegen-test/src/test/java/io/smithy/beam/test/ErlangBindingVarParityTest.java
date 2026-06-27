package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class ErlangBindingVarParityTest {

  private static final String REST_XML_MODEL =
      """
            $version: "2"
            namespace smithy.beam.test.bindingvar.restxml

            use aws.protocols#restXml

            @restXml
            service BindingVarRestXml {
                version: "2026"
                operations: [CreateResource]
            }

            @http(method: "POST", uri: "/resources")
            operation CreateResource {
                input: CreateResourceInput
                output: CreateResourceOutput
            }

            structure CreateResourceInput {
                @httpHeader("X-Client-Token")
                clientToken: String
            }

            structure CreateResourceOutput {}
            """;

  private static final String AWS_QUERY_MODEL =
      """
            $version: "2"
            namespace smithy.beam.test.bindingvar.awsquery

            use aws.protocols#awsQuery
            use aws.api#service
            use smithy.api#xmlNamespace

            @awsQuery
            @xmlNamespace(uri: "https://bindingvar.example/doc/2026-01-01/")
            @service(sdkId: "BindingVarQuery", endpointPrefix: "bindingvarquery")
            service BindingVarQuery {
                version: "2026-01-01"
                operations: [CreateResource]
            }

            operation CreateResource {
                input: CreateResourceInput
                output: CreateResourceOutput
            }

            structure CreateResourceInput {
                clientToken: String
            }

            structure CreateResourceOutput {}
            """;

  private static String generateRestXmlCodec() {
    Model model =
        Model.assembler()
            .addUnparsedModel("restxml.smithy", REST_XML_MODEL)
            .discoverModels()
            .assemble()
            .unwrap();
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember(
                            "service", "smithy.beam.test.bindingvar.restxml#BindingVarRestXml")
                        .withMember("edition", "2026")
                        .build())
                .build());
    return manifest.getFileString("binding_var_rest_xml_rest_xml.erl").orElse("");
  }

  private static String generateAwsQueryCodec() {
    Model model =
        Model.assembler()
            .addUnparsedModel("awsquery.smithy", AWS_QUERY_MODEL)
            .discoverModels()
            .assemble()
            .unwrap();
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember(
                            "service", "smithy.beam.test.bindingvar.awsquery#BindingVarQuery")
                        .withMember("edition", "2026")
                        .build())
                .build());
    return manifest.getFileString("binding_var_query_aws_query.erl").orElse("");
  }

  @Test
  void restXmlCodecUsesCamelCaseBindingVariables() {
    String codec = generateRestXmlCodec();
    assertThat(codec).contains("client_token = ClientToken");
    assertThat(codec).doesNotContain("Client_token");
  }

  @Test
  void awsQueryCodecUsesCamelCaseBindingVariables() {
    String codec = generateAwsQueryCodec();
    assertThat(codec).contains("client_token = ClientToken");
    assertThat(codec).doesNotContain("Client_token");
  }
}
