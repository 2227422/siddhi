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

package org.wso2.siddhi.core.util.collection.operator;

import org.wso2.siddhi.core.event.ComplexEvent;
import org.wso2.siddhi.core.event.ComplexEventChunk;
import org.wso2.siddhi.core.event.state.StateEvent;
import org.wso2.siddhi.core.event.stream.MetaStreamEvent;
import org.wso2.siddhi.core.event.stream.StreamEvent;
import org.wso2.siddhi.core.event.stream.StreamEventCloner;
import org.wso2.siddhi.core.event.stream.StreamEventPool;
import org.wso2.siddhi.core.executor.ExpressionExecutor;
import org.wso2.siddhi.core.query.selector.attribute.aggregator.incremental.BaseIncrementalValueStore;
import org.wso2.siddhi.core.table.Table;
import org.wso2.siddhi.core.util.IncrementalTimeConverterUtil;
import org.wso2.siddhi.query.api.aggregation.TimePeriod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Defines the logic to find a matching event from an incremental aggregator (retrieval from incremental aggregator),
 * based on the logical conditions defined herewith.
 */
public class IncrementalAggregateCompileCondition implements CompiledCondition {
    private Map<TimePeriod.Duration, CompiledCondition> withinTableCompiledConditions;
    private CompiledCondition inMemoryStoreCompileCondition;
    private CompiledCondition onCompiledCondition;
    private List<ExpressionExecutor> outputExpressionExecutors;
    private StreamEventPool streamEventPoolForInternalMeta;
    private StreamEventPool streamEventPoolForFinalMeta;
    private Map<TimePeriod.Duration, Table> aggregationTables;
    private Map<TimePeriod.Duration, Object> inMemoryStoreMap;
    private List<TimePeriod.Duration> incrementalDurations;
    private List<ExpressionExecutor> baseExecutors;
    private Map<Long, Map<String, BaseIncrementalValueStore>> inMemoryAggregateStoreMap = new HashMap<>();
    private BaseIncrementalValueStore baseIncrementalValueStore;
    private MetaStreamEvent internalMetaStreamEvent;
    private MetaStreamEvent finalOutputMetaStreamEvent;
    private final StreamEvent resetEvent;
    private final StreamEventCloner storeEventCloner;
    private final StreamEventCloner outputEventCloner;

    public IncrementalAggregateCompileCondition(Map<TimePeriod.Duration, CompiledCondition> withinTableCompiledConditions,
        CompiledCondition inMemoryStoreCompileCondition, CompiledCondition onCompiledCondition,
        List<ExpressionExecutor> baseExecutors, Map<TimePeriod.Duration, Table> aggregationTables,
        Map<TimePeriod.Duration, Object> inMemoryStoreMap, MetaStreamEvent internalMetaStreamEvent,
        List<TimePeriod.Duration> incrementalDurations, List<ExpressionExecutor> outputExpressionExecutors,
        MetaStreamEvent finalOutputMetaStreamEvent) {
        this.withinTableCompiledConditions = withinTableCompiledConditions;
        this.inMemoryStoreCompileCondition = inMemoryStoreCompileCondition;
        this.onCompiledCondition = onCompiledCondition;
        this.outputExpressionExecutors = outputExpressionExecutors;
        this.streamEventPoolForInternalMeta = new StreamEventPool(internalMetaStreamEvent, 10);
        this.streamEventPoolForFinalMeta = new StreamEventPool(finalOutputMetaStreamEvent, 10);
        this.aggregationTables = aggregationTables;
        this.inMemoryStoreMap = inMemoryStoreMap;
        this.incrementalDurations = incrementalDurations;
        this.internalMetaStreamEvent = internalMetaStreamEvent;
        this.finalOutputMetaStreamEvent = finalOutputMetaStreamEvent;
        this.resetEvent = streamEventPoolForInternalMeta.borrowEvent();
        this.resetEvent.setType(ComplexEvent.Type.RESET);
        this.storeEventCloner = new StreamEventCloner(internalMetaStreamEvent, streamEventPoolForInternalMeta);
        this.outputEventCloner = new StreamEventCloner(finalOutputMetaStreamEvent, streamEventPoolForFinalMeta);
        this.baseExecutors = baseExecutors;
        this.baseIncrementalValueStore = new BaseIncrementalValueStore(-1, baseExecutors);
    }

