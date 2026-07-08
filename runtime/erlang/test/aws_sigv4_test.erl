-module(aws_sigv4_test).

-include_lib("eunit/include/eunit.hrl").
-include("http_types.hrl").

-define(CREDENTIALS, #{
    access_key_id => <<"AKIAIOSFODNN7EXAMPLE">>,
    secret_access_key => <<"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY">>
}).

-define(CONFIG, #{
    region => <<"us-east-1">>,
    signing_name => <<"s3">>,
    presign_expires => 3600,
    credentials => ?CREDENTIALS
}).

sign_adds_authorization_header_test() ->
    Request = #http_request{
        method = <<"GET">>,
        path = <<"/">>,
        host = <<"s3.amazonaws.com">>
    },
    Signed = aws_sigv4:sign(?CONFIG, list_buckets, Request),
    ?assert(has_header(<<"authorization">>, Signed#http_request.headers)).

sign_adds_host_header_test() ->
    Request = #http_request{
        method = <<"GET">>,
        path = <<"/">>,
        headers = []
    },
    Signed = aws_sigv4:sign(?CONFIG, list_buckets, Request),
    ?assert(has_header(<<"host">>, Signed#http_request.headers)).

sign_adds_session_token_test() ->
    Config = maps:merge(?CONFIG, #{
        credentials =>
            maps:merge(?CREDENTIALS, #{session_token => <<"temporary-session-token">>})
    }),
    Request = #http_request{
        method = <<"GET">>,
        path = <<"/">>,
        host = <<"s3.amazonaws.com">>
    },
    Signed = aws_sigv4:sign(Config, list_buckets, Request),
    ?assert(has_header(<<"x-amz-security-token">>, Signed#http_request.headers)).

presign_url_test() ->
    Request = #http_request{
        method = <<"GET">>,
        path = <<"/my-bucket/object.txt">>,
        host = <<"localhost:4566">>
    },
    {ok, Url} = aws_sigv4:presign_url(?CONFIG, get_object, Request),
    ?assertMatch(
        <<"https://localhost:4566/my-bucket/object.txt?", _/binary>>,
        Url
    ),
    ?assertMatch({_, _}, binary:match(Url, <<"X-Amz-Algorithm=AWS4-HMAC-SHA256">>)),
    ?assertMatch({_, _}, binary:match(Url, <<"X-Amz-Signature=">>)).

has_header(Name, Headers) ->
    lists:any(
        fun({HeaderName, _Value}) ->
            string:equal(binary_to_list(Name), binary_to_list(HeaderName), true)
        end,
        Headers
    ).
