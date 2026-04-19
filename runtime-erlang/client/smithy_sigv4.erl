-module(smithy_sigv4).

%% AWS Signature Version 4 signing implementation
%% Reference: https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html

-export([sign_request/5]).

%% Internal functions (exported for testing)
-export([
    create_canonical_request/4,
    canonicalize_query_string/1,
    canonicalize_headers/1,
    signed_header_list/1,
    hash_sha256/1,
    create_string_to_sign/3,
    credential_scope/3,
    iso8601_datetime/0,
    derive_signing_key/4,
    calculate_signature/2,
    hmac_sha256/2,
    format_auth_header/4
]).

%% @doc Sign an HTTP request using AWS Signature Version 4
%%
%% This function adds the Authorization header and other required headers
%% to authenticate requests to AWS services.
%%
%% @param Method HTTP method (GET, POST, PUT, DELETE, etc.) as binary
%% @param Url Complete URL including protocol, host, path, and query string
%% @param Headers List of header tuples [{Name, Value}] where both are binary
%% @param Body Request body as binary
%% @param Credentials Map containing:
%%   - access_key_id: AWS access key ID (binary)
%%   - secret_access_key: AWS secret access key (binary)
%%   - region: AWS region (binary, e.g., <<"us-east-1">>)
%%   - service: AWS service name (binary, e.g., <<"s3">>)
%%   - session_token (optional): Temporary session token (binary)
%%
%% @returns Updated headers list with Authorization and other AWS headers added
-spec sign_request(
    Method :: binary(),
    Url :: binary(),
    Headers :: [{binary(), binary()}],
    Body :: binary(),
    Client :: map()
) -> {ok, [{binary(), binary()}]} | {error, term()}.
sign_request(Method, Url, Headers, Body, Client) ->
    %% Extract credentials - handle both flat and nested formats
    %% Flat: #{access_key_id => ..., secret_access_key => ...}
    %% Nested: #{credentials => #{access_key_id => ..., secret_access_key => ...}}
    {AccessKeyId, SecretAccessKey, SessionToken} =
        case maps:get(credentials, Client, undefined) of
            undefined ->
                %% Flat format
                {
                    maps:get(access_key_id, Client),
                    maps:get(secret_access_key, Client),
                    maps:get(session_token, Client, undefined)
                };
            Creds when is_map(Creds) ->
                %% Nested format
                {
                    maps:get(access_key_id, Creds),
                    maps:get(secret_access_key, Creds),
                    maps:get(session_token, Creds, undefined)
                }
        end,

    %% Get region (required)
    Region = maps:get(region, Client),

    %% Derive service from URL if not provided
    Service = maps:get(service, Client, derive_service_from_url(Url)),

    %% Generate timestamp
    DateTime = iso8601_datetime(),
    Date = binary:part(DateTime, 0, 8),

    %% Add X-Amz-Date header (required by AWS)
    HeadersWithDate = [{<<"X-Amz-Date">>, DateTime} | Headers],

    %% Add X-Amz-Security-Token if using temporary credentials
    HeadersWithToken =
        case SessionToken of
            undefined ->
                HeadersWithDate;
            Token ->
                [{<<"X-Amz-Security-Token">>, Token} | HeadersWithDate]
        end,

    %% Create canonical request
    CanonicalRequest = create_canonical_request(Method, Url, HeadersWithToken, Body),

    %% Create string to sign
    CredentialScope = credential_scope(DateTime, Region, Service),
    StringToSign = create_string_to_sign(DateTime, CredentialScope, CanonicalRequest),

    %% Calculate signature
    SigningKey = derive_signing_key(SecretAccessKey, Date, Region, Service),
    Signature = calculate_signature(SigningKey, StringToSign),

    %% Format Authorization header
    SignedHeaders = signed_header_list(HeadersWithToken),
    AuthHeader = format_auth_header(AccessKeyId, CredentialScope, SignedHeaders, Signature),

    %% Add Authorization header and return
    {ok, [{<<"Authorization">>, AuthHeader} | HeadersWithToken]}.

