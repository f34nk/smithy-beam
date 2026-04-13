# ---------------------------------------------------------------------------
# Stub for SmithyErrorMap — mirrors smithy_error_map.erl.
# Defined at top level so SmithyServer can resolve it as SmithyErrorMap.
# Phase 22 will produce the real generated module.
# ---------------------------------------------------------------------------
defmodule SmithyErrorMap do
  def to_http({:not_found, msg}), do: {404, msg}
  def to_http({:conflict, msg}), do: {409, msg}
  def to_http({:validation, msg}), do: {400, msg}
  def to_http({:internal, msg}), do: {500, msg}
  def to_http({:unauthorized, msg}), do: {401, msg}
  def to_http({:forbidden, msg}), do: {403, msg}
  def to_http(:not_implemented), do: {501, "Not implemented"}
  def to_http(_), do: {500, "Internal server error"}
end

defmodule SmithyServerTest do
  use ExUnit.Case, async: true
  import Plug.Test
  import Plug.Conn

  # ---------------------------------------------------------------------------
  # extract/1
  # ---------------------------------------------------------------------------

  describe "extract/1" do
    test "extracts HTTP method" do
      conn = conn(:post, "/items", "")
      {method, _path, _headers, _body} = SmithyServer.extract(conn)
      assert method == "POST"
    end

    test "extracts request path" do
      conn = conn(:get, "/weather/NYC", "")
      {_method, path, _headers, _body} = SmithyServer.extract(conn)
      assert path == "/weather/NYC"
    end

    test "extracts body" do
      body = ~s({"name":"Alice"})
      conn = conn(:put, "/users/1", body) |> put_req_header("content-type", "application/json")
      {_method, _path, _headers, extracted_body} = SmithyServer.extract(conn)
      assert extracted_body == body
    end

    test "extracts empty body as empty binary" do
      conn = conn(:delete, "/items/99", "")
      {_method, _path, _headers, body} = SmithyServer.extract(conn)
      assert body == ""
    end

    test "extracts headers as a map" do
      conn =
        conn(:get, "/", "")
        |> put_req_header("content-type", "application/json")
        |> put_req_header("x-api-key", "secret")

      {_method, _path, headers, _body} = SmithyServer.extract(conn)
      assert is_map(headers)
      assert headers["content-type"] == "application/json"
      assert headers["x-api-key"] == "secret"
    end

    test "returns a 4-tuple" do
      conn = conn(:get, "/", "")
      assert {_, _, _, _} = SmithyServer.extract(conn)
    end

    test "extracts PATCH with all fields" do
      body = "data"
      conn =
        conn(:patch, "/resource", body)
        |> put_req_header("accept", "*/*")

      assert {"PATCH", "/resource", headers, ^body} = SmithyServer.extract(conn)
      assert headers["accept"] == "*/*"
    end
  end

  # ---------------------------------------------------------------------------
  # response/3
  # ---------------------------------------------------------------------------

  describe "response/3" do
    test "sends correct status code" do
      conn = conn(:get, "/", "") |> SmithyServer.response(200, %{ok: true})
      assert conn.status == 200
    end

    test "sends 201 created" do
      conn = conn(:post, "/items", "") |> SmithyServer.response(201, %{id: "1"})
      assert conn.status == 201
    end

    test "sets content-type to application/json" do
      conn = conn(:get, "/", "") |> SmithyServer.response(200, %{})
      assert get_resp_header(conn, "content-type") |> List.first() =~
               "application/json"
    end

    test "body is JSON-encoded" do
      conn = conn(:get, "/", "") |> SmithyServer.response(200, %{message: "hello"})
      assert conn.resp_body =~ "hello"
      assert {:ok, _} = Jason.decode(conn.resp_body)
    end
  end

  # ---------------------------------------------------------------------------
  # error_response/2
  # ---------------------------------------------------------------------------

  describe "error_response/2" do
    test "not_found error returns 404" do
      conn = conn(:get, "/gone", "") |> SmithyServer.error_response({:not_found, "item gone"})
      assert conn.status == 404
    end

    test "conflict error returns 409" do
      conn = conn(:post, "/items", "") |> SmithyServer.error_response({:conflict, "duplicate"})
      assert conn.status == 409
    end

    test "internal error returns 500" do
      conn = conn(:get, "/", "") |> SmithyServer.error_response({:internal, "oops"})
      assert conn.status == 500
    end

    test "unknown error term returns 500" do
      conn = conn(:get, "/", "") |> SmithyServer.error_response(:totally_unknown)
      assert conn.status == 500
    end

    test "forbidden error returns 403" do
      conn = conn(:get, "/", "") |> SmithyServer.error_response({:forbidden, "no access"})
      assert conn.status == 403
    end

    test "response body contains 'message' key" do
      conn = conn(:get, "/", "") |> SmithyServer.error_response({:not_found, "item gone"})
      assert conn.resp_body =~ "message"
    end

    test "response body contains the error message text" do
      conn = conn(:get, "/", "") |> SmithyServer.error_response({:not_found, "item gone"})
      assert conn.resp_body =~ "item gone"
    end

    test "content-type is application/json" do
      conn = conn(:get, "/", "") |> SmithyServer.error_response({:not_found, "x"})
      assert get_resp_header(conn, "content-type") |> List.first() =~ "application/json"
    end
  end

  # ---------------------------------------------------------------------------
  # validation_error/2
  # ---------------------------------------------------------------------------

  describe "validation_error/2" do
    test "returns 400 status" do
      conn =
        conn(:post, "/", "")
        |> SmithyServer.validation_error({:missing_required_fields, ["name"]})

      assert conn.status == 400
    end

    test "sets content-type to application/json" do
      conn =
        conn(:post, "/", "")
        |> SmithyServer.validation_error({:missing_required_fields, ["email"]})

      assert get_resp_header(conn, "content-type") |> List.first() =~ "application/json"
    end

    test "body contains 'message' key" do
      conn =
        conn(:post, "/", "")
        |> SmithyServer.validation_error({:missing_required_fields, ["name", "email"]})

      assert conn.resp_body =~ "message"
    end

    test "body mentions the missing field" do
      conn =
        conn(:post, "/", "")
        |> SmithyServer.validation_error({:missing_required_fields, ["name"]})

      assert conn.resp_body =~ "name"
    end

    test "body is valid JSON" do
      conn =
        conn(:post, "/", "")
        |> SmithyServer.validation_error({:missing_required_fields, ["x"]})

      assert {:ok, _} = Jason.decode(conn.resp_body)
    end
  end

  # ---------------------------------------------------------------------------
  # not_found/1
  # ---------------------------------------------------------------------------

  describe "not_found/1" do
    test "returns 404 status" do
      conn = conn(:get, "/missing", "") |> SmithyServer.not_found()
      assert conn.status == 404
    end

    test "body is 'Not Found'" do
      conn = conn(:get, "/missing", "") |> SmithyServer.not_found()
      assert conn.resp_body == "Not Found"
    end
  end
end
