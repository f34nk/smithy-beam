.PHONY: compile
compile:
	rebar3 compile

.PHONY: test
test:
	rebar3 eunit

.PHONY: clean
clean:
	rm -rf _build
	rebar3 clean

.PHONY: format
format:
	erlfmt --write src/*.erl
	erlfmt --write src/*.hrl
