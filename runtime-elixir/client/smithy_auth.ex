defmodule SmithyAuth do
  @moduledoc """
  AWS Signature Version 4 (SigV4) signing for Smithy-generated Elixir clients.

  Ported from `aws_sigv4.erl`. Uses `:crypto` for HMAC-SHA256 operations.

  ## Reference

  https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html
  """

  @algorithm "AWS4-HMAC-SHA256"

  @doc """
  Sign an HTTP request by adding the `Authorization` and `X-Amz-Date`
  (and optionally `X-Amz-Security-Token`) headers.

  ## Parameters

    * `request` — map with keys `:method`, `:url`, `:headers` (list of
      `{name, value}` tuples), and `:body` (binary).
    * `opts` — keyword list; must contain `:config` with at least
      `:access_key_id`, `:secret_access_key`, and `:region`. The `:service`
      key is derived from the URL when absent. `:session_token` is optional.

  ## Return values

    * `{:ok, headers}` — the original headers list with AWS auth headers prepended.
    * `{:error, reason}` on failure.
  """
  @spec sign_request(map(), keyword()) :: {:ok, [{String.t(), String.t()}]} | {:error, term()}
  def sign_request(%{method: method, url: url, headers: headers, body: body}, opts) do
    config = Keyword.fetch!(opts, :config)

    access_key_id = Map.fetch!(config, :access_key_id)
    secret_access_key = Map.fetch!(config, :secret_access_key)
    region = Map.fetch!(config, :region)
    service = Map.get_lazy(config, :service, fn -> derive_service_from_url(url) end)
    session_token = Map.get(config, :session_token)

    datetime = iso8601_datetime()
    date = binary_part(datetime, 0, 8)

    headers_with_date = [{"x-amz-date", datetime} | headers]

    headers_with_token =
      case session_token do
        nil -> headers_with_date
        token -> [{"x-amz-security-token", token} | headers_with_date]
      end

    canonical_request = create_canonical_request(method, url, headers_with_token, body)
    credential_scope = credential_scope(date, region, service)
    string_to_sign = create_string_to_sign(datetime, credential_scope, canonical_request)
    signing_key = derive_signing_key(secret_access_key, date, region, service)
    signature = calculate_signature(signing_key, string_to_sign)
    signed_headers = signed_header_list(headers_with_token)

    auth_header =
      format_auth_header(access_key_id, credential_scope, signed_headers, signature)

    {:ok, [{"authorization", auth_header} | headers_with_token]}
  end

  # ---------------------------------------------------------------------------
  # Canonical request
  # ---------------------------------------------------------------------------

  @doc false
  def create_canonical_request(method, uri, headers, body) do
    parsed = URI.parse(uri)
    canonical_uri = if parsed.path in [nil, ""], do: "/", else: parsed.path
    canonical_query = canonicalize_query_string(parsed.query)
    canonical_headers = canonicalize_headers(headers)
    signed_headers = signed_header_list(headers)
    hashed_payload = hash_sha256(body)

    Enum.join(
      [method, canonical_uri, canonical_query, canonical_headers, signed_headers, hashed_payload],
      "\n"
    )
  end

  @doc false
  def canonicalize_query_string(nil), do: ""
  def canonicalize_query_string(""), do: ""

  def canonicalize_query_string(query) do
    query
    |> URI.decode_query()
    |> Enum.sort_by(fn {k, _} -> k end)
    |> Enum.map(fn {k, v} -> URI.encode(k, &URI.char_unreserved?/1) <> "=" <> URI.encode(v, &URI.char_unreserved?/1) end)
    |> Enum.join("&")
  end

  @doc false
  def canonicalize_headers(headers) do
    headers
    |> Enum.map(fn {name, value} -> {String.downcase(name), String.trim(value)} end)
    |> Enum.sort_by(fn {name, _} -> name end)
    |> Enum.map_join("", fn {name, value} -> "#{name}:#{value}\n" end)
  end

  @doc false
  def signed_header_list(headers) do
    headers
    |> Enum.map(fn {name, _} -> String.downcase(name) end)
    |> Enum.uniq()
    |> Enum.sort()
    |> Enum.join(";")
  end

  @doc false
  def hash_sha256(data) do
    :crypto.hash(:sha256, data)
    |> Base.encode16(case: :lower)
  end

  # ---------------------------------------------------------------------------
  # String to sign
  # ---------------------------------------------------------------------------

  @doc false
  def create_string_to_sign(datetime, credential_scope, canonical_request) do
    hashed = hash_sha256(canonical_request)
    Enum.join([@algorithm, datetime, credential_scope, hashed], "\n")
  end

  @doc false
  def credential_scope(date, region, service) do
    "#{date}/#{region}/#{service}/aws4_request"
  end

  @doc false
  def iso8601_datetime do
    {{year, month, day}, {hour, minute, second}} = :calendar.universal_time()

    :io_lib.format("~4..0B~2..0B~2..0BT~2..0B~2..0B~2..0BZ", [
      year,
      month,
      day,
      hour,
      minute,
      second
    ])
    |> IO.iodata_to_binary()
  end

  # ---------------------------------------------------------------------------
  # Signature calculation
  # ---------------------------------------------------------------------------

  @doc false
  def derive_signing_key(secret_access_key, date, region, service) do
    ("AWS4" <> secret_access_key)
    |> hmac_sha256(date)
    |> hmac_sha256(region)
    |> hmac_sha256(service)
    |> hmac_sha256("aws4_request")
  end

  @doc false
  def calculate_signature(signing_key, string_to_sign) do
    hmac_sha256(signing_key, string_to_sign)
    |> Base.encode16(case: :lower)
  end

  @doc false
  def hmac_sha256(key, data) do
    :crypto.mac(:hmac, :sha256, key, data)
  end

  # ---------------------------------------------------------------------------
  # Authorization header
  # ---------------------------------------------------------------------------

  @doc false
  def format_auth_header(access_key_id, credential_scope, signed_headers, signature) do
    "#{@algorithm} Credential=#{access_key_id}/#{credential_scope}, " <>
      "SignedHeaders=#{signed_headers}, Signature=#{signature}"
  end

  # ---------------------------------------------------------------------------
  # URL helpers
  # ---------------------------------------------------------------------------

  defp derive_service_from_url(url) do
    host = url |> URI.parse() |> Map.get(:host, "")
    extract_service_from_host(host)
  end

  defp extract_service_from_host(host) do
    cond do
      String.starts_with?(host, "s3.") -> "s3"
      String.starts_with?(host, "s3-") -> "s3"
      String.contains?(host, ".s3.") -> "s3"
      true ->
        case String.split(host, ".") do
          [service, "amazonaws", "com"] -> service
          [service, _region, "amazonaws", "com"] -> service
          [_bucket, service, _region, "amazonaws", "com"] -> service
          _ -> "s3"
        end
    end
  end
end
