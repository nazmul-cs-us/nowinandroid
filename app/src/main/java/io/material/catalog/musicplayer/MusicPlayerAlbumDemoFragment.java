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

import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.MenuItem;
import android.view.ContextThemeWrapper;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.transition.TransitionManager;
import androidx.activity.OnBackPressedDispatcher;
import android.content.ServiceConnection;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.transition.MaterialArcMotion;
import com.google.android.material.transition.MaterialContainerTransform;
import androidx.fragment.app.Fragment;
import android.Manifest;
import android.util.TypedValue;
import dagger.hilt.android.AndroidEntryPoint;
import io.material.catalog.musicplayer.MusicData.Track;
import com.starception.submission.core.qurandatabase.Ayah;
import com.starception.submission.core.qurandatabase.Surah;
import com.starception.submission.feature.quran.AudioLanguage;
import com.starception.submission.feature.quran.QuranData;
import com.starception.submission.feature.quran.QuranPlaybackService;
import com.starception.submission.feature.surah.SurahDetailUiState;
import com.starception.submission.feature.surah.SurahDetailViewModel;
import com.starception.submission.feature.surah.SurahDetailViewModelExtensionsKt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;

/** A Fragment that displays an album's details. */
@AndroidEntryPoint
public class MusicPlayerAlbumDemoFragment extends Fragment {

  public static final String TAG = "MusicPlayerAlbumDemoFragment";
  private static final String SURAH_NUMBER_KEY = "surah_number_key";
  private ValueAnimator toolbarBackgroundAnimator;
  private SurahDetailViewModel surahDetailViewModel;
  private QuranPlaybackService playbackService;
  private boolean isServiceBound = false;
  private int currentSurahIndex = -1;
  private Surah currentSurah;
  private List<Ayah> currentAyahs = new ArrayList<>();
  private List<Track> currentTracks = new ArrayList<>();
  private TrackAdapter trackAdapter;
  private FloatingActionButton fab;
  private View musicPlayerContainer;
  private LinearProgressIndicator musicProgressIndicator;
  private MaterialButton playButton;
  private MaterialButton rewindButton;
  private MaterialButton fastForwardButton;
  private MaterialButton volumeDownButton;
  private MaterialButton volumeUpButton;
  private Slider volumeSlider;
  private Toolbar toolbar;
  private AppBarLayout appBarLayout;
  private ImageView albumImage;
  private TextView albumTitle;
  private TextView albumArtist;
  private RecyclerView songRecyclerView;
  private CollapsingToolbarLayout collapsingToolbarLayout;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private int surahNumber = 1;
  private ActivityResultLauncher<String> permissionLauncher;
  private Runnable pendingPermissionAction;
  private boolean isUserSeeking = false;
  private int currentDurationMs = 0;
  private AudioLanguage currentAudioLanguage = AudioLanguage.ARABIC_ONLY;
  private List<String> availableTranslations = new ArrayList<>();
  private String currentTranslationCode = "ar";

  private static final int[] COVER_RESOURCES = {
      R.drawable.album_efe_kurnaz_unsplash,
      R.drawable.album_pawel_czerwinski_unsplash,
      R.drawable.album_jean_philippe_delberghe_unsplash,
      R.drawable.album_karina_vorozheeva_unsplash,
      R.drawable.album_amy_shamblen_unsplash,
      R.drawable.album_pawel_czerwinski_unsplash_2,
      R.drawable.album_kristopher_roller_unsplash,
      R.drawable.album_emile_seguin_unsplash,
      R.drawable.album_ellen_qin_unsplash,
      R.drawable.album_david_clode_unsplash
  };

