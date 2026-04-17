-module(aws_demo_app).
-export([run/0]).

-define(LAMBDA_FUNCTION_NAME, <<"lambda-demo-function">>).
-define(ALIAS_NAME, <<"demo">>).
-define(OPTIONS, #{enable_retry => false}).

run() ->
    io:format("~n=== Lambda Demo: Full Function Lifecycle ===~n~n"),

    Config = #{
        endpoint   => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        region     => <<"us-east-1">>,
        service    => <<"lambda">>,
        credentials => #{
            access_key_id     => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    },
    {ok, Client} = aws_lambda_client:new(Config),
    io:format("Client created~n~n"),

    FunctionArn = <<"arn:aws:lambda:us-east-1:000000000000:function:",
                    ?LAMBDA_FUNCTION_NAME/binary>>,

    %% ---------------------------------------------------------------
    %% 1. Account overview (service-level operation)
    %% ---------------------------------------------------------------
    io:format("--- 1. GetAccountSettings ---~n"),
    case aws_lambda_client:get_account_settings(Client, #{}, ?OPTIONS) of
        {ok, AcctOut} ->
            case maps:get(<<"AccountUsage">>, AcctOut, undefined) of
                undefined ->
                    io:format("  No usage data~n");
                AcctUsage ->
                    io:format("  Functions deployed : ~p~n",
                              [maps:get(<<"FunctionCount">>, AcctUsage, 0)]),
                    io:format("  Total code size    : ~p bytes~n",
                              [maps:get(<<"TotalCodeSize">>, AcctUsage, 0)])
            end,
            case maps:get(<<"AccountLimit">>, AcctOut, undefined) of
                undefined -> ok;
                AcctLimit ->
                    io:format("  Concurrent limit   : ~p~n",
                              [maps:get(<<"ConcurrentExecutions">>, AcctLimit, 0)])
            end;
        {error, AcctErr} ->
            erlang:error({get_account_settings_failed, AcctErr})
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 2. Survey — list all functions in the account
    %% ---------------------------------------------------------------
    io:format("--- 2. ListFunctions ---~n"),
    case aws_lambda_client:list_functions(Client, #{}, ?OPTIONS) of
        {ok, ListFnsOut} ->
            Fns = maps:get(<<"Functions">>, ListFnsOut, []),
            case Fns of
                [] -> erlang:error({assertion_failed, empty_function_list});
                _  -> io:format("  SUCCESS: Found ~p function(s)~n", [length(Fns)])
            end,
            FnNames = [maps:get(<<"FunctionName">>, F, <<>>) || F <- Fns],
            case lists:member(?LAMBDA_FUNCTION_NAME, FnNames) of
                true  -> io:format("  SUCCESS: Function '~s' found~n", [?LAMBDA_FUNCTION_NAME]);
                false -> erlang:error({assertion_failed, {function_not_found, ?LAMBDA_FUNCTION_NAME}})
            end,
            lists:foreach(
                fun(Fn) ->
                    FnName    = maps:get(<<"FunctionName">>, Fn, <<"?">>),
                    FnRuntime = maps:get(<<"Runtime">>, Fn, <<"?">>),
                    FnSize    = maps:get(<<"CodeSize">>, Fn, 0),
                    io:format("    ~s  runtime=~s  size=~p bytes~n",
                              [FnName, FnRuntime, FnSize])
                end,
                Fns
            );
        {error, ListFnsErr} ->
            erlang:error({list_functions_failed, ListFnsErr})
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 3. Inspect the demo function
    %% ---------------------------------------------------------------
    io:format("--- 3. GetFunction ---~n"),
    case aws_lambda_client:get_function(
             Client, #{<<"FunctionName">> => ?LAMBDA_FUNCTION_NAME}, ?OPTIONS) of
        {ok, GetFnOut} ->
            GetFnCfg = maps:get(<<"Configuration">>, GetFnOut, #{}),
            State = maps:get(<<"State">>, GetFnCfg, <<>>),
            case State of
                <<"Active">> -> io:format("  SUCCESS: Function is Active~n");
                Other        -> erlang:error({assertion_failed, {expected_active, Other}})
            end,
            io:format("  Runtime  : ~s~n",
                      [maps:get(<<"Runtime">>, GetFnCfg, <<"?">>)]),
            io:format("  Handler  : ~s~n",
                      [maps:get(<<"Handler">>, GetFnCfg, <<"?">>)]),
            io:format("  Memory   : ~p MB~n",
                      [maps:get(<<"MemorySize">>, GetFnCfg, 0)]),
            io:format("  State    : ~s~n", [State]);
        {error, GetFnErr} ->
            erlang:error({get_function_failed, GetFnErr})
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 4. Invoke the function
    %% ---------------------------------------------------------------
    io:format("--- 4. Invoke ---~n"),
    InvokeIn = #{
        <<"FunctionName">> => ?LAMBDA_FUNCTION_NAME,
        <<"Payload">>      => <<"{\"hello\": \"from smithy-erlang\"}">>
    },
    case aws_lambda_client:invoke(Client, InvokeIn, ?OPTIONS) of
        {ok, InvokeOut} ->
            InvStatus = maps:get(<<"StatusCode">>, InvokeOut, 0),
            case InvStatus of
                200 -> io:format("  SUCCESS: Invocation returned 200~n");
                _   -> erlang:error({assertion_failed, {expected_200, InvStatus}})
            end,
            io:format("  Status code : ~p~n", [InvStatus]),
            case maps:get(<<"Payload">>, InvokeOut, undefined) of
                undefined ->
                    erlang:error({assertion_failed, invoke_returned_empty_payload});
                InvPayload ->
                    io:format("  Payload     : ~s~n", [InvPayload])
            end;
        {error, InvokeErr} ->
            erlang:error({invoke_failed, InvokeErr})
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 5. Update the function description
    %% ---------------------------------------------------------------
    io:format("--- 5. UpdateFunctionConfiguration ---~n"),
    UpdateCfgIn = #{
        <<"FunctionName">> => ?LAMBDA_FUNCTION_NAME,
        <<"Description">>  => <<"Updated by smithy-erlang demo">>
    },
    case aws_lambda_client:update_function_configuration(Client, UpdateCfgIn, ?OPTIONS) of
        {ok, UpdateCfgOut} ->
            io:format("  SUCCESS: Description : ~s~n",
                      [maps:get(<<"Description">>, UpdateCfgOut, <<>>)]);
        {error, UpdateCfgErr} ->
            erlang:error({update_function_configuration_failed, UpdateCfgErr})
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 6. Publish a numbered version
    %% ---------------------------------------------------------------
    io:format("--- 6. PublishVersion ---~n"),
    PubVerIn = #{
        <<"FunctionName">> => ?LAMBDA_FUNCTION_NAME,
        <<"Description">>  => <<"v1 published by smithy-erlang demo">>
    },
    PublishedVersion =
        case aws_lambda_client:publish_version(Client, PubVerIn, ?OPTIONS) of
            {ok, PubVerOut} ->
                Ver = maps:get(<<"Version">>, PubVerOut, <<>>),
                case byte_size(Ver) > 0 of
                    true  -> io:format("  SUCCESS: Published version : ~s~n", [Ver]);
                    false -> erlang:error({assertion_failed, publish_version_returned_empty})
                end,
                Ver;
            {error, PubVerErr} ->
                erlang:error({publish_version_failed, PubVerErr})
        end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 7. List all published versions
    %% ---------------------------------------------------------------
    io:format("--- 7. ListVersionsByFunction ---~n"),
    ListVerIn = #{<<"FunctionName">> => ?LAMBDA_FUNCTION_NAME},
    case aws_lambda_client:list_versions_by_function(Client, ListVerIn, ?OPTIONS) of
        {ok, ListVerOut} ->
            Versions = maps:get(<<"Versions">>, ListVerOut, []),
            io:format("  Found ~p version(s):~n", [length(Versions)]),
            case length(Versions) >= 2 of
                true  -> io:format("  SUCCESS: >= 2 versions (original + published)~n");
                false -> erlang:error({assertion_failed, {expected_at_least_2_versions, length(Versions)}})
            end,
            lists:foreach(
                fun(V) ->
                    VNum  = maps:get(<<"Version">>, V, <<"?">>),
                    VDesc = maps:get(<<"Description">>, V, <<>>),
                    io:format("    ~s  ~s~n", [VNum, VDesc])
                end,
                Versions
            );
        {error, ListVerErr} ->
            erlang:error({list_versions_by_function_failed, ListVerErr})
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 8. Create an alias pointing at the published version
    %% ---------------------------------------------------------------
    io:format("--- 8. CreateAlias ---~n"),
    CreateAliasIn = #{
        <<"FunctionName">>    => ?LAMBDA_FUNCTION_NAME,
        <<"Name">>            => ?ALIAS_NAME,
        <<"FunctionVersion">> => PublishedVersion,
        <<"Description">>     => <<"Stable release alias">>
    },
    case aws_lambda_client:create_alias(Client, CreateAliasIn, ?OPTIONS) of
        {ok, CreateAliasOut} ->
            io:format("  SUCCESS: Alias '~s' -> version ~s~n",
                      [maps:get(<<"Name">>, CreateAliasOut, <<>>),
                       maps:get(<<"FunctionVersion">>, CreateAliasOut, <<>>)]);
        {error, CreateAliasErr} ->
            erlang:error({create_alias_failed, CreateAliasErr})
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 9. List aliases
    %% ---------------------------------------------------------------
    io:format("--- 9. ListAliases ---~n"),
    ListAliasIn = #{<<"FunctionName">> => ?LAMBDA_FUNCTION_NAME},
    case aws_lambda_client:list_aliases(Client, ListAliasIn, ?OPTIONS) of
        {ok, ListAliasOut} ->
            Aliases = maps:get(<<"Aliases">>, ListAliasOut, []),
            case Aliases of
                [] -> erlang:error({assertion_failed, empty_alias_list});
                _  -> io:format("  SUCCESS: Found ~p alias(es)~n", [length(Aliases)])
            end,
            AliasNames = [maps:get(<<"Name">>, A, <<>>) || A <- Aliases],
            case lists:member(?ALIAS_NAME, AliasNames) of
                true  -> io:format("  SUCCESS: Alias '~s' found~n", [?ALIAS_NAME]);
                false -> erlang:error({assertion_failed, {alias_not_found, ?ALIAS_NAME}})
            end,
            lists:foreach(
                fun(A) ->
                    AName = maps:get(<<"Name">>, A, <<"?">>),
                    AVer  = maps:get(<<"FunctionVersion">>, A, <<"?">>),
                    io:format("    ~s -> ~s~n", [AName, AVer])
                end,
                Aliases
            );
        {error, ListAliasErr} ->
            erlang:error({list_aliases_failed, ListAliasErr})
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 10. Invoke via alias
    %% ---------------------------------------------------------------
    io:format("--- 10. Invoke (via alias '~s') ---~n", [?ALIAS_NAME]),
    InvokeAliasIn = #{
        <<"FunctionName">> => ?LAMBDA_FUNCTION_NAME,
        <<"Qualifier">>    => ?ALIAS_NAME,
        <<"Payload">>      => <<"{\"source\": \"alias invocation\"}">>
    },
    case aws_lambda_client:invoke(Client, InvokeAliasIn, ?OPTIONS) of
        {ok, InvokeAliasOut} ->
            AliasInvStatus = maps:get(<<"StatusCode">>, InvokeAliasOut, 0),
            case AliasInvStatus of
                200 -> io:format("  SUCCESS: Alias invocation returned 200~n");
                _   -> erlang:error({assertion_failed, {expected_200, AliasInvStatus}})
            end,
            io:format("  Status code : ~p~n", [AliasInvStatus]),
            case maps:get(<<"Payload">>, InvokeAliasOut, undefined) of
                undefined -> ok;
                AliasPayload -> io:format("  Payload     : ~s~n", [AliasPayload])
            end;
        {error, InvokeAliasErr} ->
            erlang:error({invoke_failed, InvokeAliasErr})
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 11. Tag management
    %% ---------------------------------------------------------------
    io:format("--- 11. TagResource / ListTags / UntagResource ---~n"),
    TagIn = #{
        <<"Resource">> => FunctionArn,
        <<"Tags">>     => #{<<"AddedBy">> => <<"smithy-erlang-demo">>}
    },
    case aws_lambda_client:tag_resource(Client, TagIn, ?OPTIONS) of
        {ok, _} -> io:format("  Tag added~n");
        {error, TagErr} -> erlang:error({tag_resource_failed, TagErr})
    end,
    case aws_lambda_client:list_tags(
             Client, #{<<"Resource">> => FunctionArn}, ?OPTIONS) of
        {ok, ListTagsOut} ->
            AllTags = maps:get(<<"Tags">>, ListTagsOut, #{}),
            io:format("  Current tags (~p): ~p~n", [maps:size(AllTags), AllTags]),
            case maps:is_key(<<"AddedBy">>, AllTags) of
                true  -> io:format("  SUCCESS: Tag 'AddedBy' present before untag~n");
                false -> erlang:error({assertion_failed, {tag_not_present, <<"AddedBy">>}})
            end;
        {error, ListTagsErr} ->
            erlang:error({list_tags_failed, ListTagsErr})
    end,
    UntagIn = #{
        <<"Resource">> => FunctionArn,
        <<"TagKeys">>  => [<<"AddedBy">>]
    },
    case aws_lambda_client:untag_resource(Client, UntagIn, ?OPTIONS) of
        {ok, _} -> io:format("  Tag removed~n");
        {error, UntagErr} -> erlang:error({untag_resource_failed, UntagErr})
    end,
    case aws_lambda_client:list_tags(
             Client, #{<<"Resource">> => FunctionArn}, ?OPTIONS) of
        {ok, PostUntagOut} ->
            PostTags = maps:get(<<"Tags">>, PostUntagOut, #{}),
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
    DeleteAliasIn = #{
        <<"FunctionName">> => ?LAMBDA_FUNCTION_NAME,
        <<"Name">>         => ?ALIAS_NAME
    },
    case aws_lambda_client:delete_alias(Client, DeleteAliasIn, ?OPTIONS) of
        {ok, _} -> io:format("  Alias '~s' deleted~n", [?ALIAS_NAME]);
        {error, DelAliasErr} -> erlang:error({delete_alias_failed, DelAliasErr})
    end,
    DeleteFnIn = #{<<"FunctionName">> => ?LAMBDA_FUNCTION_NAME},
    case aws_lambda_client:delete_function(Client, DeleteFnIn, ?OPTIONS) of
        {ok, _} ->
            io:format("  Function '~s' deleted~n", [?LAMBDA_FUNCTION_NAME]);
        {error, DelFnErr} ->
            erlang:error({delete_function_failed, DelFnErr})
    end,
    io:format("~n"),

    io:format("=== Lambda Demo Complete ===~n"),
    ok.
