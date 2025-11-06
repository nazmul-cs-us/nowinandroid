/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.material.catalog.musicplayer;

import com.starception.submission.R;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.transition.TransitionManager;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.transition.MaterialArcMotion;
import com.google.android.material.transition.MaterialContainerTransform;
import androidx.fragment.app.Fragment;
import android.util.TypedValue;
import io.material.catalog.musicplayer.MusicData.Album;
import io.material.catalog.musicplayer.MusicData.Track;

/** A Fragment that displays an album's details. */
public class MusicPlayerAlbumDemoFragment extends Fragment {

  public static final String TAG = "MusicPlayerAlbumDemoFragment";
  private static final String ALBUM_ID_KEY = "album_id_key";

  public static MusicPlayerAlbumDemoFragment newInstance(long albumId) {
    MusicPlayerAlbumDemoFragment fragment = new MusicPlayerAlbumDemoFragment();
    Bundle bundle = new Bundle();
    bundle.putLong(ALBUM_ID_KEY, albumId);
    fragment.setArguments(bundle);
    return fragment;
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater layoutInflater,
      @Nullable ViewGroup viewGroup,
      @Nullable Bundle bundle) {
    // Wrap the context with Material Components theme for Material Design Components
    Context context = requireContext();
    // Use Material Components theme directly from the library
    // Theme.MaterialComponents.Light.NoActionBar provides all necessary Material Design attributes
    int materialTheme = com.google.android.material.R.style.Theme_MaterialComponents_Light_NoActionBar;
    ContextThemeWrapper themedContext = new ContextThemeWrapper(context, materialTheme);
    LayoutInflater themedInflater = layoutInflater.cloneInContext(themedContext);
    return themedInflater.inflate(R.layout.cat_music_player_album_fragment, viewGroup, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle bundle) {
    ViewGroup container = view.findViewById(R.id.container);
    Toolbar toolbar = view.findViewById(R.id.toolbar);
    ImageView albumImage = view.findViewById(R.id.album_image);
    TextView albumTitle = view.findViewById(R.id.album_title);
    TextView albumArtist = view.findViewById(R.id.album_artist);
    RecyclerView songRecyclerView = view.findViewById(R.id.song_recycler_view);
    setUpAlbumViews(container, toolbar, albumImage, albumTitle, albumArtist, songRecyclerView);

    AppBarLayout appBarLayout = view.findViewById(R.id.app_bar_layout);
    FloatingActionButton fab = view.findViewById(R.id.fab);
    View musicPlayerContainer = view.findViewById(R.id.music_player_container);
    
    // Get CollapsingToolbarLayout (make it final for lambda)
    final CollapsingToolbarLayout collapsingToolbarLayout;
    if (appBarLayout != null && appBarLayout.getChildCount() > 0) {
      View firstChild = appBarLayout.getChildAt(0);
      if (firstChild instanceof CollapsingToolbarLayout) {
        collapsingToolbarLayout = (CollapsingToolbarLayout) firstChild;
      } else {
        collapsingToolbarLayout = null;
      }
    } else {
      collapsingToolbarLayout = null;
    }
    
    // Get status bar height directly as fallback
    int statusBarHeight = getStatusBarHeight();
    
    // Find the spacer view and set its height
    View toolbarSpacer = view.findViewById(R.id.toolbar_spacer);
    
    // Apply window insets handling
    ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
      Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
      Insets displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
      int safeTop = Math.max(systemBars.top, displayCutout.top);
      int safeRight = Math.max(systemBars.right, displayCutout.right);
      int safeLeft = Math.max(systemBars.left, displayCutout.left);
      
      // Use the actual insets if available, otherwise use the status bar height
      int topInset = safeTop > 0 ? safeTop : statusBarHeight;
      
      // Set spacer height to push toolbar below status bar
      if (toolbarSpacer != null) {
        ViewGroup.LayoutParams params = toolbarSpacer.getLayoutParams();
        if (params != null) {
          params.height = topInset;
          toolbarSpacer.setLayoutParams(params);
        }
      }
      
      // Add top padding to AppBarLayout
      appBarLayout.setPadding(
          appBarLayout.getPaddingLeft(),
          topInset,
          appBarLayout.getPaddingRight(),
          appBarLayout.getPaddingBottom()
      );
      
      // Set toolbar content insets
      toolbar.setContentInsetsRelative(safeLeft, safeRight);
      
      return insets;
    });
    
