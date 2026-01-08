package com.bumptech.glide.load.engine;

import com.bumptech.glide.load.Key;
import javax.annotation.Nullable;

interface EngineJobListener {

  void onEngineJobComplete(EngineJob<?> engineJob, Key key, @Nullable EngineResource<?> resource);

  void onEngineJobCancelled(EngineJob<?> engineJob, Key key);
}
