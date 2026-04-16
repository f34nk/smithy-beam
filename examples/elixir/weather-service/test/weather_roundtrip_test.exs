defmodule WeatherRoundtripTest do
  use ExUnit.Case
  import Plug.Test
  import Plug.Conn

  @opts WeatherService.Dispatcher.init([])

  # ── Test cases ─────────────────────────────────────────────────────────────

  test "GET /weather/Berlin returns 200 with temperature and unit" do
    conn =
      conn(:get, "/weather/Berlin")
      |> WeatherService.Dispatcher.call(@opts)

    assert conn.status == 200
    assert {:ok, body} = Jason.decode(conn.resp_body)
    assert body["temperature"] == 22.5
    assert body["unit"] == "Celsius"
  end

  test "GET /nonexistent returns 404" do
    conn =
      conn(:get, "/nonexistent")
      |> WeatherService.Dispatcher.call(@opts)

    assert conn.status == 404
  end

  test "POST /weather creates a report and returns 201" do
    payload = Jason.encode!(%{"city" => "Berlin", "temperature" => 22.5, "unit" => "Celsius"})

    conn =
      conn(:post, "/weather", payload)
      |> put_req_header("content-type", "application/json")
      |> WeatherService.Dispatcher.call(@opts)

    assert conn.status == 201
    assert {:ok, body} = Jason.decode(conn.resp_body)
    assert is_binary(body["reportId"])
  end

  test "POST /unknown returns 404" do
    conn =
      conn(:post, "/unknown", "")
      |> WeatherService.Dispatcher.call(@opts)

    assert conn.status == 404
  end
end
