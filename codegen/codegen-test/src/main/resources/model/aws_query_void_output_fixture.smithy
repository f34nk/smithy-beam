$version: "2"
namespace smithy.beam.test.awsqueryvoid

use aws.protocols#awsQuery
use aws.api#service
use smithy.api#Unit
use smithy.api#xmlNamespace

@awsQuery
@xmlNamespace(uri: "https://queryvoid.amazonaws.com/doc/2010-05-08/")
@service(sdkId: "QueryVoidTest", endpointPrefix: "queryvoid")
service QueryVoidService {
    version: "2010-05-08"
    operations: [DeleteUser]
}

operation DeleteUser {
    input: DeleteUserInput
    output: Unit
}

structure DeleteUserInput {
    userName: String
}
