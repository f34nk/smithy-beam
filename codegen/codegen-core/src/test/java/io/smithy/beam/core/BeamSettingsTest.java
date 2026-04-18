package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import software.amazon.smithy.model.shapes.ShapeId;

class BeamSettingsTest {

    /** Minimal concrete subclass used only for testing the abstract base. */
    private static final class TestSettings extends BeamSettings {
        @Override
        public Mode mode() {
            return Mode.CLIENT;
        }
    }

    @Test
    void validate_throwsWhenServiceIsNull() {
        TestSettings settings = new TestSettings();
        settings.setEdition("2025");

        assertThatThrownBy(settings::validate)
                .isInstanceOf(BeamCodegenException.class)
                .hasMessageContaining("service");
    }

    @Test
    void validate_throwsWhenEditionIsNull() {
        TestSettings settings = new TestSettings();
        settings.setService(ShapeId.from("example.weather#Weather"));

        assertThatThrownBy(settings::validate)
                .isInstanceOf(BeamCodegenException.class)
                .hasMessageContaining("edition");
    }

    @Test
    void validate_throwsWhenEditionIsBlank() {
        TestSettings settings = new TestSettings();
        settings.setService(ShapeId.from("example.weather#Weather"));
        settings.setEdition("  ");

        assertThatThrownBy(settings::validate)
                .isInstanceOf(BeamCodegenException.class)
                .hasMessageContaining("edition");
    }

    @Test
    void validate_succeedsWhenRequiredFieldsAreSet() {
        TestSettings settings = new TestSettings();
        settings.setService(ShapeId.from("example.weather#Weather"));
        settings.setEdition("2025");

        settings.validate(); // must not throw
    }

    @Test
    void outputDir_defaultsToSrcGenerated() {
        assertThat(new TestSettings().getOutputDir()).isEqualTo("src/generated");
    }

    @Test
    void optionalFieldsAreNullByDefault() {
        TestSettings settings = new TestSettings();
        assertThat(settings.getProtocol()).isNull();
        assertThat(settings.getScaffoldDir()).isNull();
        assertThat(settings.getRelativeDate()).isNull();
        assertThat(settings.getRelativeVersion()).isNull();
    }

    @Test
    void mode_returnsValueFromSubclass() {
        assertThat(new TestSettings().mode()).isEqualTo(Mode.CLIENT);
    }
}
