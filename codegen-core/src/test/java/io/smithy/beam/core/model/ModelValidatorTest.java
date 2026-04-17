package io.smithy.beam.core.model;

import io.smithy.beam.core.CodegenException;
import io.smithy.beam.core.ir.AuthSpec;
import io.smithy.beam.core.ir.BodyEncoding;
import io.smithy.beam.core.ir.BodySpec;
import io.smithy.beam.core.ir.EnumSpec;
import io.smithy.beam.core.ir.ErrorBinding;
import io.smithy.beam.core.ir.ErrorCodeStrategy;
import io.smithy.beam.core.ir.ErrorSpec;
import io.smithy.beam.core.ir.HttpSpec;
import io.smithy.beam.core.ir.ModuleTypeSpec;
import io.smithy.beam.core.ir.OperationSpec;
import io.smithy.beam.core.ir.RetrySpec;
import io.smithy.beam.core.ir.Role;
import io.smithy.beam.core.ir.StructSpec;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelValidatorTest {

    private static final ServiceShape SERVICE = ServiceShape.builder()
            .id(ShapeId.from("test.example#TestService"))
            .version("2024-01-01")
            .build();

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ModuleTypeSpec emptyTypes() {
        return new ModuleTypeSpec("TestService",
                List.of(), List.of(), List.of(), List.of(), false);
    }

    private static OperationSpec minimalOp(String name, BodySpec body) {
        return new OperationSpec(
                name, "TestService", Role.CLIENT,
                new HttpSpec("GET", "/", 200),
                List.of(), List.of(), List.of(),
                body,
                new ErrorSpec(List.of(), ErrorCodeStrategy.REST_JSON),
                AuthSpec.none(), RetrySpec.disabled(),
                null, null, null,
                BodyEncoding.NONE, "application/json",
                ErrorCodeStrategy.REST_JSON, null);
    }

    private static OperationSpec opWithErrors(String opName, List<ErrorBinding> errors) {
        return new OperationSpec(
                opName, "TestService", Role.CLIENT,
                new HttpSpec("POST", "/", 200),
                List.of(), List.of(), List.of(),
                new BodySpec(BodyEncoding.JSON, List.of(), null),
                new ErrorSpec(errors, ErrorCodeStrategy.REST_JSON),
                AuthSpec.none(), RetrySpec.disabled(),
                null, null, null,
                BodyEncoding.JSON, "application/json",
                ErrorCodeStrategy.REST_JSON, null);
    }

    // -------------------------------------------------------------------------
    // Rule 1 — enum values
    // -------------------------------------------------------------------------

    @Test
    void passeswhenAllEnumsHaveValues() {
        ModuleTypeSpec types = new ModuleTypeSpec("TestService",
                List.of(), List.of(new EnumSpec("Colour", List.of("Red", "Blue"))),
                List.of(), List.of(), false);
        assertThatCode(() -> ModelValidator.validate(SERVICE, types, List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void failsWhenEnumHasNoValues() {
        ModuleTypeSpec types = new ModuleTypeSpec("TestService",
                List.of(), List.of(new EnumSpec("Colour", List.of())),
                List.of(), List.of(), false);
        assertThatThrownBy(() -> ModelValidator.validate(SERVICE, types, List.of()))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("enum 'Colour' has no values");
    }

    // -------------------------------------------------------------------------
    // Rule 2 — duplicate error binding names within one operation
    // -------------------------------------------------------------------------

    @Test
    void passesWhenErrorNamesAreUnique() {
        var op = opWithErrors("Op", List.of(
                new ErrorBinding("FooError", 404, ErrorCodeStrategy.REST_JSON),
                new ErrorBinding("BarError", 500, ErrorCodeStrategy.REST_JSON)));
        assertThatCode(() -> ModelValidator.validate(SERVICE, emptyTypes(), List.of(op)))
                .doesNotThrowAnyException();
    }

    @Test
    void failsWhenSameErrorNameAppearstwiceInOneOperation() {
        var op = opWithErrors("Op", List.of(
                new ErrorBinding("FooError", 404, ErrorCodeStrategy.REST_JSON),
                new ErrorBinding("FooError", 404, ErrorCodeStrategy.REST_JSON)));
        assertThatThrownBy(() -> ModelValidator.validate(SERVICE, emptyTypes(), List.of(op)))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("duplicate error binding for 'FooError'");
    }

    @Test
    void passesWhenSameErrorNameSharedAcrossOperations() {
        var op1 = opWithErrors("Op1", List.of(
                new ErrorBinding("SharedError", 400, ErrorCodeStrategy.REST_JSON)));
        var op2 = opWithErrors("Op2", List.of(
                new ErrorBinding("SharedError", 400, ErrorCodeStrategy.REST_JSON)));
        assertThatCode(() -> ModelValidator.validate(SERVICE, emptyTypes(), List.of(op1, op2)))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Rule 3 — type references resolve
    // -------------------------------------------------------------------------

    @Test
    void passesWhenInputOutputTypeNamesAreNull() {
        var op = minimalOp("Op", new BodySpec(BodyEncoding.NONE, List.of(), null));
        assertThatCode(() -> ModelValidator.validate(SERVICE, emptyTypes(), List.of(op)))
                .doesNotThrowAnyException();
    }

    @Test
    void passesWhenInputOutputTypeNamesAreMap() {
        ModuleTypeSpec types = emptyTypes();
        OperationSpec op = new OperationSpec(
                "Op", "TestService", Role.CLIENT,
                new HttpSpec("GET", "/", 200),
                List.of(), List.of(), List.of(),
                new BodySpec(BodyEncoding.NONE, List.of(), null),
                new ErrorSpec(List.of(), ErrorCodeStrategy.REST_JSON),
                AuthSpec.none(), RetrySpec.disabled(),
                null, "map", "map",
                BodyEncoding.NONE, "application/json",
                ErrorCodeStrategy.REST_JSON, null);
        assertThatCode(() -> ModelValidator.validate(SERVICE, types, List.of(op)))
                .doesNotThrowAnyException();
    }

    @Test
    void passesWhenTypeNamesResolveInTypeSpec() {
        StructSpec input  = new StructSpec("OpInput",  List.of(), false, 0);
        StructSpec output = new StructSpec("OpOutput", List.of(), false, 0);
        ModuleTypeSpec types = new ModuleTypeSpec("TestService",
                List.of(input, output), List.of(), List.of(), List.of(), false);
        OperationSpec op = new OperationSpec(
                "Op", "TestService", Role.CLIENT,
                new HttpSpec("GET", "/", 200),
                List.of(), List.of(), List.of(),
                new BodySpec(BodyEncoding.NONE, List.of(), null),
                new ErrorSpec(List.of(), ErrorCodeStrategy.REST_JSON),
                AuthSpec.none(), RetrySpec.disabled(),
                null, "OpOutput", "OpInput",
                BodyEncoding.NONE, "application/json",
                ErrorCodeStrategy.REST_JSON, null);
        assertThatCode(() -> ModelValidator.validate(SERVICE, types, List.of(op)))
                .doesNotThrowAnyException();
    }

    @Test
    void failsWhenOutputTypeNameDoesNotResolve() {
        ModuleTypeSpec types = emptyTypes();
        OperationSpec op = new OperationSpec(
                "Op", "TestService", Role.CLIENT,
                new HttpSpec("GET", "/", 200),
                List.of(), List.of(), List.of(),
                new BodySpec(BodyEncoding.NONE, List.of(), null),
                new ErrorSpec(List.of(), ErrorCodeStrategy.REST_JSON),
                AuthSpec.none(), RetrySpec.disabled(),
                null, "MissingOutput", null,
                BodyEncoding.NONE, "application/json",
                ErrorCodeStrategy.REST_JSON, null);
        assertThatThrownBy(() -> ModelValidator.validate(SERVICE, types, List.of(op)))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("unknown output type 'MissingOutput'");
    }

    @Test
    void failsWhenInputTypeNameDoesNotResolve() {
        ModuleTypeSpec types = emptyTypes();
        OperationSpec op = new OperationSpec(
                "Op", "TestService", Role.CLIENT,
                new HttpSpec("GET", "/", 200),
                List.of(), List.of(), List.of(),
                new BodySpec(BodyEncoding.NONE, List.of(), null),
                new ErrorSpec(List.of(), ErrorCodeStrategy.REST_JSON),
                AuthSpec.none(), RetrySpec.disabled(),
                null, null, "GhostInput",
                BodyEncoding.NONE, "application/json",
                ErrorCodeStrategy.REST_JSON, null);
        assertThatThrownBy(() -> ModelValidator.validate(SERVICE, types, List.of(op)))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("unknown input type 'GhostInput'");
    }

    @Test
    void passesWhenErrorTypeNameResolvesInErrorsList() {
        StructSpec errorShape = new StructSpec("NotFoundError", List.of(), true, 404);
        ModuleTypeSpec types = new ModuleTypeSpec("TestService",
                List.of(), List.of(), List.of(), List.of(errorShape), false);
        assertThatCode(() -> ModelValidator.validate(SERVICE, types, List.of()))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Rule 4 — NONE encoding must have empty body
    // -------------------------------------------------------------------------

    @Test
    void passesWhenNoneEncodingHasEmptyBody() {
        var op = minimalOp("Op", new BodySpec(BodyEncoding.NONE, List.of(), null));
        assertThatCode(() -> ModelValidator.validate(SERVICE, emptyTypes(), List.of(op)))
                .doesNotThrowAnyException();
    }

    @Test
    void failsWhenNoneEncodingHasBodyMembers() {
        var op = minimalOp("Op", new BodySpec(BodyEncoding.NONE, List.of("field"), null));
        assertThatThrownBy(() -> ModelValidator.validate(SERVICE, emptyTypes(), List.of(op)))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("encoding=NONE but non-empty bodyMemberNames");
    }

    @Test
    void failsWhenNoneEncodingHasPayloadMember() {
        var op = minimalOp("Op", new BodySpec(BodyEncoding.NONE, List.of(), "blob"));
        assertThatThrownBy(() -> ModelValidator.validate(SERVICE, emptyTypes(), List.of(op)))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("encoding=NONE but non-null payloadMember");
    }

    @Test
    void passesWhenJsonEncodingHasBodyMembers() {
        var op = minimalOp("Op",
                new BodySpec(BodyEncoding.JSON, List.of("city", "unit"), null));
        assertThatCode(() -> ModelValidator.validate(SERVICE, emptyTypes(), List.of(op)))
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Happy path — all rules pass together
    // -------------------------------------------------------------------------

    @Test
    void passesForWellFormedIR() {
        StructSpec input  = new StructSpec("GetWeatherInput",  List.of(), false, 0);
        StructSpec output = new StructSpec("GetWeatherOutput", List.of(), false, 0);
        EnumSpec   unit   = new EnumSpec("TemperatureUnit", List.of("Celsius", "Fahrenheit"));
        ModuleTypeSpec types = new ModuleTypeSpec("WeatherService",
                List.of(input, output), List.of(unit), List.of(), List.of(), false);
        OperationSpec op = new OperationSpec(
                "GetWeather", "WeatherService", Role.CLIENT,
                new HttpSpec("GET", "/weather/{city}", 200),
                List.of(), List.of(), List.of(),
                new BodySpec(BodyEncoding.NONE, List.of(), null),
                new ErrorSpec(List.of(), ErrorCodeStrategy.REST_JSON),
                AuthSpec.none(), RetrySpec.disabled(),
                null, "GetWeatherOutput", "GetWeatherInput",
                BodyEncoding.NONE, "application/json",
                ErrorCodeStrategy.REST_JSON, null);

        ServiceShape svc = ServiceShape.builder()
                .id(ShapeId.from("example.weather#WeatherService"))
                .version("2024-01-01")
                .build();
        assertThatCode(() -> ModelValidator.validate(svc, types, List.of(op)))
                .doesNotThrowAnyException();
    }
}
