$version: "2"
namespace smithy.beam.test.sigv4

use aws.auth#sigv4
use aws.api#service
use aws.protocols#restJson1

@restJson1
@sigv4(name: "sigv4test")
@service(sdkId: "SigV4Test", endpointPrefix: "sigv4test")
service Sigv4TestService {
    version: "2026"
    operations: [Ping]
}

@readonly
@http(method: "GET", uri: "/ping", code: 200)
operation Ping {
    output: PingOutput
}

structure PingOutput {
    ok: Boolean
}
