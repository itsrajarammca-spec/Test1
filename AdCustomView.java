package io.mob.resu.reandroidsdk;


import static io.mob.resu.reandroidsdk.Util.getLauncherActivityName;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.viewpager2.widget.ViewPager2;
import com.facebook.shimmer.ShimmerFrameLayout;
import java.util.ArrayList;
import java.util.List;

import io.mob.resu.reandroidsdk.interfaces.BannerCallback;
import io.mob.resu.reandroidsdk.model.AdItem;


public class AdCustomView extends FrameLayout {

    public static final int BANNER  = 0;
    public static final int LARGEBANNER = 1;
    public static final int MEDIUMRECTANGLE = 2;
    public static final int SKYSCRAPER = 5;
    public static final int WIDESKYSCRAPER = 6;
    public static final int SQUARE = 7;
    public static final int SMALLSQUARE = 8;
    public static final int SMARTBANNER = 9;
    private ViewPager2 adViewPager;
    private LinearLayout dotIndicator;
    private TextView noDataTextView;

    private List<AdItem> adList = new ArrayList<>();
    private int currentIndex = 0;
    private final Handler autoScrollHandler = new Handler();
    private Runnable autoScrollRunnable;
    AdPagerAdapter adapter;
    AdPrefsManager prefsManager;
    Context context;
    String bannerName;
    String bannerSize;
    private int size = -1;
    private ShimmerFrameLayout shimmerFrameLayout;
    DynamicZoneApiHandler dynamicZoneApiHandler;
    int widthPx, heightPx;
    private boolean isReadyToLoad = false;

    public AdCustomView(Context context) {
        super(context);
        this.context = context;
        init(context, null);
    }

    public AdCustomView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public void setBannerName(String bannerName) {
        this.bannerName = bannerName;
        checkReadyToLoad();

    }

    public void setBannerSize(int bannerSize) {
        this.size = bannerSize;
        checkReadyToLoad();
    }


    private void checkReadyToLoad() {
        if (bannerName != null && size != -1 && !isReadyToLoad) {
            isReadyToLoad = true;
            applyBannerSize(size, bannerName);
        }
    }

    private void init(Context context, AttributeSet attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_ad_slider, this, true);
        this.context = context;

        adViewPager = findViewById(R.id.adViewPager);
        dotIndicator = findViewById(R.id.dotIndicator);
        shimmerFrameLayout = findViewById(R.id.shimmerFrameLayout);
        noDataTextView = findViewById(R.id.noDataTextView);
        AdImpressionManager.init(context);

        prefsManager = new AdPrefsManager(this.getContext());

        adViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentIndex = position;
                updateDotsStaticColor();
                AdItem item = adList.get(position);

                if (!AdImpressionManager.hasSeen(item.campaignId)) {
                    AdImpressionManager.trackImpression(item.campaignId);
                    new OfflineCampaignTrack(context, item.id, "10", "", false, null, null, DataNetworkHandler.getInstance())
                            .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "");
                    AdImpressionManager.persistSessionImpressions(item.campaignId);