    // Also apply insets immediately in case they're already available
    view.post(() -> {
      WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(view);
      if (insets != null) {
        ViewCompat.dispatchApplyWindowInsets(view, insets);
      } else {
        // Fallback: apply status bar height directly
        if (toolbarSpacer != null) {
          ViewGroup.LayoutParams params = toolbarSpacer.getLayoutParams();
          if (params != null && params.height == 0) {
            params.height = statusBarHeight;
            toolbarSpacer.setLayoutParams(params);
          }
        }
        appBarLayout.setPadding(
            appBarLayout.getPaddingLeft(),
            statusBarHeight,
            appBarLayout.getPaddingRight(),
            appBarLayout.getPaddingBottom()
        );
      }
    });

    appBarLayout.addOnOffsetChangedListener(
        (appBarLayout1, verticalOffset) -> {
          float verticalOffsetPercentage =
              (float) Math.abs(verticalOffset) / (float) appBarLayout1.getTotalScrollRange();
          
          // Update toolbar background based on scroll position
          if (collapsingToolbarLayout != null) {
            if (verticalOffsetPercentage > 0.1F) {
              // Scrolled - show toolbar background
              toolbar.setBackgroundColor(toolbar.getContext().getResources().getColor(
                  android.R.color.white, null));
            } else {
              // Not scrolled - transparent background
              toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }
          }
          
          if (verticalOffsetPercentage > 0.2F && fab.isOrWillBeShown()) {
            fab.hide();
          } else if (verticalOffsetPercentage <= 0.2F
              && fab.isOrWillBeHidden()
              && musicPlayerContainer.getVisibility() != View.VISIBLE) {
            fab.show();
          }
        });

    // Set up music player transitions
    Context context = requireContext();
    MaterialContainerTransform musicPlayerEnterTransform =
        createMusicPlayerTransform(context, /* entering= */ true, fab, musicPlayerContainer);

    fab.setOnClickListener(
        v -> {
          TransitionManager.beginDelayedTransition(container, musicPlayerEnterTransform);
          fab.setVisibility(View.GONE);
          musicPlayerContainer.setVisibility(View.VISIBLE);
        });

    MaterialContainerTransform musicPlayerExitTransform =
        createMusicPlayerTransform(context, /* entering= */ false, musicPlayerContainer, fab);

