package com.ververica.cep.demo.condition;

import com.ververica.cep.demo.event.Event;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;

public class MiddleCondition extends SimpleCondition<Event> {

    @Override
    public boolean filter(Event value) throws Exception {
        return value.getName().contains("middle");
    }
}
