$version: "2"

namespace smithy.beam.demo.reserved

use aws.protocols#restJson1

string RwString

enum RwKwEnum {
    AFTER
    BEGIN
    CASE
    END
    RECEIVE
}

intEnum RwKwIntEnum {
    CASE = 1
    AFTER = 2
}

union RwKwUnion {
    case: RwString
    end: RwString
}

structure RwKwStruct {
    receive: RwString
    after: RwString
}

string MyType

string My_Type

@restJson1
service ReservedService {
    version: "2026"
    operations: [GetReservedClosure]
}

@readonly
@http(method: "GET", uri: "/reserved", code: 200)
operation GetReservedClosure {
    output: ReservedClosureOutput
}

structure ReservedClosureOutput {
    kwEnum: RwKwEnum
    kwIntEnum: RwKwIntEnum
    kwUnion: RwKwUnion
    kwStruct: RwKwStruct
    myTypeA: MyType
    myTypeB: My_Type
}
