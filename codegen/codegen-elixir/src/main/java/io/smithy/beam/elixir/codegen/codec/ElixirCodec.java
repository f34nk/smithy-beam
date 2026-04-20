package io.smithy.beam.elixir.codegen.codec;

import io.smithy.beam.elixir.codegen.ElixirContext;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Strategy interface for Elixir protocol codec implementations.
 *
 * <p>A codec is responsible for emitting the request serialisation and
 * response / error deserialisation logic for a specific wire format
 * (JSON, XML, Query, EC2-Query). Stateless; one instance is reused
 * across all operations of a service.
 *
 * <p>Concrete implementations live in this package ({@code JsonCodec},
 * {@code XmlCodec}, {@code QueryCodec}, {@code Ec2QueryCodec}). They are
 * returned by {@code DefaultElixirProtocolIntegration#codec()} and
 * consumed by the section interceptors that populate
 * {@link io.smithy.beam.elixir.codegen.sections.OperationRequestSection},
 * {@link io.smithy.beam.elixir.codegen.sections.OperationResponseSection}, and
 * {@link io.smithy.beam.elixir.codegen.sections.OperationErrorSection}.
 */
public interface ElixirCodec {

    /**
     * Emits the Elixir expression that serialises the operation input into
     * the wire-format request body (e.g. {@code Jason.encode!(body)}).
     *
     * @param w   the writer to append to
     * @param ctx the current codegen context
     * @param op  the operation whose input is being serialised
     */
    void writeRequestEncode(ElixirWriter w, ElixirContext ctx, OperationShape op);

    /**
     * Emits the Elixir expression that deserialises a successful response body
     * into the operation output struct.
     *
     * @param w   the writer to append to
     * @param ctx the current codegen context
     * @param op  the operation whose output is being deserialised
     */
    void writeResponseDecode(ElixirWriter w, ElixirContext ctx, OperationShape op);

    /**
     * Emits the Elixir expression that deserialises an error response body
     * into the appropriate error struct.
     *
     * @param w   the writer to append to
     * @param ctx the current codegen context
     * @param op  the operation whose errors are being deserialised
     */
    void writeErrorDecode(ElixirWriter w, ElixirContext ctx, OperationShape op);

    /**
     * Returns the MIME content-type string emitted in the {@code Content-Type}
     * request header for this codec (e.g. {@code "application/json"} or
     * {@code "application/x-www-form-urlencoded"}).
     *
     * @return the content-type value
     */
    String contentType();
}
