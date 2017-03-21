package me.ykrank.s1next.widget.span;

import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.View;

import org.xml.sax.Attributes;
import org.xml.sax.XMLReader;

import me.ykrank.s1next.data.api.Api;
import me.ykrank.s1next.view.activity.GalleryActivity;

/**
 * Adds {@link android.view.View.OnClickListener}
 * to {@link android.text.style.ImageSpan} and
 * handles {@literal <strike>} tag.
 */
public final class TagHandler implements Html.TagHandler {
    private static final String TAG = TagHandler.class.getCanonicalName();

    /**
     * See android.text.Html.HtmlToSpannedConverter#getLast(android.text.Spanned, java.lang.Class)
     */
    public static <T> T getLastSpan(Spanned text, Class<T> kind) {
        T[] spans = text.getSpans(0, text.length(), kind);

        if (spans.length == 0) {
            return null;
        } else {
            return spans[spans.length - 1];
        }
    }

    @Override
    public void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader) {
        if ("img".equalsIgnoreCase(tag)) {
            handleImg(opening, output);
        } else if ("acfun".equalsIgnoreCase(tag)) {
            handleAcfun(opening, output, xmlReader);
        } else if ("bilibili".equalsIgnoreCase(tag)) {
            handleBilibili(opening, output, xmlReader);
        }
    }

    /**
     * Replaces {@link android.view.View.OnClickListener}
     * with {@link TagHandler.ImageClickableSpan}.
     * <p>
     * See android.text.Html.HtmlToSpannedConverter#startImg(android.text.SpannableStringBuilder, org.xml.sax.Attributes, android.text.Html.ImageGetter)
     */
    private void handleImg(boolean opening, Editable output) {
        if (!opening) {
            int end = output.length();

            // \uFFFC: OBJECT REPLACEMENT CHARACTER
            int len = "\uFFFC".length();
            ImageSpan imageSpan = output.getSpans(end - len, end, ImageSpan.class)[0];

            String url = imageSpan.getSource();
            // replace \uFFFC with ImageSpan's source
            // in order to support url copyFrom when selected
            output.replace(end - len, end, url);

            // image from server doesn't have domain
            // skip this because we don't want to
            // make this image (emoticon or something
            // others) clickable
            if (!Api.isEmoticonName(url)) {
                output.removeSpan(imageSpan);
                // make this ImageSpan clickable
                output.setSpan(new ImageClickableSpan(imageSpan.getDrawable(), url),
                        end - len, output.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private void handleAcfun(boolean opening, Editable output, XMLReader xmlReader) {
        if (opening) {
            Attributes attributes = HtmlTagHandlerCompat.processAttributes(xmlReader);
            AcfunSpan.startAcfun((SpannableStringBuilder) output, attributes);
        } else
            AcfunSpan.endAcfun((SpannableStringBuilder) output);
    }

    private void handleBilibili(boolean opening, Editable output, XMLReader xmlReader) {
        if (opening) {
            BilibiliSpan.startBilibiliSpan((SpannableStringBuilder) output);
        } else
            BilibiliSpan.endBilibiliSpan((SpannableStringBuilder) output);
    }

    static final class ImageClickableSpan extends ImageSpan implements View.OnClickListener {

        private ImageClickableSpan(Drawable d, String source) {
            super(d, source);
        }

        @Override
        public void onClick(View v) {
            String url = getSource();
            if (!URLUtil.isNetworkUrl(url)) {
                url = Api.BASE_URL + url;
            }
            GalleryActivity.startGalleryActivity(v.getContext(), url);
        }
    }
}
