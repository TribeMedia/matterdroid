package me.gberg.matterdroid.events;

import java.util.List;

import me.gberg.matterdroid.model.Post;

public class PostsEvent {
    private final List<Post> posts;
    private final boolean scrollback;

    public PostsEvent(final List<Post> posts) {
        this.posts = posts;
        this.scrollback = false;
    }

    public PostsEvent(final List<Post> posts, final boolean scrollback) {
        this.posts = posts;
        this.scrollback = scrollback;
    }

    public final List<Post> getPosts() {
        return posts;
    }

    public final boolean isScrollback() {
        return scrollback;
    }
}
