-module(demo_app).
-export([run/0]).

-include("ec2_types.hrl").

-define(DEMO_VPC_CIDR, <<"10.0.0.0/16">>).
-define(DEMO_VPC_NAME, <<"ec2-demo-erlang-vpc">>).
-define(DEMO_SUBNET_CIDR, <<"10.0.1.0/24">>).
-define(DEMO_SUBNET_NAME, <<"ec2-demo-erlang-subnet">>).
-define(DEMO_SG_NAME, <<"ec2-demo-erlang-sg">>).
-define(DEMO_SG_DESCRIPTION, <<"Security group for EC2 demo">>).

run() ->
    {ok, _} = application:ensure_all_started(aws_credentials),
    io:format("~n=== Running EC2 Client Application ===~n~n"),

    Config = client_config(),
    io:format("EC2 client configured for ~s~n~n", [maps:get(base_url, Config)]),

    {VpcId, SubnetId, SgId} = setup_infrastructure(Config),

    describe_vpcs(Config, VpcId),
    describe_security_groups(Config, SgId),
    InstanceId = run_instance(Config, SubnetId, SgId),
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

create_demo_vpc(Config) ->
    io:format("--- CreateVpc ---~n"),
    Input = #create_vpc_input{
        cidr_block = ?DEMO_VPC_CIDR,
        tag_specifications = [
            #tag_specification{
                resource_type = vpc,
                tags = [#tag{key = <<"Name">>, value = ?DEMO_VPC_NAME}]
            }
        ]
    },
    case ec2_client:create_vpc(Config, Input) of
        {ok, #create_vpc_output{vpc = #vpc{vpc_id = VpcId}}} when is_binary(VpcId) ->
            io:format("SUCCESS: VpcId = ~s~n", [VpcId]),
            io:format("--- ModifyVpcAttribute (EnableDnsSupport) ---~n"),
            case ec2_client:modify_vpc_attribute(Config, #modify_vpc_attribute_input{
                vpc_id = VpcId,
                enable_dns_support = #attribute_boolean_value{value = true}
            }) of
                {ok, _} ->
                    io:format("SUCCESS: EnableDnsSupport~n");
                {error, DnsSupportReason} ->
                    erlang:error({modify_vpc_attribute_dns_support_failed, DnsSupportReason})
            end,
            io:format("--- ModifyVpcAttribute (EnableDnsHostnames) ---~n"),
            case ec2_client:modify_vpc_attribute(Config, #modify_vpc_attribute_input{
                vpc_id = VpcId,
                enable_dns_hostnames = #attribute_boolean_value{value = true}
            }) of
                {ok, _} ->
                    io:format("SUCCESS: EnableDnsHostnames~n~n");
                {error, DnsHostnamesReason} ->
                    erlang:error({modify_vpc_attribute_dns_hostnames_failed, DnsHostnamesReason})
            end,
            VpcId;
        {ok, _} ->
            erlang:error({assertion_failed, create_vpc_returned_no_vpc_id});
        {error, CreateVpcReason} ->
            erlang:error({create_vpc_failed, CreateVpcReason})
    end.

create_demo_subnet(Config, VpcId) ->
    io:format("--- CreateSubnet ---~n"),
    Input = #create_subnet_input{
        vpc_id = VpcId,
        cidr_block = ?DEMO_SUBNET_CIDR,
        tag_specifications = [
            #tag_specification{
                resource_type = subnet,
                tags = [#tag{key = <<"Name">>, value = ?DEMO_SUBNET_NAME}]
            }
        ]
    },
    case ec2_client:create_subnet(Config, Input) of
        {ok, #create_subnet_output{subnet = #subnet{subnet_id = SubnetId}}}
            when is_binary(SubnetId) ->
            io:format("SUCCESS: SubnetId = ~s~n", [SubnetId]),
            io:format("--- ModifySubnetAttribute (MapPublicIpOnLaunch) ---~n"),
            case ec2_client:modify_subnet_attribute(Config, #modify_subnet_attribute_input{
                subnet_id = SubnetId,
                map_public_ip_on_launch = #attribute_boolean_value{value = true}
            }) of
                {ok, _} ->
                    io:format("SUCCESS: MapPublicIpOnLaunch~n~n");
                {error, ModifySubnetReason} ->
                    erlang:error({modify_subnet_attribute_failed, ModifySubnetReason})
            end,
            SubnetId;
        {ok, _} ->
            erlang:error({assertion_failed, create_subnet_returned_no_subnet_id});
        {error, CreateSubnetReason} ->
            erlang:error({create_subnet_failed, CreateSubnetReason})
    end.

