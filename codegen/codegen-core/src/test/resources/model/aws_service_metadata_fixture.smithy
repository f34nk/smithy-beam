$version: "2"

namespace smithy.beam.test

use aws.api#service

@service(sdkId: "MetadataSdk", endpointPrefix: "metadata", arnNamespace: "metadatasign")
service MetadataService {
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
