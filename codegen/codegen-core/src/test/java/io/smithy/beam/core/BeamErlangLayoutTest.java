package io.smithy.beam.core;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import static org.assertj.core.api.Assertions.assertThat;

class BeamErlangLayoutTest {

    @Test
    void typesHeaderFile_usesServiceSnakeName() {
        BeamSettings settings = new BeamSettings();
        settings.edition("2026");
        ServiceShape service = ServiceShape.builder()
                .id(ShapeId.from("smithy.beam.demo.basic#BasicService"))
                .version("1")
                .build();
        BeamErlangLayout layout =
                new BeamErlangLayout(settings, service.getId().getNamespace(), service);

        assertThat(layout.typesHeaderFile()).isEqualTo("basic_service_types.hrl");
        assertThat(layout.clientModuleFile()).isEqualTo("basic_service_client.erl");
    }
}
