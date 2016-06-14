package io.github.bckfnn.ftp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class CmdPasv extends Cmd<CmdPasv> {
	public int port;
	
	public CmdPasv(Handler<AsyncResult<CmdPasv>> handler) {
		this.handler = handler;
	}
	
	@Override
	public void send(FtpClient client) {
		client.write("PASV");
	}
	
	@Override
	public void recieve(FtpClient client) {
		if (code.equals("227")) {
			Pattern p = Pattern.compile(".*\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\).*");
			Matcher m = p.matcher(messages.get(0));
			if (m.matches()) {
				port = Integer.parseInt(m.group(5)) * 256 + Integer.parseInt(m.group(6));
				succes(this);
			} else {
				fail(messages.get(0));
			}
		} else {
			fail(this.toString());
		}
	}
}
