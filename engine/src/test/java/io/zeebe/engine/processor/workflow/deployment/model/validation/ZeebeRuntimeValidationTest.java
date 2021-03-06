/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.validation;

import static io.zeebe.engine.processor.workflow.deployment.model.validation.ExpectedValidationResult.expect;
import static org.junit.Assert.fail;

import io.zeebe.el.ExpressionLanguage;
import io.zeebe.el.ExpressionLanguageFactory;
import io.zeebe.engine.processor.workflow.ExpressionProcessor;
import io.zeebe.engine.processor.workflow.ExpressionProcessor.VariablesLookup;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.ConditionExpression;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeLoopCharacteristics;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.zeebe.model.bpmn.traversal.ModelWalker;
import io.zeebe.model.bpmn.validation.ValidationVisitor;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.validation.ValidationResult;
import org.camunda.bpm.model.xml.validation.ValidationResults;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class ZeebeRuntimeValidationTest {

  public static final String INVALID_TIMER_START_EVENT_EXPRESSION_MESSAGE =
      "Expected a valid timer expression for start event, but encountered the following error: ";
  private static final String INVALID_EXPRESSION = "?!";
  private static final String INVALID_EXPRESSION_MESSAGE =
      "failed to parse expression '?!': [1.2] failure: end of input expected\n"
          + "\n"
          + "?!\n"
          + " ^";
  private static final String STATIC_EXPRESSION = "x";
  private static final String STATIC_EXPRESSION_MESSAGE =
      "Expected expression but found static value 'x'. An expression must start with '=' (e.g. '=x').";
  private static final String MISSING_EXPRESSION_MESSAGE = "Expected expression but not found.";
  private static final String MISSING_PATH_EXPRESSION_MESSAGE =
      "Expected path expression but not found.";
  private static final String INVALID_PATH_EXPRESSION = "a ? b";
  private static final String INVALID_PATH_EXPRESSION_MESSAGE =
      "Expected path expression 'a ? b' but doesn't match the pattern '[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*'.";
  private static final String VALID_TIMER_DURATION_EXPRESSION = "\"PT1H\"";
  public BpmnModelInstance modelInstance;

  @Parameter(0)
  public Object modelSource;

  @Parameter(1)
  public List<ExpectedValidationResult> expectedResults;

  @Parameters(name = "{index}: {1}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        // not a valid expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .exclusiveGateway()
            .sequenceFlowId("flow")
            .conditionExpression(INVALID_EXPRESSION)
            .endEvent()
            .done(),
        Arrays.asList(expect(ConditionExpression.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // static expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .exclusiveGateway()
            .sequenceFlowId("flow")
            .condition(STATIC_EXPRESSION)
            .endEvent()
            .done(),
        Arrays.asList(expect(ConditionExpression.class, STATIC_EXPRESSION_MESSAGE))
      },
      {
        // not a valid expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeInputExpression(INVALID_EXPRESSION, "foo"))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeInput.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // static expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeInput(STATIC_EXPRESSION, "bar"))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeInput.class, STATIC_EXPRESSION_MESSAGE))
      },
      {
        // empty expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeInput("", "bar"))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeInput.class, MISSING_EXPRESSION_MESSAGE))
      },
      {
        // empty path expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeInputExpression("foo", ""))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeInput.class, MISSING_PATH_EXPRESSION_MESSAGE))
      },
      {
        // invalid target expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeInputExpression("foo", INVALID_PATH_EXPRESSION))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeInput.class, INVALID_PATH_EXPRESSION_MESSAGE))
      },
      { // not a valid expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeOutputExpression(INVALID_EXPRESSION, "foo"))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeOutput.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // static expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeOutput(STATIC_EXPRESSION, "bar"))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeOutput.class, STATIC_EXPRESSION_MESSAGE))
      },
      {
        // empty expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeOutput("", "bar"))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeOutput.class, MISSING_EXPRESSION_MESSAGE))
      },
      {
        // invalid target expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeOutputExpression("foo", INVALID_PATH_EXPRESSION))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeOutput.class, INVALID_PATH_EXPRESSION_MESSAGE))
      },
      {
        // empty path expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", s -> s.zeebeOutputExpression("foo", ""))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeOutput.class, MISSING_PATH_EXPRESSION_MESSAGE))
      },
      {
        // correlation key expression is not supported
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("catch")
            .message(b -> b.name("message").zeebeCorrelationKeyExpression(INVALID_EXPRESSION))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeSubscription.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // static expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("catch")
            .message(b -> b.name("message").zeebeCorrelationKey(STATIC_EXPRESSION))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeSubscription.class, STATIC_EXPRESSION_MESSAGE))
      },
      {
        // correlation key expression is not supported
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("catch")
            .message(b -> b.name("message").zeebeCorrelationKeyExpression(INVALID_EXPRESSION))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeSubscription.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // static expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("catch")
            .message(b -> b.name("message").zeebeCorrelationKey(STATIC_EXPRESSION))
            .endEvent()
            .done(),
        Arrays.asList(expect(ZeebeSubscription.class, STATIC_EXPRESSION_MESSAGE))
      },
      {
        // input collection expression is not supported
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t -> t.multiInstance(m -> m.zeebeInputCollectionExpression(INVALID_EXPRESSION)))
            .done(),
        Arrays.asList(expect(ZeebeLoopCharacteristics.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // input collection expression is static
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task", t -> t.multiInstance(m -> m.zeebeInputCollection(STATIC_EXPRESSION)))
            .done(),
        Arrays.asList(expect(ZeebeLoopCharacteristics.class, STATIC_EXPRESSION_MESSAGE))
      },
      {
        // output element expression is not supported
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.multiInstance(
                        m ->
                            m.zeebeInputCollectionExpression("x")
                                .zeebeOutputElementExpression(INVALID_EXPRESSION)))
            .done(),
        Arrays.asList(expect(ZeebeLoopCharacteristics.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // output element  expression is static
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.multiInstance(
                        m ->
                            m.zeebeInputCollectionExpression("x")
                                .zeebeOutputElement(STATIC_EXPRESSION)))
            .done(),
        Arrays.asList(expect(ZeebeLoopCharacteristics.class, STATIC_EXPRESSION_MESSAGE))
      },
      {
        // invalid job type expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobTypeExpression(INVALID_EXPRESSION))
            .done(),
        Arrays.asList(expect(ZeebeTaskDefinition.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // invalid job retries expression
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task", t -> t.zeebeJobType("test").zeebeJobRetriesExpression(INVALID_EXPRESSION))
            .done(),
        Arrays.asList(expect(ZeebeTaskDefinition.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // output element expression is not supported
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.multiInstance(
                        m ->
                            m.zeebeInputCollectionExpression("foo")
                                .zeebeOutputCollection("bar")
                                .zeebeOutputElementExpression(INVALID_EXPRESSION)))
            .done(),
        Arrays.asList(expect(ZeebeLoopCharacteristics.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // process id expression is not supported
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessIdExpression(INVALID_EXPRESSION))
            .done(),
        Arrays.asList(expect(ZeebeCalledElement.class, INVALID_EXPRESSION_MESSAGE))
      },
      {
        // timer start event expression is not supported
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .timerWithCycleExpression(INVALID_EXPRESSION)
            .done(),
        Arrays.asList(
            expect(TimerEventDefinition.class, INVALID_EXPRESSION_MESSAGE),
            expect(
                StartEvent.class,
                INVALID_TIMER_START_EVENT_EXPRESSION_MESSAGE + INVALID_EXPRESSION_MESSAGE))
      },
      {
        // valid timer start event expression does not evaluate to correct timer type
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .timerWithCycleExpression(VALID_TIMER_DURATION_EXPRESSION)
            .done(),
        Arrays.asList(
            expect(
                StartEvent.class,
                INVALID_TIMER_START_EVENT_EXPRESSION_MESSAGE + "Repetition spec must start with R"))
      },
    };
  }

  private static ValidationResults validate(final BpmnModelInstance model) {
    final ModelWalker walker = new ModelWalker(model);
    final ExpressionLanguage expressionLanguage =
        ExpressionLanguageFactory.createExpressionLanguage();
    final VariablesLookup emptyLookup = (name, scopeKey) -> null;
    final var expressionProcessor = new ExpressionProcessor(expressionLanguage, emptyLookup);
    final ValidationVisitor visitor =
        new ValidationVisitor(
            ZeebeRuntimeValidators.getValidators(expressionLanguage, expressionProcessor));
    walker.walk(visitor);

    return visitor.getValidationResult();
  }

  @Before
  public void prepareModel() {
    if (modelSource instanceof BpmnModelInstance) {
      modelInstance = (BpmnModelInstance) modelSource;
    } else if (modelSource instanceof String) {
      final InputStream modelStream =
          ZeebeRuntimeValidationTest.class.getResourceAsStream((String) modelSource);
      modelInstance = Bpmn.readModelFromStream(modelStream);
    } else {
      throw new RuntimeException("Cannot convert parameter to bpmn model");
    }
  }

  @Test
  public void validateModel() {
    // when
    final ValidationResults results = validate(modelInstance);

    Bpmn.validateModel(modelInstance);

    // then
    final List<ExpectedValidationResult> unmatchedExpectations = new ArrayList<>(expectedResults);
    final List<ValidationResult> unmatchedResults =
        results.getResults().values().stream()
            .flatMap(l -> l.stream())
            .collect(Collectors.toList());

    match(unmatchedResults, unmatchedExpectations);

    if (!unmatchedResults.isEmpty() || !unmatchedExpectations.isEmpty()) {
      failWith(unmatchedExpectations, unmatchedResults);
    }
  }

  private void match(
      final List<ValidationResult> unmatchedResults,
      final List<ExpectedValidationResult> unmatchedExpectations) {
    final Iterator<ExpectedValidationResult> expectationIt = unmatchedExpectations.iterator();

    outerLoop:
    while (expectationIt.hasNext()) {
      final ExpectedValidationResult currentExpectation = expectationIt.next();
      final Iterator<ValidationResult> resultsIt = unmatchedResults.iterator();

      while (resultsIt.hasNext()) {
        final ValidationResult currentResult = resultsIt.next();
        if (currentExpectation.matches(currentResult)) {
          expectationIt.remove();
          resultsIt.remove();
          continue outerLoop;
        }
      }
    }
  }

  private void failWith(
      final List<ExpectedValidationResult> unmatchedExpectations,
      final List<ValidationResult> unmatchedResults) {
    final StringBuilder sb = new StringBuilder();
    sb.append("Not all expecations were matched by results (or vice versa)\n\n");
    describeUnmatchedExpectations(sb, unmatchedExpectations);
    sb.append("\n");
    describeUnmatchedResults(sb, unmatchedResults);
    fail(sb.toString());
  }

  private static void describeUnmatchedResults(
      final StringBuilder sb, final List<ValidationResult> results) {
    sb.append("Unmatched results:\n");
    results.forEach(
        e -> {
          sb.append(ExpectedValidationResult.toString(e));
          sb.append("\n");
        });
  }

  private static void describeUnmatchedExpectations(
      final StringBuilder sb, final List<ExpectedValidationResult> expectations) {
    sb.append("Unmatched expectations:\n");
    expectations.forEach(
        e -> {
          sb.append(e);
          sb.append("\n");
        });
  }
}
