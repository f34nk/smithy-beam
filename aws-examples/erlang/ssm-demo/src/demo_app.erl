-module(demo_app).
-export([run/0]).

-include("ssm_types.hrl").

-define(DATABASE_HOST_NAME, <<"/demo/database/host">>).
-define(DATABASE_PORT_NAME, <<"/demo/database/port">>).
-define(DATABASE_NAME_NAME, <<"/demo/database/name">>).
-define(API_KEY_NAME, <<"/demo/api/key">>).
-define(DATABASE_PATH, <<"/demo/database">>).
-define(TEST_PARAM_NAME, <<"/demo/test/param">>).
-define(PARAM_VALUE, <<"test-value-from-erlang">>).

run() ->
    io:format("~n=== Running SSM Client Application ===~n~n"),

    Config = client_config(),
    io:format("SSM client configured for ~s~n~n", [maps:get(base_url, Config)]),

    setup_infrastructure(Config),
    describe_parameters(Config),
    get_parameter(Config, ?DATABASE_HOST_NAME, "GetParameter"),
    get_parameters(Config),
    get_parameters_by_path(Config),
    get_parameter(Config, ?API_KEY_NAME, "GetParameter (SecureString)"),
    create_test_parameter(Config),
    verify_test_parameter(Config),
    update_test_parameter(Config),
    get_parameter_history(Config),
    list_tags_for_resource(Config),
    delete_test_parameter(Config),
    verify_deletion(Config),

    io:format("~n=== SSM Client Application Complete ===~n"),
    ok.

client_config() ->
    #{
        region => <<"us-east-1">>,
        endpoint_prefix => <<"ssm">>,
        signing_name => <<"ssm">>,
        base_url => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    }.

setup_infrastructure(Config) ->
    io:format("--- Setup infrastructure ---~n"),

    create_parameter(Config, ?DATABASE_HOST_NAME, <<"localhost">>, string, [
        #tag{key = <<"Environment">>, value = <<"demo">>},
        #tag{key = <<"Project">>, value = <<"smithy-erlang">>}
    ]),

    create_parameter(Config, ?DATABASE_PORT_NAME, <<"5432">>, string, [
        #tag{key = <<"Environment">>, value = <<"demo">>},
        #tag{key = <<"Project">>, value = <<"smithy-erlang">>}
    ]),

    create_parameter(Config, ?DATABASE_NAME_NAME, <<"myapp_db">>, string, [
        #tag{key = <<"Environment">>, value = <<"demo">>},
        #tag{key = <<"Project">>, value = <<"smithy-erlang">>}
    ]),

    create_parameter(Config, ?API_KEY_NAME, <<"super-secret-api-key-12345">>, secure_string, [
        #tag{key = <<"Environment">>, value = <<"demo">>},
        #tag{key = <<"Project">>, value = <<"smithy-erlang">>},
        #tag{key = <<"Sensitive">>, value = <<"true">>}
    ]),

    io:format(
        "Infrastructure ready: Host=~s Port=~s Name=~s ApiKey=~s~n~n",
        [?DATABASE_HOST_NAME, ?DATABASE_PORT_NAME, ?DATABASE_NAME_NAME, ?API_KEY_NAME]
    ).

