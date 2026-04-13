-module(aws_demo_app).
-export([run/0]).

-define(TEST_PARAM_NAME, <<"/demo/test/param">>).
-define(TEST_PARAM_PATH, <<"/demo/test">>).

run() ->
    io:format("~n=== Running SSM Client Application ===~n~n"),

    %% Create SSM client instance with AWS credentials
    io:format("Creating SSM client...~n"),
    Config = #{
        endpoint => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        region => <<"us-east-1">>,
        service => <<"ssm">>,  %% Required for SigV4 signing with custom endpoints
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    },
    {ok, Client} = aws_ssm_client:new(Config),
    io:format("Client created successfully~n~n"),

    %% 1. Describe parameters to see what was created by Terraform
    io:format("--- DescribeParameters ---~n"),
    case aws_ssm_client:describe_parameters(Client, #{}, #{enable_retry => false}) of
        {ok, DescribeOutput} ->
            Parameters = maps:get(<<"Parameters">>, DescribeOutput, []),
            io:format("SUCCESS: Found ~p parameter(s)~n", [length(Parameters)]),
            lists:foreach(
                fun(Param) ->
                    Name = maps:get(<<"Name">>, Param, <<"unknown">>),
                    Type = maps:get(<<"Type">>, Param, <<"unknown">>),
                    io:format("    ~s (~s)~n", [Name, Type])
                end,
                Parameters
            );
        {error, DescribeError} ->
            io:format("ERROR: ~p~n", [DescribeError])
    end,
    io:format("~n"),

    %% 2. Get a specific parameter
    io:format("--- GetParameter ---~n"),
    GetInput = #{
        <<"Name">> => <<"/demo/database/host">>,
        <<"WithDecryption">> => true
    },
    case aws_ssm_client:get_parameter(Client, GetInput, #{enable_retry => false}) of
        {ok, GetOutput} ->
            Param = maps:get(<<"Parameter">>, GetOutput, #{}),
            ParamName = maps:get(<<"Name">>, Param, <<"unknown">>),
            ParamValue = maps:get(<<"Value">>, Param, <<"unknown">>),
            ParamType = maps:get(<<"Type">>, Param, <<"unknown">>),
            io:format("SUCCESS: Got parameter~n"),
            io:format("    Name: ~s~n", [ParamName]),
            io:format("    Value: ~s~n", [ParamValue]),
            io:format("    Type: ~s~n", [ParamType]);
        {error, GetError} ->
            io:format("ERROR: ~p~n", [GetError])
    end,
    io:format("~n"),

    %% 3. Get multiple parameters at once
    io:format("--- GetParameters ---~n"),
    GetMultiInput = #{
        <<"Names">> => [
            <<"/demo/database/host">>,
            <<"/demo/database/port">>,
            <<"/demo/database/name">>
        ],
        <<"WithDecryption">> => true
    },
    case aws_ssm_client:get_parameters(Client, GetMultiInput, #{enable_retry => false}) of
        {ok, GetMultiOutput} ->
            MultiParams = maps:get(<<"Parameters">>, GetMultiOutput, []),
            InvalidParams = maps:get(<<"InvalidParameters">>, GetMultiOutput, []),
            io:format("SUCCESS: Got ~p parameter(s), ~p invalid~n", 
                      [length(MultiParams), length(InvalidParams)]),
            lists:foreach(
                fun(P) ->
                    PName = maps:get(<<"Name">>, P, <<"unknown">>),
                    PValue = maps:get(<<"Value">>, P, <<"unknown">>),
                    io:format("    ~s = ~s~n", [PName, PValue])
                end,
                MultiParams
            );
        {error, GetMultiError} ->
            io:format("ERROR: ~p~n", [GetMultiError])
    end,
    io:format("~n"),

    %% 4. Get parameters by path
    io:format("--- GetParametersByPath ---~n"),
    PathInput = #{
        <<"Path">> => <<"/demo/database">>,
        <<"Recursive">> => true,
        <<"WithDecryption">> => true
    },
    case aws_ssm_client:get_parameters_by_path(Client, PathInput, #{enable_retry => false}) of
        {ok, PathOutput} ->
            PathParams = maps:get(<<"Parameters">>, PathOutput, []),
            io:format("SUCCESS: Found ~p parameter(s) under /demo/database~n", [length(PathParams)]),
            lists:foreach(
                fun(P) ->
                    PName = maps:get(<<"Name">>, P, <<"unknown">>),
                    PValue = maps:get(<<"Value">>, P, <<"unknown">>),
                    io:format("    ~s = ~s~n", [PName, PValue])
                end,
                PathParams
            );
        {error, PathError} ->
            io:format("ERROR: ~p~n", [PathError])
    end,
    io:format("~n"),

    %% 5. Get the secure string parameter
    io:format("--- GetParameter (SecureString) ---~n"),
    SecureInput = #{
        <<"Name">> => <<"/demo/api/key">>,
        <<"WithDecryption">> => true
    },
    case aws_ssm_client:get_parameter(Client, SecureInput, #{enable_retry => false}) of
        {ok, SecureOutput} ->
            SecureParam = maps:get(<<"Parameter">>, SecureOutput, #{}),
            SecureName = maps:get(<<"Name">>, SecureParam, <<"unknown">>),
            SecureValue = maps:get(<<"Value">>, SecureParam, <<"unknown">>),
            SecureType = maps:get(<<"Type">>, SecureParam, <<"unknown">>),
            io:format("SUCCESS: Got secure parameter~n"),
            io:format("    Name: ~s~n", [SecureName]),
            io:format("    Value: ~s (decrypted)~n", [SecureValue]),
            io:format("    Type: ~s~n", [SecureType]);
        {error, SecureError} ->
            io:format("ERROR: ~p~n", [SecureError])
    end,
    io:format("~n"),

    %% 6. Create a new parameter
    io:format("--- PutParameter ---~n"),
    PutInput = #{
        <<"Name">> => ?TEST_PARAM_NAME,
        <<"Value">> => <<"test-value-from-erlang">>,
        <<"Type">> => <<"String">>,
        <<"Description">> => <<"Test parameter created by smithy-erlang demo">>,
        <<"Tags">> => [
            #{<<"Key">> => <<"Environment">>, <<"Value">> => <<"demo">>},
            #{<<"Key">> => <<"CreatedBy">>, <<"Value">> => <<"smithy-erlang">>}
        ]
    },
    ParamCreated = case aws_ssm_client:put_parameter(Client, PutInput, #{enable_retry => false}) of
        {ok, PutOutput} ->
            Version = maps:get(<<"Version">>, PutOutput, 0),
            io:format("SUCCESS: Created parameter, version: ~p~n", [Version]),
            true;
        {error, PutError} ->
            io:format("ERROR: ~p~n", [PutError]),
            false
    end,
    io:format("~n"),

    case ParamCreated of
        false ->
            io:format("Cannot continue without test parameter~n");
        true ->
            %% 7. Get the newly created parameter
            io:format("--- GetParameter (verify creation) ---~n"),
            VerifyInput = #{
                <<"Name">> => ?TEST_PARAM_NAME,
                <<"WithDecryption">> => true
            },
            case aws_ssm_client:get_parameter(Client, VerifyInput, #{enable_retry => false}) of
                {ok, VerifyOutput} ->
                    VerifyParam = maps:get(<<"Parameter">>, VerifyOutput, #{}),
                    VerifyValue = maps:get(<<"Value">>, VerifyParam, <<"unknown">>),
                    io:format("SUCCESS: Value = ~s~n", [VerifyValue]);
                {error, VerifyError} ->
                    io:format("ERROR: ~p~n", [VerifyError])
            end,
            io:format("~n"),

            %% 8. Update the parameter (overwrite)
            io:format("--- PutParameter (update) ---~n"),
            UpdateInput = #{
                <<"Name">> => ?TEST_PARAM_NAME,
                <<"Value">> => <<"updated-value-from-erlang">>,
                <<"Type">> => <<"String">>,
                <<"Overwrite">> => true
            },
            case aws_ssm_client:put_parameter(Client, UpdateInput, #{enable_retry => false}) of
                {ok, UpdateOutput} ->
                    UpdateVersion = maps:get(<<"Version">>, UpdateOutput, 0),
                    io:format("SUCCESS: Updated parameter, version: ~p~n", [UpdateVersion]);
                {error, UpdateError} ->
                    io:format("ERROR: ~p~n", [UpdateError])
            end,
            io:format("~n"),

            %% 9. Get parameter history
            io:format("--- GetParameterHistory ---~n"),
            HistoryInput = #{
                <<"Name">> => ?TEST_PARAM_NAME,
                <<"WithDecryption">> => true
            },
            case aws_ssm_client:get_parameter_history(Client, HistoryInput, #{enable_retry => false}) of
                {ok, HistoryOutput} ->
                    History = maps:get(<<"Parameters">>, HistoryOutput, []),
                    io:format("SUCCESS: Found ~p version(s)~n", [length(History)]),
                    lists:foreach(
                        fun(H) ->
                            HVersion = maps:get(<<"Version">>, H, 0),
                            HValue = maps:get(<<"Value">>, H, <<"unknown">>),
                            io:format("    Version ~p: ~s~n", [HVersion, HValue])
                        end,
                        History
                    );
                {error, HistoryError} ->
                    io:format("ERROR: ~p~n", [HistoryError])
            end,
            io:format("~n"),

            %% 10. List tags for a resource
            io:format("--- ListTagsForResource ---~n"),
            %% SSM uses the parameter name as the resource ID
            TagsInput = #{
                <<"ResourceType">> => <<"Parameter">>,
                <<"ResourceId">> => ?TEST_PARAM_NAME
            },
            case aws_ssm_client:list_tags_for_resource(Client, TagsInput, #{enable_retry => false}) of
                {ok, TagsOutput} ->
                    Tags = maps:get(<<"TagList">>, TagsOutput, []),
                    io:format("SUCCESS: Found ~p tag(s)~n", [length(Tags)]),
                    lists:foreach(
                        fun(Tag) ->
                            Key = maps:get(<<"Key">>, Tag, <<"unknown">>),
                            Value = maps:get(<<"Value">>, Tag, <<"unknown">>),
                            io:format("    ~s = ~s~n", [Key, Value])
                        end,
                        Tags
                    );
                {error, TagsError} ->
                    io:format("ERROR: ~p~n", [TagsError])
            end,
            io:format("~n"),

            %% 11. Delete the test parameter
            io:format("--- DeleteParameter ---~n"),
            DeleteInput = #{<<"Name">> => ?TEST_PARAM_NAME},
            case aws_ssm_client:delete_parameter(Client, DeleteInput, #{enable_retry => false}) of
                {ok, _} ->
                    io:format("SUCCESS: Parameter deleted~n");
                {error, DeleteError} ->
                    io:format("ERROR: ~p~n", [DeleteError])
            end,
            io:format("~n"),

            %% 12. Verify deletion
            io:format("--- GetParameter (verify deletion) ---~n"),
            case aws_ssm_client:get_parameter(Client, VerifyInput, #{enable_retry => false}) of
                {ok, _} ->
                    io:format("UNEXPECTED: Parameter still exists~n");
                {error, {aws_error, 400, <<"ParameterNotFound">>, _}} ->
                    io:format("SUCCESS: Parameter confirmed deleted~n");
                {error, DeleteVerifyError} ->
                    %% Any error likely means deleted
                    io:format("Parameter not found (deleted): ~p~n", [DeleteVerifyError])
            end
    end,

    io:format("~n=== SSM Client Application Complete ===~n"),
    ok.
