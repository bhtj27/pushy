/* Copyright (c) 2013 RelayRides
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.relayrides.pushy.apns;

import static org.junit.Assert.assertEquals;

import java.util.Date;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class ApnsClientThreadTest {

	private static final int APNS_PORT = 2195;
	private static final int FEEDBACK_PORT = 2196;
	
	private static final int MAX_PAYLOAD_SIZE = 256;
	
	private static final byte[] TOKEN = new byte[] { 0x12, 0x34, 0x56 };
	private static final String PAYLOAD = "{\"aps\":{\"alert\":\"Hello\"}}";
	private static final Date EXPIRATION = new Date(1375926408000L);
	
	private static final ApnsEnvironment TEST_ENVIRONMENT =
			new ApnsEnvironment("127.0.0.1", APNS_PORT, "127.0.0.1", FEEDBACK_PORT, false);
	
	private PushManager<SimpleApnsPushNotification> pushManager;
	private ApnsClientThread<SimpleApnsPushNotification> clientThread;
	
	private MockApnsServer server;
	
	@Before
	public void setUp() throws InterruptedException {
		this.server = new MockApnsServer(APNS_PORT, MAX_PAYLOAD_SIZE);
		this.server.start();
		
		this.pushManager = new PushManager<SimpleApnsPushNotification>(TEST_ENVIRONMENT, null, null);
		
		this.clientThread = new ApnsClientThread<SimpleApnsPushNotification>(this.pushManager);
		
		this.clientThread.connect();
		this.clientThread.start();
	}
	
	@Test
	public void testSendNotification() throws InterruptedException {
		final SimpleApnsPushNotification notification = new SimpleApnsPushNotification(TOKEN, PAYLOAD, EXPIRATION);
		
		this.pushManager.enqueuePushNotification(notification);
		
		this.waitForQueueToEmpty();
		this.clientThread.getLastWriteFuture().await();
		
		final List<SimpleApnsPushNotification> receivedNotifications = this.server.getReceivedNotifications();
		
		assertEquals(1, receivedNotifications.size());
		assertEquals(notification, receivedNotifications.get(0));
	}
	
	@Test
	public void testSendManyNotifications() throws InterruptedException {
		final SimpleApnsPushNotification notification = new SimpleApnsPushNotification(TOKEN, PAYLOAD, EXPIRATION);
		
		final int iterations = 1000;
		
		for (int i = 0; i < iterations; i++) {
			this.pushManager.enqueuePushNotification(notification);
		}
		
		this.waitForQueueToEmpty();
		this.clientThread.getLastWriteFuture().await();
		
		final List<SimpleApnsPushNotification> receivedNotifications = this.server.getReceivedNotifications();
		
		assertEquals(iterations, receivedNotifications.size());
	}
	
	@Test
	public void testSendManyNotificationsWithMultipleThreads() throws InterruptedException {
		final ApnsClientThread<SimpleApnsPushNotification> secondClientThread =
				new ApnsClientThread<SimpleApnsPushNotification>(this.pushManager);
		
		secondClientThread.connect();
		
		try {
			secondClientThread.start();
			
			final SimpleApnsPushNotification notification = new SimpleApnsPushNotification(TOKEN, PAYLOAD, EXPIRATION);
			
			final int iterations = 1000;
			
			for (int i = 0; i < iterations; i++) {
				this.pushManager.enqueuePushNotification(notification);
			}
			
			this.waitForQueueToEmpty();
			this.clientThread.getLastWriteFuture().await();
			secondClientThread.getLastWriteFuture().await();
			
			final List<SimpleApnsPushNotification> receivedNotifications = this.server.getReceivedNotifications();
			
			assertEquals(iterations, receivedNotifications.size());
		} finally {
			secondClientThread.shutdown();
		}
	}
	
	@Test
	public void testSendNotificationsWithError() throws InterruptedException {
		final SimpleApnsPushNotification notification = new SimpleApnsPushNotification(TOKEN, PAYLOAD, EXPIRATION);
		
		final int iterations = 100;
		this.server.failWithErrorAfterNotifications(RejectedNotificationReason.INVALID_TOKEN, 10);
		
		for (int i = 0; i < iterations; i++) {
			this.pushManager.enqueuePushNotification(notification);
		}
		
		this.waitForQueueToEmpty();
		this.clientThread.getLastWriteFuture().await();
		
		final List<SimpleApnsPushNotification> receivedNotifications = this.server.getReceivedNotifications();
		
		assertEquals(iterations, receivedNotifications.size());
	}
	
	@Test
	public void testSendManyNotificationsWithMultipleThreadsAndError() throws InterruptedException {
		final ApnsClientThread<SimpleApnsPushNotification> secondClientThread =
				new ApnsClientThread<SimpleApnsPushNotification>(this.pushManager);
		
		secondClientThread.connect();
		
		try {
			secondClientThread.start();
			
			final SimpleApnsPushNotification notification = new SimpleApnsPushNotification(TOKEN, PAYLOAD, EXPIRATION);
			
			final int iterations = 100;
			this.server.failWithErrorAfterNotifications(RejectedNotificationReason.INVALID_TOKEN, 10);
			
			for (int i = 0; i < iterations; i++) {
				this.pushManager.enqueuePushNotification(notification);
			}
			
			this.waitForQueueToEmpty();
			this.clientThread.getLastWriteFuture().await();
			secondClientThread.getLastWriteFuture().await();
			
			final List<SimpleApnsPushNotification> receivedNotifications = this.server.getReceivedNotifications();
			
			assertEquals(iterations, receivedNotifications.size());
		} finally {
			secondClientThread.shutdown();
		}
	}
	
	@After
	public void tearDown() throws InterruptedException {
		this.pushManager.shutdown();
		this.server.shutdown();
	}

	private void waitForQueueToEmpty() throws InterruptedException {
		while (!this.pushManager.getQueue().isEmpty()) {
			Thread.yield();
		}
		
		// TODO This is a pretty gross hack
		Thread.sleep(1000);
	}
}
