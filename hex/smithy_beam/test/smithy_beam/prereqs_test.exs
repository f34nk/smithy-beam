defmodule SmithyBeam.PrereqsTest do
  use ExUnit.Case, async: true

  alias SmithyBeam.Prereqs

  setup do
    tmp = Path.join(System.tmp_dir!(), "smithy_beam_prereqs_#{System.unique_integer([:positive])}")
    File.mkdir_p!(Path.join(tmp, "model"))
    on_exit(fn -> File.rm_rf(tmp) end)
    {:ok, tmp: tmp}
  end

  defp base_config(tmp) do
    %{
      models: "model",
      output: "lib/generated",
      config_file: nil,
      project_root: tmp
    }
  end

  test "ok when smithy exists and models dir is present", %{tmp: tmp} do
    assert :ok =
             Prereqs.check(base_config(tmp), find_executable: fn "smithy" -> "/usr/bin/smithy" end)
  end

  test "error when smithy is missing", %{tmp: tmp} do
    assert {:error, message} =
             Prereqs.check(base_config(tmp), find_executable: fn _ -> nil end)

    assert message =~ "Smithy CLI not found"
    assert message =~ "brew tap smithy-lang/tap && brew install smithy-cli"
    assert message =~ "https://smithy.io/2.0/guides/smithy-cli/cli_installation.html"
    assert message =~ "smithy --help"
  end

  test "error when models path is missing", %{tmp: tmp} do
    config = %{base_config(tmp) | models: "missing-model"}

    assert {:error, message} =
             Prereqs.check(config, find_executable: fn "smithy" -> "/usr/bin/smithy" end)

    assert message =~ "models path not found"
  end

  test "config_file mode checks the json file exists", %{tmp: tmp} do
    json = Path.join(tmp, "smithy-build.json")
    File.write!(json, "{}")

    config = %{
      models: nil,
      output: "lib/generated",
      config_file: "smithy-build.json",
      project_root: tmp
    }

    assert :ok = Prereqs.check(config, find_executable: fn "smithy" -> "/usr/bin/smithy" end)
  end
end
