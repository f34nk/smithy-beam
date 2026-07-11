-module(aws_event_stream_test).

-include_lib("eunit/include/eunit.hrl").

-define(PAYLOAD_ONLY_FRAME,
    <<0, 0, 0, 30, 0, 0, 0, 0, 186, 242, 246, 138, 123, 34, 102, 111, 111, 34, 58, 32, 34,
        98, 97, 114, 34, 125, 174, 114, 88, 228>>
).

-define(JSON_HEADER_FRAME,
    <<0, 0, 0, 62, 0, 0, 0, 32, 64, 93, 249, 70, 12, 99, 111, 110, 116, 101, 110, 116, 45,
        116, 121, 112, 101, 7, 0, 16, 97, 112, 112, 108, 105, 99, 97, 116, 105, 111, 110,
        47, 106, 115, 111, 110, 123, 34, 102, 111, 111, 34, 58, 32, 34, 98, 97, 114, 34,
        125, 111, 162, 191, 59>>
).

frame_round_trip_test() ->
    Headers = [
        {<<":event-type">>, <<"SubscribeToShardEvent">>},
        {<<":message-type">>, <<"event">>},
        {<<":content-type">>, <<"application/json">>}
    ],
    Payload = <<"{\"Records\":[]}">>,
    Framed = aws_event_stream:frame(Headers, Payload),
    [Frame] = aws_event_stream:decode_frames(Framed),
    ?assertEqual(Headers, maps:get(headers, Frame)),
    ?assertEqual(Payload, maps:get(payload, Frame)).

decode_known_payload_only_frame_test() ->
    [Frame] = aws_event_stream:decode_frames(?PAYLOAD_ONLY_FRAME),
    ?assertEqual([], maps:get(headers, Frame)),
    ?assertEqual(<<"{\"foo\": \"bar\"}">>, maps:get(payload, Frame)).

decode_known_json_header_frame_test() ->
    [Frame] = aws_event_stream:decode_frames(?JSON_HEADER_FRAME),
    ?assertEqual([{<<"content-type">>, <<"application/json">>}], maps:get(headers, Frame)),
    ?assertEqual(<<"{\"foo\": \"bar\"}">>, maps:get(payload, Frame)).

encode_matches_known_payload_only_frame_test() ->
    ?assertEqual(
        ?PAYLOAD_ONLY_FRAME,
        aws_event_stream:frame([], <<"{\"foo\": \"bar\"}">>)
    ).

encode_matches_known_json_header_frame_test() ->
    ?assertEqual(
        ?JSON_HEADER_FRAME,
        aws_event_stream:frame(
            [{<<"content-type">>, <<"application/json">>}],
            <<"{\"foo\": \"bar\"}">>
        )
    ).

decode_multiple_frames_test() ->
    Frame1 = aws_event_stream:frame([], <<"one">>),
    Frame2 = aws_event_stream:frame([{<<":event-type">>, <<"event">>}], <<"two">>),
    Frames = aws_event_stream:decode_frames(<<Frame1/binary, Frame2/binary>>),
    ?assertEqual(2, length(Frames)),
    ?assertEqual(<<"one">>, maps:get(payload, hd(Frames))),
    ?assertEqual(<<"two">>, maps:get(payload, lists:last(Frames))).

decode_empty_body_test() ->
    ?assertEqual([], aws_event_stream:decode_frames(<<>>)).

invalid_prelude_crc_test() ->
    BadFrame =
        <<0, 0, 0, 30, 0, 0, 0, 0, 0, 0, 0, 0, 123, 34, 102, 111, 111, 34, 58, 32, 34, 98,
            97, 114, 34, 125, 174, 114, 88, 228>>,
    ?assertError({bad_event_stream, invalid_prelude_crc}, aws_event_stream:decode_frames(BadFrame)).

invalid_message_crc_test() ->
    BadFrame =
        <<0, 0, 0, 30, 0, 0, 0, 0, 186, 242, 246, 138, 123, 34, 102, 111, 111, 34, 58, 32,
            34, 98, 97, 114, 34, 125, 0, 0, 0, 0>>,
    ?assertError({bad_event_stream, invalid_message_crc}, aws_event_stream:decode_frames(BadFrame)).
