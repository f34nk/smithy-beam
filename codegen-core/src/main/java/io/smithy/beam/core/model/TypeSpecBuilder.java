package io.smithy.beam.core.model;

import io.smithy.beam.core.ir.EnumSpec;
import io.smithy.beam.core.ir.FieldSpec;
import io.smithy.beam.core.ir.ModuleTypeSpec;
import io.smithy.beam.core.ir.PrimitiveKind;
import io.smithy.beam.core.ir.StructSpec;
import io.smithy.beam.core.ir.TypeRef;
import io.smithy.beam.core.ir.UnionSpec;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.SetShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.EnumTrait;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.HttpErrorTrait;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts reachable Smithy shapes into {@link ModuleTypeSpec} IR.
 */
public final class TypeSpecBuilder {

    private TypeSpecBuilder() {}

    public static ModuleTypeSpec build(ServiceShape service, Model model, Set<Shape> reachable) {
        String serviceName = service.getId().getName();

        List<StructSpec> structures = new ArrayList<>();
        List<StructSpec> errors = new ArrayList<>();
        List<EnumSpec> enums = new ArrayList<>();
        List<UnionSpec> unions = new ArrayList<>();

        Set<ShapeId> errorShapeIds = collectOperationErrorShapeIds(service, model);

        for (Shape shape : sortShapes(reachable)) {
            if (shape instanceof StructureShape s) {
                StructSpec spec = toStructSpec(s, model);
                if (s.hasTrait(ErrorTrait.class) || errorShapeIds.contains(s.getId())) {
                    errors.add(spec);
                } else {
                    structures.add(spec);
                }
            } else if (shape instanceof EnumShape e) {
                enums.add(toEnumSpec(e));
            } else if (shape instanceof StringShape ss && ss.getTrait(EnumTrait.class).isPresent()) {
                enums.add(toEnumSpec(ss.getId().getName(), ss.getTrait(EnumTrait.class).get()));
            } else if (shape instanceof UnionShape u) {
                unions.add(toUnionSpec(u, model));
            }
        }

        boolean hasServiceErrors =
                !errors.isEmpty() || TopDownIndex.of(model).getContainedOperations(service).stream()
                        .anyMatch(op -> !op.getErrors().isEmpty());

        return new ModuleTypeSpec(serviceName, structures, enums, unions, errors, hasServiceErrors);
    }

    private static Set<ShapeId> collectOperationErrorShapeIds(ServiceShape service, Model model) {
        Set<ShapeId> ids = new LinkedHashSet<>();
        for (OperationShape op : TopDownIndex.of(model).getContainedOperations(service)) {
            ids.addAll(op.getErrors());
        }
        return ids;
    }

    /** Deterministic order for stable codegen. */
    private static List<Shape> sortShapes(Set<Shape> reachable) {
        return reachable.stream()
                .filter(s -> !s.getType().equals(ShapeType.SERVICE))
                .sorted(Comparator.comparing(s -> s.getId().toString()))
                .collect(Collectors.toList());
    }

    private static StructSpec toStructSpec(StructureShape shape, Model model) {
        List<FieldSpec> fields = new ArrayList<>();
        for (MemberShape member : shape.getAllMembers().values()) {
            fields.add(toFieldSpec(member, model));
        }
        int httpError = shape.getTrait(HttpErrorTrait.class).map(HttpErrorTrait::getCode).orElse(0);
        return new StructSpec(shape.getId().getName(), fields, shape.hasTrait(ErrorTrait.class), httpError);
    }

    private static FieldSpec toFieldSpec(MemberShape member, Model model) {
        TypeRef type = memberToTypeRef(member, model);
        return new FieldSpec(
                member.getMemberName(),
                type,
                member.isRequired(),
                false,
                false,
                false,
                false);
    }

    private static TypeRef memberToTypeRef(MemberShape member, Model model) {
        Shape target = model.expectShape(member.getTarget());
        TypeRef inner = shapeToTypeRef(target, model);
        if (member.isOptional()) {
            return new TypeRef.Optional(inner);
        }
        return inner;
    }

    private static TypeRef shapeToTypeRef(Shape shape, Model model) {
        return switch (shape.getType()) {
            case BOOLEAN -> new TypeRef.Primitive(PrimitiveKind.BOOLEAN);
            case STRING -> new TypeRef.Primitive(PrimitiveKind.STRING);
            case BLOB -> new TypeRef.Primitive(PrimitiveKind.BLOB);
            case TIMESTAMP -> new TypeRef.Primitive(PrimitiveKind.TIMESTAMP);
            case BYTE, SHORT, INTEGER -> new TypeRef.Primitive(PrimitiveKind.INTEGER);
            case LONG, BIG_INTEGER -> new TypeRef.Primitive(PrimitiveKind.LONG);
            case FLOAT -> new TypeRef.Primitive(PrimitiveKind.FLOAT);
            case DOUBLE, BIG_DECIMAL -> new TypeRef.Primitive(PrimitiveKind.DOUBLE);
            case LIST -> {
                ListShape list = shape.asListShape().orElseThrow();
                yield new TypeRef.ListOf(memberToTypeRef(list.getMember(), model));
            }
            case SET -> {
                SetShape set = shape.asSetShape().orElseThrow();
                yield new TypeRef.ListOf(memberToTypeRef(set.getMember(), model));
            }
            case MAP -> {
                MapShape map = shape.asMapShape().orElseThrow();
                TypeRef k = memberToTypeRef(map.getKey(), model);
                TypeRef v = memberToTypeRef(map.getValue(), model);
                yield new TypeRef.MapOf(k, v);
            }
            case STRUCTURE, UNION, ENUM, INT_ENUM, DOCUMENT -> new TypeRef.Named(shape.getId().toString());
            default -> new TypeRef.Named(shape.getId().toString());
        };
    }

    private static EnumSpec toEnumSpec(EnumShape shape) {
        List<String> values = new ArrayList<>(shape.getEnumValues().keySet());
        values.sort(String::compareTo);
        return new EnumSpec(shape.getId().getName(), values);
    }

    private static EnumSpec toEnumSpec(String name, EnumTrait trait) {
        List<String> values = new ArrayList<>(trait.getEnumDefinitionValues());
        values.sort(String::compareTo);
        return new EnumSpec(name, values);
    }

    private static UnionSpec toUnionSpec(UnionShape shape, Model model) {
        List<FieldSpec> variants = new ArrayList<>();
        for (MemberShape member : shape.getAllMembers().values()) {
            variants.add(toFieldSpec(member, model));
        }
        return new UnionSpec(shape.getId().getName(), variants);
    }

}
