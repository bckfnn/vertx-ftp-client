package io.github.bckfnn.ftp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class ResponsePass extends Response<ResponsePass> {
	
	public ResponsePass(Handler<AsyncResult<ResponsePass>> handler) {
		super(handler);
	}
	
	@Override
	public void handle(FtpClient client) {
		succes(this);
	}
}
