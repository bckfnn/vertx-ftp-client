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
