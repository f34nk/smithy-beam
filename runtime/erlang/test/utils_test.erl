-module(utils_test).

-include_lib("eunit/include/eunit.hrl").

split_base_url_empty_test() ->
    ?assertEqual({<<>>, <<>>}, utils:split_base_url(<<>>)).

split_base_url_https_test() ->
    ?assertEqual(
        {<<"https://">>, <<"api.example.com">>},
        utils:split_base_url(<<"https://api.example.com">>)
    ).

split_base_url_with_port_test() ->
    ?assertEqual(
        {<<"https://">>, <<"localhost:4566">>},
        utils:split_base_url(<<"https://localhost:4566">>)
    ).

split_base_url_unparsed_test() ->
    ?assertEqual({<<>>, <<"not-a-url">>}, utils:split_base_url(<<"not-a-url">>)).

endpoint_host_from_prefix_test() ->
    Config = #{endpoint_prefix => <<"s3">>, region => <<"us-west-2">>},
    ?assertEqual(<<"s3.us-west-2.amazonaws.com">>, utils:endpoint_host_from_config(Config)).

endpoint_host_from_base_url_test() ->
    Config = #{base_url => <<"https://custom.example:8443">>},
    ?assertEqual(<<"custom.example:8443">>, utils:endpoint_host_from_config(Config)).

endpoint_host_missing_test() ->
    ?assertEqual(undefined, utils:endpoint_host_from_config(#{region => <<"us-east-1">>})).
