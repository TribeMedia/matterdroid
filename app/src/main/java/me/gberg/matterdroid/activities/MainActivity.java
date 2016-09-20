package me.gberg.matterdroid.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import com.mikepenz.aboutlibraries.Libs;
import com.mikepenz.aboutlibraries.LibsBuilder;
import com.mikepenz.fastadapter.IItem;
import com.mikepenz.fastadapter.IItemAdapter;
import com.mikepenz.fastadapter.adapters.FastItemAdapter;
import com.mikepenz.fastadapter.adapters.FooterAdapter;
import com.mikepenz.fastadapter_extensions.items.ProgressItem;
import com.mikepenz.fastadapter_extensions.scroll.EndlessRecyclerOnScrollListener;
import com.mikepenz.iconics.context.IconicsContextWrapper;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.trello.navi.component.support.NaviAppCompatActivity;
import com.trello.rxlifecycle.LifecycleProvider;
import com.trello.rxlifecycle.android.ActivityEvent;
import com.trello.rxlifecycle.navi.NaviLifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import me.gberg.matterdroid.App;
import me.gberg.matterdroid.R;
import me.gberg.matterdroid.adapters.items.PostBasicSubItem;
import me.gberg.matterdroid.adapters.items.PostBasicTopItem;
import me.gberg.matterdroid.adapters.items.PostItem;
import me.gberg.matterdroid.di.components.TeamComponent;
import me.gberg.matterdroid.events.AddPostsEvent;
import me.gberg.matterdroid.events.ChannelsEvent;
import me.gberg.matterdroid.events.MembersEvent;
import me.gberg.matterdroid.events.RemovePostEvent;
import me.gberg.matterdroid.managers.ChannelsManager;
import me.gberg.matterdroid.managers.MembersManager;
import me.gberg.matterdroid.managers.PostsManager;
import me.gberg.matterdroid.managers.SessionManager;
import me.gberg.matterdroid.managers.WebSocketManager;
import me.gberg.matterdroid.model.APIError;
import me.gberg.matterdroid.model.Channel;
import me.gberg.matterdroid.model.Channels;
import me.gberg.matterdroid.model.Post;
import me.gberg.matterdroid.utils.picasso.ProfileImagePicasso;
import me.gberg.matterdroid.utils.rx.Bus;
import okhttp3.OkHttpClient;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import timber.log.Timber;

public class MainActivity extends NaviAppCompatActivity {

    @BindView(R.id.toolbar)
    Toolbar toolbar;

    @BindView(R.id.co_main_messages_list)
    RecyclerView postsView;

    @BindView(R.id.co_main_new_message)
    EditText newMessageView;

    @BindView(R.id.co_main_send)
    ImageView sendView;

    @Inject
    Bus bus;

    @Inject
    SessionManager sessionManager;

    @Inject
    ChannelsManager channelsManager;

    @Inject
    PostsManager postsManager;

    @Inject
    MembersManager membersManager;

    @Inject
    WebSocketManager webSocketManager;

    @Inject
    OkHttpClient httpClient;

    private final LifecycleProvider<ActivityEvent> provider
            = NaviLifecycle.createActivityLifecycleProvider(this);

    Drawer drawer;

    private IItemAdapter<IDrawerItem> drawerAdapter;
    private Channels channels;

    private Channel channel;
    private FastItemAdapter<IItem> postsAdapter;
    private FooterAdapter<ProgressItem> footerAdapter;
    private boolean noMoreScrollBack = false;
    EndlessRecyclerOnScrollListener infiniteScrollListener;

    private ProfileImagePicasso profileImagePicasso;

    private final static String STATE_CURRENT_CHANNEL = "me.gberg.matterdroid.activities.MainActivity.state.current_channel";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Timber.v("onCreate() Called");

        // Redirect to Choose Team activity if we can't inject the Team Component.
        TeamComponent teamComponent = ((App) getApplication()).getTeamComponent();
        if (teamComponent == null) {
            ChooseTeamActivity.launch(this);
            finish();
            return;
        }
        teamComponent.inject(this);

