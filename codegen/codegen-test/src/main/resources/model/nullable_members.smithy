$version: "2"

namespace smithy.beam.demo.nullable_members

string NmString
integer NmInteger

list NmList {
    member: NmString
}

structure MixedNullable {
    @required
    label: NmString

    count: NmInteger

    tags: NmList
}

structure MixedNullableOutput {
    item: MixedNullable
}

@readonly
operation GetMixedNullable {
    output: MixedNullableOutput
}

service NullableMembersService {
    version: "2026"
    operations: [GetMixedNullable]
}
