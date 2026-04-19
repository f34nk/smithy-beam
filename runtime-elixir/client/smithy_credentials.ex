defmodule SmithyCredentials do
  @moduledoc """
  AWS credentials provider for Smithy-generated Elixir clients.

  Ported from `aws_credentials.erl`. Loads credentials from the standard
  AWS credential chain: environment variables, then `~/.aws/credentials`.
  """

  @doc """
  Load credentials from the default chain (env vars → credentials file).

  Returns `{:ok, credentials}` where `credentials` is a map with at least
  `:access_key_id` and `:secret_access_key`, plus an optional `:session_token`.
  """
  @spec get_credentials() :: {:ok, map()} | {:error, atom() | tuple()}
  def get_credentials, do: get_credentials(%{})

  @spec get_credentials(map()) :: {:ok, map()} | {:error, atom() | tuple()}
  def get_credentials(opts) when is_map(opts) do
    profile = Map.get(opts, :profile, "default")
    try_providers([
      &from_environment/0,
      fn -> from_credentials_file(profile) end,
      &from_ec2_metadata/0
    ])
  end

  @doc "Load credentials from environment variables."
  @spec from_environment() :: {:ok, map()} | {:error, atom()}
  def from_environment do
    case {System.get_env("AWS_ACCESS_KEY_ID"), System.get_env("AWS_SECRET_ACCESS_KEY")} do
      {nil, _} ->
        {:error, :no_access_key}

      {_, nil} ->
        {:error, :no_secret_key}

      {access_key, secret_key} ->
        creds = %{access_key_id: access_key, secret_access_key: secret_key}

        case System.get_env("AWS_SESSION_TOKEN") do
          nil   -> {:ok, creds}
          token -> {:ok, Map.put(creds, :session_token, token)}
        end
    end
  end

  @doc "Load credentials from `~/.aws/credentials` using the default profile."
  @spec from_credentials_file() :: {:ok, map()} | {:error, atom() | tuple()}
  def from_credentials_file, do: from_credentials_file("default")

  @doc """
  Load credentials from the EC2 Instance Metadata Service (IMDSv2).

  Uses the token-based IMDSv2 protocol.  On non-EC2 hosts the IMDS endpoint
  is unreachable; a short connect timeout (300 ms) ensures the provider
  fails fast without blocking the credential chain.
  """
  @spec from_ec2_metadata() :: {:ok, map()} | {:error, atom() | tuple()}
  def from_ec2_metadata do
    base_url = "http://169.254.169.254"
    http_opts = [receive_timeout: 1000, connect_options: [timeout: 300]]

    token =
      case get_imds_token(base_url, http_opts) do
        {:ok, t}    -> t
        {:error, _} -> nil
      end

    fetch_role_credentials(base_url, token, http_opts)
  end

  @doc "Load credentials from `~/.aws/credentials` for the given profile."
  @spec from_credentials_file(String.t()) :: {:ok, map()} | {:error, atom() | tuple()}
  def from_credentials_file(profile) do
    case System.get_env("HOME") do
      nil ->
        {:error, :home_not_set}

      home ->
        cred_file = Path.join([home, ".aws", "credentials"])

        case File.read(cred_file) do
          {:ok, content}    -> parse_credentials_file(content, profile)
          {:error, :enoent} -> {:error, :credentials_file_not_found}
          {:error, reason}  -> {:error, {:credentials_file_error, reason}}
        end
    end
  end

  # ---------------------------------------------------------------------------
  # Private helpers
  # ---------------------------------------------------------------------------

  defp get_imds_token(base_url, http_opts) do
    token_url = base_url <> "/latest/api/token"
    headers   = [{"x-aws-ec2-metadata-token-ttl-seconds", "21600"}]

    case :httpc.request(:put, {to_charlist(token_url), to_charlist_headers(headers),
                                ~c"text/plain", ""}, http_opts_to_httpc(http_opts), [{:body_format, :binary}]) do
      {:ok, {{_, 200, _}, _resp_hdrs, body}} ->
        {:ok, String.trim(to_string(body))}

      {:ok, {{_, status, _}, _, _}} ->
        {:error, {:imds_token_error, status}}

      {:error, reason} ->
        {:error, {:imds_connect_failed, reason}}
    end
  end

  defp fetch_role_credentials(base_url, token, http_opts) do
    role_url   = base_url <> "/latest/meta-data/iam/security-credentials/"
    token_hdrs = token_headers(token)

    case :httpc.request(:get, {to_charlist(role_url), to_charlist_headers(token_hdrs)},
                        http_opts_to_httpc(http_opts), [{:body_format, :binary}]) do
      {:ok, {{_, 200, _}, _, body}} ->
        role_name = String.trim(to_string(body))
        fetch_credentials_for_role(base_url, role_name, token, http_opts)

      {:ok, {{_, 404, _}, _, _}} ->
        {:error, :ec2_no_iam_role}

      {:ok, {{_, status, _}, _, _}} ->
        {:error, {:ec2_metadata_error, status}}

      {:error, reason} ->
        {:error, {:ec2_metadata_connect_failed, reason}}
    end
  end

  defp fetch_credentials_for_role(base_url, role_name, token, http_opts) do
    creds_url  = base_url <> "/latest/meta-data/iam/security-credentials/" <> role_name
    token_hdrs = token_headers(token)

    case :httpc.request(:get, {to_charlist(creds_url), to_charlist_headers(token_hdrs)},
                        http_opts_to_httpc(http_opts), [{:body_format, :binary}]) do
      {:ok, {{_, 200, _}, _, body}} ->
        parse_imds_credentials(body)

      {:ok, {{_, status, _}, _, _}} ->
        {:error, {:ec2_credentials_error, status}}

      {:error, reason} ->
        {:error, {:ec2_credentials_connect_failed, reason}}
    end
  end

  defp parse_imds_credentials(body) do
    case Jason.decode(body) do
      {:ok, map} ->
        case {Map.get(map, "AccessKeyId"), Map.get(map, "SecretAccessKey")} do
          {nil, _} -> {:error, :ec2_missing_access_key}
          {_, nil} -> {:error, :ec2_missing_secret_key}
          {ak, sk} ->
            creds = %{access_key_id: ak, secret_access_key: sk}
            creds = if token = Map.get(map, "Token"), do: Map.put(creds, :session_token, token), else: creds
            {:ok, creds}
        end

      {:error, _} ->
        {:error, :ec2_credentials_parse_error}
    end
  end

  defp token_headers(nil),   do: []
  defp token_headers(token), do: [{"x-aws-ec2-metadata-token", token}]

  defp to_charlist_headers(headers) do
    Enum.map(headers, fn {k, v} -> {to_charlist(k), to_charlist(v)} end)
  end

  defp http_opts_to_httpc(opts) do
    timeout     = Keyword.get(opts, :receive_timeout, 1000)
    conn_timeout =
      opts
      |> Keyword.get(:connect_options, [])
      |> Keyword.get(:timeout, 300)

    [{:timeout, timeout}, {:connect_timeout, conn_timeout}]
  end

  defp try_providers([]), do: {:error, :no_credentials}

  defp try_providers([provider | rest]) do
    case provider.() do
      {:ok, _} = ok -> ok
      {:error, _}   -> try_providers(rest)
    end
  end

  defp parse_credentials_file(content, profile) do
    lines = String.split(content, "\n", trim: true)
    section_header = "[#{profile}]"

    case find_profile_section(lines, section_header) do
      {:ok, section_lines} -> extract_credentials(section_lines)
      :not_found            -> {:error, {:profile_not_found, profile}}
    end
  end

  defp find_profile_section([], _header), do: :not_found

  defp find_profile_section([line | rest], header) do
    if String.trim(line) == header do
      section = Enum.take_while(rest, fn l -> not String.starts_with?(String.trim(l), "[") end)
      {:ok, section}
    else
      find_profile_section(rest, header)
    end
  end

  defp extract_credentials(lines) do
    pairs =
      lines
      |> Enum.filter(&String.contains?(&1, "="))
      |> Enum.map(fn line ->
        [key | rest] = String.split(line, "=", parts: 2)
        {String.trim(key), String.trim(Enum.join(rest, "="))}
      end)
      |> Map.new()

    case {Map.get(pairs, "aws_access_key_id"), Map.get(pairs, "aws_secret_access_key")} do
      {nil, _} ->
        {:error, :no_access_key_in_profile}

      {_, nil} ->
        {:error, :no_secret_key_in_profile}

      {access_key, secret_key} ->
        creds = %{access_key_id: access_key, secret_access_key: secret_key}

        case Map.get(pairs, "aws_session_token") do
          nil   -> {:ok, creds}
          token -> {:ok, Map.put(creds, :session_token, token)}
        end
    end
  end
end