%% @doc Derive AWS service name from URL
%%
%% Attempts to determine the service from the URL host.
%% Falls back to "s3" if unable to determine.
%%
%% @param Url The URL to parse
%% @returns Service name as binary
-spec derive_service_from_url(binary()) -> binary().
derive_service_from_url(Url) ->
    %% Parse the URL to get the host
    Host =
        case Url of
            <<"https://", Rest/binary>> -> extract_host(Rest);
            <<"http://", Rest/binary>> -> extract_host(Rest);
            _ -> extract_host(Url)
        end,

    %% Try to extract service from host
    %% Common patterns:
    %% - s3.amazonaws.com -> s3
    %% - bucket.s3.amazonaws.com -> s3
    %% - dynamodb.us-east-1.amazonaws.com -> dynamodb
    %% - sqs.us-east-1.amazonaws.com -> sqs
    %% - moto:5050 -> s3 (local testing)
    %% - localhost:5050 -> s3 (local testing)
    case Host of
        <<"s3.", _/binary>> ->
            <<"s3">>;
        <<"s3-", _/binary>> ->
            <<"s3">>;
        <<_/binary>> = H ->
            %% Check if host contains a known service
            case binary:match(H, <<".s3.">>) of
                {_, _} ->
                    <<"s3">>;
                nomatch ->
                    %% Try to extract service from subdomain
                    case binary:split(H, <<".">>) of
                        [Service, <<"amazonaws">>, <<"com">>] ->
                            Service;
                        [Service, _Region, <<"amazonaws">>, <<"com">>] ->
                            Service;
                        [_Bucket, Service, _Region, <<"amazonaws">>, <<"com">>] ->
                            Service;
                        _ ->
                            %% Default to s3 for local/unknown endpoints
                            <<"s3">>
                    end
            end
    end.

%% @doc Extract host from URL rest (after scheme)
-spec extract_host(binary()) -> binary().
extract_host(UrlRest) ->
    %% Get everything before the first /
    case binary:split(UrlRest, <<"/">>) of
        [Host | _] -> Host;
        [] -> UrlRest
    end.

%%====================================================================
%% Canonical Request Generation
%%====================================================================

%% @doc Create canonical request per AWS SigV4 specification
%%
%% The canonical request consists of:
%% 1. HTTP method
%% 2. Canonical URI (path component)
%% 3. Canonical query string (sorted, encoded)
%% 4. Canonical headers (lowercase, sorted, trimmed)
%% 5. Signed headers (list of header names)
%% 6. Hashed payload (SHA256 hex)
%%
%% @param Method HTTP method as binary (e.g., <<"GET">>)
%% @param Uri Complete URI including path and query
%% @param Headers List of header tuples [{Name, Value}]
%% @param Body Request body as binary
%% @returns Canonical request as binary
-spec create_canonical_request(
    Method :: binary(),
    Uri :: binary(),
    Headers :: [{binary(), binary()}],
    Body :: binary()
) -> binary().
create_canonical_request(Method, Uri, Headers, Body) ->
    %% 1. HTTP method (already canonical - uppercase)
    CanonicalMethod = Method,

    %% 2. Canonical URI (path component, URL-encoded except /)
    ParsedUri = uri_string:parse(Uri),
    CanonicalUri =
        case maps:get(path, ParsedUri, <<>>) of
            %% Empty path becomes /
            <<>> -> <<"/">>;
            %% Path is already URL-encoded
            Path -> Path
        end,

    %% 3. Canonical query string (sorted by key, URL-encoded)
    CanonicalQuery =
        case maps:get(query, ParsedUri, undefined) of
            undefined -> <<>>;
            Query -> canonicalize_query_string(Query)
        end,

    %% 4. Canonical headers (lowercase, sorted, trimmed)
    CanonicalHeaders = canonicalize_headers(Headers),

    %% 5. Signed headers (semicolon-separated list of header names)
    SignedHeaders = signed_header_list(Headers),

    %% 6. Hashed payload (SHA256 in lowercase hex)
    HashedPayload = hash_sha256(Body),

    %% Build canonical request with newlines between components
    <<
        CanonicalMethod/binary,
        "\n",
        CanonicalUri/binary,
        "\n",
        CanonicalQuery/binary,
        "\n",
        CanonicalHeaders/binary,
        "\n",
        SignedHeaders/binary,
        "\n",
        HashedPayload/binary
    >>.

