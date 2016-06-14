package io.github.bckfnn.ftp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class ResponseList extends Response<ResponseList> {
	
	public ResponseList(Handler<AsyncResult<ResponseList>> handler) {
		super(handler);
	}
	
	@Override
	public void handle(FtpClient client) {
		if (code.equals("150")) {
			client.handle(new ResponseList(getHandler()) {
				@Override
				public void handle(FtpClient client) {
					if (code.equals("226")) {
						succes(this);
					}
				}
			});
		} else {
			fail(code);
		}
	}

	
}
