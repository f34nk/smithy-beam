%% Shared smithy-beam Erlang Amazon Event Stream framing helpers.
-module(aws_event_stream).
-export([
    frame/2,
    decode_frames/1
]).

-type header() :: {binary(), binary() | boolean() | integer()}.
-type frame() :: #{headers := [header()], payload := binary()}.

-define(HEADER_TYPE_TRUE, 0).
-define(HEADER_TYPE_FALSE, 1).
-define(HEADER_TYPE_INTEGER, 4).
-define(HEADER_TYPE_STRING, 7).

%% @doc Encode headers and payload into a single event stream message.
-spec frame([header()], binary()) -> binary().
frame(Headers, Payload) when is_list(Headers), is_binary(Payload) ->
    HeadersBin = encode_headers(Headers),
    HeadersLen = byte_size(HeadersBin),
    PayloadLen = byte_size(Payload),
    TotalLen = HeadersLen + PayloadLen + 16,
    Prelude = <<TotalLen:32/big, HeadersLen:32/big>>,
    PreludeCrc = erlang:crc32(Prelude),
    MessageWithoutCrc =
        <<Prelude/binary, PreludeCrc:32/big, HeadersBin/binary, Payload/binary>>,
    MessageCrc = erlang:crc32(MessageWithoutCrc),
    <<MessageWithoutCrc/binary, MessageCrc:32/big>>.

%% @doc Decode one or more event stream messages from a binary body.
-spec decode_frames(binary()) -> [frame()].
decode_frames(Body) when is_binary(Body) ->
    decode_frames(Body, []).

-spec decode_frames(binary(), [frame()]) -> [frame()].
decode_frames(<<>>, Acc) ->
    lists:reverse(Acc);
decode_frames(Bin, Acc) ->
    Frame = decode_frame(Bin),
    decode_frames(maps:get(rest, Frame), [maps:without([rest], Frame) | Acc]).

-spec decode_frame(binary()) -> frame() | #{rest := binary()}.
decode_frame(Bin) ->
    case Bin of
        <<TotalLen:32/big, HeadersLen:32/big, PreludeCrc:32/big, Rest/binary>> when
            TotalLen >= 16, HeadersLen =< TotalLen - 16
        ->
            Prelude = <<TotalLen:32/big, HeadersLen:32/big>>,
            case erlang:crc32(Prelude) =:= PreludeCrc of
                false ->
                    error({bad_event_stream, invalid_prelude_crc});
                true ->
                    decode_frame_body(TotalLen, HeadersLen, PreludeCrc, Rest)
            end;
        _ ->
            error({bad_event_stream, incomplete})
    end.

-spec decode_frame_body(non_neg_integer(), non_neg_integer(), non_neg_integer(), binary()) ->
    frame() | #{rest := binary()}.
decode_frame_body(TotalLen, HeadersLen, PreludeCrc, Rest) ->
    PayloadLen = TotalLen - HeadersLen - 16,
    case Rest of
        <<HeadersBin:HeadersLen/binary, Payload:PayloadLen/binary, MessageCrc:32/big, Tail/binary>> ->
            MessageWithoutCrc =
                <<TotalLen:32/big, HeadersLen:32/big, PreludeCrc:32/big, HeadersBin/binary,
                    Payload/binary>>,
            case erlang:crc32(MessageWithoutCrc) =:= MessageCrc of
                false ->
                    error({bad_event_stream, invalid_message_crc});
                true ->
                    #{
                        headers => decode_headers(HeadersBin, []),
                        payload => Payload,
                        rest => Tail
                    }
            end;
        _ ->
            error({bad_event_stream, incomplete})
    end.

-spec encode_headers([header()]) -> binary().
encode_headers(Headers) ->
    lists:foldl(fun encode_header/2, <<>>, Headers).

-spec encode_header(header(), binary()) -> binary().
encode_header({Name, Value}, Acc) when is_binary(Name), is_binary(Value) ->
    <<Acc/binary, (byte_size(Name)):8, Name/binary, ?HEADER_TYPE_STRING:8,
        (byte_size(Value)):16/big, Value/binary>>;
encode_header({Name, true}, Acc) when is_binary(Name) ->
    <<Acc/binary, (byte_size(Name)):8, Name/binary, ?HEADER_TYPE_TRUE:8>>;
encode_header({Name, false}, Acc) when is_binary(Name) ->
    <<Acc/binary, (byte_size(Name)):8, Name/binary, ?HEADER_TYPE_FALSE:8>>;
encode_header({Name, Value}, Acc) when is_binary(Name), is_integer(Value) ->
    <<Acc/binary, (byte_size(Name)):8, Name/binary, ?HEADER_TYPE_INTEGER:8, Value:32/big>>;
encode_header({Name, _Value}, _Acc) ->
    error({bad_event_stream, {unsupported_header, Name}}).

-spec decode_headers(binary(), [header()]) -> [header()].
decode_headers(<<>>, Acc) ->
    lists:reverse(Acc);
decode_headers(Bin, Acc) ->
    case Bin of
        <<NameLen:8, Name:NameLen/binary, ?HEADER_TYPE_TRUE:8, Rest/binary>> ->
            decode_headers(Rest, [{Name, true} | Acc]);
        <<NameLen:8, Name:NameLen/binary, ?HEADER_TYPE_FALSE:8, Rest/binary>> ->
            decode_headers(Rest, [{Name, false} | Acc]);
        <<NameLen:8, Name:NameLen/binary, ?HEADER_TYPE_INTEGER:8, Value:32/big, Rest/binary>> ->
            decode_headers(Rest, [{Name, Value} | Acc]);
        <<NameLen:8, Name:NameLen/binary, ?HEADER_TYPE_STRING:8, ValueLen:16/big,
            Value:ValueLen/binary, Rest/binary>> ->
            decode_headers(Rest, [{Name, Value} | Acc]);
        _ ->
            error({bad_event_stream, invalid_header})
    end.
