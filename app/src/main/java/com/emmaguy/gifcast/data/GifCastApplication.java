package com.emmaguy.gifcast.data;

import android.app.Application;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.emmaguy.gifcast.R;
import com.emmaguy.gifcast.data.api.ImgurService;
import com.emmaguy.gifcast.data.api.LatestImagesRedditService;
import com.emmaguy.gifcast.data.api.model.ImgurGalleryJson;
import com.emmaguy.gifcast.data.api.model.ImgurGalleryJsonDeserializer;
import com.emmaguy.gifcast.data.api.model.ImgurJson;
import com.emmaguy.gifcast.data.api.model.RedditNewImagesJson;
import com.emmaguy.gifcast.util.ImgurUrlParser;
import com.emmaguy.gifcast.util.Utils;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

import retrofit.Callback;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.converter.GsonConverter;

public class GifCastApplication extends Application {
    private LatestImagesRedditService mLatestImagesRedditService;
    private ImgurService mImgurService;
    private DrawableRequestQueue mRequestQueue;
    private final RedditImagesLoader mImagesLoader = new RedditImagesLoader();

    @Override
    public void onCreate() {
        super.onCreate();

        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint("http://www.reddit.com/")
                .build();
        mLatestImagesRedditService = restAdapter.create(LatestImagesRedditService.class);

        RestAdapter imgurRestAdapter = new RestAdapter.Builder()
                .setEndpoint("https://api.imgur.com/3/")
                .setRequestInterceptor(new RequestInterceptor() {
                    @Override
                    public void intercept(RequestFacade request) {
                        request.addHeader("Authorization", "Client-Id " + getString(R.string.imgur_client_id));
                    }
                })
                .setConverter(new GsonConverter(new GsonBuilder().registerTypeAdapter(ImgurGalleryJson.class, new ImgurGalleryJsonDeserializer()).create()))
                        //.setLogLevel(RestAdapter.LogLevel.FULL)
                        //.setLog(new AndroidLog("GifCastTag-Retrofit"))
                .build();

        mImgurService = imgurRestAdapter.create(ImgurService.class);

        mRequestQueue = new DrawableRequestQueue(this);
    }

    public List<Image> getAllImages() {
        return mImagesLoader.getAllImages();
    }

    public void setImages(List<Image> images) {
        mImagesLoader.setImages(images);
    }

    public void setDataChangedListener(final DrawableRequestQueue.OnDataChangedListener listener) {
        mRequestQueue.setDataChangedListener(listener);
    }

    public void setImagesRequesterListener(final RedditImagesLoader.OnRedditItemsChanged listener) {
        mImagesLoader.setImagesRequesterListener(listener);
    }

    public void requestItems(final String before, final String after) {
        mImagesLoader.load(this, before, after);
    }

    public DrawableRequestQueue getRequestQueue() {
        return mRequestQueue;
    }

    public ImgurService getImgurService() {
        return mImgurService;
    }

    public LatestImagesRedditService getLatestImagesRedditService() {
        return mLatestImagesRedditService;
    }

    public static class RedditImagesLoader {

        private final ImgurUrlParser mImgurUrlParser = new ImgurUrlParser();
        private List<Image> mImages = new ArrayList<Image>();
        private OnRedditItemsChanged mListener;

        public List<Image> getAllImages() {
            return mImages;
        }

        public void setImagesRequesterListener(OnRedditItemsChanged listener) {
            mListener = listener;
        }

        public void setImages(List<Image> images) {
            mImages = images;
        }

        public void load(final GifCastApplication app, final String before, final String after) {
            List<String> selectedSubReddits = Utils.selectedSubReddits(app.getApplicationContext());

            if(selectedSubReddits.size() <= 0) {
                Toast.makeText(app.getApplicationContext(), R.string.no_subreddits, Toast.LENGTH_SHORT).show();
                return;
            }

            final ImgurService imgurService = app.getImgurService();

            LatestImagesRedditService imagesService = app.getLatestImagesRedditService();
            imagesService.getNewImagesInSubreddit(TextUtils.join("+", selectedSubReddits), 20, before, after, new Callback<RedditNewImagesJson>() {
                @Override
                public void success(RedditNewImagesJson data, Response response) {
                    if (data == null || data.data == null || data.data.children == null)
                        return;

                    List<Image> images = getImages(data.data.children, imgurService);

                    mImages.addAll(images);
                    mListener.onNewItemsAdded(images);
                }

                @Override
                public void failure(RetrofitError error) {
                    Log.e("GifCastTag", error.getMessage(), error);

                    Toast.makeText(app, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        private List<Image> getImages(List<RedditNewImagesJson.RedditData.RedditImageData> data, ImgurService imgurService) {
            List<Image> images = new ArrayList<Image>();

            for (RedditNewImagesJson.RedditData.RedditImageData i : data) {
                final String url = i.data.url;

                final Image img = new Image(i.data.name, i.data.title, i.data.subreddit, i.data.over_18);
                img.setThumbnailUrl(i.data.thumbnail);

                if (Utils.isImage(url)) {
                    img.updateUrl(url);
                    images.add(img);
                } else if (mImgurUrlParser.isImgurUrl(url)) {
                    final String imgurUrl = mImgurUrlParser.parseUrl(url);
                    if (mImgurUrlParser.isImgurGallery(url)) {
                        requestImgurGalleryImages(imgurService, img, imgurUrl);
                    } else if (mImgurUrlParser.isImgurAlbum(url)) {
                        requestImgurGalleryImages(imgurService, img, imgurUrl);
                    } else {
                        requestImgurImage(imgurService, img, imgurUrl);
                    }
                    images.add(img);
                } else {
                    Log.d("GifCastTag", "Ignoring url: " + url);
                }
            }
            return images;
        }

        private void requestImgurImage(ImgurService imgurService, final Image img, final String imgurUrl) {
            imgurService.getImgurImageUrl(imgurUrl, new Callback<ImgurJson>() {
                @Override
                public void success(ImgurJson imgurJson, Response response) {
                    if (imgurJson == null) return;

                    img.updateUrl(imgurJson.data.link);
                    mListener.onItemsChanged();
                }

                @Override
                public void failure(RetrofitError error) {
                    Log.d("GifCastTag", "Error getting single imgur link, url: " + imgurUrl);
                }
            });
        }

        private void requestImgurGalleryImages(ImgurService imgurService, final Image img, final String imgurUrl) {
            imgurService.getImgurImagesInGallery(imgurUrl, new Callback<ImgurGalleryJson>() {
                @Override
                public void success(ImgurGalleryJson imgurGalleryJson, Response response) {
                    if (imgurGalleryJson == null) return;

                    img.updateUrls(imgurGalleryJson.data);
                    mListener.onItemsChanged();
                }

                @Override
                public void failure(RetrofitError error) {
                    Log.d("GifCastTag", "Error getting imgur gallery url: " + imgurUrl + " msg: " + error.getMessage());
                }
            });
        }

        public interface OnRedditItemsChanged {
            void onNewItemsAdded(List<Image> images);
            void onItemsChanged();
        }
    }
}