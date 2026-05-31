$version: "2"
namespace smithy.beam.test.ec2query

use aws.protocols#ec2Query
use aws.protocols#ec2QueryName
use aws.api#service
use smithy.api#xmlNamespace

@ec2Query
@xmlNamespace(uri: "https://ec2querytest.amazonaws.com/doc/2020-07-02/")
@service(sdkId: "Ec2QueryTest", endpointPrefix: "ec2querytest")
service Ec2QueryService {
    version: "2020-07-02"
    operations: [DescribeInstances]
}

operation DescribeInstances {
    input: DescribeInstancesInput
    output: DescribeInstancesOutput
}

structure DescribeInstancesInput {
    @ec2QueryName("InstanceId")
    instanceId: String
}

structure DescribeInstancesOutput {
    count: Integer
}
