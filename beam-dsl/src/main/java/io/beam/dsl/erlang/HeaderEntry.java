package io.beam.dsl.erlang;

public sealed interface HeaderEntry
    permits HeaderComment,
        HeaderBlankLine,
        HeaderIfndef,
        HeaderDefine,
        HeaderEndif,
        HeaderRecordEntry,
        HeaderTypeAliasEntry {}