    @Override
    public CompiledCondition cloneCompiledCondition(String key) {
        Map<TimePeriod.Duration, CompiledCondition> copyOfWithinTableCompiledConditions = new HashMap<>();
        List<ExpressionExecutor> newBaseExecutors = new ArrayList<>();
        List<ExpressionExecutor> newOutputExpressionExecutors = new ArrayList<>();
        for (Map.Entry<TimePeriod.Duration, CompiledCondition> entry : withinTableCompiledConditions.entrySet()) {
            copyOfWithinTableCompiledConditions.put(entry.getKey(), entry.getValue().cloneCompiledCondition(key));
        }
        newBaseExecutors.addAll(baseExecutors.stream().map(baseExecutor ->
                baseExecutor.cloneExecutor(key)).collect(Collectors.toList()));
        newOutputExpressionExecutors.addAll(outputExpressionExecutors.stream().map(outputExpressionExecutor ->
                outputExpressionExecutor.cloneExecutor(key)).collect(Collectors.toList()));
        return new IncrementalAggregateCompileCondition(copyOfWithinTableCompiledConditions,
                inMemoryStoreCompileCondition.cloneCompiledCondition(key),
                onCompiledCondition.cloneCompiledCondition(key), newBaseExecutors, aggregationTables,
                inMemoryStoreMap, internalMetaStreamEvent, incrementalDurations, newOutputExpressionExecutors,
                finalOutputMetaStreamEvent);
    }

    public StreamEvent find(StateEvent matchingEvent, TimePeriod.Duration perValue,
                            boolean perEqualsRootExecutorDuration, BaseIncrementalValueStore newestInMemoryEvent,
                            BaseIncrementalValueStore oldestInMemoryEvent) {
        ComplexEventChunk<StreamEvent> complexEventChunkToHoldMatches = new ComplexEventChunk<>(true);
        // TODO: 8/11/17 break long return
        StreamEvent matchFromPersistedEvents = aggregationTables.get(perValue).find(matchingEvent,
                withinTableCompiledConditions.get(perValue));
        complexEventChunkToHoldMatches.add(matchFromPersistedEvents);

        //Optimization step.
        //Get the newest and oldest events from in-memory (running) aggregates, and
        //check whether at least one of those events is within the given time range. If it's not the case,
        // there's no need to iterate through in-memory data.
        if(requiresProcessingInMemoryData(newestInMemoryEvent, oldestInMemoryEvent, matchingEvent)) {
            aggregateInMemoryData(inMemoryStoreMap.get(perValue), perValue);

            if (!perEqualsRootExecutorDuration) {
                // If the 'per' is for root executor, there would be no executors prior to that
                for (int i = perValue.ordinal() - 1; i >= incrementalDurations.get(0).ordinal(); i--) {
                    TimePeriod.Duration duration = TimePeriod.Duration.values()[i];
                    aggregateInMemoryData(inMemoryStoreMap.get(duration), perValue);
                }
            }// TODO: 8/11/17 add thrown error messages to my error message (e.getMessage)
            ComplexEventChunk<StreamEvent> processedInMemoryEventChunk = new ComplexEventChunk<>(true);
            for (Map.Entry<Long, Map<String, BaseIncrementalValueStore>> timeBucketEntry :
                    inMemoryAggregateStoreMap.entrySet()) {
                for (Map.Entry<String, BaseIncrementalValueStore> groupByEntry : timeBucketEntry.getValue().entrySet()) {
                    processedInMemoryEventChunk.add(createStreamEvent(groupByEntry.getValue()));
                }
            }
            StreamEvent matchFromInMemory = ((Operator) inMemoryStoreCompileCondition).find(matchingEvent,
                    processedInMemoryEventChunk, storeEventCloner);
            complexEventChunkToHoldMatches.add(matchFromInMemory); // TODO: 8/15/17 check this
            inMemoryAggregateStoreMap.clear();
        }
        return executeOnCompileCondition(matchingEvent, complexEventChunkToHoldMatches);
    }

