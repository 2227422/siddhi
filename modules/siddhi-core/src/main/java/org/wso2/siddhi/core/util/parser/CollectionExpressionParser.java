/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.siddhi.core.util.parser;

import org.wso2.siddhi.core.config.ExecutionPlanContext;
import org.wso2.siddhi.core.event.stream.MetaStreamEvent;
import org.wso2.siddhi.core.executor.ConstantExpressionExecutor;
import org.wso2.siddhi.core.executor.ExpressionExecutor;
import org.wso2.siddhi.core.executor.VariableExpressionExecutor;
import org.wso2.siddhi.core.table.EventTable;
import org.wso2.siddhi.core.table.holder.IndexedEventHolder;
import org.wso2.siddhi.core.util.collection.executor.*;
import org.wso2.siddhi.core.util.collection.expression.*;
import org.wso2.siddhi.core.util.collection.operator.MatchingMetaStateHolder;
import org.wso2.siddhi.query.api.definition.Attribute;
import org.wso2.siddhi.query.api.expression.Expression;
import org.wso2.siddhi.query.api.expression.Variable;
import org.wso2.siddhi.query.api.expression.condition.*;
import org.wso2.siddhi.query.api.expression.constant.Constant;
import org.wso2.siddhi.query.api.expression.function.AttributeFunction;
import org.wso2.siddhi.query.api.expression.function.AttributeFunctionExtension;
import org.wso2.siddhi.query.api.expression.math.*;

import java.util.List;
import java.util.Map;

/**
 * Class to parse Expressions and create Expression executors.
 */
public class CollectionExpressionParser {

