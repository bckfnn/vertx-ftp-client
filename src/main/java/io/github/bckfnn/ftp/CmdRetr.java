package io.github.bckfnn.ftp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class CmdRetr extends Cmd<CmdRetr> {
	String file;
	
	public CmdRetr(String file, Handler<AsyncResult<CmdRetr>> handler) {
		this.file = file;
		this.handler = handler;
	}
	
	@Override
	public void send(FtpClient client) {
		client.write("RETR " + file);
	}
	
	@Override
	public void recieve(FtpClient client) {
		if (code.equals("150")) {
			client.write(new CmdRetr(null, handler) {
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
