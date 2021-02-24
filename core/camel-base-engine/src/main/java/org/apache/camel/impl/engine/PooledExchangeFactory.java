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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.NonManagedService;
import org.apache.camel.PooledExchange;
import org.apache.camel.StaticService;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.support.DefaultPooledExchange;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pooled {@link ExchangeFactory} that reuses {@link Exchange} instance from a pool.
 */
public final class PooledExchangeFactory extends ServiceSupport
        implements ExchangeFactory, CamelContextAware, StaticService, NonManagedService {

    private static final Logger LOG = LoggerFactory.getLogger(PooledExchangeFactory.class);

    private final ReleaseOnDoneTask onDone = new ReleaseOnDoneTask();
    private final Consumer consumer;
    private BlockingQueue<Exchange> pool;
    private final AtomicLong acquired = new AtomicLong();
    private final AtomicLong created = new AtomicLong();
    private final AtomicLong released = new AtomicLong();
    private final AtomicLong discarded = new AtomicLong();

    private CamelContext camelContext;
    private int capacity = 100;
    private boolean statisticsEnabled;

    public PooledExchangeFactory() {
        this.consumer = null;
    }

    private PooledExchangeFactory(Consumer consumer, CamelContext camelContext, boolean statisticsEnabled) {
        this.consumer = consumer;
        this.camelContext = camelContext;
        this.statisticsEnabled = statisticsEnabled;
    }

    @Override
    protected void doBuild() throws Exception {
        this.pool = new ArrayBlockingQueue<>(capacity);
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

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
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
            exchange = createPooledExchange(null, autoRelease);
        } else {
            if (statisticsEnabled) {
                acquired.incrementAndGet();
            }
            // reset exchange for reuse
            PooledExchange ee = exchange.adapt(PooledExchange.class);
            ee.reset(System.currentTimeMillis());
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
            exchange = new DefaultPooledExchange(fromEndpoint);
        } else {
            if (statisticsEnabled) {
                acquired.incrementAndGet();
            }
            // reset exchange for reuse
            PooledExchange ee = exchange.adapt(PooledExchange.class);
            ee.reset(System.currentTimeMillis());
        }
        return exchange;
    }

    @Override
    public boolean release(Exchange exchange) {
        try {
            // done exchange before returning back to pool
            PooledExchange ee = exchange.adapt(PooledExchange.class);
            boolean force = !ee.isAutoRelease();
            ee.done(force);
            ee.onDone(null);

            // only release back in pool if reset was success
            boolean inserted = pool.offer(exchange);

            if (statisticsEnabled) {
                if (inserted) {
                    released.incrementAndGet();
                } else {
                    discarded.incrementAndGet();
                }
            }
            return inserted;
        } catch (Exception e) {
            if (statisticsEnabled) {
                discarded.incrementAndGet();
            }
            LOG.debug("Error resetting exchange: {}. This exchange is discarded.", exchange);
            return false;
        }
    }

    protected PooledExchange createPooledExchange(Endpoint fromEndpoint, boolean autoRelease) {
        PooledExchange answer;
        if (fromEndpoint != null) {
            answer = new DefaultPooledExchange(fromEndpoint);
        } else {
            answer = new DefaultPooledExchange(camelContext);
        }
        answer.setAutoRelease(autoRelease);
        if (autoRelease) {
            // the consumer will either always be in auto release mode or not, so its safe to initialize the task only once when the exchange is created
            answer.onDone(onDone);
        }
        return answer;
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

    private final class ReleaseOnDoneTask implements PooledExchange.OnDoneTask {

        @Override
        public void onDone(Exchange exchange) {
            release(exchange);
        }
    }

}
