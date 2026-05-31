$version: "2"

namespace smithy.beam.test.awsservice

use aws.api#service

@service(sdkId: "TestSdk", endpointPrefix: "testprefix", arnNamespace: "testsign")
service AwsMetadataService {
    version: "2026"
    operations: [Ping]
}

@service(sdkId: "OnlySdk")
service MinimalAwsService {
    version: "2026"
    operations: [Ping]
}

@readonly
operation Ping {
    output: PingOutput
}

structure PingOutput {
    ok: Boolean
}
