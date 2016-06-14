package io.github.bckfnn.ftp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class CmdList extends Cmd<CmdList> {
	
	public CmdList(Handler<AsyncResult<CmdList>> handler) {
		this.handler = handler;
	}
	
	@Override
	public void send(FtpClient client) {
		client.write("LIST");
	}
	
	@Override
	public void recieve(FtpClient client) {
		if (code.equals("150")) {
			client.write(new CmdList(handler) {
				@Override
				public void send(FtpClient client) {
				}
				@Override
				public void recieve(FtpClient client) {
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
