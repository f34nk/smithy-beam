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
      fn -> from_credentials_file(profile) end
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
