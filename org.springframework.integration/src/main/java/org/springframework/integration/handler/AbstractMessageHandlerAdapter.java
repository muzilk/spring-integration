/*
 * Copyright 2002-2008 the original author or authors.
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

package org.springframework.integration.handler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.springframework.integration.channel.ChannelRegistry;
import org.springframework.integration.channel.ChannelRegistryAware;
import org.springframework.integration.message.DefaultMessageCreator;
import org.springframework.integration.message.DefaultMessageMapper;
import org.springframework.integration.message.Message;
import org.springframework.integration.message.MessageCreator;
import org.springframework.integration.message.MessageHandlingException;
import org.springframework.integration.message.MessageMapper;
import org.springframework.integration.util.AbstractMethodInvokingAdapter;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * An implementation of the {@link MessageHandler} interface that invokes the specified method and target object. Either
 * a {@link Method} reference or a 'methodName' may be provided, but both are not necessary. In fact, while preference
 * is given to a {@link Method} reference if available, an Exception will be thrown if a non-matching 'methodName' has
 * also been provided. Therefore, to avoid such ambiguity, it is recommended to provide just one or the other.
 * <p>
 * This handler also accepts an implementation of the {@link MessageMapper} strategy interface which it uses for
 * converting from the {@link Message} being handled to an Object prior to invoking the method. Likewise, if the method
 * has a non-null return value, a reply message will be generated by the configured implementation of the
 * {@link MessageCreator} strategy interface. In both cases, the default implementations will simply consider the
 * message's payload.
 * 
 * @author Mark Fisher
 */
public abstract class AbstractMessageHandlerAdapter extends AbstractMethodInvokingAdapter
		implements MessageHandler, ChannelRegistryAware {

	public static final String OUTPUT_CHANNEL_NAME_KEY = "outputChannelName";


	private volatile boolean methodExpectsMessage;

	private volatile MessageMapper messageMapper = new DefaultMessageMapper();

	private volatile MessageCreator messageCreator = new DefaultMessageCreator();

	private volatile ChannelRegistry channelRegistry;


	public void setMethodExpectsMessage(boolean methodExpectsMessage) {
		this.methodExpectsMessage = methodExpectsMessage;
	}

	public void setMessageMapper(MessageMapper messageMapper) {
		Assert.notNull(messageMapper, "'messageMapper' must not be null");
		this.messageMapper = messageMapper;
	}

	public void setMessageCreator(MessageCreator messageCreator) {
		Assert.notNull(messageCreator, "'messageCreator' must not be null");
		this.messageCreator = messageCreator;
	}

	public void setChannelRegistry(ChannelRegistry channelRegistry) {
		this.channelRegistry = channelRegistry;
	}

	protected ChannelRegistry getChannelRegistry() {
		return this.channelRegistry;
	}

	public Message<?> handle(Message<?> message) {
		if (!this.isInitialized()) {
			this.afterPropertiesSet();
		}
		if (message == null) {
			throw new IllegalArgumentException("message must not be null");
		}
		Object args[] = null;
		Object mappingResult = (this.methodExpectsMessage) ? message : this.messageMapper.mapMessage(message);
		if (mappingResult != null && mappingResult.getClass().isArray()
				&& (Object.class.isAssignableFrom(mappingResult.getClass().getComponentType()))) {
			args = (Object[]) mappingResult;
		}
		else {
			args = new Object[] { mappingResult }; 
		}
		try {
			Object result = null;
			try {
				result = this.invokeMethod(args);
			}
			catch (NoSuchMethodException e) {
				result = this.invokeMethod(message);
				this.methodExpectsMessage = true;
			}
			if (result == null) {
				return null;
			}
			return this.handleReturnValue(result, message);
		}
		catch (InvocationTargetException e) {
			throw new MessageHandlingException(message, "Handler method '"
					+ this.getMethodName() + "' threw an Exception.", e.getTargetException());
		}
		catch (Throwable e) {
			throw new MessageHandlingException(message, "Failed to invoke handler method '"
					+ this.getMethodName() + "' with arguments: " + ObjectUtils.nullSafeToString(args), e);
		}
	}

	protected Message<?> createReplyMessage(Object returnValue, Message<?> originalMessage) {
		Message<?> reply = this.messageCreator.createMessage(returnValue);
		if (reply != null) {
			reply.copyHeader(originalMessage.getHeader(), false);
			Object correlationId = reply.getHeader().getCorrelationId();
			if (correlationId == null) {
				Object orginalCorrelationId = originalMessage.getHeader().getCorrelationId();
				reply.getHeader().setCorrelationId((orginalCorrelationId != null) ?
						orginalCorrelationId : originalMessage.getId());
			}
		}
		return reply;
	}

	/**
	 * Subclasses must implement this method to handle the return value.
	 */
	protected abstract Message<?> handleReturnValue(Object returnValue, Message<?> originalMessage);

}
