/*
 * Copyright 2007-2012 the original author or authors
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package org.springframework.integration.redis.inbound;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.integration.Message;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.integration.support.converter.MessageConverter;
import org.springframework.integration.support.converter.SimpleMessageConverter;
import org.springframework.util.Assert;

/**
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @since 2.1
 */
public class RedisInboundChannelAdapter extends MessageProducerSupport {

	private final RedisMessageListenerContainer container = new RedisMessageListenerContainer();

	private RedisSerializer<?> redisSerializer;
	
	private volatile MessageConverter messageConverter = new SimpleMessageConverter();

	private volatile String[] topics;


	public RedisInboundChannelAdapter(RedisConnectionFactory connectionFactory, RedisSerializer<?> redisSerializer) {
        Assert.notNull(connectionFactory, "connectionFactory must not be null");
        Assert.notNull(redisSerializer, "rediSerializer must not be null");
        this.container.setConnectionFactory(connectionFactory);
        this.redisSerializer = redisSerializer;
    }
	
	public RedisInboundChannelAdapter(RedisConnectionFactory connectionFactory) {
		this(connectionFactory, new StringRedisSerializer());
	}

    public RedisSerializer<?> getRedisSerializer() {
        return redisSerializer;
    }

    public void setRedisSerializer(RedisSerializer<?> redisSerializer) {
        this.redisSerializer = redisSerializer;
    }

    public void setTopics(String... topics) {
		this.topics = topics;
	}
	
	public void setMessageConverter(MessageConverter messageConverter) {
		Assert.notNull(messageConverter, "messageConverter must not be null");
		this.messageConverter = messageConverter;
	}

	public String getComponentType() {
		return "redis:inbound-channel-adapter";
	}

	@Override
	protected void onInit() {
		super.onInit();
		Assert.notEmpty(this.topics, "at least one topis is required for subscription");
		MessageListenerDelegate delegate = new MessageListenerDelegate();
		MessageListenerAdapter adapter = new MessageListenerAdapter(delegate);
		adapter.setSerializer(redisSerializer);
		List<ChannelTopic> topicList = new ArrayList<ChannelTopic>();
		for (String topic : this.topics) {
			topicList.add(new ChannelTopic(topic));
		}	
		adapter.afterPropertiesSet();
		this.container.addMessageListener(adapter, topicList);
		this.container.afterPropertiesSet();
	}
	
	@Override
	protected void doStart() {
		super.doStart();
		this.container.start();
	}

	
	@Override
	protected void doStop() {
		super.doStop();
		this.container.stop();
	}

	private Message<?> convertMessage(Object s) {
		return this.messageConverter.toMessage(s);
	}


	private class MessageListenerDelegate {

		@SuppressWarnings("unused")
		public void handleMessage(Object s) {
			sendMessage(convertMessage(s));
		}
	}

}
