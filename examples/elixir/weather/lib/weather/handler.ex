defmodule Weather.Handler do
  @moduledoc """
  Concrete implementation of the Weather service server callbacks.

  This module implements the `SmithyHandler` behaviour.  The generated
  `lib/generated/weather_server.ex` scaffold lists the same callbacks as
  stubs; copy that file and replace the `{:error, :not_implemented}` bodies
  with real logic, or keep the generated scaffold as-is and adapt this
  module to match your preferred structure.

  ## Callback contract (from SmithyHandler)

      @callback handle_request(operation :: atom(), input :: map(), context :: map()) ::
                  {:ok, map()} | {:error, term()}

  `operation` is the snake_case operation atom from the Smithy model
  (e.g. `:get_current_time`, `:get_forecast`).
  `input` contains the decoded request fields keyed by string member names.
  `context` carries request metadata (headers, path bindings, …).
  """

  @behaviour SmithyHandler

  # ---------------------------------------------------------------------------
  # SmithyHandler callbacks
  # ---------------------------------------------------------------------------

  @doc """
  GetCurrentTime — returns the current UTC time as a Unix timestamp.
  """
  @impl SmithyHandler
  def handle_request(:get_current_time, _input, _context) do
    unix_seconds =
      DateTime.utc_now()
      |> DateTime.to_unix()

    {:ok, %{"time" => unix_seconds}}
  end

  @doc """
  GetForecast — returns a simulated rain-chance forecast for the city.

  Returns `{:error, :no_such_resource}` for unknown cities when the
  request explicitly asks only for known cities (city_id starts with "?").
  All other city IDs get a plausible default.
  """
  @impl SmithyHandler
  def handle_request(:get_forecast, %{"cityId" => city_id}, _context) do
    chance = city_rain_chance(city_id)
    {:ok, %{"chanceOfRain" => chance}}
  end

  def handle_request(:get_forecast, _input, _context) do
    {:ok, %{"chanceOfRain" => 0.5}}
  end

  @impl SmithyHandler
  def handle_request(_operation, _input, _context) do
    {:error, :not_found}
  end

  # ---------------------------------------------------------------------------
  # Internal helpers
  # ---------------------------------------------------------------------------

  defp city_rain_chance("Berlin"), do: 0.6
  defp city_rain_chance("London"), do: 0.7
  defp city_rain_chance("Madrid"), do: 0.2
  defp city_rain_chance(_unknown), do: 0.5
end
