package io.github.bckfnn.ftp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class CmdPass extends Cmd<CmdPass> {
	String passwd;
	
	public CmdPass(String passwd, Handler<AsyncResult<CmdPass>> handler) {
		this.passwd = passwd;
		this.handler = handler;
	}
	
	@Override
	public void send(FtpClient client) {
		client.write("PASS " + passwd);
	}
	
	@Override
	public void recieve(FtpClient client) {
		succes(this);
	}

	
}
