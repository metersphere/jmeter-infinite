package io.metersphere.assertions.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.metersphere.assertions.JSONPathAssertion;
import io.metersphere.assertions.exception.JSONAssertionException;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.oro.text.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.Objects;


public class VerifyUtils {
    private static final boolean USE_JAVA_REGEX = !JMeterUtils.getPropDefault("jmeter.regex.engine", "oro").equalsIgnoreCase("oro");

    private static final Logger log = LoggerFactory.getLogger(JSONPathAssertion.class);

    public static final ThreadLocal<String> jsonPathValue = new ThreadLocal<>();


    /**
     * 大于
     * 支持整数，小数
     *
     * @param value 实际结果
     * @return boolean
     */
    public static void greaterThanAssert(Object value, String expectedValue) {
        if (getCompareResult(value, expectedValue) > 0) {
            return;
        }
        String msg = "Value expected greaterThan to be '%s', but found '%s'";
        throwJSONAssertionException(value, expectedValue, msg);
    }

    /**
     * 大于等于
     * 支持整数，小数
     *
     * @param value 实际结果
     * @return boolean
     */
    public static void greaterThanOrEqualAssert(Object value, String expectedValue) {
        if (getCompareResult(value, expectedValue) >= 0) {
            return;
        }

        String msg = "Value expected greaterThanOrEqual to be '%s', but found '%s'";
        throwJSONAssertionException(value, expectedValue, msg);
    }

    /**
     * 小于
     * 支持整数，小数
     *
     * @param value 实际结果
     * @return boolean
     */
    public static void lessThanAssert(Object value, String expectedValue) {
        if (getCompareResult(value, expectedValue) < 0) {
            return;
        }

        String msg = "Value expected lessThan to be '%s', but found '%s'";
        throwJSONAssertionException(value, expectedValue, msg);
    }

    /**
     * 小于等于
     * 支持整数，小数
     *
     * @param value 实际结果
     * @return boolean
     */
    public static void lessThanOrEqualAssert(Object value, String expectedValue) {
        if (getCompareResult(value, expectedValue) <= 0) {
            return;
        }

        String msg = "Value expected lessThanOrEqual to be '%s', but found '%s'";
        throwJSONAssertionException(value, expectedValue, msg);
    }

    /**
     * 包含
     * 支持字符串
     *
     * @param value 实际结果
     * @return boolean
     */
    public static void containsAssert(Object value, String expectedValue) {
        if (StringUtils.contains(Objects.toString(value), expectedValue)) {
            return;
        }

        String msg = "Value expected contains to be '%s', but found '%s'";
        throwJSONAssertionException(value, expectedValue, msg);
    }

    /**
     * 不包含
     * 支持字符串
     *
     * @param value 实际结果
     * @return boolean
     */
    public static void notContainsAssert(Object value, String expectedValue) {
        if (!StringUtils.contains(Objects.toString(value), expectedValue)) {
            return;
        }

        String msg = "Value expected not contains to be '%s', but found '%s'";
        throwJSONAssertionException(value, expectedValue, msg);
    }

    /**
     * 获取两个数字比较的结果
     *
     * @param value 实际结果
     * @return >0：大于；=0：等于；<0:小于
     */
    private static int getCompareResult(Object value, String expectedValue) {
        if (value == null || StringUtils.isBlank(expectedValue)) {
            String msg = "ExpectedValue or actualValue is null, expectedValue='%s', actualValue='%s'";
            throwJSONAssertionException(value, expectedValue, msg);
        }

        String actualValue = value.toString();
        if (validateNumber(actualValue) || validateNumber(expectedValue)) {
            String msg = "ExpectedValue or actualValue is not number, expectedValue='%s', actualValue='%s'";
            throwJSONAssertionException(actualValue, expectedValue, msg);
        }

        return Double.valueOf(actualValue).compareTo(Double.valueOf(expectedValue));
    }

    /**
     * 判断是否是整数或者是小数
     *
     * @param str 目标字符串
     * @return boolean
     */
    public static boolean validateNumber(String str) {
        if (StringUtils.isBlank(str)) {
            return true;
        }
        return !str.matches("[+-]?[0-9]+(\\.[0-9]{1,10})?");
    }

