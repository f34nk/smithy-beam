package io.smithy.beam.core;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class BeamRetryIndexTest {

    private static Model loadModel() {
        URL resource = BeamRetryIndexTest.class.getResource("/model/retryable_errors_fixture.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    void forErrorReturnsEmptyWhenNotAnErrorShape() {
        Model model = loadModel();
        StructureShape unit = model.expectShape(
                ShapeId.from("smithy.beam.test.retry#Unit"), StructureShape.class);
        assertThat(BeamRetryIndex.forError(unit)).isEmpty();
    }

    @Test
    void forErrorClassifiesRetryableAndThrottlingTraits() {
        Model model = loadModel();
        StructureShape retryable = model.expectShape(
                ShapeId.from("smithy.beam.test.retry#RetryableError"), StructureShape.class);
        StructureShape throttling = model.expectShape(
                ShapeId.from("smithy.beam.test.retry#ThrottlingError"), StructureShape.class);
        StructureShape plain = model.expectShape(
                ShapeId.from("smithy.beam.test.retry#PlainError"), StructureShape.class);

        assertThat(BeamRetryIndex.forError(retryable))
                .contains(new BeamRetryIndex.RetryInfo(true, false));
        assertThat(BeamRetryIndex.forError(throttling))
                .contains(new BeamRetryIndex.RetryInfo(true, true));
        assertThat(BeamRetryIndex.forError(plain))
                .contains(new BeamRetryIndex.RetryInfo(false, false));
    }
}
