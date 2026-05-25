package io.smithy.beam.test;

import io.smithy.beam.core.BeamHttpBindings;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.net.URL;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class BeamHttpBindingsFixtureTest {

    @Test
    void describeItemBindingsCoverLabelQueryHeaderAndPayload() {
        URL resource = Objects.requireNonNull(getClass().getResource("/model/protocol_rest_json_fixture.smithy"));
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        BeamHttpBindings bindings = BeamHttpBindings.from(model);
        ShapeId opId = ShapeId.from("smithy.beam.demo.protocoljson#DescribeItem");
        OperationShape op = model.expectShape(opId, OperationShape.class);

        assertThat(bindings.requestBindings(opId, HttpBinding.Location.LABEL)).hasSize(1);
        assertThat(bindings.requestBindings(opId, HttpBinding.Location.QUERY)).hasSize(1);
        assertThat(bindings.requestBindings(opId, HttpBinding.Location.HEADER)).hasSize(1);
        assertThat(bindings.hasResponseBody(opId)).isTrue();
        assertThat(bindings.httpResponseCode(op)).isEqualTo(200);
    }
}
