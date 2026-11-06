package com.fongmi.android.tv.ui.custom;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.MobileLoginLink;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.google.android.material.button.MaterialButton;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import java.io.OutputStream;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Adapts an opaque source's QR dialog inside our own app, using public Android APIs.
 * QR data stays in memory; it is never logged, copied to the clipboard or uploaded. */
public final class MobileQrLogin {
    private final Activity activity;
    private final String disk;
    private final Set<Drawable> inspected = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Runnable poll = this::inspect;
    private int attempts;
    private boolean stopped;
    private boolean decoding;
    private boolean attached;

    public MobileQrLogin(Activity activity, String disk) {
        this.activity = activity;
        this.disk = disk;
    }

    public void start() {
        if (Build.VERSION.SDK_INT >= 29) App.post(poll, 300);
    }

    public void stop() {
        stopped = true;
        App.removeCallbacks(poll);
        inspected.clear();
    }

    private void inspect() {
        if (stopped || attached || activity.isFinishing() || activity.isDestroyed() || attempts++ >= 60) return;
        if (!decoding && Build.VERSION.SDK_INT >= 29) {
            // WindowInspector exposes only this process. A focused window other
            // than the bridge decor is the source's foreground login dialog.
            for (View root : WindowInspector.getGlobalWindowViews()) {
                if (root == activity.getWindow().getDecorView() || !root.hasWindowFocus()) continue;
                ImageView image = findImage(root);
                if (image != null) {
                    decode(image);
                    break;
                }
            }
        }
        App.post(poll, 300);
    }

    private ImageView findImage(View view) {
        if (!view.isShown()) return null;
        if (view instanceof ImageView image && image.getDrawable() != null
                && image.getWidth() >= dp(100) && image.getHeight() >= dp(100)
                && image.getTag(R.id.mobile_login_actions) == null
                && !inspected.contains(image.getDrawable())) return image;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            ImageView found = findImage(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private void decode(ImageView image) {
        Drawable drawable = image.getDrawable();
        inspected.add(drawable);
        int width = Math.max(1, drawable.getIntrinsicWidth());
        int height = Math.max(1, drawable.getIntrinsicHeight());
        if (width <= 1 || height <= 1) return;
        float scale = Math.min(1f, 1024f / Math.max(width, height));
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        android.graphics.Rect bounds = new android.graphics.Rect(drawable.getBounds());
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        drawable.draw(canvas);
        drawable.setBounds(bounds);
        decoding = true;
        Task.execute(() -> {
            String value = null;
            try {
                int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
                bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
                BinaryBitmap binary = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(bitmap.getWidth(), bitmap.getHeight(), pixels)));
                value = new MultiFormatReader().decode(binary, Map.of(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER, true)).getText();
            } catch (Exception ignored) {
                // An icon or an unsupported image must leave the source dialog intact.
            }
            String result = value;
            App.post(() -> {
                decoding = false;
                if (!stopped && !attached && result != null && image.isAttachedToWindow() && image.getDrawable() == drawable) {
                    if (image.getTag(R.id.mobile_login_actions) == null) {
                        try { attached = decorate(image, result, bitmap); } catch (RuntimeException ignored) {}
                        if (attached) image.setTag(R.id.mobile_login_actions, true);
                    }
                }
                if (!attached) bitmap.recycle();
            });
        });
    }

