package io.github.bckfnn.ftp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class CmdConnect extends Cmd<CmdConnect> {
	
	public CmdConnect(Handler<AsyncResult<CmdConnect>> handler) {
		this.handler = handler;
	}
	
	@Override
	public void send(FtpClient client) {
		client.fail("cannot send on CmdConnect");
	}
	
	@Override
	public void recieve(FtpClient client) {
		if (!code.equals("220")) {
			fail("connect " + code);
		} else {
			succes(this);
		}
	}
}
