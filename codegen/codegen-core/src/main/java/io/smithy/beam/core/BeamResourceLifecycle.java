package io.smithy.beam.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/** Normalizes Smithy resource lifecycle bindings for codegen. */
public enum BeamResourceLifecycle {
  CREATE("create"),
  READ("read"),
  UPDATE("update"),
  PUT("put"),
  DELETE("delete"),
  LIST("list");

  private final String helperName;

  BeamResourceLifecycle(String helperName) {
    this.helperName = helperName;
  }

  public String helperName() {
    return helperName;
  }

  public Optional<ShapeId> boundOperation(ResourceShape resource) {
    return switch (this) {
      case CREATE -> resource.getCreate();
      case READ -> resource.getRead();
      case UPDATE -> resource.getUpdate();
      case PUT -> resource.getPut();
      case DELETE -> resource.getDelete();
      case LIST -> resource.getList();
    };
  }

  /** Returns lifecycle bindings present on the resource, in stable declaration order. */
  public static Map<BeamResourceLifecycle, ShapeId> bindings(ResourceShape resource) {
    Map<BeamResourceLifecycle, ShapeId> out = new LinkedHashMap<>();
    for (BeamResourceLifecycle lc : values()) {
      lc.boundOperation(resource).ifPresent(op -> out.put(lc, op));
    }
    return out;
  }

  public static boolean hasEmittableBindings(ResourceShape resource) {
    return !bindings(resource).isEmpty() || !resource.getCollectionOperations().isEmpty();
  }
}
