-module(smithy_json).

%% JSON encode/decode for Smithy shapes.
%%
%% Delegates to `jsx` (the de-facto Erlang JSON library used across the
%% BEAM ecosystem). For large BigDecimal values (e.g. from the Smithy
%% `bigDecimal` shape) this module provides a `{integer(), integer()}`
%% representation; see `encode_decimal/1` / `decode_decimal/1`.
%%
%% All public functions mirror the AWS SDK convention:
%%   encode/1  → binary()
%%   decode/1  → map() with binary keys

-export([encode/1, decode/1]).

%% Internal helpers (exported for testing)
-export([
    normalise_for_encode/1,
    normalise_for_decode/1,
    encode_decimal/1,
    decode_decimal/1
]).

-type json_term() :: map() | list() | binary() | number() | boolean() | null.

%%====================================================================
%% Public API
%%====================================================================

%% @doc Encode an Erlang term to a JSON binary.
%%
%% Maps are emitted as JSON objects; lists as JSON arrays.
%% Atoms `true`, `false`, and `null` are preserved.
%% Binaries are emitted as JSON strings.
%%
%% @param Term Any Erlang term that can be represented as JSON.
%% @returns JSON-encoded binary.
-spec encode(json_term()) -> binary().
encode(Term) ->
    jsx:encode(normalise_for_encode(Term)).

%% @doc Decode a JSON binary into an Erlang term.
%%
%% JSON objects become maps with binary keys.
%% Numbers remain as integers or floats.
%% JSON `null` becomes the atom `null`.
%%
%% @param Json JSON binary to decode.
%% @returns Decoded Erlang term.
-spec decode(binary() | iolist()) -> json_term().
decode(Json) when is_binary(Json) ->
    normalise_for_decode(jsx:decode(Json, [return_maps]));
decode(Json) ->
    decode(iolist_to_binary(Json)).

%%====================================================================
%% Internal helpers
%%====================================================================

%% @doc Prepare a term for JSON encoding.
%% Converts atom map keys to binary, handles nested structures.
-spec normalise_for_encode(term()) -> term().
normalise_for_encode(M) when is_map(M) ->
    maps:fold(
        fun(K, V, Acc) ->
            BinKey = key_to_binary(K),
            maps:put(BinKey, normalise_for_encode(V), Acc)
        end,
        #{},
        M
    );
normalise_for_encode(L) when is_list(L) ->
    [normalise_for_encode(E) || E <- L];
normalise_for_encode(A) when is_atom(A), A =/= true, A =/= false, A =/= null ->
    atom_to_binary(A, utf8);
normalise_for_encode(Other) ->
    Other.

%% @doc Normalise decoded JSON — currently a no-op as jsx already returns
%% maps with binary keys. Kept for extension points (e.g. shape coercions).
-spec normalise_for_decode(term()) -> term().
normalise_for_decode(M) when is_map(M) ->
    maps:map(fun(_K, V) -> normalise_for_decode(V) end, M);
normalise_for_decode(L) when is_list(L) ->
    [normalise_for_decode(E) || E <- L];
normalise_for_decode(Other) ->
    Other.

%% @doc Encode a `{Unscaled :: integer(), Scale :: integer()}` BigDecimal
%% pair as a JSON string in decimal notation.
%%
%% Example: `{12345, -2}` → `<<"123.45">>`.
-spec encode_decimal({integer(), integer()}) -> binary().
encode_decimal({Unscaled, 0}) ->
    integer_to_binary(Unscaled);
encode_decimal({Unscaled, Scale}) when Scale < 0 ->
    AbsScale = -Scale,
    Str = integer_to_binary(abs(Unscaled)),
    Len = byte_size(Str),
    Sign = if Unscaled < 0 -> <<"-">>; true -> <<>> end,
    if
        Len =< AbsScale ->
            Pad = binary:copy(<<"0">>, AbsScale - Len),
            <<Sign/binary, "0.", Pad/binary, Str/binary>>;
        true ->
            {Int, Frac} = split_binary(Str, Len - AbsScale),
            <<Sign/binary, Int/binary, ".", Frac/binary>>
    end;
encode_decimal({Unscaled, Scale}) when Scale > 0 ->
    Zeros = binary:copy(<<"0">>, Scale),
    <<(integer_to_binary(Unscaled))/binary, Zeros/binary>>.

%% @doc Decode a JSON decimal string into `{Unscaled, Scale}`.
%% The Scale is always `≤ 0` (i.e. the pair is in standard form).
-spec decode_decimal(binary()) -> {integer(), integer()}.
decode_decimal(Bin) when is_binary(Bin) ->
    Str = binary_to_list(Bin),
    case string:split(Str, ".") of
        [IntPart] ->
            {list_to_integer(IntPart), 0};
        [IntPart, FracPart] ->
            Scale = -length(FracPart),
            Unscaled = list_to_integer(IntPart ++ FracPart),
            {Unscaled, Scale}
    end.

%%====================================================================
%% Private helpers
%%====================================================================

key_to_binary(K) when is_binary(K) -> K;
key_to_binary(K) when is_atom(K)   -> atom_to_binary(K, utf8);
key_to_binary(K) when is_list(K)   -> list_to_binary(K).
