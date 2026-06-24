encode_event_headers(EventType) ->
    [
        {<<":event-type">>, EventType},
        {<<":message-type">>, <<"event">>},
        {<<":content-type">>, <<"application/json">>}
    ].
