$version: "2"

namespace smithy.beam.test.waiter_errors.external

@error("client")
structure ExternalNotFound {
    message: String
}
