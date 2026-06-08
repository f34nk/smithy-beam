defmodule AwsDemo do
  @moduledoc """
  EC2 demo covering DescribeVpcs, DescribeSecurityGroups, RunInstances,
  DescribeInstances, and TerminateInstances via the generated AmazonEc2Client.

  Uses the aws.protocols#ec2Query protocol (XML over HTTPS).
  Run against LocalStack: make demo
  """

  alias AmazonEc2Types.{
    DescribeInstancesInput,
    DescribeSecurityGroupsInput,
    DescribeVpcsInput,
    Instance,
    Reservation,
    RunInstancesInput,
    Tag,
    TagSpecification,
    TerminateInstancesInput,
    Vpc
  }

  def run do
    IO.puts("\n=== Running EC2 Client Application ===\n")

    config = client_config()
    IO.puts("EC2 client configured for #{Map.fetch!(config, :base_url)}\n")

    describe_vpcs(config)
    describe_security_groups(config)
    instance_id = run_instance(config)
    describe_instance(config, instance_id)
    terminate_instance(config, instance_id)

    IO.puts("=== EC2 Client Application Complete ===")
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

  defp describe_vpcs(config) do
    IO.puts("--- DescribeVpcs ---")

    case AmazonEc2Client.describe_vpcs(config, %DescribeVpcsInput{}) do
      {:ok, %{vpcs: vpcs}} when is_list(vpcs) and vpcs != [] ->
        IO.puts("SUCCESS: Found #{length(vpcs)} VPC(s)")
        Enum.each(vpcs, fn %Vpc{vpc_id: id, cidr_block: cidr} ->
          IO.puts("    - #{id} (#{cidr})")
        end)

      {:ok, _} ->
        raise("assertion failed: empty VPC list")

      {:error, reason} ->
        raise("describe_vpcs_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp describe_security_groups(config) do
    IO.puts("--- DescribeSecurityGroups ---")

    case AmazonEc2Client.describe_security_groups(config, %DescribeSecurityGroupsInput{}) do
      {:ok, %{security_groups: sgs}} when is_list(sgs) and sgs != [] ->
        IO.puts("SUCCESS: Found #{length(sgs)} Security Group(s)")
        Enum.each(sgs, fn sg ->
          IO.puts("    - #{sg.group_id} (#{sg.group_name})")
        end)

      {:ok, _} ->
        raise("assertion failed: empty security group list")

      {:error, reason} ->
        raise("describe_security_groups_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp run_instance(config) do
    IO.puts("--- RunInstances ---")

    input = %RunInstancesInput{
      image_id: "ami-12345678",
      instance_type: :t2_micro,
      min_count: 1,
      max_count: 1,
      tag_specifications: [
        %TagSpecification{
          resource_type: :instance,
          tags: [%Tag{key: "Name", value: "ec2-demo-instance"}]
        }
      ]
    }

    case AmazonEc2Client.run_instances(config, input) do
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

    case AmazonEc2Client.describe_instances(config, input) do
      {:ok, %{reservations: reservations}} ->
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

    case AmazonEc2Client.terminate_instances(config, input) do
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
      state_name =
        case state do
          %{name: name} when not is_nil(name) -> name
          _ -> "unknown"
        end

      IO.puts("    - #{id} (#{type}, #{state_name})")
    end)
  end

  defp print_terminating_instances(instances) when is_list(instances) do
    IO.puts("  Terminating #{length(instances)} instance(s):")

    Enum.each(instances, fn inst ->
      state_name =
        case inst.current_state do
          %{name: name} when not is_nil(name) -> name
          _ -> "unknown"
        end

      IO.puts("    - #{inst.instance_id} -> #{state_name}")
    end)
  end

  defp print_terminating_instances(_), do: :ok
end
