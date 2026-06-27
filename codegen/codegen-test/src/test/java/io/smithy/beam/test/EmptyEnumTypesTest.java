package io.smithy.beam.test;

import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.erlang.ErlangContext;
import io.smithy.beam.erlang.ErlangWriter;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.codegen.core.directed.GenerateEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateIntEnumDirective;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmptyEnumTypesTest {

    private static final ShapeId SERVICE_ID = ShapeId.from("example.com#EmptyEnumService");
    private static final ShapeId STATUS_ID = ShapeId.from("example.com#EmptyStatus");
    private static final ShapeId PRIORITY_ID = ShapeId.from("example.com#EmptyPriority");
    private static final ShapeId BUNDLE_ID = ShapeId.from("example.com#EmptyBundle");
    private static final ShapeId OP_ID = ShapeId.from("example.com#GetEmpty");
    private static final String TYPES_FILE = "empty_enum_service_types.hrl";

    @Test
    void erlangTypesEmitValidAliasesForEmptyEnumMembers() throws Exception {
        MockManifest manifest = new MockManifest();
        Model model = modelWithPopulatedEnums();
        ServiceShape service = model.expectShape(SERVICE_ID, ServiceShape.class);
        BeamSettings settings = new BeamSettings();
        settings.edition("2026");

        SymbolProvider emptyAtomProvider = shape -> {
            if (shape.getId().equals(STATUS_ID)) {
                return Symbol.builder()
                        .definitionFile(TYPES_FILE)
                        .name("empty_status()")
                        .putProperty("enumAtoms", List.of())
                        .putProperty("enumAtomByMember", Map.of())
                        .build();
            }
            if (shape.getId().equals(PRIORITY_ID)) {
                return Symbol.builder()
                        .definitionFile(TYPES_FILE)
                        .name("empty_priority()")
                        .putProperty("enumAtoms", List.of())
                        .putProperty("enumAtomByMember", Map.of())
                        .build();
            }
            throw new UnsupportedOperationException("unexpected shape: " + shape.getId());
        };

        ErlangContext context = new ErlangContext(
                model,
                settings,
                emptyAtomProvider,
                manifest,
                new WriterDelegator<>(manifest, emptyAtomProvider, ErlangWriter.factory()),
                List.of(),
                service,
                BeamHttpBindings.from(model),
                null,
                null,
                "empty_enum_service",
                TYPES_FILE);

        Class<?> codegenClass = Class.forName("io.smithy.beam.erlang.ErlangTypeDirectedCodegen");
        Constructor<?> codegenConstructor = codegenClass.getDeclaredConstructor();
        codegenConstructor.setAccessible(true);
        Object codegen = codegenConstructor.newInstance();
        Method generateEnum = codegen.getClass()
                .getDeclaredMethod("generateEnumShape", GenerateEnumDirective.class);
        generateEnum.setAccessible(true);
        Method generateIntEnum = codegen.getClass()
                .getDeclaredMethod("generateIntEnumShape", GenerateIntEnumDirective.class);
        generateIntEnum.setAccessible(true);

        generateEnum.invoke(
                codegen,
                newEnumDirective(context, service, model.expectShape(STATUS_ID, EnumShape.class)));
        generateIntEnum.invoke(
                codegen,
                newIntEnumDirective(context, service, model.expectShape(PRIORITY_ID, IntEnumShape.class)));
        context.writerDelegator().flushWriters();

        String content = manifest.expectFileString(TYPES_FILE);
        assertThat(content).contains("-type empty_status() :: {unknown, binary()}.");
        assertThat(content).contains("-type empty_priority() :: {unknown, integer()}.");
        assertThat(content).doesNotContain(" ::  | {unknown");
    }

    private static GenerateEnumDirective<ErlangContext, BeamSettings> newEnumDirective(
            ErlangContext context, ServiceShape service, EnumShape shape) throws Exception {
        Constructor<GenerateEnumDirective> constructor =
                GenerateEnumDirective.class.getDeclaredConstructor(
                        software.amazon.smithy.codegen.core.CodegenContext.class,
                        ServiceShape.class,
                        software.amazon.smithy.model.shapes.Shape.class);
        constructor.setAccessible(true);
        return constructor.newInstance(context, service, shape);
    }

    private static GenerateIntEnumDirective<ErlangContext, BeamSettings> newIntEnumDirective(
            ErlangContext context, ServiceShape service, IntEnumShape shape) throws Exception {
        Constructor<GenerateIntEnumDirective> constructor =
                GenerateIntEnumDirective.class.getDeclaredConstructor(
                        software.amazon.smithy.codegen.core.CodegenContext.class,
                        ServiceShape.class,
                        software.amazon.smithy.model.shapes.Shape.class);
        constructor.setAccessible(true);
        return constructor.newInstance(context, service, shape);
    }

    private static Model modelWithPopulatedEnums() {
        ServiceShape service = ServiceShape.builder()
                .id(SERVICE_ID)
                .version("1")
                .addOperation(OP_ID)
                .build();
        OperationShape operation = OperationShape.builder()
                .id(OP_ID)
                .output(BUNDLE_ID)
                .build();
        EnumShape status = EnumShape.builder()
                .id(STATUS_ID)
                .addMember("ACTIVE", "ACTIVE")
                .build();
        IntEnumShape priority = IntEnumShape.builder()
                .id(PRIORITY_ID)
                .addMember("LOW", 1)
                .build();
        StructureShape bundle = StructureShape.builder()
                .id(BUNDLE_ID)
                .addMember("status", STATUS_ID)
                .addMember("priority", PRIORITY_ID)
                .build();
        return Model.assembler()
                .addShape(service)
                .addShape(operation)
                .addShape(status)
                .addShape(priority)
                .addShape(bundle)
                .assemble()
                .unwrap();
    }
}
