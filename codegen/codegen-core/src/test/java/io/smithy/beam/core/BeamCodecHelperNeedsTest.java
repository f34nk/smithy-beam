package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamCodecHelperNeedsTest {

  @Test
  void httpLabelOnlyNeedsUriHelpers() {
    Model model =
        Model.assembler()
            .addUnparsedModel(
                "test.smithy",
                """
                        $version: "2"
                        namespace smithy.beam.test.helpers

                        use aws.protocols#restJson1

                        @restJson1
                        service LabelService {
                            version: "2026"
                            operations: [GetItem]
                        }

                        @http(method: "GET", uri: "/items/{id}", code: 200)
                        operation GetItem {
                            input: GetItemInput
                            output: GetItemOutput
                        }

                        structure GetItemInput {
                            @required
                            @httpLabel
                            id: String
                        }

                        structure GetItemOutput {}
                        """)
            .discoverModels()
            .assemble()
            .unwrap();
    ServiceShape service =
        model.expectShape(ShapeId.from("smithy.beam.test.helpers#LabelService"), ServiceShape.class);

    BeamCodecHelperNeeds needs = BeamCodecHelperNeeds.of(model, service);
    assertThat(needs.uriCoding()).isTrue();
    assertThat(needs.toBinary()).isTrue();
    assertThat(needs.queryValues()).isFalse();
    assertThat(needs.prefixHeaders()).isFalse();
    assertThat(needs.sparseList()).isFalse();
    assertThat(needs.sparseMap()).isFalse();
    assertThat(needs.timestamps()).isFalse();
    assertThat(needs.idempotencyToken()).isFalse();
    assertThat(needs.jsonBody()).isFalse();
    assertThat(needs.contentTypeMatches()).isFalse();
  }

  @Test
  void idempotencyTokenNeedsUuid() {
    Model model =
        Model.assembler()
            .addUnparsedModel(
                "test.smithy",
                """
                        $version: "2"
                        namespace smithy.beam.test.helpers

                        use aws.protocols#restJson1

                        @restJson1
                        service TokenService {
                            version: "2026"
                            operations: [Create]
                        }

                        @http(method: "POST", uri: "/items", code: 200)
                        operation Create {
                            input: CreateInput
                            output: CreateOutput
                        }

                        structure CreateInput {
                            @idempotencyToken
                            clientToken: String
                        }

                        structure CreateOutput {}
                        """)
            .discoverModels()
            .assemble()
            .unwrap();
    ServiceShape service =
        model.expectShape(ShapeId.from("smithy.beam.test.helpers#TokenService"), ServiceShape.class);

    BeamCodecHelperNeeds needs = BeamCodecHelperNeeds.of(model, service);
    assertThat(needs.idempotencyToken()).isTrue();
  }

  @Test
  void awsJsonNeedsJsonBodyHelper() {
    Model model =
        Model.assembler()
            .addUnparsedModel(
                "test.smithy",
                """
                        $version: "2"
                        namespace smithy.beam.test.helpers

                        use aws.protocols#awsJson1_1
                        use aws.api#service

                        @awsJson1_1
                        @service(sdkId: "Json11", endpointPrefix: "json11")
                        service Json11Service {
                            version: "2026"
                            operations: [GetUser]
                        }

                        operation GetUser {
                            input: GetUserInput
                            output: GetUserOutput
                        }

                        structure GetUserInput {
                            userName: String
                        }

                        structure GetUserOutput {
                            userName: String
                        }
                        """)
            .discoverModels()
            .assemble()
            .unwrap();
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.test.helpers#Json11Service"), ServiceShape.class);

    BeamCodecHelperNeeds needs = BeamCodecHelperNeeds.of(model, service);
    assertThat(needs.jsonBody()).isTrue();
    assertThat(needs.toBinary()).isFalse();
    assertThat(needs.uriCoding()).isFalse();
    assertThat(needs.idempotencyToken()).isFalse();
  }
}
