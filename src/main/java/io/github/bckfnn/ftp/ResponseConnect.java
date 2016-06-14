package io.github.bckfnn.ftp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class ResponseConnect extends Response<ResponseConnect> {
	
	public ResponseConnect(Handler<AsyncResult<ResponseConnect>> handler) {
        super(handler);
    }

    @Override
	public void handle(FtpClient client) {
		if (!codeIs("220")) {
			fail("connect " + getCode());
		} else {
		    succes(this);
		}
	}
}
