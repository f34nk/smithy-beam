defmodule Weather.Server do
  @moduledoc "Generated Smithy server dispatcher for Weather. Do not edit."

  use Plug.Router

  alias Weather.Server.Types.GetWeatherInput
  alias Weather.Server.Types.GetWeatherOutput
  alias Weather.Server.Types.ListCitiesInput
  alias Weather.Server.Types.ListCitiesOutput

  plug :match
  plug :dispatch

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
    %GetWeatherInput{city: conn.path_params["city"]}
  end

  defp serialize_get_weather(%GetWeatherOutput{temperature: temperature}) do
    Jason.encode!(%{"temperatureC" => temperature})
  end

  defp deserialize_list_cities(_conn) do
    %ListCitiesInput{}
  end

  defp serialize_list_cities(%ListCitiesOutput{next_token: next_token, cities: cities}) do
    Jason.encode!(%{"nextToken" => next_token, "cities" => cities})
  end

  defp handler, do: Weather.Server.Impl
end


defmodule Weather.Server.Handler do
  @moduledoc "Behaviour for the Weather server handler."

  alias Weather.Server.Types.GetWeatherInput
  alias Weather.Server.Types.GetWeatherOutput
  alias Weather.Server.Types.ListCitiesInput
  alias Weather.Server.Types.ListCitiesOutput

  @callback get_weather(input :: GetWeatherInput.t(), ctx :: map()) ::
              {:ok, GetWeatherOutput.t()} | {:error, term()}
  @callback list_cities(input :: ListCitiesInput.t(), ctx :: map()) ::
              {:ok, ListCitiesOutput.t()} | {:error, term()}
end
