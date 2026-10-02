.PHONY: smoke-elixir smoke-erlang

# Codegen JARs require Java 21+. Prefer asdf openjdk-21 when present.
JAVA21 ?= $(HOME)/.asdf/installs/java/openjdk-21
export JAVA_HOME ?= $(JAVA21)
export PATH := $(JAVA_HOME)/bin:$(PATH)

smoke-elixir:
	cd smoke/elixir_client && mix deps.get && mix compile

smoke-erlang:
	mkdir -p smoke/erlang_client/_checkouts
	ln -sfn ../../.. smoke/erlang_client/_checkouts/smithy_beam
	cd smoke/erlang_client && rebar3 compile
