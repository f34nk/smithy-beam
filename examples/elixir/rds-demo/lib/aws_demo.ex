defmodule AwsDemo do
  @moduledoc """
  RDS demo — covers DescribeAccountAttributes, DescribeDBSubnetGroups,
  DescribeDBParameterGroups, CreateDBParameterGroup, DescribeDBInstances,
  DescribeDBEngineVersions, DescribeDBClusters,
  DescribeReservedDBInstancesOfferings, and DeleteDBParameterGroup (cleanup)
  via the generated AwsRdsClient.

  Uses the aws.protocols#query (ec2Query) protocol (XML over HTTPS).
  Note: LocalStack free tier has limited RDS support — some operations may
  return 501 errors, but the client API calls work correctly.
  Run against LocalStack: make demo
  """

  @test_param_group_name "rds-demo-test-param-group"

  def run do
    IO.puts("\n=== Running RDS Client Application ===\n")
    IO.puts("Note: LocalStack free tier has limited RDS support.")
    IO.puts("Some operations may return 501 errors, but client API calls work correctly.\n")

    config = %{
      endpoint: System.get_env("AWS_ENDPOINT"),
      region: "us-east-1",
      service: "rds",
      credentials: %{
        access_key_id: "dummy",
        secret_access_key: "dummy"
      }
    }

    {:ok, client} = AwsRdsClient.new(config)
    IO.puts("Client created successfully\n")

    # 1. DescribeAccountAttributes — LocalStack may return 501
    IO.puts("--- DescribeAccountAttributes ---")
    case AwsRdsClient.describe_account_attributes(client, %{}, %{enable_retry: false}) do
      {:ok, output} ->
        quotas = get_list(output, "AccountQuotas", "AccountQuota")
        IO.puts("SUCCESS: Found #{length(quotas)} account quota(s)")
        quotas
        |> Enum.take(5)
        |> Enum.each(fn q ->
          IO.puts("    #{Map.get(q, "AccountQuotaName", "unknown")}: #{Map.get(q, "Used", 0)} / #{Map.get(q, "Max", 0)}")
        end)
        if length(quotas) > 5, do: IO.puts("    ... and #{length(quotas) - 5} more")
      {:error, {:aws_error, 501, _, _}} ->
        IO.puts("INFO: DescribeAccountAttributes not supported (expected in LocalStack free tier)")
      {:error, err} ->
        IO.puts("ERROR (expected in LocalStack free tier): #{inspect(err)}")
    end
    IO.puts("")

    # 2. DescribeDBSubnetGroups — LocalStack may return 501
    IO.puts("--- DescribeDBSubnetGroups ---")
    case AwsRdsClient.describe_db_subnet_groups(client, %{}, %{enable_retry: false}) do
      {:ok, output} ->
        groups = get_list(output, "DBSubnetGroups", "DBSubnetGroup")
        IO.puts("SUCCESS: Found #{length(groups)} DB subnet group(s)")
        Enum.each(groups, fn g ->
          IO.puts("    #{Map.get(g, "DBSubnetGroupName", "unknown")} (Status: #{Map.get(g, "SubnetGroupStatus", "unknown")})")
        end)
      {:error, {:aws_error, 501, _, _}} ->
        IO.puts("INFO: DescribeDBSubnetGroups not supported (expected in LocalStack free tier)")
      {:error, err} ->
        IO.puts("ERROR (expected in LocalStack free tier): #{inspect(err)}")
    end
    IO.puts("")

    # 3. DescribeDBParameterGroups — LocalStack may return 501
    IO.puts("--- DescribeDBParameterGroups ---")
    case AwsRdsClient.describe_db_parameter_groups(client, %{}, %{enable_retry: false}) do
      {:ok, output} ->
        groups = get_list(output, "DBParameterGroups", "DBParameterGroup")
        IO.puts("SUCCESS: Found #{length(groups)} DB parameter group(s)")
        Enum.each(groups, fn g ->
          IO.puts("    #{Map.get(g, "DBParameterGroupName", "unknown")} (Family: #{Map.get(g, "DBParameterGroupFamily", "unknown")})")
        end)
      {:error, {:aws_error, 501, _, _}} ->
        IO.puts("INFO: DescribeDBParameterGroups not supported (expected in LocalStack free tier)")
      {:error, err} ->
        IO.puts("ERROR (expected in LocalStack free tier): #{inspect(err)}")
    end
    IO.puts("")

    # 4. CreateDBParameterGroup
    IO.puts("--- CreateDBParameterGroup ---")
    create_input = %{
      "DBParameterGroupName"   => @test_param_group_name,
      "DBParameterGroupFamily" => "mysql8.0",
      "Description"            => "Test parameter group created by smithy-elixir demo",
      "Tags" => [
        %{"Key" => "Environment", "Value" => "demo"},
        %{"Key" => "CreatedBy",   "Value" => "smithy-elixir"}
      ]
    }
    param_group_created =
      case AwsRdsClient.create_db_parameter_group(client, create_input, %{enable_retry: false}) do
        {:ok, output} ->
          created = Map.get(output, "DBParameterGroup", %{})
          created_name = Map.get(created, "DBParameterGroupName", "")
          if created_name != @test_param_group_name,
            do: raise("assertion failed: expected group name #{inspect(@test_param_group_name)}, got #{inspect(created_name)}")
          IO.puts("SUCCESS: group name matches '#{@test_param_group_name}'")
          true
        {:error, {:aws_error, 501, _, _}} ->
          IO.puts("INFO: CreateDBParameterGroup not supported (expected in LocalStack free tier)")
          false
        {:error, err} ->
          IO.puts("ERROR (expected in LocalStack free tier): #{inspect(err)}")
          false
      end
    IO.puts("")

    # 4b. DescribeDBParameterGroups after create — assert created group in list
    if param_group_created do
      IO.puts("--- DescribeDBParameterGroups (after create) ---")
      case AwsRdsClient.describe_db_parameter_groups(client, %{}, %{enable_retry: false}) do
        {:ok, output} ->
          groups = get_list(output, "DBParameterGroups", "DBParameterGroup")
          names = Enum.map(groups, &Map.get(&1, "DBParameterGroupName", ""))
          unless @test_param_group_name in names,
            do: raise("assertion failed: created group #{inspect(@test_param_group_name)} not found in list")
          IO.puts("SUCCESS: Created group '#{@test_param_group_name}' in list")
        {:error, {:aws_error, 501, _, _}} ->
          IO.puts("INFO: DescribeDBParameterGroups not supported")
        {:error, err} ->
          IO.puts("ERROR: #{inspect(err)}")
      end
      IO.puts("")
    end

    # 5. DescribeDBInstances — LocalStack may return 501
    IO.puts("--- DescribeDBInstances ---")
    case AwsRdsClient.describe_db_instances(client, %{}, %{enable_retry: false}) do
      {:ok, output} ->
        instances = get_list(output, "DBInstances", "DBInstance")
        IO.puts("SUCCESS: Found #{length(instances)} DB instance(s)")
        Enum.each(instances, fn i ->
          IO.puts("    #{Map.get(i, "DBInstanceIdentifier", "unknown")} (#{Map.get(i, "Engine", "unknown")}, #{Map.get(i, "DBInstanceClass", "unknown")}) - #{Map.get(i, "DBInstanceStatus", "unknown")}")
        end)
      {:error, {:aws_error, 501, _, _}} ->
        IO.puts("INFO: DescribeDBInstances not supported (expected in LocalStack free tier)")
      {:error, err} ->
        IO.puts("ERROR (expected in LocalStack free tier): #{inspect(err)}")
    end
    IO.puts("")

    # 6. DescribeDBEngineVersions — LocalStack may return 501
    IO.puts("--- DescribeDBEngineVersions ---")
    engine_input = %{"Engine" => "mysql", "MaxRecords" => 5}
    case AwsRdsClient.describe_db_engine_versions(client, engine_input, %{enable_retry: false}) do
      {:ok, output} ->
        versions = get_list(output, "DBEngineVersions", "DBEngineVersion")
        IO.puts("SUCCESS: Found #{length(versions)} engine version(s)")
        Enum.each(versions, fn v ->
          IO.puts("    #{Map.get(v, "Engine", "unknown")} #{Map.get(v, "EngineVersion", "unknown")}")
        end)
      {:error, {:aws_error, 501, _, _}} ->
        IO.puts("INFO: DescribeDBEngineVersions not supported (expected in LocalStack free tier)")
      {:error, err} ->
        IO.puts("ERROR (expected in LocalStack free tier): #{inspect(err)}")
    end
    IO.puts("")

    # 7. DescribeDBClusters — LocalStack may return 501
    IO.puts("--- DescribeDBClusters ---")
    case AwsRdsClient.describe_db_clusters(client, %{}, %{enable_retry: false}) do
      {:ok, output} ->
        clusters = get_list(output, "DBClusters", "DBCluster")
        IO.puts("SUCCESS: Found #{length(clusters)} DB cluster(s)")
        Enum.each(clusters, fn c ->
          IO.puts("    #{Map.get(c, "DBClusterIdentifier", "unknown")} (#{Map.get(c, "Engine", "unknown")}) - #{Map.get(c, "Status", "unknown")}")
        end)
      {:error, {:aws_error, 501, _, _}} ->
        IO.puts("INFO: DescribeDBClusters not supported (expected in LocalStack free tier)")
      {:error, err} ->
        IO.puts("ERROR (expected in LocalStack free tier): #{inspect(err)}")
    end
    IO.puts("")

    # 8. DescribeReservedDBInstancesOfferings — LocalStack may return 501
    IO.puts("--- DescribeReservedDBInstancesOfferings ---")
    case AwsRdsClient.describe_reserved_db_instances_offerings(client, %{"MaxRecords" => 5}, %{enable_retry: false}) do
      {:ok, output} ->
        offerings = get_list(output, "ReservedDBInstancesOfferings", "ReservedDBInstancesOffering")
        IO.puts("SUCCESS: Found #{length(offerings)} reserved instance offering(s)")
        offerings
        |> Enum.take(3)
        |> Enum.each(fn o ->
          IO.puts("    #{Map.get(o, "ReservedDBInstancesOfferingId", "unknown")} (#{Map.get(o, "DBInstanceClass", "unknown")})")
        end)
      {:error, {:aws_error, 501, _, _}} ->
        IO.puts("INFO: DescribeReservedDBInstancesOfferings not supported (expected in LocalStack free tier)")
      {:error, err} ->
        IO.puts("ERROR (expected in LocalStack free tier): #{inspect(err)}")
    end
    IO.puts("")

    # 9. DeleteDBParameterGroup (cleanup)
    if param_group_created do
      IO.puts("--- DeleteDBParameterGroup ---")
      delete_input = %{"DBParameterGroupName" => @test_param_group_name}
      case AwsRdsClient.delete_db_parameter_group(client, delete_input, %{enable_retry: false}) do
        {:ok, _} ->
          IO.puts("SUCCESS: Parameter group deleted")
        {:error, {:aws_error, 501, _, _}} ->
          IO.puts("INFO: DeleteDBParameterGroup not supported (expected in LocalStack free tier)")
        {:error, err} ->
          raise("delete_db_parameter_group_failed: #{inspect(err)}")
      end
      IO.puts("")

      # 9b. DescribeDBParameterGroups after delete — assert group absent
      IO.puts("--- DescribeDBParameterGroups (after delete) ---")
      case AwsRdsClient.describe_db_parameter_groups(client, %{}, %{enable_retry: false}) do
        {:ok, output} ->
          groups = get_list(output, "DBParameterGroups", "DBParameterGroup")
          names = Enum.map(groups, &Map.get(&1, "DBParameterGroupName", ""))
          if @test_param_group_name in names,
            do: raise("assertion failed: deleted group #{inspect(@test_param_group_name)} still present")
          IO.puts("SUCCESS: Deleted group '#{@test_param_group_name}' no longer in list")
        {:error, {:aws_error, 501, _, _}} ->
          IO.puts("INFO: DescribeDBParameterGroups not supported")
        {:error, err} ->
          IO.puts("ERROR: #{inspect(err)}")
      end
      IO.puts("")
    end

    IO.puts("=== RDS Client Application Complete ===")
    :ok
  end

  # Unwrap XML-style list responses that may be nested under a wrapper element.
  defp get_list(response, outer_key, inner_key) do
    case Map.get(response, outer_key) do
      nil -> []
      wrapper when is_map(wrapper) ->
        case Map.get(wrapper, inner_key) do
          nil -> []
          list when is_list(list) -> list
          item when is_map(item) -> [item]
        end
      list when is_list(list) -> list
    end
  end
end
