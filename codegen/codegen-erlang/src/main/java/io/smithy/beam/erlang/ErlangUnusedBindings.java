package io.smithy.beam.erlang;

import io.beam.dsl.erlang.BinaryPattern;
import io.beam.dsl.erlang.BinarySegmentPattern;
import io.beam.dsl.erlang.CatchPattern;
import io.beam.dsl.erlang.Expression;
import io.beam.dsl.erlang.Function;
import io.beam.dsl.erlang.FunctionClause;
import io.beam.dsl.erlang.Guard;
import io.beam.dsl.erlang.ListPattern;
import io.beam.dsl.erlang.MapPattern;
import io.beam.dsl.erlang.MapPatternEntry;
import io.beam.dsl.erlang.MatchPattern;
import io.beam.dsl.erlang.Pattern;
import io.beam.dsl.erlang.RecordPattern;
import io.beam.dsl.erlang.RecordPatternField;
import io.beam.dsl.erlang.TuplePattern;
import io.beam.dsl.erlang.Variable;
import io.beam.dsl.erlang.VariablePattern;
import io.beam.dsl.erlang.WildcardPattern;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Underscore-prefixes head pattern bindings that are never read in the clause body or guard, so
 * generated Erlang does not trigger unused-variable warnings.
 */
final class ErlangUnusedBindings {
  private ErlangUnusedBindings() {}

  static Function prefix(Function function) {
    List<FunctionClause> clauses = new ArrayList<>(function.clauses().size());
    for (FunctionClause clause : function.clauses()) {
      clauses.add(prefix(clause));
    }
    return Function.of(function.name(), clauses, function.spec(), function.doc());
  }

  static FunctionClause prefix(FunctionClause clause) {
    Set<String> used = new HashSet<>();
    collectReferenced(clause.body(), used);
    collectReferenced(clause.guard(), used);
    List<Pattern> patterns = new ArrayList<>(clause.patterns().size());
    for (Pattern pattern : clause.patterns()) {
      patterns.add(prefixPattern(pattern, used));
    }
    return FunctionClause.of(patterns, clause.guard(), clause.body());
  }

  private static String unusedName(String name, Set<String> used) {
    if (name == null || name.startsWith("_") || used.contains(name)) {
      return name;
    }
    return "_" + name;
  }

  private static Pattern prefixPattern(Pattern pattern, Set<String> used) {
    if (pattern instanceof VariablePattern variable) {
      return VariablePattern.of(unusedName(variable.name(), used));
    }
    if (pattern instanceof WildcardPattern) {
      return pattern;
    }
    if (pattern instanceof RecordPattern record) {
      List<RecordPatternField> fields = new ArrayList<>(record.fields().size());
      for (RecordPatternField field : record.fields()) {
        fields.add(RecordPatternField.of(field.name(), prefixPattern(field.pattern(), used)));
      }
      String alias = record.alias() == null ? null : unusedName(record.alias(), used);
      return new RecordPattern(record.name(), alias, fields);
    }
    if (pattern instanceof MatchPattern match) {
      return MatchPattern.of(prefixPattern(match.left(), used), prefixPattern(match.right(), used));
    }
    if (pattern instanceof TuplePattern tuple) {
      List<Pattern> elements = new ArrayList<>(tuple.elements().size());
      for (Pattern element : tuple.elements()) {
        elements.add(prefixPattern(element, used));
      }
      return TuplePattern.of(elements);
    }
    if (pattern instanceof ListPattern list) {
      List<Pattern> elements = new ArrayList<>(list.elements().size());
      for (Pattern element : list.elements()) {
        elements.add(prefixPattern(element, used));
      }
      Pattern tail = list.tail() == null ? null : prefixPattern(list.tail(), used);
      return new ListPattern(elements, tail);
    }
    if (pattern instanceof BinaryPattern binary) {
      List<BinarySegmentPattern> segments = new ArrayList<>(binary.segments().size());
      for (BinarySegmentPattern segment : binary.segments()) {
        if (segment.pattern() == null) {
          segments.add(segment);
        } else {
          segments.add(
              new BinarySegmentPattern(
                  prefixPattern(segment.pattern(), used),
                  segment.literal(),
                  segment.size(),
                  segment.type(),
                  segment.unit()));
        }
      }
      return BinaryPattern.of(segments);
    }
    if (pattern instanceof MapPattern map) {
      List<MapPatternEntry> entries = new ArrayList<>(map.entries().size());
      for (MapPatternEntry entry : map.entries()) {
        entries.add(
            MapPatternEntry.of(
                entry.key(), prefixPattern(entry.value(), used), entry.updateOnly()));
      }
      String variable = map.variable() == null ? null : unusedName(map.variable(), used);
      return new MapPattern(variable, entries);
    }
    if (pattern instanceof CatchPattern catchPattern) {
      return new CatchPattern(
          prefixPattern(catchPattern.classPattern(), used),
          prefixPattern(catchPattern.reasonPattern(), used));
    }
    return pattern;
  }

  private static void collectReferenced(Object node, Set<String> names) {
    if (node == null) {
      return;
    }
    if (node instanceof Variable variable) {
      names.add(variable.name());
      return;
    }
    if (node instanceof Enum<?> || node instanceof CharSequence || node instanceof Number) {
      return;
    }
    if (node instanceof Iterable<?> iterable) {
      for (Object element : iterable) {
        collectReferenced(element, names);
      }
      return;
    }
    if (node instanceof Record record) {
      for (RecordComponent component : record.getClass().getRecordComponents()) {
        try {
          collectReferenced(component.getAccessor().invoke(record), names);
        } catch (ReflectiveOperationException e) {
          throw new IllegalStateException(
              "Failed to walk DSL node " + record.getClass().getName(), e);
        }
      }
    }
  }

  /** Visible for tests: names referenced as {@link Variable} expressions. */
  static Set<String> referencedNames(Expression expression) {
    Set<String> names = new HashSet<>();
    collectReferenced(expression, names);
    return names;
  }

  static Set<String> referencedNames(Guard guard) {
    Set<String> names = new HashSet<>();
    collectReferenced(guard, names);
    return names;
  }
}
