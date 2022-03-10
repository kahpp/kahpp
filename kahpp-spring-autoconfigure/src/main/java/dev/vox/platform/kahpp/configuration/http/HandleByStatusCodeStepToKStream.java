package dev.vox.platform.kahpp.configuration.http;

import com.fasterxml.jackson.databind.JsonNode;
import dev.vox.platform.kahpp.processor.StepProcessorSupplier;
import dev.vox.platform.kahpp.step.ChildStep;
import io.burt.jmespath.jackson.JacksonRuntime;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.streams.processor.ProcessorSupplier;
import org.springframework.stereotype.Component;

@Component
public final class HandleByStatusCodeStepToKStream
    extends StepProcessorSupplier<HandleByStatusCode> {

  private final transient MeterRegistry meterRegistry;
  private final transient JacksonRuntime jacksonRuntime;

  public HandleByStatusCodeStepToKStream(
      MeterRegistry meterRegistry, JacksonRuntime jacksonRuntime) {
    super(HandleByStatusCode.class);
    this.meterRegistry = meterRegistry;
    this.jacksonRuntime = jacksonRuntime;
  }

  @Override
  public ProcessorSupplier<JsonNode, JsonNode> supplier(HandleByStatusCode step, ChildStep child) {
    return () -> new HttpCallStepProcessor(step, child, meterRegistry, jacksonRuntime);
  }
}