        // Subscribe to the event bus.
        // TODO: Unsubscribe at the correct lifecycle events.
        bus.toObserverable()
                .observeOn(AndroidSchedulers.mainThread())
                .compose(provider.bindToLifecycle())
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object event) {
                        if (event instanceof ChannelsEvent) {
                            handleChannelsEvent((ChannelsEvent) event);
                        } else if (event instanceof AddPostsEvent) {
                            handleAddPostsEvent((AddPostsEvent) event);
                        } else if (event instanceof RemovePostEvent) {
                            handleRemovePostEvent((RemovePostEvent) event);
                        } else if (event instanceof MembersEvent) {
                            handleMembersEvent((MembersEvent) event);
                        }
                    }
                });

        setContentView(R.layout.ac_main);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);

        profileImagePicasso = new ProfileImagePicasso(sessionManager.getServer(), this, httpClient);

        // Set up the drawer.
        drawer = new DrawerBuilder()
                .withActivity(this)
                .withToolbar(toolbar)
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(final View view, final int position, final IDrawerItem drawerItem) {
                        if (drawerItem instanceof SecondaryDrawerItem) {
                            onChannelSelected(drawerItem.getIdentifier());
                            drawer.closeDrawer();
                            return true;
                        } else {
                            return true;
                        }
                    }
                })
                .build();

        drawerAdapter = drawer.getItemAdapter();

        postsAdapter = new FastItemAdapter<>();
        footerAdapter = new FooterAdapter<>();

        final LinearLayoutManager postsViewLayoutManager = new LinearLayoutManager(this);
        postsViewLayoutManager.setReverseLayout(true);
        postsView.setLayoutManager(postsViewLayoutManager);
        postsView.setItemAnimator(new DefaultItemAnimator());
        postsView.setAdapter(footerAdapter.wrap(postsAdapter));
        recreateOnScrollListener();

        // Connect the web socket.
        webSocketManager.connect();

        channelsManager.loadChannels();

        // Load saved instance state.
        if (savedInstanceState != null) {
            Timber.v("SavedInstanceState found.");
            postsAdapter.withSavedInstanceState(savedInstanceState);
            final String channelId = savedInstanceState.getString(STATE_CURRENT_CHANNEL);
            if (channelId != null) {
                // Note: This will only restore the selected channel if the app hasn't been killed.
                channel = channelsManager.getChannelForId(channelId);
            }
        }

        if (channel != null) {
            postsManager.emitMessages();
        } else {
            drawer.openDrawer();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Timber.v("onSaveInstanceState()");
        savedInstanceState = postsAdapter.saveInstanceState(savedInstanceState);
        if (channel != null) {
            savedInstanceState.putString(STATE_CURRENT_CHANNEL, channel.id);
        }

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.me_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.me_main_about:
                new LibsBuilder()
                        //provide a style (optional) (LIGHT, DARK, LIGHT_DARK_TOOLBAR)
                        .withActivityStyle(Libs.ActivityStyle.LIGHT_DARK_TOOLBAR)
                        //start the activity
                        .start(this);
                return true;
            case R.id.me_main_change_team:
                sessionManager.changeTeam();
                ChooseTeamActivity.launch(this);
                finish();
                return true;
            case R.id.me_main_log_out:
                sessionManager.logOut();
                LoginActivity.launch(this);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(IconicsContextWrapper.wrap(newBase));
    }

    @OnClick(R.id.co_main_send)
    void onSendClicked() {
        Timber.v("onSendClicked()");

        final CharSequence text = newMessageView.getText();

        if (text == null || text.length() <= 0) {
            return;
        }

        postsManager.createNewPost(text.toString());

        newMessageView.setText(null);
    }

    /**
     * Whenever the contents of the view changes, we need to recreate the endless scroll listener
     * as resetting it messes up how we use it. At some point we should probably investiage a proper
     * solution to this issue.
     */
    private void recreateOnScrollListener() {
        postsView.clearOnScrollListeners();
        infiniteScrollListener = new EndlessRecyclerOnScrollListener(footerAdapter) {
            @Override
            public void onLoadMore(final int currentPage) {
                Timber.v("onLoadMore() noMoreScrollback: " + noMoreScrollBack);
                boolean canLoadMore = postsManager.loadMorePosts();
                if (!noMoreScrollBack && canLoadMore) {
                    footerAdapter.clear();
                    footerAdapter.add(new ProgressItem().withEnabled(false));
                }
            }
        };
        postsView.addOnScrollListener(infiniteScrollListener);
    }

    private void onChannelSelected(long id) {
        Timber.v("onChannelSelected(): " + id);
        channel = channels.channels.get((int) id);

        // Set activity title.
        toolbar.setTitle(channel.displayName);

        // Clear the message adapter.
        footerAdapter.clear();
        postsAdapter.clear();

        recreateOnScrollListener();

        noMoreScrollBack = false;

        postsManager.setChannel(channel);
        membersManager.setChannel(channel);
    }

    private void handleChannelsEvent(final ChannelsEvent event) {
        Timber.v("handleChannelsEvent()");
        if (event.isApiError()) {
            APIError apiError = event.getApiError();
            Timber.e("Unrecognised HTTP response code: " + apiError.statusCode + " with error id " + apiError.id);
            return;
        } else if (event.isError()) {
            // Unhandled error. Log it.
            Throwable e = event.getThrowable();
            Timber.e(e, e.getMessage());
            return;
        }

        // Success.
        this.channels = event.getChannels();

        drawerAdapter.clear();

        List<Channel> publicChannels = new ArrayList<>();
        List<Channel> privateChannels = new ArrayList<>();
        List<Channel> dmChannels = new ArrayList<>();

        for (final Channel channel : channels.channels) {
            if (channel.type.equals("O")) {
                publicChannels.add(channel);
            } else if (channel.type.equals("P")) {
                privateChannels.add(channel);
            } else if (channel.type.equals("D")) {
                dmChannels.add(channel);
            } else {
                publicChannels.add(channel);
            }
        }

        drawerAdapter.add(new PrimaryDrawerItem().withName(R.string.it_channels_header_public));
        for (final Channel channel : publicChannels) {
            drawerAdapter.add(new SecondaryDrawerItem()
                    .withName(channel.displayName)
                    .withIdentifier(channels.channels.indexOf(channel))
            );
        }

        drawerAdapter.add(new PrimaryDrawerItem().withName(R.string.it_channels_header_private));
        for (final Channel channel : privateChannels) {
            drawerAdapter.add(new SecondaryDrawerItem()
                    .withName(channel.displayName)
                    .withIdentifier(channels.channels.indexOf(channel))
            );
        }

        drawerAdapter.add(new PrimaryDrawerItem().withName(R.string.it_channels_header_dm));
        for (final Channel channel : dmChannels) {
            drawerAdapter.add(new SecondaryDrawerItem()
                    .withName(channel.displayName)
                    .withIdentifier(channels.channels.indexOf(channel))
            );
        }
    }
    private void handleAddPostsEvent(final AddPostsEvent event) {
        Timber.v("handleAddPostsEvent()");

        Post previousPost = null;

        // If we are inserting at the end of the adapter, there is no previous post. However, if not
        // then we should set the previous post to the one "before" where we are inserting.
        if (event.getPosition() < postsAdapter.getItemAdapter().getItemCount()) {
            try {
                PostItem postItem = (PostItem) postsAdapter.getItem(event.getPosition());
                // Check it is not null in case there is some other item here due to wrapped adapters.
                if (postItem != null) {
                    previousPost = postItem.getPost();
                }
            } catch (ClassCastException e) {
                // Not a PostItem, so ignore it.
            }

        }

        // Iterate through the posts to be added in *reverse order*, but once we have decided which
        // type of PostItem to use, reverse the order again when adding them to the new items list
        // so that we end up with them in the right order. This is necessary because they are
        // ordered programatically in descending time, which is the opposite of how the user
        // actually perceives things when interacting with the posts list.
        List<IItem> newPostItems = new ArrayList<>();
        List<Post> newPosts = event.getPosts();

        ListIterator<Post> newPostsIterator = newPosts.listIterator(newPosts.size());
        while (newPostsIterator.hasPrevious()) {
            final Post post = newPostsIterator.previous();
            if (previousPost != null && previousPost.userId.equals(post.userId) && previousPost.createAt + 900000 > post.createAt) {
                // The previous post has the same props. Insert a sub post.
                newPostItems.add(0, new PostBasicSubItem(post));
            } else {
                newPostItems.add(0, new PostBasicTopItem(post, profileImagePicasso, membersManager.getMember(post.userId)));
            }
            previousPost = post;
        }

        // Check our scroll position before the update to decide whether to scroll automatically to
        // the top (visually, bottom) of the view once the new items have been added.
        boolean shouldAutoScroll = (event.getPosition() == 0);
        if (postsView.getAdapter().getItemCount() != 0) {
            int firstCompletelyVisibleItemPosition = ((LinearLayoutManager) postsView.getLayoutManager()).findFirstCompletelyVisibleItemPosition();
            shouldAutoScroll = shouldAutoScroll && (firstCompletelyVisibleItemPosition == 0);
        }

        // Add the new items to the adapter.
        postsAdapter.add(event.getPosition(), newPostItems);
        if (event.isScrollback()) {
            footerAdapter.clear();
        }

        // If there is an item directly *before* (ie. below) where we insert these items, then we
        // should check whether to convert it's PostItem type too.
        // TODO: Implement me!

        // Now the editing of the adapter contents is complete, do the autoscroll if appropriate.
        if (shouldAutoScroll) {
            postsView.scrollToPosition(0);
        }
    }

    private void handleRemovePostEvent(final RemovePostEvent event) {
        postsAdapter.remove(event.getPosition());
        // TODO: Make sure this doesn't break the top/sub division of posts.
    }

    private void handleMembersEvent(final MembersEvent event) {
        Timber.v("handleMembersEvent()");
        if (event.isApiError()) {
            APIError apiError = event.getApiError();
            Timber.e("Unrecognised HTTP response code: " + apiError.statusCode + " with error id " + apiError.id);
            return;
        } else if (event.isError()) {
            // Unhandled error. Log it.
            Throwable e = event.getThrowable();
            Timber.e(e, e.getMessage());
            return;
        }

        // Success
        Timber.i("Members for channel retrieved: "+event.getMembersCount());
    }

    public static void launch(final Activity activity) {
        Intent intent = new Intent(activity, MainActivity.class);
        activity.startActivity(intent);
    }
}
