package io.smithy.beam.core;

/**
 * Formats Smithy waiter path expressions for generated Erlang and Elixir waiters.
 *
 * <p>Simple dotted member paths (for example {@code Table.TableStatus}) are emitted as
 * snake_case segment lists. JMESPath-style expressions are emitted as string literals
 * because they cannot be represented as field segment lists.
 */
public final class BeamWaiterPaths {

    private BeamWaiterPaths() {}

    public static boolean isSimpleDottedPath(String path) {
        return path.matches("^[A-Za-z][A-Za-z0-9]*(\\.[A-Za-z][A-Za-z0-9]*)*$");
    }

    public static String emitErlangPath(String path) {
        if (!isSimpleDottedPath(path)) {
            return "<<\"" + escapeErlangBinary(path) + "\">>";
        }
        String[] segments = path.split("\\.");
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(BeamNameUtils.toSnakeCase(segments[i]));
        }
        out.append("]");
        return out.toString();
    }

    public static String emitElixirPath(String path) {
        if (!isSimpleDottedPath(path)) {
            return "\"" + escapeElixirString(path) + "\"";
        }
        String[] segments = path.split("\\.");
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(":").append(BeamNameUtils.toSnakeCase(segments[i]));
        }
        out.append("]");
        return out.toString();
    }

    private static String escapeErlangBinary(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeElixirString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