create_demo_security_group(Config, VpcId) ->
    io:format("--- CreateSecurityGroup ---~n"),
    Input = #create_security_group_input{
        group_name = ?DEMO_SG_NAME,
        description = ?DEMO_SG_DESCRIPTION,
        vpc_id = VpcId,
        tag_specifications = [
            #tag_specification{
                resource_type = security_group,
                tags = [#tag{key = <<"Name">>, value = ?DEMO_SG_NAME}]
            }
        ]
    },
    case ec2_client:create_security_group(Config, Input) of
        {ok, #create_security_group_output{group_id = SgId}} when is_binary(SgId) ->
            io:format("SUCCESS: SecurityGroupId = ~s~n", [SgId]),
            io:format("--- AuthorizeSecurityGroupIngress ---~n"),
            IngressInput = #authorize_security_group_ingress_input{
                group_id = SgId,
                ip_permissions = [
                    #ip_permission{
                        ip_protocol = <<"tcp">>,
                        from_port = 22,
                        to_port = 22,
                        ip_ranges = [#ip_range{cidr_ip = <<"0.0.0.0/0">>}]
                    }
                ]
            },
            case ec2_client:authorize_security_group_ingress(Config, IngressInput) of
                {ok, _} ->
                    io:format("SUCCESS: SSH ingress rule~n");
                {error, IngressReason} ->
                    erlang:error({authorize_security_group_ingress_failed, IngressReason})
            end,
            SgId;
        {ok, _} ->
            erlang:error({assertion_failed, create_security_group_returned_no_group_id});
        {error, CreateSgReason} ->
            erlang:error({create_security_group_failed, CreateSgReason})
    end.

setup_infrastructure(Config) ->
    io:format("--- Setup infrastructure ---~n"),
    VpcId = create_demo_vpc(Config),
    SubnetId = create_demo_subnet(Config, VpcId),
    SgId = create_demo_security_group(Config, VpcId),
    io:format("Infrastructure ready: VpcId=~s SubnetId=~s SecurityGroupId=~s~n~n",
        [VpcId, SubnetId, SgId]),
    {VpcId, SubnetId, SgId}.

describe_vpcs(Config, ExpectedVpcId) ->
    io:format("--- DescribeVpcs ---~n"),
    Input = #describe_vpcs_input{},
    case ec2_client:describe_vpcs(Config, Input) of
        {ok, Vpcs} when is_list(Vpcs) ->
            case lists:keyfind(ExpectedVpcId, #vpc.vpc_id, Vpcs) of
                #vpc{vpc_id = VpcId, cidr_block = CidrBlock} ->
                    io:format("SUCCESS: Found expected VPC ~s (~s)~n",
                        [format_string(VpcId), format_string(CidrBlock)]);
                false ->
                    erlang:error({assertion_failed, {vpc_not_found, ExpectedVpcId}})
            end,
            io:format("  Total VPC count: ~p~n", [length(Vpcs)]);
        {ok, _} ->
            erlang:error({assertion_failed, empty_vpc_list});
        {error, Reason} ->
            erlang:error({describe_vpcs_failed, Reason})
    end,
    io:format("~n").

describe_security_groups(Config, ExpectedSgId) ->
    io:format("--- DescribeSecurityGroups ---~n"),
    Input = #describe_security_groups_input{},
    case ec2_client:describe_security_groups(Config, Input) of
        {ok, Sgs} when is_list(Sgs) ->
            case lists:keyfind(ExpectedSgId, #security_group.group_id, Sgs) of
                #security_group{group_id = GroupId, group_name = GroupName} ->
                    io:format("SUCCESS: Found expected security group ~s (~s)~n",
                        [format_string(GroupId), format_string(GroupName)]);
                false ->
                    erlang:error({assertion_failed, {security_group_not_found, ExpectedSgId}})
            end,
            io:format("  Total security group count: ~p~n", [length(Sgs)]);
        {ok, _} ->
            erlang:error({assertion_failed, empty_security_group_list});
        {error, Reason} ->
            erlang:error({describe_security_groups_failed, Reason})
    end,
    io:format("~n").

run_instance(Config, SubnetId, SgId) ->
    io:format("--- RunInstances ---~n"),
    Input = #run_instances_input{
        image_id = <<"ami-12345678">>,
        instance_type = t2_micro,
        min_count = 1,
        max_count = 1,
        subnet_id = SubnetId,
        security_group_ids = [SgId],
        tag_specifications = [
            #tag_specification{
                resource_type = instance,
                tags = [#tag{key = <<"Name">>, value = <<"ec2-demo-erlang-instance">>}]
            }
        ]
    },
    case ec2_client:run_instances(Config, Input) of
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
    case ec2_client:describe_instances(Config, Input) of
        {ok, Reservations} when is_list(Reservations) ->
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
    case ec2_client:terminate_instances(Config, Input) of
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