    private boolean decorate(ImageView image, String value, Bitmap bitmap) {
        View imageBlock = image;
        while (imageBlock.getParent() instanceof ViewGroup parent) {
            if (parent instanceof LinearLayout column && column.getOrientation() == LinearLayout.VERTICAL) {
                LinearLayout panel = new LinearLayout(activity);
                panel.setOrientation(LinearLayout.VERTICAL);
                panel.setPadding(dp(12), dp(8), dp(12), dp(8));
                panel.setBackgroundColor(Color.WHITE);
                LinearLayout actions = new LinearLayout(activity);
                actions.setOrientation(LinearLayout.VERTICAL);
                panel.addView(actions, new LinearLayout.LayoutParams(-1, -2));
                TextView hint = new TextView(activity);
                hint.setTextColor(0xff333333);
                hint.setTextSize(14);
                boolean allowed = MobileLoginLink.isAllowed(disk, value);
                hint.setText(allowed ? R.string.mobile_login_same_device_hint : R.string.mobile_login_album_hint);
                actions.addView(hint, new LinearLayout.LayoutParams(-1, -2));
                if (allowed) button(actions, R.string.mobile_login_open_link, () -> open(value.trim()));
                button(actions, R.string.mobile_login_save_qr, () -> save(bitmap));
                boolean phone = activity.getResources().getConfiguration().smallestScreenWidthDp < 600;
                if (phone) updateSourceCaption(image.getRootView());
                View qrBlock = imageBlock;
                MaterialButton toggle = button(panel, phone ? R.string.mobile_login_other_device : R.string.mobile_login_hide_qr, () -> {});
                toggle.setOnClickListener(v -> {
                    boolean show = qrBlock.getVisibility() != View.VISIBLE;
                    qrBlock.setVisibility(show ? View.VISIBLE : View.GONE);
                    actions.setVisibility(show ? View.GONE : View.VISIBLE);
                    toggle.setText(show ? R.string.mobile_login_hide_qr : R.string.mobile_login_other_device);
                });
                column.addView(panel, column.indexOfChild(imageBlock), new LinearLayout.LayoutParams(-1, -2));
                if (phone) imageBlock.setVisibility(View.GONE);
                else actions.setVisibility(View.GONE);
                return true;
            }
            imageBlock = parent;
        }
        return false;
    }

    private MaterialButton button(LinearLayout panel, int label, Runnable click) {
        MaterialButton button = new MaterialButton(activity);
        button.setText(label);
        button.setMinHeight(dp(48));
        button.setOnClickListener(v -> click.run());
        panel.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private void updateSourceCaption(View view) {
        if (view instanceof TextView text) {
            String value = text.getText().toString();
            if (value.startsWith("请使用") && value.contains("扫码")) text.setText(activity.getString(R.string.mobile_login_title, disk));
            else if (value.startsWith("打开 App") && value.contains("扫码")) text.setText(R.string.mobile_login_source_hint);
        }
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) updateSourceCaption(group.getChildAt(i));
    }

    private void open(String value) {
        // 先交给网盘自家 App（夸克 App 本身就是浏览器、已登录账号，打开二维码链接即可确认登录，相当于本机扫码）；
        // 没装再用系统浏览器
        String app = MobileLoginLink.appPackage(disk);
        if (app != null && start(new Intent(Intent.ACTION_VIEW, Uri.parse(value)).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(app))) return;
        if (!start(new Intent(Intent.ACTION_VIEW, Uri.parse(value)).addCategory(Intent.CATEGORY_BROWSABLE))) Notify.show(R.string.mobile_login_no_browser);
    }

    private boolean start(Intent intent) {
        try {
            activity.startActivity(intent);
            return true;
        } catch (android.content.ActivityNotFoundException e) {
            return false;
        }
    }

    private void save(Bitmap bitmap) {
        if (Build.VERSION.SDK_INT < 29) return;
        Task.execute(() -> {
            Uri saved = null;
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "netdisk-login-" + System.currentTimeMillis() + ".png");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/看锤子");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
                var resolver = activity.getApplicationContext().getContentResolver();
                saved = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (saved == null) throw new java.io.IOException();
                try (OutputStream output = resolver.openOutputStream(saved)) {
                    if (output == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new java.io.IOException();
                }
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(saved, values, null, null);
                App.post(() -> Notify.show(R.string.mobile_login_qr_saved));
            } catch (Exception e) {
                if (saved != null) activity.getContentResolver().delete(saved, null, null);
                App.post(() -> Notify.show(R.string.mobile_login_save_failed));
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
