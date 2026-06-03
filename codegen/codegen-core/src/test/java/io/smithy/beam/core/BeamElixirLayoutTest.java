package io.smithy.beam.core;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import static org.assertj.core.api.Assertions.assertThat;

class BeamElixirLayoutTest {

    @Test
    void typesModuleFile_usesServiceSnakeNameAtCodegenRoot() {
        BeamSettings settings = new BeamSettings();
        settings.edition("2026");
        ServiceShape service = ServiceShape.builder()
                .id(ShapeId.from("smithy.beam.demo.basic#BasicService"))
                .version("1")
                .build();
        BeamElixirLayout layout =
                new BeamElixirLayout(settings, service.getId().getNamespace(), service);

        assertThat(layout.typesModuleFile()).isEqualTo("basic_service_types.ex");
        assertThat(layout.clientModuleFile()).isEqualTo("basic_service_client.ex");
    }
}
