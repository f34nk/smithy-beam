package io.smithy.beam.core;

import software.amazon.smithy.model.shapes.ShapeId;

public final class BeamProtocolIds {

    private BeamProtocolIds() {}

    public static final ShapeId REST_JSON_1 = ShapeId.from("aws.protocols#restJson1");
    public static final ShapeId REST_XML = ShapeId.from("aws.protocols#restXml");
    public static final ShapeId AWS_JSON_1_0 = ShapeId.from("aws.protocols#awsJson1_0");
    public static final ShapeId AWS_JSON_1_1 = ShapeId.from("aws.protocols#awsJson1_1");
    public static final ShapeId AWS_QUERY = ShapeId.from("aws.protocols#awsQuery");
    public static final ShapeId EC2_QUERY = ShapeId.from("aws.protocols#ec2Query");
}