%% @doc Canonicalize query string per AWS SigV4 specification
%%
%% Process:
%% 1. Parse query parameters
%% 2. Sort by parameter name
%% 3. URL-encode names and values
%% 4. Join with &
%%
%% @param Query Query string (may include leading ?)
%% @returns Canonical query string
-spec canonicalize_query_string(binary() | undefined) -> binary().
canonicalize_query_string(undefined) ->
    <<>>;
canonicalize_query_string(<<>>) ->
    <<>>;
canonicalize_query_string(<<"?", Query/binary>>) ->
    canonicalize_query_string(Query);
canonicalize_query_string(Query) when is_binary(Query) ->
    %% Parse query string into list of {Key, Value} pairs
    Params = uri_string:dissect_query(Query),

    %% Sort by key
    SortedParams = lists:sort(fun({K1, _}, {K2, _}) -> K1 =< K2 end, Params),

    %% URL-encode and format as key=value
    EncodedParams = lists:map(
        fun({Key, Value}) ->
            %% AWS requires specific encoding: space as %20, not +
            %% uri_string:quote encodes properly for AWS
            EncodedKey = uri_string:quote(Key),
            EncodedValue = uri_string:quote(Value),
            <<EncodedKey/binary, "=", EncodedValue/binary>>
        end,
        SortedParams
    ),

    %% Join with &
    join_binaries(EncodedParams, <<"&">>).

%% @doc Canonicalize headers per AWS SigV4 specification
%%
%% Process:
%% 1. Convert header names to lowercase
%% 2. Trim whitespace from values
%% 3. Sort by header name
%% 4. Format as name:value\n
%%
%% @param Headers List of header tuples
%% @returns Canonical headers string with trailing newline
-spec canonicalize_headers([{binary(), binary()}]) -> binary().
canonicalize_headers(Headers) ->
    %% Convert to lowercase and trim
    Normalized = lists:map(
        fun({Name, Value}) ->
            LowerName = string:lowercase(Name),
            TrimmedValue = string:trim(Value, both),
            {LowerName, TrimmedValue}
        end,
        Headers
    ),

    %% Sort by header name
    Sorted = lists:sort(fun({N1, _}, {N2, _}) -> N1 =< N2 end, Normalized),

    %% Format as name:value\n for each header
    Formatted = lists:map(
        fun({Name, Value}) ->
            <<Name/binary, ":", Value/binary, "\n">>
        end,
        Sorted
    ),

    %% Join all headers
    iolist_to_binary(Formatted).

%% @doc Create signed headers list per AWS SigV4 specification
%%
%% Creates a semicolon-separated list of lowercase header names, sorted.
%%
%% @param Headers List of header tuples
%% @returns Signed headers string (e.g., <<"host;x-amz-date">>)
-spec signed_header_list([{binary(), binary()}]) -> binary().
signed_header_list(Headers) ->
    %% Extract and lowercase header names
    Names = lists:map(
        fun({Name, _Value}) -> string:lowercase(Name) end,
        Headers
    ),

    %% Sort and remove duplicates
    Sorted = lists:sort(lists:usort(Names)),

    %% Join with semicolon
    join_binaries(Sorted, <<";">>).

%% @doc Calculate SHA256 hash and return as lowercase hex string
%%
%% @param Data Binary data to hash
%% @returns SHA256 hash as lowercase hex binary
-spec hash_sha256(binary()) -> binary().
hash_sha256(Data) ->
    Hash = crypto:hash(sha256, Data),
    %% Convert to lowercase hex string
    binary:encode_hex(Hash, lowercase).

