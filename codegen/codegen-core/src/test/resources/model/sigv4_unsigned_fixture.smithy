$version: "2"
namespace smithy.beam.test.sigv4

use aws.auth#sigv4
use aws.auth#unsignedPayload
use aws.api#service
use aws.protocols#restJson1

@restJson1
@sigv4(name: "sigv4test")
@service(sdkId: "SigV4UnsignedTest", endpointPrefix: "sigv4test")
service Sigv4UnsignedTestService {
    version: "2026"
    operations: [Ping, Upload]
}

@readonly
@http(method: "GET", uri: "/ping", code: 200)
operation Ping {
    output: PingOutput
}

@unsignedPayload
@http(method: "PUT", uri: "/upload", code: 200)
operation Upload {
    input: UploadInput
}

structure PingOutput {
    ok: Boolean
}

structure UploadInput {
    @required
    @httpPayload
    body: Blob
}
