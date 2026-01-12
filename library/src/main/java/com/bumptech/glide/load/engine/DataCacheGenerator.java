package com.bumptech.glide.load.engine;

import androidx.annotation.NonNull;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoader.LoadData;
import com.bumptech.glide.util.pool.GlideTrace;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Generates {@link com.bumptech.glide.load.data.DataFetcher DataFetchers} from cache files
 * containing original unmodified source data.
 */
class DataCacheGenerator implements DataFetcherGenerator, DataFetcher.DataCallback<Object> {

  private final List<Key> cacheKeys;
  private final DecodeHelper<?> helper;
  private final FetcherReadyCallback cb;

  private int sourceIdIndex = -1;
  @Nullable private Key sourceKey;
  @Nullable private List<ModelLoader<File, ?>> modelLoaders;
  private int modelLoaderIndex;
  @Nullable private volatile LoadData<?> loadData;

  // PMD is wrong here, this File must be an instance variable because it may be used across
  // multiple calls to startNext.
  @Nullable
  @SuppressWarnings("PMD.SingularField")
  private File cacheFile;

  DataCacheGenerator(DecodeHelper<?> helper, FetcherReadyCallback cb) {
    this(helper.getCacheKeys(), helper, cb);
  }

  // In some cases we may want to load a specific cache key (when loading from source written to
  // cache), so we accept a list of keys rather than just obtain the list from the helper.
  DataCacheGenerator(List<Key> cacheKeys, DecodeHelper<?> helper, FetcherReadyCallback cb) {
    this.cacheKeys = cacheKeys;
    this.helper = helper;
    this.cb = cb;
  }

  @Override
  public boolean startNext() {
    GlideTrace.beginSection("DataCacheGenerator.startNext");
    try {
      while (modelLoaders == null || !hasNextModelLoader()) {
        sourceIdIndex++;
        if (sourceIdIndex >= cacheKeys.size()) {
          return false;
        }

        Key sourceId = cacheKeys.get(sourceIdIndex);
        // PMD.AvoidInstantiatingObjectsInLoops The loop iterates a limited number of times
        // and the actions it performs are much more expensive than a single allocation.
        @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
        Key originalKey = new DataCacheKey(sourceId, helper.getSignature());
        cacheFile = helper.getDiskCache().get(originalKey);
        if (cacheFile != null) {
          this.sourceKey = sourceId;
          modelLoaders = helper.getModelLoaders(cacheFile);
          modelLoaderIndex = 0;
        }
      }

      loadData = null;
      boolean started = false;
      while (!started && hasNextModelLoader() && cacheFile != null) {
        ModelLoader<File, ?> modelLoader = modelLoaders.get(modelLoaderIndex++);
        loadData =
            modelLoader.buildLoadData(
                cacheFile, helper.getWidth(), helper.getHeight(), helper.getOptions());
        if (loadData != null && helper.hasLoadPath(loadData.fetcher.getDataClass())) {
          started = true;
          loadData.fetcher.loadData(helper.getPriority(), this);
        }
      }
      return started;
    } finally {
      GlideTrace.endSection();
    }
  }

  private boolean hasNextModelLoader() {
    return modelLoaders != null && modelLoaderIndex < modelLoaders.size();
  }

  @Override
  public void cancel() {
    LoadData<?> local = loadData;
    if (local != null) {
      local.fetcher.cancel();
    }
  }

  @Override
  public void onDataReady(@Nullable Object data) {
    LoadData<?> local = loadData;
    if (local != null) {
      cb.onDataFetcherReady(sourceKey, data, local.fetcher, DataSource.DATA_DISK_CACHE, sourceKey);
    }
  }

  @Override
  public void onLoadFailed(@NonNull Exception e) {
    LoadData<?> local = loadData;
    if (local != null) {
      cb.onDataFetcherFailed(sourceKey, e, local.fetcher, DataSource.DATA_DISK_CACHE);
    }
  }
}
