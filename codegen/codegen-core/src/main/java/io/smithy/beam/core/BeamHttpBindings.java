package io.smithy.beam.core;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ToShapeId;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

/**
 * Thin wrapper around Smithy {@link HttpBindingIndex} for codegen callers.
 *
 * <p>Use this for URI labels, query strings, headers, payloads, documents, HTTP status codes,
 * response headers, negotiated content types, and timestamp formats derived from bindings.
 */
public final class BeamHttpBindings {

  private final HttpBindingIndex delegate;

  private BeamHttpBindings(HttpBindingIndex delegate) {
    this.delegate = delegate;
  }

  public static BeamHttpBindings from(Model model) {
    return new BeamHttpBindings(HttpBindingIndex.of(model));
  }

  public Map<String, HttpBinding> requestBindings(ToShapeId operation) {
    return delegate.getRequestBindings(operation);
  }

  public Map<String, HttpBinding> responseBindings(ToShapeId operation) {
    return delegate.getResponseBindings(operation);
  }

  public List<HttpBinding> requestBindings(ToShapeId operation, HttpBinding.Location location) {
    return delegate.getRequestBindings(operation, location);
  }

  public List<HttpBinding> responseBindings(ToShapeId operation, HttpBinding.Location location) {
    return delegate.getResponseBindings(operation, location);
  }

  public int httpResponseCode(OperationShape operation) {
    return delegate.getResponseCode(operation);
  }

  public Optional<String> requestContentType(ToShapeId operation, String mediaRangeFromTrait) {
    return delegate.determineRequestContentType(operation, mediaRangeFromTrait);
  }

  public Optional<String> responseContentType(ToShapeId operation, String mediaRangeFromTrait) {
    return delegate.determineResponseContentType(operation, mediaRangeFromTrait);
  }

  public boolean hasRequestBody(ToShapeId operation) {
    return delegate.hasRequestBody(operation);
  }

  public boolean hasResponseBody(ToShapeId operation) {
    return delegate.hasResponseBody(operation);
  }

  public TimestampFormatTrait.Format timestampFormat(
      ToShapeId shapeOrMember,
      HttpBinding.Location location,
      TimestampFormatTrait.Format defaultFormat) {
    return delegate.determineTimestampFormat(shapeOrMember, location, defaultFormat);
  }

  public List<HttpBinding> requestPrefixHeaderBindings(ToShapeId operation) {
    return delegate.getRequestBindings(operation, HttpBinding.Location.PREFIX_HEADERS);
  }

  public List<HttpBinding> responsePrefixHeaderBindings(ToShapeId operation) {
    return delegate.getResponseBindings(operation, HttpBinding.Location.PREFIX_HEADERS);
  }
}