    private void aggregateInMemoryData(Object inMemoryStore, TimePeriod.Duration perValue) {
        BaseIncrementalValueStore baseIncrementalValueStore; // TODO: 8/11/17 convert method in baseIncrementalValueStore. Extend store to have one format .have interface
        // TODO: 8/11/17 or move to 3 methods
        long timeBucket;
        String groupByKey;
        StreamEvent streamEvent;
        if (inMemoryStore instanceof ArrayList) {
            ArrayList inMemoryStoreElements = (ArrayList) inMemoryStore;
            for (int i = 0; i < inMemoryStoreElements.size(); i++) {
                if (inMemoryStoreElements.get(i) instanceof BaseIncrementalValueStore) {
                    // inMemory store is baseIncrementalValueStoreList (no group by, buffer > 0)
                    baseIncrementalValueStore = (BaseIncrementalValueStore) inMemoryStoreElements.get(i);
                    timeBucket = IncrementalTimeConverterUtil.getStartTimeOfAggregates(
                            baseIncrementalValueStore.getTimestamp(), perValue);
                    groupByKey = null;
                    // TODO: 8/11/17 add logs wherever possible
                    streamEvent = createStreamEvent(baseIncrementalValueStore);
                    updateInMemoryAggregateStoreMap(timeBucket, groupByKey, streamEvent);
                } else { // inMemory store is baseIncrementalValueGroupByStoreList (group by, buffer > 0)
                    assert inMemoryStoreElements.get(i) instanceof Map;
                    Map inMemoryMap = (Map) inMemoryStoreElements.get(i);
                    for (Object entry : inMemoryMap.entrySet()) {
                        baseIncrementalValueStore = (BaseIncrementalValueStore) ((Map.Entry) entry).getValue();
                        timeBucket = IncrementalTimeConverterUtil.getStartTimeOfAggregates(
                                baseIncrementalValueStore.getTimestamp(), perValue);
                        groupByKey = (String) ((Map.Entry) entry).getKey();
                        streamEvent = createStreamEvent(baseIncrementalValueStore);
                        updateInMemoryAggregateStoreMap(timeBucket, groupByKey, streamEvent);
                    }
                }
            }
        } else if (inMemoryStore instanceof Map) {
            // inMemory store is baseIncrementalValueStoreMap (group by, buffer == 0)
            Map inMemoryMap = (Map) inMemoryStore;
            for (Object entry : inMemoryMap.entrySet()) {
                baseIncrementalValueStore = (BaseIncrementalValueStore) ((Map.Entry) entry).getValue();
                timeBucket = IncrementalTimeConverterUtil.getStartTimeOfAggregates(
                        baseIncrementalValueStore.getTimestamp(), perValue);
                groupByKey = (String) ((Map.Entry) entry).getKey();
                streamEvent = createStreamEvent(baseIncrementalValueStore);
                updateInMemoryAggregateStoreMap(timeBucket, groupByKey, streamEvent);
            }
        } else {
            // inMemory store is baseIncrementalValueStore (no group by, buffer == 0)
            baseIncrementalValueStore = (BaseIncrementalValueStore) inMemoryStore;
            timeBucket = IncrementalTimeConverterUtil.getStartTimeOfAggregates(
                    baseIncrementalValueStore.getTimestamp(), perValue);
            groupByKey = null;
            streamEvent = createStreamEvent(baseIncrementalValueStore);
            updateInMemoryAggregateStoreMap(timeBucket, groupByKey, streamEvent);
        }
    }

    private StreamEvent createStreamEvent(BaseIncrementalValueStore aBaseIncrementalValueStore) {
        // These borrowed events have the same structure as table data
        StreamEvent streamEvent = streamEventPoolForInternalMeta.borrowEvent();
        streamEvent.setTimestamp(aBaseIncrementalValueStore.getTimestamp());
        aBaseIncrementalValueStore.setValue(aBaseIncrementalValueStore.getTimestamp(), 0);
        streamEvent.setOutputData(aBaseIncrementalValueStore.getValues()); // TODO: 8/11/17 dont write like this
        return streamEvent;
    }

