defmodule WeatherServer.Dispatcher do
  @moduledoc "Generated Smithy server dispatcher for WeatherServer. Do not edit."

  use Plug.Router
  plug :match
  plug :dispatch

  @type get_weather_input :: %{city: String.t()}
  @type get_weather_output :: %{temperature: float() | nil}
  @type list_cities_input :: %{filter: String.t() | nil, custom_header: String.t() | nil, next_token: String.t() | nil, max_results: integer() | nil}
  @type list_cities_output :: %{next_token: String.t() | nil, cities: [String.t() | nil] | nil}

  get "/weather/:city" do
    input = deserialize_get_weather(conn)
    with :ok <- SmithyValidator.validate(input, [:city]),
         {:ok, output} <- handler().get_weather(input, %{}) do
      SmithyServer.response(conn, 200, serialize_get_weather(output))
    else
      {:error, {:missing_required_fields, _} = reason} ->
        SmithyServer.validation_error(conn, reason)
      {:error, err} ->
        SmithyServer.error_response(conn, err)
    end
  end

  get "/cities" do
    input = deserialize_list_cities(conn)
    with :ok <- SmithyValidator.validate(input, []),
         {:ok, output} <- handler().list_cities(input, %{}) do
      SmithyServer.response(conn, 200, serialize_list_cities(output))
    else
      {:error, {:missing_required_fields, _} = reason} ->
        SmithyServer.validation_error(conn, reason)
      {:error, err} ->
        SmithyServer.error_response(conn, err)
    end
  end

  match _ do
    SmithyServer.not_found(conn)
  end

  defp deserialize_get_weather(conn) do
    %{:city => conn.path_params["city"]}
  end

  defp serialize_get_weather(output), do: Jason.encode!(output)

  defp deserialize_list_cities(conn) do
    %{}
  end

  defp serialize_list_cities(output), do: Jason.encode!(output)

  defp handler, do: WeatherServer.Impl
end

defmodule WeatherServer.Handler do
  @moduledoc "Behaviour for the WeatherServer server handler."

  @callback get_weather(input :: map(), ctx :: map()) :: {:ok, map()} | {:error, term()}
  @callback list_cities(input :: map(), ctx :: map()) :: {:ok, map()} | {:error, term()}
end
