package io.smithy.beam.core;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import static org.assertj.core.api.Assertions.assertThat;

class BeamServiceNamingTest {

    @Test
    void renameMapChangesEffectiveServiceName() {
        Model model = Model.assembler()
                .addImport(getClass().getResource("/model/service_self_rename.smithy"))
                .discoverModels()
                .assemble()
                .unwrap();
        ServiceShape service = model.expectShape(
                ShapeId.from("smithy.beam.test#OriginalName"), ServiceShape.class);
        assertThat(BeamServiceNaming.effectiveServiceName(service)).isEqualTo("RenamedService");
        assertThat(BeamServiceNaming.effectiveServiceSnakeName(service))
                .isEqualTo("renamed_service");
    }
}
