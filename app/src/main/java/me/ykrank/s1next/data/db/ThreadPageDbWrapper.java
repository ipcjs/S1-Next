package me.ykrank.s1next.data.db;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.List;

import javax.inject.Inject;

import me.ykrank.s1next.App;
import me.ykrank.s1next.data.db.dbmodel.ThreadPage;
import me.ykrank.s1next.data.db.dbmodel.ThreadPageDao;

import static me.ykrank.s1next.data.db.dbmodel.ThreadPageDao.Properties;

/**
 * 对帖子数据库的操作包装
 * Created by AdminYkrank on 2016/2/23.
 */
public class ThreadPageDbWrapper {
    private static ThreadPageDbWrapper dbWrapper = new ThreadPageDbWrapper();

    @Inject
    AppDaoSessionManager appDaoSessionManager;

    private ThreadPageDbWrapper() {
        App.getAppComponent().inject(this);
    }

    public static ThreadPageDbWrapper getInstance() {
        return dbWrapper;
    }

    private ThreadPageDao getThreadPageDao() {
        return appDaoSessionManager.getDaoSession().getThreadPageDao();
    }

    @Nullable
    public ThreadPage getWithThreadPage(int threadId, int page) {
        return getThreadPageDao().queryBuilder()
                .where(Properties.ThreadId.eq(threadId), Properties.Page.eq(page))
                .unique();
    }

    @NonNull
    public List<ThreadPage> getWithThreadId(int threadId) {
        return getThreadPageDao().queryBuilder()
                .where(Properties.ThreadId.eq(threadId))
                .list();
    }

    public void saveThreadPage(@NonNull ThreadPage threadPage) {
        ThreadPage oThreadPage = getWithThreadPage(threadPage.getThreadId(), threadPage.getPage());
        if (oThreadPage == null) {
            getThreadPageDao().insert(threadPage);
        } else {
            oThreadPage.copyFrom(threadPage);
            getThreadPageDao().update(oThreadPage);
        }
    }

    public void delThreadPage(int threadId, int page) {
        ThreadPage oThreadPage = getWithThreadPage(threadId, page);
        if (oThreadPage != null) {
            getThreadPageDao().delete(oThreadPage);
        }
    }

    public void delThreadPage(int threadId) {
        List<ThreadPage> oThreadPages = getWithThreadId(threadId);
        if (oThreadPages != null && !oThreadPages.isEmpty()) {
            getThreadPageDao().deleteInTx(oThreadPages);
        }
    }
}
