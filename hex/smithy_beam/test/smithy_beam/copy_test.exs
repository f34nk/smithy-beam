defmodule SmithyBeam.CopyTest do
  use ExUnit.Case, async: true

  alias SmithyBeam.Cli
  alias SmithyBeam.Copy

  setup do
    tmp = Path.join(System.tmp_dir!(), "smithy_beam_copy_#{System.unique_integer([:positive])}")
    File.mkdir_p!(tmp)
    on_exit(fn -> File.rm_rf(tmp) end)
    {:ok, tmp: tmp}
  end

  test "sync! copies plugin files into destination", %{tmp: tmp} do
    fixture =
      Path.expand("../support/fake_smithy_out", __DIR__)

    dest = Path.join(tmp, "lib/generated")
    written = Copy.sync!(fixture, "elixir-client-codegen", dest)

    assert Path.join(dest, "demo_client.ex") in written
    assert File.read!(Path.join(dest, "demo_client.ex")) == "hello\n"
  end

  test "sync! replaces previous destination contents", %{tmp: tmp} do
    fixture = Path.expand("../support/fake_smithy_out", __DIR__)
    dest = Path.join(tmp, "out")
    File.mkdir_p!(dest)
    stale = Path.join(dest, "stale.ex")
    File.write!(stale, "old")

    Copy.sync!(fixture, "elixir-client-codegen", dest)
    refute File.exists?(stale)
    assert File.exists?(Path.join(dest, "demo_client.ex"))
  end

  test "cli run_build returns ok on zero exit" do
    assert :ok =
             Cli.run_build("/tmp", "cfg.json", "out", "elixir-client-codegen",
               find_executable: fn "smithy" -> "/bin/smithy" end,
               cmd: fn _bin, _args, _opts -> {"ok", 0} end
             )
  end

  test "cli run_build returns error on non-zero exit" do
    assert {:error, message} =
             Cli.run_build("/tmp", "cfg.json", "out", "elixir-client-codegen",
               find_executable: fn "smithy" -> "/bin/smithy" end,
               cmd: fn _bin, _args, _opts -> {"boom", 1} end
             )

    assert message =~ "smithy build failed"
    assert message =~ "boom"
  end
end
