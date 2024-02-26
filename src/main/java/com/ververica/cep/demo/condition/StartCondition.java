package com.ververica.cep.demo.condition;

import com.ververica.cep.demo.event.Event;
import org.apache.flink.cep.dynamic.condition.AviatorCondition;

public class StartCondition extends AviatorCondition<Event> {

    public StartCondition(String expression) {
        super(expression);
    }
}
