defmodule UtilsTest do
  use ExUnit.Case, async: true

  test "split_base_url/1 empty string" do
    assert Utils.split_base_url("") == {"", ""}
  end

  test "split_base_url/1 https url" do
    assert Utils.split_base_url("https://api.example.com") ==
             {"https://", "api.example.com"}
  end

  test "split_base_url/1 url with port" do
    assert Utils.split_base_url("https://localhost:4566") ==
             {"https://", "localhost:4566"}
  end

  test "split_base_url/1 unparsed url" do
    assert Utils.split_base_url("not-a-url") == {"", "not-a-url"}
  end

  test "endpoint_host_from_config/1 from endpoint prefix" do
    config = %{endpoint_prefix: "s3", region: "us-west-2"}
    assert Utils.endpoint_host_from_config(config) == "s3.us-west-2.amazonaws.com"
  end

  test "endpoint_host_from_config/1 from base_url" do
    config = %{base_url: "https://custom.example:8443"}
    assert Utils.endpoint_host_from_config(config) == "custom.example:8443"
  end

  test "endpoint_host_from_config/1 missing prefix" do
    assert Utils.endpoint_host_from_config(%{region: "us-east-1"}) == nil
  end
end
