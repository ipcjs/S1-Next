package me.ykrank.s1next.data.db.dbmodel;

import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.greenrobot.greendao.annotation.Convert;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Index;
import org.greenrobot.greendao.annotation.Property;
import org.greenrobot.greendao.converter.PropertyConverter;

import java.io.IOException;

import me.ykrank.s1next.data.api.model.collection.Posts;
import me.ykrank.s1next.util.L;

/**
 * 帖子每页信息
 * Created by ykrank on 2017/2/13.
 */
@Entity(nameInDb = "ThreadPage",
        indexes = {@Index(value = "threadId ASC, page ASC", unique = true)})
public class ThreadPage {
    @Id(autoincrement = true)
    @Nullable
    private Long id;
    /**
     * 帖子ID
     */
    @Property(nameInDb = "ThreadId")
    private int threadId;
    /**
     * 页数
     */
    @Property(nameInDb = "Page")
    private int page;
    /**
     * 帖子信息
     */
    @Property(nameInDb = "Posts")
    @Convert(converter = PostsConverter.class, columnType = String.class)
    private Posts posts;
    /**
     * 更新时间
     */
    @Property(nameInDb = "Timestamp")
    private long timestamp;


    @Generated(hash = 1627648011)
    public ThreadPage(Long id, int threadId, int page, Posts posts, long timestamp) {
        this.id = id;
        this.threadId = threadId;
        this.page = page;
        this.posts = posts;
        this.timestamp = timestamp;
    }

    public ThreadPage() {
        this.timestamp = System.currentTimeMillis();
    }


    public Long getId() {
        return this.id;
    }


    public void setId(Long id) {
        this.id = id;
    }


    public int getThreadId() {
        return this.threadId;
    }


    public void setThreadId(int threadId) {
        this.threadId = threadId;
    }


    public Posts getPosts() {
        return this.posts;
    }


    public void setPosts(Posts posts) {
        this.posts = posts;
    }


    public long getTimestamp() {
        return this.timestamp;
    }


    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getPage() {
        return this.page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    @Override
    public String toString() {
        return "ThreadPage{" +
                "id=" + id +
                ", threadId=" + threadId +
                ", page=" + page +
                ", posts=" + posts +
                ", timestamp=" + timestamp +
                '}';
    }

    public void copyFrom(ThreadPage oThreadPage) {
        this.threadId = oThreadPage.threadId;
        this.page = oThreadPage.page;
        this.posts = oThreadPage.posts;
        this.timestamp = oThreadPage.timestamp;
    }

    public static final class PostsConverter implements PropertyConverter<Posts, String> {
        private static ObjectMapper mapper = new ObjectMapper();

        @Override
        public Posts convertToEntityProperty(String databaseValue) {
            if (!TextUtils.isEmpty(databaseValue)) {
                try {
                    return mapper.readValue(databaseValue, Posts.class);
                } catch (IOException e) {
                    L.report(e);
                }
            }
            return null;
        }

        @Override
        public String convertToDatabaseValue(Posts entityProperty) {
            if (entityProperty != null) {
                try {
                    return mapper.writeValueAsString(entityProperty);
                } catch (JsonProcessingException e) {
                    L.report(e);
                }
            }
            return null;
        }
    }
}
