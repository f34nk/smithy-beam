defmodule SmithyS3 do
  @moduledoc """
  S3-specific URL building and header helpers for Smithy-generated Elixir clients.

  Ported from `aws_s3.erl`. Handles bucket-in-hostname (virtual-hosted style)
  routing and path-style fallback for localhost/LocalStack/MinIO.
  """

  @doc """
  Build an S3 URL using virtual-hosted or path-style routing.

  Auto-detects path style when the endpoint is localhost or contains a port.
  """
  @spec build_url(map(), String.t(), String.t(), String.t()) :: String.t()
  def build_url(client, bucket, key, query_string),
    do: build_url(client, bucket, key, query_string, %{})

  @spec build_url(map(), String.t(), String.t(), String.t(), map()) :: String.t()
  def build_url(client, bucket, key, query_string, opts) do
    endpoint = Map.get(client, :endpoint, "https://s3.amazonaws.com")

    use_path =
      case Map.get(opts, :path_style, :auto) do
        true   -> true
        false  -> false
        :auto  -> use_path_style?(endpoint)
      end

    if use_path do
      build_path_style_url(endpoint, bucket, key, query_string)
    else
      region = Map.get(client, :region, "us-east-1")
      build_virtual_hosted_url(endpoint, region, bucket, key, query_string)
    end
  end

  @doc """
  Calculate the `Content-MD5` header value for S3 PUT/POST operations.

  Returns the base64-encoded MD5 digest of `body`.
  """
  @spec calculate_content_md5(binary()) :: String.t()
  def calculate_content_md5(body) when is_binary(body) do
    :crypto.hash(:md5, body) |> Base.encode64()
  end

  @doc "Return true if the endpoint is localhost or an IP address."
  @spec localhost?(String.t()) :: boolean()
  def localhost?(endpoint) do
    {_scheme, host} = parse_endpoint(endpoint)
    host_only = host |> String.split(":") |> List.first()
    localhost_host?(host_only)
  end

  @doc "Return true if path-style URLs should be used for the given endpoint."
  @spec use_path_style?(String.t()) :: boolean()
  def use_path_style?(endpoint) do
    {_scheme, host} = parse_endpoint(endpoint)
    has_port = String.contains?(host, ":")
    has_port or localhost?(endpoint)
  end

  # ---------------------------------------------------------------------------
  # Private helpers
  # ---------------------------------------------------------------------------

  defp build_path_style_url(endpoint, bucket, key, query_string) do
    base = String.trim_trailing(endpoint, "/")

    path =
      cond do
        bucket == "" and key == "" -> "/"
        bucket == ""               -> "/#{key}"
        key == ""                  -> "/#{bucket}"
        true                       -> "/#{bucket}/#{key}"
      end

    "#{base}#{path}#{query_string}"
  end

  defp build_virtual_hosted_url(endpoint, region, bucket, key, query_string) do
    {scheme, host} = parse_endpoint(endpoint)

    virtual_host =
      cond do
        bucket == "" ->
          host

        host == "s3.amazonaws.com" ->
          "#{bucket}.s3.#{region}.amazonaws.com"

        String.starts_with?(host, "s3.") ->
          "#{bucket}.#{host}"

        true ->
          "#{bucket}.#{host}"
      end

    path = if key == "", do: "/", else: "/#{key}"
    "#{scheme}://#{virtual_host}#{path}#{query_string}"
  end

  defp parse_endpoint("https://" <> rest), do: {"https", strip_path(rest)}
  defp parse_endpoint("http://" <> rest),  do: {"http",  strip_path(rest)}
  defp parse_endpoint(other),              do: {"https", strip_path(other)}

  defp strip_path(host_with_path) do
    host_with_path |> String.split("/") |> List.first()
  end

  defp localhost_host?("localhost"),  do: true
  defp localhost_host?("127.0.0.1"), do: true
  defp localhost_host?("0.0.0.0"),   do: true
  defp localhost_host?("::1"),       do: true
  defp localhost_host?("moto"),      do: true
  defp localhost_host?("localstack"),do: true
  defp localhost_host?("minio"),     do: true
  defp localhost_host?(host),        do: ip_address?(host)

  defp ip_address?(host) do
    case :inet.parse_address(String.to_charlist(host)) do
      {:ok, _} -> true
      _        -> false
    end
  end
end
