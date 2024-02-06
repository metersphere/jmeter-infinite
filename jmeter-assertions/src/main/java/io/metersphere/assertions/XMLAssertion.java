/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.metersphere.assertions;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Predicate;
import io.metersphere.assertions.constants.JSONAssertionCondition;
import io.metersphere.assertions.util.VerifyUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.assertions.Assertion;
import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.assertions.LogErrorHandler;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.testelement.ThreadListener;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.util.function.BiConsumer;

/**
 * Checks if the result is a well-formed XML content using {@link XMLReader}
 *
 */
public class XMLAssertion extends AbstractTestElement implements Serializable, Assertion, ThreadListener {
    private static final Logger log = LoggerFactory.getLogger(XMLAssertion.class);
    private static final long serialVersionUID = 242L;

    public static final String XML_PATH = "JSON_PATH";
    public static final String EXPECTED_VALUE = "EXPECTED_VALUE";
    public static final String JSON_VALIDATION = "JSONVALIDATION";
    public static final String CONDITION = "CONDITION";

    public String getJsonPath() {
        return getPropertyAsString(XML_PATH);
    }

    public void setJsonPath(String jsonPath) {
        setProperty(XML_PATH, jsonPath);
    }

    public String getExpectedValue() {
        return getPropertyAsString(EXPECTED_VALUE);
    }

    public void setExpectedValue(String expectedValue) {
        setProperty(EXPECTED_VALUE, expectedValue);
    }

    public void setJsonValidationBool(boolean jsonValidation) {
        setProperty(JSON_VALIDATION, jsonValidation);
    }

    public void setCondition(String condition) {
        setProperty(CONDITION, condition);
    }

    public String getCondition() {
        return getPropertyAsString(CONDITION);
    }

    public boolean isJsonValidationBool() {
        return getPropertyAsBoolean(JSON_VALIDATION);
    }

    private static DecimalFormat createDecimalFormat() {
        DecimalFormat decimalFormatter = new DecimalFormat("#.#");
        decimalFormatter.setMaximumFractionDigits(340);
        decimalFormatter.setMinimumFractionDigits(1);
        return decimalFormatter;
    }

    // one builder for all requests in a thread
    private static final ThreadLocal<XMLReader> XML_READER = new ThreadLocal<XMLReader>() {
        @Override
        protected XMLReader initialValue() {
            try {
                XMLReader reader = SAXParserFactory.newInstance()
                        .newSAXParser()
                        .getXMLReader();
                reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                return reader;
            } catch (SAXException | ParserConfigurationException e) {
                log.error("Error initializing XMLReader in XMLAssertion", e);
                return null;
            }
        }
    };

    /**
     * Returns the result of the Assertion.
     * Here it checks whether the Sample data is XML.
     * If so an AssertionResult containing a FailureMessage will be returned.
     * Otherwise the returned AssertionResult will reflect the success of the Sample.
     */
    @Override
    public AssertionResult getResult(SampleResult response) {
        // no error as default
        AssertionResult result = new AssertionResult(getName());
        String resultData = response.getResponseDataAsString();
        if (StringUtils.isBlank(resultData)) {
            log.info("XMLAssertion: responseData is null");
            return result.setResultForNull();
        }
        result.setFailure(false);
        XMLReader builder = XML_READER.get();
        if (builder != null) {
            try {
                builder.setErrorHandler(new LogErrorHandler());
                builder.parse(new InputSource(new StringReader(resultData)));
                try {
                    JSONObject xmlObject = XML.toJSONObject(resultData);
                    String jsonString = xmlObject.toString(4);
                    Object actualValue = JsonPath.read(jsonString, this.getJsonPath(), new Predicate[0]);
                    String jsonPathExpression = getJsonPath();
                    if (isJsonValidationBool() && !JsonPath.isPathDefinite(jsonPathExpression)) {
                        // 没有勾选匹配值，只检查表达式是否正确
                        log.error("JSONPath is indefinite");
                        throw new IllegalStateException("JSONPath is indefinite");
                    }
                    JSONAssertionCondition condition = JSONAssertionCondition.valueOf(getCondition());
                    log.info("JSONPathAssertion: actualValue: {}, expectedValue: {}, condition: {}", actualValue, jsonPathExpression, condition);

                    VerifyUtils.jsonPathValue.set(jsonPathExpression);
                    BiConsumer<Object, String> assertMethod = condition.getAssertMethod();
                    if (assertMethod != null) {
                        assertMethod.accept(actualValue, getExpectedValue());
                    }
                } catch (Exception e) {
                    result.setError(true);
                    result.setFailure(true);
                    result.setFailureMessage(e.getMessage());
                }
            } catch (SAXException | IOException e) {
                result.setError(true);
                result.setFailure(true);
                result.setFailureMessage(e.getMessage());
            }
        } else {
            result.setError(true);
            result.setFailureMessage("Cannot initialize XMLReader in element:" + getName() + ", check jmeter.log file");
        }

        return result;
    }

    @Override
    public void threadStarted() {
        // nothing to do on thread start
    }

    @Override
    public void threadFinished() {
        XML_READER.remove();
    }
}
