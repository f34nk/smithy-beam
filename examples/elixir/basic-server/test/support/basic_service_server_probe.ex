defmodule BasicServiceServerProbe do
  @moduledoc false

  @spec handle_get_type_closure(term(), BasicTypes.GetTypeClosureInput.t(), term()) ::
          {:ok, BasicTypes.GetTypeClosureOutput.t()} | {:error, term()}
  def handle_get_type_closure(_ctx, input, _meta) do
    {:ok,
     %BasicTypes.GetTypeClosureOutput{
       basic_string: input.name,
       basic_boolean: input.verbose
     }}
  end
end
