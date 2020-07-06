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

package io.siddhi.core.stream.input.source;

import io.siddhi.annotation.Example;
import io.siddhi.annotation.Extension;
import io.siddhi.core.config.SiddhiAppContext;
import io.siddhi.core.event.Event;
import io.siddhi.core.exception.MappingFailedException;
import io.siddhi.core.util.config.ConfigReader;
import io.siddhi.core.util.error.handler.model.ErroneousEvent;
import io.siddhi.core.util.transport.OptionHolder;
import io.siddhi.query.api.definition.StreamDefinition;
import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.List;

/**
 * Event Mapper implementation to handle pass-through scenario where user does not need to do any mapping.
 */
@Extension(
        name = "passThrough",
        namespace = "sourceMapper",
        description = "Pass-through mapper passed events (Event[]) through without any mapping or modifications.",
        examples = @Example(
                syntax = "@source(type='tcp', @map(type='passThrough'))\n" +
                        "define stream BarStream (symbol string, price float, volume long);",
                description = "In this example BarStream uses passThrough inputmapper which passes the " +
                        "received Siddhi event directly without any transformation into source."
        )
)
public class PassThroughSourceMapper extends SourceMapper {
    private static final Logger LOG = Logger.getLogger(PassThroughSourceMapper.class);

    @Override
    public void init(StreamDefinition streamDefinition, OptionHolder optionHolder, List<AttributeMapping>
            attributeMappingList, ConfigReader configReader, SiddhiAppContext siddhiAppContext) {
    }

    @Override
    public Class[] getSupportedInputEventClasses() {
        return new Class[]{Event.class, Event[].class, Object[].class};
    }

    @Override
    protected void mapAndProcess(Object eventObject, InputEventHandler inputEventHandler)
            throws MappingFailedException, InterruptedException {
        if (eventObject != null) {
            if (eventObject instanceof Event[]) {
                inputEventHandler.sendEvents((Event[]) eventObject);
            } else if (eventObject instanceof Event) {
                inputEventHandler.sendEvent((Event) eventObject);
            } else if (eventObject instanceof Object[]) {
                Event event = new Event(-1, (Object[]) eventObject);
                inputEventHandler.sendEvent(event);
            } else {
                throw new MappingFailedException(Collections.singletonList(
                        new ErroneousEvent(eventObject, "Event object must be either Event[], Event or Object[] " +
                                "but found " + eventObject.getClass().getCanonicalName())));
            }
        }
    }

    @Override
    protected boolean allowNullInTransportProperties() {
        return false;
    }
}
