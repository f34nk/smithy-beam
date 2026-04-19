package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamSettingsTest {

    /** Concrete subclass for testing — only exists to satisfy the abstract contract. */
    static final class TestSettings extends BeamSettings {
        @Override
        public Mode mode() {
            return Mode.CLIENT;
        }
    }

    @Test
    void validateThrowsWhenServiceIsNull() {
        TestSettings settings = new TestSettings();
        settings.setEdition("2025");

        assertThatThrownBy(settings::validate)
            .isInstanceOf(BeamCodegenException.class)
            .hasMessageContaining("service");
    }

    @Test
    void validateThrowsWhenEditionIsNull() {
        TestSettings settings = new TestSettings();
        settings.setService(ShapeId.from("com.example#MyService"));

        assertThatThrownBy(settings::validate)
            .isInstanceOf(BeamCodegenException.class)
            .hasMessageContaining("edition");
    }

    @Test
    void validateThrowsWhenEditionIsBlank() {
        TestSettings settings = new TestSettings();
        settings.setService(ShapeId.from("com.example#MyService"));
        settings.setEdition("   ");

        assertThatThrownBy(settings::validate)
            .isInstanceOf(BeamCodegenException.class)
            .hasMessageContaining("edition");
    }

    @Test
    void validatePassesWhenRequiredFieldsPresent() {
        TestSettings settings = new TestSettings();
        settings.setService(ShapeId.from("com.example#MyService"));
        settings.setEdition("2025");

        assertThatCode(settings::validate).doesNotThrowAnyException();
    }

    @Test
    void defaultOutputDirIsSrcGenerated() {
        assertThat(new TestSettings().getOutputDir()).isEqualTo("src/generated");
    }

    @Test
    void modeReturnsClient() {
        assertThat(new TestSettings().mode()).isEqualTo(Mode.CLIENT);
    }

    @Test
    void optionalFieldsDefaultToNull() {
        TestSettings settings = new TestSettings();
        assertThat(settings.getProtocol()).isNull();
        assertThat(settings.getScaffoldDir()).isNull();
        assertThat(settings.getRelativeDate()).isNull();
        assertThat(settings.getRelativeVersion()).isNull();
    }
}
