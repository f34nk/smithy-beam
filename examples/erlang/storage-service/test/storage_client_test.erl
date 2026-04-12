-module(storage_client_test).
-include_lib("eunit/include/eunit.hrl").

%% Verify the generated module loads.
module_loads_test() ->
    ?assert(code:ensure_loaded(storage_client) =:= {module, storage_client}).

%% new/1 returns {ok, Config}.
new_client_test() ->
    Config = #{endpoint => <<"http://localhost:8080">>},
    {ok, Client} = storage_client:new(Config),
    ?assertEqual(Config, Client).

%% The generated file contains the union type definition.
union_type_defined_test() ->
    ModulePath = "src/generated/storage_client.erl",
    {ok, Content} = file:read_file(ModulePath),
    ?assert(binary:match(Content, <<"-type storage_type()">>) =/= nomatch).

%% All union member struct types are defined.
union_member_types_test() ->
    ModulePath = "src/generated/storage_client.erl",
    {ok, Content} = file:read_file(ModulePath),
    ?assert(binary:match(Content, <<"-type s3_storage()">>) =/= nomatch),
    ?assert(binary:match(Content, <<"-type glacier_storage()">>) =/= nomatch),
    ?assert(binary:match(Content, <<"-type efs_storage()">>) =/= nomatch).

%% Enum encode round-trip for GlacierRetrievalOption.
encode_enum_test() ->
    ?assertEqual(<<"expedited">>, storage_client:encode_glacier_retrieval_option('expedited')),
    ?assertEqual(<<"standard">>,  storage_client:encode_glacier_retrieval_option('standard')),
    ?assertEqual(<<"bulk">>,      storage_client:encode_glacier_retrieval_option('bulk')).

%% Enum decode round-trip for GlacierRetrievalOption.
decode_enum_test() ->
    ?assertEqual({ok, 'expedited'}, storage_client:decode_glacier_retrieval_option(<<"expedited">>)),
    ?assertEqual({ok, 'standard'},  storage_client:decode_glacier_retrieval_option(<<"standard">>)),
    ?assertEqual({ok, 'bulk'},      storage_client:decode_glacier_retrieval_option(<<"bulk">>)),
    ?assertMatch({error, {invalid_enum_value, _}},
                 storage_client:decode_glacier_retrieval_option(<<"invalid">>)).

%% Union encode: each variant wraps its value in the correct key.
encode_union_s3_test() ->
    S3Val = #{<<"bucket">> => <<"my-bucket">>, <<"region">> => <<"us-east-1">>},
    Encoded = storage_client:encode_storage_type({s3, S3Val}),
    ?assertEqual(#{<<"s3">> => S3Val}, Encoded).

encode_union_glacier_test() ->
    GlacierVal = #{<<"vault">> => <<"my-vault">>, <<"region">> => <<"us-east-1">>},
    Encoded = storage_client:encode_storage_type({glacier, GlacierVal}),
    ?assertEqual(#{<<"glacier">> => GlacierVal}, Encoded).

encode_union_unknown_test() ->
    Encoded = storage_client:encode_storage_type({unknown, <<"something">>}),
    ?assertEqual(#{<<"unknown">> => <<"something">>}, Encoded).

%% Union decode: picks the first present key.
decode_union_s3_test() ->
    S3Val = #{<<"bucket">> => <<"my-bucket">>},
    Decoded = storage_client:decode_storage_type(#{<<"s3">> => S3Val}),
    ?assertEqual({s3, S3Val}, Decoded).

decode_union_glacier_test() ->
    GlacierVal = #{<<"vault">> => <<"my-vault">>},
    Decoded = storage_client:decode_storage_type(#{<<"glacier">> => GlacierVal}),
    ?assertEqual({glacier, GlacierVal}, Decoded).

decode_union_efs_test() ->
    EfsVal = #{<<"fileSystemId">> => <<"fs-123">>},
    Decoded = storage_client:decode_storage_type(#{<<"efs">> => EfsVal}),
    ?assertEqual({efs, EfsVal}, Decoded).

decode_union_unknown_test() ->
    Map = #{<<"other">> => <<"value">>},
    ?assertEqual({unknown, Map}, storage_client:decode_storage_type(Map)).

%% Validation: CreateStorageLocationInput requires name and storageType.
validate_create_input_missing_test() ->
    {error, {missing_required_fields, Missing}} =
        storage_client:validate_create_storage_location_input(#{}),
    ?assert(lists:member(<<"name">>, Missing) orelse lists:member(<<"storageType">>, Missing)).

validate_create_input_ok_test() ->
    Input = #{
        <<"name">>        => <<"my-store">>,
        <<"storageType">> => #{<<"s3">> => #{}}
    },
    ?assertEqual(ok, storage_client:validate_create_storage_location_input(Input)).

%% Validation: GetStorageLocationInput requires locationId.
validate_get_input_missing_test() ->
    {error, {missing_required_fields, Missing}} =
        storage_client:validate_get_storage_location_input(#{}),
    ?assert(lists:member(<<"locationId">>, Missing)).

validate_get_input_ok_test() ->
    Input = #{<<"locationId">> => <<"loc-abc">>},
    ?assertEqual(ok, storage_client:validate_get_storage_location_input(Input)).

%% parse_error/2 handles the StorageLocationNotFound (404) error.
parse_error_known_test() ->
    Body = <<"{}">>,
    Result = storage_client:parse_error(404, Body),
    ?assertMatch({error, {storage_location_not_found, _}}, Result).

parse_error_unknown_test() ->
    Body = <<"{}">>,
    Result = storage_client:parse_error(500, Body),
    ?assertMatch({error, {http_error, 500, _}}, Result).
