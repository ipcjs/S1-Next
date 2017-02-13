package me.ykrank.s1next.widget.track.event;

/**
 * Created by ykrank on 2016/12/29.
 */

public class ViewThreadTrackEvent extends TrackEvent {

    public ViewThreadTrackEvent(String title, String threadId, boolean offline) {
        setGroup("浏览帖子");
        if (offline){
            setName("离线");
        } else {
            setName("在线");
        }
        addData("title", title);
        addData("ThreadId", threadId);
    }
}
