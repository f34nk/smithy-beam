defmodule Weather.Client do
  @moduledoc "Generated Smithy client for Weather"

  alias Weather.Client.Types.GetWeatherInput
  alias Weather.Client.Types.GetWeatherOutput
  alias Weather.Client.Types.ListCitiesInput
  alias Weather.Client.Types.ListCitiesOutput

  @spec new(map()) :: {:ok, map()}
  def new(config), do: {:ok, config}

  @doc "Calls the GetWeather operation"
  @spec get_weather(map(), GetWeatherInput.t(), map()) ::
          {:ok, GetWeatherOutput.t()} | {:error, term()}
  def get_weather(client, %GetWeatherInput{} = input, opts \\ %{}) do
    SmithyClient.request(client, get_weather_op(input), opts)
  end

  defp get_weather_op(%GetWeatherInput{city: city} = input) do
    uri = "/weather/#{URI.encode_www_form(city || "")}"
    %SmithyClient.Operation{
      name: :get_weather,
      action: "GetWeather",
      http: %{method: "GET", uri: uri},
      input: input,
      output_shape: GetWeatherOutput,
      auth: :sigv4,
      static_headers: [],
      content_type: "application/json",
      encoding: :none,
      decoding: :json,
      parse_error_fn: &parse_error/2
    }
  end

  @doc "Calls the ListCities operation"
  @spec list_cities(map(), ListCitiesInput.t(), map()) ::
          {:ok, ListCitiesOutput.t()} | {:error, term()}
  def list_cities(client, %ListCitiesInput{} = input, opts \\ %{}) do
    SmithyClient.request(client, list_cities_op(input), opts)
  end

  defp list_cities_op(%ListCitiesInput{} = input) do
    %SmithyClient.Operation{
      name: :list_cities,
      action: "ListCities",
      http: %{method: "GET", uri: "/cities"},
      input: input,
      output_shape: ListCitiesOutput,
      auth: :sigv4,
      static_headers: [],
      content_type: "application/json",
      encoding: :none,
      decoding: :json,
      parse_error_fn: &parse_error/2
    }
  end

  @spec list_cities_stream(map(), ListCitiesInput.t(), map()) :: Enumerable.t()
  def list_cities_stream(client, %ListCitiesInput{} = input, opts \\ %{}) do
    SmithyClient.stream(client, list_cities_op(input), opts)
  end

  @spec validate_get_weather_input(GetWeatherInput.t()) ::
          :ok | {:error, {:missing_required_fields, [atom()]}}
  def validate_get_weather_input(%GetWeatherInput{} = input) do
    required = [:city]
    missing = Enum.reject(required, &(not is_nil(Map.get(input, &1))))
    case missing do
      [] -> :ok
      _ -> {:error, {:missing_required_fields, missing}}
    end
  end

  defp parse_error(status_code, body),
    do: {:error, {:http_error, status_code, body}}
end
