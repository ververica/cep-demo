package com.ververica.cep.demo.condition;

import org.apache.flink.cep.dynamic.condition.AviatorCondition;

import com.ververica.cep.demo.event.Event;

public class StartCondition extends AviatorCondition<Event> {

    public StartCondition(String expression) {
        super(expression);
    }
}
