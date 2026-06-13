%%% @doc Temporary stub endpoint rules evaluator emitted by smithy-beam codegen.
%%% The rule set argument is ignored for now. Endpoint resolution uses a minimal placeholder
%%% until a full AWS rules engine runtime is available.
-module(aws_endpoint_rules).

-export([evaluate/2]).

-spec evaluate(map(), map()) -> {ok, #{url := binary(), headers := map()}} | {error, term()}.
evaluate(_RuleSet, Params) ->
    Region = maps:get(<<"Region">>, Params, maps:get('Region', Params, undefined)),
    case Region of
        undefined -> {error, "Invalid Configuration: Missing Region"};
        Value -> {ok, #{url => <<"https://ec2.", Value, ".amazonaws.com">>, headers => #{}}}
    end.
