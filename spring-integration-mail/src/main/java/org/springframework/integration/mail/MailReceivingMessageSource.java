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

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.integration.Message;
import org.springframework.integration.MessagingException;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.core.PseudoTransactionalMessageSource;
import org.springframework.integration.mail.MailReceiver.MailReceiverContext;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.util.Assert;

/**
 * {@link MessageSource} implementation that delegates to a
 * {@link MailReceiver} to poll a mailbox. Each poll of the mailbox may
 * return more than one message which will then be stored in a queue.
 *
 * @author Jonas Partner
 * @author Mark Fisher
 * @author Gary Russell
 */
public class MailReceivingMessageSource implements PseudoTransactionalMessageSource<javax.mail.Message, MailReceiverContext> {

	private final Log logger = LogFactory.getLog(this.getClass());

	private final MailReceiver mailReceiver;

	private final Queue<javax.mail.Message> mailQueue = new ConcurrentLinkedQueue<javax.mail.Message>();


	public MailReceivingMessageSource(MailReceiver mailReceiver) {
		Assert.notNull(mailReceiver, "mailReceiver must not be null");
		this.mailReceiver = mailReceiver;
	}


	public Message<javax.mail.Message> receive() {
		try {
			javax.mail.Message mailMessage = this.mailQueue.poll();
			if (mailMessage == null) {
				javax.mail.Message[] messages = this.mailReceiver.receive();
				if (messages != null) {
					this.mailQueue.addAll(Arrays.asList(messages));
				}
				mailMessage = this.mailQueue.poll();
			}
			if (mailMessage != null) {
				if (logger.isDebugEnabled()) {
					logger.debug("received mail message [" + mailMessage + "]");
				}
				return MessageBuilder.withPayload(mailMessage).build();
			}
		}
		catch (Exception e) {
			throw new MessagingException("failure occurred while polling for mail", e);
		}
		return null;
	}

	public MailReceiverContext getResource() {
		return this.mailReceiver.getTransactionContext();
	}

	public void afterCommit(Object context) {
		Assert.isTrue(context instanceof MailReceiverContext, "Expected a MailReceiverContext");
		this.mailReceiver.closeContextAfterSuccess((MailReceiverContext) context);
	}

	public void afterRollback(Object context) {
		Assert.isTrue(context instanceof MailReceiverContext, "Expected a MailReceiverContext");
		this.mailReceiver.closeContextAfterFailure((MailReceiverContext) context);
	}

	/**
	 * For backwards-compatibility; with no tx, the mail adapter updates the status before the send.
	 */
	public void afterReceiveNoTx(MailReceiverContext resource) {
		this.afterCommit(resource);
	}

	/**
	 * For backwards-compatibility; with no tx, the mail adapter updates the status before the send.
	 */
	public void afterSendNoTx(MailReceiverContext resource) {
		// No op
	}
}
