$version: "2"

namespace smithy.beam.demo.member_order

string MoString

structure MemberOrder {
    zebra: MoString
    alpha: MoString
    mike: MoString
}

structure MemberOrderOutput {
    item: MemberOrder
}

@readonly
operation GetMemberOrder {
    output: MemberOrderOutput
}

service MemberOrderService {
    version: "2026"
    operations: [GetMemberOrder]
}
