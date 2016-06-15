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

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public class Response {
    protected String code;
    protected List<String> messages = new ArrayList<>();
    Handler<AsyncResult<Response>> handler;
    
    public Response(Handler<AsyncResult<Response>> handler) {
        this.handler = handler;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
    
    public void addMessage(String message) {
        this.messages.add(message);
    }

    public boolean codeIs(String value) {
        return code.equals(value);
    }

    public void fail(String msg) {
        handler.handle(Future.failedFuture(new RuntimeException(msg)));
    }

    public void fail() {
        handler.handle(Future.failedFuture(new RuntimeException(code + " " + messages.get(0))));
    }

    public void succes() {
        handler.handle(Future.succeededFuture(this));
    }
}