    /**
     * Parse the given expression and create the appropriate Executor by recursively traversing the expression
     *
     * @param expression              Expression to be parsed
     * @param matchingMetaStateHolder matchingMetaStateHolder
     * @param indexedEventHolder      indexed event holder
     * @return ExpressionExecutor
     */
    public static CollectionExpression parseCollectionExpression(Expression expression, MatchingMetaStateHolder matchingMetaStateHolder, IndexedEventHolder indexedEventHolder) {
        if (expression instanceof And) {

            CollectionExpression leftCollectionExpression = parseCollectionExpression(((And) expression).getLeftExpression(), matchingMetaStateHolder, indexedEventHolder);
            CollectionExpression rightCollectionExpression = parseCollectionExpression(((And) expression).getRightExpression(), matchingMetaStateHolder, indexedEventHolder);

            if (leftCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.NON &&
                    rightCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.NON) {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.NON);
            } else if (leftCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.EXHAUSTIVE &&
                    rightCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.EXHAUSTIVE) {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.EXHAUSTIVE);
            } else {
                return new AndCollectionExpression(expression, CollectionExpression.CollectionScope.OPTIMISED_RESULT_SET,
                        leftCollectionExpression, rightCollectionExpression);
            }
        } else if (expression instanceof Or) {
            CollectionExpression leftCollectionExpression = parseCollectionExpression(((Or) expression).getLeftExpression(), matchingMetaStateHolder, indexedEventHolder);
            CollectionExpression rightCollectionExpression = parseCollectionExpression(((Or) expression).getRightExpression(), matchingMetaStateHolder, indexedEventHolder);

            if (leftCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.NON &&
                    rightCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.NON) {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.NON);
            } else if (leftCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.EXHAUSTIVE ||
                    rightCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.EXHAUSTIVE) {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.EXHAUSTIVE);
            } else {
                return new OrCollectionExpression(expression, CollectionExpression.CollectionScope.OPTIMISED_RESULT_SET,
                        leftCollectionExpression, rightCollectionExpression);
            }
        } else if (expression instanceof Not) {
            CollectionExpression notCollectionExpression = parseCollectionExpression(((Not) expression).getExpression(), matchingMetaStateHolder, indexedEventHolder);

            if (notCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.EXHAUSTIVE) {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.EXHAUSTIVE);
            } else if (notCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.NON) {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.NON);
            } else if (notCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.INDEXED_ATTRIBUTE) {
                return new NotCollectionExpression(expression,
                        CollectionExpression.CollectionScope.INDEXED_RESULT_SET, notCollectionExpression);
            } else {
                return new NotCollectionExpression(expression,
                        CollectionExpression.CollectionScope.OPTIMISED_RESULT_SET, notCollectionExpression);
            }
        } else if (expression instanceof Compare) {
//            if (((Compare) expression).getOperator() == Compare.Operator.EQUAL) {

            CollectionExpression leftCollectionExpression = parseCollectionExpression(((Compare) expression).getLeftExpression(), matchingMetaStateHolder, indexedEventHolder);
            CollectionExpression rightCollectionExpression = parseCollectionExpression(((Compare) expression).getRightExpression(), matchingMetaStateHolder, indexedEventHolder);

            if (leftCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.NON &&
                    rightCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.NON) {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.NON);
            } else if (leftCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.INDEXED_ATTRIBUTE &&
                    rightCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.NON) {
                if (indexedEventHolder.isSupportedIndex(((AttributeCollectionExpression) leftCollectionExpression).getAttribute(),
                        ((Compare) expression).getOperator())) {
                    return new CompareCollectionExpression((Compare) expression,
                            CollectionExpression.CollectionScope.INDEXED_RESULT_SET, leftCollectionExpression, ((Compare) expression).getOperator(), rightCollectionExpression);
                } else {
                    return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.EXHAUSTIVE);
                }
            } else if (leftCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.NON &&
                    rightCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.INDEXED_ATTRIBUTE) {
                Compare.Operator operator = ((Compare) expression).getOperator();
                switch (operator) {
                    case LESS_THAN:
                        operator = Compare.Operator.GREATER_THAN;
                        break;
                    case GREATER_THAN:
                        operator = Compare.Operator.LESS_THAN;
                        break;
                    case LESS_THAN_EQUAL:
                        operator = Compare.Operator.GREATER_THAN_EQUAL;
                        break;
                    case GREATER_THAN_EQUAL:
                        operator = Compare.Operator.LESS_THAN_EQUAL;
                        break;
                    case EQUAL:
                        break;
                    case NOT_EQUAL:
                        break;
                }
                if (indexedEventHolder.isSupportedIndex(((AttributeCollectionExpression) rightCollectionExpression).getAttribute(),
                        operator)) {
                    return new CompareCollectionExpression((Compare) expression,
                            CollectionExpression.CollectionScope.INDEXED_RESULT_SET, rightCollectionExpression, operator, leftCollectionExpression);
                } else {
                    return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.EXHAUSTIVE);
                }
            } else {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.EXHAUSTIVE);
            }
        } else if (expression instanceof Constant) {
            return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.NON);
        } else if (expression instanceof Variable) {
            if (isCollectionVariable(matchingMetaStateHolder, (Variable) expression)) {
                if (indexedEventHolder.isAttributeIndexed(((Variable) expression).getAttributeName())) {
                    return new AttributeCollectionExpression(expression, ((Variable) expression).getAttributeName());
                } else {
                    return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.EXHAUSTIVE);
                }
            } else {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.NON);
            }
        } else if (expression instanceof Multiply) {
            CollectionExpression left = parseCollectionExpression(((Multiply) expression).getLeftValue(), matchingMetaStateHolder, indexedEventHolder);
            CollectionExpression right = parseCollectionExpression(((Multiply) expression).getRightValue(), matchingMetaStateHolder, indexedEventHolder);
            if (left.getCollectionScope() == CollectionExpression.CollectionScope.NON && right.getCollectionScope() == CollectionExpression.CollectionScope.NON) {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.NON);
            } else {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.EXHAUSTIVE);
            }
        } else if (expression instanceof Add) {
            CollectionExpression left = parseCollectionExpression(((Add) expression).getLeftValue(), matchingMetaStateHolder, indexedEventHolder);
            CollectionExpression right = parseCollectionExpression(((Add) expression).getRightValue(), matchingMetaStateHolder, indexedEventHolder);
            if (left.getCollectionScope() == CollectionExpression.CollectionScope.NON && right.getCollectionScope() == CollectionExpression.CollectionScope.NON) {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.NON);
            } else {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.EXHAUSTIVE);
            }
        } else if (expression instanceof Subtract) {
            CollectionExpression left = parseCollectionExpression(((Subtract) expression).getLeftValue(), matchingMetaStateHolder, indexedEventHolder);
            CollectionExpression right = parseCollectionExpression(((Subtract) expression).getRightValue(), matchingMetaStateHolder, indexedEventHolder);
            if (left.getCollectionScope() == CollectionExpression.CollectionScope.NON && right.getCollectionScope() == CollectionExpression.CollectionScope.NON) {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.NON);
            } else {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.EXHAUSTIVE);
            }
        } else if (expression instanceof Mod) {
            CollectionExpression left = parseCollectionExpression(((Mod) expression).getLeftValue(), matchingMetaStateHolder, indexedEventHolder);
            CollectionExpression right = parseCollectionExpression(((Mod) expression).getRightValue(), matchingMetaStateHolder, indexedEventHolder);
            if (left.getCollectionScope() == CollectionExpression.CollectionScope.NON && right.getCollectionScope() == CollectionExpression.CollectionScope.NON) {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.NON);
            } else {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.EXHAUSTIVE);
            }
        } else if (expression instanceof Divide) {
            CollectionExpression left = parseCollectionExpression(((Divide) expression).getLeftValue(), matchingMetaStateHolder, indexedEventHolder);
            CollectionExpression right = parseCollectionExpression(((Divide) expression).getRightValue(), matchingMetaStateHolder, indexedEventHolder);
            if (left.getCollectionScope() == CollectionExpression.CollectionScope.NON && right.getCollectionScope() == CollectionExpression.CollectionScope.NON) {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.NON);
            } else {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.EXHAUSTIVE);
            }
        } else if (expression instanceof AttributeFunctionExtension) {
            Expression[] innerExpressions = ((AttributeFunctionExtension) expression).getParameters();
            for (Expression aExpression : innerExpressions) {
                CollectionExpression collectionExpression = parseCollectionExpression(aExpression, matchingMetaStateHolder, indexedEventHolder);
                if (collectionExpression.getCollectionScope() != CollectionExpression.CollectionScope.NON) {
                    return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.EXHAUSTIVE);
                }
            }
            return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.NON);
        } else if (expression instanceof AttributeFunction) {
            Expression[] innerExpressions = ((AttributeFunction) expression).getParameters();
            for (Expression aExpression : innerExpressions) {
                CollectionExpression aCollectionExpression = parseCollectionExpression(aExpression, matchingMetaStateHolder, indexedEventHolder);
                if (aCollectionExpression.getCollectionScope() != CollectionExpression.CollectionScope.NON) {
                    return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.EXHAUSTIVE);
                }
            }
            return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.NON);
        } else if (expression instanceof In) {
            CollectionExpression inCollectionExpression = parseCollectionExpression(((In) expression).getExpression(), matchingMetaStateHolder, indexedEventHolder);
            if (inCollectionExpression.getCollectionScope() != CollectionExpression.CollectionScope.NON) {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.EXHAUSTIVE);
            }

            return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.NON);
        } else if (expression instanceof IsNull) {

            CollectionExpression nullCollectionExpression = parseCollectionExpression(((IsNull) expression).getExpression(),
                    matchingMetaStateHolder, indexedEventHolder);

            if (nullCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.NON) {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.NON);
            } else if (nullCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.INDEXED_ATTRIBUTE) {
                return new NullCollectionExpression(expression, CollectionExpression.CollectionScope.INDEXED_RESULT_SET,
                        ((AttributeCollectionExpression) nullCollectionExpression).getAttribute());
            } else {
                return new BasicCollectionExpression(expression, CollectionExpression.CollectionScope.EXHAUSTIVE);
            }
        }
        throw new UnsupportedOperationException(expression.toString() + " not supported!");
    }


    private static boolean isCollectionVariable(MatchingMetaStateHolder matchingMetaStateHolder, Variable variable) {
        if (variable.getStreamId() != null) {
            MetaStreamEvent CollectionStreamEvent = matchingMetaStateHolder.getMetaStateEvent().getMetaStreamEvent(matchingMetaStateHolder.getCandidateEventIndex());
            if (CollectionStreamEvent != null) {
                if ((CollectionStreamEvent.getInputReferenceId() != null && variable.getStreamId().equals(CollectionStreamEvent.getInputReferenceId())) ||
                        (CollectionStreamEvent.getLastInputDefinition().getId().equals(variable.getStreamId()))) {
//                    if (Arrays.asList(CollectionStreamEvent.getLastInputDefinition().getAttributeNameArray()).contains(indexAttribute)) {
                    return true;
//                    }
                }
            }
        }
        return false;
    }

    public static CollectionExecutor buildCollectionExecutor(CollectionExpression collectionExpression,
                                                             MatchingMetaStateHolder matchingMetaStateHolder,
                                                             List<VariableExpressionExecutor> variableExpressionExecutors,
                                                             Map<String, EventTable> eventTableMap,
                                                             ExecutionPlanContext executionPlanContext,
                                                             boolean isFirst) {
        if (collectionExpression instanceof AttributeCollectionExpression) {
            return new CompareCollectionExecutor(((AttributeCollectionExpression) collectionExpression).getAttribute(), Compare.Operator.EQUAL, new ConstantExpressionExecutor(true, Attribute.Type.BOOL));
        } else if (collectionExpression instanceof CompareCollectionExpression) {
            ExpressionExecutor valueExpressionExecutor = ExpressionParser.parseExpression(((CompareCollectionExpression) collectionExpression).getValueCollectionExpression().getExpression(),
                    matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(), eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0);
            AttributeCollectionExpression attributeCollectionExpression = ((AttributeCollectionExpression) ((CompareCollectionExpression) collectionExpression).getAttributeCollectionExpression());
            return new CompareCollectionExecutor(attributeCollectionExpression.getAttribute(), ((CompareCollectionExpression) collectionExpression).getOperator(), valueExpressionExecutor);
        } else if (collectionExpression instanceof NullCollectionExpression) {
            return new CompareCollectionExecutor(((NullCollectionExpression) collectionExpression).getAttribute(),
                    Compare.Operator.EQUAL, new ConstantExpressionExecutor(null, Attribute.Type.OBJECT));
        } else if (collectionExpression instanceof AndCollectionExpression) {

            CollectionExpression leftCollectionExpression = ((AndCollectionExpression) collectionExpression).getLeftCollectionExpression();
            CollectionExpression rightCollectionExpression = ((AndCollectionExpression) collectionExpression).getRightCollectionExpression();

            ExpressionExecutor expressionExecutor = null;
            CollectionExecutor aCollectionExecutor = null;
            ExhaustiveCollectionExecutor exhaustiveCollectionExecutor = null;
            CollectionExecutor leftCollectionExecutor;
            CollectionExecutor rightCollectionExecutor;
            switch (leftCollectionExpression.getCollectionScope()) {
                case NON:
                    switch (rightCollectionExpression.getCollectionScope()) {

                        case NON:
                            expressionExecutor = ExpressionParser.parseExpression(collectionExpression.getExpression(),
                                    matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(), eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0);
                            return new NonCollectionExecutor(expressionExecutor);
                        case INDEXED_ATTRIBUTE:
                        case INDEXED_RESULT_SET:
                        case OPTIMISED_RESULT_SET:
                        case EXHAUSTIVE:
                            expressionExecutor = ExpressionParser.parseExpression(leftCollectionExpression.getExpression(),
                                    matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(), eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0);
                            aCollectionExecutor = buildCollectionExecutor(rightCollectionExpression, matchingMetaStateHolder, variableExpressionExecutors, eventTableMap, executionPlanContext, isFirst);
                            return new NonAndCollectionExecutor(expressionExecutor, aCollectionExecutor, rightCollectionExpression.getCollectionScope());
                    }
                    break;
                case INDEXED_ATTRIBUTE:
                    switch (rightCollectionExpression.getCollectionScope()) {

                        case NON:
                            expressionExecutor = ExpressionParser.parseExpression(rightCollectionExpression.getExpression(),
                                    matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(), eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0);
                            aCollectionExecutor = buildCollectionExecutor(leftCollectionExpression, matchingMetaStateHolder, variableExpressionExecutors, eventTableMap, executionPlanContext, isFirst);
                            return new NonAndCollectionExecutor(expressionExecutor, aCollectionExecutor, rightCollectionExpression.getCollectionScope());
                        case INDEXED_ATTRIBUTE:
                        case INDEXED_RESULT_SET:
                        case OPTIMISED_RESULT_SET:
                            if (isFirst) {
                                aCollectionExecutor = new ExhaustiveCollectionExecutor(ExpressionParser.parseExpression(collectionExpression.getExpression(),
                                        matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(), eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0), matchingMetaStateHolder.getCandidateEventIndex());
                            }
                            leftCollectionExecutor = buildCollectionExecutor(leftCollectionExpression, matchingMetaStateHolder, variableExpressionExecutors, eventTableMap, executionPlanContext, false);
                            rightCollectionExecutor = buildCollectionExecutor(rightCollectionExpression, matchingMetaStateHolder, variableExpressionExecutors, eventTableMap, executionPlanContext, false);
                            return new AnyAndCollectionExecutor(leftCollectionExecutor, rightCollectionExecutor, aCollectionExecutor);
                        case EXHAUSTIVE:
                            if (isFirst) {
                                exhaustiveCollectionExecutor = new ExhaustiveCollectionExecutor(ExpressionParser.parseExpression(collectionExpression.getExpression(),
                                        matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(), eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0), matchingMetaStateHolder.getCandidateEventIndex());
                            }
                            leftCollectionExecutor = buildCollectionExecutor(leftCollectionExpression, matchingMetaStateHolder, variableExpressionExecutors, eventTableMap, executionPlanContext, isFirst);
                            return new CompareExhaustiveAndCollectionExecutor(leftCollectionExecutor, exhaustiveCollectionExecutor);
                    }
                    break;
                case INDEXED_RESULT_SET:
                case OPTIMISED_RESULT_SET:
                    switch (rightCollectionExpression.getCollectionScope()) {

                        case NON:
                            expressionExecutor = ExpressionParser.parseExpression(rightCollectionExpression.getExpression(),
                                    matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(), eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0);
                            aCollectionExecutor = buildCollectionExecutor(leftCollectionExpression, matchingMetaStateHolder, variableExpressionExecutors, eventTableMap, executionPlanContext, isFirst);
                            return new NonAndCollectionExecutor(expressionExecutor, aCollectionExecutor, rightCollectionExpression.getCollectionScope());

                        case INDEXED_ATTRIBUTE:
                            if (isFirst) {
                                aCollectionExecutor = new ExhaustiveCollectionExecutor(ExpressionParser.parseExpression(collectionExpression.getExpression(),
                                        matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(), eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0), matchingMetaStateHolder.getCandidateEventIndex());
                            }
                            leftCollectionExecutor = buildCollectionExecutor(leftCollectionExpression, matchingMetaStateHolder, variableExpressionExecutors, eventTableMap, executionPlanContext, false);
                            rightCollectionExecutor = buildCollectionExecutor(rightCollectionExpression, matchingMetaStateHolder, variableExpressionExecutors, eventTableMap, executionPlanContext, false);
                            return new AnyAndCollectionExecutor(rightCollectionExecutor, leftCollectionExecutor, aCollectionExecutor);
                        case INDEXED_RESULT_SET:
                        case OPTIMISED_RESULT_SET:
                            if (isFirst) {
                                aCollectionExecutor = new ExhaustiveCollectionExecutor(ExpressionParser.parseExpression(collectionExpression.getExpression(),
                                        matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(), eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0), matchingMetaStateHolder.getCandidateEventIndex());
                            }
                            leftCollectionExecutor = buildCollectionExecutor(leftCollectionExpression, matchingMetaStateHolder, variableExpressionExecutors, eventTableMap, executionPlanContext, false);
                            rightCollectionExecutor = buildCollectionExecutor(rightCollectionExpression, matchingMetaStateHolder, variableExpressionExecutors, eventTableMap, executionPlanContext, false);
                            return new AnyAndCollectionExecutor(leftCollectionExecutor, rightCollectionExecutor, aCollectionExecutor);
                        case EXHAUSTIVE:
                            if (isFirst) {
                                exhaustiveCollectionExecutor = new ExhaustiveCollectionExecutor(ExpressionParser.parseExpression(collectionExpression.getExpression(),
                                        matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(), eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0), matchingMetaStateHolder.getCandidateEventIndex());
                            }
                            leftCollectionExecutor = buildCollectionExecutor(leftCollectionExpression, matchingMetaStateHolder, variableExpressionExecutors, eventTableMap, executionPlanContext, isFirst);
                            return new CompareExhaustiveAndCollectionExecutor(leftCollectionExecutor, exhaustiveCollectionExecutor);
                    }
                    break;
                case EXHAUSTIVE:
                    switch (rightCollectionExpression.getCollectionScope()) {

                        case NON:
                            expressionExecutor = ExpressionParser.parseExpression(rightCollectionExpression.getExpression(),
                                    matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(), eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0);
                            aCollectionExecutor = buildCollectionExecutor(leftCollectionExpression, matchingMetaStateHolder, variableExpressionExecutors, eventTableMap, executionPlanContext, isFirst);
                            return new NonAndCollectionExecutor(expressionExecutor, aCollectionExecutor, rightCollectionExpression.getCollectionScope());

                        case INDEXED_ATTRIBUTE:
                        case INDEXED_RESULT_SET:
                        case OPTIMISED_RESULT_SET:
                            if (isFirst) {
                                exhaustiveCollectionExecutor = new ExhaustiveCollectionExecutor(ExpressionParser.parseExpression(collectionExpression.getExpression(),
                                        matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(), eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0), matchingMetaStateHolder.getCandidateEventIndex());
                            }
                            rightCollectionExecutor = buildCollectionExecutor(rightCollectionExpression, matchingMetaStateHolder, variableExpressionExecutors, eventTableMap, executionPlanContext, isFirst);
                            return new CompareExhaustiveAndCollectionExecutor(rightCollectionExecutor, exhaustiveCollectionExecutor);
                        case EXHAUSTIVE:
                            if (isFirst) {
                                expressionExecutor = ExpressionParser.parseExpression(collectionExpression.getExpression(),
                                        matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(),
                                        eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0);
                            }
                            return new ExhaustiveCollectionExecutor(expressionExecutor, matchingMetaStateHolder.getCandidateEventIndex());
                    }
                    break;
            }
        } else if (collectionExpression instanceof OrCollectionExpression) {
            CollectionExpression leftCollectionExpression = ((OrCollectionExpression) collectionExpression).getLeftCollectionExpression();
            CollectionExpression rightCollectionExpression = ((OrCollectionExpression) collectionExpression).getRightCollectionExpression();

            ExpressionExecutor expressionExecutor = null;
            CollectionExecutor aCollectionExecutor = null;
            CollectionExecutor leftCollectionExecutor;
            CollectionExecutor rightCollectionExecutor;
            if (leftCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.NON &&
                    rightCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.NON) {
                expressionExecutor = ExpressionParser.parseExpression(collectionExpression.getExpression(),
                        matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(), eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0);
                return new NonCollectionExecutor(expressionExecutor);
            } else if (leftCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.EXHAUSTIVE ||
                    rightCollectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.EXHAUSTIVE) {
                if (isFirst) {
                    expressionExecutor = ExpressionParser.parseExpression(collectionExpression.getExpression(),
                            matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(),
                            eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0);
                }
                return new ExhaustiveCollectionExecutor(expressionExecutor, matchingMetaStateHolder.getCandidateEventIndex());
            } else {
                if (isFirst) {
                    aCollectionExecutor = new ExhaustiveCollectionExecutor(ExpressionParser.parseExpression(collectionExpression.getExpression(),
                            matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(), eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0), matchingMetaStateHolder.getCandidateEventIndex());
                }
                leftCollectionExecutor = buildCollectionExecutor(leftCollectionExpression, matchingMetaStateHolder, variableExpressionExecutors, eventTableMap, executionPlanContext, isFirst);
                rightCollectionExecutor = buildCollectionExecutor(rightCollectionExpression, matchingMetaStateHolder, variableExpressionExecutors, eventTableMap, executionPlanContext, isFirst);
                return new OrCollectionExecutor(leftCollectionExecutor, rightCollectionExecutor, aCollectionExecutor);
            }
        } else if (collectionExpression instanceof NotCollectionExpression) {
            ExpressionExecutor expressionExecutor = null;
            switch (collectionExpression.getCollectionScope()) {

                case NON:
                    expressionExecutor = ExpressionParser.parseExpression(collectionExpression.getExpression(),
                            matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(), eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0);
                    return new NonCollectionExecutor(expressionExecutor);
                case INDEXED_ATTRIBUTE:
                case INDEXED_RESULT_SET:
                case OPTIMISED_RESULT_SET:
                    ExhaustiveCollectionExecutor exhaustiveCollectionExecutor = null;
                    if (isFirst) {
                        exhaustiveCollectionExecutor = new ExhaustiveCollectionExecutor(ExpressionParser.parseExpression(collectionExpression.getExpression(),
                                matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(), eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0), matchingMetaStateHolder.getCandidateEventIndex());
                    }
                    CollectionExecutor notCollectionExecutor = buildCollectionExecutor(((NotCollectionExpression) collectionExpression).getCollectionExpression(), matchingMetaStateHolder, variableExpressionExecutors, eventTableMap, executionPlanContext, isFirst);
                    return new NotCollectionExecutor(notCollectionExecutor, exhaustiveCollectionExecutor);

                case EXHAUSTIVE:
                    if (isFirst) {
                        expressionExecutor = ExpressionParser.parseExpression(collectionExpression.getExpression(),
                                matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(),
                                eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0);
                    }
                    return new ExhaustiveCollectionExecutor(expressionExecutor, matchingMetaStateHolder.getCandidateEventIndex());
            }
        } else { // Basic
            ExpressionExecutor expressionExecutor = null;

            if (collectionExpression.getCollectionScope() == CollectionExpression.CollectionScope.NON) {
                expressionExecutor = ExpressionParser.parseExpression(collectionExpression.getExpression(),
                        matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(),
                        eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0);
                return new NonCollectionExecutor(expressionExecutor);
            } else {// EXHAUSTIVE
                if (isFirst) {
                    expressionExecutor = ExpressionParser.parseExpression(collectionExpression.getExpression(),
                            matchingMetaStateHolder.getMetaStateEvent(), matchingMetaStateHolder.getDefaultStreamEventIndex(),
                            eventTableMap, variableExpressionExecutors, executionPlanContext, false, 0);
                }
                return new ExhaustiveCollectionExecutor(expressionExecutor, matchingMetaStateHolder.getCandidateEventIndex());
            }
        }
        throw new UnsupportedOperationException(collectionExpression.getClass().getName() + " not supported!");
    }

}
