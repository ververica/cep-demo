/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KTD, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ververica.cep.demo.dynamic;

import com.ververica.cep.demo.CepDemoException;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.cep.dynamic.impl.json.deserializer.ConditionSpecStdDeserializer;
import org.apache.flink.cep.dynamic.impl.json.deserializer.NodeSpecStdDeserializer;
import org.apache.flink.cep.dynamic.impl.json.deserializer.TimeStdDeserializer;
import org.apache.flink.cep.dynamic.impl.json.spec.ConditionSpec;
import org.apache.flink.cep.dynamic.impl.json.spec.GraphSpec;
import org.apache.flink.cep.dynamic.impl.json.spec.NodeSpec;
import org.apache.flink.cep.dynamic.processor.PatternProcessor;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.CollectionUtil;
import org.apache.flink.util.StringUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * The JDBC implementation of the {@link PeriodicPatternProcessorDiscoverer} that periodically discovers the rule
 * updates from the database by using JDBC.
 *
 * @param <T> Base type of the elements appearing in the pattern.
 */
public class JDBCPeriodicPatternProcessorDiscoverer<T> extends PeriodicPatternProcessorDiscoverer<T> {
    private static final Logger LOGGER = Logger.getLogger(JDBCPeriodicPatternProcessorDiscoverer.class.getName());

    private final String tableName;
    private final String jdbcUrl;
    private final List<PatternProcessor<T>> initialPatternProcessors;
    private final ClassLoader userCodeClassLoader;
    private Connection connection;
    private Statement statement;
    private ResultSet resultSet;
    private Map<String, Tuple4<String, Integer, String, String>> latestPatternProcessors;

    /**
     * Creates a new using the given initial {@link PatternProcessor} and the time interval how
     * often to check the pattern processor updates.
     *
     * @param jdbcUrl                  The JDBC url of the database.
     * @param jdbcDriver               The JDBC driver of the database.
     * @param initialPatternProcessors The list of the initial {@link PatternProcessor}.
     * @param intervalMillis           Time interval in milliseconds how often to check updates.
     */
    public JDBCPeriodicPatternProcessorDiscoverer(final String jdbcUrl, final String jdbcDriver, final String tableName,
            final ClassLoader userCodeClassLoader, @Nullable final List<PatternProcessor<T>> initialPatternProcessors,
            @Nullable final Long intervalMillis) throws Exception {
        super(intervalMillis);
        this.tableName = requireNonNull(tableName);
        this.initialPatternProcessors = initialPatternProcessors;
        this.userCodeClassLoader = userCodeClassLoader;
        this.jdbcUrl = jdbcUrl;
        Class.forName(requireNonNull(jdbcDriver));
        this.connection = DriverManager.getConnection(requireNonNull(jdbcUrl));
        this.statement = this.connection.createStatement();
    }

    @Override
    public boolean arePatternProcessorsUpdated() {
        if (latestPatternProcessors == null && !CollectionUtil.isNullOrEmpty(initialPatternProcessors)) {
            return true;
        }
        if (statement == null) {
            return false;
        }

        try {
            resultSet = statement.executeQuery("SELECT * FROM " + quote(tableName));
            Map<String, Tuple4<String, Integer, String, String>> currentPatternProcessors = new HashMap<>();
            while (resultSet.next()) {
                String id = resultSet.getString("id");
                if (currentPatternProcessors.containsKey(id) && currentPatternProcessors.get(id).f1 >= resultSet.getInt(
                        "version")) {
                    continue;
                }
                currentPatternProcessors.put(id,
                        new Tuple4<>(requireNonNull(resultSet.getString("id")), resultSet.getInt("version"),
                                requireNonNull(resultSet.getString("pattern")), resultSet.getString("function")));
            }
            if (latestPatternProcessors == null || isPatternProcessorUpdated(currentPatternProcessors)) {
                latestPatternProcessors = currentPatternProcessors;
                return true;
            } else {
                return false;
            }
        } catch (SQLException e) {
            LOGGER.warning(
                    () -> "Pattern processor discoverer failed to check rule changes, will try to recreate " +
                            "connection." + e.getMessage());
            try {
                statement.close();
                connection.close();
                connection = DriverManager.getConnection(requireNonNull(this.jdbcUrl));
                statement = connection.createStatement();
            } catch (SQLException ex) {
                throw new IllegalStateException(
                        "Failed to recreate connection to database with URL '" + this.jdbcUrl + "'.");
            }
        }
        return false;
    }

    private String quote(final String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<PatternProcessor<T>> getLatestPatternProcessors() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(
                new SimpleModule().addDeserializer(ConditionSpec.class, ConditionSpecStdDeserializer.INSTANCE)
                        .addDeserializer(Time.class, TimeStdDeserializer.INSTANCE)
                        .addDeserializer(NodeSpec.class, NodeSpecStdDeserializer.INSTANCE));

        return latestPatternProcessors.values().stream().map(patternProcessor -> {
            try {
                String patternStr = patternProcessor.f2;
                GraphSpec graphSpec = objectMapper.readValue(patternStr, GraphSpec.class);
                objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
                LOGGER.info(() -> getJsonString(objectMapper, graphSpec));
                PatternProcessFunction<T, ?> patternProcessFunction = null;
                String id = patternProcessor.f0;
                int version = patternProcessor.f1;
                if (!StringUtils.isNullOrWhitespaceOnly(patternProcessor.f3)) {
                    patternProcessFunction = (PatternProcessFunction<T, ?>) this.userCodeClassLoader.loadClass(
                            patternProcessor.f3).getConstructor(String.class, int.class).newInstance(id, version);
                }
                LOGGER.warning(() -> getJsonString(objectMapper, patternProcessor.f2));
                return new DefaultPatternProcessor<>(patternProcessor.f0, patternProcessor.f1, patternStr,
                        patternProcessFunction, this.userCodeClassLoader);
            } catch (Exception e) {
                LOGGER.severe(() -> "Get the latest pattern processors of the discoverer failure. - " + e.getMessage());
                e.printStackTrace();
            }
            return null;
        }).collect(Collectors.toList());
    }

    private static String getJsonString(final ObjectMapper objectMapper, final Object obj) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (final JsonProcessingException exception) {
            throw new CepDemoException("Failed to write as JSON string.", exception);
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
        try {
            if (resultSet != null) {
                resultSet.close();
            }
        } catch (SQLException e) {
            LOGGER.warning(
                    () -> "ResultSet of the pattern processor discoverer couldn't be closed - " + e.getMessage());
        } finally {
            resultSet = null;
        }
        try {
            if (statement != null) {
                statement.close();
            }
        } catch (SQLException e) {
            LOGGER.warning(
                    () -> "Statement of the pattern processor discoverer couldn't be closed - " + e.getMessage());
        } finally {
            statement = null;
        }
    }

    private boolean isPatternProcessorUpdated(
            Map<String, Tuple4<String, Integer, String, String>> currentPatternProcessors) {
        return latestPatternProcessors.size() != currentPatternProcessors.size() || !currentPatternProcessors.equals(
                latestPatternProcessors);
    }
}