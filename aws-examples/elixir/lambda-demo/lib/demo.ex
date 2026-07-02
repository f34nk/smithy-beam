defmodule Demo do
  @moduledoc """
  Lambda demo covering GetAccountSettings, ListFunctions, GetFunction, Invoke,
  UpdateFunctionConfiguration, PublishVersion, ListVersionsByFunction, CreateAlias,
  ListAliases, TagResource, ListTags, UntagResource, DeleteAlias, and
  DeleteFunction via the generated LambdaClient.

  Uses the aws.protocols#restJson1 protocol (JSON over HTTPS).
  Run against LocalStack: make demo
  """

  alias LambdaTypes.{
    AccountLimit,
    AccountUsage,
    AliasConfiguration,
    CreateAliasInput,
    CreateAliasOutput,
    CreateFunctionInput,
    DeleteAliasInput,
    DeleteFunctionInput,
    FunctionCode,
    FunctionConfiguration,
    GetAccountSettingsInput,
    GetAccountSettingsOutput,
    GetFunctionInput,
    GetFunctionOutput,
    InvokeInput,
    InvokeOutput,
    ListAliasesInput,
    ListFunctionsInput,
    ListTagsInput,
    ListTagsOutput,
    ListVersionsByFunctionInput,
    PublishVersionInput,
    PublishVersionOutput,
    ResourceConflictException,
    TagResourceInput,
    UntagResourceInput,
    UpdateFunctionConfigurationInput,
    UpdateFunctionConfigurationOutput
  }

  @function_name "lambda-demo-elixir-function"
  @alias_name "demo"
  @role_arn "arn:aws:iam::000000000000:role/lambda-demo-elixir-role"

  def run do
    IO.puts("\n=== Lambda Demo: Full Function Lifecycle ===\n")

    config = client_config()
    IO.puts("Lambda client configured for #{Map.fetch!(config, :base_url)}\n")

    setup_infrastructure(config)

    function_arn = "arn:aws:lambda:us-east-1:000000000000:function:#{@function_name}"

    get_account_settings(config)
    list_functions(config)
    get_function(config)
    invoke_function(config)
    update_function_configuration(config)
    published_version = publish_version(config)
    list_versions_by_function(config)
    create_alias(config, published_version)
    list_aliases(config)
    warmup_published_version(config, published_version)
    invoke_via_alias(config)
    tag_management(config, function_arn)
    cleanup(config)

    IO.puts("=== Lambda Demo Complete ===")
    :ok
  end

  defp client_config do
    %{
      region: "us-east-1",
      endpoint_prefix: "lambda",
      signing_name: "lambda",
      base_url: System.get_env("AWS_ENDPOINT"),
      credentials: %{
        access_key_id: "dummy",
        secret_access_key: "dummy"
      }
    }
  end

  defp setup_infrastructure(config) do
    IO.puts("--- Setup infrastructure ---")
    create_demo_function(config)
    IO.puts("Infrastructure ready: Function=#{@function_name}\n")
  end

  defp create_demo_function(config) do
    IO.puts("--- CreateFunction ---")

    zip_bytes = load_lambda_zip()

    input = %CreateFunctionInput{
      function_name: @function_name,
      role: @role_arn,
      handler: "lambda_function.handler",
      runtime: :python39,
      code: %FunctionCode{zip_file: Base.encode64(zip_bytes)},
      tags: %{
        "Name" => "lambda-demo-elixir-function",
        "Environment" => "demo",
        "Project" => "smithy-elixir"
      }
    }

    case LambdaClient.create_function(config, input) do
      {:ok, _} ->
        IO.puts("  SUCCESS: Function '#{@function_name}' created")

      {:error, %ResourceConflictException{}} ->
        IO.puts("  SUCCESS: Function '#{@function_name}' already exists")

      {:error, reason} ->
        raise("create_function_failed: #{inspect(reason)}")
    end

    IO.puts("")
    wait_demo_function_active(config)
  end

  defp wait_demo_function_active(config) do
    IO.puts("--- Wait FunctionActiveV2 ---")

    input = %GetFunctionInput{function_name: @function_name}

    case LambdaWaiters.wait_function_active_v2(config, input, []) do
      {:ok, _} ->
        IO.puts("  SUCCESS: Function '#{@function_name}' is Active\n")

      {:error, reason} ->
        raise("wait_function_active_failed: #{inspect(reason)}")
    end
  end

  defp load_lambda_zip do
    python = """
    def handler(event, context):
        return {
            'statusCode': 200,
            'body': 'Hello from Lambda!'
        }
    """

    files = [{~c"lambda_function.py", python}]

    case :zip.create(~c"function.zip", files, [:memory]) do
      {:ok, {_name, zip_bytes}} -> zip_bytes
      {:error, reason} -> raise("create_lambda_zip_failed: #{inspect(reason)}")
    end
  end

  defp get_account_settings(config) do
    IO.puts("--- 1. GetAccountSettings ---")

    case LambdaClient.get_account_settings(config, %GetAccountSettingsInput{}) do
      {:ok,
       %GetAccountSettingsOutput{
         account_usage: account_usage,
         account_limit: account_limit
       }} ->
        case account_usage do
          nil ->
            IO.puts("  No usage data")

          %AccountUsage{function_count: fn_count, total_code_size: code_size} ->
            IO.puts("  Functions deployed : #{fn_count}")
            IO.puts("  Total code size    : #{code_size} bytes")
        end

        case account_limit do
          nil ->
            :ok

          %AccountLimit{concurrent_executions: limit} ->
            IO.puts("  Concurrent limit   : #{limit}")
        end

      {:error, reason} ->
        raise("get_account_settings_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp list_functions(config) do
    IO.puts("--- 2. ListFunctions ---")

    case LambdaClient.list_functions(config, %ListFunctionsInput{}) do
      {:ok, functions} when is_list(functions) and functions != [] ->
        IO.puts("  SUCCESS: Found #{length(functions)} function(s)")

        fn_names =
          Enum.flat_map(functions, fn
            %FunctionConfiguration{function_name: name} when is_binary(name) -> [name]
            _ -> []
          end)

        unless @function_name in fn_names do
          raise("assertion failed: function #{inspect(@function_name)} not found")
        end

        IO.puts("  SUCCESS: Function '#{@function_name}' found")

        Enum.each(functions, fn
          %FunctionConfiguration{
            function_name: fn_name,
            runtime: fn_runtime,
            code_size: fn_size
          } ->
            IO.puts(
              "    #{format_string(fn_name)}  runtime=#{format_string(fn_runtime)}  size=#{fn_size} bytes"
            )

          _ ->
            :ok
        end)

      {:ok, _} ->
        raise("assertion failed: empty function list")

      {:error, reason} ->
        raise("list_functions_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp get_function(config) do
    IO.puts("--- 3. GetFunction ---")

    input = %GetFunctionInput{function_name: @function_name}

    case LambdaClient.get_function(config, input) do
      {:ok, %GetFunctionOutput{configuration: configuration}} when not is_nil(configuration) ->
        %FunctionConfiguration{
          state: state,
          runtime: runtime,
          handler: handler,
          memory_size: memory_size
        } = configuration

        if state != :active do
          raise("assertion failed: expected active function, got #{inspect(state)}")
        end

        IO.puts("  SUCCESS: Function is Active")
        IO.puts("  Runtime  : #{format_string(runtime)}")
        IO.puts("  Handler  : #{format_string(handler)}")
        IO.puts("  Memory   : #{memory_size} MB")
        IO.puts("  State    : #{state}")

      {:ok, _} ->
        raise("assertion failed: missing function configuration")

      {:error, reason} ->
        raise("get_function_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp invoke_function(config) do
    IO.puts("--- 4. Invoke ---")

    input = %InvokeInput{
      function_name: @function_name,
      payload: ~s({"hello": "from smithy-elixir"})
    }

    case LambdaClient.invoke(config, input) do
      {:ok, %InvokeOutput{status_code: 200, payload: payload}} when is_binary(payload) ->
        IO.puts("  SUCCESS: Invocation returned 200")
        IO.puts("  Status code : 200")
        IO.puts("  Payload     : #{payload}")

      {:ok, %InvokeOutput{status_code: status}} ->
        raise("assertion failed: expected status 200, got #{status}")

      {:ok, _} ->
        raise("assertion failed: invoke returned empty payload")

      {:error, reason} ->
        raise("invoke_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp update_function_configuration(config) do
    IO.puts("--- 5. UpdateFunctionConfiguration ---")

    input = %UpdateFunctionConfigurationInput{
      function_name: @function_name,
      description: "Updated by smithy-elixir demo"
    }

    case LambdaClient.update_function_configuration(config, input) do
      {:ok, %UpdateFunctionConfigurationOutput{description: description}} ->
        IO.puts("  SUCCESS: Description : #{format_string(description)}")

      {:ok, _} ->
        raise("assertion failed: update_function_configuration returned empty")

      {:error, reason} ->
        raise("update_function_configuration_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp publish_version(config) do
    IO.puts("--- 6. PublishVersion ---")

    input = %PublishVersionInput{
      function_name: @function_name,
      description: "v1 published by smithy-elixir demo"
    }

    case LambdaClient.publish_version(config, input) do
      {:ok, %PublishVersionOutput{version: version}}
      when is_binary(version) and byte_size(version) > 0 ->
        IO.puts("  SUCCESS: Published version : #{version}")
        IO.puts("")
        version

      {:ok, _} ->
        raise("assertion failed: publish_version returned empty")

      {:error, reason} ->
        raise("publish_version_failed: #{inspect(reason)}")
    end
  end

  defp list_versions_by_function(config) do
    IO.puts("--- 7. ListVersionsByFunction ---")

    input = %ListVersionsByFunctionInput{function_name: @function_name}

    case LambdaClient.list_versions_by_function(config, input) do
      {:ok, versions} when is_list(versions) ->
        IO.puts("  Found #{length(versions)} version(s):")

        if length(versions) < 2 do
          raise(
            "assertion failed: expected at least 2 versions, got #{length(versions)}"
          )
        end

        IO.puts("  SUCCESS: >= 2 versions (original + published)")

        Enum.each(versions, fn
          %FunctionConfiguration{version: v_num, description: v_desc} ->
            IO.puts("    #{format_string(v_num)}  #{format_string(v_desc)}")

          _ ->
            :ok
        end)

      {:ok, _} ->
        raise("assertion failed: empty version list")

      {:error, reason} ->
        raise("list_versions_by_function_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp create_alias(config, published_version) do
    IO.puts("--- 8. CreateAlias ---")

    input = %CreateAliasInput{
      function_name: @function_name,
      name: @alias_name,
      function_version: published_version,
      description: "Stable release alias"
    }

    case LambdaClient.create_alias(config, input) do
      {:ok, %CreateAliasOutput{name: alias_name, function_version: alias_version}} ->
        IO.puts(
          "  SUCCESS: Alias '#{format_string(alias_name)}' -> version #{format_string(alias_version)}"
        )

      {:error, reason} ->
        raise("create_alias_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp list_aliases(config) do
    IO.puts("--- 9. ListAliases ---")

    input = %ListAliasesInput{function_name: @function_name}

    case LambdaClient.list_aliases(config, input) do
      {:ok, aliases} when is_list(aliases) and aliases != [] ->
        IO.puts("  SUCCESS: Found #{length(aliases)} alias(es)")

        alias_names =
          Enum.flat_map(aliases, fn
            %AliasConfiguration{name: name} when is_binary(name) -> [name]
            _ -> []
          end)

        unless @alias_name in alias_names do
          raise("assertion failed: alias #{inspect(@alias_name)} not found")
        end

        IO.puts("  SUCCESS: Alias '#{@alias_name}' found")

        Enum.each(aliases, fn
          %AliasConfiguration{name: a_name, function_version: a_ver} ->
            IO.puts("    #{format_string(a_name)} -> #{format_string(a_ver)}")

          _ ->
            :ok
        end)

      {:ok, _} ->
        raise("assertion failed: empty alias list")

      {:error, reason} ->
        raise("list_aliases_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp warmup_published_version(config, published_version) do
    input = %InvokeInput{
      function_name: @function_name,
      qualifier: published_version,
      payload: ~s({"warmup": true})
    }

    case LambdaClient.invoke(config, input) do
      {:ok, _} -> :ok
      {:error, reason} -> raise("invoke_warmup_failed: #{inspect(reason)}")
    end

    Process.sleep(1000)
  end

  defp invoke_via_alias(config) do
    IO.puts("--- 10. Invoke (via alias '#{@alias_name}') ---")

    input = %InvokeInput{
      function_name: @function_name,
      qualifier: @alias_name,
      payload: ~s({"source": "alias invocation"})
    }

    case invoke_with_retry(config, input, 5) do
      {:ok, %InvokeOutput{status_code: 200, payload: payload}} ->
        IO.puts("  SUCCESS: Alias invocation returned 200")
        IO.puts("  Status code : 200")

        if payload do
          IO.puts("  Payload     : #{payload}")
        end

      {:ok, %InvokeOutput{status_code: status}} ->
        raise("assertion failed: expected status 200, got #{status}")

      {:error, reason} ->
        raise("invoke_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp tag_management(config, function_arn) do
    IO.puts("--- 11. TagResource / ListTags / UntagResource ---")

    tag_input = %TagResourceInput{
      resource: function_arn,
      tags: %{"AddedBy" => "smithy-elixir-demo"}
    }

    case LambdaClient.tag_resource(config, tag_input) do
      {:ok, _} -> IO.puts("  Tag added")
      {:error, reason} -> raise("tag_resource_failed: #{inspect(reason)}")
    end

    list_tags_input = %ListTagsInput{resource: function_arn}

    case LambdaClient.list_tags(config, list_tags_input) do
      {:ok, %ListTagsOutput{tags: all_tags}} when is_map(all_tags) ->
        IO.puts("  Current tags (#{map_size(all_tags)}): #{inspect(all_tags)}")

        unless Map.has_key?(all_tags, "AddedBy") do
          raise("assertion failed: tag AddedBy not present before untag")
        end

        IO.puts("  SUCCESS: Tag 'AddedBy' present before untag")

      {:error, reason} ->
        raise("list_tags_failed: #{inspect(reason)}")
    end

    untag_input = %UntagResourceInput{
      resource: function_arn,
      tag_keys: ["AddedBy"]
    }

    case LambdaClient.untag_resource(config, untag_input) do
      {:ok, _} -> IO.puts("  Tag removed")
      {:error, reason} -> raise("untag_resource_failed: #{inspect(reason)}")
    end

    case LambdaClient.list_tags(config, list_tags_input) do
      {:ok, %ListTagsOutput{tags: post_tags}} when is_map(post_tags) ->
        if Map.has_key?(post_tags, "AddedBy") do
          raise("assertion failed: tag AddedBy still present after untag")
        end

        IO.puts("  SUCCESS: Tag 'AddedBy' absent after untag")

      {:error, reason} ->
        raise("list_tags_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp cleanup(config) do
    IO.puts("--- 12. Cleanup ---")

    delete_alias_input = %DeleteAliasInput{
      function_name: @function_name,
      name: @alias_name
    }

    case LambdaClient.delete_alias(config, delete_alias_input) do
      {:ok, _} -> IO.puts("  Alias '#{@alias_name}' deleted")
      {:error, reason} -> raise("delete_alias_failed: #{inspect(reason)}")
    end

    delete_fn_input = %DeleteFunctionInput{function_name: @function_name}

    case LambdaClient.delete_function(config, delete_fn_input) do
      {:ok, _} -> IO.puts("  Function '#{@function_name}' deleted")
      {:error, reason} -> raise("delete_function_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp invoke_with_retry(_config, _input, 0) do
    {:error, :invoke_alias_retries_exhausted}
  end

  defp invoke_with_retry(config, input, attempts) when attempts > 0 do
    case LambdaClient.invoke(config, input) do
      {:ok, _} = ok ->
        ok

      {:error, {:service_exception, _, msg, _}} = err when is_binary(msg) and attempts > 1 ->
        if String.contains?(msg, "Timeout") do
          IO.puts("  Alias invoke timed out, retrying (#{attempts - 1} attempts left)...")
          Process.sleep(3000)
          invoke_with_retry(config, input, attempts - 1)
        else
          err
        end

      other ->
        other
    end
  end

  defp format_string(nil), do: "?"
  defp format_string(value) when is_binary(value), do: value
  defp format_string(value) when is_atom(value), do: Atom.to_string(value)
  defp format_string(value), do: inspect(value)
end