                    Log.e("Impression", "Queued impression for: " + item.campaignId);
                } else {
                    Log.d("Impression", "Already viewed in session: " + item.campaignId);
                }


                if (!item.isViewed) {
                    item.isViewed = true;
                    Log.d("AdCustomView", "Marked as viewed: " + item.campaignId);
                }
            }
        });

        autoScrollRunnable = () -> {
            if (adList != null && adList.size() > 1) {
                currentIndex = (currentIndex + 1) % adList.size();
                adViewPager.setCurrentItem(currentIndex, true);
                autoScrollHandler.postDelayed(autoScrollRunnable, 5000);
            }
        };

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AdCustomView);
            size = a.getInt(R.styleable.AdCustomView_bannerSize, BANNER);
            bannerName = a.getString(R.styleable.AdCustomView_bannerName);

            if (size == -1 || bannerName == null || bannerName.trim().isEmpty()) {
                TextView errorText = new TextView(context);
                errorText.setText("⚠️ Missing bannerName or bannerSize");
                errorText.setTextColor(Color.RED);
                errorText.setPadding(20, 20, 20, 20);
                removeAllViews();
                addView(errorText);
                return;
            }
            a.recycle();
        }

        if(!TextUtils.isEmpty(bannerName)) {
            applyBannerSize(size, bannerName);
        }
    }

    private void sendcampaign(String campaignId, String status, String actionName) {
        new OfflineCampaignTrack(context, campaignId, status, actionName, false, null, null, DataNetworkHandler.getInstance()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "");

    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        autoScrollHandler.removeCallbacks(autoScrollRunnable);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void applyBannerSize(int size, String bannerName) {
        int widthDp, heightDp;

        switch (size) {
            case LARGEBANNER:
                widthDp = 320;
                heightDp = 100;
                break;
            case MEDIUMRECTANGLE:
                widthDp = 300;
                heightDp = 250;
                break;
            case SKYSCRAPER:
                widthDp = 120;
                heightDp = 600;
                break;
            case WIDESKYSCRAPER:
                widthDp = 160;
                heightDp = 600;
                break;
            case SQUARE:
                widthDp = 250;
                heightDp = 250;
                break;
            case SMALLSQUARE:
                widthDp = 200;
                heightDp = 200;
                break;
            case SMARTBANNER:
                widthDp = LayoutParams.MATCH_PARENT;
                heightDp = 50;
                break;
            default:
                widthDp = 320;
                heightDp = 50;
                break;
        }

        String imageSize = widthDp + "x" + heightDp;
        bannerSize = imageSize;

        if (shimmerFrameLayout != null) {
            shimmerFrameLayout.setVisibility(View.VISIBLE);
            shimmerFrameLayout.startShimmer();
        }

        dynamicZoneApiHandler = new DynamicZoneApiHandler(context);
        dynamicZoneApiHandler.syncBannerDetails(bannerSize, bannerName, new BannerCallback() {
            @Override
            public void onBannerMatched(int bannerId) {
                dynamicZoneApiHandler.updateBanner(bannerName, bannerSize, bannerId, this);
            }

            @Override
            public void onAdItemsFetched(List<AdItem> adItems) {

                new Handler(Looper.getMainLooper()).post(() -> {
                    updatePage(context, adItems);
                });
            }

            @Override
            public void onError(String message, Exception e) {
                Log.e("BannerCallback", "Error: " + message, e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    shimmerFrameLayout.stopShimmer();
                    shimmerFrameLayout.setVisibility(View.GONE);
                });
            }
        });

        widthPx = (widthDp == LayoutParams.MATCH_PARENT) ? LayoutParams.MATCH_PARENT : dpToPx(widthDp);
        heightPx = dpToPx(heightDp);

        ViewGroup.LayoutParams vpParams = adViewPager.getLayoutParams();
        vpParams.width = widthPx;
        vpParams.height = heightPx;
        adViewPager.setLayoutParams(vpParams);

        if (shimmerFrameLayout != null) {
            ViewGroup.LayoutParams shimmerParams = shimmerFrameLayout.getLayoutParams();
            shimmerParams.width = widthPx;
            shimmerParams.height = heightPx;
            shimmerFrameLayout.setLayoutParams(shimmerParams);
        }

        View shimmerView = shimmerFrameLayout.findViewById(R.id.shimmerView);
        if (shimmerView != null) {
            ViewGroup.LayoutParams shimmerViewParams = shimmerView.getLayoutParams();
            shimmerViewParams.width = widthPx;
            shimmerViewParams.height = heightPx;
            shimmerView.setLayoutParams(shimmerViewParams);
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void updatePage(Context context, List<AdItem> adList) {
        this.adList = adList;
        adapter = new AdPagerAdapter(context, adList, widthPx, heightPx, shimmerFrameLayout);
        adViewPager.setAdapter(adapter);

        if(adList.isEmpty()){
            noDataTextView.setVisibility(View.VISIBLE);
            shimmerFrameLayout.stopShimmer();
            shimmerFrameLayout.setVisibility(View.GONE);
        }

        if (adList.size() > 1) {
            currentIndex = 0;
            adViewPager.setCurrentItem(currentIndex, true);
            autoScrollHandler.postDelayed(autoScrollRunnable, 5000);
            dotIndicator.setVisibility(View.VISIBLE);
            setupDots(adList.size());
        } else {
            dotIndicator.setVisibility(View.GONE);
        }

        adapter.setSmartLinkActionListener(new AdPagerAdapter.SmartLinkActionListener() {
            @Override
            public void onSmartLinkAction(String activityName, String fragmentName, String campaignId, int actionId, String actionName) {
                Log.d("SmartLinkAction", "activityName: " + activityName + ", fragmentName: " + fragmentName);

                sendcampaign(campaignId, String.valueOf(actionId), actionName);

                Bundle bundle = new Bundle();
                Intent intent1;

                if (activityName != null && !activityName.trim().isEmpty()) {
                    try {
                        intent1 = new Intent(context, Class.forName(activityName));
                    } catch (ClassNotFoundException e) {
                        try {
                            intent1 = new Intent(context, Class.forName(getLauncherActivityName(context)));
                        } catch (ClassNotFoundException ex) {
                            intent1 = new Intent();
                        }
                    }
                } else {
                    try {
                        intent1 = new Intent(context, Class.forName(getLauncherActivityName(context)));
                    } catch (ClassNotFoundException e) {
                        intent1 = new Intent();
                    }
                }

                intent1.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                bundle.putString("navigationScreen", activityName);
                bundle.putString("customParams", "");
                bundle.putString("category", "");
                bundle.putString("fragmentName", fragmentName);
                bundle.putString("MobileFriendlyUrl", "");
                intent1.putExtras(bundle);
                context.startActivity(intent1);
            }
        });

    }

    private void setupDots(int count) {
        dotIndicator.removeAllViews();

        int width = dpToPx(24);
        int height = dpToPx(4);
        float cornerRadius = dpToPx(2);

        for (int i = 0; i < count; i++) {
            FrameLayout dotContainer = new FrameLayout(getContext());
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(width, height);
            containerParams.setMargins(dpToPx(4), 0, dpToPx(4), 0);
            dotContainer.setLayoutParams(containerParams);
            GradientDrawable baseDrawable = new GradientDrawable();
            baseDrawable.setColor(Color.LTGRAY);
            baseDrawable.setCornerRadius(cornerRadius);
            dotContainer.setBackground(baseDrawable);

            View progressView = new View(getContext());
            FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(width, height);

            GradientDrawable progressDrawable = new GradientDrawable();
            progressDrawable.setCornerRadius(cornerRadius);
            progressView.setLayoutParams(progressParams);

            dotContainer.addView(progressView);
            dotIndicator.addView(dotContainer);
        }
    }

    private void updateDotsStaticColor() {
        int width = dpToPx(24);
        int height = dpToPx(4);

        for (int i = 0; i < dotIndicator.getChildCount(); i++) {
            FrameLayout dotContainer = (FrameLayout) dotIndicator.getChildAt(i);
            View progressView = dotContainer.getChildAt(0);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) progressView.getLayoutParams();
            params.width = width;
            params.height = height;
            progressView.setLayoutParams(params);

            GradientDrawable drawable = new GradientDrawable();
            drawable.setCornerRadius(dpToPx(2));
            drawable.setColor(i == currentIndex ? Color.WHITE : Color.TRANSPARENT);
            progressView.setBackground(drawable);
        }
    }

}
