$version: "2"
namespace smithy.beam.test.awsquery

use aws.protocols#awsQuery
use aws.api#service
use smithy.api#xmlNamespace

@awsQuery
@xmlNamespace(uri: "https://querytest.amazonaws.com/doc/2010-05-08/")
@service(sdkId: "QueryTest", endpointPrefix: "querytest")
service QueryService {
    version: "2010-05-08"
    operations: [ListUsers]
}

operation ListUsers {
    input: ListUsersInput
    output: ListUsersOutput
}

structure ListUsersInput {
    pathPrefix: String
}

structure ListUsersOutput {
    users: UserNameList
}

list UserNameList {
    member: String
}
