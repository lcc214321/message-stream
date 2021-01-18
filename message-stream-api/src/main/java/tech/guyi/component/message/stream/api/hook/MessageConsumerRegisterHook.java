package tech.guyi.component.message.stream.api.hook;

import tech.guyi.component.message.stream.api.entry.MessageConsumerEntry;

/**
 * 消息消费者注册钩子 <br />
 * 当消息消费者被注册时回调
 * @author guyi
 * @date 2021/1/18 17:39
 */
public interface MessageConsumerRegisterHook extends MessageStreamHook<MessageConsumerEntry> {
}
