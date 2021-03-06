/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.integration.mail;

import java.util.Date;
import java.util.concurrent.ScheduledFuture;

import javax.mail.Folder;
import javax.mail.FolderClosedException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Store;
import javax.mail.internet.MimeMessage;

import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.integration.mail.MailReceiver.MailReceiverContext;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.util.Assert;

/**
 * An event-driven Channel Adapter that receives mail messages from a mail
 * server that supports the IMAP "idle" command (see RFC 2177). Received mail
 * messages will be converted and sent as Spring Integration Messages to the
 * output channel. The Message payload will be the {@link javax.mail.Message}
 * instance that was received.
 *
 * @author Arjen Poutsma
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Gary Russell
 */
public class ImapIdleChannelAdapter extends MessageProducerSupport {

	private final IdleTask idleTask = new IdleTask();

	private volatile boolean shouldReconnectAutomatically = true;

	private final ImapMailReceiver mailReceiver;

	private volatile int reconnectDelay = 10000; // milliseconds

	private volatile ScheduledFuture<?> receivingTask;

	private volatile ScheduledFuture<?> pingTask;

	private volatile long connectionPingInterval = 10000;

	private final ExceptionAwarePeriodicTrigger receivingTaskTrigger = new ExceptionAwarePeriodicTrigger();


	public ImapIdleChannelAdapter(ImapMailReceiver mailReceiver) {
		Assert.notNull(mailReceiver, "'mailReceiver' must not be null");
		this.mailReceiver = mailReceiver;
	}


	/**
	 * Specify whether the IDLE task should reconnect automatically after
	 * catching a {@link FolderClosedException} while waiting for messages. The
	 * default value is <code>true</code>.
	 */
	public void setShouldReconnectAutomatically(boolean shouldReconnectAutomatically) {
		this.shouldReconnectAutomatically = shouldReconnectAutomatically;
	}

	@Override
	public String getComponentType() {
		return "mail:imap-idle-channel-adapter";
	}


	/*
	 * Lifecycle implementation
	 */

	@Override // guarded by super#lifecycleLock
	protected void doStart() {
		final TaskScheduler scheduler =  this.getTaskScheduler();
		Assert.notNull(scheduler, "'taskScheduler' must not be null" );
		this.receivingTask = scheduler.schedule(new ReceivingTask(), this.receivingTaskTrigger);
		this.pingTask = scheduler.scheduleAtFixedRate(new PingTask(), this.connectionPingInterval);
	}

	@Override
	// guarded by super#lifecycleLock
	protected void doStop() {
		this.receivingTask.cancel(true);
		this.pingTask.cancel(true);
		try {
			this.mailReceiver.destroy();
		}
		catch (Exception e) {
			throw new IllegalStateException(
					"Failure during the destruction of Mail receiver: " + mailReceiver, e);
		}
	}


	private class ReceivingTask implements Runnable {
		public void run() {
			try {
				idleTask.run();
				if (logger.isDebugEnabled()) {
					logger.debug("Task completed successfully. Re-scheduling it again right away.");
				}
			}
			catch (Exception e) { //run again after a delay
				logger.warn("Failed to execute IDLE task. Will attempt to resubmit in " + reconnectDelay + " milliseconds.", e);
				receivingTaskTrigger.delayNextExecution();
			}
		}
	}


	private class IdleTask implements Runnable {

		public void run() {
			final TaskScheduler scheduler =  getTaskScheduler();
			Assert.notNull(scheduler, "'taskScheduler' must not be null" );
			MailReceiverContext context = null;
			try {
				if (logger.isDebugEnabled()) {
					logger.debug("waiting for mail");
				}
				mailReceiver.waitForNewMessages();
				context = mailReceiver.getTransactionContext();
				Assert.state(context != null, "Mail receiver returned a null context");
				Folder folder = context.getFolder();
				if (folder.isOpen()) {
					Message[] mailMessages = mailReceiver.receive();
					if (logger.isDebugEnabled()) {
						logger.debug("received " + mailMessages.length + " mail messages");
					}
					for (Message mailMessage : mailMessages) {
						final MimeMessage copied = new MimeMessage((MimeMessage) mailMessage);
						sendMessage(MessageBuilder.withPayload(copied).build());
					}
				}
			}
			catch (MessagingException e) {
				if (logger.isWarnEnabled()) {
					logger.warn("error occurred in idle task", e);
				}
				if (shouldReconnectAutomatically) {
					throw new IllegalStateException(
							"Failure in 'idle' task. Will resubmit.", e);
				}
				else {
					throw new org.springframework.integration.MessagingException(
							"Failure in 'idle' task. Will NOT resubmit.", e);
				}
			}
			finally {
				if (context != null) {
					mailReceiver.closeContextAfterSuccess(context);
				}
			}
		}
	}


	private class PingTask implements Runnable {

		public void run() {
			try {
				Store store = mailReceiver.getStore();
				if (store != null) {
					store.isConnected();
				}
			}
			catch (Exception ignore) {
			}
		}
	}

	private class ExceptionAwarePeriodicTrigger implements Trigger {

		private volatile boolean delayNextExecution;


		public Date nextExecutionTime(TriggerContext triggerContext) {
			if (delayNextExecution){
				delayNextExecution = false;
				return new Date(System.currentTimeMillis() + reconnectDelay);
			}
			else {
				return new Date(System.currentTimeMillis());
			}
		}

		public void delayNextExecution() {
			this.delayNextExecution = true;
		}
	}

}
