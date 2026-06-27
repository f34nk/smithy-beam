package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlBinaryPattern;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlGuard;
import io.smithy.beam.ir.erlang.ErlInteger;
import io.smithy.beam.ir.erlang.ErlIntegerPattern;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.EnumValueTrait;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ErlangEnumHelperIr {
    private ErlangEnumHelperIr() {}

    static List<ErlFunction> enumDecodeEncode(EnumShape shape, SymbolProvider sp) {
        String helperName = helperName(sp, shape);
        return List.of(
                decodeEnum(shape, sp, helperName),
                encodeEnum(shape, sp, helperName));
    }

    static List<ErlFunction> intEnumDecodeEncode(IntEnumShape shape, SymbolProvider sp) {
        String helperName = helperName(sp, shape);
        return List.of(
                decodeIntEnum(shape, sp, helperName),
                encodeIntEnum(shape, sp, helperName));
    }

    private static ErlFunction decodeEnum(EnumShape shape, SymbolProvider sp, String helperName) {
        List<ErlClause> clauses = new ArrayList<>();
        for (MemberShape m : shape.members()) {
            String wireValue = m.getTrait(EnumValueTrait.class)
                    .flatMap(EnumValueTrait::getStringValue)
                    .orElse(m.getMemberName());
            clauses.add(ErlClause.clause(
                    List.of(ErlBinaryPattern.binaryPattern(wireValue)),
                    ErlAtom.atom(enumAtomForMember(sp, shape, m.getMemberName()))));
        }
        clauses.add(ErlClause.clause(
                List.of(ErlVarPattern.varPattern("V")),
                List.of(ErlGuard.guard("is_binary", ErlVar.var("V"))),
                ErlTuple.tuple(ErlAtom.atom("unknown"), ErlVar.var("V"))));
        clauses.add(ErlClause.clause(
                List.of(ErlAtomPattern.atomPattern("null")),
                ErlAtom.atom("undefined")));
        clauses.add(ErlClause.clause(
                List.of(ErlAtomPattern.atomPattern("undefined")),
                ErlAtom.atom("undefined")));
        return ErlFunction.function("decode_" + helperName, 1, clauses);
    }

    private static ErlFunction encodeEnum(EnumShape shape, SymbolProvider sp, String helperName) {
        List<ErlClause> clauses = new ArrayList<>();
        for (MemberShape m : shape.members()) {
            String wireValue = m.getTrait(EnumValueTrait.class)
                    .flatMap(EnumValueTrait::getStringValue)
                    .orElse(m.getMemberName());
            clauses.add(ErlClause.clause(
                    List.of(ErlAtomPattern.atomPattern(enumAtomForMember(sp, shape, m.getMemberName()))),
                    ErlBinary.binary(wireValue)));
        }
        clauses.add(ErlClause.clause(
                List.of(ErlTuplePattern.tuplePattern(
                        ErlAtomPattern.atomPattern("unknown"), ErlVarPattern.varPattern("V"))),
                List.of(ErlGuard.guard("is_binary", ErlVar.var("V"))),
                ErlVar.var("V")));
        clauses.add(ErlClause.clause(
                List.of(ErlAtomPattern.atomPattern("undefined")),
                ErlAtom.atom("undefined")));
        return ErlFunction.function("encode_" + helperName, 1, clauses);
    }

    private static ErlFunction decodeIntEnum(IntEnumShape shape, SymbolProvider sp, String helperName) {
        List<ErlClause> clauses = new ArrayList<>();
        for (MemberShape m : shape.members()) {
            int wireValue = m.expectTrait(EnumValueTrait.class).expectIntValue();
            clauses.add(ErlClause.clause(
                    List.of(ErlIntegerPattern.integerPattern(wireValue)),
                    ErlAtom.atom(enumAtomForMember(sp, shape, m.getMemberName()))));
        }
        clauses.add(ErlClause.clause(
                List.of(ErlVarPattern.varPattern("V")),
                List.of(ErlGuard.guard("is_integer", ErlVar.var("V"))),
                ErlTuple.tuple(ErlAtom.atom("unknown"), ErlVar.var("V"))));
        clauses.add(ErlClause.clause(
                List.of(ErlAtomPattern.atomPattern("null")),
                ErlAtom.atom("undefined")));
        clauses.add(ErlClause.clause(
                List.of(ErlAtomPattern.atomPattern("undefined")),
                ErlAtom.atom("undefined")));
        return ErlFunction.function("decode_" + helperName, 1, clauses);
    }

    private static ErlFunction encodeIntEnum(IntEnumShape shape, SymbolProvider sp, String helperName) {
        List<ErlClause> clauses = new ArrayList<>();
        for (MemberShape m : shape.members()) {
            int wireValue = m.expectTrait(EnumValueTrait.class).expectIntValue();
            clauses.add(ErlClause.clause(
                    List.of(ErlAtomPattern.atomPattern(enumAtomForMember(sp, shape, m.getMemberName()))),
                    ErlInteger.integer(wireValue)));
        }
        clauses.add(ErlClause.clause(
                List.of(ErlTuplePattern.tuplePattern(
                        ErlAtomPattern.atomPattern("unknown"), ErlVarPattern.varPattern("V"))),
                List.of(ErlGuard.guard("is_integer", ErlVar.var("V"))),
                ErlVar.var("V")));
        clauses.add(ErlClause.clause(
                List.of(ErlAtomPattern.atomPattern("undefined")),
                ErlAtom.atom("undefined")));
        return ErlFunction.function("encode_" + helperName, 1, clauses);
    }

    private static String helperName(SymbolProvider sp, Shape shape) {
        return sp.toSymbol(shape).getName().replace("()", "");
    }

    private static String enumAtomForMember(SymbolProvider sp, Shape enumShape, String memberName) {
        @SuppressWarnings("unchecked")
        Map<String, String> byMember = sp.toSymbol(enumShape)
                .getProperty("enumAtomByMember", Map.class)
                .orElseThrow();
        return byMember.get(memberName);
    }
}
