-module(demo_app).
-export([run/0]).

-include("amazon_ec2_types.hrl").

run() ->
    io:format("~n=== Running EC2 Client Application ===~n~n"),

    Config = client_config(),
    io:format("EC2 client configured for ~s~n~n", [maps:get(base_url, Config)]),

    describe_vpcs(Config),
    describe_security_groups(Config),
    InstanceId = run_instance(Config),
    describe_instance(Config, InstanceId),
    terminate_instance(Config, InstanceId),

    io:format("~n=== EC2 Client Application Complete ===~n"),
    ok.

client_config() ->
    #{
        region => <<"us-east-1">>,
        endpoint_prefix => <<"ec2">>,
        signing_name => <<"ec2">>,
        base_url => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    }.

describe_vpcs(Config) ->
    io:format("--- DescribeVpcs ---~n"),
    Input = #describe_vpcs_input{},
    case amazon_ec2_client:describe_vpcs(Config, Input) of
        {ok, #describe_vpcs_output{vpcs = Vpcs}} when is_list(Vpcs), Vpcs =/= [] ->
            io:format("SUCCESS: Found ~p VPC(s)~n", [length(Vpcs)]),
            lists:foreach(
                fun(#vpc{vpc_id = VpcId, cidr_block = CidrBlock}) ->
                    io:format("    - ~s (~s)~n", [format_string(VpcId), format_string(CidrBlock)])
                end,
                Vpcs
            );
        {ok, _} ->
            erlang:error({assertion_failed, empty_vpc_list});
        {error, Reason} ->
            erlang:error({describe_vpcs_failed, Reason})
    end,
    io:format("~n").

describe_security_groups(Config) ->
    io:format("--- DescribeSecurityGroups ---~n"),
    Input = #describe_security_groups_input{},
    case amazon_ec2_client:describe_security_groups(Config, Input) of
        {ok, #describe_security_groups_output{security_groups = Sgs}}
            when is_list(Sgs), Sgs =/= [] ->
            io:format("SUCCESS: Found ~p Security Group(s)~n", [length(Sgs)]),
            lists:foreach(
                fun(#security_group{group_id = GroupId, group_name = GroupName}) ->
                    io:format("    - ~s (~s)~n", [format_string(GroupId), format_string(GroupName)])
                end,
                Sgs
            );
        {ok, _} ->
            erlang:error({assertion_failed, empty_security_group_list});
        {error, Reason} ->
            erlang:error({describe_security_groups_failed, Reason})
    end,
    io:format("~n").

run_instance(Config) ->
    io:format("--- RunInstances ---~n"),
    Input = #run_instances_input{
        image_id = <<"ami-12345678">>,
        instance_type = t2_micro,
        min_count = 1,
        max_count = 1,
        tag_specifications = [
            #tag_specification{
                resource_type = instance,
                tags = [#tag{key = <<"Name">>, value = <<"ec2-demo-instance">>}]
            }
        ]
    },
    case amazon_ec2_client:run_instances(Config, Input) of
        {ok, #run_instances_output{instances = [#instance{instance_id = InstanceId} | _]}}
            when is_binary(InstanceId) ->
            io:format("SUCCESS: InstanceId = ~s~n~n", [InstanceId]),
            InstanceId;
        {ok, _} ->
            erlang:error({assertion_failed, run_instances_returned_no_instance_id});
        {error, Reason} ->
            erlang:error({run_instances_failed, Reason})
    end.

describe_instance(Config, InstanceId) ->
    io:format("--- DescribeInstances ---~n"),
    Input = #describe_instances_input{instance_ids = [InstanceId]},
    case amazon_ec2_client:describe_instances(Config, Input) of
        {ok, #describe_instances_output{reservations = Reservations}} ->
            Instances = instances_from_reservations(Reservations),
            InstanceIds = [Id || #instance{instance_id = Id} <- Instances, is_binary(Id)],
            case lists:member(InstanceId, InstanceIds) of
                true ->
                    io:format("SUCCESS: InstanceId ~s found in DescribeInstances~n", [InstanceId]);
                false ->
                    erlang:error({assertion_failed, {instance_not_found, InstanceId}, {in, InstanceIds}})
            end,
            print_instances(Instances);
        {error, Reason} ->
            erlang:error({describe_instances_failed, Reason})
    end,
    io:format("~n").

terminate_instance(Config, InstanceId) ->
    io:format("--- TerminateInstances ---~n"),
    Input = #terminate_instances_input{instance_ids = [InstanceId]},
    case amazon_ec2_client:terminate_instances(Config, Input) of
        {ok, #terminate_instances_output{terminating_instances = Instances}} ->
            io:format("SUCCESS: TerminateInstances returned~n"),
            print_terminating_instances(Instances);
        {error, Reason} ->
            erlang:error({terminate_instances_failed, Reason})
    end,
    io:format("~n").

instances_from_reservations(undefined) ->
    [];
instances_from_reservations(Reservations) ->
    lists:flatmap(
        fun(#reservation{instances = undefined}) -> [];
           (#reservation{instances = Items}) -> Items
        end,
        Reservations
    ).

print_instances(Instances) ->
    io:format("  Found ~p Instance(s):~n", [length(Instances)]),
    lists:foreach(
        fun(#instance{
            instance_id = Id,
            instance_type = Type,
            state = State
        }) ->
            io:format(
                "    - ~s (~s, ~s)~n",
                [format_string(Id), format_instance_type(Type), format_state_name(State)]
            )
        end,
        Instances
    ).

print_terminating_instances(undefined) ->
    ok;
print_terminating_instances(Instances) ->
    io:format("  Terminating ~p instance(s):~n", [length(Instances)]),
    lists:foreach(
        fun(#instance_state_change{
            instance_id = Id,
            current_state = State
        }) ->
            io:format("    - ~s -> ~s~n", [format_string(Id), format_state_name(State)])
        end,
        Instances
    ).

format_string(undefined) ->
    <<"unknown">>;
format_string(Value) when is_binary(Value) ->
    Value;
format_string(Value) when is_list(Value) ->
    list_to_binary(Value).

format_instance_type(undefined) ->
    <<"unknown">>;
format_instance_type(Type) when is_binary(Type) ->
    Type;
format_instance_type(Type) when is_atom(Type) ->
    atom_to_binary(Type, utf8).

format_state_name(undefined) ->
    <<"unknown">>;
format_state_name(#instance_state{name = Name}) when is_atom(Name) ->
    atom_to_binary(Name, utf8);
format_state_name(#instance_state{name = Name}) when is_binary(Name) ->
    Name;
format_state_name(_) ->
    <<"unknown">>.
