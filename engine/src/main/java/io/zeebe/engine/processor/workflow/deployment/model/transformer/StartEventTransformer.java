/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import io.zeebe.el.EvaluationResult;
import io.zeebe.el.ExpressionLanguage;
import io.zeebe.el.ResultType;
import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEventElement;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMessage;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public final class StartEventTransformer implements ModelElementTransformer<StartEvent> {

  @Override
  public Class<StartEvent> getType() {
    return StartEvent.class;
  }

  @Override
  public void transform(final StartEvent element, final TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableStartEvent startEvent =
        workflow.getElementById(element.getId(), ExecutableStartEvent.class);

    startEvent.setInterrupting(element.isInterrupting());

    if (element.getScope() instanceof FlowNode) {
      final FlowNode scope = (FlowNode) element.getScope();

      final ExecutableFlowElementContainer subprocess =
          workflow.getElementById(scope.getId(), ExecutableFlowElementContainer.class);
      subprocess.addStartEvent(startEvent);
    } else {
      // top-level start event
      workflow.addStartEvent(startEvent);
    }

    if (startEvent.isMessage()) {
      evaluateMessageNameExpression(startEvent, context);
    }

    bindLifecycle(startEvent);
  }

  private void bindLifecycle(final ExecutableCatchEventElement startEvent) {
    startEvent.bindLifecycleState(
        WorkflowInstanceIntent.EVENT_OCCURRED, BpmnStep.START_EVENT_EVENT_OCCURRED);
  }

  /**
   * Evaluates the message name expression of the message. For start events, there are no variables
   * available, so only static expressions or expressions based on literals are valid
   *
   * @param startEvent the start event; must not be {@code null}
   * @param context the transformation context; must not be {@code null}
   * @throws IllegalStateException thrown if either the evaluation failed or the result of the
   *     evaluation was not a String
   */
  private void evaluateMessageNameExpression(
      ExecutableStartEvent startEvent, TransformContext context) {
    final ExecutableMessage message = startEvent.getMessage();

    if (message.getMessageName().isEmpty()) {
      final ExpressionLanguage expressionLanguage = context.getExpressionLanguage();

      final EvaluationResult messageNameResult =
          expressionLanguage.evaluateExpression(
              message.getMessageNameExpression(), variable -> null);

      if (messageNameResult.isFailure()) {
        throw new IllegalStateException(
            String.format(
                "Error while evaluating '%s': %s",
                message.getMessageNameExpression(), messageNameResult.getFailureMessage()));
      } else if (messageNameResult.getType() == ResultType.STRING) {
        final String messageName = messageNameResult.getString();
        message.setMessageName(messageName);
      } else {
        throw new IllegalStateException(
            String.format(
                "Expected FEEL expression or static value of '%s' of type STRING, but was: %s",
                messageNameResult.getExpression(), messageNameResult.getType().name()));
      }
    }
  }
}
