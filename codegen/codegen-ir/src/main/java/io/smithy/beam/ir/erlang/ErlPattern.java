package io.smithy.beam.ir.erlang;

import java.util.List;

public sealed interface ErlPattern extends IrObject
    permits ErlAtomPattern,
        ErlVarPattern,
        ErlIntegerPattern,
        ErlTuplePattern,
        ErlConsPattern,
        ErlNilPattern,
        ErlBinaryPattern,
        ErlBinPattern,
        ErlRecordPattern,
        ErlMatchPattern,
        ErlMapPattern {

  /** Build clause patterns from emitter arg strings (one pattern per argument). */
  static List<ErlPattern> functionHeadPatterns(List<String> args) {
    return args.stream().map(ErlPattern::patternForArg).toList();
  }

  private static ErlPattern patternForArg(String arg) {
    if (arg.startsWith("#")) {
      return ErlVarPattern.varPattern(arg);
    }
    return ErlVarPattern.varPattern(arg);
  }
}
