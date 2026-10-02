defmodule SmithyBeam.GenerateTest do
  use ExUnit.Case, async: false

  alias SmithyBeam.Generate

  setup do
    tmp = Path.join(System.tmp_dir!(), "smithy_beam_gen_#{System.unique_integer([:positive])}")
    model_dir = Path.join(tmp, "model")
    File.mkdir_p!(model_dir)

    File.cp!(
      Path.expand("../support/model/basic.smithy", __DIR__),
      Path.join(model_dir, "basic.smithy")
    )

    on_exit(fn -> File.rm_rf(tmp) end)
    {:ok, tmp: tmp}
  end

  test "run wires build json, cli, and copy with injectable cli", %{tmp: tmp} do
    plugin = "elixir-client-codegen"
    scratch_plugin = Path.join(tmp, "_build/smithy_beam/out/source/#{plugin}")
    File.mkdir_p!(scratch_plugin)
    File.write!(Path.join(scratch_plugin, "generated.ex"), "ok")

    opts = [
      config: [
        models: "model",
        language: :elixir,
        kind: :client,
        edition: "2026",
        output: "lib/generated",
        project_root: tmp
      ],
      prereqs_opts: [find_executable: fn "smithy" -> "/bin/smithy" end],
      cli_opts: [
        find_executable: fn "smithy" -> "/bin/smithy" end,
        cmd: fn _bin, _args, _opts ->
          {"", 0}
        end
      ]
    ]

    assert :ok = Generate.run(opts)
    assert File.exists?(Path.join(tmp, "lib/generated/generated.ex"))
    assert File.exists?(Path.join(tmp, "_build/smithy_beam/smithy-build.json"))
    assert File.exists?(Path.join(tmp, "_build/smithy_beam/manifest"))
  end

  @tag :smithy_cli
  test "integration with real smithy CLI", %{tmp: tmp} do
    opts = [
      config: [
        models: "model",
        language: :elixir,
        kind: :client,
        edition: "2026",
        service: "smithy.beam.demo.basic#BasicService",
        output: "lib/generated",
        project_root: tmp
      ]
    ]

    case Generate.run(opts) do
      :ok ->
        assert File.dir?(Path.join(tmp, "lib/generated"))
        assert Enum.any?(File.ls!(Path.join(tmp, "lib/generated")), &String.ends_with?(&1, ".ex"))

      {:error, message} ->
        if String.contains?(message, "class file version") do
          # Host Smithy CLI JVM is older than the published codegen JARs require.
          :ok
        else
          flunk(message)
        end
    end
  end
end
