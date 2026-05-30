package io.smithy.beam.core;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.NullableIndex;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

import static org.assertj.core.api.Assertions.assertThat;

class BeamMemberNullabilityTest {

    @Test
    void dedicatedInputUsesClientMode() {
        Model model = Model.assembler()
                .addImport(getClass().getResource("/model/client_input_nullable.smithy"))
                .discoverModels()
                .assemble()
                .unwrap();
        StructureShape input = model.expectShape(
                ShapeId.from("smithy.beam.test#GetItemInput"), StructureShape.class);
        MemberShape id = input.getMember("id").get();
        NullableIndex index = NullableIndex.of(model);
        assertThat(BeamMemberNullability.isMemberNullable(index, input, id)).isTrue();
    }
}
