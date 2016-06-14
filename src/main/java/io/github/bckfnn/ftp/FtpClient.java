package io.github.bckfnn.ftp;

import java.util.concurrent.atomic.AtomicInteger;
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

    private Response response = null;
    Handler<AsyncResult<Response>> next;

    public FtpClient(Vertx vertx, String host, int port) {
        this.vertx = vertx;
        this.host = host;
        this.port = port;
    }

    public void connect(Handler<AsyncResult<Void>> handler) {
        next = Flow.with(handler, c -> {
            switch (c.getCode()) {
            case "220":
                handler.handle(Future.succeededFuture());
                break;
            default:
                c.fail();
                break;
            }
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
        write("USER " + user, Flow.with(handler, u -> {
            switch (u.getCode()) {
            case "230":
                handler.handle(Future.succeededFuture());
                break;
            case "331":
            case "332":
                write("PASS " + passwd, Flow.with(handler, p -> {
                    switch (p.getCode()) {
                    case "230":
                    case "202":
                        handler.handle(Future.succeededFuture());
                        break;
                    default:
                        p.fail();
                        break;
                    }
                }));
                break;
            default:
                u.fail();
            }
        }));
    }

    public void list(Handler<AsyncResult<Buffer>> handler) {
        Buffer data = Buffer.buffer();

        write("PASV", Flow.with(handler, pasv -> {
            switch (pasv.getCode()) {
            case "227":
                int port = pasvPort(pasv);
                client.connect(port, host, res -> {
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
                write("LIST", Flow.with(handler, list -> {
                    switch (list.getCode()) {
                    case "150":
                        handle(Flow.with(handler, listdone -> {
                            switch (listdone.getCode()) {
                            case "226":
                                System.out.println("list handler called");
                                handler.handle(Future.succeededFuture(data));
                                break;
                            default:
                                handler.handle(Future.failedFuture(listdone.getCode()));
                                break;
                            }
                        }));
                        break;
                    default:
                        handler.handle(Future.failedFuture(list.getCode()));
                        break;
                    }
                }));
                break;
            default:
                pasv.fail();
                break;
            }
        }));
    }

    public void retr(String file, AsyncFile localFile, Handler<AsyncResult<Void>> handler) {
        AtomicInteger ends = new AtomicInteger(0);
        write("PASV", Flow.with(handler, pasv -> {
            switch (pasv.getCode()) {
            case "227":
                int port = pasvPort(pasv);
                client.connect(port, host, res -> {
                    NetSocket datasocket = res.result();
                    datasocket.endHandler($ -> {
                        if (ends.incrementAndGet() == 2) {
                            handler.handle(Future.succeededFuture());
                        }
                    });
                    Pump.pump(datasocket, localFile).start();
                });
                write("RETR " + file, Flow.with(handler, retr -> {
                    switch (retr.getCode()) {
                    case "150":
                        handle(Flow.with(handler, listdone -> {
                            switch (listdone.getCode()) {
                            case "226":
                                if (ends.incrementAndGet() == 2) {
                                    handler.handle(Future.succeededFuture());
                                }
                                break;
                            default:
                                handler.handle(Future.failedFuture(listdone.getCode()));
                                break;
                            }
                        }));
                        break;
                    default:
                        handler.handle(Future.failedFuture(retr.getCode()));
                        break;
                    }

                }));
                break;
            default:
                pasv.fail();
                break;
            }
        }));
    }

    public void write(String cmd, Handler<AsyncResult<Response>> handler) {
        this.next = handler;
        write(cmd);
    }

    public void handle(Handler<AsyncResult<Response>> handler) {
        this.next = handler;
    }

    public void write(String line) {
        log.debug(">{}", line);
        socket.write(line + "\r\n");
    }

    public void fail(String msg) {
        throw new RuntimeException(msg);
    }

    public void output(Buffer buf) {
        if (response == null) {
            response = new Response(next);
        }
        Pattern p = Pattern.compile("(\\d\\d\\d)([ -])(.*)");
        Matcher m = p.matcher(buf.toString().trim());

        if (m.matches()) {
            String code = m.group(1);
            log.debug(code + " " + m.group(2) + " " + m.group(3));
            if (code.equals("421")) {
                socket.close();
                endHandler.handle(null);
                return;
            }
            if (response == null) {
                fail("unexpected response " + buf);
                return;
            }
            response.setCode(m.group(1));
            response.addMessage(m.group(3));

            if (m.group(2).equals(" ")) {
                Response response = this.response;
                this.response = null;
                log.trace("handling {}", response);
                next.handle(Future.succeededFuture(response));
            } else if (m.group(2).equals("-")) {
                log.info("waiting for more");
            }
        }
    }

    private int pasvPort(Response resp) {
        Pattern p = Pattern.compile(".*\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\).*");
        Matcher m = p.matcher(resp.messages.get(0));
        if (m.matches()) {
            return Integer.parseInt(m.group(5)) * 256 + Integer.parseInt(m.group(6));
        }
        throw new RuntimeException("xx");
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
