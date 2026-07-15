-module(runtime_utils_test).
-include_lib("eunit/include/eunit.hrl").

split_base_url_empty_test() ->
    ?assertEqual({<<>>, <<>>}, runtime_utils:split_base_url(<<>>)).

split_base_url_https_test() ->
    ?assertEqual(
        {<<"https://">>, <<"api.example.com">>},
        runtime_utils:split_base_url(<<"https://api.example.com">>)
    ).

split_base_url_with_port_test() ->
    ?assertEqual(
        {<<"https://">>, <<"localhost:4566">>},
        runtime_utils:split_base_url(<<"https://localhost:4566">>)
    ).

split_base_url_unparsed_test() ->
    ?assertEqual({<<>>, <<"not-a-url">>}, runtime_utils:split_base_url(<<"not-a-url">>)).

endpoint_host_from_config_prefix_test() ->
    Config = #{endpoint_prefix => <<"s3">>, region => <<"us-west-2">>},
    ?assertEqual(<<"s3.us-west-2.amazonaws.com">>, runtime_utils:endpoint_host_from_config(Config)).

endpoint_host_from_config_base_url_test() ->
    Config = #{base_url => <<"https://custom.example:8443">>},
    ?assertEqual(<<"custom.example:8443">>, runtime_utils:endpoint_host_from_config(Config)).

endpoint_host_from_config_missing_prefix_test() ->
    ?assertEqual(undefined, runtime_utils:endpoint_host_from_config(#{region => <<"us-east-1">>})).
