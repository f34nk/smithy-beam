$version: "2"

namespace smithy.beam.demo.basic

use aws.protocols#restJson1
use smithy.api#error
use smithy.api#String
use smithy.api#retryable

// Simple shapes — one named type per Smithy prelude scalar.

string BasicString
integer BasicInteger
long BasicLong
float BasicFloat
boolean BasicBoolean
blob BasicBlob
byte BasicByte
short BasicShort
double BasicDouble
bigDecimal BasicBigDecimal
bigInteger BasicBigInteger
timestamp BasicTimestamp
document BasicDocument

// ─── Enum ───────────────────────────────────────────────────────────────────
//
// String-keyed enum. Unknown wire values MUST round-trip without failure.

enum BasicStatus {
    ACTIVE
    INACTIVE
    PENDING
}

// ─── IntEnum ────────────────────────────────────────────────────────────────
//
// Integer-keyed enum. Unknown wire values MUST round-trip without failure.

intEnum BasicPriority {
    LOW = 1
    MEDIUM = 2
    HIGH = 3
}

// ─── List ───────────────────────────────────────────────────────────────────

list BasicList {
    member: BasicString
}

// ─── Map ────────────────────────────────────────────────────────────────────

map BasicMap {
    key: BasicString
    value: BasicString
}

// ─── Union ──────────────────────────────────────────────────────────────────
//
// Exactly one member is active. Unknown variants MUST be preserved.

union BasicUnion {
    text: BasicString
    number: BasicInteger
    flag: BasicBoolean
}

// ─── Structure ──────────────────────────────────────────────────────────────
//
// Required members: typed without nil.
// Optional members: typed | nil  (NullableIndex rule).

structure BasicItem {
    @required
    name: BasicString

    count: BasicInteger
}

@error("client")
@retryable
structure BasicNotFound {
    message: String
}

// Minimal service so Erlang type codegen can run CodegenDirector with a service scope.

@restJson1
service BasicService {
    version: "2026"
    operations: [GetTypeClosure]
}

// Single operation whose output references every named shape above so Walker and
// DirectedCodegen include them in the service closure (example / codegen demo).

@readonly
@http(method: "GET", uri: "/types/{name}", code: 200)
operation GetTypeClosure {
    input: GetTypeClosureInput
    output: TypeClosureOutput
    errors: [BasicNotFound]
}

structure GetTypeClosureInput {
    @required
    @httpLabel
    name: BasicString

    @httpQuery("verbose")
    verbose: BasicBoolean

    @httpHeader("X-Request-Tag")
    requestTag: BasicString
}

structure TypeClosureOutput {
    @httpHeader("ETag")
    etag: BasicString

    basicString: BasicString
    basicInteger: BasicInteger
    basicLong: BasicLong
    basicFloat: BasicFloat
    basicBoolean: BasicBoolean
    basicBlob: BasicBlob
    basicByte: BasicByte
    basicShort: BasicShort
    basicDouble: BasicDouble
    basicBigDecimal: BasicBigDecimal
    basicBigInteger: BasicBigInteger
    basicTimestamp: BasicTimestamp
    basicDocument: BasicDocument
    basicStatus: BasicStatus
    basicPriority: BasicPriority
    basicList: BasicList
    basicMap: BasicMap
    basicUnion: BasicUnion
    basicItem: BasicItem
}
