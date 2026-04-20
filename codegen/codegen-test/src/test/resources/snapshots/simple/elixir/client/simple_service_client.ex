defmodule SimpleService.Client do
  @spec get_item(map(), GetItemInput.t()) ::
    {:ok, GetItemOutput.t()} | {:error, term()}
  def get_item(config, input) do
    {:error, :not_implemented}
  end

end
