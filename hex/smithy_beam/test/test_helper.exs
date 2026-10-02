# Real Smithy CLI integration is opt-in (needs network + compatible JVM):
#   INCLUDE_SMITHY_CLI=1 mix test --only smithy_cli
exclude =
  if System.get_env("INCLUDE_SMITHY_CLI") in ["1", "true"] do
    []
  else
    [:smithy_cli]
  end

ExUnit.start(exclude: exclude)
