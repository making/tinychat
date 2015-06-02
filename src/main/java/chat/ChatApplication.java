package chat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.io.Serializable;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SpringBootApplication
@Controller
public class ChatApplication {

    @Configuration
    @EnableWebSocketMessageBroker // WebSocketに関する設定クラス(おまじない)
    static class StompConfig extends AbstractWebSocketMessageBrokerConfigurer {

        @Override
        public void registerStompEndpoints(StompEndpointRegistry registry) {
            registry.addEndpoint("endpoint"); // WebSocketのエンドポイント
        }

        @Override
        public void configureMessageBroker(MessageBrokerRegistry registry) {
            registry.setApplicationDestinationPrefixes("/app"); // Controllerに処理させる宛先のPrefix
            registry.enableSimpleBroker("/topic");
        }
    }

    @Autowired
    Participants participants;

    /**
     * 宛先'app/join'へのSBSCRIBEコマンドに対応して、処理結果を宛先'topic/join'へ送信する
     */
    @SubscribeMapping("/join")
    @SendTo("/topic/join")
    Collection<String> join(@Header("simpSessionId") String sessionId,
                            @Header("nickname") String nickname) {
        return this.participants.join(sessionId, nickname); //参加後の全参加者名を送信する
    }

    /**
     * 宛先'app/message'へのメッセージを処理して、処理結果を宛先'topic/message'へ送信する
     */
    @MessageMapping("/message")
    @SendTo("/topic/message")
    OutboundMessage message(@Header("simpSessionId") String sessionId, String message) {
        String nickname = participants.nickname(sessionId);
        return new OutboundMessage(nickname, message);
    }

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}

@Component
class Participants {
    ConcurrentMap<String, String> participants = new ConcurrentHashMap<>();

    public Collection<String> join(String sessionId, String nickname) {
        this.participants.put(sessionId, nickname);
        return this.participants.values();
    }

    public Collection<String> leave(String sessionId) {
        this.participants.remove(sessionId);
        return this.participants.values();
    }

    public String nickname(String sessionId) {
        return this.participants.get(sessionId);
    }
}

/**
 * 接続が切れた際にイベントハンドラ
 */
@Component
class SessionDisconnectEventListener implements ApplicationListener<SessionDisconnectEvent> {
    @Autowired
    Participants participants;
    @Autowired
    SimpMessagingTemplate messagingTemplate;

    @Override
    public void onApplicationEvent(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Collection<String> nicknames = participants.leave(sessionId);
        // 退席後の全参加者名を送信する
        messagingTemplate.convertAndSend("/topic/join", nicknames);
    }
}

class OutboundMessage implements Serializable {
    private final String nickname;
    private final String message;

    public OutboundMessage(String nickname, String message) {
        this.nickname = nickname;
        this.message = message;
    }

    public String getNickname() {
        return nickname;
    }

    public String getMessage() {
        return message;
    }
}