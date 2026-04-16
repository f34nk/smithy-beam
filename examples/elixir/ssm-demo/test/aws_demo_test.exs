defmodule AwsDemoTest do
  use ExUnit.Case

  test "AwsDemo.run/0 is exported" do
    assert function_exported?(AwsDemo, :run, 0)
  end
end
