package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamSigV4IndexTest {

  private static final ShapeId SERVICE =
      ShapeId.from("smithy.beam.test.sigv4#Sigv4UnsignedTestService");
  private static final ShapeId PING = ShapeId.from("smithy.beam.test.sigv4#Ping");
  private static final ShapeId UPLOAD = ShapeId.from("smithy.beam.test.sigv4#Upload");

  private static Model loadModel() {
    URL resource = BeamSigV4IndexTest.class.getResource("/model/sigv4_unsigned_fixture.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  @Test
  void serviceMetadataReadsSigningNameAndUnsignedPayloadDefault() {
    Model model = loadModel();
    ServiceShape service = model.expectShape(SERVICE, ServiceShape.class);

    assertThat(BeamSigV4Metadata.from(service)).isPresent();
    assertThat(BeamSigV4Metadata.from(service).get().signingName()).isEqualTo("sigv4test");
    assertThat(BeamSigV4Metadata.from(service).get().unsignedPayload()).isFalse();
  }

  @Test
  void indexMarksOnlyUnsignedPayloadOperations() {
    Model model = loadModel();
    ServiceShape service = model.expectShape(SERVICE, ServiceShape.class);
    BeamSigV4Index index = BeamSigV4Index.of(model, service);

    OperationShape ping = model.expectShape(PING, OperationShape.class);
    OperationShape upload = model.expectShape(UPLOAD, OperationShape.class);

    assertThat(index.operationUsesUnsignedPayload(ping)).isFalse();
    assertThat(index.operationUsesUnsignedPayload(upload)).isTrue();
    assertThat(index.operationsWithUnsignedPayload(model, service))
        .extracting(op -> op.getId().getName())
        .containsExactly("Upload");
  }
}
