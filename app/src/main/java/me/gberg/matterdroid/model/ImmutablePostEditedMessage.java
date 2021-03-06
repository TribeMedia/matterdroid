package me.gberg.matterdroid.model;

import com.google.gson.Gson;

import org.immutables.value.Value;

@Value.Immutable
public abstract class ImmutablePostEditedMessage implements IWebSocketMessage {
    public static PostEditedMessage create(Gson gson, WebSocketMessage message) {
        Post post = gson.fromJson(message.data().get("post"), Post.class);
        return PostEditedMessage.builder()
                .setParsedData(PostEditedMessageData.builder()
                        .setPost(post)
                        .build())
                .setBroadcast(message.broadcast())
                .build();
    }

    @Value.Immutable
    public abstract static class ImmutablePostEditedMessageData {
        public abstract Post post();
    }

    public abstract Broadcast broadcast();

    public abstract PostEditedMessageData parsedData();
}
