package io.smithy.beam.core;

import java.util.Optional;
import java.util.Set;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.EndpointTrait;
import software.amazon.smithy.model.traits.IdempotencyTokenTrait;
import software.amazon.smithy.model.traits.MediaTypeTrait;
import software.amazon.smithy.model.traits.SparseTrait;

/**
 * Tracks which private codec helpers a service needs so generated modules omit unused functions.
 */
public final class BeamCodecHelperNeeds {

  private final boolean queryValues;
  private final boolean uriCoding;
  private final boolean prefixHeaders;
  private final boolean headerValue;
  private final boolean sparseList;
  private final boolean sparseMap;
  private final boolean decodeList;
  private final boolean timestamps;
  private final boolean idempotencyToken;
  private final boolean jsonBody;
  private final boolean contentTypeMatches;
  private final boolean toBinary;

  private BeamCodecHelperNeeds(
      boolean queryValues,
      boolean uriCoding,
      boolean prefixHeaders,
      boolean headerValue,
      boolean sparseList,
      boolean sparseMap,
      boolean decodeList,
      boolean timestamps,
      boolean idempotencyToken,
      boolean jsonBody,
      boolean contentTypeMatches,
      boolean toBinary) {
    this.queryValues = queryValues;
    this.uriCoding = uriCoding;
    this.prefixHeaders = prefixHeaders;
    this.headerValue = headerValue;
    this.sparseList = sparseList;
    this.sparseMap = sparseMap;
    this.decodeList = decodeList;
    this.timestamps = timestamps;
    this.idempotencyToken = idempotencyToken;
    this.jsonBody = jsonBody;
    this.contentTypeMatches = contentTypeMatches;
    this.toBinary = toBinary;
  }

  public static BeamCodecHelperNeeds of(Model model, ServiceShape service) {
    boolean queryValues = false;
    boolean uriCoding = false;
    boolean prefixHeaders = false;
    boolean headerValue = false;
    boolean contentTypeMatches = false;
    boolean jsonBody = false;
    boolean toBinary = false;

    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    TopDownIndex topDown = TopDownIndex.of(model);
    BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);

    Optional<ShapeId> protocol = BeamProtocolResolver.resolveServiceProtocol(model, service);
    if (protocol.isPresent()
        && (BeamProtocolIds.AWS_JSON_1_0.equals(protocol.get())
            || BeamProtocolIds.AWS_JSON_1_1.equals(protocol.get()))) {
      // AWS JSON request/response decoders always call decode_json_body.
      jsonBody = true;
    }

    for (OperationShape op : topDown.getContainedOperations(service)) {
      if (!httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY).isEmpty()
          || !httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY_PARAMS).isEmpty()) {
        queryValues = true;
        toBinary = true;
      }
      if (!httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL).isEmpty()) {
        uriCoding = true;
        toBinary = true;
      }
      if (!httpIndex.getRequestBindings(op, HttpBinding.Location.PREFIX_HEADERS).isEmpty()
          || !httpIndex.getResponseBindings(op, HttpBinding.Location.PREFIX_HEADERS).isEmpty()) {
        prefixHeaders = true;
        toBinary = true;
      }
      if (!httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER).isEmpty()
          || !httpIndex.getResponseBindings(op, HttpBinding.Location.HEADER).isEmpty()) {
        headerValue = true;
      }
      if (responsePayloadRequiresContentTypeCheck(model, httpIndex, op)) {
        contentTypeMatches = true;
      }
      if (operationNeedsJsonBodyHelper(model, httpIndex, op)) {
        jsonBody = true;
      }
      if (!hostLabelIndex.hostLabelMembers(op).isEmpty() && op.hasTrait(EndpointTrait.class)) {
        uriCoding = true;
        toBinary = true;
      }
    }

    if (BeamEventStreamIndex.of(model).serviceHasEventStreams(service)) {
      // Elixir event-stream helpers call header_value.
      headerValue = true;
    }

    boolean sparseList = false;
    boolean sparseMap = false;
    boolean decodeList = false;
    boolean timestamps = false;
    boolean idempotencyToken = false;

    Set<Shape> closure = new Walker(model).walkShapes(service);
    for (Shape shape : closure) {
      if (shape instanceof TimestampShape) {
        timestamps = true;
      }
      if (shape instanceof ListShape list) {
        if (list.hasTrait(SparseTrait.class)) {
          sparseList = true;
        } else {
          decodeList = true;
        }
      }
      if (shape instanceof MapShape map && map.hasTrait(SparseTrait.class)) {
        sparseMap = true;
      }
      if (shape instanceof StructureShape structure) {
        for (MemberShape member : structure.members()) {
          if (member.hasTrait(IdempotencyTokenTrait.class)) {
            idempotencyToken = true;
          }
        }
      }
    }

    return new BeamCodecHelperNeeds(
        queryValues,
        uriCoding,
        prefixHeaders,
        headerValue,
        sparseList,
        sparseMap,
        decodeList,
        timestamps,
        idempotencyToken,
        jsonBody,
        contentTypeMatches,
        toBinary);
  }

  private static boolean responsePayloadRequiresContentTypeCheck(
      Model model, HttpBindingIndex httpIndex, OperationShape op) {
    var respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);
    if (respPayload.isEmpty()) {
      return false;
    }
    Shape target = model.expectShape(respPayload.get(0).getMember().getTarget());
    return target.hasTrait(MediaTypeTrait.class);
  }

  /**
   * True when generated codecs call {@code decode_json_body}: error dispatch, document/payload JSON
   * decode, or AWS JSON request/response body decode.
   */
  private static boolean operationNeedsJsonBodyHelper(
      Model model, HttpBindingIndex httpIndex, OperationShape op) {
    if (!op.getErrors().isEmpty()) {
      return true;
    }
    if (!httpIndex.getRequestBindings(op, HttpBinding.Location.DOCUMENT).isEmpty()
        || !httpIndex.getResponseBindings(op, HttpBinding.Location.DOCUMENT).isEmpty()) {
      return true;
    }
    return jsonPayloadNeedsBodyDecode(model, httpIndex, op);
  }

  private static boolean jsonPayloadNeedsBodyDecode(
      Model model, HttpBindingIndex httpIndex, OperationShape op) {
    var payload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);
    if (payload.isEmpty()) {
      return false;
    }
    Shape target = model.expectShape(payload.get(0).getMember().getTarget());
    if (target.hasTrait(MediaTypeTrait.class)) {
      return false;
    }
    return !(target instanceof UnionShape
        && BeamEventStreamIndex.of(model).isEventStreamUnion((UnionShape) target));
  }

  public boolean queryValues() {
    return queryValues;
  }

  public boolean uriCoding() {
    return uriCoding;
  }

  public boolean prefixHeaders() {
    return prefixHeaders;
  }

  public boolean headerValue() {
    return headerValue;
  }

  public boolean sparseList() {
    return sparseList;
  }

  public boolean sparseMap() {
    return sparseMap;
  }

  public boolean decodeList() {
    return decodeList;
  }

  public boolean timestamps() {
    return timestamps;
  }

  public boolean idempotencyToken() {
    return idempotencyToken;
  }

  public boolean jsonBody() {
    return jsonBody;
  }

  public boolean contentTypeMatches() {
    return contentTypeMatches;
  }

  public boolean toBinary() {
    return toBinary;
  }
}
