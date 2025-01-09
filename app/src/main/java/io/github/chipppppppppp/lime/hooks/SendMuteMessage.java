package io.github.chipppppppppp.lime.hooks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.chipppppppppp.lime.LimeOptions;

public class SendMuteMessage implements IHook {
    private static boolean isHandlingHook = false;

    @Override
    public void hook(LimeOptions limeOptions, XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if (!limeOptions.sendMuteMessage.checked) return;

        XposedBridge.hookAllMethods(
                loadPackageParam.classLoader.loadClass(Constants.MUTE_MESSAGE_HOOK.className),
                Constants.MUTE_MESSAGE_HOOK.methodName,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        final Method valueOf = param.args[0].getClass().getMethod("valueOf", String.class);
                        if (param.args[0].toString().equals("NONE")) {
                            param.args[0] = valueOf.invoke(param.args[0], "TO_BE_SENT_SILENTLY");
                        } else {
                            param.args[0] = valueOf.invoke(param.args[0], "NONE");
                        }
                    }
                }
        );
        XposedHelpers.findAndHookMethod(
                "android.content.res.Resources",
                loadPackageParam.classLoader,
                "getString",
                int.class,
                new XC_MethodHook() {


                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (isHandlingHook) {
                            return;
                        }

                        int resourceId = (int) param.args[0];
                        Resources resources = (Resources) param.thisObject;

                        try {
                            isHandlingHook = true;

                            if (resourceId == 2132085513) {
                                @SuppressLint("ResourceType") String replacement = resources.getString(2132085514);
                                param.setResult(replacement);
                            } else if (resourceId == 2132085514) {
                                @SuppressLint("ResourceType") String replacement = resources.getString(2132085513);
                                param.setResult(replacement);
                            }
                        } finally {
                            isHandlingHook = false;
                        }
                    }


                }
        );


        XposedBridge.hookAllMethods(
                ListView.class,
                "dispatchDraw",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        ListView listView = (ListView) param.thisObject;
                        if (listView.getTag() != null) return;
                        Context context = listView.getContext();
                        if (!(context instanceof ContextWrapper) || !((ContextWrapper) context).getBaseContext().getClass().getName().equals("jp.naver.line.android.activity.chathistory.ChatHistoryActivity"))
                            return;
                        if (listView.getChildCount() == 2) {
                            ViewGroup viewGroup0 = (ViewGroup) listView.getChildAt(0);
                            ViewGroup viewGroup1 = (ViewGroup) listView.getChildAt(1);
                            TextView textView0 = (TextView) viewGroup0.getChildAt(0);
                            TextView textView1 = (TextView) viewGroup1.getChildAt(0);
                            CharSequence text = textView0.getText();
                            textView0.setText(textView1.getText());
                            textView1.setText(text);
                            viewGroup0.removeAllViews();
                            viewGroup1.removeAllViews();
                            viewGroup0.addView(textView1);
                            viewGroup1.addView(textView0);
                            listView.setTag(true);
                        }
                    }
                }
        );
    }
}
