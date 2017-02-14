package me.ykrank.s1next.view.fragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.google.common.base.Preconditions;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import me.ykrank.s1next.App;
import me.ykrank.s1next.R;
import me.ykrank.s1next.data.User;
import me.ykrank.s1next.data.api.Api;
import me.ykrank.s1next.data.api.model.Thread;
import me.ykrank.s1next.data.api.model.ThreadLink;
import me.ykrank.s1next.data.api.model.collection.Posts;
import me.ykrank.s1next.data.db.BlackListDbWrapper;
import me.ykrank.s1next.data.db.ReadProgressDbWrapper;
import me.ykrank.s1next.data.db.ThreadDbWrapper;
import me.ykrank.s1next.data.db.dbmodel.DbThread;
import me.ykrank.s1next.data.db.dbmodel.ReadProgress;
import me.ykrank.s1next.data.event.BlackListAddEvent;
import me.ykrank.s1next.data.event.PostSelectableChangeEvent;
import me.ykrank.s1next.data.event.QuickSidebarEnableChangeEvent;
import me.ykrank.s1next.data.event.QuoteEvent;
import me.ykrank.s1next.data.pref.GeneralPreferencesManager;
import me.ykrank.s1next.data.pref.ReadProgressPreferencesManager;
import me.ykrank.s1next.util.ClipboardUtil;
import me.ykrank.s1next.util.IntentUtil;
import me.ykrank.s1next.util.L;
import me.ykrank.s1next.util.LooperUtil;
import me.ykrank.s1next.util.MathUtil;
import me.ykrank.s1next.util.RxJavaUtil;
import me.ykrank.s1next.util.StringUtil;
import me.ykrank.s1next.view.activity.ReplyActivity;
import me.ykrank.s1next.view.dialog.LoginPromptDialogFragment;
import me.ykrank.s1next.view.dialog.PostSelectableChangeDialogFragment;
import me.ykrank.s1next.view.dialog.ThreadAttachmentDialogFragment;
import me.ykrank.s1next.view.dialog.ThreadFavouritesAddDialogFragment;
import me.ykrank.s1next.view.internal.CoordinatorLayoutAnchorDelegate;
import me.ykrank.s1next.view.internal.PagerScrollState;
import me.ykrank.s1next.widget.EventBus;
import me.ykrank.s1next.widget.track.event.ViewThreadTrackEvent;
import me.ykrank.s1next.widget.track.event.page.PageEndEvent;
import me.ykrank.s1next.widget.track.event.page.PageStartEvent;


/**
 * A Fragment includes {@link android.support.v4.view.ViewPager}
 * to represent each page of post lists.
 */
