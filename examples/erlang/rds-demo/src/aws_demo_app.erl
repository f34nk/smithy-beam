-module(aws_demo_app).
-export([run/0]).

-define(TEST_PARAM_GROUP_NAME, <<"rds-demo-test-param-group">>).

run() ->
    io:format("~n=== Running RDS Client Application ===~n~n"),
    io:format("Note: LocalStack free tier has limited RDS support.~n"),
    io:format("Some operations may return 501 errors, but client API calls work correctly.~n~n"),

    %% Create RDS client instance with AWS credentials
    io:format("Creating RDS client...~n"),
    Config = #{
        endpoint => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        region => <<"us-east-1">>,
        service => <<"rds">>,  %% Required for SigV4 signing with custom endpoints
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    },
    {ok, Client} = aws_rds_client:new(Config),
    io:format("Client created successfully~n~n"),

    %% 1. Describe account attributes
    io:format("--- DescribeAccountAttributes ---~n"),
    case aws_rds_client:describe_account_attributes(Client, #{}, #{enable_retry => false}) of
        {ok, AcctOutput} ->
            Quotas = get_quotas_from_response(AcctOutput),
            io:format("SUCCESS: Found ~p account quota(s)~n", [length(Quotas)]),
            lists:foreach(
                fun(Quota) ->
                    Name = maps:get(<<"AccountQuotaName">>, Quota, <<"unknown">>),
                    Used = maps:get(<<"Used">>, Quota, 0),
                    Max = maps:get(<<"Max">>, Quota, 0),
                    io:format("    ~s: ~p / ~p~n", [Name, Used, Max])
                end,
                lists:sublist(Quotas, 5)
            ),
            case length(Quotas) > 5 of
                true -> io:format("    ... and ~p more~n", [length(Quotas) - 5]);
                false -> ok
            end;
        {error, AcctError} ->
            io:format("ERROR (expected in LocalStack free tier): ~p~n", [AcctError])
    end,
    io:format("~n"),

    %% 2. Describe DB subnet groups
    io:format("--- DescribeDBSubnetGroups ---~n"),
    case aws_rds_client:describe_db_subnet_groups(Client, #{}, #{enable_retry => false}) of
        {ok, SubnetOutput} ->
            SubnetGroups = get_subnet_groups_from_response(SubnetOutput),
            io:format("SUCCESS: Found ~p DB subnet group(s)~n", [length(SubnetGroups)]),
            lists:foreach(
                fun(Group) ->
                    Name = maps:get(<<"DBSubnetGroupName">>, Group, <<"unknown">>),
                    Status = maps:get(<<"SubnetGroupStatus">>, Group, <<"unknown">>),
                    io:format("    ~s (Status: ~s)~n", [Name, Status])
                end,
                SubnetGroups
            );
        {error, SubnetError} ->
            io:format("ERROR (expected in LocalStack free tier): ~p~n", [SubnetError])
    end,
    io:format("~n"),

    %% 3. Describe DB parameter groups
    io:format("--- DescribeDBParameterGroups ---~n"),
    case aws_rds_client:describe_db_parameter_groups(Client, #{}, #{enable_retry => false}) of
        {ok, ParamOutput} ->
            ParamGroups = get_param_groups_from_response(ParamOutput),
            io:format("SUCCESS: Found ~p DB parameter group(s)~n", [length(ParamGroups)]),
            lists:foreach(
                fun(Group) ->
                    Name = maps:get(<<"DBParameterGroupName">>, Group, <<"unknown">>),
                    Family = maps:get(<<"DBParameterGroupFamily">>, Group, <<"unknown">>),
                    io:format("    ~s (Family: ~s)~n", [Name, Family])
                end,
                ParamGroups
            );
        {error, ParamError} ->
            io:format("ERROR (expected in LocalStack free tier): ~p~n", [ParamError])
    end,
    io:format("~n"),

    %% 4. Create a new DB parameter group
    io:format("--- CreateDBParameterGroup ---~n"),
    CreateParamInput = #{
        <<"DBParameterGroupName">> => ?TEST_PARAM_GROUP_NAME,
        <<"DBParameterGroupFamily">> => <<"mysql8.0">>,
        <<"Description">> => <<"Test parameter group created by smithy-erlang demo">>,
        <<"Tags">> => [
            #{<<"Key">> => <<"Environment">>, <<"Value">> => <<"demo">>},
            #{<<"Key">> => <<"CreatedBy">>, <<"Value">> => <<"smithy-erlang">>}
        ]
    },
    ParamGroupCreated = case aws_rds_client:create_db_parameter_group(Client, CreateParamInput, #{enable_retry => false}) of
        {ok, CreateParamOutput} ->
            CreatedGroup = maps:get(<<"DBParameterGroup">>, CreateParamOutput, #{}),
            CreatedName = maps:get(<<"DBParameterGroupName">>, CreatedGroup, <<"unknown">>),
            io:format("SUCCESS: Created parameter group: ~s~n", [CreatedName]),
            true;
        {error, CreateParamError} ->
            io:format("ERROR (expected in LocalStack free tier): ~p~n", [CreateParamError]),
            false
    end,
    io:format("~n"),

    %% 5. Describe DB instances
    io:format("--- DescribeDBInstances ---~n"),
    case aws_rds_client:describe_db_instances(Client, #{}, #{enable_retry => false}) of
        {ok, InstanceOutput} ->
            Instances = get_instances_from_response(InstanceOutput),
            io:format("SUCCESS: Found ~p DB instance(s)~n", [length(Instances)]),
            lists:foreach(
                fun(Instance) ->
                    Id = maps:get(<<"DBInstanceIdentifier">>, Instance, <<"unknown">>),
                    Class = maps:get(<<"DBInstanceClass">>, Instance, <<"unknown">>),
                    Engine = maps:get(<<"Engine">>, Instance, <<"unknown">>),
                    Status = maps:get(<<"DBInstanceStatus">>, Instance, <<"unknown">>),
                    io:format("    ~s (~s, ~s) - ~s~n", [Id, Engine, Class, Status])
                end,
                Instances
            );
        {error, InstanceError} ->
            io:format("ERROR (expected in LocalStack free tier): ~p~n", [InstanceError])
    end,
    io:format("~n"),

    %% 6. Describe DB engine versions
    io:format("--- DescribeDBEngineVersions ---~n"),
    EngineInput = #{
        <<"Engine">> => <<"mysql">>,
        <<"MaxRecords">> => 5
    },
    case aws_rds_client:describe_db_engine_versions(Client, EngineInput, #{enable_retry => false}) of
        {ok, EngineOutput} ->
            Versions = get_engine_versions_from_response(EngineOutput),
            io:format("SUCCESS: Found ~p engine version(s)~n", [length(Versions)]),
            lists:foreach(
                fun(Version) ->
                    Engine = maps:get(<<"Engine">>, Version, <<"unknown">>),
                    EngineVersion = maps:get(<<"EngineVersion">>, Version, <<"unknown">>),
                    io:format("    ~s ~s~n", [Engine, EngineVersion])
                end,
                Versions
            );
        {error, EngineError} ->
            io:format("ERROR (expected in LocalStack free tier): ~p~n", [EngineError])
    end,
    io:format("~n"),

    %% 7. Describe DB clusters
    io:format("--- DescribeDBClusters ---~n"),
    case aws_rds_client:describe_db_clusters(Client, #{}, #{enable_retry => false}) of
        {ok, ClusterOutput} ->
            Clusters = get_clusters_from_response(ClusterOutput),
            io:format("SUCCESS: Found ~p DB cluster(s)~n", [length(Clusters)]),
            lists:foreach(
                fun(Cluster) ->
                    Id = maps:get(<<"DBClusterIdentifier">>, Cluster, <<"unknown">>),
                    Engine = maps:get(<<"Engine">>, Cluster, <<"unknown">>),
                    Status = maps:get(<<"Status">>, Cluster, <<"unknown">>),
                    io:format("    ~s (~s) - ~s~n", [Id, Engine, Status])
                end,
                Clusters
            );
        {error, ClusterError} ->
            io:format("ERROR (expected in LocalStack free tier): ~p~n", [ClusterError])
    end,
    io:format("~n"),

    %% 8. Describe reserved DB instances offerings (pricing info)
    io:format("--- DescribeReservedDBInstancesOfferings ---~n"),
    OfferingsInput = #{<<"MaxRecords">> => 5},
    case aws_rds_client:describe_reserved_db_instances_offerings(Client, OfferingsInput, #{enable_retry => false}) of
        {ok, OfferingsOutput} ->
            Offerings = get_offerings_from_response(OfferingsOutput),
            io:format("SUCCESS: Found ~p reserved instance offering(s)~n", [length(Offerings)]),
            lists:foreach(
                fun(Offering) ->
                    Id = maps:get(<<"ReservedDBInstancesOfferingId">>, Offering, <<"unknown">>),
                    Class = maps:get(<<"DBInstanceClass">>, Offering, <<"unknown">>),
                    io:format("    ~s (~s)~n", [Id, Class])
                end,
                lists:sublist(Offerings, 3)
            );
        {error, OfferingsError} ->
            io:format("ERROR (expected in LocalStack free tier): ~p~n", [OfferingsError])
    end,
    io:format("~n"),

    %% 9. Cleanup - delete parameter group if created
    case ParamGroupCreated of
        true ->
            io:format("--- DeleteDBParameterGroup ---~n"),
            DeleteParamInput = #{<<"DBParameterGroupName">> => ?TEST_PARAM_GROUP_NAME},
            case aws_rds_client:delete_db_parameter_group(Client, DeleteParamInput, #{enable_retry => false}) of
                {ok, _} ->
                    io:format("SUCCESS: Parameter group deleted~n");
                {error, DeleteParamError} ->
                    io:format("ERROR: ~p~n", [DeleteParamError])
            end,
            io:format("~n");
        false ->
            ok
    end,

    io:format("=== RDS Client Application Complete ===~n"),
    ok.

%% Helper to extract account quotas from response
get_quotas_from_response(Response) ->
    case maps:get(<<"AccountQuotas">>, Response, undefined) of
        undefined -> [];
        Wrapper when is_map(Wrapper) ->
            case maps:get(<<"AccountQuota">>, Wrapper, undefined) of
                undefined -> [];
                Member when is_list(Member) -> Member;
                Member when is_map(Member) -> [Member]
            end;
        List when is_list(List) -> List
    end.

%% Helper to extract subnet groups from response
get_subnet_groups_from_response(Response) ->
    case maps:get(<<"DBSubnetGroups">>, Response, undefined) of
        undefined -> [];
        Wrapper when is_map(Wrapper) ->
            case maps:get(<<"DBSubnetGroup">>, Wrapper, undefined) of
                undefined -> [];
                Member when is_list(Member) -> Member;
                Member when is_map(Member) -> [Member]
            end;
        List when is_list(List) -> List
    end.

%% Helper to extract parameter groups from response
get_param_groups_from_response(Response) ->
    case maps:get(<<"DBParameterGroups">>, Response, undefined) of
        undefined -> [];
        Wrapper when is_map(Wrapper) ->
            case maps:get(<<"DBParameterGroup">>, Wrapper, undefined) of
                undefined -> [];
                Member when is_list(Member) -> Member;
                Member when is_map(Member) -> [Member]
            end;
        List when is_list(List) -> List
    end.

%% Helper to extract instances from response
get_instances_from_response(Response) ->
    case maps:get(<<"DBInstances">>, Response, undefined) of
        undefined -> [];
        Wrapper when is_map(Wrapper) ->
            case maps:get(<<"DBInstance">>, Wrapper, undefined) of
                undefined -> [];
                Member when is_list(Member) -> Member;
                Member when is_map(Member) -> [Member]
            end;
        List when is_list(List) -> List
    end.

%% Helper to extract engine versions from response
get_engine_versions_from_response(Response) ->
    case maps:get(<<"DBEngineVersions">>, Response, undefined) of
        undefined -> [];
        Wrapper when is_map(Wrapper) ->
            case maps:get(<<"DBEngineVersion">>, Wrapper, undefined) of
                undefined -> [];
                Member when is_list(Member) -> Member;
                Member when is_map(Member) -> [Member]
            end;
        List when is_list(List) -> List
    end.

%% Helper to extract clusters from response
get_clusters_from_response(Response) ->
    case maps:get(<<"DBClusters">>, Response, undefined) of
        undefined -> [];
        Wrapper when is_map(Wrapper) ->
            case maps:get(<<"DBCluster">>, Wrapper, undefined) of
                undefined -> [];
                Member when is_list(Member) -> Member;
                Member when is_map(Member) -> [Member]
            end;
        List when is_list(List) -> List
    end.

%% Helper to extract offerings from response
get_offerings_from_response(Response) ->
    case maps:get(<<"ReservedDBInstancesOfferings">>, Response, undefined) of
        undefined -> [];
        Wrapper when is_map(Wrapper) ->
            case maps:get(<<"ReservedDBInstancesOffering">>, Wrapper, undefined) of
                undefined -> [];
                Member when is_list(Member) -> Member;
                Member when is_map(Member) -> [Member]
            end;
        List when is_list(List) -> List
    end.
