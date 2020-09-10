/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package io.siddhi.core.util.error.handler.util;

import io.siddhi.core.event.ComplexEventChunk;
import io.siddhi.core.event.state.StateEvent;
import io.siddhi.core.table.CompiledUpdateSet;
import io.siddhi.core.util.collection.AddingStreamEventExtractor;
import io.siddhi.core.util.collection.operator.CompiledCondition;

import java.io.Serializable;

/**
 * This class will wrap necessary objects to replay an erreneous event
 */
public class ReplayableTableRecord implements Serializable {
    private ComplexEventChunk complexEventChunk;
    private CompiledCondition compiledCondition;
    private StateEvent stateEvent;
    private CompiledUpdateSet compiledUpdateSet;
    private AddingStreamEventExtractor addingStreamEventExtractor;

    /**
     * For onAddError
     * @param complexEventChunk
     */
    public ReplayableTableRecord(ComplexEventChunk complexEventChunk) {
        this.complexEventChunk = complexEventChunk;
    }

    /**
     * For onFindError and onContainsError
     * @param compiledCondition
     * @param stateEvent
     */
    public ReplayableTableRecord(CompiledCondition compiledCondition, StateEvent stateEvent) {
        this.compiledCondition = compiledCondition;
        this.stateEvent = stateEvent;
    }

    /**
     * For onDeleteError
     * @param complexEventChunk
     * @param compiledCondition
     */
    public ReplayableTableRecord(ComplexEventChunk complexEventChunk, CompiledCondition compiledCondition) {
        this.complexEventChunk = complexEventChunk;
        this.compiledCondition = compiledCondition;
    }

    /**
     * For onUpdateError
     * @param complexEventChunk
     * @param compiledCondition
     * @param compiledUpdateSet
     */
    public ReplayableTableRecord(ComplexEventChunk complexEventChunk, CompiledCondition compiledCondition,
                                 CompiledUpdateSet compiledUpdateSet) {
        this.complexEventChunk = complexEventChunk;
        this.compiledCondition = compiledCondition;
        this.compiledUpdateSet = compiledUpdateSet;
    }

    /**
     * For onUpdateOrAddError
     * @param complexEventChunk
     * @param compiledCondition
     * @param compiledUpdateSet
     * @param addingStreamEventExtractor
     */
    public ReplayableTableRecord(ComplexEventChunk complexEventChunk, CompiledCondition compiledCondition,
                                 CompiledUpdateSet compiledUpdateSet,
                                 AddingStreamEventExtractor addingStreamEventExtractor) {
        this.complexEventChunk = complexEventChunk;
        this.compiledCondition = compiledCondition;
        this.compiledUpdateSet = compiledUpdateSet;
        this.addingStreamEventExtractor = addingStreamEventExtractor;
    }

    public void setComplexEventChunk(ComplexEventChunk complexEventChunk) {
        this.complexEventChunk = complexEventChunk;
    }

    public void setCompiledCondition(CompiledCondition compiledCondition) {
        this.compiledCondition = compiledCondition;
    }

    public void setStateEvent(StateEvent stateEvent) {
        this.stateEvent = stateEvent;
    }

    public void setCompiledUpdateSet(CompiledUpdateSet compiledUpdateSet) {
        this.compiledUpdateSet = compiledUpdateSet;
    }

    public void setAddingStreamEventExtractor(AddingStreamEventExtractor addingStreamEventExtractor) {
        this.addingStreamEventExtractor = addingStreamEventExtractor;
    }

    public ComplexEventChunk getComplexEventChunk() {
        return complexEventChunk;
    }

    public CompiledCondition getCompiledCondition() {
        return compiledCondition;
    }

    public StateEvent getStateEvent() {
        return stateEvent;
    }

    public CompiledUpdateSet getCompiledUpdateSet() {
        return compiledUpdateSet;
    }

    public AddingStreamEventExtractor getAddingStreamEventExtractor() {
        return addingStreamEventExtractor;
    }
}
