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

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import io.vertx.core.Vertx;
import io.vertx.core.file.OpenOptions;

public class FtpClientTele2Test {
	static Vertx vertx;
	
	@BeforeClass
	public static void setup() {
		 vertx = Vertx.vertx();
	}
	
	@AfterClass
	public static void teardown() {
		 vertx.close();
	}
	
	@Test
	public void testLogin() {
		login("speedtest.tele2.net", "anonymous", "passwd", (client, latch) -> {
			latch.countDown();
		});
	}
	
	@Test
	public void testList() {
		login("speedtest.tele2.net", "anonymous", "passwd", (client, latch) -> {
			client.list(list -> {
				if (latch.failed(list)) {
					return;
				}
                System.out.println("list " + list.result());
				latch.countDown();
            });
		});
	}
	
	
	@Test
	public void testListParsed() {
		login("speedtest.tele2.net", "anonymous", "passwd", (client, latch) -> {
			client.list(list -> {
				if (latch.failed(list)) {
					return;
				}
				for (FtpFile f : FtpFile.listing(list.result().toString())) {
	                System.out.println(f);					
				}

				latch.countDown();
            });
		});
	}
	
	@Test
	public void testRetr() {
		login("speedtest.tele2.net", "anonymous", "passwd", (client, latch) -> {
            vertx.fileSystem().open("target/tmp.zip", new OpenOptions().setWrite(true).setTruncateExisting(true), arfile -> {
            	if (latch.failed(arfile)) {
					return;
				}
                client.retr("512KB.zip", arfile.result(), retr -> {
                	if (latch.failed(retr)) {
    					return;
    				}
                    arfile.result().close(close -> {
                    	if (latch.failed(close)) {
        					return;
        				}
                    	latch.countDown();
                    });
                });

            });
        });
    }
	
	private void login(String host, String user, String pass, BiConsumer<FtpClient, HandlerLatch<Void>> handler) {
		HandlerLatch<Void> latch = new HandlerLatch<>();
		
		FtpClient client = new FtpClient(vertx, host, 21);
		client.connect(connect -> {
			if (latch.failed(connect)) {
				return;
			}
			client.login(user, pass, login -> {
				if (latch.failed(login)) {
					return;
				}
				handler.accept(client, latch);
			});
		});
		latch.await();
	}
}
