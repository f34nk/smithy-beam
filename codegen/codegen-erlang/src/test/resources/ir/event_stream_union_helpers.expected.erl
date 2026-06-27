encode_event_stream(Events) when is_list(Events) -> [encode_event_stream_event(E) || E <- Events].

decode_event_stream(Body) when is_binary(Body) ->
    [decode_event_stream_event(F) || F <- aws_event_stream:decode_frames(Body)].

encode_event_stream_event({member, Value}) ->
    Payload = jsone:encode(
        maps:filter(
            fun(_, V) ->
                V =/= undefined
            end,
            #{<<"value">> => Value#member_event.value}
        )
    ),
    Headers = encode_event_headers(<<"member">>),
    aws_event_stream:frame(Headers, Payload);
encode_event_stream_event({unknown, _}) ->
    error({bad_event, unknown}).

decode_event_stream_event(#{headers := Headers, payload := Payload}) ->
    EventType = header_value(Headers, <<":event-type">>),
    decode_event_stream_event_type(EventType, Payload).

decode_event_stream_event_type(<<"member">>, Payload) ->
    {member, #member_event{
        value = maps:get(<<"value">>, jsone:decode(Payload, [return_maps]), undefined)
    }};
decode_event_stream_event_type(EventType, _Payload) ->
    error({bad_event, EventType}).