%%====================================================================
%% String to Sign Generation
%%====================================================================

%% @doc Create string to sign from canonical request
%%
%% The string to sign consists of:
%% 1. Algorithm identifier (AWS4-HMAC-SHA256)
%% 2. DateTime in ISO8601 format (YYYYMMDDTHHMMSSZ)
%% 3. Credential scope (YYYYMMDD/region/service/aws4_request)
%% 4. Hashed canonical request (SHA256 hex)
%%
%% Format:
%% AWS4-HMAC-SHA256\n
%% <DateTime>\n
%% <CredentialScope>\n
%% <HashedCanonicalRequest>
%%
%% @param DateTime ISO8601 timestamp (e.g., <<"20230101T120000Z">>)
%% @param CredentialScope Credential scope string
%% @param CanonicalRequest The canonical request to hash
%% @returns String to sign as binary
-spec create_string_to_sign(
    DateTime :: binary(),
    CredentialScope :: binary(),
    CanonicalRequest :: binary()
) -> binary().
create_string_to_sign(DateTime, CredentialScope, CanonicalRequest) ->
    Algorithm = <<"AWS4-HMAC-SHA256">>,
    HashedCanonicalRequest = hash_sha256(CanonicalRequest),

    <<
        Algorithm/binary,
        "\n",
        DateTime/binary,
        "\n",
        CredentialScope/binary,
        "\n",
        HashedCanonicalRequest/binary
    >>.

%% @doc Create credential scope string
%%
%% Format: YYYYMMDD/region/service/aws4_request
%%
%% The credential scope defines the validity of the signature:
%% - Date: Extracted from DateTime (first 8 characters)
%% - Region: AWS region (e.g., us-east-1)
%% - Service: AWS service name (e.g., s3)
%% - Terminator: Always "aws4_request"
%%
%% @param DateTime ISO8601 timestamp (e.g., <<"20230101T120000Z">>)
%% @param Region AWS region as binary
%% @param Service AWS service name as binary
%% @returns Credential scope string
-spec credential_scope(
    DateTime :: binary(),
    Region :: binary(),
    Service :: binary()
) -> binary().
credential_scope(DateTime, Region, Service) ->
    %% Extract date from DateTime (first 8 characters: YYYYMMDD)
    Date = binary:part(DateTime, 0, 8),
    <<Date/binary, "/", Region/binary, "/", Service/binary, "/aws4_request">>.

%% @doc Generate current timestamp in ISO8601 format
%%
%% Format: YYYYMMDDTHHMMSSZ
%% Example: <<"20230101T120000Z">>
%%
%% This is the timestamp format required by AWS SigV4.
%% The timestamp must be:
%% - In UTC (indicated by the Z suffix)
%% - Without separators (no dashes or colons)
%% - With T separator between date and time
%%
%% @returns Current UTC timestamp in ISO8601 format
-spec iso8601_datetime() -> binary().
iso8601_datetime() ->
    %% Get current UTC time
    {{Year, Month, Day}, {Hour, Minute, Second}} = calendar:universal_time(),

    %% Format as YYYYMMDDTHHMMSSZ
    iolist_to_binary(
        io_lib:format(
            "~4..0B~2..0B~2..0BT~2..0B~2..0B~2..0BZ",
            [Year, Month, Day, Hour, Minute, Second]
        )
    ).

%%====================================================================
%% Signature Calculation
%%====================================================================