create_parameter(Config, Name, Value, Type, Tags) ->
    Input = #put_parameter_input{
        name = Name,
        value = Value,
        type = Type,
        tags = Tags
    },
    case ssm_client:put_parameter(Config, Input) of
        {ok, #put_parameter_output{version = Version}} ->
            io:format("SUCCESS: Parameter ~s ready (version ~p)~n", [Name, Version]);
        {error, Reason} ->
            erlang:error({put_parameter_failed, Reason})
    end.

describe_parameters(Config) ->
    io:format("--- DescribeParameters ---~n"),
    case ssm_client:describe_parameters(Config, #describe_parameters_input{}) of
        {ok, Pages} when is_list(Pages) ->
            Parameters = lists:flatmap(
                fun(#describe_parameters_output{parameters = Params}) ->
                    ensure_list(Params)
                end,
                Pages
            ),
            case Parameters of
                [] -> erlang:error({assertion_failed, empty_parameter_list});
                _ -> io:format("SUCCESS: Found ~p parameter(s)~n", [length(Parameters)])
            end,
            lists:foreach(
                fun(#parameter_metadata{name = Name, type = Type}) ->
                    io:format("    ~s (~s)~n", [format_string(Name), format_type(Type)])
                end,
                Parameters
            );
        {ok, _} ->
            erlang:error({assertion_failed, empty_parameter_list});
        {error, Reason} ->
            erlang:error({describe_parameters_failed, Reason})
    end,
    io:format("~n").

get_parameter(Config, Name, Label) ->
    io:format("--- ~s ---~n", [Label]),
    Input = #get_parameter_input{name = Name, with_decryption = true},
    case ssm_client:get_parameter(Config, Input) of
        {ok, #get_parameter_output{parameter = #parameter{} = Param}} ->
            io:format("SUCCESS: Got parameter~n"),
            io:format("    Name:  ~s~n", [format_string(Param#parameter.name)]),
            io:format("    Value: ~s~n", [format_string(Param#parameter.value)]),
            io:format("    Type:  ~s~n", [format_type(Param#parameter.type)]);
        {ok, _} ->
            erlang:error({assertion_failed, get_parameter_returned_no_parameter});
        {error, Reason} ->
            erlang:error({get_parameter_failed, Reason})
    end,
    io:format("~n").

get_parameters(Config) ->
    io:format("--- GetParameters ---~n"),
    Input = #get_parameters_input{
        names = [?DATABASE_HOST_NAME, ?DATABASE_PORT_NAME, ?DATABASE_NAME_NAME],
        with_decryption = true
    },
    case ssm_client:get_parameters(Config, Input) of
        {ok, #get_parameters_output{parameters = Params, invalid_parameters = Invalid}} ->
            ParamList = ensure_list(Params),
            InvalidList = ensure_list(Invalid),
            io:format(
                "SUCCESS: Got ~p parameter(s), ~p invalid~n",
                [length(ParamList), length(InvalidList)]
            ),
            case InvalidList of
                [] -> io:format("SUCCESS: No invalid parameters~n");
                _ -> erlang:error({assertion_failed, {invalid_parameters, InvalidList}})
            end,
            lists:foreach(
                fun(#parameter{name = Name, value = Value}) ->
                    io:format("    ~s = ~s~n", [format_string(Name), format_string(Value)])
                end,
                ParamList
            );
        {ok, _} ->
            erlang:error({assertion_failed, unexpected_get_parameters_output});
        {error, Reason} ->
            erlang:error({get_parameters_failed, Reason})
    end,
    io:format("~n").

get_parameters_by_path(Config) ->
    io:format("--- GetParametersByPath ---~n"),
    Input = #get_parameters_by_path_input{
        path = ?DATABASE_PATH,
        recursive = true,
        with_decryption = true
    },
    case ssm_client:get_parameters_by_path(Config, Input) of
        {ok, Pages} when is_list(Pages) ->
            Params = lists:flatmap(
                fun(#get_parameters_by_path_output{parameters = PageParams}) ->
                    ensure_list(PageParams)
                end,
                Pages
            ),
            case Params of
                [] ->
                    erlang:error({assertion_failed, {empty_parameters_by_path, ?DATABASE_PATH}});
                _ ->
                    io:format(
                        "SUCCESS: Found ~p parameter(s) under ~s~n",
                        [length(Params), ?DATABASE_PATH]
                    )
            end,
            lists:foreach(
                fun(#parameter{name = Name, value = Value}) ->
                    io:format("    ~s = ~s~n", [format_string(Name), format_string(Value)])
                end,
                Params
            );
        {ok, _} ->
            erlang:error({assertion_failed, {empty_parameters_by_path, ?DATABASE_PATH}});
        {error, Reason} ->
            erlang:error({get_parameters_by_path_failed, Reason})
    end,
    io:format("~n").

create_test_parameter(Config) ->
    io:format("--- PutParameter ---~n"),
    Input = #put_parameter_input{
        name = ?TEST_PARAM_NAME,
        value = ?PARAM_VALUE,
        type = string,
        description = <<"Test parameter created by smithy-erlang demo">>,
        tags = [
            #tag{key = <<"Environment">>, value = <<"demo">>},
            #tag{key = <<"CreatedBy">>, value = <<"smithy-erlang">>}
        ]
    },
    case ssm_client:put_parameter(Config, Input) of
        {ok, #put_parameter_output{version = Version}} ->
            io:format("SUCCESS: Created parameter, version: ~p~n", [Version]);
        {error, Reason} ->
            erlang:error({put_parameter_failed, Reason})
    end,
    io:format("~n").

verify_test_parameter(Config) ->
    io:format("--- GetParameter (verify creation) ---~n"),
    Input = #get_parameter_input{name = ?TEST_PARAM_NAME, with_decryption = true},
    case ssm_client:get_parameter(Config, Input) of
        {ok, #get_parameter_output{parameter = #parameter{value = Value}}} ->
            case Value =:= ?PARAM_VALUE of
                true ->
                    io:format("SUCCESS: Parameter value matches '~s'~n", [?PARAM_VALUE]);
                false ->
                    erlang:error({assertion_failed, {expected_value, ?PARAM_VALUE}, {got, Value}})
            end;
        {ok, _} ->
            erlang:error({assertion_failed, get_parameter_returned_no_parameter});
        {error, Reason} ->
            erlang:error({get_parameter_failed, Reason})
    end,
    io:format("~n").

update_test_parameter(Config) ->
    io:format("--- PutParameter (update) ---~n"),
    Input = #put_parameter_input{
        name = ?TEST_PARAM_NAME,
        value = <<"updated-value-from-erlang">>,
        type = string,
        overwrite = true
    },
    case ssm_client:put_parameter(Config, Input) of
        {ok, #put_parameter_output{version = Version}} ->
            io:format("SUCCESS: Updated parameter, version: ~p~n", [Version]),
            case Version > 1 of
                true ->
                    io:format("SUCCESS: Version ~p > 1 (proves update)~n", [Version]);
                false ->
                    erlang:error({assertion_failed, {expected_version_gt_1, Version}})
            end;
        {error, Reason} ->
            erlang:error({put_parameter_failed, Reason})
    end,
    io:format("~n").

get_parameter_history(Config) ->
    io:format("--- GetParameterHistory ---~n"),
    Input = #get_parameter_history_input{name = ?TEST_PARAM_NAME, with_decryption = true},
    case ssm_client:get_parameter_history(Config, Input) of
        {ok, Pages} when is_list(Pages) ->
            History = lists:flatmap(
                fun(#get_parameter_history_output{parameters = PageParams}) ->
                    ensure_list(PageParams)
                end,
                Pages
            ),
            io:format("SUCCESS: Found ~p version(s)~n", [length(History)]),
            case length(History) >= 2 of
                true -> io:format("SUCCESS: History has >= 2 versions~n");
                false -> erlang:error({assertion_failed, {expected_history_versions_gte_2, length(History)}})
            end,
            lists:foreach(
                fun(#parameter_history{version = Version, value = Value}) ->
                    io:format("    Version ~p: ~s~n", [Version, format_string(Value)])
                end,
                History
            );
        {ok, _} ->
            erlang:error({assertion_failed, expected_parameter_history_pages});
        {error, Reason} ->
            erlang:error({get_parameter_history_failed, Reason})
    end,
    io:format("~n").

list_tags_for_resource(Config) ->
    io:format("--- ListTagsForResource ---~n"),
    Input = #list_tags_for_resource_input{
        resource_type = parameter,
        resource_id = ?TEST_PARAM_NAME
    },
    case ssm_client:list_tags_for_resource(Config, Input) of
        {ok, #list_tags_for_resource_output{tag_list = Tags}} ->
            TagList = ensure_list(Tags),
            io:format("SUCCESS: Found ~p tag(s)~n", [length(TagList)]),
            lists:foreach(
                fun(#tag{key = Key, value = Value}) ->
                    io:format("    ~s = ~s~n", [format_string(Key), format_string(Value)])
                end,
                TagList
            );
        {error, Reason} ->
            erlang:error({list_tags_for_resource_failed, Reason})
    end,
    io:format("~n").

delete_test_parameter(Config) ->
    io:format("--- DeleteParameter ---~n"),
    Input = #delete_parameter_input{name = ?TEST_PARAM_NAME},
    case ssm_client:delete_parameter(Config, Input) of
        {ok, _} ->
            io:format("SUCCESS: Parameter deleted~n");
        {error, Reason} ->
            erlang:error({delete_parameter_failed, Reason})
    end,
    io:format("~n").

verify_deletion(Config) ->
    io:format("--- GetParameter (verify deletion) ---~n"),
    Input = #get_parameter_input{name = ?TEST_PARAM_NAME, with_decryption = true},
    case ssm_client:get_parameter(Config, Input) of
        {ok, _} ->
            erlang:error({assertion_failed, {parameter_still_exists, ?TEST_PARAM_NAME}});
        {error, #parameter_not_found{}} ->
            io:format("SUCCESS: Parameter confirmed deleted~n");
        {error, Reason} ->
            case is_parameter_deleted_transport_error(Reason) of
                true ->
                    io:format("SUCCESS: Parameter confirmed deleted (connection closed on 4xx)~n");
                false ->
                    erlang:error({get_parameter_failed, Reason})
            end
    end,
    io:format("~n").

ensure_list(undefined) -> [];
ensure_list(List) when is_list(List) -> List.

format_string(undefined) -> "unknown";
format_string(Value) when is_binary(Value) -> unicode:characters_to_list(Value);
format_string(Value) when is_atom(Value) -> atom_to_list(Value);
format_string(Value) -> io_lib:format("~p", [Value]).

format_type(undefined) -> "unknown";
format_type(string) -> "String";
format_type(string_list) -> "StringList";
format_type(secure_string) -> "SecureString";
format_type({unknown, Wire}) when is_binary(Wire) -> unicode:characters_to_list(Wire);
format_type(Type) -> format_string(Type).

is_parameter_deleted_transport_error({shutdown, _}) -> true;
is_parameter_deleted_transport_error(closed) -> true;
is_parameter_deleted_transport_error({closed, _}) -> true;
is_parameter_deleted_transport_error(_) -> false.
