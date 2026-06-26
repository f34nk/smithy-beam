$version: "2"

namespace smithy.beam.test.eventstream

use aws.protocols#restJson1
use aws.protocols#awsJson1_1
use aws.api#service
use smithy.api#httpPayload
use smithy.api#streaming

@restJson1
service EventStreamRestJsonService {
    version: "2026"
    operations: [StreamEvents]
}

@http(method: "POST", uri: "/events")
operation StreamEvents {
    input: StreamEventsInput
    output: StreamEventsOutput
}

structure StreamEventsInput {}

structure StreamEventsOutput {
    @httpPayload
    events: EventStream
}

@streaming
union EventStream {
    member: MemberEvent
}

structure MemberEvent {
    value: String
}

@awsJson1_1
@service(sdkId: "EventStreamJson11", endpointPrefix: "eventstreamjson11")
service EventStreamAwsJsonService {
    version: "2026"
    operations: [StreamEventsAwsJson]
}

operation StreamEventsAwsJson {
    input: StreamEventsAwsJsonInput
    output: StreamEventsAwsJsonOutput
}

structure StreamEventsAwsJsonInput {}

structure StreamEventsAwsJsonOutput {
    events: EventStream
}
