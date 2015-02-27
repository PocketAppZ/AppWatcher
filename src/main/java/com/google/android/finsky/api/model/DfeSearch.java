package com.google.android.finsky.api.model;

import com.android.volley.Response;
import com.google.android.finsky.api.*;
import android.os.*;
import com.google.android.finsky.protos.*;
import com.android.volley.*;
import java.util.*;

public class DfeSearch extends ContainerList<Search.SearchResponse> implements Parcelable
{
    public static Parcelable.Creator<DfeSearch> CREATOR;
    private Boolean mAggregatedQuery;
    private DfeApi mDfeApi;
    private boolean mFullPageReplaced;
    private final String mInitialUrl;
    private String mQuery;
    private String mSuggestedQuery;
    
    static {
        DfeSearch.CREATOR = (Parcelable.Creator<DfeSearch>)new Parcelable.Creator<DfeSearch>() {
            public DfeSearch createFromParcel(final Parcel parcel) {
                boolean aggregatedQuery = true;
                final int int1 = parcel.readInt();
                final ArrayList<UrlOffsetPair> list = new ArrayList<UrlOffsetPair>();
                for (int i = 0; i < int1; ++i) {
                    list.add(new UrlOffsetPair(parcel.readInt(), parcel.readString()));
                }
                final int int2 = parcel.readInt();
                final String string = parcel.readString();
                final int int3 = parcel.readInt();
                Boolean value = null;
                if (int3 >= 0) {
                    if (int3 != 1) {
                        aggregatedQuery = false;
                    }
                    value = aggregatedQuery;
                }
                return new DfeSearch(list, int2, string, value);
            }
            
            public DfeSearch[] newArray(final int n) {
                return new DfeSearch[n];
            }
        };
    }
    
    public DfeSearch(final DfeApi mDfeApi, final String mQuery, final String mInitialUrl) {
        super(mInitialUrl,false);
        this.mFullPageReplaced = false;
        this.mAggregatedQuery = null;
        this.mInitialUrl = mInitialUrl;
        this.mDfeApi = mDfeApi;
        this.mQuery = mQuery;
    }
    
    private DfeSearch(final List<UrlOffsetPair> list, final int n, final String mQuery, final Boolean mAggregatedQuery) {
        super(list, n, true);
        this.mFullPageReplaced = false;
        this.mAggregatedQuery = null;
        this.mQuery = mQuery;
        this.mAggregatedQuery = mAggregatedQuery;
        final int size = list.size();
        String url = null;
        if (size > 0) {
            url = list.get(0).url;
        }
        this.mInitialUrl = url;
    }
    
    protected void clearDiskCache() {
        throw new IllegalStateException("not supported");
    }
    
    public int describeContents() {
        return 0;
    }
    
    @Override
    public int getBackendId() {
        if (this.isAggregateResult()) {
            return 0;
        }
        return super.getBackendId();
    }
    
    protected Document[] getItemsFromResponse(final Search.SearchResponse searchResponse) {
        if (this.mAggregatedQuery == null) {
            this.mAggregatedQuery = searchResponse.aggregateQuery;
        }
        if (searchResponse.suggestedQuery.length() > 0) {
            this.mSuggestedQuery = searchResponse.suggestedQuery;
            this.mFullPageReplaced = searchResponse.fullPageReplaced;
        }
        if (searchResponse.doc == null || searchResponse.doc.length == 0) {
            return new Document[0];
        }
        return this.updateContainerAndGetItems(searchResponse.doc[0]);
    }
    
    protected String getNextPageUrl(final Search.SearchResponse searchResponse) {
        final int length = searchResponse.doc.length;
        String nextPageUrl = null;
        if (length == 1) {
            final DocumentV2.DocV2 docV2 = searchResponse.doc[0];
            final Containers.ContainerMetadata containerMetadata = docV2.containerMetadata;
            nextPageUrl = null;
            if (containerMetadata != null) {
                nextPageUrl = docV2.containerMetadata.nextPageUrl;
            }
        }
        return nextPageUrl;
    }
    
    public String getQuery() {
        return this.mQuery;
    }
    
    public Search.RelatedSearch[] getRelatedSearches() {
        return ((Search.SearchResponse)this.mLastResponse).relatedSearch;
    }
    
    public byte[] getServerLogsCookie() {
        if (this.mLastResponse == null || ((Search.SearchResponse)this.mLastResponse).serverLogsCookie.length == 0) {
            return null;
        }
        return ((Search.SearchResponse)this.mLastResponse).serverLogsCookie;
    }
    
    public String getSuggestedQuery() {
        return this.mSuggestedQuery;
    }
    
    public boolean hasBackendId() {
        return this.mAggregatedQuery != null;
    }
    
    public boolean isAggregateResult() {
        return this.mAggregatedQuery;
    }
    
    public boolean isFullPageReplaced() {
        return this.mFullPageReplaced;
    }
    
    protected Request<?> makeRequest(final String url) {
        return this.mDfeApi.search(url, this, this);
    }
    
    public void writeToParcel(final Parcel parcel, final int n) {
        parcel.writeInt(this.mUrlOffsetList.size());
        for (final UrlOffsetPair urlOffsetPair : this.mUrlOffsetList) {
            parcel.writeInt(urlOffsetPair.offset);
            parcel.writeString(urlOffsetPair.url);
        }
        parcel.writeInt(this.getCount());
        parcel.writeString(this.mQuery);
        if (this.mAggregatedQuery == null) {
            parcel.writeInt(-1);
            return;
        }
        int n2;
        if (this.mAggregatedQuery) {
            n2 = 1;
        }
        else {
            n2 = 0;
        }
        parcel.writeInt(n2);
    }
}