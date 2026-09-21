package io.beam.lang.erlang;

public sealed interface HeaderEntry
    permits HeaderComment,
        HeaderBlankLine,
        HeaderIfndef,
        HeaderDefine,
        HeaderEndif,
        HeaderRecordEntry,
        HeaderTypeAliasEntry {}
