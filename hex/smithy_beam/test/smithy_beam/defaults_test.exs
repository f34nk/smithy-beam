defmodule SmithyBeam.DefaultsTest do
  use ExUnit.Case, async: true

  alias SmithyBeam.Defaults

  test "maps language and kind to plugin ids" do
    assert Defaults.plugin_id(:elixir, :client) == "elixir-client-codegen"
    assert Defaults.plugin_id(:elixir, :server) == "elixir-server-codegen"
    assert Defaults.plugin_id(:elixir, :types) == "elixir-types-codegen"
    assert Defaults.plugin_id(:erlang, :client) == "erlang-client-codegen"
    assert Defaults.plugin_id(:erlang, :server) == "erlang-server-codegen"
    assert Defaults.plugin_id(:erlang, :types) == "erlang-types-codegen"
  end

  test "elixir and erlang codegen coordinates differ" do
    elixir = Defaults.codegen_coordinate(:elixir)
    erlang = Defaults.codegen_coordinate(:erlang)

    assert elixir != erlang
    assert String.contains?(elixir, "codegen-elixir")
    assert String.contains?(erlang, "codegen-erlang")
    assert String.ends_with?(elixir, ":#{Defaults.codegen_version()}")
  end

  test "default outputs depend on language" do
    assert Defaults.default_output(:elixir) == "lib/generated"
    assert Defaults.default_output(:erlang) == "src/generated"
  end

  test "known_plugin? recognizes configured pairs" do
    assert Defaults.known_plugin?({:elixir, :client})
    refute Defaults.known_plugin?({:elixir, :unknown})
  end
end
