package io.smithy.beam.core;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.BooleanNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.NullNode;
import software.amazon.smithy.model.node.NumberNode;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait;

/**
 * Serializes {@code @endpointRuleSet} traits for BEAM runtime emission.
 */
public final class RuleSetSerializer {

    private RuleSetSerializer() {}

    /**
     * Validates the trait with smithy-rules-engine and returns compact JSON.
     */
    public static String toJson(Model model, EndpointRuleSetTrait trait) {
        trait.getEndpointRuleSet();
        return Node.printJson(trait.getRuleSet());
    }

    /**
     * Emits an Erlang map literal for embedding in generated modules.
     */
    public static String toErlangMap(EndpointRuleSetTrait trait) {
        trait.getEndpointRuleSet();
        return erlangNode(trait.getRuleSet());
    }

    /**
     * Emits an Elixir map literal for embedding in generated modules.
     */
    public static String toElixirMap(EndpointRuleSetTrait trait) {
        trait.getEndpointRuleSet();
        return elixirNode(trait.getRuleSet());
    }

    private static String erlangNode(Node node) {
        if (node instanceof ObjectNode objectNode) {
            if (objectNode.getMembers().isEmpty()) {
                return "#{}";
            }
            StringBuilder out = new StringBuilder("#{");
            boolean first = true;
            for (var entry : objectNode.getMembers().entrySet()) {
                if (!first) {
                    out.append(", ");
                }
                first = false;
                out.append(erlangKey(entry.getKey().getValue()));
                out.append(" => ");
                out.append(erlangNode(entry.getValue()));
            }
            out.append('}');
            return out.toString();
        }
        if (node instanceof ArrayNode arrayNode) {
            if (arrayNode.getElements().isEmpty()) {
                return "[]";
            }
            StringBuilder out = new StringBuilder('[');
            boolean first = true;
            for (Node element : arrayNode.getElements()) {
                if (!first) {
                    out.append(", ");
                }
                first = false;
                out.append(erlangNode(element));
            }
            out.append(']');
            return out.toString();
        }
        if (node instanceof BooleanNode booleanNode) {
            return booleanNode.getValue() ? "true" : "false";
        }
        if (node instanceof NumberNode numberNode) {
            Number value = numberNode.getValue();
            if (value.doubleValue() == Math.floor(value.doubleValue())) {
                return Integer.toString(value.intValue());
            }
            return value.toString();
        }
        if (node instanceof StringNode stringNode) {
            return erlangString(stringNode.getValue());
        }
        if (node instanceof NullNode) {
            return "undefined";
        }
        return erlangString(node.toString());
    }

    private static String erlangKey(String key) {
        return erlangString(key);
    }

    private static String erlangString(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "<<" + '"' + escaped + '"' + ">>";
    }

    private static String elixirNode(Node node) {
        if (node instanceof ObjectNode objectNode) {
            if (objectNode.getMembers().isEmpty()) {
                return "%{}";
            }
            StringBuilder out = new StringBuilder("%{");
            boolean first = true;
            for (var entry : objectNode.getMembers().entrySet()) {
                if (!first) {
                    out.append(", ");
                }
                first = false;
                out.append(elixirKey(entry.getKey().getValue()));
                out.append(" => ");
                out.append(elixirNode(entry.getValue()));
            }
            out.append('}');
            return out.toString();
        }
        if (node instanceof ArrayNode arrayNode) {
            if (arrayNode.getElements().isEmpty()) {
                return "[]";
            }
            StringBuilder out = new StringBuilder('[');
            boolean first = true;
            for (Node element : arrayNode.getElements()) {
                if (!first) {
                    out.append(", ");
                }
                first = false;
                out.append(elixirNode(element));
            }
            out.append(']');
            return out.toString();
        }
        if (node instanceof BooleanNode booleanNode) {
            return booleanNode.getValue() ? "true" : "false";
        }
        if (node instanceof NumberNode numberNode) {
            Number value = numberNode.getValue();
            if (value.doubleValue() == Math.floor(value.doubleValue())) {
                return Integer.toString(value.intValue());
            }
            return value.toString();
        }
        if (node instanceof StringNode stringNode) {
            return elixirString(stringNode.getValue());
        }
        if (node instanceof NullNode) {
            return "nil";
        }
        return elixirString(node.toString());
    }

    private static String elixirKey(String key) {
        if (key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return key;
        }
        return elixirString(key);
    }

    private static String elixirString(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }
}
