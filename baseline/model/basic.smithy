$version: "2"

namespace smithy.beam.demo.basic

use aws.protocols#restJson1
use smithy.api#error
use smithy.api#String
use smithy.api#retryable

// Simple shapes — one named type per Smithy prelude scalar supported by restJson1.
// bigDecimal is omitted here; restJson1 wire encoding requires an explicit opt-in codec.

string BasicString
integer BasicInteger
long BasicLong
float BasicFloat
boolean BasicBoolean
blob BasicBlob
byte BasicByte
short BasicShort
double BasicDouble
bigInteger BasicBigInteger
timestamp BasicTimestamp
document BasicDocument

// ─── Enum ───────────────────────────────────────────────────────────────────
//
// String-keyed enum. Unknown wire values MUST round-trip without failure.

@documentation("Lifecycle state of a basic resource.")
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

@documentation("Item returned in list responses.")
structure BasicItem {
    @documentation("Unique item name.")
    @required
    name: BasicString

    @documentation("Optional count when known.")
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
    operations: [GetTypeClosure, ListBasicItems]
}

// Single operation whose output references every named shape above so Walker and
// DirectedCodegen include them in the service closure (example / codegen demo).

@documentation("""
    Returns every named type in the service closure for the given name.

    ## How to call

    Supply `name` as the path label. Set the `verbose` query parameter when you want
    optional fields in the response.

    ```
    config = %{base_url: "http://localhost:8080"}
    input  = %{name: "example", verbose: true}
    {:ok, output} = BasicServiceClient.get_type_closure(config, input)
    ```

    Failures surface as `{:error, term()}`. A missing name may map to the modeled
    `BasicNotFound` error.
    """)
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

list BasicItemList {
    member: BasicItem
}

// Paginated list operation.

@documentation("""
    Returns basic items across all pages.

    ## How to call

    ```
    config = #{base_url => "http://localhost:8080", http_client => MyHttpMock},
    input = #{page_size => 10},
    {ok, Items} = basic_service_client:list_basic_items(Config, Input).
    ```

    Elixir:

    ```
    config = %{base_url: "http://localhost:8080", http_client: MyHttpMock}
    input = %{page_size: 10}
    {:ok, items} = BasicServiceClient.list_basic_items(config, input)
    ```
    """)
@readonly
@http(method: "GET", uri: "/basic-items", code: 200)
@paginated(
    items: "items"
    inputToken: "nextToken"
    outputToken: "nextToken"
    pageSize: "pageSize"
)
operation ListBasicItems {
    input: ListBasicItemsInput
    output: ListBasicItemsOutput
}

structure ListBasicItemsInput {
    @httpQuery("nextToken")
    nextToken: BasicString

    @httpQuery("pageSize")
    pageSize: BasicInteger
}

structure ListBasicItemsOutput {
    items: BasicItemList
    nextToken: BasicString
}
