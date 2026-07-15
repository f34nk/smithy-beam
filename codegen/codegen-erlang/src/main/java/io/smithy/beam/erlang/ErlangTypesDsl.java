package io.smithy.beam.erlang;

import io.beam.dsl.erlang.HeaderBlankLine;
import io.beam.dsl.erlang.HeaderComment;
import io.beam.dsl.erlang.HeaderDefine;
import io.beam.dsl.erlang.HeaderEntry;
import io.beam.dsl.erlang.HeaderTypeAliasEntry;
import io.beam.dsl.erlang.TypeAlias;
import java.util.ArrayList;
import java.util.List;

final class ErlangTypesDsl {

  private ErlangTypesDsl() {}

  static List<HeaderEntry> endpointRuleSetEntries(String ruleSetMap) {
    List<HeaderEntry> entries = new ArrayList<>();
    entries.add(new HeaderBlankLine());
    entries.add(new HeaderComment("@endpointRuleSet embedded at codegen time."));
    entries.add(new HeaderTypeAliasEntry(TypeAlias.of("endpoint_rule_set", "map()")));
    entries.add(new HeaderDefine("ENDPOINT_RULE_SET", ruleSetMap));
    return entries;
  }
}
