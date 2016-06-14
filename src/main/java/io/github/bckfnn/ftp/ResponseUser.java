package io.github.bckfnn.ftp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class ResponseUser extends Response<ResponseUser> {
    public ResponseUser(Handler<AsyncResult<ResponseUser>> handler) {
        super(handler);
    }
	
    @Override
    public void handle(FtpClient client) {
        succes(this);
    }
}
