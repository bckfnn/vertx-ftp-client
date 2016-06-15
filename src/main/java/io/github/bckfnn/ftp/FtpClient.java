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
    private Handler<AsyncResult<Response>> next;

    public FtpClient(Vertx vertx, String host, int port) {
        this.vertx = vertx;
        this.host = host;
        this.port = port;
    }

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

    public void retr(String file, AsyncFile localFile, Handler<AsyncResult<Void>> handler) {
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
                        Pump.pump(datasocket, localFile).start();
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
