-module(demo_app).
-export([run/0]).

-include("lambda_types.hrl").

-define(LAMBDA_FUNCTION_NAME, <<"lambda-demo-function">>).
-define(ALIAS_NAME, <<"demo">>).
-define(LAMBDA_ROLE_ARN, <<"arn:aws:iam::000000000000:role/lambda-demo-role">>).
-define(LAMBDA_ZIP_PATH, "terraform/lambda_function.zip").

run() ->
    io:format("~n=== Lambda Demo: Full Function Lifecycle ===~n~n"),

    Config = client_config(),
    io:format("Lambda client configured for ~s~n~n", [maps:get(base_url, Config)]),

    setup_infrastructure(Config),

    FunctionArn = <<"arn:aws:lambda:us-east-1:000000000000:function:",
                    ?LAMBDA_FUNCTION_NAME/binary>>,

    %% ---------------------------------------------------------------
    %% 1. Account overview (service-level operation)
    %% ---------------------------------------------------------------
    io:format("--- 1. GetAccountSettings ---~n"),
    case lambda_client:get_account_settings(Config, #get_account_settings_input{}) of
        {ok, #get_account_settings_output{account_usage = AcctUsage, account_limit = AcctLimit}} ->
            case AcctUsage of
                undefined ->
                    io:format("  No usage data~n");
                #account_usage{function_count = FnCount, total_code_size = CodeSize} ->
                    io:format("  Functions deployed : ~p~n", [FnCount]),
                    io:format("  Total code size    : ~p bytes~n", [CodeSize])
            end,
            case AcctLimit of
                undefined -> ok;
                #account_limit{concurrent_executions = Limit} ->
                    io:format("  Concurrent limit   : ~p~n", [Limit])
            end;
        {error, AcctErr} ->
            erlang:error({get_account_settings_failed, AcctErr})
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 2. Survey — list all functions in the account
    %% ---------------------------------------------------------------
    io:format("--- 2. ListFunctions ---~n"),
    case lambda_client:list_functions(Config, #list_functions_input{}) of
        {ok, Fns} when is_list(Fns), Fns =/= [] ->
            io:format("  SUCCESS: Found ~p function(s)~n", [length(Fns)]),
            FnNames = [F#function_configuration.function_name || F <- Fns, F =/= undefined],
            case lists:member(?LAMBDA_FUNCTION_NAME, FnNames) of
                true  -> io:format("  SUCCESS: Function '~s' found~n", [?LAMBDA_FUNCTION_NAME]);
                false -> erlang:error({assertion_failed, {function_not_found, ?LAMBDA_FUNCTION_NAME}})
            end,
            lists:foreach(
                fun(#function_configuration{
                        function_name = FnName,
                        runtime = FnRuntime,
                        code_size = FnSize
                    }) ->
                    io:format("    ~s  runtime=~s  size=~p bytes~n",
                              [format_binary(FnName), format_binary(FnRuntime), FnSize])
                end,
                Fns
            );
        {ok, _} ->
            erlang:error({assertion_failed, empty_function_list});
        {error, ListFnsErr} ->
            erlang:error({list_functions_failed, ListFnsErr})
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 3. Inspect the demo function
    %% ---------------------------------------------------------------
    io:format("--- 3. GetFunction ---~n"),
    GetFnInput = #get_function_input{function_name = ?LAMBDA_FUNCTION_NAME},
    case lambda_client:get_function(Config, GetFnInput) of
        {ok, #get_function_output{configuration = GetFnCfg}} when GetFnCfg =/= undefined ->
            #function_configuration{
                state = State,
                runtime = Runtime,
                handler = Handler,
                memory_size = MemorySize
            } = GetFnCfg,
            case State of
                active -> io:format("  SUCCESS: Function is Active~n");
                Other  -> erlang:error({assertion_failed, {expected_active, Other}})
            end,
            io:format("  Runtime  : ~s~n", [format_binary(Runtime)]),
            io:format("  Handler  : ~s~n", [format_binary(Handler)]),
            io:format("  Memory   : ~p MB~n", [MemorySize]),
            io:format("  State    : ~p~n", [State]);
        {ok, _} ->
            erlang:error({assertion_failed, missing_function_configuration});
        {error, GetFnErr} ->
            erlang:error({get_function_failed, GetFnErr})
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 4. Invoke the function
    %% ---------------------------------------------------------------
    io:format("--- 4. Invoke ---~n"),
    InvokeIn = #invoke_input{
        function_name = ?LAMBDA_FUNCTION_NAME,
        payload = <<"{\"hello\": \"from smithy-erlang\"}">>
    },
    case lambda_client:invoke(Config, InvokeIn) of
        {ok, #invoke_output{status_code = 200, payload = InvPayload}} when is_binary(InvPayload) ->
            io:format("  SUCCESS: Invocation returned 200~n"),
            io:format("  Status code : 200~n"),
            io:format("  Payload     : ~s~n", [InvPayload]);
        {ok, #invoke_output{status_code = InvStatus}} ->
            erlang:error({assertion_failed, {expected_200, InvStatus}});
        {ok, _} ->
            erlang:error({assertion_failed, invoke_returned_empty_payload});
        {error, InvokeErr} ->
            erlang:error({invoke_failed, InvokeErr})
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 5. Update the function description
    %% ---------------------------------------------------------------
    io:format("--- 5. UpdateFunctionConfiguration ---~n"),
    UpdateCfgIn = #update_function_configuration_input{
        function_name = ?LAMBDA_FUNCTION_NAME,
        description = <<"Updated by smithy-erlang demo">>
    },
    case lambda_client:update_function_configuration(Config, UpdateCfgIn) of
        {ok, #update_function_configuration_output{description = Description}} ->
            io:format("  SUCCESS: Description : ~s~n", [format_binary(Description)]);
        {ok, _} ->
            erlang:error({assertion_failed, update_function_configuration_returned_empty});
        {error, UpdateCfgErr} ->
            erlang:error({update_function_configuration_failed, UpdateCfgErr})
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 6. Publish a numbered version
    %% ---------------------------------------------------------------
    io:format("--- 6. PublishVersion ---~n"),
    PubVerIn = #publish_version_input{
        function_name = ?LAMBDA_FUNCTION_NAME,
        description = <<"v1 published by smithy-erlang demo">>
    },
    PublishedVersion =
        case lambda_client:publish_version(Config, PubVerIn) of
            {ok, #publish_version_output{version = Ver}} when is_binary(Ver), byte_size(Ver) > 0 ->
                io:format("  SUCCESS: Published version : ~s~n", [Ver]),
                Ver;
            {ok, _} ->
                erlang:error({assertion_failed, publish_version_returned_empty});
            {error, PubVerErr} ->
                erlang:error({publish_version_failed, PubVerErr})
        end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 7. List all published versions
    %% ---------------------------------------------------------------
    io:format("--- 7. ListVersionsByFunction ---~n"),
    ListVerIn = #list_versions_by_function_input{function_name = ?LAMBDA_FUNCTION_NAME},
    case lambda_client:list_versions_by_function(Config, ListVerIn) of
        {ok, Versions} when is_list(Versions) ->
            io:format("  Found ~p version(s):~n", [length(Versions)]),
            case length(Versions) >= 2 of
                true  -> io:format("  SUCCESS: >= 2 versions (original + published)~n");
                false -> erlang:error({assertion_failed, {expected_at_least_2_versions, length(Versions)}})
            end,
            lists:foreach(
                fun(#function_configuration{version = VNum, description = VDesc}) ->
                    io:format("    ~s  ~s~n", [format_binary(VNum), format_binary(VDesc)])
                end,
                Versions
            );
        {ok, _} ->
            erlang:error({assertion_failed, empty_version_list});
        {error, ListVerErr} ->
            erlang:error({list_versions_by_function_failed, ListVerErr})
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 8. Create an alias pointing at the published version
    %% ---------------------------------------------------------------
    io:format("--- 8. CreateAlias ---~n"),
    CreateAliasIn = #create_alias_input{
        function_name = ?LAMBDA_FUNCTION_NAME,
        name = ?ALIAS_NAME,
        function_version = PublishedVersion,
        description = <<"Stable release alias">>
    },
    case lambda_client:create_alias(Config, CreateAliasIn) of
        {ok, #create_alias_output{name = AliasName, function_version = AliasVersion}} ->
            io:format("  SUCCESS: Alias '~s' -> version ~s~n",
                      [format_binary(AliasName), format_binary(AliasVersion)]);
        {error, CreateAliasErr} ->
            erlang:error({create_alias_failed, CreateAliasErr})
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 9. List aliases
    %% ---------------------------------------------------------------
    io:format("--- 9. ListAliases ---~n"),
    ListAliasIn = #list_aliases_input{function_name = ?LAMBDA_FUNCTION_NAME},
    case lambda_client:list_aliases(Config, ListAliasIn) of
        {ok, Aliases} when is_list(Aliases), Aliases =/= [] ->
            io:format("  SUCCESS: Found ~p alias(es)~n", [length(Aliases)]),
            AliasNames = [A#alias_configuration.name || A <- Aliases, A =/= undefined],
            case lists:member(?ALIAS_NAME, AliasNames) of
                true  -> io:format("  SUCCESS: Alias '~s' found~n", [?ALIAS_NAME]);
                false -> erlang:error({assertion_failed, {alias_not_found, ?ALIAS_NAME}})
            end,
            lists:foreach(
                fun(#alias_configuration{name = AName, function_version = AVer}) ->
                    io:format("    ~s -> ~s~n", [format_binary(AName), format_binary(AVer)])
                end,
                Aliases
            );
        {ok, _} ->
            erlang:error({assertion_failed, empty_alias_list});
        {error, ListAliasErr} ->
            erlang:error({list_aliases_failed, ListAliasErr})
    end,
    io:format("~n"),

    %% Warm up the published version before alias invoke. LocalStack often needs a
    %% separate cold start for qualifier-based invocations.
    WarmupIn = #invoke_input{
        function_name = ?LAMBDA_FUNCTION_NAME,
        qualifier = PublishedVersion,
        payload = <<"{\"warmup\": true}">>
    },
    case lambda_client:invoke(Config, WarmupIn) of
        {ok, _} -> ok;
        {error, WarmupErr} -> erlang:error({invoke_warmup_failed, WarmupErr})
    end,
    timer:sleep(1000),

    %% ---------------------------------------------------------------
    %% 10. Invoke via alias
    %% ---------------------------------------------------------------
    io:format("--- 10. Invoke (via alias '~s') ---~n", [?ALIAS_NAME]),
    InvokeAliasIn = #invoke_input{
        function_name = ?LAMBDA_FUNCTION_NAME,
        qualifier = ?ALIAS_NAME,
        payload = <<"{\"source\": \"alias invocation\"}">>
    },
    case invoke_with_retry(Config, InvokeAliasIn, 5) of
        {ok, #invoke_output{status_code = 200, payload = AliasPayload}} ->
            io:format("  SUCCESS: Alias invocation returned 200~n"),
            io:format("  Status code : 200~n"),
            case AliasPayload of
                undefined -> ok;
                _ -> io:format("  Payload     : ~s~n", [AliasPayload])
            end;
        {ok, #invoke_output{status_code = AliasInvStatus}} ->
            erlang:error({assertion_failed, {expected_200, AliasInvStatus}});
        {error, InvokeAliasErr} ->
            erlang:error({invoke_failed, InvokeAliasErr})
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 11. Tag management
    %% ---------------------------------------------------------------
    io:format("--- 11. TagResource / ListTags / UntagResource ---~n"),
    TagIn = #tag_resource_input{
        resource = FunctionArn,
        tags = #{<<"AddedBy">> => <<"smithy-erlang-demo">>}
    },
    case lambda_client:tag_resource(Config, TagIn) of
        {ok, _} -> io:format("  Tag added~n");
        {error, TagErr} -> erlang:error({tag_resource_failed, TagErr})
    end,
    ListTagsInput = #list_tags_input{resource = FunctionArn},
    case lambda_client:list_tags(Config, ListTagsInput) of
        {ok, #list_tags_output{tags = AllTags}} when is_map(AllTags) ->
            io:format("  Current tags (~p): ~p~n", [maps:size(AllTags), AllTags]),
            case maps:is_key(<<"AddedBy">>, AllTags) of
                true  -> io:format("  SUCCESS: Tag 'AddedBy' present before untag~n");
                false -> erlang:error({assertion_failed, {tag_not_present, <<"AddedBy">>}})
            end;
        {error, ListTagsErr} ->
            erlang:error({list_tags_failed, ListTagsErr})
    end,
    UntagIn = #untag_resource_input{
        resource = FunctionArn,
        tag_keys = [<<"AddedBy">>]
    },
    case lambda_client:untag_resource(Config, UntagIn) of
        {ok, _} -> io:format("  Tag removed~n");
        {error, UntagErr} -> erlang:error({untag_resource_failed, UntagErr})
    end,
    case lambda_client:list_tags(Config, ListTagsInput) of
        {ok, #list_tags_output{tags = PostTags}} when is_map(PostTags) ->
            case maps:is_key(<<"AddedBy">>, PostTags) of
                false -> io:format("  SUCCESS: Tag 'AddedBy' absent after untag~n");
                true  -> erlang:error({assertion_failed, {tag_still_present_after_untag, <<"AddedBy">>}})
            end;
        {error, PostTagsErr} ->
            erlang:error({list_tags_failed, PostTagsErr})
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 12. Cleanup: delete alias, then function
    %% ---------------------------------------------------------------
    io:format("--- 12. Cleanup ---~n"),
    DeleteAliasIn = #delete_alias_input{
        function_name = ?LAMBDA_FUNCTION_NAME,
        name = ?ALIAS_NAME
    },
    case lambda_client:delete_alias(Config, DeleteAliasIn) of
        {ok, _} -> io:format("  Alias '~s' deleted~n", [?ALIAS_NAME]);
        {error, DelAliasErr} -> erlang:error({delete_alias_failed, DelAliasErr})
    end,
    DeleteFnIn = #delete_function_input{function_name = ?LAMBDA_FUNCTION_NAME},
    case lambda_client:delete_function(Config, DeleteFnIn) of
        {ok, _} ->
            io:format("  Function '~s' deleted~n", [?LAMBDA_FUNCTION_NAME]);
        {error, DelFnErr} ->
            erlang:error({delete_function_failed, DelFnErr})
    end,
    io:format("~n"),

    io:format("=== Lambda Demo Complete ===~n"),
    ok.

client_config() ->
    #{
        region => <<"us-east-1">>,
        endpoint_prefix => <<"lambda">>,
        signing_name => <<"lambda">>,
        base_url => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    }.

load_lambda_zip() ->
    case file:read_file(?LAMBDA_ZIP_PATH) of
        {ok, ZipBytes} ->
            ZipBytes;
        {error, Reason} ->
            erlang:error({read_lambda_zip_failed, ?LAMBDA_ZIP_PATH, Reason})
    end.

create_demo_function(Config) ->
    io:format("--- CreateFunction ---~n"),
    ZipBytes = load_lambda_zip(),
    Input = #create_function_input{
        function_name = ?LAMBDA_FUNCTION_NAME,
        role = ?LAMBDA_ROLE_ARN,
        handler = <<"lambda_function.handler">>,
        runtime = python39,
        code = #function_code{zip_file = base64:encode(ZipBytes)},
        tags = #{
            <<"Name">> => <<"lambda-demo-function">>,
            <<"Environment">> => <<"demo">>,
            <<"Project">> => <<"smithy-erlang">>
        }
    },
    case lambda_client:create_function(Config, Input) of
        {ok, _} ->
            io:format("  SUCCESS: Function '~s' created~n", [?LAMBDA_FUNCTION_NAME]);
        {error, #resource_conflict_exception{}} ->
            io:format("  SUCCESS: Function '~s' already exists~n", [?LAMBDA_FUNCTION_NAME]);
        {error, Reason} ->
            erlang:error({create_function_failed, Reason})
    end,
    io:format("~n"),
    wait_demo_function_active(Config),
    ok.

wait_demo_function_active(Config) ->
    io:format("--- Wait FunctionActiveV2 ---~n"),
    Input = #get_function_input{function_name = ?LAMBDA_FUNCTION_NAME},
    case lambda_waiters:wait_function_active_v2(Config, Input, #{}) of
        ok ->
            io:format("  SUCCESS: Function '~s' is Active~n", [?LAMBDA_FUNCTION_NAME]);
        {ok, _} ->
            io:format("  SUCCESS: Function '~s' is Active~n", [?LAMBDA_FUNCTION_NAME]);
        {error, Reason} ->
            erlang:error({wait_function_active_failed, Reason})
    end,
    io:format("~n"),
    ok.

setup_infrastructure(Config) ->
    io:format("--- Setup infrastructure ---~n"),
    create_demo_function(Config),
    io:format("Infrastructure ready: Function=~s~n~n", [?LAMBDA_FUNCTION_NAME]),
    ok.

format_binary(undefined) -> <<"?">>;
format_binary(Value) when is_binary(Value) -> Value;
format_binary(Value) when is_atom(Value) -> atom_to_binary(Value, utf8);
format_binary(Value) -> io_lib:format("~p", [Value]).

invoke_with_retry(Config, Input, Attempts) when Attempts > 0 ->
    case lambda_client:invoke(Config, Input) of
        {ok, _} = Ok ->
            Ok;
        {error, {service_exception, _, Msg, _} = Err} when is_binary(Msg), Attempts > 1 ->
            case binary:match(Msg, <<"Timeout">>) of
                nomatch ->
                    {error, Err};
                _ ->
                    io:format("  Alias invoke timed out, retrying (~p attempts left)...~n",
                              [Attempts - 1]),
                    timer:sleep(3000),
                    invoke_with_retry(Config, Input, Attempts - 1)
            end;
        Other ->
            Other
    end;
invoke_with_retry(_Config, _Input, 0) ->
    {error, invoke_alias_retries_exhausted}.
