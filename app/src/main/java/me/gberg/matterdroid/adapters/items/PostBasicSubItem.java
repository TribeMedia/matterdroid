package me.gberg.matterdroid.adapters.items;

import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.mikepenz.fastadapter.utils.ViewHolderFactory;

import org.sufficientlysecure.htmltextview.HtmlHttpImageGetter;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import me.gberg.matterdroid.R;
import me.gberg.matterdroid.model.Post;
import me.gberg.matterdroid.utils.ui.HtmlTextView;
import me.gberg.matterdroid.utils.ui.HtmlTextViewLinkMovementMethod;

public class PostBasicSubItem extends PostItem<PostBasicSubItem, PostBasicSubItem.ViewHolder> {
    public PostBasicSubItem(final Post post) {
        super(post);
    }

    @Override
    public int getType() {
        return R.id.id_it_post_basic_sub;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.it_post_basic_sub;
    }

    @Override
    public void bindView(ViewHolder viewHolder, List payloads) {
        super.bindView(viewHolder, payloads);

        viewHolder.body.setHtml(post.markdown(), viewHolder.imageGetter);
    }

    protected static class ViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.it_message_text)
        HtmlTextView body;

        HtmlHttpImageGetter imageGetter;

        public ViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
            imageGetter = new HtmlHttpImageGetter(body);
            body.setMovementMethod(new HtmlTextViewLinkMovementMethod());
        }
    }

    private static final ViewHolderFactory<? extends ViewHolder> FACTORY = new ItemFactory();

    protected static class ItemFactory implements ViewHolderFactory<ViewHolder> {
        public ViewHolder create(View v) {
            return new ViewHolder(v);
        }
    }

    @Override
    public ViewHolderFactory<? extends ViewHolder> getFactory() {
        return FACTORY;
    }
}
