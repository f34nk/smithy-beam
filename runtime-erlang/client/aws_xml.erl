-module(aws_xml).

%% AWS XML encoding/decoding module for REST-XML protocol support
%% Used by services like S3, CloudFront, Route 53, SES
%%
%% Uses xmerl for XML processing (Erlang/OTP standard library)

%% Include xmerl records (must be before code that uses them)
-include_lib("xmerl/include/xmerl.hrl").

-export([encode/1, encode/2, decode/1]).

%% Internal functions (exported for testing)
-export([
    encode_element/2,
    encode_value/1,
    xml_to_map/1,
    extract_text/1,
    extract_children/1
]).

%% @doc Encode an Erlang map to XML binary
%%
%% @param Map The map to encode
%% @returns XML as iolist (can be used directly with httpc)
-spec encode(map()) -> iolist().
encode(Map) when is_map(Map) ->
    encode(Map, undefined).

%% @doc Encode an Erlang map to XML binary with a root element name
%%
%% @param Map The map to encode
%% @param RootName The name of the root XML element (atom or binary)
%% @returns XML as iolist
-spec encode(map(), atom() | binary() | undefined) -> iolist().
encode(Map, undefined) when is_map(Map) ->
    %% No root element, encode map contents directly
    Elements = maps:fold(
        fun(K, V, Acc) ->
            [encode_element(K, V) | Acc]
        end,
        [],
        Map
    ),
    lists:reverse(Elements);
encode(Map, RootName) when is_map(Map) ->
    %% Wrap in root element
    RootAtom = to_atom(RootName),
    Elements = maps:fold(
        fun(K, V, Acc) ->
            [encode_element(K, V) | Acc]
        end,
        [],
        Map
    ),
    Content = lists:reverse(Elements),
    xmerl:export_simple([{RootAtom, [], Content}], xmerl_xml).

%% @doc Encode a single element (key-value pair)
-spec encode_element(atom() | binary() | string(), term()) -> tuple().
encode_element(Key, Value) ->
    KeyAtom = to_atom(Key),
    {KeyAtom, [], encode_value(Value)}.

%% @doc Encode a value to XML-compatible format
-spec encode_value(term()) -> [tuple()] | list().
encode_value(Value) when is_map(Value) ->
    %% Nested map - recursively encode
    maps:fold(
        fun(K, V, Acc) ->
            [encode_element(K, V) | Acc]
        end,
        [],
        Value
    );
encode_value(Value) when is_list(Value) ->
    case is_string_list(Value) of
        true ->
            %% It's a string, return as text content
            [Value];
        false ->
            %% It's a list of items - encode each item
            lists:map(
                fun(Item) ->
                    case is_map(Item) of
                        true ->
                            %% For list of maps, encode as nested content
                            encode_value(Item);
                        false ->
                            encode_value(Item)
                    end
                end,
                Value
            )
    end;
encode_value(Value) when is_binary(Value) ->
    [binary_to_list(Value)];
encode_value(Value) when is_integer(Value) ->
    [integer_to_list(Value)];
encode_value(Value) when is_float(Value) ->
    [float_to_list(Value)];
%% Specific atom values must come before generic is_atom clause
encode_value(true) ->
    ["true"];
encode_value(false) ->
    ["false"];
encode_value(undefined) ->
    [];
encode_value(Value) when is_atom(Value) ->
    [atom_to_list(Value)].

%% @doc Decode XML binary to Erlang map
%%
%% @param XmlBinary XML content as binary or string
%% @returns {ok, Map} | {error, Reason}
-spec decode(binary() | string()) -> {ok, map()} | {error, term()}.
decode(XmlBinary) when is_binary(XmlBinary) ->
    decode(binary_to_list(XmlBinary));
decode(XmlString) when is_list(XmlString) ->
    try
        {Xml, _Rest} = xmerl_scan:string(XmlString, [{quiet, true}]),
        {ok, xml_to_map(Xml)}
    catch
        _:Reason ->
            {error, {xml_parse_error, Reason}}
    end.

%% @doc Convert parsed XML element to map
-spec xml_to_map(tuple()) -> map().
xml_to_map(#xmlElement{name = Name, content = Content}) ->
    Children = extract_children(Content),
    case Children of
        [] ->
            %% No child elements, check for text content
            Text = extract_text(Content),
            case Text of
                <<>> -> #{atom_to_binary(Name, utf8) => #{}};
                _ -> #{atom_to_binary(Name, utf8) => Text}
            end;
        _ ->
            %% Has child elements
            #{atom_to_binary(Name, utf8) => merge_children(Children)}
    end;
xml_to_map(#xmlText{value = Value}) ->
    Text = string:trim(Value),
    case Text of
        "" -> #{};
        _ -> #{<<"text">> => list_to_binary(Text)}
    end;
xml_to_map(_) ->
    #{}.

%% @doc Extract text content from XML content list
-spec extract_text([tuple()]) -> binary().
extract_text(Content) ->
    TextParts = lists:filtermap(
        fun
            (#xmlText{value = Value}) ->
                Trimmed = string:trim(Value),
                case Trimmed of
                    "" -> false;
                    _ -> {true, Trimmed}
                end;
            (_) ->
                false
        end,
        Content
    ),
    case TextParts of
        [] -> <<>>;
        _ -> list_to_binary(string:join(TextParts, ""))
    end.

%% @doc Extract child elements from XML content list
-spec extract_children([tuple()]) -> [map()].
extract_children(Content) ->
    lists:filtermap(
        fun
            (#xmlElement{} = El) -> {true, xml_to_map(El)};
            (_) -> false
        end,
        Content
    ).

%% @doc Merge list of child maps into a single map
%% Handles duplicate keys by creating lists
-spec merge_children([map()]) -> map().
merge_children(Children) ->
    lists:foldl(
        fun(ChildMap, Acc) ->
            maps:fold(
                fun(K, V, InnerAcc) ->
                    case maps:find(K, InnerAcc) of
                        {ok, ExistingValue} when is_list(ExistingValue) ->
                            maps:put(K, ExistingValue ++ [V], InnerAcc);
                        {ok, ExistingValue} ->
                            maps:put(K, [ExistingValue, V], InnerAcc);
                        error ->
                            maps:put(K, V, InnerAcc)
                    end
                end,
                Acc,
                ChildMap
            )
        end,
        #{},
        Children
    ).

%% ============================================================================
%% Internal helper functions
%% ============================================================================

%% @doc Convert key to atom for xmerl
-spec to_atom(atom() | binary() | string()) -> atom().
to_atom(Key) when is_atom(Key) -> Key;
to_atom(Key) when is_binary(Key) -> binary_to_atom(Key, utf8);
to_atom(Key) when is_list(Key) -> list_to_atom(Key).

%% @doc Check if a list is a string (list of characters)
-spec is_string_list(list()) -> boolean().
is_string_list([]) ->
    true;
is_string_list([H | T]) when is_integer(H), H >= 0, H =< 1114111 ->
    is_string_list(T);
is_string_list(_) ->
    false.
