package com.layman.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.layman.entity.*;
import com.layman.redis.RedisUtil;
import com.layman.utils.JsonUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;


/**
 * @ClassName MyHandler
 * @Description TODO
 * @Author 叶泽文
 * @Data 2019/9/9 17:05
 * @Version 3.0
 **/
@Component
public class MyHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    Logger logger = LoggerFactory.getLogger(MyHandler.class.getName());

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${redis.message.topic}")
    private String redisSendTopic;

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {

        return super.acceptInboundMessage(msg);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        // 1. 获取客户端Channel
        Channel currentChannel = ctx.channel();
        logger.info("socket message =======>" + msg.text());
        // 1. 解析msg
        CustomerMessage customerMessage = parseMsg(msg);
        Object message = customerMessage.getMessage();
        // 如果消息类型或者消息内容为空 退出处理
        if (customerMessage.getMessageType() == null || message == null) {
            return;
        }
        // 如果为初始化消息
        if (customerMessage.getMessageType() == MessageType.INIT) {
            String messageJson = JsonUtils.objectToJson(message);
            InitMessage initMessage = JsonUtils.jsonToPojo(messageJson, InitMessage.class);
            cacheChannel(ctx, initMessage);
        } else {
            String messageJson = JsonUtils.objectToJson(message);
            CpwMessage cpwMessage = JsonUtils.jsonToPojo(messageJson, CpwMessage.class);

            //原来
            /*RedisUtil redisUtil = new RedisUtil();
            redisUtil.redisMessageSend(cpwMessage);*/

            //改造
            yxx(cpwMessage);

        }
    }

    /**
     * 先判断自己服务器上，没有，就发布到别的机器上
     *
     * @param cpwMessage
     */
    public void yxx(CpwMessage cpwMessage) {
        Map<String, ChannelHandlerContext> userChannelMap = CacheChannel.userChannelMap;
        String toId = cpwMessage.getToId();
        ChannelHandlerContext channelHandlerContext = (ChannelHandlerContext) userChannelMap.get(toId);
        if (!Objects.isNull(channelHandlerContext)) {
            // 如果该用户的客户端是与本服务器建立的channel,直接推送消息
            channelHandlerContext.channel().writeAndFlush(new TextWebSocketFrame(JsonUtils.objectToJson(cpwMessage)));
        } else {
            // 发布，给其他服务器消费
            RedisUtil redisUtil = new RedisUtil();
            redisUtil.redisMessageSend(cpwMessage);
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
    }

    private void cacheChannel(ChannelHandlerContext ctx, InitMessage msg) {
        // 前台系统用户
        switch (msg.getUserType()) {
            case CustomerUserType.user: {
                System.out.println(msg.getUserId());
                System.out.println(ctx);
                CacheChannel.userChannelMap.put(msg.getUserId(), ctx);
                break;
            }
            // 公司用户
            case CustomerUserType.company: {
                CacheChannel.companyChannelMap.put(msg.getUserId(), ctx);
                break;
            }
            // 平台后台用户
            case CustomerUserType.admin: {
                CacheChannel.adminChannelMap.put(msg.getUserId(), ctx);
                break;
            }
            default:
                break;
        }
    }


    /**
     * @return com.cpw.customer.entity.CustomerMessage
     * @Author 叶泽文
     * @Description 消息解析
     * @Date 17:12 2019/9/18
     * @Param [msg]
     **/
    private static CustomerMessage parseMsg(TextWebSocketFrame msg) {
        //获取客户端传输过来的消息
        String content = msg.text();

        CustomerMessage customerMessage = JsonUtils.jsonToPojo(content, CustomerMessage.class);

        return customerMessage;
    }

}
