package com.wenxing.runyitong.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.wenxing.runyitong.R;
import com.wenxing.runyitong.api.ApiClient;

public class VideoPlayerActivity extends AppCompatActivity {

    private static final String TAG = "VideoPlayerActivity";
    private PlayerView playerView;
    private SimpleExoPlayer player;
    private ImageView btnBack;
    private TextView videoTitle;
    private String videoUrl;
    private String title;
    private boolean isActivityDestroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_video_player);

            // 初始化UI组件
            initViews();
            
            // 检查Intent是否为null
            if (getIntent() == null) {
                Log.e(TAG, "Intent为null，无法获取视频信息");
                if (videoTitle != null) {
                    videoTitle.setText("错误：无法获取视频信息");
                }
                return;
            }
            
            // 获取传递的视频信息
            videoUrl = getIntent().getStringExtra("video_url");
            title = getIntent().getStringExtra("video_title");
            
            // 处理视频URL，确保是完整的URL
            if (videoUrl != null && !videoUrl.isEmpty()) {
                // 检查URL是否已经是完整的URL（以http://或https://开头）
                if (!videoUrl.startsWith("http://") && !videoUrl.startsWith("https://")) {
                    // 如果不是完整URL，添加基础URL前缀
                    String baseUrl = ApiClient.getBaseUrl();
                    // 确保baseUrl和videoUrl之间有且只有一个斜杠
                    if (baseUrl.endsWith("/") && videoUrl.startsWith("/")) {
                        // 如果两边都有斜杠，去掉videoUrl开头的斜杠
                        videoUrl = baseUrl + videoUrl.substring(1);
                    } else if (!baseUrl.endsWith("/") && !videoUrl.startsWith("/")) {
                        // 如果两边都没有斜杠，添加一个斜杠
                        videoUrl = baseUrl + "/" + videoUrl;
                    } else {
                        // 否则直接拼接
                        videoUrl = baseUrl + videoUrl;
                    }
                }
            }
            
            // 设置标题，提供默认值
            if (videoTitle != null) {
                videoTitle.setText(title != null ? title : "视频播放");
            }

            // 在onCreate()中检查视频URL是否为空
            if (videoUrl == null || videoUrl.isEmpty()) {
                Log.e(TAG, "视频URL为空，无法播放视频");
                if (videoTitle != null) {
                    videoTitle.setText("错误：视频URL无效");
                }
            } else {
                // 初始化播放器
                initializePlayer();
            }
        } catch (Exception e) {
            Log.e(TAG, "onCreate过程中发生异常", e);
        }
    }

    private void initViews() {
        try {
            playerView = findViewById(R.id.player_view);
            btnBack = findViewById(R.id.btn_back);
            videoTitle = findViewById(R.id.video_title);

            // 设置返回按钮点击事件
            btnBack.setOnClickListener(v -> {
                try {
                    finish();
                } catch (Exception e) {
                    Log.e(TAG, "返回按钮点击事件异常", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "初始化UI组件异常", e);
        }
    }

    private void initializePlayer() {
        try {
            // 创建ExoPlayer实例
            player = new SimpleExoPlayer.Builder(this).build();
            playerView.setPlayer(player);

            // 创建媒体项
            MediaItem mediaItem = MediaItem.fromUri(videoUrl);
            player.setMediaItem(mediaItem);

            // 准备播放
            player.prepare();
            player.setPlayWhenReady(true);

            // 设置播放器状态监听
            player.addListener(new Player.Listener() {
                @Override
                public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                    if (playbackState == Player.STATE_ENDED) {
                        Log.d(TAG, "视频播放结束");
                    } else if (playbackState == Player.STATE_READY) {
                        Log.d(TAG, "视频准备就绪");
                    } else if (playbackState == Player.STATE_BUFFERING) {
                        Log.d(TAG, "视频缓冲中");
                    }
                }
                
                @Override
                public void onPlayerError(PlaybackException error) {
                    Log.e(TAG, "播放器错误", error);
                    if (!isActivityDestroyed && videoTitle != null) {
                        runOnUiThread(() -> {
                            if (!isActivityDestroyed && videoTitle != null) {
                                videoTitle.setText("播放错误：" + error.getMessage());
                            }
                        });
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "初始化播放器失败", e);
            if (!isActivityDestroyed && videoTitle != null) {
                runOnUiThread(() -> {
                    if (!isActivityDestroyed && videoTitle != null) {
                        videoTitle.setText("初始化错误：" + e.getMessage());
                    }
                });
            }
        }
    }

    @Override
    protected void onPause() {
        try {
            super.onPause();
            if (player != null) {
                player.pause();
            }
        } catch (Exception e) {
            Log.e(TAG, "onPause过程中发生异常", e);
        }
    }

    @Override
    protected void onStop() {
        try {
            super.onStop();
            releasePlayer();
        } catch (Exception e) {
            Log.e(TAG, "onStop过程中发生异常", e);
        }
    }
    
    @Override
    protected void onDestroy() {
        isActivityDestroyed = true;
        try {
            releasePlayer();
        } catch (Exception e) {
            Log.e(TAG, "onDestroy过程中释放资源异常", e);
        }
        super.onDestroy();
    }

    private void releasePlayer() {
        try {
            if (player != null) {
                player.release();
                player = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "释放播放器资源异常", e);
        }
    }
}