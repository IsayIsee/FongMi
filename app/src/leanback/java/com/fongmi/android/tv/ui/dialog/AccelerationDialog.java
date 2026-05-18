package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.databinding.DialogAccelerationBinding;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.AccelerationCallback;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.QRCode;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class AccelerationDialog implements DialogInterface.OnDismissListener {

    private final DialogAccelerationBinding binding;
    private final AccelerationCallback callback;
    private final AlertDialog dialog;

    public static AccelerationDialog create(FragmentActivity activity) {
        return new AccelerationDialog(activity);
    }

    public AccelerationDialog(FragmentActivity activity) {
        this.callback = (AccelerationCallback) activity;
        this.binding = DialogAccelerationBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
    }

    public void show() {
        initDialog();
        initView();
        initEvent();
    }

    private void initDialog() {
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * 0.55f);
        dialog.getWindow().setAttributes(params);
        dialog.getWindow().setDimAmount(0);
        dialog.setOnDismissListener(this);
        dialog.show();
    }

    // 初始化视图
    private void initView() {
        String text = Setting.getAcceleration();
        binding.text.setText(text);
        binding.text.setSelection(TextUtils.isEmpty(text) ? 0 : text.length());
        binding.code.setImageBitmap(QRCode.getBitmap(Server.get().getAddress(3), 200, 0));
        binding.info.setText(ResUtil.getString(R.string.push_info, Server.get().getAddress()).replace("，", "\n"));
    }

    // 初始化事件监听
    private void initEvent() {
        EventBus.getDefault().register(this);
        binding.positive.setOnClickListener(this::onPositive);
        binding.negative.setOnClickListener(this::onNegative);

        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) binding.positive.performClick();
            return true;
        });
    }


    private void onPositive(View view) {
        String url = binding.text.getText().toString().trim();

        if (TextUtils.isEmpty(url) || isValidHttps(url)) {
            callback.setAccelerationUrl(url);
            dialog.dismiss();
        } else {
            Notify.show(ResUtil.getString(R.string.error_input_check));
        }
    }

    private boolean isValidHttps(String url) {
        return !TextUtils.isEmpty(url)
                && android.util.Patterns.WEB_URL.matcher(url).matches()
                && url.toLowerCase().startsWith("https://");
    }

    private void onNegative(View view) {
        dialog.dismiss();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() != ServerEvent.Type.SETTING) return;
        binding.text.setText(event.text());
        binding.positive.performClick();
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        EventBus.getDefault().unregister(this);
    }
}
