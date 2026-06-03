defmodule BasicServiceServerProbe do
  @moduledoc false

  @spec handle_get_type_closure(term(), BasicServiceTypes.GetTypeClosureInput.t(), term()) ::
          {:ok, BasicServiceTypes.GetTypeClosureOutput.t()} | {:error, term()}
  def handle_get_type_closure(_ctx, input, _meta) do
    {:ok,
     %BasicServiceTypes.GetTypeClosureOutput{
       basic_string: input.name,
       basic_boolean: input.verbose
     }}
  end
end
