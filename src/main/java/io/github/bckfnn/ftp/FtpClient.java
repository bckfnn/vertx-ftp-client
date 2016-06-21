/*
 * Copyright 2016 Finn Bock
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.bckfnn.ftp;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;

/**
 * Main FtpClient class to use when sending commands to a FTP server.
 * 
 * @see https://www.ietf.org/rfc/rfc959.txt
 */
public class FtpClient {
    private static Logger log = LoggerFactory.getLogger(FtpClient.class);

    private Vertx vertx;
    private String host;
    private int port;	
    private NetClient client;
    private NetSocket socket;
    private Handler<Void> endHandler = $ -> {};

    private Response response = null;
    private Handler<AsyncResult<Response>> next;

    /**
     * Create a ftp client which connects to the specified host and port.
     * @param vertx the vertx instance to use for creating connections.
     * @param host the host where the FTP server is running.
     * @param port the port on the FTP server.
     */
    public FtpClient(Vertx vertx, String host, int port) {
        this.vertx = vertx;
        this.host = host;
        this.port = port;
    }

    /**
     * Connect to the server.
     * @param handler callback handler that is called when the connection is completed.
     */
    public void connect(Handler<AsyncResult<Void>> handler) {
        next = resp(handler, when("220", c -> {
            handler.handle(Future.succeededFuture());
        }));

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

    /**
     * Perform a login on the FTP server using the specified user name and password.
     * @param user the user name
     * @param passwd the password
     * @param handler callback handler that is called when the login is completed.
     */
    public void login(String user, String passwd, Handler<AsyncResult<Void>> handler) {
        write("USER " + user, resp(handler,
                when("230", u -> {
                    handler.handle(Future.succeededFuture());
                }),
                when("331", "332", u -> {
                    write("PASS " + passwd, resp(handler, 
                            when("230", "202", p -> {
                                handler.handle(Future.succeededFuture());
                            })));
                })));
    }

    public void list(Handler<AsyncResult<Buffer>> handler) {
        Buffer data = Buffer.buffer();

 
        write("PASV", resp(handler, 
                when("227", pasv -> {
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
                    write("LIST", resp(handler, 
                            when("125", "150", list -> {
                                handle(resp(handler, 
                                        when("226", "250", listdone -> {
                                            handler.handle(Future.succeededFuture(data));
                                        })));
                            })));

                })));
    }

    public void retr(String file, AsyncFile localFile, Handler<Progress> progress, Handler<AsyncResult<Void>> handler) {
        AtomicInteger ends = new AtomicInteger(0);
        write("PASV", resp(handler,
                when("227", pasv -> {
                    int port = pasvPort(pasv);
                    client.connect(port, host, res -> {
                        NetSocket datasocket = res.result();
                        datasocket.endHandler($ -> {
                            if (ends.incrementAndGet() == 2) {
                                handler.handle(Future.succeededFuture());
                            }
                        });
                        new ProgressPump(datasocket, localFile, pumped -> {
                            progress.handle(new Progress(this, pumped));
                        }).start();
                    });
                    write("RETR " + file, resp(handler,
                            when("125", "150", retr -> {
                                handle(resp(handler, 
                                        when("226", "250", listdone -> {
                                            if (ends.incrementAndGet() == 2) {
                                                handler.handle(Future.succeededFuture());
                                            }
                                        })));
                            })));
                })));
    }
    
    public void stor(String file, AsyncFile localFile, Handler<Progress> progress, Handler<AsyncResult<Void>> handler) {
        AtomicInteger ends = new AtomicInteger(0);
        write("PASV", resp(handler,
                when("227", pasv -> {
                    int port = pasvPort(pasv);
                    client.connect(port, host, res -> {
                        NetSocket datasocket = res.result();
                        datasocket.endHandler($ -> {
                            if (ends.incrementAndGet() == 2) {
                                handler.handle(Future.succeededFuture());
                            }
                        });
                        new ProgressPump(localFile, datasocket, pumped -> {
                            progress.handle(new Progress(this, pumped));
                        }).start();
                    });
                    write("STOR " + file, resp(handler,
                            when("125", "150", retr -> {
                                handle(resp(handler, 
                                        when("226", "250", listdone -> {
                                            if (ends.incrementAndGet() == 2) {
                                                handler.handle(Future.succeededFuture());
                                            }
                                        })));
                            })));
                })));
    }
    
    public void dele(String file, Handler<AsyncResult<Void>> handler) {
        write("DELE " + file, resp(handler,
                when("250", pasv -> {
                    handler.handle(Future.succeededFuture());
                })));
    }

    public void cwd(String dir, Handler<AsyncResult<Void>> handler) {
        write("CWD " + dir, resp(handler,
                when("250", pasv -> {
                    handler.handle(Future.succeededFuture());
                })));
    }

    public void cdup(Handler<AsyncResult<Void>> handler) {
        write("CDUP", resp(handler,
                when("200", pasv -> {
                    handler.handle(Future.succeededFuture());
                })));
    }

    public void rmd(String dir, Handler<AsyncResult<Void>> handler) {
        write("RMD " + dir, resp(handler,
                when("250", pasv -> {
                    handler.handle(Future.succeededFuture());
                })));
    }
    
    public void mkd(String dir, Handler<AsyncResult<Void>> handler) {
        write("MKD " + dir, resp(handler,
                when("257", pasv -> {
                    handler.handle(Future.succeededFuture());
                })));
    }
    
    public void quit(Handler<AsyncResult<Void>> handler) {
        write("QUIT", resp(handler,
                when("200", pasv -> {
                    handler.handle(Future.succeededFuture());
                })));
    }

    private void write(String cmd, Handler<AsyncResult<Response>> handler) {
        this.next = handler;
        write(cmd);
    }

    private void handle(Handler<AsyncResult<Response>> handler) {
        this.next = handler;
    }

    private void write(String line) {
        log.debug(">{}", line);
        socket.write(line + "\r\n");
    }

    private void pasv(Handler<NetSocket> dataConnection, Handler<AsyncResult<Void>> handler) {
        write("PASV", resp(handler,
                when("227", pasv -> {
                    int port = pasvPort(pasv);
                    client.connect(port, host, res -> {
                        NetSocket datasocket = res.result();
                        dataConnection.handle(datasocket);
                    });
                })));
    }

    private void fail(String msg) {
        throw new RuntimeException(msg);
    }

    private void output(Buffer buf) {
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


    public static <R> Handler<AsyncResult<Response>> resp(Handler<AsyncResult<R>> handler, When... whens) {
        return ar -> {
            if (ar.succeeded()) {
                Response r = ar.result();
                for (When w : whens) {
                    if (w.match(r)) {
                        return;
                    }
                }
                handler.handle(Future.failedFuture(r.code + " " + r.messages.get(0)));
            } else {
                handler.handle(Future.failedFuture(ar.cause()));
            }
        };
    }

    public static When when(String code, Consumer<Response> func) {
        return new When(func, code);
    }

    public static When when(String code1, String code2, Consumer<Response> func) {
        return new When(func, code1, code2);
    }

    private static class When {
        String[] codes;
        Consumer<Response> func;
        
        public When(Consumer<Response> func, String... codes) {
            this.codes = codes;
            this.func = func;
        }
        public boolean match(Response r) {
            for (String code : codes) {
                if (r.getCode().equals(code)) {
                    func.accept(r);
                    return true;
                }
            }
            return false;
        }
    }
}
