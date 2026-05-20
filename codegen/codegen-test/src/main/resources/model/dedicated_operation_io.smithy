$version: "2"

namespace smithy.beam.demo.dedicated_io

use smithy.api#Unit

@readonly
operation HealthCheck {
    output: Unit
}

service DedicatedIoService {
    version: "2026"
    operations: [HealthCheck]
}
