package me.ykrank.s1next.data.db;

/**
 * Created by ykrank on 2017/2/13.
 */

public class DbException extends Exception{
    public DbException(String msg) {
        super(msg);
    }

    public DbException(Throwable cause) {
        super(cause);
    }

    public DbException(String message, Throwable cause) {
        super(message, cause);
    }
}
