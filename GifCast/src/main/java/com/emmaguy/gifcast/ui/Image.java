package com.emmaguy.gifcast.ui;

import java.util.ArrayList;
import java.util.List;

public class Image {
    private final String mTitle;
    private final String mRedditId;
    private final boolean mIsNSFW;

    public List<String> mUrls;

    public Image(final String id, final String title, final boolean isNSFW) {
        mUrls = new ArrayList<String>();

        mRedditId = id;
        mTitle = title;
        mIsNSFW = isNSFW;
    }

    public void updateUrl(String url) {
        mUrls.clear();
        mUrls.add(url);
    }

    public void updateUrls(List<String> urls) {
        this.mUrls = urls;
    }

    public String thumbnailUrl() {
        return mUrls.get(0);
    }

    public int getNumberOfImages() {
        return mUrls.size();
    }

    public String[] getImageUrls() {
        return mUrls.toArray(new String[mUrls.size()]);
    }

    public String getRedditId() {
        return mRedditId;
    }

    public String getTitle() {
        return mTitle;
    }

    public boolean isNSFW() {
        return mIsNSFW;
    }
}