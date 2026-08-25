defmodule SyntaxFixtureTransform do
  @moduledoc "Transform coverage for Elixir IR rendering."

  alias SyntaxTypes.Request, as: Request
  alias SyntaxTypes.Response, as: Response

  @type config :: %{binary() => term()}

  @spec transform(config(), Request.t()) :: Response.t()
  def transform(config, %Request{path: path, query: query, headers: headers}) do
    base_url =
      case Map.get(config, :base_url) do
        nil ->
          coalesce([
            Map.get(config, :endpoint),
            resolve_host(config)
          ])

        given_url ->
          given_url
      end

    query_str =
      case Map.to_list(query) do
        [] ->
          ""

        pairs ->
          encoded = URI.encode_query(pairs)
          "?" <> encoded
      end

    {scheme, authority} = split_base_url(base_url)
    req_url = scheme <> authority <> path <> query_str

    %Response{
      status: 200,
      headers: headers,
      body: req_url
    }
  end

  defp coalesce([h | rest]) do
    case h do
      nil -> coalesce(rest)
      "" -> coalesce(rest)
      value -> value
    end
  end

  defp coalesce([]), do: nil

  defp resolve_host(config), do: Map.get(config, :host, "localhost")

  defp split_base_url(""), do: {"", ""}

  defp split_base_url(base_url) do
    case URI.parse(base_url) do
      %URI{scheme: scheme, host: host} = uri when is_binary(host) ->
        port_suffix =
          case uri.port do
            nil -> ""
            port -> ":#{port}"
          end

        {scheme <> "://", host <> port_suffix}

      _ ->
        {"", base_url}
    end
  end
end
