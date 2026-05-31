package io.smithy.beam.core;

import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.XmlAttributeTrait;
import software.amazon.smithy.model.traits.XmlFlattenedTrait;
import software.amazon.smithy.model.traits.XmlNameTrait;
import software.amazon.smithy.model.traits.XmlNamespaceTrait;

import java.util.Optional;

/**
 * Resolves XML wire element names and binding traits for restXml and Query XML codecs.
 */
public final class BeamXmlBindingIndex {

    public static final String LIST_MEMBER_ELEMENT = "member";

    private BeamXmlBindingIndex() {}

    public static String memberElementName(MemberShape member) {
        return member.getTrait(XmlNameTrait.class)
                .map(XmlNameTrait::getValue)
                .orElseGet(() -> capitalizeFirst(member.getMemberName()));
    }

    public static String shapeElementName(Shape shape) {
        if (shape instanceof StructureShape || shape instanceof UnionShape) {
            return shape.getTrait(XmlNameTrait.class)
                    .map(XmlNameTrait::getValue)
                    .orElseGet(() -> capitalizeFirst(shape.getId().getName()));
        }
        return capitalizeFirst(shape.getId().getName());
    }

    public static String payloadRootElementName(MemberShape payloadMember, Shape payloadTarget) {
        if (payloadMember.hasTrait(XmlNameTrait.class)) {
            return payloadMember.expectTrait(XmlNameTrait.class).getValue();
        }
        return shapeElementName(payloadTarget);
    }

    public static boolean isXmlAttribute(MemberShape member) {
        return member.hasTrait(XmlAttributeTrait.class);
    }

    public static boolean isXmlFlattened(Shape shape) {
        return shape.hasTrait(XmlFlattenedTrait.class);
    }

    public static Optional<String> xmlNamespaceUri(Shape shape) {
        return shape.getTrait(XmlNamespaceTrait.class).map(XmlNamespaceTrait::getUri);
    }

    public static Optional<String> xmlNamespacePrefix(Shape shape) {
        return shape.getTrait(XmlNamespaceTrait.class).flatMap(XmlNamespaceTrait::getPrefix);
    }

    public static String listItemElementName(ListShape listShape) {
        if (isXmlFlattened(listShape)) {
            return memberElementName(listShape.getMember());
        }
        return LIST_MEMBER_ELEMENT;
    }

    private static String capitalizeFirst(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
