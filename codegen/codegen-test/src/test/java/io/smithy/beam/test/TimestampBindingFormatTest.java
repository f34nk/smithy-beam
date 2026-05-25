package io.smithy.beam.test;

import io.smithy.beam.core.BeamHttpBindings;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

import java.net.URL;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class TimestampBindingFormatTest {

    @Test
    void fixtureChoosesFormatFromBindingIndex() {
        URL resource = Objects.requireNonNull(getClass().getResource("/model/protocol_rest_json_fixture.smithy"));
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        BeamHttpBindings bindings = BeamHttpBindings.from(model);
        ShapeId payload = ShapeId.from("smithy.beam.demo.protocoljson#ItemPayload");
        StructureShape payloadShape = model.expectShape(payload, StructureShape.class);
        MemberShape created = payloadShape.getMember("createdAt").get();
        TimestampFormatTrait.Format format =
                bindings.timestampFormat(created, HttpBinding.Location.DOCUMENT, TimestampFormatTrait.Format.DATE_TIME);
        assertThat(format).isNotNull();
        // TODO: when codecs emit literals, assert encoder ignores modeled timezone display strings
        // and always serializes instants using the resolved TimestampFormatTrait.Format.
    }
}
