package dev.vox.platform.kahpp.configuration.http;

import dev.vox.platform.kahpp.configuration.RecordAction;
import dev.vox.platform.kahpp.configuration.conditional.Condition;
import dev.vox.platform.kahpp.configuration.conditional.Conditional;
import dev.vox.platform.kahpp.configuration.http.client.ApiClient;
import dev.vox.platform.kahpp.streams.KaHPPRecord;
import io.burt.jmespath.jackson.JacksonRuntime;
import io.vavr.control.Either;
import io.vavr.control.Try;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractHttpCall implements HttpCall, Conditional {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractHttpCall.class);

  @NotBlank private final transient String name;

  /** todo: Custom validator that exposes the missing api reference */
  @NotNull(message = "Could not find apiClient, possibly missing entry in `kahpp.apis`")
  private final transient ApiClient apiClient;

  @Pattern(regexp = "POST|PUT|PATCH")
  private transient String method = "POST";

  @NotNull protected transient ResponseHandler responseHandler;

  @Pattern(regexp = "true|false")
  private transient String forwardRecordOnError = "false";

  @NotNull private transient Condition condition;

  @NotBlank private transient String path;

  // This path could change due to the placeholder
  private String actualPath;

  protected AbstractHttpCall(String name, Map<String, ?> config) {
    this.name = name;
    if (config.containsKey("path")) {
      this.path = config.get("path").toString();
      this.actualPath = path;
    }

    if (config.containsKey("method")) {
      this.method = config.get("method").toString().toUpperCase(Locale.ROOT);
    }

    if (config.containsKey(RESPONSE_HANDLER_CONFIG)) {
      this.responseHandler = (ResponseHandler) config.get(RESPONSE_HANDLER_CONFIG);
    }

    if (config.containsKey("forwardRecordOnError")) {
      this.forwardRecordOnError =
          config.get("forwardRecordOnError").toString().toLowerCase(Locale.ROOT);
    }

    if (config.containsKey(STEP_CONFIGURATION_CONDITION)) {
      this.condition = (Condition) config.get(STEP_CONFIGURATION_CONDITION);
    }

    this.apiClient = loadApiClient(config);
  }

  @Override
  public @NotNull Condition condition() {
    return this.condition;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Either<Throwable, RecordAction> call(KaHPPRecord record) {
    ResponseHandler responseHandler = this.responseHandler;

    return Try.of(() -> apiClient.sendRequest(method, actualPath, record.getValue().toString()))
        .mapTry(responseHandler::handle)
        .toEither();
  }

  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  @Override
  public Either<Throwable, RecordAction> call(KaHPPRecord record, JacksonRuntime jacksonRuntime) {
    Matcher m = java.util.regex.Pattern.compile("\\$\\{(.*?)\\}").matcher(path);
    while (m.find()) {
      String value = jacksonRuntime.compile(m.group(1)).search(record.build()).textValue();
      if (value == null) {
        LOGGER.warn("No value found inside the record for this placeholder {}", m.group(0));
        return Either.left(new RuntimeException("Placeholder value not found"));
      }
      actualPath = path.replace(m.group(0), value);
    }
    return call(record);
  }

  @Override
  public boolean shouldForwardRecordOnError() {
    return Boolean.parseBoolean(forwardRecordOnError);
  }

  private static ApiClient loadApiClient(Map<String, ?> config) {
    Object candidateApiClient = config.get("apiClient");
    if (candidateApiClient instanceof ApiClient) {
      return (ApiClient) candidateApiClient;
    }

    return null;
  }
}
