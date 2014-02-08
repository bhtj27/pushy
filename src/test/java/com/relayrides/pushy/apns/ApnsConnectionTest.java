package com.relayrides.pushy.apns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class ApnsConnectionTest extends BasePushyTest {

	private class TestListener implements ApnsConnectionListener<SimpleApnsPushNotification> {

		private final Object mutex;

		private boolean connectionSucceeded = false;
		private boolean connectionClosed = false;

		private final ArrayList<SimpleApnsPushNotification> writeFailures;

		private SimpleApnsPushNotification rejectedNotification;
		private RejectedNotificationReason rejectionReason;

		public TestListener(final Object mutex) {
			this.mutex = mutex;

			this.writeFailures = new ArrayList<SimpleApnsPushNotification>();
		}

		public void handleConnectionSuccess(final ApnsConnection<SimpleApnsPushNotification> connection) {
			synchronized (this.mutex) {
				this.connectionSucceeded = true;
				this.mutex.notifyAll();
			}
		}

		public void handleConnectionFailure(final ApnsConnection<SimpleApnsPushNotification> connection, final Throwable cause) {
			synchronized (mutex) {
				this.mutex.notifyAll();
			}
		}

		public void handleConnectionClosure(ApnsConnection<SimpleApnsPushNotification> connection) {
			synchronized (mutex) {
				this.connectionClosed = true;
				this.mutex.notifyAll();
			}
		}

		public void handleWriteFailure(ApnsConnection<SimpleApnsPushNotification> connection,
				SimpleApnsPushNotification notification, Throwable cause) {

			this.writeFailures.add(notification);
		}

		public void handleRejectedNotification(
				ApnsConnection<SimpleApnsPushNotification> connection,
				SimpleApnsPushNotification rejectedNotification,
				RejectedNotificationReason reason,
				Collection<SimpleApnsPushNotification> unprocessedNotifications) {

			this.rejectedNotification = rejectedNotification;
			this.rejectionReason = reason;
		}
	}

	@Test
	public void testConnect() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		// For this test, we just want to make sure that connection succeeds and nothing explodes.
		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getWorkerGroup(), listener);

		synchronized (mutex) {
			apnsConnection.connect();
			mutex.wait(1000);
		}

		assertTrue(listener.connectionSucceeded);
	}

	@Test(expected = IllegalStateException.class)
	public void testDoubleConnect() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getWorkerGroup(),
						new TestListener(new Object()));

		apnsConnection.connect();
		apnsConnection.connect();
	}

	@Test
	public void testSendNotification() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getWorkerGroup(), listener);

		final CountDownLatch latch = this.getApnsServer().getCountDownLatch(1);

		synchronized (mutex) {
			apnsConnection.connect();
			mutex.wait(1000);
		}

		assertTrue(listener.connectionSucceeded);

		apnsConnection.sendNotification(this.createTestNotification());
		this.waitForLatch(latch);

		assertEquals(1, this.getApnsServer().getReceivedNotifications().size());
	}

	@Test
	public void testSendNotificationWithError() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getWorkerGroup(), listener);

		synchronized (mutex) {
			apnsConnection.connect();
			mutex.wait(1000);
		}

		assertTrue(listener.connectionSucceeded);

		final SimpleApnsPushNotification bogusNotification =
				new SimpleApnsPushNotification(new byte[] {}, "This is a bogus notification and should be rejected.");

		synchronized (mutex) {
			apnsConnection.sendNotification(bogusNotification);
			mutex.wait(1000);
		}

		assertTrue(listener.connectionClosed);
		assertEquals(bogusNotification, listener.rejectedNotification);
		assertEquals(RejectedNotificationReason.MISSING_TOKEN, listener.rejectionReason);
	}

	@Test
	public void testShutdownGracefully() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getWorkerGroup(), listener);

		synchronized (mutex) {
			apnsConnection.connect();
			mutex.wait(1000);
		}

		assertTrue(listener.connectionSucceeded);

		synchronized (mutex) {
			apnsConnection.shutdownGracefully();
			mutex.wait();
		}

		assertTrue(listener.connectionClosed);
	}

	@Test
	public void testShutdownImmediately() throws UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {
		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);
		final ApnsConnection<SimpleApnsPushNotification> apnsConnection =
				new ApnsConnection<SimpleApnsPushNotification>(
						TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(), this.getWorkerGroup(), listener);

		synchronized (mutex) {
			apnsConnection.connect();
			mutex.wait(1000);
		}

		assertTrue(listener.connectionSucceeded);

		synchronized (mutex) {
			apnsConnection.shutdownImmediately();;
			mutex.wait();
		}

		assertTrue(listener.connectionClosed);
	}
}