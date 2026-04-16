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
    operations: [DescribeInstances, RunInstances]
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

/// Minimal RunInstances-like operation for testing nested @xmlName override collection.
operation RunInstances {
    input:  RunInstancesInput
    output: RunInstancesOutput
}

@input
structure RunInstancesInput {
    /// @xmlName renames TagSpecifications → TagSpecification at the top level.
    @xmlName("TagSpecification")
    TagSpecifications: TagSpecificationList

    /// Plain member without any rename — nested overrides must still be empty.
    MaxCount: Integer
}

list TagSpecificationList {
    member: TagSpecification
}

structure TagSpecification {
    ResourceType: String

    /// @xmlName renames Tags → Tag at the nested level.
    @xmlName("Tag")
    Tags: TagList
}

list TagList {
    member: Tag
}

structure Tag {
    Key:   String
    Value: String
}

@output
structure RunInstancesOutput {
    instanceId: String
}