%% @doc Derive signing key using HMAC-SHA256 key derivation
%%
%% AWS SigV4 uses a chained HMAC key derivation:
%% 1. DateKey = HMAC("AWS4" + SecretAccessKey, Date)
%% 2. RegionKey = HMAC(DateKey, Region)
%% 3. ServiceKey = HMAC(RegionKey, Service)
%% 4. SigningKey = HMAC(ServiceKey, "aws4_request")
%%
%% This creates a key that is specific to:
%% - The secret access key
%% - A specific date (YYYYMMDD)
%% - A specific region
%% - A specific service
%%
%% The signing key is valid for one day and should be cached.
%%
%% @param SecretAccessKey AWS secret access key as binary
%% @param Date Date string in YYYYMMDD format (e.g., <<"20230101">>)
%% @param Region AWS region (e.g., <<"us-east-1">>)
%% @param Service AWS service name (e.g., <<"s3">>)
%% @returns Signing key as binary (32 bytes)
-spec derive_signing_key(
    SecretAccessKey :: binary(),
    Date :: binary(),
    Region :: binary(),
    Service :: binary()
) -> binary().
derive_signing_key(SecretAccessKey, Date, Region, Service) ->
    %% Prepend "AWS4" to secret key and hash with date
    DateKey = hmac_sha256(<<"AWS4", SecretAccessKey/binary>>, Date),

    %% Hash with region
    RegionKey = hmac_sha256(DateKey, Region),

    %% Hash with service
    ServiceKey = hmac_sha256(RegionKey, Service),

    %% Hash with "aws4_request" terminator
    SigningKey = hmac_sha256(ServiceKey, <<"aws4_request">>),

    SigningKey.

%% @doc Calculate signature from string to sign
%%
%% Process:
%% 1. Sign the string to sign with the derived signing key using HMAC-SHA256
%% 2. Convert the signature to lowercase hexadecimal
%%
%% @param SigningKey Derived signing key from derive_signing_key/4
%% @param StringToSign String to sign from create_string_to_sign/3
%% @returns Signature as lowercase hex string (64 characters)
-spec calculate_signature(
    SigningKey :: binary(),
    StringToSign :: binary()
) -> binary().
calculate_signature(SigningKey, StringToSign) ->
    %% Sign the string to sign
    Signature = hmac_sha256(SigningKey, StringToSign),

    %% Convert to lowercase hex
    binary:encode_hex(Signature, lowercase).

%% @doc Calculate HMAC-SHA256
%%
%% Wrapper around crypto:mac/4 for HMAC-SHA256.
%% This is used in both key derivation and signature calculation.
%%
%% @param Key HMAC key as binary
%% @param Data Data to sign as binary
%% @returns HMAC-SHA256 output as binary (32 bytes)
-spec hmac_sha256(binary(), binary()) -> binary().
hmac_sha256(Key, Data) ->
    crypto:mac(hmac, sha256, Key, Data).

%%====================================================================
%% Authorization Header Generation
%%====================================================================

%% @doc Format Authorization header value
%%
%% Creates the Authorization header value according to AWS SigV4 format:
%% AWS4-HMAC-SHA256 Credential=<AccessKeyId>/<CredentialScope>,
%% SignedHeaders=<SignedHeaders>, Signature=<Signature>
%%
%% Example:
%% AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20230101/us-east-1/s3/aws4_request,
%% SignedHeaders=host;x-amz-date, Signature=abc123...
%%
%% @param AccessKeyId AWS access key ID
%% @param CredentialScope Credential scope from credential_scope/3
%% @param SignedHeaders Signed headers list from signed_header_list/1
%% @param Signature Signature from calculate_signature/2
%% @returns Authorization header value as binary
-spec format_auth_header(
    AccessKeyId :: binary(),
    CredentialScope :: binary(),
    SignedHeaders :: binary(),
    Signature :: binary()
) -> binary().
format_auth_header(AccessKeyId, CredentialScope, SignedHeaders, Signature) ->
    <<
        "AWS4-HMAC-SHA256 ",
        "Credential=",
        AccessKeyId/binary,
        "/",
        CredentialScope/binary,
        ", ",
        "SignedHeaders=",
        SignedHeaders/binary,
        ", ",
        "Signature=",
        Signature/binary
    >>.

%%====================================================================
%% Helper Functions
%%====================================================================

%% @doc Join list of binaries with separator
-spec join_binaries([binary()], binary()) -> binary().
join_binaries([], _Sep) ->
    <<>>;
join_binaries([H], _Sep) ->
    H;
join_binaries([H | T], Sep) ->
    iolist_to_binary([H, [[Sep, X] || X <- T]]).
