-module(aws_demo_app).
-export([run/0]).

run() ->
    io:format("~n=== Running EC2 Client Application ===~n~n"),

    io:format("Creating EC2 client...~n"),
    Config = #{
        endpoint => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        region => <<"us-east-1">>,
        service => <<"ec2">>,
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    },
    {ok, Client} = aws_ec2_client:new(Config),
    io:format("Client created successfully~n~n"),

    %% 1. Describe VPCs
    io:format("--- DescribeVpcs ---~n"),
    case aws_ec2_client:describe_vpcs(Client, #{}, #{enable_retry => false}) of
        {ok, VpcsOutput} ->
            io:format("SUCCESS: DescribeVpcs returned~n"),
            Vpcs = extract_items(VpcsOutput, [<<"vpcSet">>, <<"Vpcs">>]),
            case Vpcs of
                [] -> erlang:error({assertion_failed, empty_vpc_list});
                _  -> io:format("SUCCESS: Found ~p VPC(s)~n", [length(Vpcs)])
            end,
            lists:foreach(
                fun(Vpc) ->
                    VpcId = maps:get(<<"vpcId">>, Vpc, maps:get(<<"VpcId">>, Vpc, <<"unknown">>)),
                    CidrBlock = maps:get(<<"cidrBlock">>, Vpc, maps:get(<<"CidrBlock">>, Vpc, <<"unknown">>)),
                    io:format("    - ~s (~s)~n", [VpcId, CidrBlock])
                end,
                Vpcs
            );
        {error, VpcsError} ->
            erlang:error({describe_vpcs_failed, VpcsError})
    end,
    io:format("~n"),

    %% 2. Describe Security Groups
    io:format("--- DescribeSecurityGroups ---~n"),
    case aws_ec2_client:describe_security_groups(Client, #{}, #{enable_retry => false}) of
        {ok, SgsOutput} ->
            io:format("SUCCESS: DescribeSecurityGroups returned~n"),
            Sgs = extract_items(SgsOutput, [<<"securityGroupInfo">>, <<"SecurityGroups">>]),
            case Sgs of
                [] -> erlang:error({assertion_failed, empty_security_group_list});
                _  -> io:format("SUCCESS: Found ~p Security Group(s)~n", [length(Sgs)])
            end,
            lists:foreach(
                fun(Sg) ->
                    GroupId = maps:get(<<"groupId">>, Sg, maps:get(<<"GroupId">>, Sg, <<"unknown">>)),
                    GroupName = maps:get(<<"groupName">>, Sg, maps:get(<<"GroupName">>, Sg, <<"unknown">>)),
                    io:format("    - ~s (~s)~n", [GroupId, GroupName])
                end,
                Sgs
            );
        {error, SgsError} ->
            erlang:error({describe_security_groups_failed, SgsError})
    end,
    io:format("~n"),

    %% 3. Run an EC2 instance
    io:format("--- RunInstances ---~n"),
    RunInput = #{
        <<"ImageId">> => <<"ami-12345678">>,
        <<"InstanceType">> => <<"t2.micro">>,
        <<"MinCount">> => 1,
        <<"MaxCount">> => 1,
        <<"TagSpecifications">> => [
            #{
                <<"ResourceType">> => <<"instance">>,
                <<"Tags">> => [
                    #{<<"Key">> => <<"Name">>, <<"Value">> => <<"ec2-demo-instance">>}
                ]
            }
        ]
    },
    InstanceId = case aws_ec2_client:run_instances(Client, RunInput, #{enable_retry => false}) of
        {ok, RunOutput} ->
            io:format("SUCCESS: RunInstances returned~n"),
            Instances = get_instances_from_response(RunOutput),
            case Instances of
                [Instance | _] ->
                    Id = maps:get(<<"instanceId">>, Instance, maps:get(<<"InstanceId">>, Instance, undefined)),
                    case Id of
                        undefined ->
                            erlang:error({assertion_failed, run_instances_returned_no_instance_id});
                        _ when is_binary(Id) ->
                            io:format("SUCCESS: InstanceId = ~s~n", [Id]),
                            Id
                    end;
                _ ->
                    erlang:error({assertion_failed, run_instances_returned_no_instance_id})
            end;
        {error, RunError} ->
            erlang:error({run_instances_failed, RunError})
    end,
    io:format("~n"),

    %% 4. Describe Instances to see the running instance
    io:format("--- DescribeInstances ---~n"),
    DescribeInput = #{<<"InstanceIds">> => [InstanceId]},
    case aws_ec2_client:describe_instances(Client, DescribeInput, #{enable_retry => false}) of
        {ok, DescribeOutput} ->
            io:format("SUCCESS: DescribeInstances returned~n"),
            Reservations = extract_items(DescribeOutput, [<<"reservationSet">>, <<"Reservations">>]),
            AllInstances = lists:flatmap(
                fun(Res) -> extract_items(Res, [<<"instancesSet">>, <<"Instances">>]) end,
                Reservations
            ),
            DescribedIds = [maps:get(<<"instanceId">>, I, maps:get(<<"InstanceId">>, I, <<>>)) || I <- AllInstances],
            case lists:member(InstanceId, DescribedIds) of
                true  -> io:format("SUCCESS: InstanceId ~s found in DescribeInstances~n", [InstanceId]);
                false -> erlang:error({assertion_failed, {instance_not_found, InstanceId}, {in, DescribedIds}})
            end,
            print_instances(DescribeOutput);
        {error, DescribeError} ->
            erlang:error({describe_instances_failed, DescribeError})
    end,
    io:format("~n"),

    %% 5. Terminate the instance (cleanup)
    io:format("--- TerminateInstances ---~n"),
    TerminateInput = #{<<"InstanceIds">> => [InstanceId]},
    case aws_ec2_client:terminate_instances(Client, TerminateInput, #{enable_retry => false}) of
        {ok, TerminateOutput} ->
            io:format("SUCCESS: TerminateInstances returned~n"),
            print_terminating_instances(TerminateOutput);
        {error, TerminateError} ->
            erlang:error({terminate_instances_failed, TerminateError})
    end,
    io:format("~n"),

    io:format("=== EC2 Client Application Complete ===~n"),
    ok.

%% Helper to extract instances from RunInstances response
get_instances_from_response(Response) ->
    case maps:get(<<"Instances">>, Response, undefined) of
        undefined ->
            case maps:get(<<"instancesSet">>, Response, undefined) of
                undefined ->
                    case maps:get(<<"RunInstancesResponse">>, Response, undefined) of
                        undefined -> [];
                        RIR ->
                            case maps:get(<<"instancesSet">>, RIR, undefined) of
                                undefined -> [];
                                #{<<"item">> := Items} when is_list(Items) -> Items;
                                #{<<"item">> := Item} when is_map(Item) -> [Item];
                                _ -> []
                            end
                    end;
                #{<<"item">> := Items} when is_list(Items) -> Items;
                #{<<"item">> := Item} when is_map(Item) -> [Item];
                Items when is_list(Items) -> Items;
                _ -> []
            end;
        Instances when is_list(Instances) -> Instances;
        _ -> []
    end.

%% Print Instances from DescribeInstances response
print_instances(Response) ->
    Reservations = extract_items(Response, [<<"reservationSet">>, <<"Reservations">>]),
    AllInstances = lists:flatmap(
        fun(Res) ->
            extract_items(Res, [<<"instancesSet">>, <<"Instances">>])
        end,
        Reservations
    ),
    io:format("  Found ~p Instance(s):~n", [length(AllInstances)]),
    lists:foreach(
        fun(Instance) ->
            InstanceId = maps:get(<<"instanceId">>, Instance, maps:get(<<"InstanceId">>, Instance, <<"unknown">>)),
            State = maps:get(<<"instanceState">>, Instance, maps:get(<<"State">>, Instance, #{})),
            StateName = maps:get(<<"name">>, State, maps:get(<<"Name">>, State, <<"unknown">>)),
            InstanceType = maps:get(<<"instanceType">>, Instance, maps:get(<<"InstanceType">>, Instance, <<"unknown">>)),
            io:format("    - ~s (~s, ~s)~n", [InstanceId, InstanceType, StateName])
        end,
        AllInstances
    ).

%% Print terminating instances
print_terminating_instances(Response) ->
    Instances = extract_items(Response, [<<"instancesSet">>, <<"TerminatingInstances">>]),
    io:format("  Terminating ~p instance(s):~n", [length(Instances)]),
    lists:foreach(
        fun(Instance) ->
            InstanceId = maps:get(<<"instanceId">>, Instance, maps:get(<<"InstanceId">>, Instance, <<"unknown">>)),
            CurrentState = maps:get(<<"currentState">>, Instance, maps:get(<<"CurrentState">>, Instance, #{})),
            StateName = maps:get(<<"name">>, CurrentState, maps:get(<<"Name">>, CurrentState, <<"unknown">>)),
            io:format("    - ~s -> ~s~n", [InstanceId, StateName])
        end,
        Instances
    ).

%% Extract items from response, trying multiple possible keys
extract_items(Response, Keys) ->
    extract_items_loop(Response, Keys).

extract_items_loop(_Response, []) ->
    [];
extract_items_loop(Response, [Key | Rest]) ->
    case maps:get(Key, Response, undefined) of
        undefined ->
            extract_items_loop(Response, Rest);
        #{<<"item">> := Items} when is_list(Items) ->
            Items;
        #{<<"item">> := Item} when is_map(Item) ->
            [Item];
        Items when is_list(Items) ->
            Items;
        _ ->
            extract_items_loop(Response, Rest)
    end.
