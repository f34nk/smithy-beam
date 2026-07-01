defmodule DemoTest do
  use ExUnit.Case

  test "Demo.run/0 is exported" do
    Code.ensure_loaded!(Demo)
    assert function_exported?(Demo, :run, 0)
  end
end
