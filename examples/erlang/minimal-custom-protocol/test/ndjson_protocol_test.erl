-module(ndjson_protocol_test).

-include_lib("eunit/include/eunit.hrl").
-include("greeting_service_types.hrl").

encode_say_hello_request_test() ->
    Input = #say_hello_input{name = <<"world">>},
    Req = greeting_service_ndjson_protocol:encode_say_hello_request(Input),
    ?assertEqual(#{operation => say_hello, input => Input}, Req).

decode_say_hello_response_test() ->
    ?assertEqual({ok, undefined}, greeting_service_ndjson_protocol:decode_say_hello_response(<<>>)).

client_wires_custom_codec_test() ->
    {ok, ClientSrc} = file:read_file("src/generated/greeting_service_client.erl"),
    ?assertNotEqual(nomatch, binary:match(ClientSrc, <<"greeting_service_ndjson_protocol:encode_say_hello_request">>)),
    ?assertEqual(nomatch, binary:match(ClientSrc, <<"not_implemented">>)).
