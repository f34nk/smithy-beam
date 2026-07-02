defmodule Demo do
  @moduledoc """
  EC2 demo covering CreateVpc, CreateSubnet, CreateSecurityGroup,
  DescribeVpcs, DescribeSecurityGroups, RunInstances, DescribeInstances,
  and TerminateInstances via the generated Ec2Client.

  Uses the aws.protocols#ec2Query protocol (XML over HTTPS).
  Run against LocalStack: make demo
  """

  alias Ec2Types.{
    AttributeBooleanValue,
    AuthorizeSecurityGroupIngressInput,
    CreateSecurityGroupInput,
    CreateSecurityGroupOutput,
    CreateSubnetInput,
    CreateSubnetOutput,
    CreateVpcInput,
    CreateVpcOutput,
    DescribeInstancesInput,
    DescribeSecurityGroupsInput,
    DescribeVpcsInput,
    Instance,
    IpPermission,
    IpRange,
    Reservation,
    RunInstancesInput,
    SecurityGroup,
    Tag,
    TagSpecification,
    TerminateInstancesInput,
    Vpc
  }

  @demo_vpc_cidr "10.0.0.0/16"
  @demo_vpc_name "ec2-demo-elixir-vpc"
  @demo_subnet_cidr "10.0.1.0/24"
  @demo_subnet_name "ec2-demo-elixir-subnet"
  @demo_sg_name "ec2-demo-elixir-sg"
  @demo_sg_description "Security group for EC2 demo"

  def run do
    IO.puts("\n=== Running EC2 Client Application ===\n")

    config = client_config()
    IO.puts("EC2 client configured for #{Map.fetch!(config, :base_url)}\n")

    {vpc_id, subnet_id, sg_id} = setup_infrastructure(config)

    describe_vpcs(config, vpc_id)
    describe_security_groups(config, sg_id)
    instance_id = run_instance(config, subnet_id, sg_id)
    describe_instance(config, instance_id)
    terminate_instance(config, instance_id)

    IO.puts("\n=== EC2 Client Application Complete ===")
    :ok
  end

  defp client_config do
    %{
      region: "us-east-1",
      endpoint_prefix: "ec2",
      signing_name: "ec2",
      base_url: System.get_env("AWS_ENDPOINT"),
      credentials: %{
        access_key_id: "dummy",
        secret_access_key: "dummy"
      }
    }
  end

  defp setup_infrastructure(config) do
    IO.puts("--- Setup infrastructure ---")

    vpc_id = create_demo_vpc(config)
    subnet_id = create_demo_subnet(config, vpc_id)
    sg_id = create_demo_security_group(config, vpc_id)

    IO.puts(
      "Infrastructure ready: VpcId=#{vpc_id} SubnetId=#{subnet_id} SecurityGroupId=#{sg_id}\n"
    )

    {vpc_id, subnet_id, sg_id}
  end

  defp create_demo_vpc(config) do
    IO.puts("--- CreateVpc ---")

    input = %CreateVpcInput{
      cidr_block: @demo_vpc_cidr,
      tag_specifications: [
        %TagSpecification{
          resource_type: :vpc,
          tags: [%Tag{key: "Name", value: @demo_vpc_name}]
        }
      ]
    }

    case Ec2Client.create_vpc(config, input) do
      {:ok, %CreateVpcOutput{vpc: %Vpc{vpc_id: vpc_id}}} when is_binary(vpc_id) ->
        IO.puts("SUCCESS: VpcId = #{vpc_id}")
        modify_vpc_dns_support(config, vpc_id)
        modify_vpc_dns_hostnames(config, vpc_id)
        vpc_id

      {:ok, _} ->
        raise("assertion failed: create_vpc returned no VpcId")

      {:error, reason} ->
        raise("create_vpc_failed: #{inspect(reason)}")
    end
  end

  defp modify_vpc_dns_support(config, vpc_id) do
    IO.puts("--- ModifyVpcAttribute (EnableDnsSupport) ---")

    input = %Ec2Types.ModifyVpcAttributeInput{
      vpc_id: vpc_id,
      enable_dns_support: %AttributeBooleanValue{value: true}
    }

    case Ec2Client.modify_vpc_attribute(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: EnableDnsSupport")

      {:error, reason} ->
        raise("modify_vpc_attribute_dns_support_failed: #{inspect(reason)}")
    end
  end

  defp modify_vpc_dns_hostnames(config, vpc_id) do
    IO.puts("--- ModifyVpcAttribute (EnableDnsHostnames) ---")

    input = %Ec2Types.ModifyVpcAttributeInput{
      vpc_id: vpc_id,
      enable_dns_hostnames: %AttributeBooleanValue{value: true}
    }

    case Ec2Client.modify_vpc_attribute(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: EnableDnsHostnames\n")

      {:error, reason} ->
        raise("modify_vpc_attribute_dns_hostnames_failed: #{inspect(reason)}")
    end
  end

  defp create_demo_subnet(config, vpc_id) do
    IO.puts("--- CreateSubnet ---")

    input = %CreateSubnetInput{
      vpc_id: vpc_id,
      cidr_block: @demo_subnet_cidr,
      tag_specifications: [
        %TagSpecification{
          resource_type: :subnet,
          tags: [%Tag{key: "Name", value: @demo_subnet_name}]
        }
      ]
    }

    case Ec2Client.create_subnet(config, input) do
      {:ok, %CreateSubnetOutput{subnet: %{subnet_id: subnet_id}}} when is_binary(subnet_id) ->
        IO.puts("SUCCESS: SubnetId = #{subnet_id}")
        modify_subnet_map_public_ip(config, subnet_id)
        subnet_id

      {:ok, _} ->
        raise("assertion failed: create_subnet returned no SubnetId")

      {:error, reason} ->
        raise("create_subnet_failed: #{inspect(reason)}")
    end
  end

  defp modify_subnet_map_public_ip(config, subnet_id) do
    IO.puts("--- ModifySubnetAttribute (MapPublicIpOnLaunch) ---")

    input = %Ec2Types.ModifySubnetAttributeInput{
      subnet_id: subnet_id,
      map_public_ip_on_launch: %AttributeBooleanValue{value: true}
    }

    case Ec2Client.modify_subnet_attribute(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: MapPublicIpOnLaunch\n")

      {:error, reason} ->
        raise("modify_subnet_attribute_failed: #{inspect(reason)}")
    end
  end

  defp create_demo_security_group(config, vpc_id) do
    IO.puts("--- CreateSecurityGroup ---")

    input = %CreateSecurityGroupInput{
      group_name: @demo_sg_name,
      description: @demo_sg_description,
      vpc_id: vpc_id,
      tag_specifications: [
        %TagSpecification{
          resource_type: :security_group,
          tags: [%Tag{key: "Name", value: @demo_sg_name}]
        }
      ]
    }

    case Ec2Client.create_security_group(config, input) do
      {:ok, %CreateSecurityGroupOutput{group_id: sg_id}} when is_binary(sg_id) ->
        IO.puts("SUCCESS: SecurityGroupId = #{sg_id}")
        authorize_ssh_ingress(config, sg_id)
        sg_id

      {:ok, _} ->
        raise("assertion failed: create_security_group returned no GroupId")

      {:error, reason} ->
        raise("create_security_group_failed: #{inspect(reason)}")
    end
  end

  defp authorize_ssh_ingress(config, sg_id) do
    IO.puts("--- AuthorizeSecurityGroupIngress ---")

    input = %AuthorizeSecurityGroupIngressInput{
      group_id: sg_id,
      ip_permissions: [
        %IpPermission{
          ip_protocol: "tcp",
          from_port: 22,
          to_port: 22,
          ip_ranges: [%IpRange{cidr_ip: "0.0.0.0/0"}]
        }
      ]
    }

    case Ec2Client.authorize_security_group_ingress(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: SSH ingress rule")

      {:error, reason} ->
        raise("authorize_security_group_ingress_failed: #{inspect(reason)}")
    end
  end

  defp describe_vpcs(config, expected_vpc_id) do
    IO.puts("--- DescribeVpcs ---")

    case Ec2Client.describe_vpcs(config, %DescribeVpcsInput{}) do
      {:ok, vpcs} when is_list(vpcs) ->
        case Enum.find(vpcs, &(&1.vpc_id == expected_vpc_id)) do
          %Vpc{vpc_id: vpc_id, cidr_block: cidr_block} ->
            IO.puts("SUCCESS: Found expected VPC #{format_string(vpc_id)} (#{format_string(cidr_block)})")

          nil ->
            raise("assertion failed: vpc_not_found #{inspect(expected_vpc_id)}")
        end

        IO.puts("  Total VPC count: #{length(vpcs)}")

      {:ok, _} ->
        raise("assertion failed: empty VPC list")

      {:error, reason} ->
        raise("describe_vpcs_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp describe_security_groups(config, expected_sg_id) do
    IO.puts("--- DescribeSecurityGroups ---")

    case Ec2Client.describe_security_groups(config, %DescribeSecurityGroupsInput{}) do
      {:ok, sgs} when is_list(sgs) ->
        case Enum.find(sgs, &(&1.group_id == expected_sg_id)) do
          %SecurityGroup{group_id: group_id, group_name: group_name} ->
            IO.puts(
              "SUCCESS: Found expected security group #{format_string(group_id)} (#{format_string(group_name)})"
            )

          nil ->
            raise("assertion failed: security_group_not_found #{inspect(expected_sg_id)}")
        end

        IO.puts("  Total security group count: #{length(sgs)}")

      {:ok, _} ->
        raise("assertion failed: empty security group list")

      {:error, reason} ->
        raise("describe_security_groups_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp run_instance(config, subnet_id, sg_id) do
    IO.puts("--- RunInstances ---")

    input = %RunInstancesInput{
      image_id: "ami-12345678",
      instance_type: :t2_micro,
      min_count: 1,
      max_count: 1,
      subnet_id: subnet_id,
      security_group_ids: [sg_id],
      tag_specifications: [
        %TagSpecification{
          resource_type: :instance,
          tags: [%Tag{key: "Name", value: "ec2-demo-elixir-instance"}]
        }
      ]
    }

    case Ec2Client.run_instances(config, input) do
      {:ok, %{instances: [%Instance{instance_id: instance_id} | _]}}
      when is_binary(instance_id) ->
        IO.puts("SUCCESS: InstanceId = #{instance_id}\n")
        instance_id

      {:ok, _} ->
        raise("assertion failed: RunInstances returned no InstanceId")

      {:error, reason} ->
        raise("run_instances_failed: #{inspect(reason)}")
    end
  end

  defp describe_instance(config, instance_id) do
    IO.puts("--- DescribeInstances ---")

    input = %DescribeInstancesInput{instance_ids: [instance_id]}

    case Ec2Client.describe_instances(config, input) do
      {:ok, reservations} when is_list(reservations) ->
        instances = instances_from_reservations(reservations)
        described_ids = Enum.map(instances, & &1.instance_id)

        unless instance_id in described_ids do
          raise(
            "assertion failed: InstanceId #{inspect(instance_id)} not found in DescribeInstances"
          )
        end

        IO.puts("SUCCESS: InstanceId #{instance_id} found in DescribeInstances")
        print_instances(instances)

      {:error, reason} ->
        raise("describe_instances_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp terminate_instance(config, instance_id) do
    IO.puts("--- TerminateInstances ---")

    input = %TerminateInstancesInput{instance_ids: [instance_id]}

    case Ec2Client.terminate_instances(config, input) do
      {:ok, %{terminating_instances: instances}} ->
        IO.puts("SUCCESS: TerminateInstances returned")
        print_terminating_instances(instances)

      {:error, reason} ->
        raise("terminate_instances_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp instances_from_reservations(nil), do: []

  defp instances_from_reservations(reservations) when is_list(reservations) do
    Enum.flat_map(reservations, fn
      %Reservation{instances: instances} when is_list(instances) -> instances
      _ -> []
    end)
  end

  defp print_instances(instances) do
    IO.puts("  Found #{length(instances)} Instance(s):")

    Enum.each(instances, fn %Instance{
                              instance_id: id,
                              instance_type: type,
                              state: state
                            } ->
      IO.puts(
        "    - #{format_string(id)} (#{format_instance_type(type)}, #{format_state_name(state)})"
      )
    end)
  end

  defp print_terminating_instances(instances) when is_list(instances) do
    IO.puts("  Terminating #{length(instances)} instance(s):")

    Enum.each(instances, fn inst ->
      IO.puts(
        "    - #{format_string(inst.instance_id)} -> #{format_state_name(inst.current_state)}"
      )
    end)
  end

  defp print_terminating_instances(_), do: :ok

  defp format_string(nil), do: "unknown"
  defp format_string(value) when is_binary(value), do: value

  defp format_instance_type(nil), do: "unknown"
  defp format_instance_type(type) when is_atom(type), do: Ec2Types.InstanceType.to_string(type)
  defp format_instance_type(type) when is_binary(type), do: type

  defp format_state_name(nil), do: "unknown"
  defp format_state_name(%{name: name}) when is_atom(name), do: Atom.to_string(name)
  defp format_state_name(%{name: name}) when is_binary(name), do: name
  defp format_state_name(_), do: "unknown"
end
