package io.smithy.beam.erlang.codegen.codec;

import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Strategy interface for Erlang protocol codec implementations.
 *
 * <p>A codec is responsible for emitting the request serialisation and
 * response / error deserialisation logic for a specific wire format
 * (JSON, XML, Query, EC2-Query). Stateless; one instance is reused
 * across all operations of a service.
 *
 * <p>Concrete implementations live in this package ({@code JsonCodec},
 * {@code XmlCodec}, {@code QueryCodec}, {@code Ec2QueryCodec}). They are
 * returned by {@code DefaultErlangProtocolIntegration#codec()} and
 * consumed by the section interceptors that populate
 * {@link io.smithy.beam.erlang.codegen.sections.OperationRequestSection},
 * {@link io.smithy.beam.erlang.codegen.sections.OperationResponseSection}, and
 * {@link io.smithy.beam.erlang.codegen.sections.OperationErrorSection}.
 */
public interface ErlangCodec {

    /**
     * Emits the Erlang expression that serialises the operation input into
     * the wire-format request body (e.g. {@code jsx:encode(Body)}).
     *
     * @param w   the writer to append to
     * @param ctx the current codegen context
     * @param op  the operation whose input is being serialised
     */
    void writeRequestEncode(ErlangWriter w, ErlangContext ctx, OperationShape op);

    /**
     * Emits the Erlang expression that deserialises a successful response body
     * into the operation output record.
     *
     * @param w   the writer to append to
     * @param ctx the current codegen context
     * @param op  the operation whose output is being deserialised
     */
    void writeResponseDecode(ErlangWriter w, ErlangContext ctx, OperationShape op);

    /**
     * Emits the Erlang expression that deserialises an error response body
     * into the appropriate error record.
     *
     * @param w   the writer to append to
     * @param ctx the current codegen context
     * @param op  the operation whose errors are being deserialised
     */
    void writeErrorDecode(ErlangWriter w, ErlangContext ctx, OperationShape op);

    /**
     * Returns the MIME content-type string emitted in the {@code Content-Type}
     * request header for this codec (e.g. {@code "application/json"} or
     * {@code "application/x-www-form-urlencoded"}).
     *
     * @return the content-type value
     */
    String contentType();
}
