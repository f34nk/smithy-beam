defmodule WeatherClient do
  @moduledoc "Generated Smithy client for weather_client"

  @type get_weather_input :: %{city: String.t()}
  @type get_weather_output :: %{temperature: float() | nil}
  @type list_cities_input :: %{filter: String.t() | nil, custom_header: String.t() | nil, next_token: String.t() | nil, max_results: integer() | nil}
  @type list_cities_output :: %{next_token: String.t() | nil, cities: [String.t() | nil] | nil}

  @spec new(map()) :: {:ok, map()}
  def new(config), do: {:ok, config}

  @doc "Calls the GetWeather operation"
  @spec get_weather(map(), map(), map()) :: {:ok, map()} | {:error, term()}
  def get_weather(client, input, opts \\ %{}) do
    SmithyClient.request(client, get_weather_op(input), opts)
  end

  defp get_weather_op(input) do
    uri = "/weather/#{URI.encode_www_form((Map.get(input, :city) || Map.get(input, "city") || ""))}"
    %SmithyClient.Operation{
      name: :get_weather,
      action: "GetWeather",
      http: %{method: "GET", uri: uri},
      input: input,
      output_shape: :get_weather_output,
      auth: :sigv4,
      static_headers: [],
      content_type: "application/json",
      encoding: :none,
      decoding: :json,
      parse_error_fn: &parse_error/2
    }
  end

  @doc "Calls the ListCities operation"
  @spec list_cities(map(), map(), map()) :: {:ok, map()} | {:error, term()}
  def list_cities(client, input, opts \\ %{}) do
    SmithyClient.request(client, list_cities_op(input), opts)
  end

  defp list_cities_op(input) do
    %SmithyClient.Operation{
      name: :list_cities,
      action: "ListCities",
      http: %{method: "GET", uri: "/cities"},
      input: input,
      output_shape: :list_cities_output,
      auth: :sigv4,
      static_headers: [],
      content_type: "application/json",
      encoding: :none,
      decoding: :json,
      parse_error_fn: &parse_error/2
    }
  end

  @spec list_cities_stream(map(), map(), map()) :: Enumerable.t()
  def list_cities_stream(client, input, opts \\ %{}) do
    SmithyClient.stream(client, list_cities_op(input), opts)
  end
  def validate_get_weather_input(input) do
    required = [:city]
    missing = Enum.reject(required, &Map.has_key?(input, &1))
    case missing do
      [] -> :ok
      _ -> {:error, {:missing_required_fields, missing}}
    end
  end

  defp parse_error(status_code, body),
    do: {:error, {:http_error, status_code, body}}
end
