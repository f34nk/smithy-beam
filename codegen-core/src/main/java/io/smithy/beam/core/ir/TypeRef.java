package io.smithy.beam.core.ir;

public sealed interface TypeRef {
    record Primitive(PrimitiveKind kind) implements TypeRef {}

    record Named(String name) implements TypeRef {}

    record ListOf(TypeRef element) implements TypeRef {}

    record MapOf(TypeRef key, TypeRef value) implements TypeRef {}

    record Optional(TypeRef inner) implements TypeRef {}
}
