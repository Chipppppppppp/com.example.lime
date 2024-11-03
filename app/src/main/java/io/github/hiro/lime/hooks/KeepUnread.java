package io.github.hiro.lime.hooks;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Switch;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hiro.lime.LimeOptions;
import io.github.hiro.lime.R;

public class KeepUnread implements IHook {
    static boolean keepUnread = false;

    @Override
    public void hook(LimeOptions limeOptions, XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if (limeOptions.removeKeepUnread.checked) return;


        XposedHelpers.findAndHookMethod(
                "com.linecorp.line.chatlist.view.fragment.ChatListFragment",
                loadPackageParam.classLoader,
                "onCreateView",
                LayoutInflater.class, ViewGroup.class, android.os.Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        View rootView = (View) param.getResult();
                        Context appContext = rootView.getContext();

                        // モジュールのContextを取得
                        Context moduleContext = AndroidAppHelper.currentApplication().createPackageContext(
                                "io.github.hiro.lime", Context.CONTEXT_IGNORE_SECURITY);

                        RelativeLayout layout = new RelativeLayout(appContext);
                        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                        layout.setLayoutParams(layoutParams);

                        keepUnread = readStateFromFile(appContext);

                        // ImageViewをスイッチの代わりに使用
                        ImageView imageView = new ImageView(appContext);
                        updateSwitchImage(imageView, keepUnread, moduleContext);

                        RelativeLayout.LayoutParams imageParams = new RelativeLayout.LayoutParams(
                                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);

                        // 垂直中央に配置し、真ん中から右にずらす
                        imageParams.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE); // 垂直中央に配置
                        imageParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE); // 水平中央に配置
                        imageParams.setMargins(50, 0, 0, 0); // 水平に50ピクセル右に移動（必要に応じて調整）

                        // クリックリスナーで状態を切り替え
                        imageView.setOnClickListener(v -> {
                            keepUnread = !keepUnread;
                            updateSwitchImage(imageView, keepUnread, moduleContext);
                            saveStateToFile(appContext, keepUnread);
                        });

                        // 画像をレイアウトに追加
                        layout.addView(imageView, imageParams);

                        if (rootView instanceof ViewGroup) {
                            ViewGroup rootViewGroup = (ViewGroup) rootView;
                            if (rootViewGroup.getChildCount() > 0 && rootViewGroup.getChildAt(0) instanceof ListView) {
                                ListView listView = (ListView) rootViewGroup.getChildAt(0);
                                listView.addFooterView(layout);
                            } else {
                                rootViewGroup.addView(layout);
                            }
                        }
                    }


                    private void updateSwitchImage(ImageView imageView, boolean isOn, Context moduleContext) {
                        try {
                            // 状態に応じて画像のリソースIDを取得
                            String imageName = isOn ? "switch_on" : "switch_off";
                            int imageResource = moduleContext.getResources().getIdentifier(imageName, "drawable", "io.github.hiro.lime");

                            // Drawableを取得
                            if (imageResource != 0) {
                                Drawable drawable = moduleContext.getResources().getDrawable(imageResource, null);
                                if (drawable != null) {
                                    // Drawableをスケーリング
                                    drawable = scaleDrawable(drawable, 86, 86); // 幅と高さを96ピクセルに調整

                                    imageView.setImageDrawable(drawable); // DrawableをImageViewに設定
                                    XposedBridge.log("Drawable loaded successfully: " + (drawable != null));
                                } else {
                                    XposedBridge.log("Drawable is null for resource ID: " + imageResource);
                                }
                            } else {
                                XposedBridge.log("Resource not found for " + imageName + " in the specified package.");
                            }

                            XposedBridge.log("Expected package name: io.github.hiro.lime");
                            XposedBridge.log("Resource ID obtained: " + imageResource);

                            if (imageResource == 0) {
                                XposedBridge.log("Failed to retrieve resource. Check if 'switch_off' and 'switch_on' are in the drawable directory.");
                            }

                        } catch (Resources.NotFoundException e) {
                            e.printStackTrace();
                            XposedBridge.log("Exception occurred while setting image resource.");
                            // エラー時のデフォルト画像
                            imageView.setImageResource(android.R.drawable.ic_dialog_alert);
                        }
                    }

                    // Drawableを指定したサイズにスケーリングするメソッド
                    private Drawable scaleDrawable(Drawable drawable, int width, int height) {
                        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
                        return new BitmapDrawable(scaledBitmap);
                    }

                    private void saveStateToFile(Context context, boolean state) {
                        String filename = "keep_unread_state.txt";
                        try (FileOutputStream fos = context.openFileOutput(filename, Context.MODE_PRIVATE)) {
                            fos.write((state ? "1" : "0").getBytes());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    private boolean readStateFromFile(Context context) {
                        String filename = "keep_unread_state.txt";
                        try (FileInputStream fis = context.openFileInput(filename)) {
                            int c;
                            StringBuilder sb = new StringBuilder();
                            while ((c = fis.read()) != -1) {
                                sb.append((char) c);
                            }
                            return "1".equals(sb.toString());
                        } catch (IOException e) {
                            e.printStackTrace();
                            return false;
                        }
                    }
                }
        );

        XposedHelpers.findAndHookMethod(
                loadPackageParam.classLoader.loadClass(Constants.MARK_AS_READ_HOOK.className),
                Constants.MARK_AS_READ_HOOK.methodName,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (keepUnread) {
                            param.setResult(null); // スイッチがtrueのときのみ実行
                        }
                    }
                }
        );
    }
}