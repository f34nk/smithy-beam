-module(demo_app).
-export([run/0]).

-include("rds_types.hrl").

-define(TEST_PARAM_GROUP_NAME, <<"rds-demo-test-param-group">>).

run() ->
    io:format("~n=== Running RDS Client Application ===~n~n"),
    io:format("Note: LocalStack free tier has limited RDS support.~n"),
    io:format("Some operations may return 501 errors, but client API calls work correctly.~n~n"),

    Config = client_config(),
    io:format("RDS client configured for ~s~n~n", [maps:get(base_url, Config)]),

    %% 1. Describe account attributes
    io:format("--- DescribeAccountAttributes ---~n"),
    case rds_client:describe_account_attributes(
        Config, #describe_account_attributes_input{}) of
        {ok, #describe_account_attributes_output{account_quotas = Quotas}} ->
            io:format("SUCCESS: Found ~p account quota(s)~n", [length(Quotas)]),
            lists:foreach(
                fun(#account_quota{account_quota_name = Name, used = Used, max = Max}) ->
                    io:format("    ~s: ~p / ~p~n", [Name, Used, Max])
                end,
                lists:sublist(Quotas, 5)
            ),
            case length(Quotas) > 5 of
                true -> io:format("    ... and ~p more~n", [length(Quotas) - 5]);
                false -> ok
            end;
        {error, {unknown_error, 501, _}} ->
            io:format("INFO: DescribeAccountAttributes not supported (expected in LocalStack free tier)~n");
        {error, AcctError} ->
            io:format("ERROR (expected in LocalStack free tier): ~p~n", [AcctError])
    end,
    io:format("~n"),

    %% 2. Describe DB subnet groups
    io:format("--- DescribeDBSubnetGroups ---~n"),
    case rds_client:describe_db_subnet_groups(
        Config, #describe_db_subnet_groups_input{}) of
        {ok, #describe_db_subnet_groups_output{db_subnet_groups = SubnetGroups}} ->
            io:format("SUCCESS: Found ~p DB subnet group(s)~n", [length(SubnetGroups)]),
            lists:foreach(
                fun(#db_subnet_group{db_subnet_group_name = Name, subnet_group_status = Status}) ->
                    io:format("    ~s (Status: ~s)~n", [Name, Status])
                end,
                SubnetGroups
            );
        {error, {unknown_error, 501, _}} ->
            io:format("INFO: DescribeDBSubnetGroups not supported (expected in LocalStack free tier)~n");
        {error, SubnetError} ->
            io:format("ERROR (expected in LocalStack free tier): ~p~n", [SubnetError])
    end,
    io:format("~n"),

    %% 3. Describe DB parameter groups
    io:format("--- DescribeDBParameterGroups ---~n"),
    case rds_client:describe_db_parameter_groups(
        Config, #describe_db_parameter_groups_input{}) of
        {ok, #describe_db_parameter_groups_output{db_parameter_groups = ParamGroups}} ->
            io:format("SUCCESS: Found ~p DB parameter group(s)~n", [length(ParamGroups)]),
            lists:foreach(
                fun(#db_parameter_group{db_parameter_group_name = Name, db_parameter_group_family = Family}) ->
                    io:format("    ~s (Family: ~s)~n", [Name, Family])
                end,
                ParamGroups
            );
        {error, {unknown_error, 501, _}} ->
            io:format("INFO: DescribeDBParameterGroups not supported (expected in LocalStack free tier)~n");
        {error, ParamError} ->
            io:format("ERROR (expected in LocalStack free tier): ~p~n", [ParamError])
    end,
    io:format("~n"),

    %% 4. Create a new DB parameter group
    io:format("--- CreateDBParameterGroup ---~n"),
    CreateParamInput = #create_db_parameter_group_input{
        db_parameter_group_name = ?TEST_PARAM_GROUP_NAME,
        db_parameter_group_family = <<"mysql8.0">>,
        description = <<"Test parameter group created by smithy-erlang demo">>,
        tags = [
            #tag{key = <<"Environment">>, value = <<"demo">>},
            #tag{key = <<"CreatedBy">>, value = <<"smithy-erlang">>}
        ]
    },
    ParamGroupCreated = case rds_client:create_db_parameter_group(Config, CreateParamInput) of
        {ok, #create_db_parameter_group_output{
            db_parameter_group = #db_parameter_group{
                db_parameter_group_name = ?TEST_PARAM_GROUP_NAME
            }
        }} ->
            io:format("SUCCESS: group name matches '~s'~n", [?TEST_PARAM_GROUP_NAME]),
            true;
        {error, #db_parameter_group_already_exists_fault{}} ->
            io:format("SUCCESS: group '~s' already exists~n", [?TEST_PARAM_GROUP_NAME]),
            true;
        {error, {unknown_error, 501, _}} ->
            io:format("INFO: CreateDBParameterGroup not supported (expected in LocalStack free tier)~n"),
            false;
        {error, CreateParamError} ->
            io:format("ERROR (expected in LocalStack free tier): ~p~n", [CreateParamError]),
            false
    end,
    io:format("~n"),

    %% 4b. Describe DB parameter groups after create
    case ParamGroupCreated of
        true ->
            io:format("--- DescribeDBParameterGroups (after create) ---~n"),
            case rds_client:describe_db_parameter_groups(
                Config, #describe_db_parameter_groups_input{}) of
                {ok, #describe_db_parameter_groups_output{db_parameter_groups = PostGroups}} ->
                    PostGroupNames = [G#db_parameter_group.db_parameter_group_name || G <- PostGroups],
                    case lists:member(?TEST_PARAM_GROUP_NAME, PostGroupNames) of
                        true  -> io:format("SUCCESS: Created group '~s' in list~n", [?TEST_PARAM_GROUP_NAME]);
                        false -> erlang:error({assertion_failed, {created_group_not_found, ?TEST_PARAM_GROUP_NAME}})
                    end;
                {error, {unknown_error, 501, _}} ->
                    io:format("INFO: DescribeDBParameterGroups not supported~n");
                {error, PostErr} ->
                    io:format("ERROR: ~p~n", [PostErr])
            end,
            io:format("~n");
        false ->
            ok
    end,

    %% 5. Describe DB instances
    io:format("--- DescribeDBInstances ---~n"),
    case rds_client:describe_db_instances(Config, #describe_db_instances_input{}) of
        {ok, #describe_db_instances_output{db_instances = Instances}} ->
            io:format("SUCCESS: Found ~p DB instance(s)~n", [length(Instances)]),
            lists:foreach(
                fun(#db_instance{
                    db_instance_identifier = Id,
                    db_instance_class = Class,
                    engine = Engine,
                    db_instance_status = Status
                }) ->
                    io:format("    ~s (~s, ~s) - ~s~n", [Id, Engine, Class, Status])
                end,
                Instances
            );
        {error, {unknown_error, 501, _}} ->
            io:format("INFO: DescribeDBInstances not supported (expected in LocalStack free tier)~n");
        {error, InstanceError} ->
            io:format("ERROR (expected in LocalStack free tier): ~p~n", [InstanceError])
    end,
    io:format("~n"),

    %% 6. Describe DB engine versions
    io:format("--- DescribeDBEngineVersions ---~n"),
    EngineInput = #describe_db_engine_versions_input{
        engine = <<"mysql">>,
        max_records = 5
    },
    case rds_client:describe_db_engine_versions(Config, EngineInput) of
        {ok, #describe_db_engine_versions_output{db_engine_versions = Versions}} ->
            io:format("SUCCESS: Found ~p engine version(s)~n", [length(Versions)]),
            lists:foreach(
                fun(#db_engine_version{engine = Engine, engine_version = EngineVersion}) ->
                    io:format("    ~s ~s~n", [Engine, EngineVersion])
                end,
                Versions
            );
        {error, {unknown_error, 501, _}} ->
            io:format("INFO: DescribeDBEngineVersions not supported (expected in LocalStack free tier)~n");
        {error, EngineError} ->
            io:format("ERROR (expected in LocalStack free tier): ~p~n", [EngineError])
    end,
    io:format("~n"),

    %% 7. Describe DB clusters
    io:format("--- DescribeDBClusters ---~n"),
    case rds_client:describe_db_clusters(Config, #describe_db_clusters_input{}) of
        {ok, #describe_db_clusters_output{db_clusters = Clusters}} ->
            io:format("SUCCESS: Found ~p DB cluster(s)~n", [length(Clusters)]),
            lists:foreach(
                fun(#db_cluster{db_cluster_identifier = Id, engine = Engine, status = Status}) ->
                    io:format("    ~s (~s) - ~s~n", [Id, Engine, Status])
                end,
                Clusters
            );
        {error, {unknown_error, 501, _}} ->
            io:format("INFO: DescribeDBClusters not supported (expected in LocalStack free tier)~n");
        {error, ClusterError} ->
            io:format("ERROR (expected in LocalStack free tier): ~p~n", [ClusterError])
    end,
    io:format("~n"),

    %% 8. Describe reserved DB instances offerings
    io:format("--- DescribeReservedDBInstancesOfferings ---~n"),
    OfferingsInput = #describe_reserved_db_instances_offerings_input{max_records = 5},
    case rds_client:describe_reserved_db_instances_offerings(Config, OfferingsInput) of
        {ok, #describe_reserved_db_instances_offerings_output{
            reserved_db_instances_offerings = Offerings
        }} ->
            io:format("SUCCESS: Found ~p reserved instance offering(s)~n", [length(Offerings)]),
            lists:foreach(
                fun(#reserved_db_instances_offering{
                    reserved_db_instances_offering_id = Id,
                    db_instance_class = Class
                }) ->
                    io:format("    ~s (~s)~n", [Id, Class])
                end,
                lists:sublist(Offerings, 3)
            );
        {error, {unknown_error, 501, _}} ->
            io:format("INFO: DescribeReservedDBInstancesOfferings not supported (expected in LocalStack free tier)~n");
        {error, OfferingsError} ->
            io:format("ERROR (expected in LocalStack free tier): ~p~n", [OfferingsError])
    end,
    io:format("~n"),

    %% 9. Cleanup - delete parameter group if created
    case ParamGroupCreated of
        true ->
            io:format("--- DeleteDBParameterGroup ---~n"),
            DeleteParamInput = #delete_db_parameter_group_input{
                db_parameter_group_name = ?TEST_PARAM_GROUP_NAME
            },
            case rds_client:delete_db_parameter_group(Config, DeleteParamInput) of
                {ok, _} ->
                    io:format("SUCCESS: Parameter group deleted~n");
                {error, {unknown_error, 501, _}} ->
                    io:format("INFO: DeleteDBParameterGroup not supported (expected in LocalStack free tier)~n");
                {error, DeleteParamError} ->
                    erlang:error({delete_db_parameter_group_failed, DeleteParamError})
            end,
            io:format("~n"),

            io:format("--- DescribeDBParameterGroups (after delete) ---~n"),
            case rds_client:describe_db_parameter_groups(
                Config, #describe_db_parameter_groups_input{}) of
                {ok, #describe_db_parameter_groups_output{db_parameter_groups = PostDeleteGroups}} ->
                    PostDeleteNames = [G#db_parameter_group.db_parameter_group_name || G <- PostDeleteGroups],
                    case lists:member(?TEST_PARAM_GROUP_NAME, PostDeleteNames) of
                        false -> io:format("SUCCESS: Deleted group '~s' no longer in list~n", [?TEST_PARAM_GROUP_NAME]);
                        true  -> erlang:error({assertion_failed, {deleted_group_still_present, ?TEST_PARAM_GROUP_NAME}})
                    end;
                {error, {unknown_error, 501, _}} ->
                    io:format("INFO: DescribeDBParameterGroups not supported~n");
                {error, PostDeleteErr} ->
                    io:format("ERROR: ~p~n", [PostDeleteErr])
            end,
            io:format("~n");
        false ->
            ok
    end,

    io:format("=== RDS Client Application Complete ===~n"),
    ok.

client_config() ->
    #{
        region => <<"us-east-1">>,
        endpoint_prefix => <<"rds">>,
        signing_name => <<"rds">>,
        base_url => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    }.

is_not_implemented({unknown_error, 501, _}) ->
    true;
is_not_implemented(_) ->
    false.

is_parameter_group_already_exists(#db_parameter_group_already_exists_fault{}) ->
    true;
is_parameter_group_already_exists(_) ->
    false.
