package org.sv2708;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.sv2708.handlers.ChatMessage;
import org.sv2708.handlers.RedisMessageListener;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ChatIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private String nodeId;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private ValueOperations<String, String> valueOperations;
    
    @Autowired
    private RedisMessageListener redisMessageSubscriber;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testJoinAndMessageFlow() throws Exception {
        // Mock Redis behavior
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(valueOperations.get(anyString())).thenReturn(nodeId); // All users on this node

        // Simulate Redis Pub/Sub loopback for the test
        doAnswer(invocation -> {
            String channel = invocation.getArgument(0);
            String message = invocation.getArgument(1);
            redisMessageSubscriber.onMessage(new DefaultMessage(channel.getBytes(), message.getBytes()), null);
            return null;
        }).when(redisTemplate).convertAndSend(anyString(), anyString());

        StandardWebSocketClient client = new StandardWebSocketClient();
        
        // Use a blocking queue to capture incoming messages for assertions
        BlockingQueue<ChatMessage> aliceMessages = new LinkedBlockingQueue<>();
        BlockingQueue<ChatMessage> bobMessages = new LinkedBlockingQueue<>();

        // Connect Alice
        WebSocketSession aliceSession = client.execute(new TestHandler(aliceMessages), 
                "ws://localhost:" + port + "/ws").get(5, TimeUnit.SECONDS);

        // Alice Joins
        aliceSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                new ChatMessage("JOIN", "Alice", null, null))));

        // Verify Alice gets welcome message
        ChatMessage aliceWelcome = aliceMessages.poll(5, TimeUnit.SECONDS);
        assertThat(aliceWelcome).isNotNull();
        assertThat(aliceWelcome.type()).isEqualTo("SYSTEM");
        assertThat(aliceWelcome.handle()).isEqualTo("Alice");

        // Connect Bob
        WebSocketSession bobSession = client.execute(new TestHandler(bobMessages), 
                "ws://localhost:" + port + "/ws").get(5, TimeUnit.SECONDS);

        // Bob Joins
        bobSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                new ChatMessage("JOIN", "Bob", null, null))));

        // Verify Bob gets welcome message
        ChatMessage bobWelcome = bobMessages.poll(5, TimeUnit.SECONDS);
        assertThat(bobWelcome).isNotNull();
        assertThat(bobWelcome.handle()).isEqualTo("Bob");

        // Alice broadcasts a message
        aliceSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                new ChatMessage("BROADCAST", null, null, "Hello everyone!"))));

        // Bob should receive the broadcast
        ChatMessage bobReceivedBroadcast = bobMessages.poll(5, TimeUnit.SECONDS);
        assertThat(bobReceivedBroadcast).isNotNull();
        assertThat(bobReceivedBroadcast.type()).isEqualTo("BROADCAST");
        assertThat(bobReceivedBroadcast.handle()).isEqualTo("Alice");
        assertThat(bobReceivedBroadcast.content()).isEqualTo("Hello everyone!");

        // Bob sends a direct message to Alice
        bobSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                new ChatMessage("DIRECT", null, "Alice", "Hi Alice!"))));

        // Alice should receive the direct message
        ChatMessage aliceReceivedDirect = aliceMessages.poll(5, TimeUnit.SECONDS);
        assertThat(aliceReceivedDirect).isNotNull();
        assertThat(aliceReceivedDirect.type()).isEqualTo("DIRECT");
        assertThat(aliceReceivedDirect.handle()).isEqualTo("Bob");
        assertThat(aliceReceivedDirect.content()).isEqualTo("Hi Alice!");

        aliceSession.close();
        bobSession.close();
    }

    private class TestHandler extends TextWebSocketHandler {
        private final BlockingQueue<ChatMessage> messageQueue;

        public TestHandler(BlockingQueue<ChatMessage> messageQueue) {
            this.messageQueue = messageQueue;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            messageQueue.add(objectMapper.readValue(message.getPayload(), ChatMessage.class));
        }
    }
}
