%% Generated Erlang server behaviour for smithy.beam.demo.basic#BasicService.
-module(basic_service_behaviour).
-include("basic_service_types.hrl").

%% @doc
%% Returns every named type in the service closure for the given name.
%%
%% ## How to call
%%
%% Supply `name` as the path label. Set the `verbose` query parameter when you want
%% optional fields in the response.
%%
%% ```
%% config = %{base_url: "http://localhost:8080"}
%% input  = %{name: "example", verbose: true}
%% {:ok, output} = BasicServiceClient.get_type_closure(config, input)
%% ```
%%
%% Failures surface as `{:error, term()}`. A missing name may map to the modeled
%% `BasicNotFound` error.
-callback handle_get_type_closure(
    Ctx :: term(),
    Input :: get_type_closure_input(),
    Meta :: term()
) -> {ok, get_type_closure_output()} | {error, term()}.

%% @doc
%% Returns a page of basic items. Pass `nextToken` from a prior response to fetch the next page.
%%
%% ## How to call
%%
%% Use the generated paginator to walk every page:
%%
%% ```
%% config = %{base_url: "http://localhost:8080", http_client: MyHttpMock}
%% input  = %{page_size: 10}
%% {:ok, items} = BasicServicePaginators.paginate_list_basic_items(config, input)
%% ```
%%
%% Or call the client operation directly for a single page:
%%
%% ```
%% {:ok, output} = BasicServiceClient.list_basic_items(config, input)
%% items = Map.get(output, :items, [])
%% next_token = Map.get(output, :next_token)
%% ```
-callback handle_list_basic_items(
    Ctx :: term(),
    Input :: list_basic_items_input(),
    Meta :: term()
) -> {ok, list_basic_items_output()} | {error, term()}.
