package com.ververica.cep.demo.condition;

import org.apache.flink.cep.pattern.conditions.SimpleCondition;

import com.ververica.cep.demo.event.Event;

public class EndCondition extends SimpleCondition<Event> {

    @Override
    public boolean filter(Event value) throws Exception {
        return value.getAction() != 1;
    }
}
