defmodule BasicServerProbe do
  @moduledoc false

  @spec handle_get_type_closure(term(), Basic.GetTypeClosureInput.t(), term()) ::
          {:ok, Basic.GetTypeClosureOutput.t()} | {:error, term()}
  def handle_get_type_closure(_ctx, input, _meta) do
    {:ok,
     %Basic.GetTypeClosureOutput{
       basic_string: input.name,
       basic_boolean: input.verbose
     }}
  end
end
