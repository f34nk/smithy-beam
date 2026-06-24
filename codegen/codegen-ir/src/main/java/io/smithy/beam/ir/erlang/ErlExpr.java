package io.smithy.beam.ir.erlang;

public sealed interface ErlExpr extends IrObject
        permits ErlAtom, ErlVar, ErlInteger, ErlString, ErlBinary, ErlCall, ErlCallLocal, ErlRecord,
                ErlTuple, ErlList, ErlMap, ErlRecordAccess, ErlRecordUpdate, ErlCase, ErlMatch, ErlFun,
                ErlOp, ErlApply, ErlTry, ErlListComprehension, ErlBinaryTemplate, ErlExprBlock {}
