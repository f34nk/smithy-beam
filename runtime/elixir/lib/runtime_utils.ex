defmodule RuntimeUtils do
  @moduledoc false

  @spec split_base_url(String.t()) :: {String.t(), String.t()}
  def split_base_url(""), do: {"", ""}

  def split_base_url(base_url) do
    case URI.parse(base_url) do
      %URI{scheme: scheme, host: host} = uri when is_binary(host) ->
        port_suffix =
          case {uri.scheme, uri.port} do
            {"https", 443} -> ""
            {"http", 80} -> ""
            {_, nil} -> ""
            {_, port} -> ":#{port}"
          end

        {scheme <> "://", host <> port_suffix}

      _ ->
        {"", base_url}
    end
  end

  @spec endpoint_host_from_config(map()) :: String.t() | nil
  def endpoint_host_from_config(config) do
    case Map.get(config, :base_url) do
      nil ->
        case {Map.get(config, :endpoint_prefix), Map.get(config, :region, "us-east-1")} do
          {nil, _} -> nil
          {prefix, region} -> "#{prefix}.#{region}.amazonaws.com"
        end

      base_url ->
        {_scheme, authority} = split_base_url(base_url)
        authority
    end
  end
end
