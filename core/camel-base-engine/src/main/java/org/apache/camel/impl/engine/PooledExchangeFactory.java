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

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.*;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pooled {@link ExchangeFactory} that reuses {@link Exchange} instance from a pool.
 */
@Experimental
public class PooledExchangeFactory extends ServiceSupport
        implements ExchangeFactory, CamelContextAware, StaticService, NonManagedService {

    private static final Logger LOG = LoggerFactory.getLogger(PooledExchangeFactory.class);

    private final Consumer consumer;
    private final ReleaseOnCompletion onCompletion = new ReleaseOnCompletion();
    private final ConcurrentLinkedQueue<Exchange> pool = new ConcurrentLinkedQueue<>();
    private final AtomicLong acquired = new AtomicLong();
    private final AtomicLong created = new AtomicLong();
    private final AtomicLong released = new AtomicLong();
    private final AtomicLong discarded = new AtomicLong();

    private CamelContext camelContext;
    private boolean statisticsEnabled = true;

    public PooledExchangeFactory() {
        this.consumer = null;
    }

    private PooledExchangeFactory(Consumer consumer, CamelContext camelContext, boolean statisticsEnabled) {
        this.consumer = consumer;
        this.camelContext = camelContext;
        this.statisticsEnabled = statisticsEnabled;
    }

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
        return new PooledExchangeFactory(consumer, camelContext, statisticsEnabled);
    }

    public boolean isStatisticsEnabled() {
        return statisticsEnabled;
    }

    public void setStatisticsEnabled(boolean statisticsEnabled) {
        this.statisticsEnabled = statisticsEnabled;
    }

    @Override
    public Exchange create(boolean autoRelease) {
        Exchange exchange = pool.poll();
        if (exchange == null) {
            if (statisticsEnabled) {
                created.incrementAndGet();
            }
            // create a new exchange as there was no free from the pool
            exchange = new DefaultExchange(camelContext);
        } else {
            if (statisticsEnabled) {
                acquired.incrementAndGet();
            }
        }
        if (autoRelease) {
            // add on completion which will return the exchange when done
            exchange.adapt(ExtendedExchange.class).addOnCompletion(onCompletion);
        }
        return exchange;
    }

    @Override
    public Exchange create(Endpoint fromEndpoint, boolean autoRelease) {
        Exchange exchange = pool.poll();
        if (exchange == null) {
            if (statisticsEnabled) {
                created.incrementAndGet();
            }
            // create a new exchange as there was no free from the pool
            exchange = new DefaultExchange(fromEndpoint);
        } else {
            if (statisticsEnabled) {
                acquired.incrementAndGet();
            }
            // need to mark this exchange from the given endpoint
            exchange.adapt(ExtendedExchange.class).setFromEndpoint(fromEndpoint);
        }
        if (autoRelease) {
            // add on completion which will return the exchange when done
            exchange.adapt(ExtendedExchange.class).addOnCompletion(onCompletion);
        }
        return exchange;
    }

    @Override
    public void release(Exchange exchange) {
        // reset exchange before returning to pool
        try {
            // TODO: reset on pool as this then update created to be up-to-date
            ExtendedExchange ee = exchange.adapt(ExtendedExchange.class);
            ee.reset();

            // only release back in pool if reset was success
            if (statisticsEnabled) {
                released.incrementAndGet();
            }
            pool.offer(exchange);
        } catch (Exception e) {
            if (statisticsEnabled) {
                discarded.incrementAndGet();
            }
            LOG.debug("Error resetting exchange: {}. This exchange is discarded.", exchange);
        }
    }

    @Override
    protected void doStop() throws Exception {
        pool.clear();

        if (statisticsEnabled && consumer != null) {
            // only log if there is any usage
            boolean shouldLog = created.get() > 0 || acquired.get() > 0 || released.get() > 0 || discarded.get() > 0;
            if (shouldLog) {
                String uri = consumer.getEndpoint().getEndpointBaseUri();
                uri = URISupport.sanitizeUri(uri);

                LOG.info("PooledExchangeFactory ({}) usage [created: {}, reused: {}, released: {}, discarded: {}]",
                        uri, created.get(), acquired.get(), released.get(), discarded.get());
            }
        }

        created.set(0);
        acquired.set(0);
        released.set(0);
        discarded.set(0);
    }

    private final class ReleaseOnCompletion extends SynchronizationAdapter {

        @Override
        public int getOrder() {
            // should be very very last so set as highest value possible
            return Integer.MAX_VALUE;
        }

        @Override
        public void onDone(Exchange exchange) {
            if (exchange != null) {
                release(exchange);
            }
        }
    }

}
