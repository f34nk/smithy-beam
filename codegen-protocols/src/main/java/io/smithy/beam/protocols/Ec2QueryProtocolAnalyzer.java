package io.smithy.beam.protocols;

import software.amazon.smithy.model.shapes.ShapeId;

/**
 * {@code aws.protocols#ec2Query} protocol analyzer.
 *
 * <p>Nearly identical to {@link AwsQueryProtocolAnalyzer}: all operations are {@code POST /} with
 * form-encoded bodies and XML responses.  The difference is in how member names are serialized on
 * the wire — EC2 uses title-cased names (e.g. {@code maxResults → MaxResults}) or an explicit
 * {@code @ec2QueryName} value.  That transformation is performed by the {@code aws_query.erl}
 * runtime encoder at call time, so the analyzer simply records all input members as body members
 * (same as awsQuery) and returns the correct protocol {@link ShapeId}.
 */
public final class Ec2QueryProtocolAnalyzer extends AwsQueryProtocolAnalyzer {

    private static final ShapeId PROTOCOL = ShapeId.from("aws.protocols#ec2Query");

    @Override
    protected ShapeId protocol() {
        return PROTOCOL;
    }
}
