package io.github.bckfnn.ftp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class ResponseRetr extends Response<ResponseRetr> {
	
	public ResponseRetr(Handler<AsyncResult<ResponseRetr>> handler) {
		super(handler);
	}
	
	@Override
	public void handle(FtpClient client) {
		if (code.equals("150")) {
			client.handle(new ResponseRetr(getHandler()) {
				@Override
				public void handle(FtpClient client) {
					if (code.equals("226")) {
						succes(this);
					} else {
					    fail(code);
					}
				}
			});
		} else {
			fail(code);
		}
	}

	
}