    /**
     * 包含
     * 支持字符串
     *
     * @param actualValue 实际结果
     * @return boolean
     */
    public static void equalsAssert(Object actualValue, String expectedValue) {
        if (actualValue instanceof JSONArray) {
            if (arrayMatched((JSONArray) actualValue, expectedValue)) {
                return;
            }
        } else if (actualValue instanceof String) {
            if (isObjectEquals(actualValue, "\"" + expectedValue + "\"")) {
                return;
            }
        } else if (isObjectEquals(actualValue, expectedValue)) {
            return;
        }
        String msg = "Value expected to be: \n %s \n but found: \n %s";
        throwJSONAssertionException(actualValue, expectedValue, msg);
    }

    public static void startsWithAssert(Object actualValue, String expectedValue) {
        if (StringUtils.startsWith(actualValue.toString(), expectedValue)) {
            return;
        }
        String msg = "Value expected to start with : \n %s \n but found: \n %s";
        throwJSONAssertionException(actualValue, expectedValue, msg);
    }

    public static void endsWithAssert(Object actualValue, String expectedValue) {
        if (StringUtils.endsWith(actualValue.toString(), expectedValue)) {
            return;
        }
        String msg = "Value expected to end with : \n %s \n but found: \n %s";
        throwJSONAssertionException(actualValue, expectedValue, msg);
    }

    private static void throwJSONAssertionException(Object actualValue, String expectedValue, String msg) {
        throw new JSONAssertionException(String.format(msg, expectedValue, objectToString(actualValue)), objectToString(actualValue));
    }

    private static void throwJSONAssertionLengthException(Object actualValue, String expectedValue, String msg) {
        throw new JSONAssertionException(String.format(msg, expectedValue, objectToString(actualValue).length()), objectToString(actualValue));
    }

    public static void emptyAssert(Object actualValue, String expectedValue) {
        if (actualValue == null || StringUtils.isBlank(actualValue.toString())) {
            return;
        }
        String msg = "Value expected to be: null, but found: \n %s";
        throw new JSONAssertionException(String.format(msg, actualValue), objectToString(actualValue));
    }

    public static void notEmptyAssert(Object actualValue, String expectedValue) {
        if (actualValue != null && StringUtils.isNotBlank(actualValue.toString())) {
            return;
        }
        String msg = "Value expected to be: not null, but found: null";
        throw new JSONAssertionException(msg, objectToString(actualValue));
    }

    public static void regexAssert(Object actualValue, String expectedValue) {
        String str = objectToString(actualValue);
        if (USE_JAVA_REGEX) {
            if (JMeterUtils.compilePattern(expectedValue).matcher(str).matches()) {
                return;
            }
        } else {
            Pattern pattern = JMeterUtils.getPatternCache().getPattern(expectedValue);
            if (JMeterUtils.getMatcher().matches(str, pattern)) {
                return;
            }
        }
        String msg = "Value expected to match regexp '%s', but it did not match: '%s'";
        throwJSONAssertionException(actualValue, expectedValue, msg);
    }

    private static DecimalFormat createDecimalFormat() {
        DecimalFormat decimalFormatter = new DecimalFormat("#.#");
        decimalFormatter.setMaximumFractionDigits(340); // java.text.DecimalFormat.DOUBLE_FRACTION_DIGITS == 340
        decimalFormatter.setMinimumFractionDigits(1);
        return decimalFormatter;
    }

    public static void lengthEqualsAssert(Object actualValue, String expectedValue) {
        try {
            int expectedLength = Integer.parseInt(expectedValue);
            if (actualValue != null && String.valueOf(actualValue).length() == expectedLength) {
                return;
            }
        } catch (NumberFormatException e) {
        }
        String msg = "Value expected to length: \n %s \n but found: \n %s";
        throwJSONAssertionLengthException(actualValue, expectedValue, msg);
    }

    public static void lengthNotEqualsAssert(Object actualValue, String expectedValue) {
        try {
            int expectedLength = Integer.parseInt(expectedValue);
            if (actualValue != null && String.valueOf(actualValue).length() != expectedLength) {
                return;
            }
        } catch (NumberFormatException e) {
        }
        String msg = "Value expected to length not equals: \n %s \n but found: \n %s";
        throwJSONAssertionLengthException(actualValue, expectedValue, msg);
    }

