$version: "2"
namespace example.ec2

use aws.protocols#ec2Query
use aws.protocols#ec2QueryName
use aws.auth#sigv4

/// Minimal EC2-like service for testing aws.protocols#ec2Query analysis.
@ec2Query
@sigv4(name: "ec2")
@xmlNamespace(uri: "https://ec2.amazonaws.com/doc/2016-11-15/")
service Ec2Service {
    version: "2016-11-15"
    operations: [DescribeInstances]
}

operation DescribeInstances {
    input: DescribeInstancesInput
    output: DescribeInstancesOutput
}

@input
structure DescribeInstancesInput {
    /// Uses @ec2QueryName to override the wire name.
    @ec2QueryName("Filter")
    filters: String

    maxResults: Integer
}

@output
structure DescribeInstancesOutput {
    reservationSet: String
}
