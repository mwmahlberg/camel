/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spi;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

/**
 * Factory for creating {@link Exchange}.
 *
 * The factory is pluggable which allows to use different strategies. The default factory will create a new
 * {@link Exchange} instance, and the pooled factory will pool and reuse exchanges.
 */
public interface ExchangeFactory {

    // TODO: new factory per consumer so there is no single race bottleneck
    // TODO: only use factory on route consumer to limit its scope to most significant impact
    // TODO: release from extended exchange without onCompletion (overhead)
    // TODO: reuse unit of work (expensive to create)

    /**
     * Service factory key.
     */
    String FACTORY = "exchange-factory";

    /**
     * Creates a new {@link ExchangeFactory} that is private for the given consumer.
     *
     * @param consumer the consumer that will use the created {@link ExchangeFactory}
     * @return the created factory.
     */
    ExchangeFactory newExchangeFactory(Consumer consumer);

    /**
     * Gets a new {@link Exchange}
     *
     * @param autoRelease whether to auto release the exchange when routing is complete via {@link UnitOfWork}
     */
    Exchange create(boolean autoRelease);

    /**
     * Gets a new {@link Exchange}
     *
     * @param autoRelease  whether to auto release the exchange when routing is complete via {@link UnitOfWork}
     * @param fromEndpoint the from endpoint
     */
    Exchange create(Endpoint fromEndpoint, boolean autoRelease);

    default void release(Exchange exchange) {
        // noop
    }
}
