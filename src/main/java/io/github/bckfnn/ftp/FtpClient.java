package io.github.bckfnn.ftp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.streams.Pump;

public class FtpClient {
	private static Logger log = LoggerFactory.getLogger(FtpClient.class);
	
	private Vertx vertx;
	private String host;
	private int port;	
	private NetClient client;
	private NetSocket socket;
	private Handler<Void> endHandler = $ -> {};
	
	private Cmd<?> cmd = null;
	
	public FtpClient(Vertx vertx, String host, int port) {
		this.vertx = vertx;
		this.host = host;
		this.port = port;
	}
	
	public void connect(Handler<AsyncResult<Void>> handler) {
		cmd = new CmdConnect($ -> {
			handler.handle(Future.succeededFuture());
		});
		client = vertx.createNetClient();
		client.connect(port, host, res -> {
			socket = res.result();

			if (res.failed()) {
				handler.handle(Future.failedFuture(res.cause()));
			} else {
				RecordParser parser = RecordParser.newDelimited("\n", this::output);
				socket.handler(parser);
			}
		});
	}


	public void login(String user, String passwd, Handler<AsyncResult<Void>> handler) {
		write(new CmdUser(user, Flow.with(handler, u -> {
			write(new CmdPass(passwd, Flow.with(handler, p -> {
				handler.handle(Future.succeededFuture());
			})));
		})));
	}
	
	public void list(Handler<AsyncResult<Buffer>> handler) {
		Buffer data = Buffer.buffer();
		
		write(new CmdPasv(Flow.with(handler, pasv -> {
			client.connect(pasv.port, host, res -> {
				NetSocket datasocket = res.result();
				datasocket.handler(b -> {
					data.appendBuffer(b);
				});
				datasocket.endHandler(b -> {
					log.debug("end");
				});
				datasocket.exceptionHandler(e -> {
					log.error("error", e);
				});
			});
			write(new CmdList(Flow.with(handler, list -> {
				handler.handle(Future.succeededFuture(data));
			})));
		})));
	}
	
	public void retr(String file, AsyncFile localFile, Handler<AsyncResult<Void>> handler) {

		write(new CmdPasv(Flow.with(handler, pasv -> {
			client.connect(pasv.port, host, res -> {
				NetSocket datasocket = res.result();
				Pump.pump(datasocket, localFile).start();
			});
			write(new CmdRetr(file, Flow.with(handler, list -> {
				handler.handle(Future.succeededFuture());
			})));
		})));
	}
	
	public void write(Cmd<?> cmd) {
		this.cmd = cmd;
		cmd.send(this);
	}
	
	
	public void write(String line) {
		log.trace(">{}", line);
		socket.write(line + "\r\n");
	}
	
	public void fail(String msg) {
		throw new RuntimeException(msg);
	}
	
	public void output(Buffer buf) {
		Pattern p = Pattern.compile("(\\d\\d\\d)([ -])(.*)");
		Matcher m = p.matcher(buf.toString().trim());

		if (m.matches()) {
			String code = m.group(1);
			log.trace(code + " " + m.group(2) + " " + m.group(3));
			if (code.equals("421")) {
				socket.close();
				endHandler.handle(null);
				return;
			}
			if (cmd == null) {
				fail("unexpected response " + buf);
				return;
			}
			cmd.setCode(m.group(1));
			cmd.addMessage(m.group(3));

			if (m.group(2).equals(" ")) {
				Cmd<?> cmd = this.cmd;
				this.cmd = null;
				log.trace("handling {}", cmd);
				cmd.recieve(this);
			} else if (m.group(2).equals("-")) {
				log.info("waiting for more");
			}
		}
	}
	
	public static void main(String[] args) throws Exception {
		Vertx vertx = Vertx.vertx();
		FtpClient client = new FtpClient(vertx, "speedtest.tele2.net", 21);
		client.connect(a -> {
			client.login("anonymous", "passwd", $2 -> {
				client.list($ -> {
					System.out.println("list " + $.result());
					vertx.fileSystem().open("target/tmp.zip", new OpenOptions().setWrite(true).setTruncateExisting(true), arfile -> {
						client.retr("512KB.zip", arfile.result(), $3 -> {
							arfile.result().close($5 -> {
								System.out.println("retr");
							});
						});
						
					});
				});
			});
		});
		
	}
}
