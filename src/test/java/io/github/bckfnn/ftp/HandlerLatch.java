/*
 * Copyright 2016 Finn Bock
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.bckfnn.ftp;

import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.AsyncResultHandler;

public class HandlerLatch<T> implements AsyncResultHandler<T> {
    static Logger log = LoggerFactory.getLogger(HandlerLatch.class);

    private CountDownLatch latch = new CountDownLatch(1);
    private Throwable error;
    
    @Override
    public void handle(AsyncResult<T> event) {
        System.out.println(event.succeeded() + " " + event.result() + " " + event.cause());
        if (event.failed()) {
            log.error("caugth", event.cause());
            latch.countDown();
        }
    }

    public void countDown() {
        latch.countDown();
        log.debug("countDown");
    }

    public Void fail(Throwable error) {
    	this.error = error;
        latch.countDown();
        return null;
    }
    
    public <R> boolean failed(AsyncResult<R> asyncResult) {
    	if (asyncResult.failed()) {
    		this.error = asyncResult.cause();
    		latch.countDown();
    		return true;
    	}
    	return false;
    }
    
    public void await() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        log.debug("awaited");
        if (error != null) {
        	Assert.assertTrue(error.toString(), false);
        }
    }
}