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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import io.vertx.core.Vertx;

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
		HandlerLatch<Void> latch = new HandlerLatch<>();
		FtpClient client = new FtpClient(vertx, "speedtest.tele2.net", 21);
		client.connect(a -> {
			if (latch.failed(a)) {
				return;
			}
			client.login("anonymous", "passwd", login -> {
				if (latch.failed(login)) {
					return;
				}
				latch.countDown();
			});
		});
		latch.await();
	}
	
	@Test
	public void testList() {
		HandlerLatch<Void> latch = new HandlerLatch<>();
		FtpClient client = new FtpClient(vertx, "speedtest.tele2.net", 21);
		client.connect(a -> {
			if (latch.failed(a)) {
				return;
			}
			client.login("anonymous", "passwd", login -> {
				if (latch.failed(login)) {
					return;
				}
				client.list(list -> {
					if (latch.failed(list)) {
						return;
					}
                    System.out.println("list " + list.result());
    				latch.countDown();
                });
			});
		});
		latch.await();
	}
}