public final class PostListFragment extends BaseViewPagerFragment
        implements PostListPagerFragment.PagerCallback, View.OnClickListener {
    public static final String PREF_KEY_POST_SELECTABLE = "pref_key_post_selectable";
    public static final String PREF_KEY_QUICK_SIDE_BAR_ENABLE = "pref_key_quick_side_bar_enable";

    public static final String TAG = PostListFragment.class.getName();

    private static final String ARG_THREAD = "thread";
    private static final String ARG_SHOULD_GO_TO_LAST_PAGE = "should_go_to_last_page";

    /**
     * ARG_JUMP_PAGE takes precedence over {@link #ARG_SHOULD_GO_TO_LAST_PAGE}.
     */
    private static final String ARG_JUMP_PAGE = "jump_page";
    private static final String ARG_QUOTE_POST_ID = "quote_post_id";

    private static final String ARG_READ_PROGRESS = "read_progress";
    private static final String ARG_OFFLINE = "offline";

    @Inject
    EventBus mEventBus;

    @Inject
    User mUser;

    @Inject
    GeneralPreferencesManager mGeneralPreferencesManager;

    @Inject
    ReadProgressPreferencesManager mReadProgressPrefManager;

    private String mThreadId;
    @Nullable
    private String mThreadTitle;
    private boolean mOffline;

    private Posts.ThreadAttachment mThreadAttachment;
    private MenuItem mMenuThreadAttachment;

    private Disposable quoteDisposable;
    private Disposable blackListAddDisposable;
    private Disposable mReadProgressDisposable;
    private ReadProgress readProgress;
    private PagerScrollState scrollState = new PagerScrollState();

    private Disposable mLastReadDisposable, mLastThreadInfoDisposable;
    private PublishSubject<Integer> mLastThreadInfoSubject;

    private PostListPagerAdapter mAdapter;

    public static PostListFragment newInstance(Thread thread, boolean shouldGoToLastPage) {
        PostListFragment fragment = new PostListFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(ARG_THREAD, thread);
        bundle.putBoolean(ARG_SHOULD_GO_TO_LAST_PAGE, shouldGoToLastPage);
        fragment.setArguments(bundle);

        return fragment;
    }

    public static PostListFragment newInstance(ThreadLink threadLink) {
        Thread thread = new Thread();
        thread.setId(threadLink.getThreadId());

        PostListFragment fragment = new PostListFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(ARG_THREAD, thread);
        bundle.putInt(ARG_JUMP_PAGE, threadLink.getJumpPage());
        if (threadLink.getQuotePostId().isPresent()) {
            bundle.putString(ARG_QUOTE_POST_ID, threadLink.getQuotePostId().get());
        }
        fragment.setArguments(bundle);

        return fragment;
    }

    public static PostListFragment newInstance(Thread thread, ReadProgress progress) {
        PostListFragment fragment = new PostListFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(ARG_THREAD, thread);
        bundle.putParcelable(ARG_READ_PROGRESS, progress);
        fragment.setArguments(bundle);

        return fragment;
    }

    public static PostListFragment newInstance(Thread thread, boolean offline, boolean shouldGoToLastPage) {
        PostListFragment fragment = new PostListFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(ARG_THREAD, thread);
        bundle.putBoolean(ARG_OFFLINE, offline);
        bundle.putBoolean(ARG_SHOULD_GO_TO_LAST_PAGE, shouldGoToLastPage);
        fragment.setArguments(bundle);

        return fragment;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        App.getPrefComponent().inject(this);

        Bundle bundle = getArguments();
        Thread thread = Preconditions.checkNotNull(bundle.getParcelable(ARG_THREAD));
        // thread title is null if this thread comes from ThreadLink
        mThreadTitle = thread.getTitle();
        mThreadId = thread.getId();
        mOffline = bundle.getBoolean(ARG_OFFLINE);
        
        trackAgent.post(new ViewThreadTrackEvent(mThreadTitle, mThreadId, mOffline));
        L.leaveMsg("PostListFragment##ThreadTitle:" + mThreadTitle + ",ThreadId:" + mThreadId+ ",Offline:" + mOffline);

        if (savedInstanceState == null) {
            final int jumpPage;
            //读取进度
            readProgress = bundle.getParcelable(ARG_READ_PROGRESS);
            if (readProgress != null) {
                scrollState.setState(PagerScrollState.BEFORE_SCROLL_PAGE);
                jumpPage = readProgress.getPage();
            } else {
                jumpPage = bundle.getInt(ARG_JUMP_PAGE, 0);
            }

            if (jumpPage != 0) {
                // we do not know the total page if we open this thread by URL
                // so we set the jump page to total page
                setTotalPages(jumpPage);
                setCurrentPage(jumpPage - 1);
            } else {
                // +1 for original post
                setTotalPageByPosts(thread.getReliesCount() + 1);
                if (bundle.getBoolean(ARG_SHOULD_GO_TO_LAST_PAGE, false)) {
                    setCurrentPage(getTotalPages() - 1);
                }
            }
        }

        ((CoordinatorLayoutAnchorDelegate) getActivity()).setupFloatingActionButton(
                R.drawable.ic_insert_comment_black_24dp, this);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLastThreadInfoSubject = PublishSubject.create();
        mLastThreadInfoDisposable = mLastThreadInfoSubject.throttleFirst(1, TimeUnit.SECONDS)
                .observeOn(Schedulers.io())
                .subscribe(threadCount -> {
                    LooperUtil.enforceOnWorkThread();
                    DbThread dbThread = new DbThread(Integer.valueOf(mThreadId), threadCount);
                    ThreadDbWrapper.getInstance().saveThread(dbThread);
                }, L::report);
    }

    @Override
    public void onResume() {
        super.onResume();
        trackAgent.post(new PageStartEvent(getContext(), "帖子详情列表-PostListFragment"));

        quoteDisposable = mEventBus.get()
                .ofType(QuoteEvent.class)
                .subscribe(quoteEvent ->
                        startReplyActivity(quoteEvent.getQuotePostId(), quoteEvent.getQuotePostCount())
                );
        blackListAddDisposable = mEventBus.get()
                .ofType(BlackListAddEvent.class)
                .subscribe(blackListEvent -> {
                    BlackListDbWrapper dbWrapper = BlackListDbWrapper.getInstance();
                    if (blackListEvent.isAdd()) {
                        RxJavaUtil.workWithUiThread(() -> dbWrapper.saveDefaultBlackList(
                                blackListEvent.getAuthorPostId(), blackListEvent.getAuthorPostName(),
                                blackListEvent.getRemark()),
                                this::afterBlackListChange);
                    } else {
                        RxJavaUtil.workWithUiThread(() -> dbWrapper.delDefaultBlackList(blackListEvent.getAuthorPostId(), blackListEvent.getAuthorPostName()),
                                this::afterBlackListChange);
                    }
                });
    }

    @Override
    public void onPause() {
        //save last read progress
        final PostListPagerFragment fragment = getCurPostPageFragment();
        if (fragment != null) {
            mLastReadDisposable = Single.just(fragment.getCurReadProgress())
                    .delay(5, TimeUnit.SECONDS)
                    .map(mReadProgressPrefManager::saveLastReadProgress)
                    .doOnError(L::e)
                    .subscribe(b -> L.i("Save last read progress:" + b));
        }
        trackAgent.post(new PageEndEvent(getContext(), "帖子详情列表-PostListFragment"));
        super.onPause();

        RxJavaUtil.disposeIfNotNull(quoteDisposable);
        RxJavaUtil.disposeIfNotNull(blackListAddDisposable);
        RxJavaUtil.disposeIfNotNull(mReadProgressDisposable);
    }

    @Override
    public void onDestroy() {
        RxJavaUtil.disposeIfNotNull(mLastReadDisposable);
        RxJavaUtil.disposeIfNotNull(mLastThreadInfoDisposable);
        mReadProgressPrefManager.saveLastReadProgress(null);

        //Auto save read progress
        if (mReadProgressPrefManager.isSaveAuto()) {
            if (getCurPostPageFragment() != null) {
                PostListPagerFragment.saveReadProgressBack(getCurPostPageFragment().getCurReadProgress());
            }
        }
        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_post, menu);

        mMenuThreadAttachment = menu.findItem(R.id.menu_thread_attachment);
        if (mThreadAttachment == null) {
            mMenuThreadAttachment.setVisible(false);
        }

        if (mReadProgressPrefManager.isSaveAuto()) {
            MenuItem saveMenu = menu.findItem(R.id.menu_save_progress);
            saveMenu.setVisible(false);
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem mMenuPostSelectable = menu.findItem(R.id.menu_post_selectable);
        mMenuPostSelectable.setChecked(mGeneralPreferencesManager.isPostSelectable());
        MenuItem mMenuQuickSideBarEnable = menu.findItem(R.id.menu_quick_side_bar_enable);
        mMenuQuickSideBarEnable.setChecked(mGeneralPreferencesManager.isQuickSideBarEnable());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_thread_attachment:
                ThreadAttachmentDialogFragment.newInstance(mThreadAttachment).show(
                        getActivity().getSupportFragmentManager(),
                        ThreadAttachmentDialogFragment.TAG);

                return true;
            case R.id.menu_favourites_add:
                if (!LoginPromptDialogFragment.showLoginPromptDialogIfNeeded(getActivity(), mUser)) {
                    ThreadFavouritesAddDialogFragment.newInstance(mThreadId).show(
                            getActivity().getSupportFragmentManager(),
                            ThreadFavouritesAddDialogFragment.TAG);
                }

                return true;
            case R.id.menu_link:
                ClipboardUtil.copyText(getContext(), Api.getPostListUrlForBrowser(mThreadId,
                        getCurrentPage()));
                ((CoordinatorLayoutAnchorDelegate) getActivity()).showShortSnackbar(
                        R.string.message_thread_link_copy);

                return true;
            case R.id.menu_share:
                String value;
                String url = Api.getPostListUrlForBrowser(mThreadId, getCurrentPage());
                if (TextUtils.isEmpty(mThreadTitle)) {
                    value = url;
                } else {
                    value = StringUtil.concatWithTwoSpaces(mThreadTitle, url);
                }

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, value);
                intent.setType("text/plain");

                startActivity(Intent.createChooser(intent, getString(R.string.menu_title_share)));

                return true;
            case R.id.menu_browser:
                IntentUtil.startViewIntentExcludeOurApp(getContext(), Uri.parse(
                        Api.getPostListUrlForBrowser(mThreadId, getCurrentPage() + 1)));

                return true;
            case R.id.menu_save_progress:
                if (getCurPostPageFragment() != null) {
                    getCurPostPageFragment().saveReadProgress();
                }
                return true;
            case R.id.menu_load_progress:
                loadReadProgress();
                return true;
            case R.id.menu_post_selectable:
                //Switch text selectable
                PostSelectableChangeDialogFragment.newInstance(!item.isChecked())
                        .setPositiveListener((dialog, which) -> {
                            //reload all data
                            item.setChecked(!item.isChecked());
                            mGeneralPreferencesManager.invalidatePostSelectable(item.isChecked());
                            mEventBus.post(new PostSelectableChangeEvent());
                        })
                        .show(getFragmentManager(), null);
                return true;
            case R.id.menu_quick_side_bar_enable:
                item.setChecked(!item.isChecked());
                mGeneralPreferencesManager.invalidateQuickSideBarEnable(item.isChecked());
                mEventBus.post(new QuickSidebarEnableChangeEvent());
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    BaseFragmentStatePagerAdapter getPagerAdapter(FragmentManager fragmentManager) {
        if (mAdapter == null) {
            mAdapter = new PostListPagerAdapter(fragmentManager);
        }
        return mAdapter;
    }

    @Nullable
    @Override
    CharSequence getTitleWithoutPosition() {
        return mThreadTitle;
    }

    @Override
    public void setTotalPageByPosts(int threads) {
        setTotalPages(MathUtil.divide(threads, Api.POSTS_PER_PAGE));
        //save reply count in database
        try {
            mLastThreadInfoSubject.onNext(threads - 1);
        } catch (Exception e) {
            mLastThreadInfoSubject.onError(e);
        }
    }

    @Override
    public void setThreadTitle(CharSequence title) {
        Thread thread = Preconditions.checkNotNull(getArguments().getParcelable(ARG_THREAD));
        thread.setTitle(title.toString());
        mThreadTitle = thread.getTitle();
        setTitleWithPosition(getCurrentPage());
    }

    @Override
    public void setupThreadAttachment(Posts.ThreadAttachment threadAttachment) {
        this.mThreadAttachment = threadAttachment;

        // mMenuThreadAttachment = null when configuration changes (like orientation changes)
        // but we don't need to care about the visibility of mMenuThreadAttachment
        // because mThreadAttachment != null and we won't invoke
        // mMenuThreadAttachment.setVisible(false) during onCreateOptionsMenu(Menu)
        if (mMenuThreadAttachment != null) {
            mMenuThreadAttachment.setVisible(true);
        }
    }

    @Override
    public void onClick(View v) {
        startReplyActivity(null, null);
    }

    /**
     * 获取当前的具体帖子fragment
     */
    @Nullable
    PostListPagerFragment getCurPostPageFragment() {
        return mAdapter.getCurrentFragment();
    }

    /**
     * 读取阅读进度
     */
    void loadReadProgress() {
        ReadProgressDbWrapper dbWrapper = ReadProgressDbWrapper.getInstance();
        mReadProgressDisposable = RxJavaUtil.workWithUiThread(() -> {
            readProgress = dbWrapper.getWithThreadId(Integer.valueOf(mThreadId));
            if (readProgress != null) {
                scrollState.setState(PagerScrollState.BEFORE_SCROLL_PAGE);
            }
        }, this::afterLoadReadProgress);
    }

    /**
     * 读取阅读进度后的操作，主线程
     */
    @MainThread
    private void afterLoadReadProgress() {
        if (readProgress != null && scrollState.getState() == PagerScrollState.BEFORE_SCROLL_PAGE) {
            scrollState.setState(PagerScrollState.BEFORE_SCROLL_POSITION);
            PostListPagerFragment fragment = getCurPostPageFragment();
            if (fragment != null) {
                if (getCurrentPage() != readProgress.getPage() - 1) {
                    setCurrentPage(readProgress.getPage() - 1);
                    getCurPostPageFragment().setReadProgress(readProgress, false);
                } else {
                    getCurPostPageFragment().setReadProgress(readProgress, true);
                }
            }
        }
    }

    private void afterBlackListChange() {
        PostListPagerFragment currFragment = getCurPostPageFragment();
        if (currFragment != null) {
            currFragment.startBlackListRefresh();
        }
        getActivity().setResult(Activity.RESULT_OK);
    }

    private void startReplyActivity(@Nullable String quotePostId, @Nullable String quotePostCount) {
        if (LoginPromptDialogFragment.showLoginPromptDialogIfNeeded(getActivity(), mUser)) {
            return;
        }

        ReplyActivity.startReplyActivityForResultMessage(getActivity(), mThreadId, mThreadTitle,
                quotePostId, quotePostCount);
    }

    /**
     * Returns a Fragment corresponding to one of the pages of posts.
     */
    private class PostListPagerAdapter extends BaseFragmentStatePagerAdapter<PostListPagerFragment> {

        private PostListPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public PostListPagerFragment getItem(int i) {
            Bundle bundle = getArguments();
            int jumpPage = bundle.getInt(ARG_JUMP_PAGE, -1);
            String quotePostId = bundle.getString(ARG_QUOTE_POST_ID);
            if (jumpPage == i + 1 && !TextUtils.isEmpty(quotePostId)) {
                // clear this arg string because we only need to tell PostListPagerFragment once
                bundle.putString(ARG_QUOTE_POST_ID, null);
                return PostListPagerFragment.newInstance(mThreadId, jumpPage, quotePostId);
            } else if (readProgress != null && readProgress.getPage() == i + 1
                    && scrollState.getState() == PagerScrollState.BEFORE_SCROLL_PAGE) {
                scrollState.setState(PagerScrollState.BEFORE_SCROLL_POSITION);
                return PostListPagerFragment.newInstance(mThreadId, i + 1, readProgress, scrollState);
            } else {
                return PostListPagerFragment.newInstance(mThreadId, i + 1, mOffline);
            }
        }
    }
}
