package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamS3CustomizationIndexTest {

  @Test
  void detectsS3ServiceAndBucketLabelBinding() {
    java.net.URL resource =
        BeamS3CustomizationIndexTest.class.getResource("/model/s3_rest_xml_fixture.smithy");
    assertThat(resource).isNotNull();
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();

    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.test.s3restxml#S3RestXmlService"), ServiceShape.class);
    assertThat(BeamS3CustomizationIndex.isS3Service(service)).isTrue();

    BeamS3CustomizationIndex index = BeamS3CustomizationIndex.of(model);
    OperationShape putObject =
        model.expectShape(
            ShapeId.from("smithy.beam.test.s3restxml#PutObject"), OperationShape.class);
    assertThat(index.bucketLabelBinding(putObject)).isPresent();
    assertThat(index.keyLabelBinding(putObject)).isPresent();
    assertThat(index.serviceUsesBucketAddressing(service)).isTrue();
  }
}
