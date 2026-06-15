package io.smithy.beam.core;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class BeamClientRetrySupportTest {

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
    void operationHasRetryableErrors_trueWhenOperationDeclaresRetryableError() {
        Model model = loadModel();
        OperationShape ping = model.expectShape(
                ShapeId.from("smithy.beam.test.retry#Ping"), OperationShape.class);
        assertThat(BeamClientRetrySupport.operationHasRetryableErrors(model, ping)).isTrue();
    }
}
