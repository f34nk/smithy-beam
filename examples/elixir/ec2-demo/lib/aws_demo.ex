defmodule AwsDemo do
  @moduledoc """
  EC2 demo — covers DescribeVpcs, DescribeSecurityGroups, RunInstances,
  DescribeInstances, and TerminateInstances via the generated AwsEc2Client.

  Uses the aws.protocols#ec2Query protocol (XML over HTTPS).
  Run against LocalStack: make demo
  """

  def run do
    IO.puts("\n=== Running EC2 Client Application ===\n")

    config = %{
      endpoint: System.get_env("AWS_ENDPOINT"),
      region: "us-east-1",
      service: "ec2",
      credentials: %{
        access_key_id: "dummy",
        secret_access_key: "dummy"
      }
    }

    {:ok, client} = AwsEc2Client.new(config)
    IO.puts("Client created successfully\n")

    # 1. DescribeVpcs — verify the demo VPC created by Terraform
    IO.puts("--- DescribeVpcs ---")
    case AwsEc2Client.describe_vpcs(client, %{}, %{enable_retry: false}) do
      {:ok, output} ->
        IO.puts("SUCCESS: DescribeVpcs returned")
        vpcs = extract_items(output, ["vpcSet", "Vpcs"])
        IO.puts("  Found #{length(vpcs)} VPC(s):")
        Enum.each(vpcs, fn vpc ->
          id   = Map.get(vpc, "vpcId",    Map.get(vpc, "VpcId",    "unknown"))
          cidr = Map.get(vpc, "cidrBlock", Map.get(vpc, "CidrBlock", "unknown"))
          IO.puts("    - #{id} (#{cidr})")
        end)
      {:error, err} ->
        IO.puts("ERROR: #{inspect(err)}")
    end
    IO.puts("")

    # 2. DescribeSecurityGroups
    IO.puts("--- DescribeSecurityGroups ---")
    case AwsEc2Client.describe_security_groups(client, %{}, %{enable_retry: false}) do
      {:ok, output} ->
        IO.puts("SUCCESS: DescribeSecurityGroups returned")
        sgs = extract_items(output, ["securityGroupInfo", "SecurityGroups"])
        IO.puts("  Found #{length(sgs)} Security Group(s):")
        Enum.each(sgs, fn sg ->
          id   = Map.get(sg, "groupId",   Map.get(sg, "GroupId",   "unknown"))
          name = Map.get(sg, "groupName",  Map.get(sg, "GroupName", "unknown"))
          IO.puts("    - #{id} (#{name})")
        end)
      {:error, err} ->
        IO.puts("ERROR: #{inspect(err)}")
    end
    IO.puts("")

    # 3. RunInstances
    IO.puts("--- RunInstances ---")
    run_input = %{
      "ImageId"      => "ami-12345678",
      "InstanceType" => "t2.micro",
      "MinCount"     => 1,
      "MaxCount"     => 1,
      "TagSpecifications" => [
        %{
          "ResourceType" => "instance",
          "Tags" => [%{"Key" => "Name", "Value" => "ec2-demo-instance"}]
        }
      ]
    }

    instance_id =
      case AwsEc2Client.run_instances(client, run_input, %{enable_retry: false}) do
        {:ok, output} ->
          IO.puts("SUCCESS: RunInstances returned")
          instances = extract_run_instances(output)
          case instances do
            [instance | _] ->
              id = Map.get(instance, "instanceId", Map.get(instance, "InstanceId", "unknown"))
              IO.puts("  Instance ID: #{id}")
              id
            _ ->
              IO.puts("  No instances in response")
              nil
          end
        {:error, err} ->
          IO.puts("ERROR: #{inspect(err)}")
          nil
      end
    IO.puts("")

    # 4. DescribeInstances
    IO.puts("--- DescribeInstances ---")
    describe_input = if instance_id, do: %{"InstanceIds" => [instance_id]}, else: %{}
    case AwsEc2Client.describe_instances(client, describe_input, %{enable_retry: false}) do
      {:ok, output} ->
        IO.puts("SUCCESS: DescribeInstances returned")
        reservations  = extract_items(output, ["reservationSet", "Reservations"])
        all_instances = Enum.flat_map(reservations, &extract_items(&1, ["instancesSet", "Instances"]))
        IO.puts("  Found #{length(all_instances)} Instance(s):")
        Enum.each(all_instances, fn inst ->
          id    = Map.get(inst, "instanceId",   Map.get(inst, "InstanceId",   "unknown"))
          type  = Map.get(inst, "instanceType",  Map.get(inst, "InstanceType", "unknown"))
          state = inst |> Map.get("instanceState", Map.get(inst, "State", %{})) |> Map.get("name", "unknown")
          IO.puts("    - #{id} (#{type}, #{state})")
        end)
      {:error, err} ->
        IO.puts("ERROR: #{inspect(err)}")
    end
    IO.puts("")

    # 5. TerminateInstances
    case instance_id do
      nil ->
        IO.puts("--- TerminateInstances (skipped - no instance) ---")
      id ->
        IO.puts("--- TerminateInstances ---")
        case AwsEc2Client.terminate_instances(client, %{"InstanceIds" => [id]}, %{enable_retry: false}) do
          {:ok, output} ->
            IO.puts("SUCCESS: TerminateInstances returned")
            terminating = extract_items(output, ["instancesSet", "TerminatingInstances"])
            IO.puts("  Terminating #{length(terminating)} instance(s):")
            Enum.each(terminating, fn inst ->
              inst_id = Map.get(inst, "instanceId",   Map.get(inst, "InstanceId",   "unknown"))
              current = inst |> Map.get("currentState", Map.get(inst, "CurrentState", %{})) |> Map.get("name", "unknown")
              IO.puts("    - #{inst_id} -> #{current}")
            end)
          {:error, err} ->
            IO.puts("ERROR: #{inspect(err)}")
        end
    end
    IO.puts("")

    IO.puts("=== EC2 Client Application Complete ===")
    :ok
  end

  # ── Private helpers ─────────────────────────────────────────────────────────

  defp extract_run_instances(response) do
    case Map.get(response, "Instances") do
      items when is_list(items) -> items
      _ ->
        case Map.get(response, "instancesSet") do
          %{"item" => items} when is_list(items) -> items
          %{"item" => item}  when is_map(item)   -> [item]
          items when is_list(items)              -> items
          _                                      -> []
        end
    end
  end

  defp extract_items(_response, []), do: []
  defp extract_items(response, [key | rest]) do
    case Map.get(response, key) do
      nil                                    -> extract_items(response, rest)
      %{"item" => items} when is_list(items) -> items
      %{"item" => item}  when is_map(item)   -> [item]
      items when is_list(items)              -> items
      _                                      -> extract_items(response, rest)
    end
  end
end
