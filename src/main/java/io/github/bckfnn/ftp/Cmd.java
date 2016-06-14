package io.github.bckfnn.ftp;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public abstract class Cmd<T extends Cmd<T>> {
	protected String code;
	protected List<String> messages = new ArrayList<>();
	protected Handler<AsyncResult<T>> handler;
	
	abstract void send(FtpClient client);

	abstract void recieve(FtpClient client);
	
	public void setCode(String code) {
		this.code = code;
	}
	
	public void addMessage(String message) {
		this.messages.add(message);
	}

	public void fail(String msg) {
		handler.handle(Future.failedFuture(new RuntimeException(msg)));
		
	}
	
	public void succes(T cmd) {
		handler.handle(Future.succeededFuture(cmd));
		
	}
}
