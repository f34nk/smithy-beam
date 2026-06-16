$version: "2"

namespace aws.foo

use aws.api#service

@service(
    sdkId: "Some Value"
    cloudFormationName: "Foo"
    arnNamespace: "myservice"
    cloudTrailEventSource: "myservice.amazon.aws"
    docId: "some-value-2018-03-17"
    endpointPrefix: "my-endpoint"
    cloudWatchNamespace: "AWS/SomeValue"
)
service Foo_123 {
    version: "2018-03-17"
}