  public static MusicPlayerAlbumDemoFragment newInstance(int surahNumber) {
    MusicPlayerAlbumDemoFragment fragment = new MusicPlayerAlbumDemoFragment();
    Bundle bundle = new Bundle();
    bundle.putInt(SURAH_NUMBER_KEY, surahNumber);
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
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Bundle args = getArguments();
    if (args != null) {
      surahNumber = args.getInt(SURAH_NUMBER_KEY, 1);
    }

    permissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
          if (granted) {
            if (pendingPermissionAction != null) {
              pendingPermissionAction.run();
              pendingPermissionAction = null;
            }
          } else if (isAdded()) {
            Toast.makeText(
                    requireContext(),
                    getString(R.string.quran_audio_permission_required),
                    Toast.LENGTH_LONG)
                .show();
            pendingPermissionAction = null;
          }
        });
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle bundle) {
    ViewGroup container = view.findViewById(R.id.container);
    toolbar = view.findViewById(R.id.toolbar);
    albumImage = view.findViewById(R.id.album_image);
    albumTitle = view.findViewById(R.id.album_title);
    albumArtist = view.findViewById(R.id.album_artist);
    songRecyclerView = view.findViewById(R.id.song_recycler_view);
    appBarLayout = view.findViewById(R.id.app_bar_layout);
    fab = view.findViewById(R.id.fab);
    musicPlayerContainer = view.findViewById(R.id.music_player_container);
    musicProgressIndicator = view.findViewById(R.id.music_progress_indicator);
    playButton = view.findViewById(R.id.music_play_button);
    rewindButton = view.findViewById(R.id.rewind_button);
    fastForwardButton = view.findViewById(R.id.fast_forward_button);
    volumeDownButton = view.findViewById(R.id.volume_down_button);
    volumeUpButton = view.findViewById(R.id.volume_up_button);
    volumeSlider = view.findViewById(R.id.volume_slider);

    trackAdapter = new TrackAdapter();
    songRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
    songRecyclerView.setAdapter(trackAdapter);

    configureCollapsingToolbar();
    setUpToolbarNavigation();
    configureWindowInsets(view);
    configureToolbarBackgroundAnimation();
    setUpMusicPlayerTransitions(container);
    initializePlayerControls();
    initViewModelObservers();

    surahDetailViewModel.loadSurah(surahNumber);
  }

  private void configureCollapsingToolbar() {
    if (appBarLayout == null) {
      collapsingToolbarLayout = null;
      return;
    }
    View firstChild = appBarLayout.getChildCount() > 0 ? appBarLayout.getChildAt(0) : null;
    if (firstChild instanceof CollapsingToolbarLayout) {
      collapsingToolbarLayout = (CollapsingToolbarLayout) firstChild;
    } else {
      collapsingToolbarLayout = null;
    }
  }

  private void setUpToolbarNavigation() {
    if (toolbar == null) {
      return;
    }

    ViewCompat.setElevation(toolbar, 0F);
    toolbar.setClickable(true);
    toolbar.setFocusable(true);
    toolbar.setNavigationOnClickListener(v -> {
      if (isAdded()) {
        OnBackPressedDispatcher dispatcher = requireActivity().getOnBackPressedDispatcher();
        dispatcher.onBackPressed();
      }
    });
    toolbar.setOnMenuItemClickListener(this::onToolbarMenuItemClick);

    toolbar.post(() -> {
      int childCount = toolbar.getChildCount();
      for (int i = 0; i < childCount; i++) {
        View child = toolbar.getChildAt(i);
        if (child != null) {
          child.setClickable(true);
          child.setFocusable(true);
        }
      }
    });
  }

  private boolean onToolbarMenuItemClick(@NonNull MenuItem item) {
    if (item.getItemId() == R.id.item_translation) {
      showTranslationSelectionDialog();
      return true;
    }
    return false;
  }

  private void configureWindowInsets(@NonNull View rootView) {
    if (toolbar == null || appBarLayout == null) {
      return;
    }

    final View toolbarSpacer = rootView.findViewById(R.id.toolbar_spacer);
    final int statusBarHeight = getStatusBarHeight();

    ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
      Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
      Insets displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
      int safeTop = Math.max(systemBars.top, displayCutout.top);
      int safeRight = Math.max(systemBars.right, displayCutout.right);
      int safeLeft = Math.max(systemBars.left, displayCutout.left);

      int topInset = safeTop > 0 ? safeTop : statusBarHeight;

      if (toolbarSpacer != null) {
        ViewGroup.LayoutParams params = toolbarSpacer.getLayoutParams();
        if (params != null) {
          params.height = topInset;
          toolbarSpacer.setLayoutParams(params);
        }
      }

      appBarLayout.setPadding(
          appBarLayout.getPaddingLeft(),
          topInset,
          appBarLayout.getPaddingRight(),
          appBarLayout.getPaddingBottom()
      );

      toolbar.setContentInsetsRelative(safeLeft, safeRight);
      return insets;
    });

    rootView.post(() -> {
      WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(rootView);
      if (insets != null) {
        ViewCompat.dispatchApplyWindowInsets(rootView, insets);
      } else {
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
  }

  private void configureToolbarBackgroundAnimation() {
    if (toolbar == null || appBarLayout == null) {
      return;
    }

    toolbar.setBackgroundColor(Color.TRANSPARENT);
    final boolean[] isToolbarBackgroundVisible = {false};

    appBarLayout.addOnOffsetChangedListener((appBarLayout1, verticalOffset) -> {
      float verticalOffsetPercentage =
          (float) Math.abs(verticalOffset) / (float) appBarLayout1.getTotalScrollRange();

      boolean shouldShowBackground = verticalOffsetPercentage > 0.9F;

      if (shouldShowBackground != isToolbarBackgroundVisible[0]) {
        isToolbarBackgroundVisible[0] = shouldShowBackground;

        int targetAlpha = shouldShowBackground ? 255 : 0;
        int currentAlpha = 0;

        if (toolbar.getBackground() instanceof ColorDrawable) {
          currentAlpha = ((ColorDrawable) toolbar.getBackground()).getAlpha();
        } else if (toolbar.getBackground() != null) {
          currentAlpha = toolbar.getBackground().getAlpha();
        }

        if (toolbarBackgroundAnimator != null && toolbarBackgroundAnimator.isRunning()) {
          toolbarBackgroundAnimator.cancel();
        }

        toolbarBackgroundAnimator = ValueAnimator.ofInt(currentAlpha, targetAlpha);
        toolbarBackgroundAnimator.setDuration(200);
        toolbarBackgroundAnimator.addUpdateListener(animator -> {
          int alpha = (Integer) animator.getAnimatedValue();
          int lightGreyColor = Color.rgb(245, 245, 245);
          if (alpha == 0) {
            toolbar.setBackgroundColor(Color.TRANSPARENT);
          } else {
            int colorWithAlpha = Color.argb(alpha, Color.red(lightGreyColor),
                Color.green(lightGreyColor), Color.blue(lightGreyColor));
            toolbar.setBackgroundColor(colorWithAlpha);
          }
        });
        toolbarBackgroundAnimator.start();
      }

      if (fab != null && musicPlayerContainer != null) {
        if (verticalOffsetPercentage > 0.2F && fab.isOrWillBeShown()) {
          fab.hide();
        } else if (verticalOffsetPercentage <= 0.2F
            && fab.isOrWillBeHidden()
            && musicPlayerContainer.getVisibility() != View.VISIBLE) {
          fab.show();
        }
      }
    });
  }

  private void setUpMusicPlayerTransitions(@Nullable ViewGroup container) {
    if (fab == null || musicPlayerContainer == null || container == null) {
      return;
    }

    Context context = requireContext();
    MaterialContainerTransform musicPlayerEnterTransform =
        createMusicPlayerTransform(context, /* entering= */ true, fab, musicPlayerContainer);
    MaterialContainerTransform musicPlayerExitTransform =
        createMusicPlayerTransform(context, /* entering= */ false, musicPlayerContainer, fab);

    fab.setOnClickListener(
        v -> {
          TransitionManager.beginDelayedTransition(container, musicPlayerEnterTransform);
          fab.setVisibility(View.GONE);
          musicPlayerContainer.setVisibility(View.VISIBLE);
        });

    musicPlayerContainer.setOnClickListener(
        v -> {
          TransitionManager.beginDelayedTransition(container, musicPlayerExitTransform);
          musicPlayerContainer.setVisibility(View.GONE);
          fab.setVisibility(View.VISIBLE);
        });
  }

  private void initializePlayerControls() {
    if (musicProgressIndicator != null) {
      musicProgressIndicator.setProgressCompat(0, false);
      musicProgressIndicator.setClickable(true);
      musicProgressIndicator.setFocusable(true);
      musicProgressIndicator.setOnTouchListener((view, motionEvent) -> {
        if (!isAdded()) {
          return false;
        }

        switch (motionEvent.getActionMasked()) {
          case MotionEvent.ACTION_DOWN:
            if (!hasAudioPermission()) {
              final int requestedSeekPositionDown = calculateSeekPosition(view, motionEvent.getX());
              requestAudioPermission(() -> {
                if (playbackService != null && currentDurationMs > 0) {
                  playbackService.seekTo(requestedSeekPositionDown);
                  updateProgress(requestedSeekPositionDown, currentDurationMs);
                }
              });
              return false;
            }
            isUserSeeking = true;
            updateIndicatorForSeek(calculateSeekPosition(view, motionEvent.getX()));
            ViewParent parent = view.getParent();
            if (parent != null) {
              parent.requestDisallowInterceptTouchEvent(true);
            }
            return true;

          case MotionEvent.ACTION_MOVE:
            if (!isUserSeeking) {
              return false;
            }
            updateIndicatorForSeek(calculateSeekPosition(view, motionEvent.getX()));
            return true;

          case MotionEvent.ACTION_UP:
          case MotionEvent.ACTION_CANCEL:
            if (!isUserSeeking) {
              return false;
            }
            int targetPosition = calculateSeekPosition(view, motionEvent.getX());
            isUserSeeking = false;

            if (!hasAudioPermission()) {
              requestAudioPermission(() -> {
                if (playbackService != null && currentDurationMs > 0) {
                  playbackService.seekTo(targetPosition);
                  updateProgress(targetPosition, currentDurationMs);
                }
              });
              if (playbackService != null) {
                updateProgress(playbackService.getCurrentPosition(), playbackService.getDuration());
              } else {
                updateProgress(0, currentDurationMs);
              }
              return true;
            }

            if (playbackService != null && currentDurationMs > 0) {
              playbackService.seekTo(targetPosition);
              updateProgress(targetPosition, currentDurationMs);
            } else {
              updateProgress(0, currentDurationMs);
            }
            return true;

          default:
            return false;
        }
      });
    }

    if (playButton != null) {
      playButton.setOnClickListener(v -> togglePlayPause());
    }

    if (rewindButton != null) {
      rewindButton.setOnClickListener(v -> playPreviousSurah());
    }

    if (fastForwardButton != null) {
      fastForwardButton.setOnClickListener(v -> playNextSurah());
    }

    if (volumeSlider != null) {
      volumeSlider.addOnChangeListener((slider, value, fromUser) -> {
        if (fromUser && playbackService != null) {
          playbackService.setVolume(value / 10f);
        }
      });
    }

    if (volumeDownButton != null) {
      volumeDownButton.setOnClickListener(v -> adjustVolume(-0.5f));
    }

    if (volumeUpButton != null) {
      volumeUpButton.setOnClickListener(v -> adjustVolume(0.5f));
    }
  }

  private void initViewModelObservers() {
    surahDetailViewModel = new ViewModelProvider(requireActivity()).get(SurahDetailViewModel.class);
    LiveData<SurahDetailUiState> uiStateLiveData =
        SurahDetailViewModelExtensionsKt.uiStateLiveData(surahDetailViewModel);
    uiStateLiveData.observe(getViewLifecycleOwner(), this::handleUiState);

    availableTranslations = new ArrayList<>(surahDetailViewModel.getAvailableTranslations());

    LiveData<String> translationCodeLiveData =
        SurahDetailViewModelExtensionsKt.currentTranslationLiveData(surahDetailViewModel);
    translationCodeLiveData.observe(getViewLifecycleOwner(), code -> {
      if (code != null && !code.isEmpty()) {
        currentTranslationCode = code;
      }
    });
  }

  private void handleUiState(@NonNull SurahDetailUiState state) {
    if (state instanceof SurahDetailUiState.Success) {
      SurahDetailUiState.Success success = (SurahDetailUiState.Success) state;
      bindSurahData(success.getSurah(), success.getAyahs());
    } else if (state instanceof SurahDetailUiState.Error) {
      SurahDetailUiState.Error error = (SurahDetailUiState.Error) state;
      if (isAdded()) {
        Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_SHORT).show();
      }
    }
  }

  private void bindSurahData(@NonNull Surah surah, @NonNull List<Ayah> ayahs) {
    currentSurah = surah;
    currentAyahs = new ArrayList<>(ayahs);
    currentSurahIndex = Math.max(0, surah.getNumber() - 1);

    if (getView() != null) {
      ViewGroup rootContainer = getView().findViewById(R.id.container);
      if (rootContainer != null) {
        ViewCompat.setTransitionName(rootContainer, surah.getNameEnglish());
      }
    }

    if (albumImage != null) {
      int coverIndex = currentSurahIndex % COVER_RESOURCES.length;
      albumImage.setImageResource(COVER_RESOURCES[coverIndex]);
    }

    if (albumTitle != null) {
      albumTitle.setText(surah.getNameEnglish());
    }

    if (albumArtist != null) {
      albumArtist.setText(surah.getNameArabic());
    }

    if (collapsingToolbarLayout != null) {
      collapsingToolbarLayout.setTitle(surah.getNameEnglish());
      collapsingToolbarLayout.setExpandedTitleColor(Color.TRANSPARENT);
      collapsingToolbarLayout.setCollapsedTitleTextColor(
          toolbar.getContext().getResources().getColor(android.R.color.black, null));
    }

    updateTrackList(currentAyahs);
  }

  private void updateTrackList(@NonNull List<Ayah> ayahs) {
    List<Track> tracks = new ArrayList<>();
    for (Ayah ayah : ayahs) {
      tracks.add(new Track(ayah.getNumberInSurah(), ayah.getText(), "", false));
    }
    currentTracks = tracks;
    if (trackAdapter != null) {
      trackAdapter.submitList(new ArrayList<>(currentTracks));
    }
  }

  private void updatePlayButtonState(boolean isPlaying) {
    if (playButton != null) {
      playButton.setIconResource(isPlaying ? R.drawable.ic_pause_white_24dp
          : R.drawable.ic_play_arrow_white_24dp);
      playButton.setContentDescription(
          isPlaying ? getString(R.string.cat_music_player_pause_button_content_description)
              : getString(R.string.cat_music_player_play_button_content_description));
    }

    if (fab != null) {
      fab.setImageResource(isPlaying ? R.drawable.ic_pause_white_24dp
          : R.drawable.ic_play_arrow_white_24dp);
    }

    if (!currentTracks.isEmpty()) {
      for (Track track : currentTracks) {
        track.playing = false;
      }
      if (isPlaying) {
        currentTracks.get(0).playing = true;
      }
      if (trackAdapter != null) {
        trackAdapter.submitList(new ArrayList<>(currentTracks));
      }
    }
  }

  private void updateProgress(int positionMs, int durationMs) {
    currentDurationMs = durationMs;
    if (musicProgressIndicator == null) {
      return;
    }

    if (durationMs <= 0) {
      if (!isUserSeeking) {
        musicProgressIndicator.setProgressCompat(0, false);
      }
      musicProgressIndicator.setEnabled(false);
      return;
    }

    musicProgressIndicator.setEnabled(true);

    if (isUserSeeking) {
      return;
    }

    int percent = (int) ((positionMs / (float) durationMs) * 100f);
    percent = Math.max(0, Math.min(100, percent));
    musicProgressIndicator.setProgressCompat(percent, true);
  }

  private void updateIndicatorForSeek(int positionMs) {
    if (musicProgressIndicator == null || currentDurationMs <= 0) {
      return;
    }
    int percent = (int) ((positionMs / (float) currentDurationMs) * 100f);
    percent = Math.max(0, Math.min(100, percent));
    musicProgressIndicator.setProgressCompat(percent, false);
  }

  private int calculateSeekPosition(@NonNull View view, float touchX) {
    if (currentDurationMs <= 0) {
      return 0;
    }
    int availableWidth = view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();
    if (availableWidth <= 0) {
      return 0;
    }
    float relativeX = touchX - view.getPaddingLeft();
    float clampedX = Math.max(0f, Math.min(relativeX, availableWidth));
    float fraction = clampedX / availableWidth;
    fraction = Math.max(0f, Math.min(fraction, 1f));
    return (int) (currentDurationMs * fraction);
  }

  private void adjustVolume(float delta) {
    if (volumeSlider == null) {
      return;
    }
    float newValue = Math.max(0f, Math.min(10f, volumeSlider.getValue() + delta));
    volumeSlider.setValue(newValue);
    if (playbackService != null) {
      playbackService.setVolume(newValue / 10f);
    }
  }

  private void togglePlayPause() {
    if (!hasAudioPermission()) {
      requestAudioPermission(this::performTogglePlayPause);
      return;
    }
    performTogglePlayPause();
  }

  private void playNextSurah() {
    if (!hasAudioPermission()) {
      requestAudioPermission(this::performPlayNextSurah);
      return;
    }
    performPlayNextSurah();
  }

  private void playPreviousSurah() {
    if (!hasAudioPermission()) {
      requestAudioPermission(this::performPlayPreviousSurah);
      return;
    }
    performPlayPreviousSurah();
  }

  private void bindPlaybackService() {
    if (!isAdded() || isServiceBound) {
      return;
    }
    Context appContext = requireContext().getApplicationContext();
    Intent intent = new Intent(appContext, QuranPlaybackService.class);
    appContext.startService(intent);
    appContext.bindService(intent, playbackConnection, Context.BIND_AUTO_CREATE);
  }

  private void unbindPlaybackService() {
    if (!isAdded() || !isServiceBound) {
      return;
    }
    Context appContext = requireContext().getApplicationContext();
    try {
      appContext.unbindService(playbackConnection);
    } catch (IllegalArgumentException ignored) {
      // Service already unbound.
    }
    isServiceBound = false;
    playbackService = null;
  }

  private void configurePlaybackCallbacks() {
    if (playbackService == null) {
      return;
    }

    playbackService.setOnPlaybackStateChanged(new Function1<Boolean, Unit>() {
      @Override
      public Unit invoke(Boolean playing) {
        mainHandler.post(() -> updatePlayButtonState(playing));
        return Unit.INSTANCE;
      }
    });

    playbackService.setOnSurahChanged(new Function1<Integer, Unit>() {
      @Override
      public Unit invoke(Integer index) {
        mainHandler.post(() -> {
          currentSurahIndex = index != null ? index : currentSurahIndex;
          List<com.starception.submission.feature.quran.Surah> surahList =
              QuranData.INSTANCE.getSurahs();
          if (index != null && index >= 0 && index < surahList.size()) {
            com.starception.submission.feature.quran.Surah surahMeta = surahList.get(index);
            surahNumber = surahMeta.getNumber();
            if (surahDetailViewModel != null) {
              surahDetailViewModel.loadSurah(surahNumber, currentTranslationCode);
            }
          }
          updatePlayButtonState(playbackService != null && playbackService.isPlaying());
        });
        return Unit.INSTANCE;
      }
    });

    playbackService.setOnProgressChanged(new Function2<Integer, Integer, Unit>() {
      @Override
      public Unit invoke(Integer position, Integer duration) {
        mainHandler.post(() -> updateProgress(
            position != null ? position : 0,
            duration != null ? duration : 0));
        return Unit.INSTANCE;
      }
    });

    playbackService.setAudioLanguage(currentAudioLanguage);
    updatePlayButtonState(playbackService.isPlaying());
    updateProgress(playbackService.getCurrentPosition(), playbackService.getDuration());
    if (volumeSlider != null) {
      volumeSlider.setValue(3.5f);
    }
  }

  private void clearPlaybackCallbacks() {
    if (playbackService != null) {
      playbackService.setOnPlaybackStateChanged(null);
      playbackService.setOnSurahChanged(null);
      playbackService.setOnProgressChanged(null);
    }
  }

  private final ServiceConnection playbackConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      if (service instanceof QuranPlaybackService.QuranBinder) {
        QuranPlaybackService.QuranBinder binder =
            (QuranPlaybackService.QuranBinder) service;
        playbackService = binder.getService();
        isServiceBound = true;
        configurePlaybackCallbacks();
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      clearPlaybackCallbacks();
      playbackService = null;
      isServiceBound = false;
      mainHandler.post(() -> updatePlayButtonState(false));
    }
  };

  @Override
  public void onStart() {
    super.onStart();
    bindPlaybackService();
  }

  @Override
  public void onStop() {
    super.onStop();
    clearPlaybackCallbacks();
    unbindPlaybackService();
  }

  private void performTogglePlayPause() {
    if (playbackService == null) {
      return;
    }
    if (playbackService.isPlaying()) {
      playbackService.togglePlayPause();
    } else {
      if (currentSurahIndex < 0) {
        currentSurahIndex = Math.max(0, surahNumber - 1);
      }
      playbackService.setAudioLanguage(currentAudioLanguage);
      playbackService.playSurah(currentSurahIndex, true);
    }
  }

  private void performPlayNextSurah() {
    if (playbackService != null) {
      playbackService.playNext();
    }
  }

  private void performPlayPreviousSurah() {
    if (playbackService != null) {
      playbackService.playPrevious();
    }
  }

  private void showTranslationSelectionDialog() {
    if (!isAdded()) {
      return;
    }

    List<String> translationCodes = !availableTranslations.isEmpty()
        ? availableTranslations
        : Arrays.asList("ar", "bn", "en");

    CharSequence[] options = new CharSequence[translationCodes.size()];
    for (int i = 0; i < translationCodes.size(); i++) {
      String code = translationCodes.get(i);
      options[i] = getTranslationDisplayName(code);
    }

    int selectedIndex = translationCodes.indexOf(currentTranslationCode);
    if (selectedIndex < 0) {
      selectedIndex = 0;
    }

    ContextThemeWrapper dialogContext =
        new ContextThemeWrapper(requireContext(),
            com.google.android.material.R.style.Theme_MaterialComponents_Light_Dialog_Alert);

    new MaterialAlertDialogBuilder(dialogContext)
        .setTitle(R.string.quran_translation_dialog_title)
        .setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
          String selectedCode = translationCodes.get(which);
          dialog.dismiss();
          handleTranslationSelection(selectedCode);
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void handleTranslationSelection(@NonNull String translationCode) {
    String sanitizedCode = translationCode.isEmpty() ? "ar" : translationCode;
    String previousCode = currentTranslationCode;
    currentTranslationCode = sanitizedCode;

    if (surahDetailViewModel != null && !sanitizedCode.equals(previousCode)) {
      surahDetailViewModel.changeTranslation(sanitizedCode, surahNumber);
    }

    AudioLanguage mappedLanguage = mapTranslationCodeToAudioLanguage(sanitizedCode);
    if (mappedLanguage == null) {
      if (isAdded()) {
        Toast.makeText(
            requireContext(),
            getString(
                R.string.quran_translation_audio_not_available,
                getTranslationDisplayName(sanitizedCode)),
            Toast.LENGTH_SHORT)
            .show();
      }
      return;
    }

    if (mappedLanguage == currentAudioLanguage) {
      return;
    }

    if (!hasAudioPermission()) {
      requestAudioPermission(() -> applyAudioLanguage(mappedLanguage));
    } else {
      applyAudioLanguage(mappedLanguage);
    }
  }

  private void applyAudioLanguage(@NonNull AudioLanguage language) {
    currentAudioLanguage = language;

    if (playbackService != null) {
      playbackService.setAudioLanguage(language);
      int index = currentSurahIndex >= 0 ? currentSurahIndex : Math.max(0, surahNumber - 1);
      boolean shouldAutoPlay = playbackService.isPlaying();
      playbackService.playSurah(index, shouldAutoPlay);
      if (!shouldAutoPlay) {
        updateProgress(playbackService.getCurrentPosition(), playbackService.getDuration());
      }
    }

    if (isAdded()) {
      Toast.makeText(
          requireContext(),
          getString(R.string.quran_translation_applied_toast, getTranslationLabel(language)),
          Toast.LENGTH_SHORT)
          .show();
    }
  }

  private String getTranslationLabel(@NonNull AudioLanguage language) {
    switch (language) {
      case BENGALI_TRANSLATION:
        return getString(R.string.quran_translation_option_bengali);
      case ENGLISH_TRANSLATION:
        return getString(R.string.quran_translation_option_english);
      case ARABIC_ONLY:
      default:
        return getString(R.string.quran_translation_option_arabic_only);
    }
  }

  @Nullable
  private AudioLanguage mapTranslationCodeToAudioLanguage(@NonNull String code) {
    switch (code) {
      case "ar":
        return AudioLanguage.ARABIC_ONLY;
      case "bn":
        return AudioLanguage.BENGALI_TRANSLATION;
      case "en":
        return AudioLanguage.ENGLISH_TRANSLATION;
      default:
        return null;
    }
  }

  private String getTranslationDisplayName(@NonNull String code) {
    if (surahDetailViewModel != null) {
      return surahDetailViewModel.getTranslationName(code);
    }
    return code.toUpperCase(Locale.US);
  }

  private boolean hasAudioPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return true;
    }
    if (!isAdded()) {
      return false;
    }
    String permission = getRequiredAudioPermission();
    return ContextCompat.checkSelfPermission(requireContext(), permission)
        == PackageManager.PERMISSION_GRANTED;
  }

  private void requestAudioPermission(@NonNull Runnable onGranted) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      onGranted.run();
      return;
    }
    if (hasAudioPermission()) {
      onGranted.run();
      return;
    }
    pendingPermissionAction = onGranted;
    if (permissionLauncher != null) {
      permissionLauncher.launch(getRequiredAudioPermission());
    }
  }

  private String getRequiredAudioPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      return Manifest.permission.READ_MEDIA_AUDIO;
    } else {
      return Manifest.permission.READ_EXTERNAL_STORAGE;
    }
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

