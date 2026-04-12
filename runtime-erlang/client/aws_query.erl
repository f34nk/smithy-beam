-module(aws_query).

%% AWS Query protocol encoding module
%% Used by services like SQS, SNS, RDS, CloudFormation, Elastic Load Balancing
%%
%% Encodes request parameters as form-urlencoded body with:
%% - Action parameter (operation name)
%% - Version parameter (API version)
%% - Flattened nested structures using dot notation
%% - List indexing with .N suffix (1-based numeric suffixes)

-export([encode/2, encode/3]).

%% Internal functions (exported for testing)
-export([
    flatten_params/1,
    flatten_params/2,
    flatten_value/3,
    to_query_key/1,
    to_query_value/1
]).

%% @doc Encode an AWS Query protocol request body
%%
%% @param Action The operation name (e.g., "SendMessage", "CreateQueue")
%% @param Params Map of request parameters
%% @returns URL-encoded query string
-spec encode(binary() | string(), map()) -> binary().
encode(Action, Params) when is_map(Params) ->
    encode(Action, Params, undefined).

%% @doc Encode an AWS Query protocol request body with explicit version
%%
%% @param Action The operation name
%% @param Params Map of request parameters
%% @param Version API version (e.g., "2012-11-05") or undefined for default
%% @returns URL-encoded query string
-spec encode(binary() | string(), map(), binary() | string() | undefined) -> binary().
encode(Action, Params, Version) when is_map(Params) ->
    ActionStr = to_query_value(Action),

    %% Flatten nested parameters
    FlatParams = flatten_params(Params),

    %% Build query parameters list
    BaseParams = [{<<"Action">>, ActionStr}],

    %% Add version if provided
    ParamsWithVersion =
        case Version of
            undefined -> BaseParams;
            V -> [{<<"Version">>, to_query_value(V)} | BaseParams]
        end,

    %% Combine with flattened parameters
    AllParams = ParamsWithVersion ++ FlatParams,

    %% Encode as query string
    encode_query_string(AllParams).

%% @doc Flatten a map of parameters to a list of {Key, Value} tuples
%% Nested structures use dot notation: Parent.Child.Field
%% Lists use 1-based indexing: Items.1, Items.2, etc.
-spec flatten_params(map()) -> [{binary(), binary()}].
flatten_params(Params) when is_map(Params) ->
    flatten_params(Params, <<>>).

%% @doc Flatten parameters with a prefix
-spec flatten_params(map(), binary()) -> [{binary(), binary()}].
flatten_params(Params, Prefix) when is_map(Params) ->
    maps:fold(
        fun(Key, Value, Acc) ->
            KeyBin = to_query_key(Key),
            FullKey =
                case Prefix of
                    <<>> -> KeyBin;
                    _ -> <<Prefix/binary, ".", KeyBin/binary>>
                end,
            flatten_value(FullKey, Value, Acc)
        end,
        [],
        Params
    ).

%% @doc Flatten a single value
-spec flatten_value(binary(), term(), [{binary(), binary()}]) -> [{binary(), binary()}].
flatten_value(Key, Value, Acc) when is_map(Value) ->
    %% Nested map - recursively flatten with key as prefix
    NestedParams = flatten_params(Value, Key),
    NestedParams ++ Acc;
flatten_value(_Key, [], Acc) ->
    %% Empty list - don't add any parameters
    Acc;
flatten_value(Key, Value, Acc) when is_list(Value) ->
    case is_string_list(Value) of
        true ->
            %% It's a string value (non-empty)
            [{Key, list_to_binary(Value)} | Acc];
        false ->
            %% It's a list of items - use 1-based indexing
            {_, IndexedParams} = lists:foldl(
                fun(Item, {Index, InnerAcc}) ->
                    IndexBin = integer_to_binary(Index),
                    IndexedKey = <<Key/binary, ".", IndexBin/binary>>,
                    NewAcc = flatten_value(IndexedKey, Item, InnerAcc),
                    {Index + 1, NewAcc}
                end,
                {1, Acc},
                Value
            ),
            IndexedParams
    end;
flatten_value(Key, Value, Acc) ->
    %% Scalar value
    [{Key, to_query_value(Value)} | Acc].

%% ============================================================================
%% Internal helper functions
%% ============================================================================

%% @doc Convert a key to query parameter key (binary)
-spec to_query_key(atom() | binary() | string()) -> binary().
to_query_key(Key) when is_atom(Key) ->
    atom_to_binary(Key, utf8);
to_query_key(Key) when is_binary(Key) ->
    Key;
to_query_key(Key) when is_list(Key) ->
    list_to_binary(Key).

%% @doc Convert a value to query parameter value (binary)
-spec to_query_value(term()) -> binary().
to_query_value(Value) when is_binary(Value) ->
    Value;
to_query_value(Value) when is_atom(Value) ->
    atom_to_binary(Value, utf8);
to_query_value(Value) when is_list(Value) ->
    list_to_binary(Value);
to_query_value(Value) when is_integer(Value) ->
    integer_to_binary(Value);
to_query_value(Value) when is_float(Value) ->
    float_to_binary(Value);
to_query_value(true) ->
    <<"true">>;
to_query_value(false) ->
    <<"false">>.

%% @doc Check if a list is a string (list of characters)
-spec is_string_list(list()) -> boolean().
is_string_list([]) ->
    true;
is_string_list([H | T]) when is_integer(H), H >= 0, H =< 1114111 ->
    is_string_list(T);
is_string_list(_) ->
    false.

%% @doc Encode a list of {Key, Value} tuples as URL-encoded query string
-spec encode_query_string([{binary(), binary()}]) -> binary().
encode_query_string(Params) ->
    Pairs = lists:map(
        fun({K, V}) ->
            EncodedK = uri_string:quote(K),
            EncodedV = uri_string:quote(V),
            <<EncodedK/binary, "=", EncodedV/binary>>
        end,
        Params
    ),
    iolist_to_binary(lists:join(<<"&">>, Pairs)).
