package dev.vox.platform.kahpp.configuration.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.vox.platform.kahpp.configuration.TransformRecord;
import dev.vox.platform.kahpp.configuration.TransformRecordApplier;
import dev.vox.platform.kahpp.configuration.conditional.Condition;
import dev.vox.platform.kahpp.streams.KaHPPRecord;
import io.burt.jmespath.Expression;
import io.burt.jmespath.jackson.JacksonRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotBlank;

public final class SplitValueTransform implements FlatRecordTransform {

  @NotBlank private final transient String name;
  @NotBlank private final transient String jmesPath;
  @NotBlank private final transient String to;
  private transient Condition condition = Condition.ALWAYS;

  public SplitValueTransform(String name, Map<String, ?> config) {
    this.name = name;
    this.jmesPath = config.get("jmesPath").toString();
    this.to = config.containsKey("to") ? config.get("to").toString() : "";
    if (config.containsKey(STEP_CONFIGURATION_CONDITION)) {
      this.condition = (Condition) config.get(STEP_CONFIGURATION_CONDITION);
    }
  }

  @Override
  public List<KaHPPRecord> transform(JacksonRuntime runtime, KaHPPRecord record) {
    Expression<JsonNode> jsonNodeExpression = runtime.compile(jmesPath);

    JsonNode possibleArrayNode = jsonNodeExpression.search(record.build());
    if (!(possibleArrayNode instanceof ArrayNode)) {
      throw new SplitValueException(
          String.format(
              "Could not split record value: data found at JmesPath %s is not an array", jmesPath));
    }

    if (possibleArrayNode.isEmpty()) {
      throw new SplitValueException(
          String.format(
              "Could not split record value: array found at JmesPath %s has no elements",
              jmesPath));
    }

    List<KaHPPRecord> records = new ArrayList<>();

    possibleArrayNode
        .iterator()
        .forEachRemaining(
            jsonNode -> {
              KaHPPRecord mutatedRecord =
                  KaHPPRecord.build(
                      record.getKey().deepCopy(),
                      jsonNode,
                      record.getTimestamp(),
                      record.getHeaders());
              if (!this.to.isEmpty()) {
                mutatedRecord =
                    KaHPPRecord.build(
                        record.getKey().deepCopy(),
                        record.getValue().deepCopy(),
                        record.getTimestamp(),
                        record.getHeaders());
                TransformRecordApplier.apply(
                    runtime, mutatedRecord, TransformRecord.replacePath(jsonNode, to));
                TransformRecordApplier.apply(
                    runtime,
                    mutatedRecord,
                    TransformRecord.withMutation(
                        mutatedRecord.build(),
                        TransformRecord.RemoveFieldMutation.field(jmesPath)));
              }
              records.add(mutatedRecord);
            });

    return records;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Condition condition() {
    return this.condition;
  }
}
