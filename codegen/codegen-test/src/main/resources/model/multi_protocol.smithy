$version: "2"

namespace smithy.beam.demo.multi_protocol

@protocolDefinition
@trait(selector: "service")
structure ProtoOne {}

@protocolDefinition
@trait(selector: "service")
structure ProtoTwo {}

string S

@ProtoOne
@ProtoTwo
service DualProtocolService {
    version: "2026"
    operations: [GetS]
}

@readonly
operation GetS {
    output: Out
}

structure Out {
    v: S
}
