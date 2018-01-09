/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.pulsar.functions.runtime.container;

import java.util.concurrent.CompletableFuture;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.functions.fs.FunctionStatus;
import org.apache.pulsar.functions.runtime.instance.JavaInstanceConfig;
import org.apache.pulsar.functions.runtime.functioncache.FunctionCacheManager;
import org.apache.pulsar.functions.runtime.instance.JavaInstanceRunnable;
import org.apache.pulsar.functions.stats.FunctionStats;

/**
 * A function container implemented using java thread.
 */
@Slf4j
class ThreadFunctionContainer implements FunctionContainer {

    // The thread that invokes the function
    @Getter
    private final Thread fnThread;

    // The id of the thread
    private final String id;

    private JavaInstanceRunnable javaInstanceRunnable;

    ThreadFunctionContainer(JavaInstanceConfig instanceConfig,
                            int maxBufferedTuples,
                            FunctionCacheManager fnCache,
                            ThreadGroup threadGroup,
                            String jarFile,
                            PulsarClient pulsarClient) {
        this.javaInstanceRunnable = new JavaInstanceRunnable(instanceConfig, maxBufferedTuples,
                fnCache, jarFile, pulsarClient);
        this.id = instanceConfig.getFunctionConfig().getFullyQualifiedName();
        this.fnThread = new Thread(threadGroup, javaInstanceRunnable, this.id);
    }

    @Override
    public String getId() {
        return id;
    }

    /**
     * The core logic that initialize the thread container and executes the function
     */
    @Override
    public void start() throws Exception {
        this.fnThread.start();
    }

    @Override
    public void join() throws InterruptedException {
        fnThread.join();
    }

    @Override
    public void stop() {
        javaInstanceRunnable.close();
        try {
            fnThread.join();
        } catch (InterruptedException e) {
            // ignore this
        }
    }

    @Override
    public CompletableFuture<FunctionStatus> getFunctionStatus() {
        FunctionStats stats = javaInstanceRunnable.getStats();
        FunctionStatus retval = new FunctionStatus();
        retval.setNumProcessed(stats.getTotalProcessed());
        retval.setNumSuccessfullyProcessed(stats.getTotalSuccessfullyProcessed());
        retval.setNumUserExceptions(stats.getTotalUserExceptions());
        retval.setNumSystemExceptions(stats.getTotalSystemExceptions());
        retval.setNumTimeouts(stats.getTotalTimeoutExceptions());
        return CompletableFuture.completedFuture(retval);
    }
}