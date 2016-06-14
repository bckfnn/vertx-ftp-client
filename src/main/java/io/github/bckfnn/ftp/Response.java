package io.github.bckfnn.ftp;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public abstract class Response<T extends Response<T>> {
    protected String code;
    protected List<String> messages = new ArrayList<>();
    final private Handler<AsyncResult<T>> handler;

    public Response(Handler<AsyncResult<T>> handler) {
        this.handler = handler;
    }

    public abstract void handle(FtpClient client);
    
    public void setCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
    
    public Handler<AsyncResult<T>> getHandler() {
        return handler;
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

    public void succes(T response) {
        handler.handle(Future.succeededFuture(response));

    }
}
