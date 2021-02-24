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
package org.apache.camel.impl.engine;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.support.DefaultExchange;

/**
 * Default {@link ExchangeFactory} that creates a new {@link Exchange} instance.
 */
public final class DefaultExchangeFactory implements ExchangeFactory, CamelContextAware {

    private CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public ExchangeFactory newExchangeFactory(Consumer consumer) {
        // we just use a shared factory
        return this;
    }

    @Override
    public Exchange create(boolean autoRelease) {
        return new DefaultExchange(camelContext);
    }

    @Override
    public Exchange create(Endpoint fromEndpoint, boolean autoRelease) {
        return new DefaultExchange(fromEndpoint);
    }

    @Override
    public boolean isStatisticsEnabled() {
        return false;
    }

    @Override
    public void setStatisticsEnabled(boolean statisticsEnabled) {
        // not in use
    }

    @Override
    public int getCapacity() {
        return 0;
    }

    @Override
    public void setCapacity(int capacity) {
        // not in use
    }
}
