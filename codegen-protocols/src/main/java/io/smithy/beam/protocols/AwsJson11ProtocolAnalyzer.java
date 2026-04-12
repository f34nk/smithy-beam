package io.smithy.beam.protocols;

import software.amazon.smithy.model.shapes.ShapeId;

/**
 * {@code aws.protocols#awsJson1_1} protocol analyzer.
 *
 * Identical to {@link AwsJsonProtocolAnalyzer} except for the protocol {@link ShapeId} and Content-Type.
 * Used by services such as AWS Lambda, Amazon ECS, and AWS CloudWatch.
 */
public final class AwsJson11ProtocolAnalyzer extends AwsJsonProtocolAnalyzer {

    private static final ShapeId PROTOCOL = ShapeId.from("aws.protocols#awsJson1_1");

    @Override
    protected ShapeId protocol() {
        return PROTOCOL;
    }

    @Override
    protected String baseContentType() {
        return "application/x-amz-json-1.1";
    }
}
