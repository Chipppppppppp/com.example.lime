package io.github.chipppppppppp.lime;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.customtabs.CustomTabsIntent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import android.app.AndroidAppHelper;
import android.content.res.XModuleResources;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LayoutInflated;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.chipppppppppp.lime.hooks.AddRegistrationOptions;
import io.github.chipppppppppp.lime.hooks.BlockTracking;
import io.github.chipppppppppp.lime.hooks.EmbedOptions;
import io.github.chipppppppppp.lime.hooks.IHook;
import io.github.chipppppppppp.lime.hooks.KeepUnread;
import io.github.chipppppppppp.lime.hooks.OutputCommunication;
import io.github.chipppppppppp.lime.hooks.PreventMarkAsRead;
import io.github.chipppppppppp.lime.hooks.PreventUnsendMessage;
import io.github.chipppppppppp.lime.hooks.RedirectWebView;
import io.github.chipppppppppp.lime.hooks.RemoveAds;
import io.github.chipppppppppp.lime.hooks.RemoveIconLabels;
import io.github.chipppppppppp.lime.hooks.RemoveIcons;
import io.github.chipppppppppp.lime.hooks.RemoveRecommendation;
import io.github.chipppppppppp.lime.hooks.RemoveReplyMute;
import io.github.chipppppppppp.lime.hooks.SecondaryLogin;
import io.github.chipppppppppp.lime.hooks.SendMuteMessage;
import io.github.chipppppppppp.lime.hooks.SpoofAndroidId;

public class Main implements IXposedHookLoadPackage, IXposedHookInitPackageResources, IXposedHookZygoteInit {
    public static String MODULE_PATH;
    public static final String PACKAGE = "jp.naver.line.android";
    public static final String MODULE = "io.github.chipppppppppp.lime";

    public static XSharedPreferences xModulePrefs;
    public static XSharedPreferences xPackagePrefs;
    public static XSharedPreferences xPrefs;
    public static LimeOptions limeOptions = new LimeOptions();

    static final IHook[] hooks = {
            new SpoofAndroidId(),
            new SecondaryLogin(),
            new AddRegistrationOptions(),
            new EmbedOptions(),
            new RemoveIcons(),
            new RemoveIconLabels(),
            new RemoveAds(),
            new RemoveRecommendation(),
            new RemoveReplyMute(),
            new RedirectWebView(),
            new PreventMarkAsRead(),
            new PreventUnsendMessage(),
            new SendMuteMessage(),
            new KeepUnread(),
            new BlockTracking(),
            new OutputCommunication()
    };

    public void handleLoadPackage(@NonNull XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if (!loadPackageParam.packageName.equals(PACKAGE)) return;

        xModulePrefs = new XSharedPreferences(MODULE, "options");
        xPackagePrefs = new XSharedPreferences(PACKAGE, MODULE + "-options");
        if (xModulePrefs.getBoolean("unembed_options", false)) {
            xPrefs = xModulePrefs;
        } else {
            xPrefs = xPackagePrefs;
        }
        for (LimeOptions.Option option : limeOptions.options) {
            option.checked = xPrefs.getBoolean(option.name, option.checked);
        }

        for (IHook hook : hooks) {
            hook.hook(limeOptions, loadPackageParam);
        }
    }

    @Override
    public void handleInitPackageResources(@NonNull XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable {
        if (!resparam.packageName.equals(PACKAGE) || !limeOptions.deleteIconLabels.checked) return;

        XModuleResources xModuleResources = XModuleResources.createInstance(MODULE_PATH, resparam.res);

        resparam.res.setReplacement(PACKAGE, "dimen", "main_bnb_button_height", xModuleResources.fwd(R.dimen.main_bnb_button_height));
        resparam.res.setReplacement(PACKAGE, "dimen", "main_bnb_button_width", xModuleResources.fwd(R.dimen.main_bnb_button_width));
        resparam.res.hookLayout(PACKAGE, "layout", "app_main_bottom_navigation_bar_button", new XC_LayoutInflated() {
            @Override
            public void handleLayoutInflated(XC_LayoutInflated.LayoutInflatedParam liparam) throws Throwable {
                liparam.view.setTranslationY(xModuleResources.getDimensionPixelSize(R.dimen.gnav_icon_offset));
            }
        });
    }

    @Override
    public void initZygote(@NonNull StartupParam startupParam) throws Throwable {
        MODULE_PATH = startupParam.modulePath;
    }
}
