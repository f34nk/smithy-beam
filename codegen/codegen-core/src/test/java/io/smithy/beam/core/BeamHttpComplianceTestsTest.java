package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.protocoltests.traits.AppliesTo;

class BeamHttpComplianceTestsTest {

  private static Model loadModel() {
    URL resource =
        BeamHttpComplianceTestsTest.class.getResource("/model/compliance_tests_fixture.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  @Test
  void requestTestsParsesHttpRequestTestsTrait() {
    Model model = loadModel();
    OperationShape operation =
        model.expectShape(
            ShapeId.from("smithy.beam.test.compliance#GetItem"), OperationShape.class);

    var cases = BeamHttpComplianceTests.requestTests(model, operation);
    assertThat(cases).hasSize(1);

    BeamHttpComplianceTests.HttpRequestTestCase testCase = cases.get(0);
    assertThat(testCase.id()).isEqualTo("GetItemRequest");
    assertThat(testCase.method()).isEqualTo("GET");
    assertThat(testCase.uri()).isEqualTo("/items/abc");
    assertThat(testCase.headers()).containsEntry("X-Test", "1");
    assertThat(testCase.params().expectStringMember("id").getValue()).isEqualTo("abc");
    assertThat(testCase.appliesTo()).contains(AppliesTo.CLIENT);
    assertThat(testCase.protocol()).isEqualTo(RestJson1Trait.ID);
  }

  @Test
  void responseTestsParsesHttpResponseTestsTrait() {
    Model model = loadModel();
    OperationShape operation =
        model.expectShape(
            ShapeId.from("smithy.beam.test.compliance#GetItem"), OperationShape.class);

    var cases = BeamHttpComplianceTests.responseTests(model, operation);
    assertThat(cases).hasSize(1);

    BeamHttpComplianceTests.HttpResponseTestCase testCase = cases.get(0);
    assertThat(testCase.id()).isEqualTo("GetItemResponse");
    assertThat(testCase.code()).isEqualTo(200);
    assertThat(testCase.headers()).containsEntry("Content-Type", "application/json");
    assertThat(testCase.body()).isEqualTo("{\"name\": \"widget\"}");
    assertThat(testCase.params().expectStringMember("name").getValue()).isEqualTo("widget");
    assertThat(testCase.errorShapeId()).isEmpty();
  }

  @Test
  void requestTestsForServiceCollectsOperationCases() {
    Model model = loadModel();
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.test.compliance#ComplianceService"), ServiceShape.class);

    var bindings = BeamHttpComplianceTests.requestTestsForService(model, service);
    assertThat(bindings).hasSize(1);
    assertThat(bindings.get(0).operation().getId().getName()).isEqualTo("GetItem");
    assertThat(bindings.get(0).cases()).hasSize(1);
  }

  @Test
  void filterRequestTestsMatchesProtocolAndAppliesTo() {
    Model model = loadModel();
    OperationShape operation =
        model.expectShape(
            ShapeId.from("smithy.beam.test.compliance#GetItem"), OperationShape.class);

    var filtered =
        BeamHttpComplianceTests.filterRequestTests(
            BeamHttpComplianceTests.requestTests(model, operation),
            AppliesTo.CLIENT,
            RestJson1Trait.ID);
    assertThat(filtered).hasSize(1);

    var serverFiltered =
        BeamHttpComplianceTests.filterRequestTests(
            BeamHttpComplianceTests.requestTests(model, operation),
            AppliesTo.SERVER,
            RestJson1Trait.ID);
    assertThat(serverFiltered).isEmpty();
  }
}
