package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.NetdiskLogin;

/**
 * 二次开发：网盘扫码登录的透明垫页（用户看不见，下面的详情页照常显示）。
 * <p>
 * 配置中心类站源处理「扫码登录」时：先关掉当前界面，再把二维码弹到下一个露出来的界面上，
 * 而且不往详情页（VideoActivity）上弹（它是按「片库点条目 → 打开详情页 → 关掉详情页、在片库上弹码」设计的）。
 * 所以垫两层本页：
 * <ul>
 * <li>上层 TRIGGER：发起请求，被站源关掉；</li>
 * <li>下层 HOST：二维码弹在它上面；二维码窗口关掉后（本页重新拿到焦点）自己退出，回到详情页。</li>
 * </ul>
 */
public class LoginBridgeActivity extends AppCompatActivity {

    public static final int REQUEST_CODE = 6019;

    private static final String MODE_HOST = "host";
    private static final String MODE_TRIGGER = "trigger";
    /** 站源关掉上层后，这么久还没弹出二维码就退出，免得透明页挡住遥控器 */
    private static final long NO_DIALOG_FINISH_MS = 3000;

    private int resumeCount;
    private boolean dialogShown;

    public static void start(Activity activity, NetdiskLogin.Entry entry, int token) {
        activity.startActivityForResult(intent(activity, entry, MODE_HOST).putExtra("token", token), REQUEST_CODE);
        activity.overridePendingTransition(0, 0);
    }

    private static Intent intent(Activity activity, NetdiskLogin.Entry entry, String mode) {
        Intent intent = new Intent(activity, LoginBridgeActivity.class);
        intent.putExtra("mode", mode);
        intent.putExtra("key", entry.siteKey());
        intent.putExtra("id", entry.vodId());
        intent.putExtra("action", entry.action());
        return intent;
    }

    private NetdiskLogin.Entry entry() {
        return new NetdiskLogin.Entry(getIntent().getStringExtra("key"), "", getIntent().getStringExtra("id"), getIntent().getStringExtra("action"), "");
    }

    private boolean isHost() {
        return MODE_HOST.equals(getIntent().getStringExtra("mode"));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isHost()) setResult(RESULT_OK, new Intent().putExtra("token", getIntent().getIntExtra("token", -1)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumeCount++;
        if (isHost()) onHostResume();
        else if (resumeCount == 1) App.post(() -> new Thread(this::request, "NetdiskLoginBridge").start(), 300);
    }

    private void onHostResume() {
        if (resumeCount == 1) {
            // 第一次：在自己上面再垫一层 TRIGGER
            startActivity(intent(this, entry(), MODE_TRIGGER));
            overridePendingTransition(0, 0);
        } else if (resumeCount == 2) {
            // TRIGGER 已被站源关掉，接下来二维码应弹在本页上；一直没弹就退出
            App.post(() -> {
                if (!dialogShown) finishBridge();
            }, NO_DIALOG_FINISH_MS);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // HOST 要等 TRIGGER 关掉后（第二次 resume）才算；TRIGGER 发起请求后就算（有的站源走 action，把码直接弹在 TRIGGER 上）
        boolean watching = isHost() ? resumeCount >= 2 : resumeCount >= 1;
        if (!watching) return;
        if (!hasFocus) dialogShown = true;    // 二维码窗口弹出，焦点被它拿走
        else if (dialogShown) finishBridge(); // 二维码窗口关掉，焦点回到本页 → 退出
    }

    private void request() {
        try {
            NetdiskLogin.showQr(entry());
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 保险：站源没有关掉 TRIGGER 的话自己关
        App.post(() -> {
            if (!dialogShown && !isFinishing() && !isDestroyed()) finishBridge();
        }, 3000);
    }

    private void finishBridge() {
        if (isFinishing() || isDestroyed()) return;
        if (isHost()) setResult(RESULT_OK, new Intent().putExtra("token", getIntent().getIntExtra("token", -1)));
        finish();
        overridePendingTransition(0, 0);
    }
}
