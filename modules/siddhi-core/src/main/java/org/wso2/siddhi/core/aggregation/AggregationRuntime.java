/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.siddhi.core.aggregation;

import org.wso2.siddhi.core.config.SiddhiAppContext;
import org.wso2.siddhi.core.event.ComplexEventChunk;
import org.wso2.siddhi.core.event.state.MetaStateEvent;
import org.wso2.siddhi.core.event.state.StateEvent;
import org.wso2.siddhi.core.event.stream.MetaStreamEvent;
import org.wso2.siddhi.core.event.stream.StreamEvent;
import org.wso2.siddhi.core.exception.SiddhiAppCreationException;
import org.wso2.siddhi.core.exception.SiddhiAppRuntimeException;
import org.wso2.siddhi.core.executor.ExpressionExecutor;
import org.wso2.siddhi.core.executor.VariableExpressionExecutor;
import org.wso2.siddhi.core.query.input.stream.single.EntryValveExecutor;
import org.wso2.siddhi.core.query.input.stream.single.SingleStreamRuntime;
import org.wso2.siddhi.core.query.selector.attribute.aggregator.incremental.IncrementalExecutor;
import org.wso2.siddhi.core.table.Table;
import org.wso2.siddhi.core.util.collection.operator.CompiledCondition;
import org.wso2.siddhi.core.util.collection.operator.IncrementalAggregateCompileCondition;
import org.wso2.siddhi.core.util.collection.operator.MatchingMetaInfoHolder;
import org.wso2.siddhi.core.util.parser.ExpressionParser;
import org.wso2.siddhi.core.util.parser.MatcherParser;
import org.wso2.siddhi.core.util.parser.OperatorParser;
import org.wso2.siddhi.query.api.aggregation.TimePeriod;
import org.wso2.siddhi.query.api.aggregation.Within;
import org.wso2.siddhi.query.api.definition.AbstractDefinition;
import org.wso2.siddhi.query.api.definition.AggregationDefinition;
import org.wso2.siddhi.query.api.definition.Attribute;
import org.wso2.siddhi.query.api.expression.Expression;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.wso2.siddhi.core.util.SiddhiConstants.UNKNOWN_STATE;

/**
 * Aggregation runtime managing aggregation operations for aggregation definition
 */
public class AggregationRuntime {
    private final AggregationDefinition aggregationDefinition;
    private final Map<TimePeriod.Duration, IncrementalExecutor> incrementalExecutorMap;
    private final Map<TimePeriod.Duration, Table> aggregationTables;
    private final SiddhiAppContext siddhiAppContext;
    private final MetaStreamEvent internalMetaStreamEvent;
    private List<TimePeriod.Duration> incrementalDurations;
    private SingleStreamRuntime singleStreamRuntime;
    private EntryValveExecutor entryValveExecutor;
    private ExpressionExecutor perExpressionExecutor;
    private Map<TimePeriod.Duration, Object> inMemoryStoreMap = new HashMap<>();
    private List<ExpressionExecutor> baseExecutors;
    private List<ExpressionExecutor> outputExpressionExecutors;

    public AggregationRuntime(AggregationDefinition aggregationDefinition,
            Map<TimePeriod.Duration, IncrementalExecutor> incrementalExecutorMap,
            Map<TimePeriod.Duration, Table> aggregationTables, SingleStreamRuntime singleStreamRuntime,
            EntryValveExecutor entryValveExecutor, List<TimePeriod.Duration> incrementalDurations,
            SiddhiAppContext siddhiAppContext, List<ExpressionExecutor> baseExecutors,
            MetaStreamEvent internalMetaStreamEvent, List<ExpressionExecutor> outputExpressionExecutors) {
        this.aggregationDefinition = aggregationDefinition;
        this.incrementalExecutorMap = incrementalExecutorMap;
        this.aggregationTables = aggregationTables;
        this.incrementalDurations = incrementalDurations;
        this.siddhiAppContext = siddhiAppContext;
        this.singleStreamRuntime = singleStreamRuntime;
        this.entryValveExecutor = entryValveExecutor;
        this.baseExecutors = baseExecutors;
        this.internalMetaStreamEvent = internalMetaStreamEvent;
        this.outputExpressionExecutors = outputExpressionExecutors;
    }

    public Map<TimePeriod.Duration, IncrementalExecutor> getIncrementalExecutorMap() {
        return incrementalExecutorMap;
    }

    public Map<TimePeriod.Duration, Table> getAggregationTables() {
        return aggregationTables;
    }

    public AggregationDefinition getAggregationDefinition() {
        return aggregationDefinition;
    }

    public SiddhiAppContext getSiddhiAppContext() {
        return siddhiAppContext;
    }

    public SingleStreamRuntime getSingleStreamRuntime() {
        return singleStreamRuntime;
    }

    public EntryValveExecutor getEntryValveExecutor() {
        return entryValveExecutor;
    }

    public List<TimePeriod.Duration> getIncrementalDurations() {
        return incrementalDurations;
    }