    private void process(StreamEvent streamEvent, BaseIncrementalValueStore baseIncrementalValueStore) {
        List<ExpressionExecutor> expressionExecutors = baseIncrementalValueStore.getExpressionExecutors();
        for (int i = 0; i < expressionExecutors.size(); i++) { // keeping timestamp value location as null
            ExpressionExecutor expressionExecutor = expressionExecutors.get(i);
            baseIncrementalValueStore.setValue(expressionExecutor.execute(streamEvent), i + 1);
        }
        baseIncrementalValueStore.setProcessed(true);
    }

    private void updateInMemoryAggregateStoreMap(long timeBucket, String groupByKey, StreamEvent streamEvent) {
        if (inMemoryAggregateStoreMap.get(timeBucket) != null) {
            if (inMemoryAggregateStoreMap.get(timeBucket).get(groupByKey) != null) {
                process(streamEvent, inMemoryAggregateStoreMap.get(timeBucket).get(groupByKey));
            } else {
                String key = (groupByKey != null) ? (((Long)timeBucket).toString()).concat(groupByKey) :
                        ((Long)timeBucket).toString();
                BaseIncrementalValueStore baseIncrementalValueStore =
                        this.baseIncrementalValueStore.cloneStore(key, timeBucket);
                inMemoryAggregateStoreMap.get(timeBucket).put(groupByKey, baseIncrementalValueStore);
                process(streamEvent, baseIncrementalValueStore);
            }
        } else {
            String key = (groupByKey != null) ? (((Long)timeBucket).toString()).concat(groupByKey) :
                    ((Long)timeBucket).toString();
            BaseIncrementalValueStore baseIncrementalValueStore =
                    this.baseIncrementalValueStore.cloneStore(key, timeBucket);
            Map<String, BaseIncrementalValueStore> baseIncrementalValueGroupByStore = new HashMap<>();
            baseIncrementalValueGroupByStore.put(groupByKey, baseIncrementalValueStore);
            inMemoryAggregateStoreMap.put(timeBucket, baseIncrementalValueGroupByStore);
            process(streamEvent, baseIncrementalValueStore);
        }
    }

    private StreamEvent executeOnCompileCondition(StateEvent matchingEvent,
                                                  ComplexEventChunk<StreamEvent> complexEventChunkToHoldMatches) {
        ComplexEventChunk<StreamEvent> finalComplexEventChunk = new ComplexEventChunk<>(true);
        while (complexEventChunkToHoldMatches.hasNext()) {
            StreamEvent streamEvent = complexEventChunkToHoldMatches.next();
            StreamEvent newStreamEvent = streamEventPoolForFinalMeta.borrowEvent();
            Object outputData[] = new Object[newStreamEvent.getOutputData().length];
            for (int i = 0; i < outputExpressionExecutors.size(); i++) {
                outputData[i] = outputExpressionExecutors.get(i).execute(streamEvent);
            }
            newStreamEvent.setTimestamp(streamEvent.getTimestamp());
            newStreamEvent.setOutputData(outputData);
            finalComplexEventChunk.add(newStreamEvent);
        }
        for (ExpressionExecutor expressionExecutor : outputExpressionExecutors) {
            expressionExecutor.execute(resetEvent);
        }
        return ((Operator) onCompiledCondition).find(matchingEvent, finalComplexEventChunk, outputEventCloner);
    }

    private boolean requiresProcessingInMemoryData(BaseIncrementalValueStore newestInMemoryEvent,
                                                   BaseIncrementalValueStore oldestInMemoryEvent,
                                                   StateEvent matchingEvent) {
        ComplexEventChunk<StreamEvent> newestAndOldestEventChunk = new ComplexEventChunk<>(true);
        if (newestInMemoryEvent == null && oldestInMemoryEvent == null) {
            return false;
        }
        if (newestInMemoryEvent != null) {
            newestAndOldestEventChunk.add(createStreamEvent(newestInMemoryEvent));
        }
        if (oldestInMemoryEvent != null) {
            newestAndOldestEventChunk.add(createStreamEvent(oldestInMemoryEvent));
        }
        return ((Operator) inMemoryStoreCompileCondition).find(matchingEvent, newestAndOldestEventChunk,
                storeEventCloner) != null;

    }
}
