defmodule AwsDemo do
  @moduledoc """
  SSM demo — covers DescribeParameters, GetParameter, GetParameters,
  GetParametersByPath, PutParameter, GetParameterHistory, ListTagsForResource,
  DeleteParameter, and deletion verification via the generated AwsSsmClient.

  Uses the aws.protocols#awsJson1_1 protocol (JSON over HTTPS).
  Run against LocalStack: make demo
  """

  @test_param_name "/demo/test/param"
  @test_param_path "/demo/test"

  def run do
    IO.puts("\n=== Running SSM Client Application ===\n")

    config = %{
      endpoint: System.get_env("AWS_ENDPOINT"),
      region: "us-east-1",
      service: "ssm",
      credentials: %{
        access_key_id: "dummy",
        secret_access_key: "dummy"
      }
    }

    {:ok, client} = AwsSsmClient.new(config)
    IO.puts("Client created successfully\n")

    # 1. DescribeParameters — verify parameters created by Terraform
    IO.puts("--- DescribeParameters ---")
    case AwsSsmClient.describe_parameters(client, %{}, %{enable_retry: false}) do
      {:ok, output} ->
        parameters = Map.get(output, "Parameters", [])
        IO.puts("SUCCESS: Found #{length(parameters)} parameter(s)")
        Enum.each(parameters, fn param ->
          name = Map.get(param, "Name", "unknown")
          type = Map.get(param, "Type", "unknown")
          IO.puts("    #{name} (#{type})")
        end)
      {:error, err} ->
        IO.puts("ERROR: #{inspect(err)}")
    end
    IO.puts("")

    # 2. GetParameter — fetch a single Terraform-created parameter
    IO.puts("--- GetParameter ---")
    get_input = %{"Name" => "/demo/database/host", "WithDecryption" => true}
    case AwsSsmClient.get_parameter(client, get_input, %{enable_retry: false}) do
      {:ok, output} ->
        param = Map.get(output, "Parameter", %{})
        IO.puts("SUCCESS: Got parameter")
        IO.puts("    Name:  #{Map.get(param, "Name",  "unknown")}")
        IO.puts("    Value: #{Map.get(param, "Value", "unknown")}")
        IO.puts("    Type:  #{Map.get(param, "Type",  "unknown")}")
      {:error, err} ->
        IO.puts("ERROR: #{inspect(err)}")
    end
    IO.puts("")

    # 3. GetParameters — fetch multiple parameters at once
    IO.puts("--- GetParameters ---")
    get_multi_input = %{
      "Names"          => ["/demo/database/host", "/demo/database/port", "/demo/database/name"],
      "WithDecryption" => true
    }
    case AwsSsmClient.get_parameters(client, get_multi_input, %{enable_retry: false}) do
      {:ok, output} ->
        params  = Map.get(output, "Parameters",        [])
        invalid = Map.get(output, "InvalidParameters", [])
        IO.puts("SUCCESS: Got #{length(params)} parameter(s), #{length(invalid)} invalid")
        Enum.each(params, fn p ->
          IO.puts("    #{Map.get(p, "Name", "?")} = #{Map.get(p, "Value", "?")}")
        end)
      {:error, err} ->
        IO.puts("ERROR: #{inspect(err)}")
    end
    IO.puts("")

    # 4. GetParametersByPath
    IO.puts("--- GetParametersByPath ---")
    path_input = %{
      "Path"           => "/demo/database",
      "Recursive"      => true,
      "WithDecryption" => true
    }
    case AwsSsmClient.get_parameters_by_path(client, path_input, %{enable_retry: false}) do
      {:ok, output} ->
        params = Map.get(output, "Parameters", [])
        IO.puts("SUCCESS: Found #{length(params)} parameter(s) under /demo/database")
        Enum.each(params, fn p ->
          IO.puts("    #{Map.get(p, "Name", "?")} = #{Map.get(p, "Value", "?")}")
        end)
      {:error, err} ->
        IO.puts("ERROR: #{inspect(err)}")
    end
    IO.puts("")

    # 5. GetParameter — SecureString
    IO.puts("--- GetParameter (SecureString) ---")
    secure_input = %{"Name" => "/demo/api/key", "WithDecryption" => true}
    case AwsSsmClient.get_parameter(client, secure_input, %{enable_retry: false}) do
      {:ok, output} ->
        param = Map.get(output, "Parameter", %{})
        IO.puts("SUCCESS: Got secure parameter")
        IO.puts("    Name:  #{Map.get(param, "Name",  "unknown")}")
        IO.puts("    Value: #{Map.get(param, "Value", "unknown")} (decrypted)")
        IO.puts("    Type:  #{Map.get(param, "Type",  "unknown")}")
      {:error, err} ->
        IO.puts("ERROR: #{inspect(err)}")
    end
    IO.puts("")

    # 6. PutParameter — create a test parameter
    IO.puts("--- PutParameter ---")
    put_input = %{
      "Name"        => @test_param_name,
      "Value"       => "test-value-from-elixir",
      "Type"        => "String",
      "Description" => "Test parameter created by smithy-elixir demo",
      "Tags"        => [
        %{"Key" => "Environment", "Value" => "demo"},
        %{"Key" => "CreatedBy",   "Value" => "smithy-elixir"}
      ]
    }
    param_created =
      case AwsSsmClient.put_parameter(client, put_input, %{enable_retry: false}) do
        {:ok, output} ->
          IO.puts("SUCCESS: Created parameter, version: #{Map.get(output, "Version", 0)}")
          true
        {:error, err} ->
          IO.puts("ERROR: #{inspect(err)}")
          false
      end
    IO.puts("")

    if param_created do
      # 7. GetParameter — verify creation
      IO.puts("--- GetParameter (verify creation) ---")
      verify_input = %{"Name" => @test_param_name, "WithDecryption" => true}
      case AwsSsmClient.get_parameter(client, verify_input, %{enable_retry: false}) do
        {:ok, output} ->
          value = output |> Map.get("Parameter", %{}) |> Map.get("Value", "unknown")
          IO.puts("SUCCESS: Value = #{value}")
        {:error, err} ->
          IO.puts("ERROR: #{inspect(err)}")
      end
      IO.puts("")

      # 8. PutParameter — overwrite / update
      IO.puts("--- PutParameter (update) ---")
      update_input = %{
        "Name"      => @test_param_name,
        "Value"     => "updated-value-from-elixir",
        "Type"      => "String",
        "Overwrite" => true
      }
      case AwsSsmClient.put_parameter(client, update_input, %{enable_retry: false}) do
        {:ok, output} ->
          IO.puts("SUCCESS: Updated parameter, version: #{Map.get(output, "Version", 0)}")
        {:error, err} ->
          IO.puts("ERROR: #{inspect(err)}")
      end
      IO.puts("")

      # 9. GetParameterHistory
      IO.puts("--- GetParameterHistory ---")
      history_input = %{"Name" => @test_param_name, "WithDecryption" => true}
      case AwsSsmClient.get_parameter_history(client, history_input, %{enable_retry: false}) do
        {:ok, output} ->
          history = Map.get(output, "Parameters", [])
          IO.puts("SUCCESS: Found #{length(history)} version(s)")
          Enum.each(history, fn h ->
            IO.puts("    Version #{Map.get(h, "Version", 0)}: #{Map.get(h, "Value", "?")}")
          end)
        {:error, err} ->
          IO.puts("ERROR: #{inspect(err)}")
      end
      IO.puts("")

      # 10. ListTagsForResource
      IO.puts("--- ListTagsForResource ---")
      tags_input = %{"ResourceType" => "Parameter", "ResourceId" => @test_param_name}
      case AwsSsmClient.list_tags_for_resource(client, tags_input, %{enable_retry: false}) do
        {:ok, output} ->
          tags = Map.get(output, "TagList", [])
          IO.puts("SUCCESS: Found #{length(tags)} tag(s)")
          Enum.each(tags, fn tag ->
            IO.puts("    #{Map.get(tag, "Key", "?")} = #{Map.get(tag, "Value", "?")}")
          end)
        {:error, err} ->
          IO.puts("ERROR: #{inspect(err)}")
      end
      IO.puts("")

      # 11. DeleteParameter
      IO.puts("--- DeleteParameter ---")
      delete_input = %{"Name" => @test_param_name}
      case AwsSsmClient.delete_parameter(client, delete_input, %{enable_retry: false}) do
        {:ok, _} ->
          IO.puts("SUCCESS: Parameter deleted")
        {:error, err} ->
          IO.puts("ERROR: #{inspect(err)}")
      end
      IO.puts("")

      # 12. GetParameter — verify deletion
      IO.puts("--- GetParameter (verify deletion) ---")
      case AwsSsmClient.get_parameter(client, verify_input, %{enable_retry: false}) do
        {:ok, _} ->
          IO.puts("UNEXPECTED: Parameter still exists")
        {:error, {:aws_error, 400, "ParameterNotFound", _}} ->
          IO.puts("SUCCESS: Parameter confirmed deleted")
        {:error, err} ->
          IO.puts("Parameter not found (deleted): #{inspect(err)}")
      end
    end

    IO.puts("\n=== SSM Client Application Complete ===")
    :ok
  end
end
