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
package org.springframework.integration.endpoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.Message;
import org.springframework.integration.MessageHeaders;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.core.PseudoTransactionalMessageSource;
import org.springframework.integration.message.GenericMessage;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

/**
 * @author Gary Russell
 * @since 2.2
 *
 */
public class PseudoTransactionalMessageSourceTests {

	@Test
	public void testCommit() {
		SourcePollingChannelAdapter adapter = new SourcePollingChannelAdapter();
		QueueChannel outputChannel = new QueueChannel();
		adapter.setOutputChannel(outputChannel);
		final Object object = new Object();
		final AtomicReference<Object> committed = new AtomicReference<Object>();
		final AtomicReference<Object> rolledBack = new AtomicReference<Object>();
		adapter.setSource(new PseudoTransactionalMessageSource<String, Object>() {

			public Message<String> receive() {
				return new GenericMessage<String>("foo");
			}

			public Object getResource() {
				return object;
			}

			public void afterCommit(Object resource) {
				committed.set(resource);
			}

			public void afterRollback(Object resource) {
				rolledBack.set(resource);
			}

			public void afterReceiveNoTx(Object resource) {
			}

			public void afterSendNoTx(Object resource) {
			}
		});

		TransactionSynchronizationManager.initSynchronization();
		TransactionSynchronizationManager.setActualTransactionActive(true);
		adapter.doPoll();
		TransactionSynchronizationUtils.triggerAfterCommit();
		assertSame(object, committed.get());
		TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);
		TransactionSynchronizationManager.clearSynchronization();
		TransactionSynchronizationManager.setActualTransactionActive(false);
		assertNull(rolledBack.get());
	}

	@Test
	public void testPseudoCommitWithMessage() {
		SourcePollingChannelAdapter adapter = new SourcePollingChannelAdapter();
		QueueChannel outputChannel = new QueueChannel();
		adapter.setOutputChannel(outputChannel);
		final Object object = new Object();
		final AtomicReference<Object> afterReceive = new AtomicReference<Object>();
		final AtomicReference<Object> afterSend = new AtomicReference<Object>();
		adapter.setSource(new PseudoTransactionalMessageSource<String, Object>() {

			public Message<String> receive() {
				return new GenericMessage<String>("foo");
			}

			public Object getResource() {
				return object;
			}

			public void afterCommit(Object resource) {
				throw new RuntimeException("no tx - commit not expected");
			}

			public void afterRollback(Object resource) {
				throw new RuntimeException("no tx - rollback not expected");
			}

			public void afterReceiveNoTx(Object resource) {
				afterReceive.set(resource);
			}

			public void afterSendNoTx(Object resource) {
				afterSend.set(resource);
			}
		});

		adapter.doPoll();
		assertSame(object, afterReceive.get());
		assertSame(object, afterSend.get());
	}

	@Test
	public void testPseudoCommitNoMessage() {
		SourcePollingChannelAdapter adapter = new SourcePollingChannelAdapter();
		QueueChannel outputChannel = new QueueChannel();
		adapter.setOutputChannel(outputChannel);
		final Object object = new Object();
		final AtomicReference<Object> afterReceive = new AtomicReference<Object>();
		adapter.setSource(new PseudoTransactionalMessageSource<String, Object>() {

			public Message<String> receive() {
				return null;
			}

			public Object getResource() {
				return object;
			}

			public void afterCommit(Object resource) {
				throw new RuntimeException("no tx - commit not expected");
			}

			public void afterRollback(Object resource) {
				throw new RuntimeException("no tx - rollback not expected");
			}

			public void afterReceiveNoTx(Object resource) {
				afterReceive.set(resource);
			}

			public void afterSendNoTx(Object resource) {
				throw new RuntimeException("no message - after send not expected");
			}
		});

		adapter.doPoll();
		assertSame(object, afterReceive.get());
	}

	@Test
	public void testRollback() {
		SourcePollingChannelAdapter adapter = new SourcePollingChannelAdapter();
		QueueChannel outputChannel = new QueueChannel();
		adapter.setOutputChannel(outputChannel);
		final Object object = new Object();
		final AtomicReference<Object> committed = new AtomicReference<Object>();
		final AtomicReference<Object> rolledBack = new AtomicReference<Object>();
		adapter.setSource(new PseudoTransactionalMessageSource<String, Object>() {

			public Message<String> receive() {
				return new GenericMessage<String>("foo");
			}

			public Object getResource() {
				return object;
			}

			public void afterCommit(Object resource) {
				committed.set(resource);
			}

			public void afterRollback(Object resource) {
				rolledBack.set(resource);
			}

			public void afterReceiveNoTx(Object resource) {
			}

			public void afterSendNoTx(Object resource) {
			}
		});

		TransactionSynchronizationManager.initSynchronization();
		TransactionSynchronizationManager.setActualTransactionActive(true);
		adapter.doPoll();
		TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
		assertSame(object, rolledBack.get());
		TransactionSynchronizationManager.clearSynchronization();
		TransactionSynchronizationManager.setActualTransactionActive(false);
		assertNull(committed.get());
	}

	@Test
	public void testSuccessAndFailureEvaluationWithResource() {
		SourcePollingChannelAdapter adapter = new SourcePollingChannelAdapter();
		QueueChannel outputChannel = new QueueChannel();
		adapter.setOutputChannel(outputChannel);
		final Object object = new Bar();
		final AtomicReference<Object> committed = new AtomicReference<Object>();
		final AtomicReference<Object> rolledBack = new AtomicReference<Object>();
		adapter.setSource(new PseudoTransactionalMessageSource<String, Object>() {

			public Message<String> receive() {
				return new GenericMessage<String>("foo");
			}

			public Object getResource() {
				return object;
			}

			public void afterCommit(Object resource) {
				committed.set(resource);
			}

			public void afterRollback(Object resource) {
				rolledBack.set(resource);
			}

			public void afterReceiveNoTx(Object resource) {
			}

			public void afterSendNoTx(Object resource) {
			}
		});

		TransactionSynchronizationManager.initSynchronization();
		TransactionSynchronizationManager.setActualTransactionActive(true);
		adapter.setOnSuccessExpression(new SpelExpressionParser().parseExpression("payload + #resource.value"));
		QueueChannel success = new QueueChannel();
		adapter.setOnSuccessResultChannel(success);
		adapter.setOnFailureExpression(new SpelExpressionParser().parseExpression("payload + 'X' + #resource.value"));
		QueueChannel failure = new QueueChannel();
		adapter.setOnFailureChannel(failure);

		adapter.doPoll();
		TransactionSynchronizationUtils.triggerAfterCommit();
		assertSame(object, committed.get());
		TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);
		TransactionSynchronizationManager.clearSynchronization();
		TransactionSynchronizationManager.setActualTransactionActive(false);
		assertNull(rolledBack.get());
		Message<?> result = success.receive(10000);
		assertNotNull(result);
		assertEquals("foobar", result.getHeaders().get(MessageHeaders.DISPOSITION_RESULT));
		committed.set(null);

		TransactionSynchronizationManager.initSynchronization();
		TransactionSynchronizationManager.setActualTransactionActive(true);
		adapter.doPoll();
		TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
		assertSame(object, rolledBack.get());
		TransactionSynchronizationManager.clearSynchronization();
		TransactionSynchronizationManager.setActualTransactionActive(false);
		assertNull(committed.get());
		result = failure.receive(10000);
		assertNotNull(result);
		assertEquals("fooXbar", result.getHeaders().get(MessageHeaders.DISPOSITION_RESULT));
	}

	public class Bar {
		public String getValue() {
			return "bar";
		}
	}
}
