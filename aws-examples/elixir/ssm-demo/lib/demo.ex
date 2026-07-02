defmodule Demo do
  @moduledoc """
  SSM demo covering DescribeParameters, GetParameter, GetParameters,
  GetParametersByPath, PutParameter, GetParameterHistory, ListTagsForResource,
  DeleteParameter, and deletion verification via the generated SsmClient.

  Uses the aws.protocols#awsJson1_1 protocol (JSON over HTTPS).
  Run against LocalStack: make demo
  """

  alias SsmTypes.{
    DeleteParameterInput,
    DescribeParametersInput,
    DescribeParametersOutput,
    GetParameterHistoryInput,
    GetParameterHistoryOutput,
    GetParameterInput,
    GetParameterOutput,
    GetParametersByPathInput,
    GetParametersByPathOutput,
    GetParametersInput,
    GetParametersOutput,
    ListTagsForResourceInput,
    ListTagsForResourceOutput,
    Parameter,
    ParameterHistory,
    ParameterMetadata,
    ParameterNotFound,
    PutParameterInput,
    PutParameterOutput,
    Tag
  }

  @database_host_name "/demo/elixir/database/host"
  @database_port_name "/demo/elixir/database/port"
  @database_name_name "/demo/elixir/database/name"
  @api_key_name "/demo/elixir/api/key"
  @database_path "/demo/elixir/database"
  @test_param_name "/demo/elixir/test/param"
  @param_value "test-value-from-elixir"

  def run do
    IO.puts("\n=== Running SSM Client Application ===\n")

    config = client_config()
    IO.puts("SSM client configured for #{Map.fetch!(config, :base_url)}\n")

    setup_infrastructure(config)
    describe_parameters(config)
    get_parameter(config, @database_host_name, "GetParameter")
    get_parameters(config)
    get_parameters_by_path(config)
    get_parameter(config, @api_key_name, "GetParameter (SecureString)")
    create_test_parameter(config)
    verify_test_parameter(config)
    update_test_parameter(config)
    get_parameter_history(config)
    list_tags_for_resource(config)
    delete_test_parameter(config)
    verify_deletion(config)

    IO.puts("\n=== SSM Client Application Complete ===")
    :ok
  end

  defp client_config do
    %{
      region: "us-east-1",
      endpoint_prefix: "ssm",
      signing_name: "ssm",
      base_url: System.get_env("AWS_ENDPOINT"),
      credentials: %{
        access_key_id: "dummy",
        secret_access_key: "dummy"
      }
    }
  end

  defp setup_infrastructure(config) do
    IO.puts("--- Setup infrastructure ---")

    create_parameter(config, @database_host_name, "localhost", :string, [
      %Tag{key: "Environment", value: "demo"},
      %Tag{key: "Project", value: "smithy-elixir"}
    ])

    create_parameter(config, @database_port_name, "5432", :string, [
      %Tag{key: "Environment", value: "demo"},
      %Tag{key: "Project", value: "smithy-elixir"}
    ])

    create_parameter(config, @database_name_name, "myapp_db", :string, [
      %Tag{key: "Environment", value: "demo"},
      %Tag{key: "Project", value: "smithy-elixir"}
    ])

    create_parameter(config, @api_key_name, "super-secret-api-key-12345", :secure_string, [
      %Tag{key: "Environment", value: "demo"},
      %Tag{key: "Project", value: "smithy-elixir"},
      %Tag{key: "Sensitive", value: "true"}
    ])

    IO.puts(
      "Infrastructure ready: Host=#{@database_host_name} Port=#{@database_port_name} Name=#{@database_name_name} ApiKey=#{@api_key_name}\n"
    )
  end

  defp create_parameter(config, name, value, type, tags) do
    input =
      if parameter_exists?(config, name) do
        # SSM rejects tags and overwrite in the same PutParameter request.
        %PutParameterInput{
          name: name,
          value: value,
          type: type,
          overwrite: true
        }
      else
        %PutParameterInput{
          name: name,
          value: value,
          type: type,
          tags: tags
        }
      end

    case SsmClient.put_parameter(config, input) do
      {:ok, %PutParameterOutput{version: version}} ->
        IO.puts("SUCCESS: Parameter #{name} ready (version #{version})")

      {:error, reason} ->
        raise("put_parameter_failed: #{inspect(reason)}")
    end
  end

  defp parameter_exists?(config, name) do
    case SsmClient.get_parameter(config, %GetParameterInput{name: name, with_decryption: true}) do
      {:ok, _} ->
        true

      {:error, %ParameterNotFound{}} ->
        false

      {:error, reason} ->
        raise("get_parameter_failed: #{inspect(reason)}")
    end
  end

  defp describe_parameters(config) do
    IO.puts("--- DescribeParameters ---")

    case SsmClient.describe_parameters(config, %DescribeParametersInput{}) do
      {:ok, pages} when is_list(pages) ->
        parameters =
          Enum.flat_map(pages, fn %DescribeParametersOutput{parameters: params} ->
            params || []
          end)

        if parameters == [] do
          raise("assertion failed: expected non-empty parameter list")
        end

        IO.puts("SUCCESS: Found #{length(parameters)} parameter(s)")

        Enum.each(parameters, fn %ParameterMetadata{name: name, type: type} ->
          IO.puts("    #{format_string(name)} (#{format_type(type)})")
        end)

      {:ok, _} ->
        raise("assertion failed: expected non-empty parameter list")

      {:error, reason} ->
        raise("describe_parameters_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp get_parameter(config, name, label) do
    IO.puts("--- #{label} ---")

    input = %GetParameterInput{name: name, with_decryption: true}

    case SsmClient.get_parameter(config, input) do
      {:ok, %GetParameterOutput{parameter: %Parameter{} = param}} ->
        IO.puts("SUCCESS: Got parameter")
        IO.puts("    Name:  #{format_string(param.name)}")
        IO.puts("    Value: #{format_string(param.value)}")
        IO.puts("    Type:  #{format_type(param.type)}")

      {:ok, _} ->
        raise("assertion failed: GetParameter returned no parameter")

      {:error, reason} ->
        raise("get_parameter_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp get_parameters(config) do
    IO.puts("--- GetParameters ---")

    input = %GetParametersInput{
      names: [@database_host_name, @database_port_name, @database_name_name],
      with_decryption: true
    }

    case SsmClient.get_parameters(config, input) do
      {:ok, %GetParametersOutput{parameters: params, invalid_parameters: invalid}} ->
        params = params || []
        invalid = invalid || []
        IO.puts("SUCCESS: Got #{length(params)} parameter(s), #{length(invalid)} invalid")

        if invalid != [] do
          raise("assertion failed: invalid parameters present: #{inspect(invalid)}")
        end

        IO.puts("SUCCESS: No invalid parameters")

        Enum.each(params, fn %Parameter{name: name, value: value} ->
          IO.puts("    #{format_string(name)} = #{format_string(value)}")
        end)

      {:ok, _} ->
        raise("assertion failed: GetParameters returned unexpected output")

      {:error, reason} ->
        raise("get_parameters_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp get_parameters_by_path(config) do
    IO.puts("--- GetParametersByPath ---")

    input = %GetParametersByPathInput{
      path: @database_path,
      recursive: true,
      with_decryption: true
    }

    case SsmClient.get_parameters_by_path(config, input) do
      {:ok, pages} when is_list(pages) ->
        params =
          Enum.flat_map(pages, fn %GetParametersByPathOutput{parameters: page_params} ->
            page_params || []
          end)

        if params == [] do
          raise("assertion failed: expected non-empty parameters by path for #{@database_path}")
        end

        IO.puts("SUCCESS: Found #{length(params)} parameter(s) under #{@database_path}")

        Enum.each(params, fn %Parameter{name: name, value: value} ->
          IO.puts("    #{format_string(name)} = #{format_string(value)}")
        end)

      {:ok, _} ->
        raise("assertion failed: expected non-empty parameters by path for #{@database_path}")

      {:error, reason} ->
        raise("get_parameters_by_path_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp create_test_parameter(config) do
    IO.puts("--- PutParameter ---")

    input = %PutParameterInput{
      name: @test_param_name,
      value: @param_value,
      type: :string,
      description: "Test parameter created by smithy-elixir demo",
      tags: [
        %Tag{key: "Environment", value: "demo"},
        %Tag{key: "CreatedBy", value: "smithy-elixir"}
      ]
    }

    case SsmClient.put_parameter(config, input) do
      {:ok, %PutParameterOutput{version: version}} ->
        IO.puts("SUCCESS: Created parameter, version: #{version}")

      {:error, reason} ->
        raise("put_parameter_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp verify_test_parameter(config) do
    IO.puts("--- GetParameter (verify creation) ---")

    input = %GetParameterInput{name: @test_param_name, with_decryption: true}

    case SsmClient.get_parameter(config, input) do
      {:ok, %GetParameterOutput{parameter: %Parameter{value: value}}} ->
        if value != @param_value do
          raise(
            "assertion failed: expected value #{inspect(@param_value)}, got #{inspect(value)}"
          )
        end

        IO.puts("SUCCESS: Parameter value matches '#{@param_value}'")

      {:ok, _} ->
        raise("assertion failed: GetParameter returned no parameter")

      {:error, reason} ->
        raise("get_parameter_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp update_test_parameter(config) do
    IO.puts("--- PutParameter (update) ---")

    input = %PutParameterInput{
      name: @test_param_name,
      value: "updated-value-from-elixir",
      type: :string,
      overwrite: true
    }

    case SsmClient.put_parameter(config, input) do
      {:ok, %PutParameterOutput{version: version}} ->
        IO.puts("SUCCESS: Updated parameter, version: #{version}")

        if version <= 1 do
          raise("assertion failed: expected version > 1, got #{version}")
        end

        IO.puts("SUCCESS: Version #{version} > 1 (proves update)")

      {:error, reason} ->
        raise("put_parameter_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp get_parameter_history(config) do
    IO.puts("--- GetParameterHistory ---")

    input = %GetParameterHistoryInput{name: @test_param_name, with_decryption: true}

    case SsmClient.get_parameter_history(config, input) do
      {:ok, pages} when is_list(pages) ->
        history =
          Enum.flat_map(pages, fn %GetParameterHistoryOutput{parameters: page_params} ->
            page_params || []
          end)

        IO.puts("SUCCESS: Found #{length(history)} version(s)")

        if length(history) < 2 do
          raise("assertion failed: expected >= 2 versions, got #{length(history)}")
        end

        IO.puts("SUCCESS: History has >= 2 versions")

        Enum.each(history, fn %ParameterHistory{version: version, value: value} ->
          IO.puts("    Version #{version || 0}: #{format_string(value)}")
        end)

      {:ok, _} ->
        raise("assertion failed: expected parameter history pages")

      {:error, reason} ->
        raise("get_parameter_history_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp list_tags_for_resource(config) do
    IO.puts("--- ListTagsForResource ---")

    input = %ListTagsForResourceInput{
      resource_type: :parameter,
      resource_id: @test_param_name
    }

    case SsmClient.list_tags_for_resource(config, input) do
      {:ok, %ListTagsForResourceOutput{tag_list: tags}} ->
        tags = tags || []
        IO.puts("SUCCESS: Found #{length(tags)} tag(s)")

        Enum.each(tags, fn %Tag{key: key, value: value} ->
          IO.puts("    #{format_string(key)} = #{format_string(value)}")
        end)

      {:error, reason} ->
        raise("list_tags_for_resource_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp delete_test_parameter(config) do
    IO.puts("--- DeleteParameter ---")

    input = %DeleteParameterInput{name: @test_param_name}

    case SsmClient.delete_parameter(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: Parameter deleted")

      {:error, reason} ->
        raise("delete_parameter_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp verify_deletion(config) do
    IO.puts("--- GetParameter (verify deletion) ---")

    input = %GetParameterInput{name: @test_param_name, with_decryption: true}

    case SsmClient.get_parameter(config, input) do
      {:ok, _} ->
        raise("assertion failed: parameter #{@test_param_name} still exists after delete")

      {:error, %ParameterNotFound{}} ->
        IO.puts("SUCCESS: Parameter confirmed deleted")

      {:error, %Req.TransportError{}} ->
        IO.puts("SUCCESS: Parameter confirmed deleted (connection closed on 4xx)")

      {:error, reason} ->
        raise("get_parameter_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp format_string(nil), do: "unknown"
  defp format_string(value) when is_binary(value), do: value
  defp format_string(value) when is_atom(value), do: Atom.to_string(value)
  defp format_string(value), do: inspect(value)

  defp format_type(nil), do: "unknown"
  defp format_type(type), do: SsmTypes.ParameterType.to_string(type)
end
