defmodule AwsSigv4Test do
  use ExUnit.Case, async: true

  alias RuntimeTypes.HttpRequest

  @credentials %{
    access_key_id: "AKIAIOSFODNN7EXAMPLE",
    secret_access_key: "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
  }

  @config %{
    region: "us-east-1",
    signing_name: "s3",
    presign_expires: 3600,
    credentials: @credentials
  }

  test "sign/3 adds authorization header" do
    request = %HttpRequest{method: "GET", path: "/", host: "s3.amazonaws.com"}
    signed = AwsSigv4.sign(@config, :list_buckets, request)
    assert has_header?("authorization", signed.headers)
  end

  test "sign/3 adds host header" do
    request = %HttpRequest{method: "GET", path: "/", headers: []}
    signed = AwsSigv4.sign(@config, :list_buckets, request)
    assert has_header?("host", signed.headers)
  end

  test "sign/3 adds session token" do
    config =
      Map.put(
        @config,
        :credentials,
        Map.put(@credentials, :session_token, "temporary-session-token")
      )

    request = %HttpRequest{method: "GET", path: "/", host: "s3.amazonaws.com"}
    signed = AwsSigv4.sign(config, :list_buckets, request)
    assert has_header?("x-amz-security-token", signed.headers)
  end

  test "presign_url/3" do
    request = %HttpRequest{
      method: "GET",
      path: "/my-bucket/object.txt",
      host: "localhost:4566"
    }

    assert {:ok, url} = AwsSigv4.presign_url(@config, :get_object, request)
    assert String.starts_with?(url, "https://localhost:4566/my-bucket/object.txt?")
    assert String.contains?(url, "X-Amz-Algorithm=AWS4-HMAC-SHA256")
    assert String.contains?(url, "X-Amz-Signature=")
  end

  defp has_header?(name, headers) do
    Enum.any?(headers, fn {header_name, _value} ->
      String.downcase(header_name) == String.downcase(name)
    end)
  end
end