    public StreamEvent find(StateEvent matchingEvent, CompiledCondition compiledCondition) {
        String perValueAsString = perExpressionExecutor.execute(matchingEvent).toString();
        TimePeriod.Duration perValue = TimePeriod.Duration.valueOf(perValueAsString.toUpperCase());
        if (!incrementalExecutorMap.keySet().contains(perValue)) {
            throw new SiddhiAppRuntimeException("The aggregate values for " + perValue.toString()
                    + " granularity cannot be provided since aggregation definition " + aggregationDefinition.getId()
                    + " does not contain " + perValue.toString() + " duration");
        } 
        return ((IncrementalAggregateCompileCondition) compiledCondition).find(matchingEvent, perValue,
                incrementalExecutorMap.get(perValue).isRoot());
    }

    public IncrementalAggregateCompileCondition compileExpression(Expression expression, Within within, Expression per,
            MatchingMetaInfoHolder matchingMetaInfoHolder, List<VariableExpressionExecutor> variableExpressionExecutors,
            Map<String, Table> tableMap, String queryName, SiddhiAppContext siddhiAppContext) {
        Map<TimePeriod.Duration, CompiledCondition> withinTableCompiledConditions = new HashMap<>();
        perExpressionExecutor = ExpressionParser.parseExpression(per, matchingMetaInfoHolder.getMetaStateEvent(),
                matchingMetaInfoHolder.getCurrentState(), tableMap, variableExpressionExecutors, siddhiAppContext,
                false, 0, queryName);
        if (perExpressionExecutor.getReturnType() != Attribute.Type.STRING) {
            throw new SiddhiAppCreationException("Query " + queryName + "'s per value expected a string but found "
                    + perExpressionExecutor.getReturnType());
        }
        // TODO: 8/11/17 add as a function 
        Expression withinExpression = Expression.incrementalWithinTime(within, Expression.variable("_TIMESTAMP"));
        for (Map.Entry<TimePeriod.Duration, Table> entry : aggregationTables.entrySet()) {
            CompiledCondition tableCompileCondition = entry.getValue().compileCondition(withinExpression,
                    aggregationTableMetaInfoHolder(matchingMetaInfoHolder, entry.getValue().getTableDefinition()),
                    siddhiAppContext, variableExpressionExecutors, tableMap, queryName);
            withinTableCompiledConditions.put(entry.getKey(), tableCompileCondition);
            // TODO: 8/11/17 do this in the init 
            inMemoryStoreMap.put(entry.getKey(), incrementalExecutorMap.get(entry.getKey()).getInMemoryStore());
        }
        CompiledCondition inMemoryStoreCompileCondition = OperatorParser.constructOperator(
                new ComplexEventChunk<>(true), withinExpression,
                aggregationTableMetaInfoHolder(matchingMetaInfoHolder,
                        ((Table) aggregationTables.values().toArray()[0]).getTableDefinition()),
                siddhiAppContext, variableExpressionExecutors, tableMap, queryName);
        // TODO: 8/11/17 optimize on and to retrieve group by
        CompiledCondition onCompiledCondition = OperatorParser.constructOperator(new ComplexEventChunk<>(true),
                expression, matchingMetaInfoHolder, siddhiAppContext, variableExpressionExecutors, tableMap, queryName);
        MetaStreamEvent finalOutputMetaStreamEvent = null;
        for (MetaStreamEvent metaStreamEvent : matchingMetaInfoHolder.getMetaStateEvent().getMetaStreamEvents()) {
            if (metaStreamEvent.getLastInputDefinition().getId()
                    .equals(matchingMetaInfoHolder.getStoreDefinition().getId())) {
                if (metaStreamEvent.getOutputData() == null || metaStreamEvent.getOutputData().isEmpty()) {
                    metaStreamEvent.getLastInputDefinition().getAttributeList().forEach(metaStreamEvent::addOutputData);
                } // TODO: 8/11/17 get from aggregate parser
                finalOutputMetaStreamEvent = metaStreamEvent;
            }
        }
        return new IncrementalAggregateCompileCondition(withinTableCompiledConditions, inMemoryStoreCompileCondition,
                onCompiledCondition, baseExecutors, aggregationTables, inMemoryStoreMap, internalMetaStreamEvent,
                incrementalDurations, outputExpressionExecutors, finalOutputMetaStreamEvent);
    }

    private static MatchingMetaInfoHolder aggregationTableMetaInfoHolder(MatchingMetaInfoHolder matchingMetaInfoHolder,
                                                                         AbstractDefinition tableDefinition) {
        MetaStreamEvent rightMetaStreamEventForTable = new MetaStreamEvent();
        rightMetaStreamEventForTable.setEventType(MetaStreamEvent.EventType.TABLE);
        rightMetaStreamEventForTable.addInputDefinition(tableDefinition);
        MetaStateEvent metaStateEvent = new MetaStateEvent(2);
        metaStateEvent.addEvent(matchingMetaInfoHolder.getMetaStateEvent()
                .getMetaStreamEvent(matchingMetaInfoHolder.getMatchingStreamEventIndex()));
        metaStateEvent.addEvent(rightMetaStreamEventForTable);
        return MatcherParser.constructMatchingMetaStateHolder(metaStateEvent, 0, tableDefinition, UNKNOWN_STATE);
    }
}
