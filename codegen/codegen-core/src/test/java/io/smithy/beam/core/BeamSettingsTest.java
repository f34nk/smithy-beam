package io.smithy.beam.core;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BeamSettingsTest {

    // ── resolveService ───────────────────────────────────────────────────────

    @Test
    void resolveService_returnsExplicitServiceId_whenSet() {
        BeamSettings settings = new BeamSettings();
        settings.edition("2026");
        ShapeId id = ShapeId.from("com.example#MyService");
        settings.service(id);

        Model model = Model.builder()
            .addShape(ServiceShape.builder().id(id).version("1").build())
            .build();

        assertThat(settings.resolveService(model)).isEqualTo(id);
    }

    @Test
    void resolveService_resolvesAutomatically_whenModelHasExactlyOneService() {
        ShapeId id = ShapeId.from("com.example#OnlyService");
        Model model = Model.builder()
            .addShape(ServiceShape.builder().id(id).version("1").build())
            .build();

        BeamSettings settings = new BeamSettings();
        settings.edition("2026");

        assertThat(settings.resolveService(model)).isEqualTo(id);
    }

    @Test
    void resolveService_sortsDeterministically_andReturnsSingleService() {
        ShapeId id = ShapeId.from("com.example#Alpha");
        Model model = Model.builder()
            .addShape(ServiceShape.builder().id(id).version("1").build())
            .build();

        BeamSettings settings = new BeamSettings();
        settings.edition("2026");

        assertThat(settings.resolveService(model)).isEqualTo(id);
    }

    @Test
    void resolveService_throwsCodegenException_whenModelHasNoService() {
        Model model = Model.builder().build();

        BeamSettings settings = new BeamSettings();
        settings.edition("2026");

        assertThatThrownBy(() -> settings.resolveService(model))
            .isInstanceOf(CodegenException.class)
            .hasMessageContaining("service");
    }

    @Test
    void resolveService_throwsCodegenException_whenModelHasMultipleServices() {
        Model model = Model.builder()
            .addShape(ServiceShape.builder().id(ShapeId.from("com.example#ServiceA")).version("1").build())
            .addShape(ServiceShape.builder().id(ShapeId.from("com.example#ServiceB")).version("1").build())
            .build();

        BeamSettings settings = new BeamSettings();
        settings.edition("2026");

        assertThatThrownBy(() -> settings.resolveService(model))
            .isInstanceOf(CodegenException.class)
            .hasMessageContaining("service");
    }

    @Test
    void resolveService_throwsCodegenException_whenEditionIsMissing() {
        ShapeId id = ShapeId.from("com.example#MyService");
        Model model = Model.builder()
            .addShape(ServiceShape.builder().id(id).version("1").build())
            .build();

        BeamSettings settings = new BeamSettings();

        assertThatThrownBy(() -> settings.resolveService(model))
            .isInstanceOf(CodegenException.class)
            .hasMessageContaining("edition");
    }

    @Test
    void resolveService_throwsCodegenException_whenEditionIsBlank() {
        ShapeId id = ShapeId.from("com.example#MyService");
        Model model = Model.builder()
            .addShape(ServiceShape.builder().id(id).version("1").build())
            .build();

        BeamSettings settings = new BeamSettings();
        settings.edition("   ");

        assertThatThrownBy(() -> settings.resolveService(model))
            .isInstanceOf(CodegenException.class)
            .hasMessageContaining("edition");
    }

    // ── resolveModule ────────────────────────────────────────────────────────

    @Test
    void resolveModule_returnsExplicitModule_whenSet() {
        BeamSettings settings = new BeamSettings();
        settings.module("custom_module");

        assertThat(settings.resolveModule("com.example.ignored")).isEqualTo("custom_module");
    }

    @Test
    void resolveModule_derivesLastNamespaceSegment_whenModuleIsNotSet() {
        BeamSettings settings = new BeamSettings();

        assertThat(settings.resolveModule("smithy.beam.demo.basic")).isEqualTo("basic");
    }

    @Test
    void resolveModule_handlesSimpleNamespace() {
        BeamSettings settings = new BeamSettings();

        assertThat(settings.resolveModule("basic")).isEqualTo("basic");
    }

    @Test
    void resolveModule_doesNotFallBackToNamespace_whenModuleIsEmptyString() {
        BeamSettings settings = new BeamSettings();
        settings.module("");

        assertThat(settings.resolveModule("com.example.foo")).isEqualTo("foo");
    }

    // ── accessors ────────────────────────────────────────────────────────────

    @Test
    void accessors_roundTrip() {
        ShapeId serviceId = ShapeId.from("com.example#Svc");

        BeamSettings settings = new BeamSettings();
        settings.service(serviceId);
        settings.module("my_module");
        settings.edition("2026");
        settings.relativeDate("2026-01-01");
        settings.relativeVersion("1.0");

        assertThat(settings.service()).isEqualTo(serviceId);
        assertThat(settings.module()).isEqualTo("my_module");
        assertThat(settings.edition()).isEqualTo("2026");
        assertThat(settings.relativeDate()).isEqualTo("2026-01-01");
        assertThat(settings.relativeVersion()).isEqualTo("1.0");
    }

    @Test
    void accessors_defaultToNull_whenNotSet() {
        BeamSettings settings = new BeamSettings();

        assertThat(settings.service()).isNull();
        assertThat(settings.module()).isNull();
        assertThat(settings.edition()).isNull();
        assertThat(settings.relativeDate()).isNull();
        assertThat(settings.relativeVersion()).isNull();
    }
}
