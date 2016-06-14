package io.github.bckfnn.ftp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class CmdUser extends Cmd<CmdUser> {
	String user;
	
	public CmdUser(String user, Handler<AsyncResult<CmdUser>> handler) {
		this.user = user;
		this.handler = handler;
	}
	
	@Override
	public void send(FtpClient client) {
		client.write("USER " + user);
	}
	
	@Override
	public void recieve(FtpClient client) {
		succes(this);
	}
}
