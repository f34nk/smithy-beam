defmodule Demo do
  @moduledoc """
  RDS demo covering DescribeAccountAttributes, DescribeDBSubnetGroups,
  DescribeDBParameterGroups, CreateDBParameterGroup, DescribeDBInstances,
  DescribeDBEngineVersions, DescribeDBClusters,
  DescribeReservedDBInstancesOfferings, and DeleteDBParameterGroup via the
  generated RdsClient.

  Uses the aws.protocols#awsQuery protocol (XML over HTTPS).
  Run against LocalStack: make demo
  """

  alias RdsTypes.{
    AccountQuota,
    CreateDbParameterGroupInput,
    CreateDbParameterGroupOutput,
    DbCluster,
    DbEngineVersion,
    DbInstance,
    DbParameterGroup,
    DbParameterGroupAlreadyExistsFault,
    DbSubnetGroup,
    DeleteDbParameterGroupInput,
    DescribeAccountAttributesInput,
    DescribeAccountAttributesOutput,
    DescribeDbClustersInput,
    DescribeDbClustersOutput,
    DescribeDbEngineVersionsInput,
    DescribeDbEngineVersionsOutput,
    DescribeDbInstancesInput,
    DescribeDbInstancesOutput,
    DescribeDbParameterGroupsInput,
    DescribeDbParameterGroupsOutput,
    DescribeDbSubnetGroupsInput,
    DescribeDbSubnetGroupsOutput,
    DescribeReservedDbInstancesOfferingsInput,
    DescribeReservedDbInstancesOfferingsOutput,
    ReservedDbInstancesOffering,
    Tag
  }

  @test_param_group_name "rds-demo-elixir-test-param-group"

  def run do
    IO.puts("\n=== Running RDS Client Application ===\n")
    IO.puts("Note: LocalStack free tier has limited RDS support.")
    IO.puts("Some operations may return 501 errors, but client API calls work correctly.\n")

    config = client_config()
    IO.puts("RDS client configured for #{Map.fetch!(config, :base_url)}\n")

    describe_account_attributes(config)
    describe_db_subnet_groups(config)
    describe_db_parameter_groups(config, "DescribeDBParameterGroups")
    param_group_created = create_db_parameter_group(config)

    if param_group_created do
      describe_db_parameter_groups(config, "DescribeDBParameterGroups (after create)")
      verify_created_group_in_list(config)
    end

    describe_db_instances(config)
    describe_db_engine_versions(config)
    describe_db_clusters(config)
    describe_reserved_db_instances_offerings(config)

    if param_group_created do
      delete_db_parameter_group(config)
      describe_db_parameter_groups(config, "DescribeDBParameterGroups (after delete)")
      verify_deleted_group_not_in_list(config)
    end

    IO.puts("=== RDS Client Application Complete ===")
    :ok
  end

  defp client_config do
    %{
      region: "us-east-1",
      endpoint_prefix: "rds",
      signing_name: "rds",
      base_url: System.get_env("AWS_ENDPOINT"),
      credentials: %{
        access_key_id: "dummy",
        secret_access_key: "dummy"
      }
    }
  end

  defp describe_account_attributes(config) do
    IO.puts("--- DescribeAccountAttributes ---")

    case RdsClient.describe_account_attributes(config, %DescribeAccountAttributesInput{}) do
      {:ok, %DescribeAccountAttributesOutput{account_quotas: quotas}} when is_list(quotas) ->
        IO.puts("SUCCESS: Found #{length(quotas)} account quota(s)")

        quotas
        |> Enum.take(5)
        |> Enum.each(fn %AccountQuota{account_quota_name: name, used: used, max: max} ->
          IO.puts("    #{name}: #{used} / #{max}")
        end)

        if length(quotas) > 5 do
          IO.puts("    ... and #{length(quotas) - 5} more")
        end

      {:error, reason} ->
        handle_optional_error(reason, "DescribeAccountAttributes")
    end

    IO.puts("")
  end

  defp describe_db_subnet_groups(config) do
    IO.puts("--- DescribeDBSubnetGroups ---")

    case RdsClient.describe_db_subnet_groups(config, %DescribeDbSubnetGroupsInput{}) do
      {:ok, %DescribeDbSubnetGroupsOutput{db_subnet_groups: groups}} when is_list(groups) ->
        IO.puts("SUCCESS: Found #{length(groups)} DB subnet group(s)")

        Enum.each(groups, fn %DbSubnetGroup{
                               db_subnet_group_name: name,
                               subnet_group_status: status
                             } ->
          IO.puts("    #{name} (Status: #{status})")
        end)

      {:error, reason} ->
        handle_optional_error(reason, "DescribeDBSubnetGroups")
    end

    IO.puts("")
  end

  defp describe_db_parameter_groups(config, label) do
    IO.puts("--- #{label} ---")

    case RdsClient.describe_db_parameter_groups(config, %DescribeDbParameterGroupsInput{}) do
      {:ok, %DescribeDbParameterGroupsOutput{db_parameter_groups: groups}} when is_list(groups) ->
        IO.puts("SUCCESS: Found #{length(groups)} DB parameter group(s)")

        Enum.each(groups, fn %DbParameterGroup{
                               db_parameter_group_name: name,
                               db_parameter_group_family: family
                             } ->
          IO.puts("    #{name} (Family: #{family})")
        end)

      {:error, reason} ->
        if String.contains?(label, "after") do
          IO.puts("ERROR: #{inspect(reason)}")
        else
          handle_optional_error(reason, "DescribeDBParameterGroups")
        end
    end

    IO.puts("")
  end

  defp create_db_parameter_group(config) do
    IO.puts("--- CreateDBParameterGroup ---")

    input = %CreateDbParameterGroupInput{
      db_parameter_group_name: @test_param_group_name,
      db_parameter_group_family: "mysql8.0",
      description: "Test parameter group created by smithy-elixir demo",
      tags: [
        %Tag{key: "Environment", value: "demo"},
        %Tag{key: "CreatedBy", value: "smithy-elixir"}
      ]
    }

    result =
      case RdsClient.create_db_parameter_group(config, input) do
        {:ok,
         %CreateDbParameterGroupOutput{
           db_parameter_group: %DbParameterGroup{db_parameter_group_name: @test_param_group_name}
         }} ->
          IO.puts("SUCCESS: group name matches '#{@test_param_group_name}'")
          true

        {:error, %DbParameterGroupAlreadyExistsFault{}} ->
          IO.puts("SUCCESS: group '#{@test_param_group_name}' already exists")
          true

        {:error, reason} ->
          case handle_optional_error(reason, "CreateDBParameterGroup") do
            :not_supported -> false
            :error -> false
          end
      end

    IO.puts("")
    result
  end

  defp verify_created_group_in_list(config) do
    case RdsClient.describe_db_parameter_groups(config, %DescribeDbParameterGroupsInput{}) do
      {:ok, %DescribeDbParameterGroupsOutput{db_parameter_groups: groups}} when is_list(groups) ->
        names = Enum.map(groups, & &1.db_parameter_group_name)

        if @test_param_group_name in names do
          IO.puts("SUCCESS: Created group '#{@test_param_group_name}' in list")
        else
          raise("assertion failed: created group not found: #{@test_param_group_name}")
        end

      {:error, reason} ->
        if not_implemented?(reason) do
          IO.puts("INFO: DescribeDBParameterGroups not supported")
        else
          IO.puts("ERROR: #{inspect(reason)}")
        end
    end

    IO.puts("")
  end

  defp describe_db_instances(config) do
    IO.puts("--- DescribeDBInstances ---")

    case RdsClient.describe_db_instances(config, %DescribeDbInstancesInput{}) do
      {:ok, %DescribeDbInstancesOutput{db_instances: instances}} when is_list(instances) ->
        IO.puts("SUCCESS: Found #{length(instances)} DB instance(s)")

        Enum.each(instances, fn %DbInstance{
                                  db_instance_identifier: id,
                                  db_instance_class: class,
                                  engine: engine,
                                  db_instance_status: status
                                } ->
          IO.puts("    #{id} (#{engine}, #{class}) - #{status}")
        end)

      {:error, reason} ->
        handle_optional_error(reason, "DescribeDBInstances")
    end

    IO.puts("")
  end

  defp describe_db_engine_versions(config) do
    IO.puts("--- DescribeDBEngineVersions ---")

    input = %DescribeDbEngineVersionsInput{
      engine: "mysql",
      max_records: 5
    }

    case RdsClient.describe_db_engine_versions(config, input) do
      {:ok, %DescribeDbEngineVersionsOutput{db_engine_versions: versions}} when is_list(versions) ->
        IO.puts("SUCCESS: Found #{length(versions)} engine version(s)")

        Enum.each(versions, fn %DbEngineVersion{engine: engine, engine_version: version} ->
          IO.puts("    #{engine} #{version}")
        end)

      {:error, reason} ->
        handle_optional_error(reason, "DescribeDBEngineVersions")
    end

    IO.puts("")
  end

  defp describe_db_clusters(config) do
    IO.puts("--- DescribeDBClusters ---")

    case RdsClient.describe_db_clusters(config, %DescribeDbClustersInput{}) do
      {:ok, %DescribeDbClustersOutput{db_clusters: clusters}} when is_list(clusters) ->
        IO.puts("SUCCESS: Found #{length(clusters)} DB cluster(s)")

        Enum.each(clusters, fn %DbCluster{
                                 db_cluster_identifier: id,
                                 engine: engine,
                                 status: status
                               } ->
          IO.puts("    #{id} (#{engine}) - #{status}")
        end)

      {:error, reason} ->
        handle_optional_error(reason, "DescribeDBClusters")
    end

    IO.puts("")
  end

  defp describe_reserved_db_instances_offerings(config) do
    IO.puts("--- DescribeReservedDBInstancesOfferings ---")

    input = %DescribeReservedDbInstancesOfferingsInput{max_records: 5}

    case RdsClient.describe_reserved_db_instances_offerings(config, input) do
      {:ok,
       %DescribeReservedDbInstancesOfferingsOutput{
         reserved_db_instances_offerings: offerings
       }}
      when is_list(offerings) ->
        IO.puts("SUCCESS: Found #{length(offerings)} reserved instance offering(s)")

        offerings
        |> Enum.take(3)
        |> Enum.each(fn %ReservedDbInstancesOffering{
                          reserved_db_instances_offering_id: id,
                          db_instance_class: class
                        } ->
          IO.puts("    #{id} (#{class})")
        end)

      {:error, reason} ->
        handle_optional_error(reason, "DescribeReservedDBInstancesOfferings")
    end

    IO.puts("")
  end

  defp delete_db_parameter_group(config) do
    IO.puts("--- DeleteDBParameterGroup ---")

    input = %DeleteDbParameterGroupInput{
      db_parameter_group_name: @test_param_group_name
    }

    case RdsClient.delete_db_parameter_group(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: Parameter group deleted")

      {:error, reason} ->
        if not_implemented?(reason) do
          IO.puts("INFO: DeleteDBParameterGroup not supported (expected in LocalStack free tier)")
        else
          raise("delete_db_parameter_group_failed: #{inspect(reason)}")
        end
    end

    IO.puts("")
  end

  defp verify_deleted_group_not_in_list(config) do
    case RdsClient.describe_db_parameter_groups(config, %DescribeDbParameterGroupsInput{}) do
      {:ok, %DescribeDbParameterGroupsOutput{db_parameter_groups: groups}} when is_list(groups) ->
        names = Enum.map(groups, & &1.db_parameter_group_name)

        if @test_param_group_name in names do
          raise("assertion failed: deleted group still present: #{@test_param_group_name}")
        else
          IO.puts("SUCCESS: Deleted group '#{@test_param_group_name}' no longer in list")
        end

      {:error, reason} ->
        if not_implemented?(reason) do
          IO.puts("INFO: DescribeDBParameterGroups not supported")
        else
          IO.puts("ERROR: #{inspect(reason)}")
        end
    end

    IO.puts("")
  end

  defp handle_optional_error(reason, operation) do
    cond do
      not_implemented?(reason) ->
        IO.puts("INFO: #{operation} not supported (expected in LocalStack free tier)")
        :not_supported

      true ->
        IO.puts("ERROR (expected in LocalStack free tier): #{inspect(reason)}")
        :error
    end
  end

  defp not_implemented?({:unknown_error, 501, _}), do: true
  defp not_implemented?(_), do: false
end
