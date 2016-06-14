package io.github.bckfnn.ftp;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.streams.ReadStream;

/*
 * Copyright 2016 [inno:vasion]
 */
public class Flow {
    private static final Logger log = LoggerFactory.getLogger(Flow.class);

    public static <T, R> Handler<AsyncResult<T>> with(Handler<AsyncResult<R>> handler, Consumer<T> func) {
        return ar -> {
            if (ar.succeeded()) {
                func.accept(ar.result());
            } else {
                handler.handle(Future.failedFuture(ar.cause()));
            }
        };
    }

    public static <T, R> Handler<AsyncResult<T>> withValue(Handler<AsyncResult<R>> handler, Function<T, R> func) {
        return ar -> {
            if (ar.succeeded()) {
                handler.handle(Future.succeededFuture(func.apply(ar.result())));
            } else {
                handler.handle(Future.failedFuture(ar.cause()));
            }
        };
    }
    


    public static <T, R> Handler<AsyncResult<T>> ignoreError(Consumer<T> func) {
        return ar -> {
            func.accept(null);
        };
    }

   
    public static <T> void blocking(Vertx vertx, SupplierExc<T> func, Handler<AsyncResult<T>> handler) {
        vertx.executeBlocking(fut -> {
            try {
                fut.complete(func.get());
            } catch (Throwable e) {
                e.printStackTrace();
                fut.fail(e);
            }
        }, handler);
    }

    
    public static RuntimeException rethrow(Throwable t) {
        if (t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof RuntimeException) {
            return (RuntimeException) t;
        }
        return new RuntimeException(t);
    }


    public static <T> T val(SupplierExc<T> func) {
        try {
            return func.get();
        } catch (Exception e) {
            //e.printStackTrace();
            throw rethrow(e);
        }
    }

    public interface SupplierExc<T> {
        T get() throws Exception;
    }


    public static <T> void forEach(ReadStream<T> stream, BiConsumer<T, Handler<AsyncResult<Void>>> x, Handler<AsyncResult<Void>> handler) {
        stream.endHandler($ -> {
            log.info("loadquery endhandler");
            handler.handle(Future.succeededFuture());
        });
        stream.exceptionHandler(e -> {
            log.info("loadquery exchandler {}", e);
            handler.handle(Future.failedFuture(e));
        });

        stream.handler(elm -> {
            stream.pause();
            x.accept(elm, res -> {
                if (res.failed()) {
                    handler.handle(res);
                } else {
                    stream.resume();
                }
            });
        });
    }

    public static <T> void forEach(List<T> list, BiConsumer<T, Handler<AsyncResult<Void>>> x, Handler<AsyncResult<Void>> handler) {
        AtomicInteger cnt = new AtomicInteger(list.size());
        if (cnt.get() == 0) {
            handler.handle(Future.succeededFuture());
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            log.trace("forEach.loop {}", i);
            Handler<AsyncResult<Void>> h = $ -> {
                if (cnt.decrementAndGet() == 0) {
                    log.trace("forEach.done {} items", list.size());
                    handler.handle(Future.succeededFuture());
                }
            };
            x.accept(list.get(i), h);
        }
    }

    public static <T> void forEach(List<T> list, int chunks, BiConsumer<T, Handler<AsyncResult<Void>>> x, Handler<AsyncResult<Void>> handler) {
        AtomicBoolean failed = new AtomicBoolean(false);
        AtomicInteger cnt = new AtomicInteger(0);
        AtomicInteger completed = new AtomicInteger(0);

        if (list.size() == 0) {
            handler.handle(Future.succeededFuture());
            return;
        }

        AtomicInteger idx = new AtomicInteger(0);
        while (cnt.getAndIncrement() < chunks) {
            T elm = list.get(idx.getAndIncrement());
            Handler<AsyncResult<Void>> h = new Handler<AsyncResult<Void>>() {
                @Override
                public void handle(AsyncResult<Void> event) {
                    if (event.failed()){
                        if (!failed.getAndSet(true)) {
                            handler.handle(event);
                        }
                    }
                    if (failed.get()) {
                        return;
                    }
                    if (completed.getAndIncrement() >= list.size()) {
                        log.trace("forEach.done {} items", list.size());
                        handler.handle(Future.succeededFuture());
                    } else if (idx.get() < list.size()) {
                        x.accept(list.get(idx.getAndIncrement()), this);
                    }
                }
            };
            x.accept(elm, h);
        }
    }

    public static <T> void forEach(Stream<T> list, BiConsumer<T, Handler<AsyncResult<Void>>> x, Handler<AsyncResult<Void>> handler) {
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicInteger cnt = new AtomicInteger(0);
        list.forEach(elm -> {
            cnt.incrementAndGet();
            log.debug("each {} {}", finished.get(), cnt.get());
            x.accept(elm, $ -> {
                if (cnt.decrementAndGet() == 0 && finished.get()) {
                    handler.handle(Future.succeededFuture());
                }
            });
        });
        log.debug("onClose {}", cnt.get());
        finished.set(true);
        if (cnt.get() == 0) {
            handler.handle(Future.succeededFuture());
        }
    }
}