    public static void lengthGreaterThanAssert(Object actualValue, String expectedValue) {
        try {
            int expectedLength = Integer.parseInt(expectedValue);
            if (actualValue != null && String.valueOf(actualValue).length() > expectedLength) {
                return;
            }
        } catch (NumberFormatException e) {
        }
        String msg = "Value expected to length greater than: \n %s \n but found: \n %s";
        throwJSONAssertionLengthException(actualValue, expectedValue, msg);
    }

    public static void lengthGreaterThanOrEqualAssert(Object actualValue, String expectedValue) {
        try {
            int expectedLength = Integer.parseInt(expectedValue);
            if (actualValue != null && String.valueOf(actualValue).length() >= expectedLength) {
                return;
            }
        } catch (NumberFormatException e) {
        }
        String msg = "Value expected to length greater or equals than: \n %s \n but found: \n %s";
        throwJSONAssertionLengthException(actualValue, expectedValue, msg);
    }

    public static void lengthLessThanAssert(Object actualValue, String expectedValue) {
        try {
            int expectedLength = Integer.parseInt(expectedValue);
            if (actualValue != null && String.valueOf(actualValue).length() < expectedLength) {
                return;
            }
        } catch (NumberFormatException e) {
        }
        String msg = "Value expected to length less than: \n %s \n but found: \n %s";
        throwJSONAssertionLengthException(actualValue, expectedValue, msg);
    }

    public static void lengthLessThanOrEqualAssert(Object actualValue, String expectedValue) {
        try {
            int expectedLength = Integer.parseInt(expectedValue);
            if (actualValue != null && String.valueOf(actualValue).length() <= expectedLength) {
                return;
            }
        } catch (NumberFormatException e) {
        }
        String msg = "Value expected to length less or equals than: \n %s \n but found: \n %s";
        throwJSONAssertionLengthException(actualValue, expectedValue, msg);
    }


    private static boolean isObjectEquals(Object actualValue, String expectedValue) {
        Object expected = JSONValue.parse(expectedValue);
        if (Objects.equals(expected, actualValue)) {
            return true;
        }
        try {
            String expectedJson = toJsonString(expected);
            String actualJson = toJsonString(actualValue);
            String[] split = StringUtils.split(jsonPathValue.get(), ",");
            for (String path : split) {
                expectedJson = removeByJsonPath(expectedJson, path);
                actualJson = removeByJsonPath(actualJson, path);
            }

            boolean equals = compare(expectedJson, actualJson);
            if (!equals) {
                return false;
            }
        } catch (Exception e) {
            log.error("equalsAssert error: ", e);
            return false;
        }
        return true;
    }

    private static boolean arrayMatched(JSONArray value, String expectedValue) {
        if (value.isEmpty() && "[]".equals(expectedValue)) {
            return true;
        }
        return isObjectEquals(value, expectedValue);
    }

    public static void notEqualsAssert(Object actualValue, String expectedValue) {
        if (actualValue instanceof JSONArray) {
            if (!arrayMatched((JSONArray) actualValue, expectedValue)) {
                return;
            }
        } else if (actualValue instanceof String) {
            if (!isObjectEquals(actualValue, "\"" + expectedValue + "\"")) {
                return;
            }
        } else if (!isObjectEquals(actualValue, expectedValue)) {
            return;
        }
        String msg = "Value expected to not equals : \n %s \n but found: \n %s";
        throwJSONAssertionException(actualValue, expectedValue, msg);
    }

    public static String objectToString(Object value) {
        String str;
        if (value == null) {
            str = "null";
        } else if (value instanceof Map) {
            //noinspection unchecked
            str = new JSONObject((Map<String, ?>) value).toJSONString();
        } else if (value instanceof Double || value instanceof Float) {
            str = createDecimalFormat().format(value);
        } else {
            str = value.toString();
        }
        return str;
    }

    private static String removeByJsonPath(String json, String path) {
        DocumentContext documentContext = JsonPath.parse(json);
        return documentContext.delete(path).jsonString();
    }

    private static String toJsonString(Object json) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(json);
    }

    private static boolean compare(String source, String target) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode1 = objectMapper.readTree(source);
        JsonNode jsonNode2 = objectMapper.readTree(target);
        return jsonNode1.equals(jsonNode2);
    }
}
