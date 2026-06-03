package io.smithy.beam.core;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.CodegenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BeamEditionTest {

    @Test
    void fromSettingsResolvesKnownEditionLabel() {
        BeamSettings settings = new BeamSettings();
        settings.edition("2026");

        assertThat(BeamEdition.fromSettings(settings)).isEqualTo(BeamEdition.V2026);
    }

    @Test
    void fromSettingsRejectsUnknownEdition() {
        BeamSettings settings = new BeamSettings();
        settings.edition("1999");

        assertThatThrownBy(() -> BeamEdition.fromSettings(settings))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("Unknown edition");
    }

    @Test
    void v2026SupportsEventStreams() {
        assertThat(BeamEdition.V2026.supportsEventStreams()).isTrue();
    }
}
