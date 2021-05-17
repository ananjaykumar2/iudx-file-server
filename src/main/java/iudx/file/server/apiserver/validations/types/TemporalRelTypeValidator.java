package iudx.file.server.apiserver.validations.types;

import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TemporalRelTypeValidator implements Validator {

  private static final Logger LOGGER = LogManager.getLogger(TemporalRelTypeValidator.class);

  private final List<String> allowedTimeRel = Arrays.asList("after", "before", "during");

  private String value;
  private boolean required;

  public TemporalRelTypeValidator(String value, boolean required) {
    this.value = value;
    this.required = required;
  }

  @Override
  public boolean isValid() {
    LOGGER.debug("value : "+value+ "required : "+required);
    if (required && (value == null || value.isBlank())) {
      LOGGER.error("Validation error : null or blank value for required mandatory field");
      return false;
    } else {
      if (value == null || value.isBlank()) {
        return true;
      }
    }
    if (!allowedTimeRel.contains(value)) {
      LOGGER.error("Validation error : Invalid temporal relation value passed [ " + value + " ]");
      return false;
    }
    return true;
  }

  @Override
  public int failureCode() {
    // TODO Auto-generated method stub
    return 400;
  }

  @Override
  public String failureMessage() {
    // TODO Auto-generated method stub
    return "invalid time relation value.";
  }



}
// public ParameterTypeValidator create() {
// ParameterTypeValidator relValidator = new TemporalRelValueValidator();
// return relValidator;
// }
//
//
// class TemporalRelValueValidator implements ParameterTypeValidator {
//
// private boolean isValidValue(String value) {
// return value.equals("during");
// }
//
// @Override
// public RequestParameter isValid(String value) throws ValidationException {
// // TODO Auto-generated method stub
// if (value.isBlank()) {
// throw ValidationException.ValidationExceptionFactory
// .generateNotMatchValidationException("Empty values are not allowed in parameter.");
// }
//
// if (!isValidValue(value)) {
// throw ValidationException.ValidationExceptionFactory
// .generateNotMatchValidationException("Not a valid value for timerel");
// }
// return RequestParameter.create(value);
// }
//
// }
