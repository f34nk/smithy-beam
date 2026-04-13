-module(cowboy_req).

%% Test-only mock for cowboy_req used by smithy_server_test.
%% The real cowboy_req is a Cowboy framework module; this stub allows
%% smithy_server:extract/1 to be exercised without a live HTTP server.
%%
%% A fake request is a plain map:
%%   #{
%%       method  => binary(),
%%       path    => binary(),
%%       headers => map(),
%%       body    => binary()
%%   }

-export([method/1, path/1, headers/1, read_body/1]).

method(#{method := M}) -> M.

path(#{path := P}) -> P.

headers(#{headers := H}) -> H.

%% read_body/1 returns {ok, Body, UpdatedReq} matching the real Cowboy API.
read_body(#{body := B} = Req) -> {ok, B, Req}.
