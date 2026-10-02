-module(smithy_beam).

-export([init/1]).

-spec init(rebar_state:t()) -> {ok, rebar_state:t()}.
init(State) ->
    {ok, State1} = smithy_beam_prv_generate:init(State),
    {ok, State1}.
