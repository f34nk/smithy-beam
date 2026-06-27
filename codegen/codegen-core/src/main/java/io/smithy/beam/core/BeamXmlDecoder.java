package io.smithy.beam.core;

import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.XmlFlattenedTrait;
import software.amazon.smithy.model.traits.XmlNameTrait;

/**
 * Wire naming helpers for AWS Query XML responses. Used by language emitters when generating decode
 * functions.
 */
public final class BeamXmlDecoder {

  public static final String ERROR_RESPONSE_ELEMENT = "ErrorResponse";
  public static final String EC2_RESPONSE_ELEMENT = "Response";
  public static final String EC2_ERRORS_ELEMENT = "Errors";
  public static final String ERROR_ELEMENT = "Error";
  public static final String ERROR_CODE_ELEMENT = "Code";
  public static final String ERROR_MESSAGE_ELEMENT = "Message";
  public static final String LIST_MEMBER_ELEMENT = "member";

  private BeamXmlDecoder() {}

  public static String queryResultElementName(OperationShape operation, ServiceShape service) {
    return operation.getId().getName(service) + "Result";
  }

  public static String ec2QueryResultElementName(OperationShape operation, ServiceShape service) {
    return operation.getId().getName(service) + "Response";
  }

  public static String memberElementName(MemberShape member) {
    return member
        .getTrait(XmlNameTrait.class)
        .map(XmlNameTrait::getValue)
        .orElseGet(() -> capitalizeFirst(member.getMemberName()));
  }

  public static boolean isXmlFlattened(Shape shape) {
    return shape.hasTrait(XmlFlattenedTrait.class);
  }

  public static String listItemElementName(ListShape listShape) {
    MemberShape member = listShape.getMember();
    if (isXmlFlattened(listShape) || member.hasTrait(XmlNameTrait.class)) {
      return memberElementName(member);
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
