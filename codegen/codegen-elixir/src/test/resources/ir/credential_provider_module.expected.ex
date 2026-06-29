defmodule Sigv4testServiceCredentials do
  @moduledoc "false"

  @spec resolve(client_config()) :: {:ok, aws_credentials()} | {:error, term()}
  def resolve(config) do
    case Map.get(config, :credentials) do
      nil -> resolve_chain(config)

      creds -> {:ok, creds}
    end
  end

  @spec resolve_chain(client_config()) :: {:ok, aws_credentials()} | {:error, term()}
  defp resolve_chain(config) do
    resolve_chain(config, [:env, :profile, :ecs, :ec2])
  end

  defp resolve_chain(_config, []), do: {:error, :not_found}

  defp resolve_chain(
    config,
    [provider | rest]
  ) do
    case resolve_provider(provider, config) do
      {:ok, creds} -> {:ok, creds}

      _ -> resolve_chain(config, rest)
    end
  end

  defp resolve_provider(:env, config), do: resolve_from_env(config)
  defp resolve_provider(:profile, config), do: resolve_from_profile(config)
  defp resolve_provider(:ecs, config), do: resolve_from_ecs(config)
  defp resolve_provider(:ec2, config), do: resolve_from_ec2(config)

  @spec resolve_from_env(client_config()) :: {:ok, aws_credentials()} | {:error, term()}
  defp resolve_from_env(_config) do
    case {System.get_env("AWS_ACCESS_KEY_ID"), System.get_env("AWS_SECRET_ACCESS_KEY")} do
      {id, secret} when is_binary(id) and is_binary(secret) ->
        token = System.get_env("AWS_SESSION_TOKEN")
        {:ok, %{
          access_key_id: id,
          secret_access_key: secret,
          session_token: env_session_token(token)
        }}
    
      _ ->
        {:error, :not_found}
    end
  end

  @spec resolve_from_profile(client_config()) :: {:ok, aws_credentials()} | {:error, term()}
  defp resolve_from_profile(config) do
    profile = profile_name(config)
    path = profile_credentials_path(config)
    case File.read(path) do
      {:ok, contents} -> parse_profile_credentials(contents, profile)
      {:error, _} -> {:error, :not_found}
    end
  end

  defp profile_name(config) do
    case Map.get(config, :profile) do
      nil -> System.get_env("AWS_PROFILE") || "default"
      name -> name
    end
  end

  defp profile_credentials_path(config) do
    case Map.get(config, :credentials_path) do
      nil ->
        System.get_env("AWS_SHARED_CREDENTIALS_FILE") ||
          Path.join(home_directory(), ".aws/credentials")
    
      path -> path
    end
  end

  defp parse_profile_credentials(
    contents,
    profile
  ) do
    contents
    |> String.split("\n")
    |> find_profile_section(profile, %{})
  end

  defp find_profile_section([], _profile, acc), do: map_to_credentials(acc)
  defp find_profile_section(
    [line | rest],
    profile,
    acc
  ) do
    trimmed = String.trim(line)
    cond do
      trimmed == "[" <> profile <> "]" ->
        read_profile_entries(rest, %{})
    
      map_size(acc) > 0 ->
        map_to_credentials(acc)
    
      true ->
        find_profile_section(rest, profile, acc)
    end
  end

  defp read_profile_entries([], acc), do: map_to_credentials(acc)
  defp read_profile_entries(
    [line | rest],
    acc
  ) do
    trimmed = String.trim(line)
    cond do
      String.starts_with?(trimmed, "[") -> map_to_credentials(acc)
      trimmed == "" -> read_profile_entries(rest, acc)
      true ->
        case String.split(trimmed, "=", parts: 2) do
          [key, value] -> read_profile_entries(rest, Map.put(acc, key, value))
          _ -> read_profile_entries(rest, acc)
        end
    end
  end

  defp map_to_credentials(fields) do
    case fields do
      %{"aws_access_key_id" => id, "aws_secret_access_key" => secret} = fields ->
        {:ok, %{
          access_key_id: String.trim(id),
          secret_access_key: String.trim(secret),
          session_token: optional_credential(Map.get(fields, "aws_session_token"))
        }}
    
      _ ->
        {:error, :not_found}
    end
  end

  defp optional_credential(nil), do: nil
  defp optional_credential(value), do: String.trim(value)

  @spec resolve_from_ecs(client_config()) :: {:ok, aws_credentials()} | {:error, term()}
  defp resolve_from_ecs(_config) do
    case System.get_env("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI") do
      nil ->
        case System.get_env("AWS_CONTAINER_CREDENTIALS_FULL_URI") do
          nil -> {:error, :not_found}
          uri -> fetch_json_credentials(uri)
        end
    
      rel ->
        fetch_json_credentials("http://169.254.170.2" <> rel)
    end
  end

  @spec resolve_from_ec2(client_config()) :: {:ok, aws_credentials()} | {:error, term()}
  defp resolve_from_ec2(_config) do
    with {:ok, role_bin} <- ec2_metadata_request("/latest/meta-data/iam/security-credentials/"),
         role = String.trim(role_bin),
         path = "/latest/meta-data/iam/security-credentials/" <> role,
         {:ok, json_bin} <- ec2_metadata_request(path) do
      decode_json_credentials(json_bin)
    else
      {:error, reason} -> {:error, reason}
    end
  end

  defp fetch_json_credentials(url) do
    case http_get(url, []) do
      {:ok, body} -> decode_json_credentials(body)

      {:error, reason} -> {:error, reason}
    end
  end

  defp decode_json_credentials(body) do
    case Jason.decode(body) do
      {:ok, %{"AccessKeyId" => id, "SecretAccessKey" => secret} = doc} ->
        {:ok, %{
          access_key_id: id,
          secret_access_key: secret,
          session_token: Map.get(doc, "Token")
        }}
    
      _ ->
        {:error, :invalid_credentials}
    end
  end

  defp ec2_metadata_request(path) do
    token_headers =
      case :httpc.request(
             :put,
             {~c"http://169.254.169.254/latest/api/token",
              [{~c"X-aws-ec2-metadata-token-ttl-seconds", ~c"60"}]},
             [],
             ~c""
           ) do
        {:ok, {{_, 200, _}, resp_headers, _}} ->
          case :proplists.get_value(~c"x-aws-ec2-metadata-token", resp_headers) do
            :undefined -> []
    
            token -> [{~c"X-aws-ec2-metadata-token", token}]
          end
    
        _ ->
          []
      end
    
    url = "http://169.254.169.254" <> path
    http_get(url, token_headers)
  end

  defp http_get(
    url,
    extra_headers
  ) do
    case :httpc.request(:get, {url, extra_headers}, [], [{:body_format, :binary}]) do
      {:ok, {{_, 200, _}, _resp_headers, body}} -> {:ok, body}
    
      {:ok, {{_, _, _}, _, _}} -> {:error, :http_error}
    
      {:error, reason} -> {:error, reason}
    end
  end

  defp env_session_token(nil), do: nil
  defp env_session_token(token), do: token

  defp home_directory do
    System.get_env("HOME") || System.get_env("USERPROFILE") || ""
  end
  @type client_config :: map()

  @type aws_credentials :: %{
          required(:access_key_id) => String.t(),
          required(:secret_access_key) => String.t(),
          optional(:session_token) => String.t() | nil
  }
end