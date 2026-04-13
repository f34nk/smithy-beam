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
            io:format("  ERROR: ~p~n", [AcctErr])
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 2. Survey — list all functions in the account
    %%    (resource-bound operation, now available via TopDownIndex fix)
    %% ---------------------------------------------------------------
    io:format("--- 2. ListFunctions ---~n"),
    case aws_lambda_client:list_functions(Client, #{}, ?OPTIONS) of
        {ok, ListFnsOut} ->
            Fns = maps:get(<<"Functions">>, ListFnsOut, []),
            io:format("  Found ~p function(s):~n", [length(Fns)]),
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
            io:format("  ERROR: ~p~n", [ListFnsErr])
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 3. Inspect the demo function
    %%    (resource-bound operation, now available via TopDownIndex fix)
    %% ---------------------------------------------------------------
    io:format("--- 3. GetFunction ---~n"),
    case aws_lambda_client:get_function(
             Client, #{<<"FunctionName">> => ?LAMBDA_FUNCTION_NAME}, ?OPTIONS) of
        {ok, GetFnOut} ->
            GetFnCfg = maps:get(<<"Configuration">>, GetFnOut, #{}),
            io:format("  Runtime  : ~s~n",
                      [maps:get(<<"Runtime">>, GetFnCfg, <<"?">>)]),
            io:format("  Handler  : ~s~n",
                      [maps:get(<<"Handler">>, GetFnCfg, <<"?">>)]),
            io:format("  Memory   : ~p MB~n",
                      [maps:get(<<"MemorySize">>, GetFnCfg, 0)]),
            io:format("  State    : ~s~n",
                      [maps:get(<<"State">>, GetFnCfg, <<"?">>)]);
        {error, GetFnErr} ->
            io:format("  ERROR: ~p~n", [GetFnErr])
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 4. Invoke the function
    %%    (resource-bound operation, now available via TopDownIndex fix)
    %% ---------------------------------------------------------------
    io:format("--- 4. Invoke ---~n"),
    InvokeIn = #{
        <<"FunctionName">> => ?LAMBDA_FUNCTION_NAME,
        <<"Payload">>      => <<"{\"hello\": \"from smithy-erlang\"}">>
    },
    case aws_lambda_client:invoke(Client, InvokeIn, ?OPTIONS) of
        {ok, InvokeOut} ->
            InvStatus = maps:get(<<"StatusCode">>, InvokeOut, 0),
            io:format("  Status code : ~p~n", [InvStatus]),
            case maps:get(<<"Payload">>, InvokeOut, undefined) of
                undefined -> ok;
                InvPayload -> io:format("  Payload     : ~s~n", [InvPayload])
            end;
        {error, InvokeErr} ->
            io:format("  ERROR: ~p~n", [InvokeErr])
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 5. Update the function description
    %%    (resource-bound operation, now available via TopDownIndex fix)
    %% ---------------------------------------------------------------
    io:format("--- 5. UpdateFunctionConfiguration ---~n"),
    UpdateCfgIn = #{
        <<"FunctionName">> => ?LAMBDA_FUNCTION_NAME,
        <<"Description">>  => <<"Updated by smithy-erlang demo">>
    },
    case aws_lambda_client:update_function_configuration(Client, UpdateCfgIn, ?OPTIONS) of
        {ok, UpdateCfgOut} ->
            io:format("  Description : ~s~n",
                      [maps:get(<<"Description">>, UpdateCfgOut, <<>>)]);
        {error, UpdateCfgErr} ->
            io:format("  ERROR: ~p~n", [UpdateCfgErr])
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 6. Publish a numbered version
    %%    (resource-bound operation, now available via TopDownIndex fix)
    %% ---------------------------------------------------------------
    io:format("--- 6. PublishVersion ---~n"),
    PubVerIn = #{
        <<"FunctionName">> => ?LAMBDA_FUNCTION_NAME,
        <<"Description">>  => <<"v1 published by smithy-erlang demo">>
    },
    PublishedVersion =
        case aws_lambda_client:publish_version(Client, PubVerIn, ?OPTIONS) of
            {ok, PubVerOut} ->
                Ver = maps:get(<<"Version">>, PubVerOut, <<"$LATEST">>),
                io:format("  Published version : ~s~n", [Ver]),
                Ver;
            {error, PubVerErr} ->
                io:format("  ERROR: ~p~n", [PubVerErr]),
                <<"$LATEST">>
        end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 7. List all published versions
    %%    (resource-bound operation, now available via TopDownIndex fix)
    %% ---------------------------------------------------------------
    io:format("--- 7. ListVersionsByFunction ---~n"),
    ListVerIn = #{<<"FunctionName">> => ?LAMBDA_FUNCTION_NAME},
    case aws_lambda_client:list_versions_by_function(Client, ListVerIn, ?OPTIONS) of
        {ok, ListVerOut} ->
            Versions = maps:get(<<"Versions">>, ListVerOut, []),
            io:format("  Found ~p version(s):~n", [length(Versions)]),
            lists:foreach(
                fun(V) ->
                    VNum  = maps:get(<<"Version">>, V, <<"?">>),
                    VDesc = maps:get(<<"Description">>, V, <<>>),
                    io:format("    ~s  ~s~n", [VNum, VDesc])
                end,
                Versions
            );
        {error, ListVerErr} ->
            io:format("  ERROR: ~p~n", [ListVerErr])
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 8. Create an alias pointing at the published version
    %%    (resource-bound operation, now available via TopDownIndex fix)
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
            io:format("  Alias '~s' -> version ~s~n",
                      [maps:get(<<"Name">>, CreateAliasOut, <<>>),
                       maps:get(<<"FunctionVersion">>, CreateAliasOut, <<>>)]);
        {error, CreateAliasErr} ->
            io:format("  ERROR: ~p~n", [CreateAliasErr])
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 9. List aliases
    %%    (resource-bound operation, now available via TopDownIndex fix)
    %% ---------------------------------------------------------------
    io:format("--- 9. ListAliases ---~n"),
    ListAliasIn = #{<<"FunctionName">> => ?LAMBDA_FUNCTION_NAME},
    case aws_lambda_client:list_aliases(Client, ListAliasIn, ?OPTIONS) of
        {ok, ListAliasOut} ->
            Aliases = maps:get(<<"Aliases">>, ListAliasOut, []),
            io:format("  Found ~p alias(es):~n", [length(Aliases)]),
            lists:foreach(
                fun(A) ->
                    AName = maps:get(<<"Name">>, A, <<"?">>),
                    AVer  = maps:get(<<"FunctionVersion">>, A, <<"?">>),
                    io:format("    ~s -> ~s~n", [AName, AVer])
                end,
                Aliases
            );
        {error, ListAliasErr} ->
            io:format("  ERROR: ~p~n", [ListAliasErr])
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 10. Invoke via alias
    %%     (resource-bound operation, now available via TopDownIndex fix)
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
            io:format("  Status code : ~p~n", [AliasInvStatus]),
            case maps:get(<<"Payload">>, InvokeAliasOut, undefined) of
                undefined -> ok;
                AliasPayload -> io:format("  Payload     : ~s~n", [AliasPayload])
            end;
        {error, InvokeAliasErr} ->
            io:format("  ERROR: ~p~n", [InvokeAliasErr])
    end,
    io:format("~n"),

    %% ---------------------------------------------------------------
    %% 11. Tag management (simplified)
    %% ---------------------------------------------------------------
    io:format("--- 11. TagResource / ListTags / UntagResource ---~n"),
    TagIn = #{
        <<"Resource">> => FunctionArn,
        <<"Tags">>     => #{<<"AddedBy">> => <<"smithy-erlang-demo">>}
    },
    case aws_lambda_client:tag_resource(Client, TagIn, ?OPTIONS) of
        {ok, _} -> io:format("  Tag added~n");
        {error, TagErr} -> io:format("  Tag ERROR: ~p~n", [TagErr])
    end,
    case aws_lambda_client:list_tags(
             Client, #{<<"Resource">> => FunctionArn}, ?OPTIONS) of
        {ok, ListTagsOut} ->
            AllTags = maps:get(<<"Tags">>, ListTagsOut, #{}),
            io:format("  Current tags (~p): ~p~n", [maps:size(AllTags), AllTags]);
        {error, ListTagsErr} ->
            io:format("  ListTags ERROR: ~p~n", [ListTagsErr])
    end,
    UntagIn = #{
        <<"Resource">> => FunctionArn,
        <<"TagKeys">>  => [<<"AddedBy">>]
    },
    case aws_lambda_client:untag_resource(Client, UntagIn, ?OPTIONS) of
        {ok, _} -> io:format("  Tag removed~n");
        {error, UntagErr} -> io:format("  Untag ERROR: ~p~n", [UntagErr])
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
        {error, DelAliasErr} -> io:format("  DeleteAlias ERROR: ~p~n", [DelAliasErr])
    end,
    DeleteFnIn = #{<<"FunctionName">> => ?LAMBDA_FUNCTION_NAME},
    case aws_lambda_client:delete_function(Client, DeleteFnIn, ?OPTIONS) of
        {ok, _} ->
            io:format("  Function '~s' deleted~n", [?LAMBDA_FUNCTION_NAME]);
        {error, DelFnErr} ->
            io:format("  DeleteFunction ERROR: ~p~n", [DelFnErr])
    end,
    io:format("~n"),

    io:format("=== Lambda Demo Complete ===~n"),
    ok.
