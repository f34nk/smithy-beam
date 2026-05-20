$version: "2"

namespace smithy.beam.demo.streaming_blob

use smithy.api#default
use smithy.api#streaming

@streaming
blob SbStreamingPayload

blob SbBlob

structure SbBody {
    @default("")
    data: SbStreamingPayload
    checksum: SbBlob
}

@readonly
operation GetSbBody {
    output: SbBody
}

service StreamingBlobService {
    version: "2026"
    operations: [GetSbBody]
}