    musicPlayerContainer.setOnClickListener(
        v -> {
          TransitionManager.beginDelayedTransition(container, musicPlayerExitTransform);
          musicPlayerContainer.setVisibility(View.GONE);
          fab.setVisibility(View.VISIBLE);
        });
  }

  protected void setUpAlbumViews(
      @NonNull ViewGroup container,
      @NonNull Toolbar toolbar,
      @NonNull ImageView albumImage,
      @NonNull TextView albumTitle,
      @NonNull TextView albumArtist,
      @NonNull RecyclerView songRecyclerView) {
    long albumId = getArguments().getLong(ALBUM_ID_KEY, 0L);
    Album album = MusicData.getAlbumById(albumId);

    // Set the transition name which matches the list/grid item to be transitioned from for
    // the shared element transition.
    ViewCompat.setTransitionName(container, album.title);

    // Set up toolbar
    ViewCompat.setElevation(toolbar, 0F);
    
    // Ensure toolbar is clickable and can receive touch events
    toolbar.setClickable(true);
    toolbar.setFocusable(true);
    
    // Set up navigation click listener with proper touch handling
    toolbar.setNavigationOnClickListener(v -> {
      if (getActivity() != null) {
        OnBackPressedDispatcher dispatcher = requireActivity().getOnBackPressedDispatcher();
        dispatcher.onBackPressed();
      }
    });
    
    // Also set a click listener on the navigation icon view directly if possible
    // This ensures the back button is always touchable
    toolbar.post(() -> {
      // Find the navigation icon view and ensure it's clickable
      for (int i = 0; i < toolbar.getChildCount(); i++) {
        android.view.View child = toolbar.getChildAt(i);
        if (child != null) {
          child.setClickable(true);
          child.setFocusable(true);
        }
      }
    });

    // Set up album info area
    albumImage.setImageResource(album.cover);
    albumTitle.setText(album.title);
    albumArtist.setText(album.artist);
    
    // Set collapsing toolbar title to show when collapsed
    AppBarLayout appBarLayoutForTitle = container.findViewById(R.id.app_bar_layout);
    if (appBarLayoutForTitle != null && appBarLayoutForTitle.getChildCount() > 0) {
      View firstChild = appBarLayoutForTitle.getChildAt(0);
      if (firstChild instanceof CollapsingToolbarLayout) {
        CollapsingToolbarLayout collapsingToolbarLayout = (CollapsingToolbarLayout) firstChild;
        collapsingToolbarLayout.setTitle(album.title);
        collapsingToolbarLayout.setExpandedTitleColor(android.graphics.Color.TRANSPARENT);
        collapsingToolbarLayout.setCollapsedTitleTextColor(
            toolbar.getContext().getResources().getColor(android.R.color.black, null));
      }
    }

    // Set up track list
    songRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
    TrackAdapter adapter = new TrackAdapter();
    songRecyclerView.setAdapter(adapter);
    adapter.submitList(album.tracks);
  }

  private static MaterialContainerTransform createMusicPlayerTransform(
      Context context, boolean entering, View startView, View endView) {
    MaterialContainerTransform musicPlayerTransform =
        new MaterialContainerTransform(context, entering);
    musicPlayerTransform.setPathMotion(new MaterialArcMotion());
    musicPlayerTransform.setScrimColor(Color.TRANSPARENT);
    musicPlayerTransform.setStartView(startView);
    musicPlayerTransform.setEndView(endView);
    musicPlayerTransform.addTarget(endView);
    return musicPlayerTransform;
  }
  
  private int getStatusBarHeight() {
    int result = 0;
    int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
    if (resourceId > 0) {
      result = getResources().getDimensionPixelSize(resourceId);
    }
    // Fallback: use a default value if status bar height can't be determined
    if (result == 0) {
      result = (int) TypedValue.applyDimension(
          TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
    }
    return result;
  }

  /** An adapter to hold an albums list of tracks. */
  class TrackAdapter extends ListAdapter<Track, TrackAdapter.TrackViewHolder> {

    TrackAdapter() {
      super(Track.DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
      return new TrackViewHolder(
          LayoutInflater.from(viewGroup.getContext())
              .inflate(R.layout.cat_music_player_track_list_item, viewGroup, false));
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder trackViewHolder, int i) {
      trackViewHolder.bind(getItem(i));
    }

    /** A ViewHolder for a single track item. */
    class TrackViewHolder extends RecyclerView.ViewHolder {

      private final ImageView playingIcon;
      private final TextView trackNumber;
      private final TextView trackTitle;
      private final TextView trackDuration;

      TrackViewHolder(View view) {
        super(view);
        playingIcon = view.findViewById(R.id.playing_icon);
        trackNumber = view.findViewById(R.id.track_number);
        trackTitle = view.findViewById(R.id.track_title);
        trackDuration = view.findViewById(R.id.track_duration);
      }

      public void bind(Track track) {
        playingIcon.setVisibility(track.playing ? View.VISIBLE : View.INVISIBLE);
        trackNumber.setText(String.valueOf(track.track));
        trackTitle.setText(track.title);
        trackDuration.setText(track.duration);
      }
    }
  }
}

