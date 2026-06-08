$version: "2"

namespace smithy.beam.demo.custom_protocol

use smithy.api#String
use smithy.api#http
use smithy.api#httpPayload
use smithy.api#httpQuery
use smithy.api#protocolDefinition
use smithy.api#trait

@trait(selector: "service")
@protocolDefinition
structure ndjsonProtocol {}

@http(method: "GET", uri: "/hello")
@readonly
operation SayHello {
    input: SayHelloInput
    output: SayHelloOutput
}

structure SayHelloInput {
    @httpQuery("name")
    name: String
}

structure SayHelloOutput {
    @httpPayload
    message: String
}

// The service omits @ndjsonProtocol; smithy-build.json selects the wire protocol.
service GreetingService {
    version: "2026"
    operations: [SayHello]
}
