$version: "2"

namespace smithy.beam.test.waiter_errors

use aws.protocols#restJson1
use smithy.waiters#waitable

@restJson1
service WaiterExternalErrorService {
    version: "2026"
    operations: [PollResource]
}

@waitable(
    ResourceExists: {
        acceptors: [
            {
                state: "success"
                matcher: {
                    success: true
                }
            }
            {
                state: "retry"
                matcher: {
                    errorType: "smithy.beam.test.waiter_errors.external#ExternalNotFound"
                }
            }
        ]
    }
)
@readonly
@http(method: "GET", uri: "/resource")
operation PollResource {
    input: PollResourceInput
    output: PollResourceOutput
}

structure PollResourceInput {}

structure PollResourceOutput {}
