package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.databinding.DialogAccelerationBinding;
import com.fongmi.android.tv.impl.AccelerationCallback;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class AccelerationDialog {

    private final DialogAccelerationBinding binding;
    private final AccelerationCallback callback;
    private AlertDialog dialog;

    public static AccelerationDialog create(Fragment fragment) {
        return new AccelerationDialog(fragment);
    }

    public AccelerationDialog(Fragment fragment) {
        this.callback = (AccelerationCallback) fragment;
        this.binding = DialogAccelerationBinding.inflate(LayoutInflater.from(fragment.getContext()));
    }

    public void show() {
        initDialog();
        initView();
        initEvent();
    }

    private void initDialog() {
        dialog = new MaterialAlertDialogBuilder(binding.getRoot().getContext())
                .setTitle(R.string.setting_acceleration)
                .setView(binding.getRoot())
                // 传 null，防止系统默认的“点击即关闭”逻辑
                .setPositiveButton(R.string.dialog_positive, null)
                .setNegativeButton(R.string.dialog_negative, this::onNegative)
                .create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(this::onPositive);
    }

    private void initView() {
        String text = Setting.getAcceleration();
        binding.text.setText(text);
        binding.text.setSelection(TextUtils.isEmpty(text) ? 0 : text.length());
    }

    private void initEvent() {
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            return true;
        });
    }

    private void onPositive(View view) {
        // 从输入框取值
        String url = binding.text.getText().toString().trim();

        // 逻辑校验
        if (TextUtils.isEmpty(url) || isValidHttps(url)) {
            // 执行回调保存数据
            callback.setAccelerationUrl(url);

            // 关闭对话框
            if (dialog != null) dialog.dismiss();
        } else {
            binding.text.setError(ResUtil.getString(R.string.error_input_check));
        }
    }

    private boolean isValidHttps(String url) {
        return !TextUtils.isEmpty(url)
                && android.util.Patterns.WEB_URL.matcher(url).matches()
                && url.toLowerCase().startsWith("https://");
    }

    private void onNegative(DialogInterface dialog, int which) {
        dialog.dismiss();
    }
}
