package io.smithy.beam.core.protocol;

import io.smithy.beam.core.ir.ErrorCodeStrategy;
import io.smithy.beam.core.ir.HeaderBinding;
import io.smithy.beam.core.ir.OperationSpec;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.List;

public interface ProtocolAnalyzer {

    ShapeId getProtocol();

    OperationSpec analyzeClientOperation(OperationShape op, Model model, ServiceShape service);

    default OperationSpec analyzeServerOperation(OperationShape op, Model model, ServiceShape service) {
        throw new UnsupportedOperationException("Server generation not supported for: " + getProtocol());
    }

    String contentType(ServiceShape service);

    /**
     * Returns the dominant {@link ErrorCodeStrategy} for this protocol.
     *
     * <p>The strategy governs how the generated client identifies which modelled error a response
     * represents: by HTTP status code ({@link ErrorCodeStrategy#REST_JSON}) or by an error-code
     * string in the response body ({@link ErrorCodeStrategy#AWS_JSON},
     * {@link ErrorCodeStrategy#AWS_QUERY}, {@link ErrorCodeStrategy#REST_XML}).
     *
     * <p>The default returns {@link ErrorCodeStrategy#REST_JSON}; each concrete analyzer overrides
     * to return its own value.  The pipeline uses this method rather than reading the strategy
     * from the first {@link OperationSpec}, decoupling error dispatch from operation analysis.
     */
    default ErrorCodeStrategy errorStrategy(ServiceShape service) {
        return ErrorCodeStrategy.REST_JSON;
    }

    /**
     * Returns the Smithy member name of the {@code @httpPayload}-annotated member in the given
     * output shape, or {@code null} if the protocol does not use an explicit payload binding.
     *
     * <p>When non-null, the HTTP response body is placed as-is (without JSON/XML decoding) under
     * this key in the result map by the generated client code.
     *
     * <p>The default returns {@code null}; override in protocols that support
     * {@code @httpPayload} on output shapes (e.g. restJson1, restXml).
     */
    default String responsePayloadMember(StructureShape output) {
        return null;
    }

    /**
     * Returns the Smithy member name of the {@code @httpResponseCode}-annotated member in the
     * given output shape, or {@code null} if the protocol does not capture the HTTP status code
     * in the output map.
     *
     * <p>The default returns {@code null}; override in protocols that support
     * {@code @httpResponseCode} on output shapes (e.g. restJson1, restXml).
     */
    default String responseCodeMember(StructureShape output) {
        return null;
    }

    /**
     * Returns {@link HeaderBinding} entries for every {@code @httpHeader}-annotated member in
     * the given output shape. These represent response headers the generated client should
     * extract and place into the result map.
     *
     * <p>The default returns an empty list; override in protocols that support
     * {@code @httpHeader} on output shapes (e.g. restJson1, restXml).
     */
    default List<HeaderBinding> responseHeaders(StructureShape output) {
        return List.of();
    }

    default boolean requiresXmlRuntime() {
        return false;
    }

    default boolean requiresQueryRuntime() {
        return false;
    }

    default boolean requiresS3Runtime(ServiceShape service) {
        return false;
    }
}
