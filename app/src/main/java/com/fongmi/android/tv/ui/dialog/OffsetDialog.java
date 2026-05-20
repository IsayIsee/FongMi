package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.C;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogOffsetBinding;
import com.fongmi.android.tv.player.PlayerManager;

import java.util.Locale;

public final class OffsetDialog extends BaseBottomSheetDialog {

    private static final long OFFSET_MIN_MS = -10_000;
    private static final long OFFSET_MAX_MS = 10_000;
    private static final long OFFSET_STEP_MS = 100;

    private DialogOffsetBinding binding;
    private PlayerManager player;
    private int type;

    public static OffsetDialog create() {
        return new OffsetDialog();
    }

    public OffsetDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public OffsetDialog type(int type) {
        this.type = type;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof OffsetDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    private boolean isText() {
        return type == C.TRACK_TYPE_TEXT;
    }

    private boolean isAudio() {
        return type == C.TRACK_TYPE_AUDIO;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogOffsetBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.audioSection.setVisibility(isAudio() ? View.VISIBLE : View.GONE);
        binding.textSection.setVisibility(isText() ? View.VISIBLE : View.GONE);
        binding.audioSlider.setValue(clamp(player.getAudioOffsetMs()));
        binding.textSlider.setValue(clamp(player.getTextOffsetMs()));
        setAudioValue(binding.audioSlider.getValue());
        setTextValue(binding.textSlider.getValue());
    }

    @Override
    protected void initEvent() {
        binding.reset.setOnClickListener(this::onReset);
        binding.textSlider.addOnChangeListener((slider, value, fromUser) -> onTextChange(value));
        binding.audioSlider.addOnChangeListener((slider, value, fromUser) -> onAudioChange(value));
        binding.textSlider.setLabelFormatter(v -> String.format(Locale.getDefault(), "%+.1fs", v / 1000f));
        binding.audioSlider.setLabelFormatter(v -> String.format(Locale.getDefault(), "%+.1fs", v / 1000f));
    }

    private void onReset(View view) {
        if (isText()) binding.textSlider.setValue(0);
        if (isAudio()) binding.audioSlider.setValue(0);
    }

    private void onAudioChange(float value) {
        player.setAudioOffsetMs(Math.round(value / OFFSET_STEP_MS) * OFFSET_STEP_MS);
        setAudioValue(value);
    }

    private void onTextChange(float value) {
        player.setTextOffsetMs(Math.round(value / OFFSET_STEP_MS) * OFFSET_STEP_MS);
        setTextValue(value);
    }

    private void setAudioValue(float value) {
        binding.audioValue.setText(String.format(Locale.getDefault(), "%+.1fs", value / 1000f));
    }

    private void setTextValue(float value) {
        binding.textValue.setText(String.format(Locale.getDefault(), "%+.1fs", value / 1000f));
    }

    private float clamp(long value) {
        return Math.max(OFFSET_MIN_MS, Math.min(OFFSET_MAX_MS, value));
    }
}
