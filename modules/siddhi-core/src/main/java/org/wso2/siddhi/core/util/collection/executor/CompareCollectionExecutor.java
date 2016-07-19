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

package org.wso2.siddhi.core.util.collection.executor;

import org.wso2.siddhi.core.event.ComplexEventChunk;
import org.wso2.siddhi.core.event.state.StateEvent;
import org.wso2.siddhi.core.event.stream.StreamEvent;
import org.wso2.siddhi.core.event.stream.StreamEventCloner;
import org.wso2.siddhi.core.executor.ExpressionExecutor;
import org.wso2.siddhi.core.table.holder.IndexedEventHolder;
import org.wso2.siddhi.query.api.expression.condition.Compare;

import java.util.Collection;

public class CompareCollectionExecutor implements CollectionExecutor {


    private ExpressionExecutor expressionExecutor;
    private int candidateEventIndex;
    private final String attribute;
    private final Compare.Operator operator;
    private final ExpressionExecutor valueExpressionExecutor;

    public CompareCollectionExecutor(ExpressionExecutor expressionExecutor, int candidateEventIndex, String attribute, Compare.Operator operator, ExpressionExecutor valueExpressionExecutor) {
        this.expressionExecutor = expressionExecutor;
        this.candidateEventIndex = candidateEventIndex;

        this.attribute = attribute;
        this.operator = operator;
        this.valueExpressionExecutor = valueExpressionExecutor;
    }

    public StreamEvent find(StateEvent matchingEvent, IndexedEventHolder indexedEventHolder, StreamEventCloner candidateEventCloner) {

        ComplexEventChunk<StreamEvent> returnEventChunk = new ComplexEventChunk<StreamEvent>(false);
        Collection<StreamEvent> candidateEventSet = findEvents(matchingEvent, indexedEventHolder);

        if (candidateEventSet == null) {
            Collection<StreamEvent> candidateEvents = indexedEventHolder.getAllEvents();
            for (StreamEvent candidateEvent : candidateEvents) {
                matchingEvent.setEvent(candidateEventIndex, candidateEvent);
                if ((Boolean) expressionExecutor.execute(matchingEvent)) {
                    if (candidateEventCloner != null) {
                        returnEventChunk.add(candidateEventCloner.copyStreamEvent(candidateEvent));
                    } else {
                        returnEventChunk.add(candidateEvent);
                    }
                }
                matchingEvent.setEvent(candidateEventIndex, null);
            }
            return returnEventChunk.getFirst();
        } else {
            for (StreamEvent candidateEvent : candidateEventSet) {
                if (candidateEventCloner != null) {
                    returnEventChunk.add(candidateEventCloner.copyStreamEvent(candidateEvent));
                } else {
                    returnEventChunk.add(candidateEvent);
                }
            }
            return returnEventChunk.getFirst();
        }
    }

    public Collection<StreamEvent> findEvents(StateEvent matchingEvent, IndexedEventHolder indexedEventHolder) {
        if (operator == Compare.Operator.NOT_EQUAL) {
            return null;
        }
        return indexedEventHolder.findEvents(attribute, operator, valueExpressionExecutor.execute(matchingEvent));
    }

    @Override
    public boolean contains(StateEvent matchingEvent, IndexedEventHolder indexedEventHolder) {
        return indexedEventHolder.containsEventSet(attribute, operator, valueExpressionExecutor.execute(matchingEvent));
    }

    @Override
    public void delete(StateEvent deletingEvent, IndexedEventHolder indexedEventHolder) {
        indexedEventHolder.delete(attribute, operator, valueExpressionExecutor.execute(deletingEvent));
    }

}
