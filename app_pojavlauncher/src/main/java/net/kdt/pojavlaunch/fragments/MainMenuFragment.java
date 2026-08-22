package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.LauncherActivity;
import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.OnlineChecker;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";
    private TextView onlineTextView;
    private Handler handler;
    private Runnable statusRunnable;

    private static final long UPDATE_INTERVAL = 15000;
    private mcVersionSpinner mVersionSpinner;

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        View mDiscordButton = view.findViewById(R.id.discord_button);
        View mTelegramButton = view.findViewById(R.id.social_media_button);
        View mCustomControlButton = view.findViewById(R.id.custom_control_button);
        View mShareLogsButton = view.findViewById(R.id.share_logs_button);
        View mOpenDirectoryButton = view.findViewById(R.id.open_files_button);
        View mSettingsButton = view.findViewById(R.id.setting_button);
        TextView onlineTextView = view.findViewById(R.id.online_counter_text);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        View mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.cm2_discord_invite)));
        mTelegramButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.cm2_telegram_invite)));
        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        mSettingsButton.setOnClickListener(v -> ((LauncherActivity) requireActivity()).openSettings());
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        if (mShareLogsButton != null) {
            mShareLogsButton.setOnClickListener(v -> shareLog(requireContext()));
        }

        if (mOpenDirectoryButton != null) {
            mOpenDirectoryButton.setOnClickListener(v -> openGameDirectory(v.getContext()));
        }

        handler = new Handler(Looper.getMainLooper());

        statusRunnable = new Runnable() {
            @Override
            public void run() {
                OnlineChecker.fetchOnline("android.cherry.pizza", new OnlineChecker.OnlineCallback() {
                    @Override
                    public void onOnlineReceived(int playersCount) {
                        if (onlineTextView != null) {
                            String formattedText = onlineTextView.getContext().getString(R.string.playing_online_format, playersCount);
                            onlineTextView.setText(formattedText);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        if (onlineTextView != null) {
                            String errorText = onlineTextView.getContext().getString(R.string.unknown_online_count);
                            onlineTextView.setText(errorText);
                        }
                    }
                });

                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
    }

    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if(FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        }else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
        handler.post(statusRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (handler != null && statusRunnable != null) {
            handler.removeCallbacks(statusRunnable);
        }
    }
}
